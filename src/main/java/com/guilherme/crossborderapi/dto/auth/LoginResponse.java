package com.guilherme.crossborderapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "Token JWT: envie em todas as chamadas no header Authorization: Bearer <token>")
        String accessToken,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,

        @Schema(description = "Validade do token, em segundos", example = "3600")
        long expiresIn
) {
}
