package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void throwsWhenBcbHasNoQuoteForDate() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        when(exchangeRateRepository.findByQuoteDate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeRateService.getBuyRate(sunday))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }
}
