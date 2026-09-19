package com.guilherme.crossborderapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guilherme.crossborderapi.config.CacheConfig;
import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Cada teste usa datas próprias: o cache em memória é compartilhado entre os testes.
class ExchangeRateCacheTest extends ApiIntegrationTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private boolean isCached(LocalDate date) {
        return cacheManager.getCache(CacheConfig.EXCHANGE_RATES).get(date) != null;
    }

    @Test
    void quoteOfTheExactDayIsServedFromMemoryWithoutTouchingTheDatabaseOrBcb() throws Exception {
        LocalDate day = LocalDate.of(2025, 10, 1);
        when(bcbClient.fetchBuyRate(day)).thenReturn(Optional.of(new BigDecimal("5.3300")));
        getJson("/exchange-rates/2025-10-01").andExpect(status().isOk());
        assertThat(isCached(day)).isTrue();

        // Sem a linha no banco, só o cache em memória consegue responder
        exchangeRateRepository.deleteAll();
        getJson("/exchange-rates/2025-10-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyRate").value(5.33));

        verify(bcbClient, times(1)).fetchBuyRate(day);
    }

    @Test
    void fallbackQuoteIsNotKeptInMemory() throws Exception {
        LocalDate sunday = LocalDate.of(2025, 10, 5);
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.empty());
        when(bcbClient.fetchBuyRate(LocalDate.of(2025, 10, 3))).thenReturn(Optional.of(new BigDecimal("5.3000")));

        getJson("/exchange-rates/2025-10-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteDate").value("2025-10-03"));

        assertThat(isCached(sunday)).isFalse();
    }

    @Test
    void failuresAreNotCachedSoTheNextCallTriesTheBcbAgain() throws Exception {
        LocalDate day = LocalDate.of(2025, 10, 8);
        when(bcbClient.fetchBuyRate(day)).thenThrow(new ExchangeRateProviderUnavailableException("BCB fora do ar", null));
        getJson("/exchange-rates/2025-10-08").andExpect(status().isServiceUnavailable());
        assertThat(isCached(day)).isFalse();

        doReturn(Optional.of(new BigDecimal("5.2500"))).when(bcbClient).fetchBuyRate(day);
        getJson("/exchange-rates/2025-10-08")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyRate").value(5.25));
    }

    @Test
    void futureDatesAreNeverCached() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        getJson("/exchange-rates/" + tomorrow).andExpect(status().isUnprocessableContent());

        assertThat(isCached(tomorrow)).isFalse();
    }
}
