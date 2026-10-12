package com.whatap.userapi.loader;

import com.whatap.userapi.domain.User;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * user.txt의 한 줄({@code ID , 이름 , 나이 , 성별 , 연봉})을 {@link User}로 변환한다.
 * 스프링에 의존하지 않는 순수 로직이며, 검증 실패는 첫 번째 오류 하나를 {@link ParseResult.Failure}로 반환한다.
 */
public class UserLineParser {

	private static final String DELIMITER = ",";
	private static final int FIELD_COUNT = 5;
	private static final char BOM = '﻿';

	private static final int MIN_AGE = 0;
	private static final int MAX_AGE = 150;
	private static final Set<String> GENDERS = Set.of("M", "F");

	private static final Pattern DIGITS = Pattern.compile("\\d+");
	// 1000000 또는 1_000_000 형태만 허용 (자릿수 구분자 '_'는 3자리마다)
	private static final Pattern SALARY = Pattern.compile("\\d+|\\d{1,3}(_\\d{3})+");

	public ParseResult parse(int lineNumber, String rawLine) {
		String line = stripBom(rawLine);
		// limit -1: 빈 필드(",,")를 버리지 않고 필드 수 검증에 반영한다
		String[] fields = line.split(DELIMITER, -1);
		if (fields.length != FIELD_COUNT) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_FIELD_COUNT,
					"필드 수가 %d개가 아닙니다: %d개".formatted(FIELD_COUNT, fields.length));
		}

		String idText = fields[0].strip();
		String name = fields[1].strip();
		String ageText = fields[2].strip();
		String gender = fields[3].strip();
		String salaryText = fields[4].strip();

		OptionalInt id = parseNonNegativeInt(idText);
		if (id.isEmpty() || id.getAsInt() == 0) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_ID,
					"ID는 양의 정수여야 합니다: " + idText);
		}
		if (name.isEmpty()) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_NAME, "이름이 비어 있습니다");
		}
		OptionalInt age = parseNonNegativeInt(ageText);
		if (age.isEmpty() || age.getAsInt() < MIN_AGE || age.getAsInt() > MAX_AGE) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_AGE,
					"나이는 %d~%d 사이의 정수여야 합니다: %s".formatted(MIN_AGE, MAX_AGE, ageText));
		}
		if (!GENDERS.contains(gender)) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_GENDER,
					"성별은 M 또는 F여야 합니다: " + gender);
		}
		OptionalInt salary = SALARY.matcher(salaryText).matches()
				? parseNonNegativeInt(salaryText.replace("_", ""))
				: OptionalInt.empty();
		if (salary.isEmpty()) {
			return failure(lineNumber, rawLine, ParseErrorCode.INVALID_SALARY,
					"연봉 형식이 올바르지 않습니다: " + salaryText);
		}

		User user = new User(id.getAsInt(), name, age.getAsInt(), gender, salary.getAsInt());
		return new ParseResult.Success(user);
	}

	private static String stripBom(String line) {
		return !line.isEmpty() && line.charAt(0) == BOM ? line.substring(1) : line;
	}

	/**
	 * 부호 없는 숫자만 허용한다. Integer.parseInt는 "+1", "-1"을 받아들이므로 먼저 패턴으로 거른다.
	 */
	private static OptionalInt parseNonNegativeInt(String text) {
		if (!DIGITS.matcher(text).matches()) {
			return OptionalInt.empty();
		}
		try {
			return OptionalInt.of(Integer.parseInt(text));
		} catch (NumberFormatException e) {
			// 숫자만으로 이뤄졌지만 int 범위를 넘는 경우
			return OptionalInt.empty();
		}
	}

	private static ParseResult failure(int lineNumber, String rawLine, ParseErrorCode code, String message) {
		return new ParseResult.Failure(new ParseFailure(lineNumber, rawLine, code, message));
	}
}
