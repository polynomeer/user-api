package com.whatap.userapi.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A-8: 기동 시 데이터 파일이 없어도 애플리케이션은 빈 데이터로 기동한다.
 */
@SpringBootTest(properties = "user-api.data.file-path=./build/no-such-dir/user.txt")
@AutoConfigureMockMvc
class UserDataStartupWithoutFileTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("파일 없이 기동하면 빈 목록과 적재 시각 null을 반환하고, 재적재는 500으로 실패한다")
	void startsWithEmptyData() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
		mockMvc.perform(get("/api/users/parse-failures"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0))
				.andExpect(jsonPath("$.loadedAt").doesNotExist());
		mockMvc.perform(post("/api/users/reload"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("DATA_LOAD_FAILED"));
	}
}
