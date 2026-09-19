package com.guilherme.crossborderapi.exception.handler;

import com.guilherme.crossborderapi.exception.AmountTooSmallException;
import com.guilherme.crossborderapi.exception.DailyLimitExceededException;
import com.guilherme.crossborderapi.exception.DuplicateResourceException;
import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.exception.IdempotencyKeyInUseException;
import com.guilherme.crossborderapi.exception.IdempotencyKeyReuseException;
import com.guilherme.crossborderapi.exception.InsufficientBalanceException;
import com.guilherme.crossborderapi.exception.InvalidCredentialsException;
import com.guilherme.crossborderapi.exception.InvalidIdempotencyKeyException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.exception.SelfRemittanceException;
import tools.jackson.databind.exc.InvalidFormatException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Recurso duplicado: e-mail ou documento já cadastrado");
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Regras de negócio que a requisição, mesmo bem formada, não consegue atender no estado atual.
    @ExceptionHandler({
            AmountTooSmallException.class,
            ExchangeRateUnavailableException.class,
            InsufficientBalanceException.class,
            SelfRemittanceException.class,
            DailyLimitExceededException.class,
            IdempotencyKeyReuseException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException ex) {
        log.warn("Regra de negócio violada: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    // O BCB não respondeu: o cliente pode tentar de novo. Não é falha de regra de negócio (422) nem bug (500).
    @ExceptionHandler(ExchangeRateProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(ExchangeRateProviderUnavailableException ex) {
        log.warn("Fonte de cotação indisponível: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE,
                "Cotação do dólar temporariamente indisponível. Tente novamente em instantes.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Tentativa de login com credenciais inválidas");
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // Outra requisição com a mesma Idempotency-Key gravou primeiro: o cliente repete e recebe o resultado guardado.
    @ExceptionHandler(IdempotencyKeyInUseException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyInUse(IdempotencyKeyInUseException ex) {
        log.warn("Requisição concorrente com a mesma Idempotency-Key");
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex) {
        // A chave inválida não vai pro log de propósito: é texto livre do cliente.
        log.warn("Idempotency-Key com formato inválido");
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    // Header obrigatório ausente (hoje só o Idempotency-Key de POST /remittances).
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Header obrigatório ausente: {}", ex.getHeaderName());
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST,
                "Header obrigatório ausente: " + ex.getHeaderName());
        return ResponseEntity.badRequest().body(body);
    }

    // Duas transações concorrentes tentaram atualizar a mesma linha(Wallet) ao mesmo tempo.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência (lock otimista): {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(HttpStatus.CONFLICT,
                "Esse recurso foi modificado por outra operação concorrente. Tente novamente.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Erro de desserialização do JSON (ex: valor de enum inválido) acontece antes do @Valid rodar,
    // então cai aqui e não em MethodArgumentNotValidException.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Corpo da requisição inválido ou ilegível");
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
        log.warn("Erro de validação nos campos: {}", fieldErrors.keySet());
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST, "Erro de validação", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // Ex: GET /remittances/abc o {id} da URL não é um número. O valor enviado fica fora do log e da resposta.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parâmetro '{}' com tipo inválido", ex.getName());
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST,
                "Valor inválido para o parâmetro '" + ex.getName() + "'");
        return ResponseEntity.badRequest().body(body);
    }

    // Erros de cliente que o próprio Spring MVC reconhece: rota inexistente, método HTTP ou Content-Type não suportado. 
    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class
    })
    public ResponseEntity<ErrorResponse> handleFrameworkClientError(Exception ex) {
        HttpStatus status = ex instanceof org.springframework.web.ErrorResponse springError
                ? HttpStatus.valueOf(springError.getStatusCode().value())
                : HttpStatus.BAD_REQUEST;
        String message = switch (status) {
            case NOT_FOUND -> "Rota não encontrada";
            case METHOD_NOT_ALLOWED -> "Método HTTP não permitido para esta rota";
            case UNSUPPORTED_MEDIA_TYPE -> "Content-Type não suportado; use application/json";
            case NOT_ACCEPTABLE -> "Formato de resposta não disponível; use application/json";
            default -> status.getReasonPhrase();
        };
        log.warn("Requisição recusada pelo Spring MVC: {} ({})", status.value(), ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        ErrorResponse body = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
