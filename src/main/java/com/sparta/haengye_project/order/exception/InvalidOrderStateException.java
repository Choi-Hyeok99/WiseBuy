package com.sparta.haengye_project.order.exception;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message){
        super(message);
    }
}
