package com.guilherme.crossborderapi.dto.exchangerate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateQuote(LocalDate quoteDate, BigDecimal buyRate) {
}
