package com.whatap.userapi.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 에러 응답을 RFC 9457 ProblemDetail로 통일하고, 클라이언트가 분기할 수 있도록 code 필드를 추가한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(UserNotFoundException.class)
	public ProblemDetail handleUserNotFound(UserNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e.getMessage());
	}

	/**
	 * 서버 파일 경로 등 내부 정보는 로그에만 남기고 응답에는 노출하지 않는다.
	 */
	@ExceptionHandler(DataLoadException.class)
	public ProblemDetail handleDataLoad(DataLoadException e) {
		log.error("사용자 데이터 재적재 실패 - 기존 데이터를 유지합니다.", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_LOAD_FAILED",
				"사용자 데이터 파일을 읽을 수 없어 기존 데이터를 유지합니다.");
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		return problem;
	}
}
