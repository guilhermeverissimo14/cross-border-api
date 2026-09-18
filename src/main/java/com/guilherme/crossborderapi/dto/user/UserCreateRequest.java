package com.guilherme.crossborderapi.dto.user;

import com.guilherme.crossborderapi.enums.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "fullName é obrigatório")
        String fullName,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "password é obrigatório")
        @Size(min = 6, message = "password deve ter no mínimo 6 caracteres")
        String password,

        @NotNull(message = "userType é obrigatório")
        UserType userType,

        @NotBlank(message = "document é obrigatório")
        String document
) {
}
