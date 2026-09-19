package com.guilherme.crossborderapi.dto.wallet;

import com.guilherme.crossborderapi.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record WalletDepositRequest(
        @Schema(description = "Moeda a depositar (BRL ou USD)", example = "BRL")
        @NotNull(message = "currency é obrigatório")
        Currency currency,

        @Schema(description = "Valor a depositar", example = "1000.00")
        @NotNull(message = "amount é obrigatório")
        @DecimalMin(value = "0.01", message = "amount deve ser de no mínimo 0,01")
        @Digits(integer = 15, fraction = 2, message = "amount deve ter no máximo 2 casas decimais")
        BigDecimal amount
) {
}
