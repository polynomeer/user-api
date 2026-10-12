package com.whatap.userapi.loader;

import com.whatap.userapi.domain.User;

/**
 * 한 줄의 파싱 결과. 실패를 예외가 아닌 값으로 돌려줘 한 줄의 오류가 전체 적재를 중단시키지 않게 한다.
 */
public sealed interface ParseResult {

	record Success(User user) implements ParseResult {
	}

	record Failure(ParseFailure failure) implements ParseResult {
	}
}
