package com.whatap.userapi.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.whatap.userapi.domain.User;
import com.whatap.userapi.exception.DataLoadException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class UserFileLoaderTest {

	private final UserFileLoader loader = new UserFileLoader();

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("L-1: 샘플 user.txt는 성공 37건, 실패 3건(16·31·39행)으로 적재된다")
	void loadsSampleFile() throws IOException {
		Path file = Files.copy(Path.of("user.txt"), tempDir.resolve("user.txt"));

		LoadResult result = loader.load(file);

		assertThat(result.users()).hasSize(37);
		assertThat(result.failures())
				.extracting(ParseFailure::lineNumber, ParseFailure::errorCode)
				.containsExactly(
						tuple(16, ParseErrorCode.INVALID_SALARY),
						tuple(31, ParseErrorCode.INVALID_FIELD_COUNT),
						tuple(39, ParseErrorCode.INVALID_SALARY));
		assertThat(result.users()).last().isEqualTo(new User(1040, "오뚜기", 62, "M", 69_000_000));
	}

	@Test
	@DisplayName("FR-3: 파싱 실패 행마다 행 번호·에러 코드·원본을 WARN 로그로 남기고, 적재 요약을 INFO로 남긴다")
	void logsParseFailures(CapturedOutput output) throws IOException {
		Path file = Files.copy(Path.of("user.txt"), tempDir.resolve("user.txt"));

		loader.load(file);

		assertThat(output.getOut())
				.contains("user.txt 파싱 실패 - line=16, code=INVALID_SALARY")
				.contains("raw='1016 , 바사삭 , 38 , F , 45_OOO_000'")
				.contains("user.txt 파싱 실패 - line=31, code=INVALID_FIELD_COUNT")
				.contains("user.txt 파싱 실패 - line=39, code=INVALID_SALARY")
				.contains("success=37, failure=3");
	}

	@Test
	@DisplayName("L-2: 빈 줄, 헤더, 구분선은 실패로 기록하지 않고 건너뛴다")
	void skipsBlankHeaderAndSeparatorLines() throws IOException {
		Path file = write("""
				ID , 이름 , 나이 , 성별 , 연봉
				=========================

				1001 , 김다나 , 23 , M , 30_000_000
				   \s
				1002 , 김나나 , 24 , F , 31_000_000
				""");

		LoadResult result = loader.load(file);

		assertThat(result.users()).extracting(User::id).containsExactly(1001, 1002);
		assertThat(result.failures()).isEmpty();
	}

	@Test
	@DisplayName("헤더는 첫 데이터 행 이전에만 인정하고, 이후의 'id ,'로 시작하는 행은 실패로 기록한다")
	void treatsHeaderLikeLineAfterDataAsFailure() throws IOException {
		Path file = write("""
				ID , 이름 , 나이 , 성별 , 연봉
				1001 , 김다나 , 23 , M , 30_000_000
				id , 홍길동 , 20 , M , 1_000
				""");

		LoadResult result = loader.load(file);

		assertThat(result.users()).extracting(User::id).containsExactly(1001);
		assertThat(result.failures()).singleElement().satisfies(f -> {
			assertThat(f.lineNumber()).isEqualTo(3);
			assertThat(f.errorCode()).isEqualTo(ParseErrorCode.INVALID_ID);
		});
	}

	@Test
	@DisplayName("L-3: 중복 ID는 먼저 나온 행을 유지하고 이후 행을 DUPLICATE_ID로 기록한다")
	void recordsDuplicateId() throws IOException {
		Path file = write("""
				1001 , 김다나 , 23 , M , 30_000_000
				1001 , 중복이 , 30 , F , 40_000_000
				""");

		LoadResult result = loader.load(file);

		assertThat(result.users()).extracting(User::name).containsExactly("김다나");
		assertThat(result.failures()).singleElement().satisfies(f -> {
			assertThat(f.lineNumber()).isEqualTo(2);
			assertThat(f.errorCode()).isEqualTo(ParseErrorCode.DUPLICATE_ID);
		});
	}

	@Test
	@DisplayName("L-4: 파일이 없으면 DataLoadException을 던진다")
	void throwsWhenFileMissing() {
		assertThatThrownBy(() -> loader.load(tempDir.resolve("none.txt")))
				.isInstanceOf(DataLoadException.class)
				.hasMessageContaining("none.txt");
	}

	@Test
	@DisplayName("L-5: CRLF/LF 혼재와 BOM이 있어도 정상 적재된다")
	void handlesMixedLineEndingsAndBom() throws IOException {
		Path file = write("\uFEFF1001 , 김다나 , 23 , M , 30_000_000\r\n1002 , 김나나 , 24 , F , 31_000_000\n");

		LoadResult result = loader.load(file);

		assertThat(result.users()).extracting(User::id).containsExactly(1001, 1002);
		assertThat(result.failures()).isEmpty();
	}

	@Test
	@DisplayName("UTF-8이 아닌 파일은 DataLoadException을 던진다")
	void throwsWhenNotUtf8() throws IOException {
		Path file = tempDir.resolve("euc-kr.txt");
		Files.write(file, "1001 , 김다나 , 23 , M , 30_000_000".getBytes("EUC-KR"));

		assertThatThrownBy(() -> loader.load(file)).isInstanceOf(DataLoadException.class);
	}

	private Path write(String content) throws IOException {
		return Files.writeString(tempDir.resolve("user.txt"), content, StandardCharsets.UTF_8);
	}
}
