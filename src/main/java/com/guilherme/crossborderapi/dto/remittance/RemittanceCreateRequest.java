package com.guilherme.crossborderapi.dto.remittance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record RemittanceCreateRequest(
        @Schema(description = "Id do usuário que recebe a remessa (o remetente é o usuário do token)", example = "2")
        @NotNull(message = "receiverId é obrigatório")
        Long receiverId,

        @Schema(description = "Valor em Real a ser enviado (será convertido para Dólar)", example = "1000.00")
        @NotNull(message = "amountBrl é obrigatório")
        @DecimalMin(value = "0.01", message = "amountBrl deve ser de no mínimo 0,01")
        @Digits(integer = 15, fraction = 2, message = "amountBrl deve ter no máximo 2 casas decimais")
        BigDecimal amountBrl
) {
}
