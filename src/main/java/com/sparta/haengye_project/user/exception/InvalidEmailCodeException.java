package com.sparta.haengye_project.user.exception;

public class InvalidEmailCodeException extends RuntimeException {
    public InvalidEmailCodeException(String message) {
        super(message);
    }
}