package com.guilherme.crossborderapi.controller;

import com.guilherme.crossborderapi.dto.remittance.RemittanceCreateRequest;
import com.guilherme.crossborderapi.dto.remittance.RemittanceResponse;
import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import com.guilherme.crossborderapi.service.RemittanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/remittances")
@RequiredArgsConstructor
@Tag(name = "Remessas", description = "Remessa entre usuários: conversão de Real para Dólar seguida de transferência")
public class RemittanceController {

    private final RemittanceService remittanceService;

    @Operation(
            summary = "Criar remessa",
            description = "Converte o valor em Real para Dólar usando a cotação do dia (API do BCB) e "
                    + "transfere o valor convertido para a carteira em Dólar do destinatário. Valida saldo "
                    + "do remetente antes de processar; tudo roda em uma única transação, revertida em "
                    + "caso de qualquer inconsistência."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Remessa realizada com sucesso",
                    content = @Content(schema = @Schema(implementation = RemittanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (campo obrigatório ausente, valor não positivo, etc.)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Remetente, destinatário ou alguma das carteiras não encontrados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada (saldo insuficiente, remessa para o próprio usuário, cotação indisponível)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<RemittanceResponse> createRemittance(@Valid @RequestBody RemittanceCreateRequest request) {
        RemittanceResponse response = remittanceService.createRemittance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
