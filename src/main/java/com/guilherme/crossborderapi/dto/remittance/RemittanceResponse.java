package com.guilherme.crossborderapi.dto.remittance;

import com.guilherme.crossborderapi.entity.Remittance;
import com.guilherme.crossborderapi.enums.RemittanceDirection;
import com.guilherme.crossborderapi.enums.RemittanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RemittanceResponse(
        @Schema(description = "Id da remessa", example = "1")
        Long id,

        @Schema(description = "Id do usuário que enviou", example = "1")
        Long senderId,

        @Schema(description = "Id do usuário que recebeu", example = "2")
        Long receiverId,

        @Schema(description = "Valor debitado em Real", example = "1000.00")
        BigDecimal amountBrl,

        @Schema(description = "Valor creditado em Dólar (já convertido)", example = "200.00")
        BigDecimal amountUsd,

        @Schema(description = "Cotação utilizada nessa remessa", example = "5.1234")
        BigDecimal exchangeRate,

        @Schema(description = "Dia a que pertence a cotação usada. Difere do dia da remessa em fim de semana, "
                + "feriado ou antes da publicação do dia (vale a última cotação disponível)", example = "2026-09-18")
        LocalDate quoteDate,

        @Schema(description = "Sentido da conversão")
        RemittanceDirection direction,

        @Schema(description = "Status final da remessa")
        RemittanceStatus status,

        @Schema(description = "Data/hora da remessa")
        LocalDateTime createdAt
) {

    public static RemittanceResponse fromEntity(Remittance remittance) {
        return new RemittanceResponse(
                remittance.getId(),
                remittance.getSender().getId(),
                remittance.getReceiver().getId(),
                remittance.getAmountBrl(),
                remittance.getAmountUsd(),
                remittance.getExchangeRate(),
                remittance.getQuoteDate(),
                remittance.getDirection(),
                remittance.getStatus(),
                remittance.getCreatedAt()
        );
    }
}
