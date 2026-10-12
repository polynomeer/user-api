package com.whatap.userapi.api.error;

import com.whatap.userapi.exception.DataLoadException;
import com.whatap.userapi.exception.UserNotFoundException;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 에러 응답을 RFC 9457 ProblemDetail로 통일하고, 클라이언트가 분기할 수 있도록 code 필드를 추가한다.
 * 스프링 MVC 표준 예외는 상위 클래스가 상태 코드를 결정하고, 여기서는 한글 detail과 code만 채운다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String CODE = "code";
	private static final String INVALID_PARAMETER = "INVALID_PARAMETER";
	// 클라이언트 입력값을 detail에 되돌려 줄 때의 최대 길이 (긴 입력이 응답·로그를 부풀리지 않도록)
	private static final int MAX_ECHO_LENGTH = 50;

	@ExceptionHandler(UserNotFoundException.class)
	public ProblemDetail handleUserNotFound(UserNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e.getMessage());
	}

	/**
	 * 서버 파일 경로 등 내부 정보는 로그에만 남기고 응답에는 노출하지 않는다.
	 * HTTP로 DataLoadException이 전파되는 경로는 재적재 API뿐이라는 전제로 "기존 데이터 유지"를 안내한다
	 * (기동 시 실패는 UserDataInitializer가 직접 처리). 다른 경로에서 던지게 되면 문구를 함께 바꿔야 한다.
	 */
	@ExceptionHandler(DataLoadException.class)
	public ProblemDetail handleDataLoad(DataLoadException e) {
		log.error("사용자 데이터 재적재 실패 - 기존 데이터를 유지합니다.", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_LOAD_FAILED",
				"사용자 데이터 파일을 읽을 수 없어 기존 데이터를 유지합니다.");
	}

	/**
	 * 처리하지 못한 예외. 원인은 스택트레이스와 함께 로그에 남기고, 응답에는 내부 메시지를 노출하지 않는다.
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");
	}

	// 경로 변수·쿼리 파라미터 타입 변환 실패 (예: /api/users/abc, page=abc, int 범위 초과)
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = "파라미터 '%s'의 값 '%s'가 올바른 형식이 아닙니다.".formatted(ex.getPropertyName(), abbreviate(ex.getValue()));
		return handleExceptionInternal(ex, problem(status, INVALID_PARAMETER, detail), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		String detail = "필수 파라미터 '%s'이(가) 없습니다.".formatted(ex.getParameterName());
		return handleExceptionInternal(ex, problem(status, INVALID_PARAMETER, detail), headers, status, request);
	}

	// @Min, @Max, @NotBlank 등 컨트롤러 파라미터 제약 위반. 메시지는 컨트롤러의 제약 선언에 정의한다
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getAllErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.distinct()
				.collect(Collectors.joining(" "));
		return handleExceptionInternal(ex, problem(status, INVALID_PARAMETER, detail), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(
			NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = "요청한 경로를 찾을 수 없습니다: /" + ex.getResourcePath();
		return handleExceptionInternal(ex, problem(status, "NOT_FOUND", detail), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
			HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		String detail = "지원하지 않는 HTTP 메서드입니다: " + ex.getMethod();
		return handleExceptionInternal(ex, problem(status, "METHOD_NOT_ALLOWED", detail), headers, status, request);
	}

	/**
	 * 위에서 따로 다루지 않은 스프링 표준 예외(415, 406 등)도 같은 형식이 되도록 code는 상태 코드 이름으로,
	 * detail은 한글 공통 문구로 채운다. 원래의 영문 detail은 디버깅을 위해 로그에 남긴다.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(
			Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		// 상위 클래스는 body가 null이면 이 메서드 안에서 ProblemDetail을 만들므로, 만들어진 뒤의 본문을 보정한다
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			String code = status != null ? status.name() : String.valueOf(statusCode.value());
			log.warn("스프링 표준 예외 처리 - code={}, detail={}", code, problem.getDetail());
			problem.setProperty(CODE, code);
			problem.setDetail("요청을 처리할 수 없습니다. (" + code + ")");
		}
		return response;
	}

	private static String abbreviate(@Nullable Object value) {
		String text = String.valueOf(value);
		return text.length() <= MAX_ECHO_LENGTH ? text : text.substring(0, MAX_ECHO_LENGTH) + "...";
	}

	private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code);
		return problem;
	}
}
