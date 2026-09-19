package com.guilherme.crossborderapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.enums.UserType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DocumentValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<UserCreateRequest>> validate(UserType type, String document) {
        return validator.validate(new UserCreateRequest("Maria", "maria@email.com", "senha123", type, document));
    }

    @ParameterizedTest
    @CsvSource({
            "PF, 12345678909",
            "PF, 123.456.789-09",
            "PF, '  123 456 789 09 '",
            "PJ, 12345678000195",
            "PJ, 12.345.678/0001-95"
    })
    void acceptsDocumentsWithTheRightNumberOfDigits(UserType type, String document) {
        assertThat(validate(type, document)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "PF, 12345678000195",
            "PJ, 12345678909",
            "PF, 1234567890",
            "PF, 123456789099",
            "PF, abc",
            "PF, 12345678909abc",
            "PJ, 12.345.678/0001-9X"
    })
    void rejectsWrongLengthOrForeignCharacters(UserType type, String document) {
        Set<ConstraintViolation<UserCreateRequest>> violations = validate(type, document);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("document");
    }

    @Test
    void leavesMissingValuesToTheFieldLevelConstraints() {
        assertThat(validate(null, "123")).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("userType");
        assertThat(validate(UserType.PF, " ")).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("document"); // só o @NotBlank, sem uma segunda mensagem
    }

    @Test
    void normalizesEmailAndDocument() {
        assertThat(InputNormalizer.email("  Ana@X.COM ")).isEqualTo("ana@x.com");
        assertThat(InputNormalizer.document("12.345.678/0001-95")).isEqualTo("12345678000195");
    }
}
