package com.whatap.userapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whatap.userapi.domain.User;
import com.whatap.userapi.loader.LoadResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSnapshotTest {

	@Test
	@DisplayName("파일 순서와 관계없이 ID 오름차순으로 정렬하고 ID로 조회할 수 있다")
	void sortsByIdAndIndexes() {
		User late = new User(2001, "나중", 30, "F", 1_000);
		User early = new User(1001, "먼저", 20, "M", 1_000);
		LoadResult result = new LoadResult(List.of(late, early), List.of(), Instant.now());

		UserSnapshot snapshot = UserSnapshot.from(result);

		assertThat(snapshot.usersSortedById()).containsExactly(early, late);
		assertThat(snapshot.findById(2001)).contains(late);
		assertThat(snapshot.findById(9999)).isEmpty();
	}

	@Test
	@DisplayName("빈 스냅샷은 데이터가 없고 적재 시각이 null이다")
	void emptySnapshot() {
		UserSnapshot empty = UserSnapshot.empty();

		assertThat(empty.usersSortedById()).isEmpty();
		assertThat(empty.failures()).isEmpty();
		assertThat(empty.loadedAt()).isNull();
	}

	@Test
	@DisplayName("스냅샷의 컬렉션은 외부에서 변경할 수 없다")
	void isImmutable() {
		UserSnapshot snapshot = UserSnapshot.from(
				new LoadResult(List.of(new User(1001, "먼저", 20, "M", 1_000)), List.of(), Instant.now()));

		assertThatThrownBy(() -> snapshot.usersSortedById().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> snapshot.usersById().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
