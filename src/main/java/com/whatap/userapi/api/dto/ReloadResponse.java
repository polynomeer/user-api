package com.whatap.userapi.api.dto;

import com.whatap.userapi.repository.UserSnapshot;
import java.time.Instant;

public record ReloadResponse(int successCount, int failureCount, Instant loadedAt) {

	public static ReloadResponse from(UserSnapshot snapshot) {
		return new ReloadResponse(snapshot.usersSortedById().size(), snapshot.failures().size(), snapshot.loadedAt());
	}
}
