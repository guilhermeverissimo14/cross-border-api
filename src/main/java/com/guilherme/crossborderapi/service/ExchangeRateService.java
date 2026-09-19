package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.config.CacheConfig;
import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateQuote;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    // Cobre fim de semana/feriado prolongado (ex: virada de ano) sem correr pra trás pra sempre.
    private static final int MAX_LOOKBACK_DAYS = 7;

    private final ExchangeRateRepository exchangeRateRepository;
    private final BcbExchangeRateClient bcbExchangeRateClient;
    private final Clock clock;

    // Tenta a data pedida; se o BCB responder que não há cotação (fim de semana, feriado), tenta o dia anterior, e assim por diante.
    // Se o BCB não responder, o cliente lança ExchangeRateProviderUnavailableException e a consulta falha (503): não se volta dias às cegas.
    // Devolve também a data em que a cotação realmente existe, pra quem consulta enxergar o fallback.
    // Só cacheia a cotação do dia exato: um fallback (ex: hoje antes das 13h devolve a de ontem) ficaria desatualizado depois da publicação. Falhas também não são cacheadas.
    @Cacheable(cacheNames = CacheConfig.EXCHANGE_RATES, key = "#date", unless = "!#result.quoteDate().equals(#date)")
    @Transactional
    public ExchangeRateQuote getQuote(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ExchangeRateUnavailableException("Não existe cotação para datas futuras: " + date);
        }

        LocalDate candidateDate = date;

        for (int i = 0; i <= MAX_LOOKBACK_DAYS; i++) {
            Optional<BigDecimal> cached = exchangeRateRepository.findByQuoteDate(candidateDate) //verifica tem salva no banco na data atual
                    .map(ExchangeRate::getBuyRate);
            if (cached.isPresent()) {
                log.debug("Cotação de {} encontrada em cache: {}", candidateDate, cached.get());
                logIfFallback(date, candidateDate);
                return new ExchangeRateQuote(candidateDate, cached.get());
            }

            //verifica na api e se encontrar salva no banco
            Optional<BigDecimal> fetched = bcbExchangeRateClient.fetchBuyRate(candidateDate);
            if (fetched.isPresent()) {
                cacheQuote(candidateDate, fetched.get());
                log.info("Cotação de {} obtida do BCB e salva em cache: {}", candidateDate, fetched.get());
                logIfFallback(date, candidateDate);
                return new ExchangeRateQuote(candidateDate, fetched.get());
            }

            candidateDate = candidateDate.minusDays(1); // se chegou aqui tenta um dia anterior
        }

        throw new ExchangeRateUnavailableException(
                "Nenhuma cotação encontrada nos últimos " + MAX_LOOKBACK_DAYS + " dias a partir de " + date);
    }

    private void logIfFallback(LocalDate requestedDate, LocalDate usedDate) {
        if (!requestedDate.equals(usedDate)) {
            log.warn("Sem cotação para {}; usando a cotação de {}", requestedDate, usedDate);
        }
    }

    private void cacheQuote(LocalDate date, BigDecimal buyRate) {
        ExchangeRate exchangeRate = ExchangeRate.builder()
                .quoteDate(date)
                .buyRate(buyRate)
                .build();
        exchangeRateRepository.save(exchangeRate);
    }
}
