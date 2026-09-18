package com.guilherme.crossborderapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Cada teste usa datas próprias porque a cotação fica em cache no banco, que é compartilhado entre os testes.
class ExchangeRateEndpointTest extends ApiIntegrationTest {

    @Test
    void returnsTheQuoteOfTheRequestedDay() throws Exception {
        LocalDate day = LocalDate.of(2025, 3, 11);
        when(bcbClient.fetchBuyRate(day)).thenReturn(Optional.of(new BigDecimal("5.7123")));

        getJson("/exchange-rates/2025-03-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedDate").value("2025-03-11"))
                .andExpect(jsonPath("$.quoteDate").value("2025-03-11"))
                .andExpect(jsonPath("$.buyRate").value(5.7123));
    }

    @Test
    void secondCallIsServedFromTheCache() throws Exception {
        LocalDate day = LocalDate.of(2025, 4, 1);
        when(bcbClient.fetchBuyRate(day)).thenReturn(Optional.of(new BigDecimal("5.6000")));

        getJson("/exchange-rates/2025-04-01").andExpect(status().isOk());
        getJson("/exchange-rates/2025-04-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyRate").value(5.6));

        verify(bcbClient, times(1)).fetchBuyRate(day);
    }

    @Test
    void weekendFallsBackToTheLastBusinessDay() throws Exception {
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.empty());
        when(bcbClient.fetchBuyRate(LocalDate.of(2025, 5, 16))).thenReturn(Optional.of(new BigDecimal("5.6800")));

        getJson("/exchange-rates/2025-05-18")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedDate").value("2025-05-18"))
                .andExpect(jsonPath("$.quoteDate").value("2025-05-16"))
                .andExpect(jsonPath("$.buyRate").value(5.68));
    }

    @Test
    void noQuoteInTheLastSevenDaysReturns422() throws Exception {
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.empty());

        getJson("/exchange-rates/2025-06-20")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Nenhuma cotação encontrada nos últimos 7 dias a partir de 2025-06-20"));
    }

    @Test
    void futureDateReturns422WithoutCallingBcb() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        getJson("/exchange-rates/" + tomorrow)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Não existe cotação para datas futuras: " + tomorrow));

        verify(bcbClient, times(0)).fetchBuyRate(any());
    }

    @Test
    void invalidDateFormatReturns400() throws Exception {
        getJson("/exchange-rates/17-09-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'data'"));
        getJson("/exchange-rates/2026-02-30").andExpect(status().isBadRequest());
    }
}
