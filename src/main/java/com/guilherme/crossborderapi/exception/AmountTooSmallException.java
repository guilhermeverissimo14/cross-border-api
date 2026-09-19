package com.guilherme.crossborderapi.exception;

public class AmountTooSmallException extends RuntimeException {

    public AmountTooSmallException(String message) {
        super(message);
    }
}
