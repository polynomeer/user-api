package com.whatap.userapi.repository;

import com.whatap.userapi.domain.User;
import com.whatap.userapi.loader.LoadResult;
import com.whatap.userapi.loader.ParseFailure;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 한 번의 적재 결과를 담는 불변 스냅샷. 조회는 하나의 스냅샷 안에서만 이뤄져 재적재 중에도 일관된 결과를 본다.
 *
 * @param usersSortedById ID 오름차순으로 정렬된 사용자 목록 (페이징 순서)
 * @param loadedAt        적재 시각. 한 번도 적재되지 않았으면 null
 */
public record UserSnapshot(
		List<User> usersSortedById,
		Map<Integer, User> usersById,
		List<ParseFailure> failures,
		Instant loadedAt) {

	private static final UserSnapshot EMPTY = new UserSnapshot(List.of(), Map.of(), List.of(), null);

	public UserSnapshot {
		usersSortedById = List.copyOf(usersSortedById);
		usersById = Map.copyOf(usersById);
		failures = List.copyOf(failures);
	}

	public static UserSnapshot empty() {
		return EMPTY;
	}

	public static UserSnapshot from(LoadResult result) {
		List<User> sorted = result.users().stream()
				.sorted(Comparator.comparingInt(User::id))
				.toList();
		// 로더가 중복 ID를 걸러내므로 키 충돌은 발생하지 않는다
		Map<Integer, User> byId = sorted.stream()
				.collect(Collectors.toMap(User::id, Function.identity()));
		return new UserSnapshot(sorted, byId, result.failures(), result.loadedAt());
	}

	public Optional<User> findById(int id) {
		return Optional.ofNullable(usersById.get(id));
	}
}
