package com.whatap.userapi.loader;

import com.whatap.userapi.domain.User;
import com.whatap.userapi.exception.DataLoadException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * user.txt를 줄 단위로 읽어 {@link LoadResult}로 만든다.
 * 행 단위 오류는 기록 후 계속 진행하고, 파일 자체를 읽을 수 없으면 {@link DataLoadException}을 던진다.
 */
@Component
public class UserFileLoader {

	private static final Logger log = LoggerFactory.getLogger(UserFileLoader.class);

	// 과제 문서의 데이터 설명에 있는 헤더("ID , 이름 , ...")와 구분선("=====").
	// 헤더는 첫 데이터 행 이전에만 인정한다. 이후의 "id ,"로 시작하는 행은 ID가 깨진 데이터로 보고 실패 처리한다
	private static final Pattern HEADER = Pattern.compile("^\\uFEFF?\\s*ID\\s*,.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern SEPARATOR = Pattern.compile("^\\uFEFF?\\s*=+\\s*$");

	private final UserLineParser parser = new UserLineParser();

	public LoadResult load(Path path) {
		List<User> users = new ArrayList<>();
		List<ParseFailure> failures = new ArrayList<>();
		Set<Integer> seenIds = new HashSet<>();

		// readLine은 \n, \r\n 모두를 줄 끝으로 처리한다. 잘못된 UTF-8 바이트는 MalformedInputException(IOException)
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			int lineNumber = 0;
			boolean dataStarted = false;
			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (isSkippable(line, dataStarted)) {
					continue;
				}
				dataStarted = true;
				switch (parser.parse(lineNumber, line)) {
					case ParseResult.Success(User user) -> {
						if (seenIds.add(user.id())) {
							users.add(user);
						} else {
							failures.add(new ParseFailure(lineNumber, line, ParseErrorCode.DUPLICATE_ID,
									"이미 적재된 ID입니다: " + user.id()));
						}
					}
					case ParseResult.Failure(ParseFailure failure) -> failures.add(failure);
				}
			}
		} catch (IOException e) {
			throw new DataLoadException("사용자 데이터 파일을 읽을 수 없습니다: " + path.toAbsolutePath().normalize(), e);
		}

		failures.forEach(f -> log.warn("user.txt 파싱 실패 - line={}, code={}, reason={}, raw='{}'",
				f.lineNumber(), f.errorCode(), f.message(), f.rawLine()));
		log.info("user.txt 적재 완료 - path={}, success={}, failure={}",
				path.toAbsolutePath().normalize(), users.size(), failures.size());
		return new LoadResult(users, failures, Instant.now());
	}

	private static boolean isSkippable(String line, boolean dataStarted) {
		if (line.isBlank() || SEPARATOR.matcher(line).matches()) {
			return true;
		}
		return !dataStarted && HEADER.matcher(line).matches();
	}
}
