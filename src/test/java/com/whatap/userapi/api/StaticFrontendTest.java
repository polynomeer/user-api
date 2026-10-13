package com.whatap.userapi.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whatap.userapi.service.UserService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프론트엔드 정적 리소스(src/main/resources/static)가 제공되고, 기존 API 에러 처리와 충돌하지 않는지 확인한다.
 * 화면 동작 자체는 docs/11-frontend-design.md 8절의 수동 시나리오로 확인한다.
 */
@WebMvcTest(UserController.class)
class StaticFrontendTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserService userService;

	@Test
	@DisplayName("FE-1: 루트 경로는 index.html로 연결된다 (welcome page)")
	void rootForwardsToIndex() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));
	}

	@Test
	@DisplayName("FE-2: index.html은 같은 출처만 허용하는 CSP와 화면 스크립트를 포함한다")
	void servesIndexWithCsp() throws Exception {
		// 정적 리소스의 Content-Type에는 charset이 없어 MockMvc가 ISO-8859-1로 읽으므로, 브라우저처럼 meta charset(UTF-8)으로 읽는다
		String html = mockMvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/html"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html)
				.contains("<meta charset=\"utf-8\">")
				.contains("<title>사용자 데이터 콘솔</title>")
				.contains("content=\"default-src 'self'\"")
				.contains("src=\"app.js\"");
	}

	@Test
	@DisplayName("FE-3: 스크립트와 스타일시트가 올바른 Content-Type으로 제공된다")
	void servesAssets() throws Exception {
		mockMvc.perform(get("/app.js"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
		mockMvc.perform(get("/app.css"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	@Test
	@DisplayName("FE-4: 데이터를 innerHTML로 렌더링하지 않는다 (원본 행의 HTML이 해석되지 않도록)")
	void scriptDoesNotUseInnerHtml() throws Exception {
		String script = mockMvc.perform(get("/app.js"))
				.andReturn().getResponse().getContentAsString();

		assertThat(script)
				.doesNotContain("innerHTML")
				.doesNotContain("insertAdjacentHTML")
				.contains("textContent");
	}

	@Test
	@DisplayName("FE-5: 정적 리소스를 추가해도 없는 파일은 기존과 같은 404 NOT_FOUND 응답이다")
	void missingAssetKeepsProblemDetail() throws Exception {
		mockMvc.perform(get("/missing.js"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
