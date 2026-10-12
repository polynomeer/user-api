package com.whatap.userapi.service;

import java.util.List;

/**
 * 0부터 시작하는 페이지 응답. 범위를 넘는 page는 빈 content로 응답한다.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResult<T> of(List<T> all, int page, int size) {
		// page * size가 int 범위를 넘을 수 있으므로 long으로 계산한다
		long from = (long) page * size;
		List<T> content = from >= all.size()
				? List.of()
				: all.subList((int) from, (int) Math.min(from + size, all.size()));
		int totalPages = (int) Math.ceilDiv(all.size(), (long) size);
		return new PageResult<>(List.copyOf(content), page, size, all.size(), totalPages);
	}
}
