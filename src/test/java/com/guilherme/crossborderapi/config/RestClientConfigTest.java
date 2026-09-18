package com.guilherme.crossborderapi.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    // O ServerSocket completa o handshake TCP (a conexão entra no backlog do SO) mas nunca chama
    // accept() nem escreve nada — simula uma API que "pendura" sem devolver erro.
    @Test
    void failsFastInsteadOfHangingWhenServerNeverResponds() throws Exception {
        try (ServerSocket silentServer = new ServerSocket(0)) {
            RestClient client = RestClientConfig
                    .buildRestClientBuilder(Duration.ofSeconds(1), Duration.ofMillis(500))
                    .build();
            String url = "http://localhost:" + silentServer.getLocalPort() + "/";

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThatThrownBy(() -> client.get().uri(url).retrieve().body(String.class))
                            .isInstanceOf(ResourceAccessException.class));
        }
    }
}
