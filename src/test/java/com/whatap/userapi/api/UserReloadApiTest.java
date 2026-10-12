package com.whatap.userapi.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 재적재는 데이터 파일을 바꾸므로 조회 테스트와 파일을 공유하지 않도록 별도 컨텍스트로 분리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserReloadApiTest {

	private static final Path DATA_FILE = createTempDataFile();

	@Autowired
	MockMvc mockMvc;

	@DynamicPropertySource
	static void dataFile(DynamicPropertyRegistry registry) {
		registry.add("user-api.data.file-path", DATA_FILE::toString);
	}

	private static Path createTempDataFile() {
		try {
			return Files.createTempDirectory("user-api-reload-test").resolve("user.txt");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@BeforeEach
	void resetToSampleData() throws Exception {
		Files.copy(Path.of("user.txt"), DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
		mockMvc.perform(post("/api/users/reload")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("API-11: 파일 변경 후 재적재하면 건수와 조회 결과에 반영된다")
	void reloadsChangedFile() throws Exception {
		Files.writeString(DATA_FILE, """
				2001 , 신규 , 30 , F , 50_000_000
				1001 , 김다나 , 23 , M , 30_000_000
				2002 , 오류 , 30 , X , 1_000
				""");

		mockMvc.perform(post("/api/users/reload"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.successCount").value(2))
				.andExpect(jsonPath("$.failureCount").value(1))
				.andExpect(jsonPath("$.loadedAt").isNotEmpty());

		mockMvc.perform(get("/api/users/2001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("신규"));
		mockMvc.perform(get("/api/users/1018"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/users"))
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/users/parse-failures"))
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.failures[0].lineNumber").value(3))
				.andExpect(jsonPath("$.failures[0].errorCode").value("INVALID_GENDER"));
	}

	@Test
	@DisplayName("API-12: 파일을 읽지 못하면 500 DATA_LOAD_FAILED를 반환하고 기존 데이터를 유지한다")
	void keepsPreviousDataWhenReloadFails() throws Exception {
		Files.delete(DATA_FILE);

		mockMvc.perform(post("/api/users/reload"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("DATA_LOAD_FAILED"))
				.andExpect(jsonPath("$.detail").value("사용자 데이터 파일을 읽을 수 없어 기존 데이터를 유지합니다."));

		mockMvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(37));
		mockMvc.perform(get("/api/users/parse-failures"))
				.andExpect(jsonPath("$.count").value(3));
	}

	/**
	 * 읽을 수만 있으면 0건도 유효한 결과로 보고 교체한다 (03-design 6.1, 07-tech-decisions 3.5).
	 * 0건을 거부하는 가드를 넣기로 하면 이 테스트를 함께 바꾼다.
	 */
	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("R-6: 빈 파일이나 헤더만 있는 파일을 재적재하면 200과 함께 0건으로 교체된다")
	@ValueSource(strings = {"", "ID , 이름 , 나이 , 성별 , 연봉\r\n=====\r\n"})
	void replacesWithEmptyDataWhenFileHasNoRows(String content) throws Exception {
		Files.writeString(DATA_FILE, content);

		mockMvc.perform(post("/api/users/reload"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.successCount").value(0))
				.andExpect(jsonPath("$.failureCount").value(0));

		mockMvc.perform(get("/api/users"))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/users/1001"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/users/parse-failures"))
				.andExpect(jsonPath("$.count").value(0))
				.andExpect(jsonPath("$.loadedAt").isNotEmpty());
	}
}
