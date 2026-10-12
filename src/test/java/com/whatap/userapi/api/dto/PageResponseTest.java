package com.whatap.userapi.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResponseTest {

	@ParameterizedTest(name = "total={0}, page={1}, size={2} → content={3}, totalPages={4}")
	@CsvSource({
			"0,  0, 10,  0, 0",   // 데이터 없음
			"10, 0, 10, 10, 1",   // 정확히 한 페이지
			"10, 1, 10,  0, 1",   // 나누어떨어질 때 다음 페이지는 비어 있음
			"11, 1, 10,  1, 2",   // 마지막 페이지 1건
			"5,  0, 100, 5, 1",   // size가 전체보다 큼
			"5,  4, 1,   1, 5",   // 마지막 인덱스
			"5,  5, 1,   0, 5"    // 범위 밖
	})
	void paginates(int total, int page, int size, int expectedContent, int expectedTotalPages) {
		List<Integer> all = IntStream.range(0, total).boxed().toList();

		PageResponse<Integer> response = PageResponse.of(all, page, size);

		assertThat(response.content()).hasSize(expectedContent);
		assertThat(response.totalElements()).isEqualTo(total);
		assertThat(response.totalPages()).isEqualTo(expectedTotalPages);
		if (expectedContent > 0) {
			assertThat(response.content().getFirst()).isEqualTo(page * size);
		}
	}
}
