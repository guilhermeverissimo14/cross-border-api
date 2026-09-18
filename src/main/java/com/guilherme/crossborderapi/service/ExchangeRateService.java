package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateQuote;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    // Cobre fim de semana/feriado prolongado (ex: virada de ano) sem correr pra trás pra sempre caso a API do BCB fique fora do ar por muito tempo.
    private static final int MAX_LOOKBACK_DAYS = 7;

    private final ExchangeRateRepository exchangeRateRepository;
    private final BcbExchangeRateClient bcbExchangeRateClient;

    @Transactional
    public BigDecimal getBuyRate(LocalDate date) {
        return getQuote(date).buyRate();
    }

    // Tenta a data pedida; se não tiver cotação (fim de semana, feriado ou API fora do ar os dois casos chegam aqui como Optional vazio), tenta o dia anterior, e assim por diante.
    // Devolve também a data em que a cotação realmente existe, pra quem consulta enxergar o fallback.
    @Transactional
    public ExchangeRateQuote getQuote(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
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
