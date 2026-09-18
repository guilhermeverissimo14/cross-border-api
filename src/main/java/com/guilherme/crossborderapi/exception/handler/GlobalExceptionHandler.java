package com.guilherme.crossborderapi.exception.handler;

import com.guilherme.crossborderapi.exception.DailyLimitExceededException;
import com.guilherme.crossborderapi.exception.DuplicateResourceException;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.exception.InsufficientBalanceException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.exception.SelfRemittanceException;
import tools.jackson.databind.exc.InvalidFormatException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Regras de negócio que a requisição, mesmo bem formada, não consegue atender no estado atual.
    @ExceptionHandler({
            ExchangeRateUnavailableException.class,
            InsufficientBalanceException.class,
            SelfRemittanceException.class,
            DailyLimitExceededException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    // Duas transações concorrentes tentaram atualizar a mesma linha(Wallet) ao mesmo tempo.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT,
                "Esse recurso foi modificado por outra operação concorrente. Tente novamente.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Erro de desserialização do JSON (ex: valor de enum inválido) acontece antes do @Valid rodar,
    // então cai aqui e não em MethodArgumentNotValidException.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Corpo da requisição inválido";
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof InvalidFormatException)) {
            cause = cause.getCause();
        }
        if (cause instanceof InvalidFormatException invalidFormatException && !invalidFormatException.getPath().isEmpty()) {
            String field = invalidFormatException.getPath().get(0).getPropertyName();
            message = "Valor inválido para o campo '" + field + "': " + invalidFormatException.getValue();
        }
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity.badRequest().body(body);
    }

    //Erro de validação do @Valid, caso algum campo obrigatório esteja ausente ou mal formado.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST, "Erro de validação", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
