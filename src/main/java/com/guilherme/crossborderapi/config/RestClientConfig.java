package com.guilherme.crossborderapi.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Configuração do RestClient para injeção de dependência.
// Sem timeout, uma API externa que "pendura" (sem responder nem dar erro) travaria a requisição
@Configuration
public class RestClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient.Builder restClientBuilder() {
        return buildRestClientBuilder(CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    static RestClient.Builder buildRestClientBuilder(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
