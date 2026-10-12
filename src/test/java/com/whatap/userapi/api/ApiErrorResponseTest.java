package com.whatap.userapi.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whatap.userapi.exception.GlobalExceptionHandler;
import com.whatap.userapi.service.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 에러 응답 형식(ProblemDetail + code, 한글 detail)을 검증한다. 데이터와 무관하므로 서비스는 목으로 대체한다.
 */
@WebMvcTest(UserController.class)
class ApiErrorResponseTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserService userService;

	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("A-3: page/size 범위 위반은 400 INVALID_PARAMETER와 한글 사유를 반환한다")
	@CsvSource(delimiter = '|', textBlock = """
			/api/users?size=0                  | size는 1 이상 100 이하여야 합니다.
			/api/users?size=101                | size는 1 이상 100 이하여야 합니다.
			/api/users?page=-1                 | page는 0 이상이어야 합니다.
			/api/users/search?name=김&size=0   | size는 1 이상 100 이하여야 합니다.
			""")
	void rejectsOutOfRangePaging(String uri, String detail) throws Exception {
		mockMvc.perform(get(uri))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
				.andExpect(jsonPath("$.detail").value(detail))
				.andExpect(jsonPath("$.status").value(400));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("A-3, A-6: 숫자가 아닌 page/size/id는 400 INVALID_PARAMETER를 반환한다")
	@CsvSource(delimiter = '|', textBlock = """
			/api/users?page=abc  | 파라미터 'page'의 값 'abc'가 올바른 형식이 아닙니다.
			/api/users?size=1.5  | 파라미터 'size'의 값 '1.5'가 올바른 형식이 아닙니다.
			/api/users/abc       | 파라미터 'id'의 값 'abc'가 올바른 형식이 아닙니다.
			/api/users/99999999999 | 파라미터 'id'의 값 '99999999999'가 올바른 형식이 아닙니다.
			""")
	void rejectsTypeMismatch(String uri, String detail) throws Exception {
		mockMvc.perform(get(uri))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
				.andExpect(jsonPath("$.detail").value(detail));
	}

	@Test
	@DisplayName("A-8: 검색어 파라미터가 없으면 400 INVALID_PARAMETER를 반환한다")
	void rejectsMissingName() throws Exception {
		mockMvc.perform(get("/api/users/search"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
				.andExpect(jsonPath("$.detail").value("필수 파라미터 'name'이(가) 없습니다."));
	}

	@ParameterizedTest
	@DisplayName("A-8: 검색어가 비어 있거나 공백뿐이면 400 INVALID_PARAMETER를 반환한다")
	@CsvSource({"''", "'   '"})
	void rejectsBlankName(String name) throws Exception {
		mockMvc.perform(get("/api/users/search").param("name", name))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
				.andExpect(jsonPath("$.detail").value("name은 공백일 수 없습니다."));
	}

	@Test
	@DisplayName("없는 경로는 404 NOT_FOUND를 반환한다")
	void returns404ForUnknownPath() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("요청한 경로를 찾을 수 없습니다: /api/unknown"));
	}

	@Test
	@DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED를 반환한다")
	void returns405ForUnsupportedMethod() throws Exception {
		mockMvc.perform(delete("/api/users/1001"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.detail").value("지원하지 않는 HTTP 메서드입니다: DELETE"));
	}

	@Test
	@DisplayName("타입 불일치 detail의 입력값은 50자로 잘라서 보여준다")
	void abbreviatesLongInputInDetail() throws Exception {
		String longValue = "a".repeat(60);

		mockMvc.perform(get("/api/users/" + longValue))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("파라미터 'id'의 값 '" + "a".repeat(50) + "...'가 올바른 형식이 아닙니다."));
	}

	@Test
	@DisplayName("따로 처리하지 않은 스프링 표준 예외(406)도 code와 한글 detail을 갖는다")
	void fillsCodeAndKoreanDetailForOtherSpringExceptions() throws Exception {
		// MockMvc로 Accept: application/xml을 보내면 JSON 본문을 쓸 수 없어 본문 없는 406이 되므로 핸들러를 직접 호출한다
		ResponseEntity<Object> response = new GlobalExceptionHandler().handleException(
				new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
				new ServletWebRequest(new MockHttpServletRequest()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
			assertThat(problem.getProperties()).containsEntry("code", "NOT_ACCEPTABLE");
			assertThat(problem.getDetail()).isEqualTo("요청을 처리할 수 없습니다. (NOT_ACCEPTABLE)");
		});
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR를 반환하고 내부 메시지를 노출하지 않는다")
	void hidesInternalMessageOnUnexpectedError() throws Exception {
		given(userService.findById(1001)).willThrow(new IllegalStateException("내부 상태 비밀값"));

		mockMvc.perform(get("/api/users/1001"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
	}

	@Test
	@DisplayName("핸들러 단위: 예상하지 못한 예외의 응답에 원본 메시지가 포함되지 않는다")
	void unexpectedHandlerDoesNotLeakMessage() {
		ProblemDetail problem = new GlobalExceptionHandler().handleUnexpected(new RuntimeException("secret"));

		assertThat(problem.getDetail()).doesNotContain("secret");
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
	}
}
