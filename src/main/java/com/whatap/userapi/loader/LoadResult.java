package com.whatap.userapi.loader;

import com.whatap.userapi.domain.User;
import java.time.Instant;
import java.util.List;

/**
 * 파일 한 번 적재의 결과. users는 파일에 나온 순서를 유지한다.
 */
public record LoadResult(List<User> users, List<ParseFailure> failures, Instant loadedAt) {

	public LoadResult {
		users = List.copyOf(users);
		failures = List.copyOf(failures);
	}
}
