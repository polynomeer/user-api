package com.whatap.userapi.startup;

import com.whatap.userapi.exception.DataLoadException;
import com.whatap.userapi.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 user.txt를 한 번 적재한다.
 * 파일을 읽지 못해도 애플리케이션은 빈 데이터로 기동하고, 재적재 API로 복구할 수 있게 한다.
 */
@Component
public class UserDataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UserDataInitializer.class);

	private final UserService userService;

	public UserDataInitializer(UserService userService) {
		this.userService = userService;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			userService.reload();
		} catch (DataLoadException e) {
			log.error("기동 시 사용자 데이터 적재 실패 - 빈 데이터로 시작합니다. 재적재 API로 복구할 수 있습니다.", e);
		}
	}
}
