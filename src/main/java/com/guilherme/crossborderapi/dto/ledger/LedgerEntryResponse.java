package com.guilherme.crossborderapi.dto.ledger;

import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.LedgerEntryType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerEntryResponse(
        @Schema(description = "Id do lançamento", example = "1")
        Long id,

        @Schema(description = "Moeda do lançamento", example = "BRL")
        Currency currency,

        @Schema(description = "DEBIT = saiu dinheiro da carteira; CREDIT = entrou dinheiro", example = "DEBIT")
        LedgerEntryType type,

        @Schema(description = "Valor movimentado. Sempre positivo: o sentido vem do campo type", example = "100.0000")
        BigDecimal amount,

        @Schema(description = "Operação que originou o lançamento", example = "REMITTANCE")
        String referenceType,

        @Schema(description = "Id da operação que originou o lançamento (ex: id da remessa)", example = "1")
        Long referenceId,

        @Schema(description = "Data/hora do lançamento")
        LocalDateTime createdAt
) {

    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getCurrency(),
                entry.getType(),
                entry.getAmount(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getCreatedAt()
        );
    }
}
