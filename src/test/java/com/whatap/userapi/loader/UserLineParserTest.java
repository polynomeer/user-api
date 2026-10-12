package com.whatap.userapi.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.whatap.userapi.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserLineParserTest {

	private final UserLineParser parser = new UserLineParser();

	@Test
	@DisplayName("P-1: 정상 행을 User로 변환하고 연봉의 '_' 구분자를 제거한다")
	void parsesValidLine() {
		ParseResult result = parser.parse(1, "1001 , 김다나 , 23 , M , 30_000_000");

		assertThat(result).isEqualTo(new ParseResult.Success(new User(1001, "김다나", 23, "M", 30_000_000)));
	}

	@Test
	@DisplayName("P-2: 후행 공백·CR이 있어도 정상 파싱한다")
	void parsesLineWithTrailingWhitespace() {
		ParseResult result = parser.parse(40, "1040 , 오뚜기 , 62 , M , 69_000_000   \r");

		assertThat(result).isEqualTo(new ParseResult.Success(new User(1040, "오뚜기", 62, "M", 69_000_000)));
	}

	@Test
	@DisplayName("P-12: 구분자 없는 연봉도 허용한다")
	void parsesSalaryWithoutSeparator() {
		ParseResult result = parser.parse(1, "1001 , 김다나 , 23 , F , 30000000");

		assertThat(result).isEqualTo(new ParseResult.Success(new User(1001, "김다나", 23, "F", 30_000_000)));
	}

	@Test
	@DisplayName("BOM으로 시작하는 행도 정상 파싱한다")
	void parsesLineWithBom() {
		ParseResult result = parser.parse(1, "﻿1001 , 김다나 , 23 , M , 30_000_000");

		assertThat(result).isInstanceOf(ParseResult.Success.class);
	}

	@Test
	@DisplayName("실패 결과에는 행 번호, 원본 행, 에러 코드, 메시지가 담긴다")
	void failureContainsLineInfo() {
		String raw = "1016 , 바사삭 , 38 , F , 45_OOO_000";

		ParseResult result = parser.parse(16, raw);

		assertThat(result).isInstanceOfSatisfying(ParseResult.Failure.class, failure -> {
			ParseFailure f = failure.failure();
			assertThat(f.lineNumber()).isEqualTo(16);
			assertThat(f.rawLine()).isEqualTo(raw);
			assertThat(f.errorCode()).isEqualTo(ParseErrorCode.INVALID_SALARY);
			assertThat(f.message()).contains("45_OOO_000");
		});
	}

	@ParameterizedTest(name = "[{index}] {0} → {1}")
	@DisplayName("P-3~P-11: 잘못된 행은 에러 코드와 함께 실패한다")
	@CsvSource(delimiter = '|', textBlock = """
			1016 , 바사삭 , 38 , F , 45_OOO_000      | INVALID_SALARY
			1039 , 농심 , 61 , M , 68_AAA_000        | INVALID_SALARY
			1031 , 일본 , 53 , F ,, 60_000_000       | INVALID_FIELD_COUNT
			1031 , 일본 , 53 , 60_000_000            | INVALID_FIELD_COUNT
			abc , 홍길동 , 20 , M , 1_000             | INVALID_ID
			0 , 홍길동 , 20 , M , 1_000               | INVALID_ID
			-1 , 홍길동 , 20 , M , 1_000              | INVALID_ID
			+1 , 홍길동 , 20 , M , 1_000              | INVALID_ID
			9999999999 , 홍길동 , 20 , M , 1_000      | INVALID_ID
			1050 ,   , 20 , M , 1_000                | INVALID_NAME
			1050 , 홍길동 , -1 , M , 1_000            | INVALID_AGE
			1050 , 홍길동 , 151 , M , 1_000           | INVALID_AGE
			1050 , 홍길동 , 20살 , M , 1_000          | INVALID_AGE
			1050 , 홍길동 , 20 , X , 1_000            | INVALID_GENDER
			1050 , 홍길동 , 20 , m , 1_000            | INVALID_GENDER
			1050 , 홍길동 , 20 ,  , 1_000             | INVALID_GENDER
			1050 , 홍길동 , 20 , M , 1__000           | INVALID_SALARY
			1050 , 홍길동 , 20 , M , _1000            | INVALID_SALARY
			1050 , 홍길동 , 20 , M , 1000_            | INVALID_SALARY
			1050 , 홍길동 , 20 , M , 10_00            | INVALID_SALARY
			1050 , 홍길동 , 20 , M , -1_000           | INVALID_SALARY
			1050 , 홍길동 , 20 , M , 9_999_999_999    | INVALID_SALARY
			""")
	void failsOnInvalidLine(String rawLine, ParseErrorCode expected) {
		ParseResult result = parser.parse(1, rawLine);

		assertThat(result).isInstanceOfSatisfying(ParseResult.Failure.class,
				failure -> assertThat(failure.failure().errorCode()).isEqualTo(expected));
	}

	@ParameterizedTest
	@DisplayName("나이 경계값 0과 150은 허용한다")
	@ValueSource(ints = {0, 150})
	void acceptsAgeBoundaries(int age) {
		ParseResult result = parser.parse(1, "1050 , 홍길동 , " + age + " , M , 1_000");

		assertThat(result).isInstanceOf(ParseResult.Success.class);
	}
}
