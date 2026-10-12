package com.whatap.userapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whatap.userapi.config.UserDataProperties;
import com.whatap.userapi.exception.DataLoadException;
import com.whatap.userapi.loader.UserFileLoader;
import com.whatap.userapi.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserServiceReloadTest {

	@TempDir
	Path tempDir;

	private Path file;
	private UserRepository repository;
	private UserService service;

	@BeforeEach
	void setUp() {
		file = tempDir.resolve("user.txt");
		repository = new UserRepository();
		service = new UserService(new UserFileLoader(), repository, new UserDataProperties(file.toString()));
	}

	@Test
	@DisplayName("재적재하면 파일의 현재 내용으로 스냅샷이 교체된다")
	void reloadReplacesSnapshot() throws IOException {
		Files.writeString(file, "1001 , 김다나 , 23 , M , 30_000_000\n");
		service.reload();

		Files.writeString(file, "1002 , 김나나 , 24 , F , 31_000_000\n1003 , 오류 , 25 , X , 1_000\n");
		service.reload();

		assertThat(repository.current().findById(1001)).isEmpty();
		assertThat(repository.current().findById(1002)).isPresent();
		assertThat(repository.current().failures()).hasSize(1);
	}

	@Test
	@DisplayName("재적재가 실패하면 예외를 던지고 기존 스냅샷을 유지한다")
	void reloadFailureKeepsPreviousSnapshot() throws IOException {
		Files.writeString(file, "1001 , 김다나 , 23 , M , 30_000_000\n");
		service.reload();
		Files.delete(file);

		assertThatThrownBy(service::reload).isInstanceOf(DataLoadException.class);
		assertThat(repository.current().findById(1001)).isPresent();
	}
}
