package com.guilherme.crossborderapi.controller;

import com.guilherme.crossborderapi.dto.ledger.LedgerEntryResponse;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import com.guilherme.crossborderapi.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Extrato (Ledger)",
        description = "Lançamentos imutáveis de débito/crédito gerados pelas remessas. Somente leitura.")
public class LedgerController {

    private final LedgerService ledgerService;

    @Operation(
            summary = "Extrato do usuário",
            description = "Lançamentos das carteiras do usuário, do mais recente para o mais antigo. "
                    + "Cada remessa gera um DEBIT em BRL na carteira de quem envia e um CREDIT em USD na de "
                    + "quem recebe. Com `currency`, retorna só os lançamentos daquela moeda. "
                    + "Não inclui depósitos (o endpoint de depósito é só auxiliar de teste)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lançamentos do usuário (pode ser lista vazia)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(responseCode = "400", description = "Moeda inválida (use BRL ou USD) ou id não numérico",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/users/{id}/ledger-entries")
    public ResponseEntity<List<LedgerEntryResponse>> getUserLedgerEntries(
            @Parameter(description = "Id do usuário", example = "1") @PathVariable Long id,
            @Parameter(description = "Filtro opcional por moeda: BRL ou USD", example = "BRL")
            @RequestParam(required = false) Currency currency) {
        return ResponseEntity.ok(ledgerService.getUserLedgerEntries(id, currency));
    }
}
