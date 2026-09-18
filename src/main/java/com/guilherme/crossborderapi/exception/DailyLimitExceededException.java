package com.guilherme.crossborderapi.exception;

public class DailyLimitExceededException extends RuntimeException {

    public DailyLimitExceededException(String message) {
        super(message);
    }
}
