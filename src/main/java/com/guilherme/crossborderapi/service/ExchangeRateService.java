package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    // Cobre fim de semana/feriado prolongado (ex: virada de ano) sem correr pra trás pra sempre caso a API do BCB fique fora do ar por muito tempo.
    private static final int MAX_LOOKBACK_DAYS = 7;

    private final ExchangeRateRepository exchangeRateRepository;
    private final BcbExchangeRateClient bcbExchangeRateClient;

    // Tenta a data pedida; se não tiver cotação (fim de semana, feriado ou API fora do ar os dois
    // casos chegam aqui como Optional vazio), tenta o dia anterior, e assim por diante.
    @Transactional
    public BigDecimal getBuyRate(LocalDate date) {
        LocalDate candidateDate = date;

        for (int i = 0; i <= MAX_LOOKBACK_DAYS; i++) {
            Optional<BigDecimal> cached = exchangeRateRepository.findByQuoteDate(candidateDate) //verifica tem salva no banco na data atual
                    .map(ExchangeRate::getBuyRate);
            if (cached.isPresent()) {
                return cached.get();
            }

            //verifica na api e se encontrar salva no banco
            Optional<BigDecimal> fetched = bcbExchangeRateClient.fetchBuyRate(candidateDate);
            if (fetched.isPresent()) {
                cacheQuote(candidateDate, fetched.get());
                return fetched.get();
            }

            candidateDate = candidateDate.minusDays(1); // se chegou aqui tenta um dia anterior
        }

        throw new ExchangeRateUnavailableException(
                "Nenhuma cotação encontrada nos últimos " + MAX_LOOKBACK_DAYS + " dias a partir de " + date);
    }

    private void cacheQuote(LocalDate date, BigDecimal buyRate) {
        ExchangeRate exchangeRate = ExchangeRate.builder()
                .quoteDate(date)
                .buyRate(buyRate)
                .build();
        exchangeRateRepository.save(exchangeRate);
    }
}
