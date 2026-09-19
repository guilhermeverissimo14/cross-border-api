package com.guilherme.crossborderapi.controller;

import com.guilherme.crossborderapi.dto.remittance.RemittanceCreateRequest;
import com.guilherme.crossborderapi.dto.remittance.RemittanceResponse;
import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import com.guilherme.crossborderapi.service.RemittanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            description = "O remetente é o usuário do token. Converte o valor em Real para Dólar usando a cotação do dia (API do BCB) e "
                    + "transfere o valor convertido para a carteira em Dólar do destinatário. Valida saldo "
                    + "do remetente antes de processar; tudo roda em uma única transação, revertida em "
                    + "caso de qualquer inconsistência.\n\n"
                    + "**Idempotência:** o header `Idempotency-Key` é obrigatório. Repetir a chamada com a "
                    + "mesma chave e os mesmos dados (ex: retry após timeout, clique duplo) devolve a remessa "
                    + "já criada, sem debitar de novo. Gere uma chave nova (ex: UUID) para cada remessa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Remessa realizada com sucesso (ou, em reenvio da mesma chave, a remessa já criada)",
                    content = @Content(schema = @Schema(implementation = RemittanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (campo obrigatório ausente, valor não positivo, header Idempotency-Key ausente ou mal formatado)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Remetente, destinatário ou alguma das carteiras não encontrados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma remessa sendo processada com essa Idempotency-Key, ou a carteira foi alterada por outra operação; tente novamente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "API do BCB fora do ar e sem cotação recente em cache; tente novamente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada (saldo insuficiente, limite diário, remessa para o próprio usuário, cotação indisponível) ou Idempotency-Key já usada com dados diferentes",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<RemittanceResponse> createRemittance(
            @Parameter(description = "Chave de idempotência: 1 a 64 caracteres entre letras, números, '.', '_' e '-'. "
                    + "Use uma chave nova para cada remessa e reaproveite-a apenas ao repetir a mesma chamada.",
                    required = true, example = "7f9c2b1e-3d4a-4c8e-9f10-2a6b5d8e1c34")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RemittanceCreateRequest request,
            Authentication authentication) {
        RemittanceResponse response = remittanceService.createRemittance(
                Long.valueOf(authentication.getName()), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Buscar remessa por id",
            description = "Só quem enviou ou recebeu a remessa a enxerga; para os demais, responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Remessa encontrada",
                    content = @Content(schema = @Schema(implementation = RemittanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Remessa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<RemittanceResponse> getRemittanceById(
            @Parameter(description = "Id da remessa", example = "1") @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(remittanceService.getRemittanceById(id, Long.valueOf(authentication.getName())));
    }
}
