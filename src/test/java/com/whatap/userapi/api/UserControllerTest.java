package com.whatap.userapi.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

	@Autowired
	MockMvc mockMvc;

	/**
	 * 원본 user.txt를 건드리지 않도록 임시 복사본을 데이터 파일로 사용한다.
	 */
	@DynamicPropertySource
	static void dataFile(DynamicPropertyRegistry registry) {
		registry.add("user-api.data.file-path", () -> copySampleFile().toString());
	}

	private static Path copySampleFile() {
		try {
			Path dir = Files.createTempDirectory("user-api-test");
			return Files.copy(Path.of("user.txt"), dir.resolve("user.txt"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	@DisplayName("A-1: 목록 조회는 기본 page=0, size=10으로 ID 오름차순 페이지를 반환한다")
	void listsFirstPageByDefault() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(10))
				.andExpect(jsonPath("$.content[0].id").value(1001))
				.andExpect(jsonPath("$.content[0].name").value("김다나"))
				.andExpect(jsonPath("$.content[0].salary").value(30_000_000))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(37))
				.andExpect(jsonPath("$.totalPages").value(4));
	}

	@Test
	@DisplayName("마지막 페이지는 남은 건수만 반환하고, 파싱 실패 ID(1016)는 건너뛴다")
	void listsLastPage() throws Exception {
		mockMvc.perform(get("/api/users").param("page", "3").param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(7))
				.andExpect(jsonPath("$.content[0].id").value(1033))
				.andExpect(jsonPath("$.content[6].id").value(1040));
	}

	@Test
	@DisplayName("A-2: 범위를 넘는 페이지는 빈 content를 반환한다")
	void returnsEmptyContentWhenPageOutOfRange() throws Exception {
		mockMvc.perform(get("/api/users").param("page", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(37));
	}

	@Test
	@DisplayName("page × size가 int 범위를 넘어도 오버플로 없이 빈 content를 반환한다")
	void handlesHugePageWithoutOverflow() throws Exception {
		mockMvc.perform(get("/api/users").param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	@DisplayName("A-4: ID로 단건 조회한다")
	void findsById() throws Exception {
		mockMvc.perform(get("/api/users/1018"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1018))
				.andExpect(jsonPath("$.name").value("와탭랩스"))
				.andExpect(jsonPath("$.age").value(40))
				.andExpect(jsonPath("$.gender").value("M"))
				.andExpect(jsonPath("$.salary").value(47_000_000));
	}

	@Test
	@DisplayName("A-5: 없는 ID(파싱 실패 행 포함)는 404와 USER_NOT_FOUND를 반환한다")
	void returns404WhenUserNotFound() throws Exception {
		mockMvc.perform(get("/api/users/1016"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("ID 1016에 해당하는 사용자가 없습니다."));
	}

	@Test
	@DisplayName("A-7: 이름에 포함된 문자열로 검색한다")
	void searchesByName() throws Exception {
		mockMvc.perform(get("/api/users/search").param("name", "파이"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", containsInAnyOrder("파이리", "파이추")))
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	@DisplayName("검색어 앞뒤 공백은 무시하고, 검색 결과도 페이징된다")
	void searchesWithTrimmedKeywordAndPaging() throws Exception {
		mockMvc.perform(get("/api/users/search").param("name", " 김 ").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", contains("김다나", "김나나")))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	@DisplayName("A-9: 검색 결과가 없으면 빈 content를 반환한다")
	void returnsEmptyWhenNoMatch() throws Exception {
		mockMvc.perform(get("/api/users/search").param("name", "없는이름"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@DisplayName("A-10: 파싱 실패 목록을 행 번호, 원본, 에러 코드와 함께 조회한다")
	void listsParseFailures() throws Exception {
		mockMvc.perform(get("/api/users/parse-failures"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.loadedAt").isNotEmpty())
				.andExpect(jsonPath("$.failures[*].lineNumber", contains(16, 31, 39)))
				.andExpect(jsonPath("$.failures[0].rawLine").value("1016 , 바사삭 , 38 , F , 45_OOO_000"))
				.andExpect(jsonPath("$.failures[0].errorCode").value("INVALID_SALARY"))
				.andExpect(jsonPath("$.failures[1].errorCode").value("INVALID_FIELD_COUNT"));
	}
}
