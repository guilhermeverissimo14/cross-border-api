package com.guilherme.crossborderapi.dto.wallet;

import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import java.math.BigDecimal;

public record WalletResponse(
        Currency currency,
        BigDecimal balance
) {

    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(wallet.getCurrency(), wallet.getBalance());
    }
}
