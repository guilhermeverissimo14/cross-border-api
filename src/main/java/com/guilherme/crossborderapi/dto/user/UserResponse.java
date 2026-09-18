package com.guilherme.crossborderapi.dto.user;

import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record UserResponse(
        @Schema(description = "Id do usuário", example = "1")
        Long id,

        @Schema(description = "Nome completo do usuário", example = "Maria da Silva")
        String fullName,

        @Schema(description = "E-mail do usuário", example = "maria.silva@email.com")
        String email,

        @Schema(description = "Tipo do usuário", example = "PF")
        UserType userType,

        @Schema(description = "CPF ou CNPJ do usuário", example = "12345678900")
        String document,

        @Schema(description = "Data de criação do cadastro")
        LocalDateTime createdAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUserType(),
                user.getDocument(),
                user.getCreatedAt()
        );
    }
}
