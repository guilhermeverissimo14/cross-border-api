package com.guilherme.crossborderapi.dto.wallet;

import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record WalletResponse(
        @Schema(description = "Moeda da carteira", example = "BRL")
        Currency currency,

        @Schema(description = "Saldo disponível nessa moeda", example = "1000.00")
        BigDecimal balance
) {

    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(wallet.getCurrency(), wallet.getBalance());
    }
}
