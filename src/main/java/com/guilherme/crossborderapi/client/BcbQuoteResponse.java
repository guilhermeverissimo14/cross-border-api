package com.guilherme.crossborderapi.client;

import java.math.BigDecimal;
import java.util.List;

// Formato de resposta da API PTAX do Banco Central.
public record BcbQuoteResponse(List<Quote> value) {

    public record Quote(BigDecimal cotacaoCompra, BigDecimal cotacaoVenda, String dataHoraCotacao) {
    }
}
