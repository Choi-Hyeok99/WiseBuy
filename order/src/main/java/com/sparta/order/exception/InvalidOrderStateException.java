package com.sparta.order.exception;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message){
        super(message);
    }
}
