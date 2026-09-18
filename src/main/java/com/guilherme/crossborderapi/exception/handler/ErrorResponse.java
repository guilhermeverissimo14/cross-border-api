package com.guilherme.crossborderapi.exception.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ErrorResponse(
        @Schema(description = "Momento em que o erro ocorreu")
        LocalDateTime timestamp,

        @Schema(description = "Código HTTP do erro", example = "404")
        int status,

        @Schema(description = "Nome do status HTTP", example = "Not Found")
        String error,

        @Schema(description = "Mensagem explicando o erro", example = "Usuário não encontrado: id=99")
        String message,

        @Schema(description = "Mapa campo → mensagem, preenchido apenas em erros de validação")
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, fieldErrors);
    }
}
