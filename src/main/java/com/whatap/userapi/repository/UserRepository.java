package com.whatap.userapi.repository;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Repository;

/**
 * 현재 사용자 데이터를 메모리에 보관한다. 스냅샷 전체를 원자적으로 교체하므로 조회 쪽에는 락이 필요 없다.
 */
@Repository
public class UserRepository {

	private final AtomicReference<UserSnapshot> snapshot = new AtomicReference<>(UserSnapshot.empty());

	public UserSnapshot current() {
		return snapshot.get();
	}

	public void replace(UserSnapshot newSnapshot) {
		snapshot.set(newSnapshot);
	}
}
