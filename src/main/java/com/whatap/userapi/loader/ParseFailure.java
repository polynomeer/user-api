package com.whatap.userapi.loader;

public record ParseFailure(int lineNumber, String rawLine, ParseErrorCode errorCode, String message) {
}
