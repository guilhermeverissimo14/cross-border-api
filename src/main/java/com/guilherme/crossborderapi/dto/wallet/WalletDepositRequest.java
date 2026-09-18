package com.guilherme.crossborderapi.dto.wallet;

import com.guilherme.crossborderapi.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record WalletDepositRequest(
        @Schema(description = "Moeda a depositar (BRL ou USD)", example = "BRL")
        @NotNull(message = "currency é obrigatório")
        Currency currency,

        @Schema(description = "Valor a depositar", example = "1000.00")
        @NotNull(message = "amount é obrigatório")
        @Positive(message = "amount deve ser maior que zero")
        BigDecimal amount
) {
}
