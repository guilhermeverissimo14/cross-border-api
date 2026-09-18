package com.guilherme.crossborderapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "E-mail cadastrado", example = "maria.silva@email.com")
        @NotBlank(message = "email é obrigatório")
        String email,

        @Schema(description = "Senha do usuário", example = "senhaForte123")
        @NotBlank(message = "password é obrigatório")
        String password
) {
}
