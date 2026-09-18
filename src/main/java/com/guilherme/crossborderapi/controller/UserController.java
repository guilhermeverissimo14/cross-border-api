package com.guilherme.crossborderapi.controller;

import com.guilherme.crossborderapi.dto.remittance.RemittanceResponse;
import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.dto.user.UserResponse;
import com.guilherme.crossborderapi.dto.wallet.UserWalletsResponse;
import com.guilherme.crossborderapi.dto.wallet.WalletDepositRequest;
import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import com.guilherme.crossborderapi.service.RemittanceService;
import com.guilherme.crossborderapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Cadastro e consulta de usuários PF/PJ e suas carteiras")
public class UserController {

    private final UserService userService;
    private final RemittanceService remittanceService;

    @Operation(
            summary = "Cadastrar usuário",
            description = "Cria um usuário (PF ou PJ) e já provisiona automaticamente suas carteiras "
                    + "em Real e em Dólar, ambas com saldo zero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (campo obrigatório ausente, e-mail mal formado, etc.)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail ou documento (CPF/CNPJ) já cadastrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Buscar usuário por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "Id do usuário", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Listar todos os usuários")
    @ApiResponse(responseCode = "200", description = "Lista de usuários",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(
            summary = "Consultar carteiras do usuário",
            description = "Retorna o saldo em Real e em Dólar do usuário."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carteiras do usuário",
                    content = @Content(schema = @Schema(implementation = UserWalletsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/wallets")
    public ResponseEntity<UserWalletsResponse> getUserWallets(
            @Parameter(description = "Id do usuário", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserWallets(id));
    }

    @Operation(
            summary = "Depositar saldo",
            description = "Adiciona saldo à carteira do usuário na moeda informada (BRL ou USD). "
                    + "Endpoint auxiliar para testes/demonstração — não faz parte do fluxo de remessa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Depósito realizado, retorna os saldos atualizados",
                    content = @Content(schema = @Schema(implementation = UserWalletsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário ou carteira não encontrados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/wallets/deposit")
    public ResponseEntity<UserWalletsResponse> deposit(
            @Parameter(description = "Id do usuário", example = "1") @PathVariable Long id,
            @Valid @RequestBody WalletDepositRequest request) {
        return ResponseEntity.ok(userService.deposit(id, request));
    }

    @Operation(
            summary = "Listar remessas enviadas pelo usuário",
            description = "Retorna as remessas enviadas pelo usuário, da mais recente para a mais antiga. "
                    + "Com `remittanceId`, retorna apenas essa remessa: lista com 1 item, ou vazia se ela não "
                    + "existir ou tiver sido enviada por outro usuário."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de remessas (pode ser vazia)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(responseCode = "400", description = "remittanceId ou id do usuário não numérico",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/remittances")
    public ResponseEntity<List<RemittanceResponse>> getUserRemittances(
            @Parameter(description = "Id do usuário remetente", example = "1") @PathVariable Long id,
            @Parameter(description = "Filtro opcional: id de uma remessa específica", example = "5")
            @RequestParam(required = false) Long remittanceId) {
        return ResponseEntity.ok(remittanceService.getRemittancesSentByUser(id, remittanceId));
    }
}
