package com.whatap.userapi.exception;

/**
 * 데이터 파일 자체를 읽을 수 없을 때(파일 없음, 권한, 인코딩 오류 등) 발생한다.
 * 개별 행의 파싱 실패는 이 예외가 아니라 ParseFailure로 기록된다.
 */
public class DataLoadException extends RuntimeException {

	public DataLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
