package com.whatap.userapi.service;

import com.whatap.userapi.api.dto.PageResponse;
import com.whatap.userapi.config.UserDataProperties;
import com.whatap.userapi.domain.User;
import com.whatap.userapi.exception.DataLoadException;
import com.whatap.userapi.exception.UserNotFoundException;
import com.whatap.userapi.loader.LoadResult;
import com.whatap.userapi.loader.UserFileLoader;
import com.whatap.userapi.repository.UserRepository;
import com.whatap.userapi.repository.UserSnapshot;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserFileLoader loader;
	private final UserRepository repository;
	private final Path dataFile;

	public UserService(UserFileLoader loader, UserRepository repository, UserDataProperties properties) {
		this.loader = loader;
		this.repository = repository;
		this.dataFile = Path.of(properties.filePath());
	}

	// 각 메서드는 스냅샷을 한 번만 읽어 그 안에서 처리한다 (재적재와 겹쳐도 total과 content가 어긋나지 않도록)

	public PageResponse<User> findAll(int page, int size) {
		return PageResponse.of(repository.current().usersSortedById(), page, size);
	}

	public User findById(int id) {
		return repository.current().findById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
	}

	public PageResponse<User> searchByName(String keyword, int page, int size) {
		String trimmed = keyword.strip();
		List<User> matched = repository.current().usersSortedById().stream()
				.filter(user -> user.name().contains(trimmed))
				.toList();
		return PageResponse.of(matched, page, size);
	}

	public UserSnapshot currentSnapshot() {
		return repository.current();
	}

	/**
	 * 데이터 파일을 다시 읽어 메모리 데이터를 교체한다.
	 * 동시 호출은 직렬화하며, 파일을 읽지 못하면 기존 데이터를 그대로 두고 예외를 던진다.
	 *
	 * @throws DataLoadException 파일을 읽을 수 없는 경우
	 */
	public synchronized UserSnapshot reload() {
		LoadResult result = loader.load(dataFile);
		UserSnapshot snapshot = UserSnapshot.from(result);
		repository.replace(snapshot);
		return snapshot;
	}
}
