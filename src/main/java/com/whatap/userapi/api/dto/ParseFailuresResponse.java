package com.whatap.userapi.api.dto;

import com.whatap.userapi.loader.ParseFailure;
import com.whatap.userapi.repository.UserSnapshot;
import java.time.Instant;
import java.util.List;

/**
 * @param loadedAt 이 실패 목록을 만든 적재 시각. 한 번도 적재되지 않았으면 null
 */
public record ParseFailuresResponse(Instant loadedAt, int count, List<ParseFailure> failures) {

	public static ParseFailuresResponse from(UserSnapshot snapshot) {
		return new ParseFailuresResponse(snapshot.loadedAt(), snapshot.failures().size(), snapshot.failures());
	}
}
