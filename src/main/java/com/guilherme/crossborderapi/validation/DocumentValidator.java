package com.guilherme.crossborderapi.validation;

import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.enums.UserType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentValidator implements ConstraintValidator<ValidDocument, UserCreateRequest> {

    private static final int CPF_DIGITS = 11;
    private static final int CNPJ_DIGITS = 14;

    @Override
    public boolean isValid(UserCreateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.userType() == null
                || request.document() == null || request.document().isBlank()) {
            return true; // a ausência é apontada pelo @NotNull/@NotBlank do próprio campo
        }

        boolean personal = request.userType() == UserType.PF;
        boolean valid = InputNormalizer.hasOnlyDocumentCharacters(request.document())
                && InputNormalizer.document(request.document()).length() == (personal ? CPF_DIGITS : CNPJ_DIGITS);

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            personal ? "document deve ser um CPF com 11 dígitos" : "document deve ser um CNPJ com 14 dígitos")
                    .addPropertyNode("document")
                    .addConstraintViolation();
        }
        return valid;
    }
}
