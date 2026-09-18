package com.guilherme.crossborderapi.repository;

import com.guilherme.crossborderapi.entity.ExchangeRate;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByQuoteDate(LocalDate quoteDate);
}
