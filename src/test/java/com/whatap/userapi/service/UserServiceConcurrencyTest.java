package com.whatap.userapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.whatap.userapi.api.dto.PageResponse;
import com.whatap.userapi.config.UserDataProperties;
import com.whatap.userapi.domain.User;
import com.whatap.userapi.loader.UserFileLoader;
import com.whatap.userapi.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-3: 재적재 중에도 조회는 부분 갱신된 상태를 보지 않는다.
 */
class UserServiceConcurrencyTest {

	private static final int READERS = 4;
	private static final int RELOADS = 200;

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("재적재가 반복되는 동안 조회 결과는 항상 두 파일 중 하나의 완전한 상태다")
	void readersNeverSeePartialState() throws Exception {
		Path file = tempDir.resolve("user.txt");
		String small = lines(1001, 3);   // 3명
		String large = lines(2001, 50);  // 50명
		Files.writeString(file, small);

		UserService service = new UserService(new UserFileLoader(), new UserRepository(),
				new UserDataProperties(file.toString()));
		service.reload();

		AtomicBoolean running = new AtomicBoolean(true);
		Set<String> violations = ConcurrentHashMap.newKeySet();
		Set<Long> observedTotals = ConcurrentHashMap.newKeySet();
		CountDownLatch readersStarted = new CountDownLatch(READERS);
		ExecutorService executor = Executors.newFixedThreadPool(READERS);
		try {
			List<Future<?>> readers = new ArrayList<>();
			for (int i = 0; i < READERS; i++) {
				readers.add(executor.submit(() -> {
					readersStarted.countDown();
					while (running.get()) {
						PageResponse<User> page = service.findAll(0, 100);
						observedTotals.add(page.totalElements());
						checkConsistent(page, violations);
					}
				}));
			}
			readersStarted.await();

			for (int i = 0; i < RELOADS; i++) {
				Files.writeString(file, i % 2 == 0 ? large : small);
				service.reload();
			}
			running.set(false);
			for (Future<?> reader : readers) {
				reader.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(violations).isEmpty();
		// 두 상태가 모두 관측돼야 조회가 실제로 재적재와 겹쳐 실행됐다고 볼 수 있다
		assertThat(observedTotals).containsExactlyInAnyOrder(3L, 50L);
	}

	/**
	 * 한 응답 안에서 total과 content가 같은 스냅샷에서 나왔는지, ID가 한 파일에서만 왔는지 확인한다.
	 */
	private static void checkConsistent(PageResponse<User> page, Set<String> violations) {
		long total = page.totalElements();
		if (page.content().size() != total) {
			violations.add("total=%d, content=%d".formatted(total, page.content().size()));
		}
		Set<Integer> prefixes = page.content().stream().map(u -> u.id() / 1000).collect(Collectors.toSet());
		if (prefixes.size() > 1) {
			violations.add("두 파일의 데이터가 섞임: " + prefixes);
		}
	}

	private static String lines(int startId, int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> "%d , 사용자%d , 30 , M , 1_000_000".formatted(startId + i, i))
				.collect(Collectors.joining("\n"));
	}
}
