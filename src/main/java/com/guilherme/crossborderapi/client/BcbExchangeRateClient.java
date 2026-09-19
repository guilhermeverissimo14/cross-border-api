package com.guilherme.crossborderapi.client;

import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class BcbExchangeRateClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final String BASE_URL = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata";

    private final RestClient restClient;

    public BcbExchangeRateClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    // Optional vazio = o BCB respondeu que não há cotação no dia. Falha de comunicação lança exceção.
    public Optional<BigDecimal> fetchBuyRate(LocalDate date) {
        try {
            BcbQuoteResponse response = restClient.get()
                    .uri("/CotacaoDolarDia(dataCotacao='{date}')?$format=json", date.format(DATE_FORMAT))
                    .retrieve()
                    .body(BcbQuoteResponse.class);

            if (response == null || response.value().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.value().get(0).cotacaoCompra());
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a API do BCB para a data {}: {}", date, e.getMessage());
            throw new ExchangeRateProviderUnavailableException("API do BCB indisponível para a data " + date, e);
        }
    }
}