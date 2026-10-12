package com.whatap.userapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * 실제 내장 서버를 띄워 HTTP로 호출하는 통합 테스트.
 * MockMvc 테스트가 다루지 못하는 서블릿 컨테이너 라우팅, URL 인코딩(한글 검색어), JSON 직렬화·Content-Type을
 * 실제 요청 경로 그대로 검증하고, 기동 → 조회 → 파일 변경 → 재적재 → 실패 → 복구의 전체 흐름을 확인한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserApiIntegrationTest {

	private static final Path DATA_FILE = createTempDataFile();

	@Value("${local.server.port}")
	int port;

	RestClient client;

	@DynamicPropertySource
	static void dataFile(DynamicPropertyRegistry registry) {
		registry.add("user-api.data.file-path", DATA_FILE::toString);
	}

	private static Path createTempDataFile() {
		try {
			Path file = Files.createTempDirectory("user-api-it").resolve("user.txt");
			// 기동 시 적재(ApplicationRunner)가 읽을 수 있도록 컨텍스트 생성 전에 복사해 둔다
			Files.copy(Path.of("user.txt"), file);
			return file;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@BeforeEach
	void resetToSampleData() throws IOException {
		client = RestClient.create("http://localhost:" + port);
		Files.copy(Path.of("user.txt"), DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
		assertThat(call(HttpMethod.POST, "/api/users/reload").status()).isEqualTo(200);
	}

	@Test
	@DisplayName("I-1: 실제 HTTP 요청으로 샘플 데이터 목록을 JSON으로 조회한다")
	void listsUsersOverHttp() {
		Response response = call(HttpMethod.GET, "/api/users?page=0&size=10");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.contentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(response.body().get("totalElements").asInt()).isEqualTo(37);
		assertThat(response.body().get("totalPages").asInt()).isEqualTo(4);
		JsonNode first = response.body().get("content").get(0);
		assertThat(first.get("id").asInt()).isEqualTo(1001);
		assertThat(first.get("name").asString()).isEqualTo("김다나");
		assertThat(first.get("salary").asInt()).isEqualTo(30_000_000);
	}

	@Test
	@DisplayName("I-2: 한글 검색어가 URL 인코딩되어 전달되고 부분 일치 결과를 반환한다")
	void searchesByKoreanName() {
		Response response = call(HttpMethod.GET, "/api/users/search?name={name}", "파이");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body().get("totalElements").asInt()).isEqualTo(2);
		response.body().get("content")
				.forEach(user -> assertThat(user.get("name").asString()).contains("파이"));
	}

	@Test
	@DisplayName("I-3: 에러 응답은 실제 서버에서도 problem+json 형식과 code를 갖는다")
	void returnsProblemDetailOverHttp() {
		Response notFound = call(HttpMethod.GET, "/api/users/9999");
		assertThat(notFound.status()).isEqualTo(404);
		assertThat(notFound.contentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		assertThat(notFound.body().get("code").asString()).isEqualTo("USER_NOT_FOUND");
		assertThat(notFound.body().get("instance").asString()).isEqualTo("/api/users/9999");

		Response badId = call(HttpMethod.GET, "/api/users/abc");
		assertThat(badId.status()).isEqualTo(400);
		assertThat(badId.body().get("code").asString()).isEqualTo("INVALID_PARAMETER");

		Response noPath = call(HttpMethod.GET, "/api/nope");
		assertThat(noPath.status()).isEqualTo(404);
		assertThat(noPath.body().get("code").asString()).isEqualTo("NOT_FOUND");

		Response badMethod = call(HttpMethod.DELETE, "/api/users/1001");
		assertThat(badMethod.status()).isEqualTo(405);
		assertThat(badMethod.body().get("code").asString()).isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	@DisplayName("I-4: 파일 변경 → 재적재 반영 → 파일 삭제 시 500과 기존 데이터 유지 → 복구 후 재적재 성공")
	void reloadLifecycle() throws IOException {
		// 1. 기동 시 적재 결과: 파싱 실패 3건(행 16·31·39)
		Response failures = call(HttpMethod.GET, "/api/users/parse-failures");
		assertThat(failures.body().get("count").asInt()).isEqualTo(3);
		List<Integer> lineNumbers = new ArrayList<>();
		failures.body().get("failures").forEach(failure -> lineNumbers.add(failure.get("lineNumber").asInt()));
		assertThat(lineNumbers).containsExactly(16, 31, 39);

		// 2. 행 추가 후 재적재하면 즉시 반영된다
		Files.writeString(DATA_FILE, Files.readString(DATA_FILE) + "\r\n1041 , 신규사용자 , 30 , F , 50_000_000");
		Response reloaded = call(HttpMethod.POST, "/api/users/reload");
		assertThat(reloaded.status()).isEqualTo(200);
		assertThat(reloaded.body().get("successCount").asInt()).isEqualTo(38);
		assertThat(reloaded.body().get("failureCount").asInt()).isEqualTo(3);
		assertThat(call(HttpMethod.GET, "/api/users/1041").body().get("name").asString()).isEqualTo("신규사용자");

		// 3. 파일이 사라지면 500이지만 서비스 중인 데이터는 유지된다
		Files.delete(DATA_FILE);
		Response failed = call(HttpMethod.POST, "/api/users/reload");
		assertThat(failed.status()).isEqualTo(500);
		assertThat(failed.body().get("code").asString()).isEqualTo("DATA_LOAD_FAILED");
		assertThat(failed.body().get("detail").asString()).doesNotContain(DATA_FILE.toString());
		assertThat(call(HttpMethod.GET, "/api/users").body().get("totalElements").asInt()).isEqualTo(38);
		assertThat(call(HttpMethod.GET, "/api/users/1041").status()).isEqualTo(200);

		// 4. 파일을 복구하면 다시 재적재된다
		Files.copy(Path.of("user.txt"), DATA_FILE);
		Response recovered = call(HttpMethod.POST, "/api/users/reload");
		assertThat(recovered.status()).isEqualTo(200);
		assertThat(recovered.body().get("successCount").asInt()).isEqualTo(37);
		assertThat(call(HttpMethod.GET, "/api/users/1041").status()).isEqualTo(404);
	}

	/**
	 * 4xx/5xx도 예외 없이 상태 코드와 본문을 그대로 돌려받는다.
	 */
	private Response call(HttpMethod method, String uri, Object... uriVariables) {
		return client.method(method)
				.uri(uri, uriVariables)
				.exchange((request, response) -> new Response(
						response.getStatusCode().value(),
						response.getHeaders().getContentType(),
						response.bodyTo(JsonNode.class)));
	}

	private record Response(int status, MediaType contentType, JsonNode body) {
	}
}
