package com.guilherme.crossborderapi.exception;

public class IdempotencyKeyInUseException extends RuntimeException {

    public IdempotencyKeyInUseException(String message) {
        super(message);
    }
}
