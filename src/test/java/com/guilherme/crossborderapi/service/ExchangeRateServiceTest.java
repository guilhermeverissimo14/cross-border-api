package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final int MAX_LOOKBACK_DAYS = 7;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private BcbExchangeRateClient bcbExchangeRateClient;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    void returnsCachedRateWithoutCallingBcbApi() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        ExchangeRate cached = ExchangeRate.builder()
                .quoteDate(date)
                .buyRate(new BigDecimal("5.1234"))
                .build();
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.of(cached));

        BigDecimal result = exchangeRateService.getBuyRate(date);

        assertThat(result).isEqualByComparingTo("5.1234");
        verifyNoInteractions(bcbExchangeRateClient);
    }

    @Test
    void fetchesFromBcbAndCachesWhenNotFoundLocally() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(date)).thenReturn(Optional.of(new BigDecimal("5.5000")));

        BigDecimal result = exchangeRateService.getBuyRate(date);

        assertThat(result).isEqualByComparingTo("5.5000");

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        assertThat(captor.getValue().getQuoteDate()).isEqualTo(date);
        assertThat(captor.getValue().getBuyRate()).isEqualByComparingTo("5.5000");
    }

    @Test
    void fallsBackToLastAvailableDateWhenWeekendHasNoQuote() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        LocalDate saturday = sunday.minusDays(1);
        LocalDate friday = sunday.minusDays(2);

        when(exchangeRateRepository.findByQuoteDate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenReturn(Optional.empty());

        when(exchangeRateRepository.findByQuoteDate(saturday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(saturday)).thenReturn(Optional.empty());

        when(exchangeRateRepository.findByQuoteDate(friday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(friday)).thenReturn(Optional.of(new BigDecimal("5.10")));

        BigDecimal result = exchangeRateService.getBuyRate(sunday);

        assertThat(result).isEqualByComparingTo("5.10");

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        // salva com a data em que a cotação realmente existe (sexta), não com a data pedida (domingo)
        assertThat(captor.getValue().getQuoteDate()).isEqualTo(friday);
    }

    @Test
    void usesCachedFallbackDateWithoutCallingBcbAgain() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        LocalDate saturday = sunday.minusDays(1);
        LocalDate friday = sunday.minusDays(2);

        when(exchangeRateRepository.findByQuoteDate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenReturn(Optional.empty());

        when(exchangeRateRepository.findByQuoteDate(saturday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(saturday)).thenReturn(Optional.empty());

        // sexta já estava em cache (de uma remessa anterior que também caiu no fallback)
        when(exchangeRateRepository.findByQuoteDate(friday)).thenReturn(
                Optional.of(ExchangeRate.builder().quoteDate(friday).buyRate(new BigDecimal("5.10")).build()));

        BigDecimal result = exchangeRateService.getBuyRate(sunday);

        assertThat(result).isEqualByComparingTo("5.10");
        verify(bcbExchangeRateClient, never()).fetchBuyRate(friday);
    }

    @Test
    void throwsWhenNoQuoteFoundWithinLookbackWindow() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        for (int i = 0; i <= MAX_LOOKBACK_DAYS; i++) {
            LocalDate candidate = date.minusDays(i);
            when(exchangeRateRepository.findByQuoteDate(candidate)).thenReturn(Optional.empty());
            when(bcbExchangeRateClient.fetchBuyRate(candidate)).thenReturn(Optional.empty());
        }

        assertThatThrownBy(() -> exchangeRateService.getBuyRate(date))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }
}
