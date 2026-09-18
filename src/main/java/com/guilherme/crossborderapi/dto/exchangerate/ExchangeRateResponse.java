package com.guilherme.crossborderapi.dto.exchangerate;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
        @Schema(description = "Data consultada", example = "2026-09-13")
        LocalDate requestedDate,

        @Schema(description = "Data da cotação efetivamente usada. Difere da consultada quando não há cotação "
                + "naquele dia (fim de semana, feriado ou ainda não publicada) e vale a última disponível",
                example = "2026-09-11")
        LocalDate quoteDate,

        @Schema(description = "Cotação de compra do dólar (cotacaoCompra do BCB), em reais por US$ 1", example = "5.151500")
        BigDecimal buyRate
) {

    public static ExchangeRateResponse from(LocalDate requestedDate, ExchangeRateQuote quote) {
        return new ExchangeRateResponse(requestedDate, quote.quoteDate(), quote.buyRate());
    }
}
