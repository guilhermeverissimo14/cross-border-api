package com.guilherme.crossborderapi.exception;

// A fonte da cotação (API do BCB) não respondeu: erro, timeout ou 5xx. Diferente de "respondeu sem cotação".
public class ExchangeRateProviderUnavailableException extends RuntimeException {

    public ExchangeRateProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
