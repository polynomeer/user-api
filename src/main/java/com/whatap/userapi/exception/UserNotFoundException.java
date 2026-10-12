package com.whatap.userapi.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(int id) {
		super("ID %d에 해당하는 사용자가 없습니다.".formatted(id));
	}
}
