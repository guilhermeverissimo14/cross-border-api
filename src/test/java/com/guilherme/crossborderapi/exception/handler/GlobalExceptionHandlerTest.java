package com.guilherme.crossborderapi.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.exception.IdempotencyKeyInUseException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNonNumericPathVariableTo400InsteadOf500() {
        var ex = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Valor inválido para o parâmetro 'id'");
        assertThat(response.getBody().message()).doesNotContain("abc");
    }

    @Test
    void mapsUnknownRouteTo404InsteadOf500() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/segredo-do-cliente", "segredo-do-cliente");

        ResponseEntity<ErrorResponse> response = handler.handleFrameworkClientError(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Rota não encontrada");
        assertThat(response.getBody().message()).doesNotContain("segredo-do-cliente");
    }

    @Test
    void mapsUnsupportedHttpMethodTo405InsteadOf500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleFrameworkClientError(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().message()).isEqualTo("Método HTTP não permitido para esta rota");
    }

    @Test
    void mapsUnsupportedContentTypeTo415InsteadOf500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleFrameworkClientError(new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().message()).contains("application/json");
    }

    @Test
    void mapsNotAcceptableTo406InsteadOf500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleFrameworkClientError(new HttpMediaTypeNotAcceptableException("nada aceitável"));

        assertThat(response.getStatusCode().value()).isEqualTo(406);
    }

    @Test
    void mapsConcurrentIdempotencyKeyTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIdempotencyKeyInUse(new IdempotencyKeyInUseException("em processamento"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("em processamento");
    }

    @Test
    void mapsOptimisticLockFailureTo409() {
        var ex = new ObjectOptimisticLockingFailureException(Wallet.class, 1L);

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailure(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("Tente novamente");
    }

    @Test
    void mapsUnexpectedErrorTo500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new IllegalStateException("senha do banco: 123"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Erro interno no servidor");
    }
}
