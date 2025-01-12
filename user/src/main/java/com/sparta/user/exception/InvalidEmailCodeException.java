package com.sparta.user.exception;

public class InvalidEmailCodeException extends RuntimeException {
    public InvalidEmailCodeException(String message) {
        super(message);
    }
}