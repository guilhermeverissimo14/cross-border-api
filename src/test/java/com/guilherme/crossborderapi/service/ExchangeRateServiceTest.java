package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateQuote;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final int MAX_LOOKBACK_DAYS = 7;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private BcbExchangeRateClient bcbExchangeRateClient;

    // Sexta-feira 18/09/2026, 15:00 em Brasília
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T18:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService(exchangeRateRepository, bcbExchangeRateClient, CLOCK);
    }

    @Test
    void returnsCachedRateWithoutCallingBcbApi() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        ExchangeRate cached = ExchangeRate.builder()
                .quoteDate(date)
                .buyRate(new BigDecimal("5.1234"))
                .build();
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.of(cached));

        BigDecimal result = exchangeRateService.getQuote(date).buyRate();

        assertThat(result).isEqualByComparingTo("5.1234");
        verifyNoInteractions(bcbExchangeRateClient);
    }

    @Test
    void fetchesFromBcbAndCachesWhenNotFoundLocally() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(date)).thenReturn(Optional.of(new BigDecimal("5.5000")));

        BigDecimal result = exchangeRateService.getQuote(date).buyRate();

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

        BigDecimal result = exchangeRateService.getQuote(sunday).buyRate();

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

        BigDecimal result = exchangeRateService.getQuote(sunday).buyRate();

        assertThat(result).isEqualByComparingTo("5.10");
        verify(bcbExchangeRateClient, never()).fetchBuyRate(friday);
    }

    @Test
    void getQuoteReportsTheRequestedDateWhenItHasAQuote() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.of(
                ExchangeRate.builder().quoteDate(date).buyRate(new BigDecimal("5.1515")).build()));

        ExchangeRateQuote quote = exchangeRateService.getQuote(date);

        assertThat(quote.quoteDate()).isEqualTo(date);
        assertThat(quote.buyRate()).isEqualByComparingTo("5.1515");
    }

    @Test
    void getQuoteReportsTheFallbackDateWhenRequestedDayHasNoQuote() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        LocalDate saturday = sunday.minusDays(1);
        LocalDate friday = sunday.minusDays(2);

        when(exchangeRateRepository.findByQuoteDate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByQuoteDate(saturday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(saturday)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByQuoteDate(friday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(friday)).thenReturn(Optional.of(new BigDecimal("5.10")));

        ExchangeRateQuote quote = exchangeRateService.getQuote(sunday);

        assertThat(quote.quoteDate()).isEqualTo(friday); // a data em que a cotação existe, não a pedida
        assertThat(quote.buyRate()).isEqualByComparingTo("5.10");
    }

    @Test
    void rejectsFutureDatesWithoutTouchingTheCacheOrBcb() {
        LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> exchangeRateService.getQuote(tomorrow))
                .isInstanceOf(ExchangeRateUnavailableException.class);
        verifyNoInteractions(exchangeRateRepository, bcbExchangeRateClient);
    }

    @Test
    void todayIsNotConsideredAFutureDate() {
        when(exchangeRateRepository.findByQuoteDate(TODAY)).thenReturn(Optional.of(
                ExchangeRate.builder().quoteDate(TODAY).buyRate(new BigDecimal("5.20")).build()));

        assertThat(exchangeRateService.getQuote(TODAY).buyRate()).isEqualByComparingTo("5.20");
    }

    @Test
    void whatCountsAsFutureDependsOnTheClockNotOnTheRealDate() {
        Clock lateSaturday = Clock.fixed(Instant.parse("2026-09-20T02:59:00Z"), ZoneId.of("America/Sao_Paulo"));
        ExchangeRateService service = new ExchangeRateService(exchangeRateRepository, bcbExchangeRateClient, lateSaturday);
        // 23:59 de sábado 19/09 em Brasília: domingo 20 ainda é futuro, mesmo já sendo dia 20 em UTC

        assertThatThrownBy(() -> service.getQuote(LocalDate.of(2026, 9, 20)))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void throwsWhenNoQuoteFoundWithinLookbackWindow() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        for (int i = 0; i <= MAX_LOOKBACK_DAYS; i++) {
            LocalDate candidate = date.minusDays(i);
            when(exchangeRateRepository.findByQuoteDate(candidate)).thenReturn(Optional.empty());
            when(bcbExchangeRateClient.fetchBuyRate(candidate)).thenReturn(Optional.empty());
        }

        assertThatThrownBy(() -> exchangeRateService.getQuote(date).buyRate())
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    // --- BCB fora do ar ---

    private static final ExchangeRateProviderUnavailableException BCB_DOWN =
            new ExchangeRateProviderUnavailableException("BCB fora do ar", null);

    @Test
    void whenBcbIsDownItFailsRightAwayWithoutWalkingBackDays() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(bcbExchangeRateClient.fetchBuyRate(date)).thenThrow(BCB_DOWN);

        assertThatThrownBy(() -> exchangeRateService.getQuote(date)).isSameAs(BCB_DOWN);

        // uma única chamada ao BCB: a queda não é tratada como "dia sem cotação"
        verify(bcbExchangeRateClient, times(1)).fetchBuyRate(any());
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void whenBcbIsDownItDoesNotFallBackEvenIfAnOlderDayIsCached() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        when(exchangeRateRepository.findByQuoteDate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenThrow(BCB_DOWN);

        assertThatThrownBy(() -> exchangeRateService.getQuote(sunday))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);

        verify(exchangeRateRepository, never()).findByQuoteDate(sunday.minusDays(2));
    }

    @Test
    void bcbGoingDownMidWalkBackFailsInsteadOfUsingOlderQuotes() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        LocalDate saturday = sunday.minusDays(1);
        when(bcbExchangeRateClient.fetchBuyRate(sunday)).thenReturn(Optional.empty());
        when(bcbExchangeRateClient.fetchBuyRate(saturday)).thenThrow(BCB_DOWN);

        assertThatThrownBy(() -> exchangeRateService.getQuote(sunday))
                .isInstanceOf(ExchangeRateProviderUnavailableException.class);

        verify(exchangeRateRepository, never()).findByQuoteDate(sunday.minusDays(2));
    }

    @Test
    void aQuoteAlreadyCachedForTheExactDayIsStillServedWhileBcbIsDown() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(exchangeRateRepository.findByQuoteDate(date)).thenReturn(Optional.of(
                ExchangeRate.builder().quoteDate(date).buyRate(new BigDecimal("5.20")).build()));

        assertThat(exchangeRateService.getQuote(date).buyRate()).isEqualByComparingTo("5.20");

        verifyNoInteractions(bcbExchangeRateClient);
    }
}
