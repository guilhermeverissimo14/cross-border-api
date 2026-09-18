package com.guilherme.crossborderapi.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserWalletsResponse(
        @Schema(description = "Id do usuário dono das carteiras", example = "1")
        Long userId,

        @Schema(description = "Saldos do usuário por moeda")
        List<WalletResponse> balances
) {
}
