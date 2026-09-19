package com.guilherme.crossborderapi.dto.user;

import com.guilherme.crossborderapi.enums.UserType;
import com.guilherme.crossborderapi.validation.ValidDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidDocument
public record UserCreateRequest(
        @Schema(description = "Nome completo do usuário", example = "Maria da Silva")
        @NotBlank(message = "fullName é obrigatório")
        String fullName,

        @Schema(description = "E-mail único do usuário", example = "maria.silva@email.com")
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @Schema(description = "Senha do usuário (armazenada com hash)", example = "senhaForte123")
        @NotBlank(message = "password é obrigatório")
        @Size(min = 6, message = "password deve ter no mínimo 6 caracteres")
        String password,

        @Schema(description = "Tipo do usuário: PF (Pessoa Física) ou PJ (Pessoa Jurídica)", example = "PF")
        @NotNull(message = "userType é obrigatório")
        UserType userType,

        @Schema(description = "CPF (11 dígitos, se PF) ou CNPJ (14 dígitos, se PJ), com ou sem pontuação. É guardado só com os dígitos e é único no sistema", example = "123.456.789-09")
        @NotBlank(message = "document é obrigatório")
        String document
) {
}
