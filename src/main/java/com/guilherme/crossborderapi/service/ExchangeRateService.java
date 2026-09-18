package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.entity.ExchangeRate;
import com.guilherme.crossborderapi.exception.ExchangeRateUnavailableException;
import com.guilherme.crossborderapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final BcbExchangeRateClient bcbExchangeRateClient;

    //Se tem a cotação daquele dia no banco de dados, retorna ela. Se não, busca na API do BCB e salva no banco.
    @Transactional
    public BigDecimal getBuyRate(LocalDate date) {
        return exchangeRateRepository.findByQuoteDate(date)
                .map(ExchangeRate::getBuyRate)
                .orElseGet(() -> fetchAndCache(date));
    }

    private BigDecimal fetchAndCache(LocalDate date) {
        BigDecimal buyRate = bcbExchangeRateClient.fetchBuyRate(date)
                .orElseThrow(() -> new ExchangeRateUnavailableException(
                        "Cotação não disponível para " + date));

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .quoteDate(date)
                .buyRate(buyRate)
                .build();
        exchangeRateRepository.save(exchangeRate);

        return buyRate;
    }
}
