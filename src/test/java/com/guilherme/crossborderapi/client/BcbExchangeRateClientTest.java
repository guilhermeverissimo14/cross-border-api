package com.guilherme.crossborderapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BcbExchangeRateClientTest {

    private static final String EXPECTED_URL =
            "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"
                    + "CotacaoDolarDia(dataCotacao='09-17-2026')?$format=json";

    @Test
    void returnsBuyRateWhenApiRespondsSuccessfully() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BcbExchangeRateClient client = new BcbExchangeRateClient(builder);

        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(
                        "{\"value\":[{\"cotacaoCompra\":5.1234,\"cotacaoVenda\":5.1240,"
                                + "\"dataHoraCotacao\":\"2026-09-17 13:00:00\"}]}",
                        MediaType.APPLICATION_JSON));

        Optional<BigDecimal> result = client.fetchBuyRate(LocalDate.of(2026, 9, 17));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("5.1234");
    }

    @Test
    void returnsEmptyWhenApiHasNoQuoteForDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BcbExchangeRateClient client = new BcbExchangeRateClient(builder);

        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess("{\"value\":[]}", MediaType.APPLICATION_JSON));

        Optional<BigDecimal> result = client.fetchBuyRate(LocalDate.of(2026, 9, 17));

        assertThat(result).isEmpty();
    }

    @Test
    void throwsProviderUnavailableWhenTheBodyIsEmpty() {
        BcbExchangeRateClient client = clientRespondingWith("");

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class)
                .hasMessageContaining("fora do formato");
    }

    @Test
    void throwsProviderUnavailableWhenTheValueFieldIsMissing() {
        BcbExchangeRateClient client = clientRespondingWith("{}");

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);
    }

    @Test
    void throwsProviderUnavailableWhenTheQuoteHasNoBuyRate() {
        BcbExchangeRateClient client = clientRespondingWith("{\"value\":[{\"cotacaoVenda\":5.1240}]}");

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);
    }

    @Test
    void throwsProviderUnavailableWhenTheBodyIsNotJson() {
        BcbExchangeRateClient client = clientRespondingWith("<html>Bad Gateway</html>");

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);
    }

    private BcbExchangeRateClient clientRespondingWith(String jsonBody) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL)).andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
        return new BcbExchangeRateClient(builder);
    }

    @Test
    void throwsProviderUnavailableWhenApiReturnsServerError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BcbExchangeRateClient client = new BcbExchangeRateClient(builder);

        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class)
                .hasMessageContaining("2026-09-17");
    }

    @Test
    void throwsProviderUnavailableWhenConnectionFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BcbExchangeRateClient client = new BcbExchangeRateClient(builder);

        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.fetchBuyRate(LocalDate.of(2026, 9, 17)))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);
    }
}
