package com.guilherme.crossborderapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// O tamanho válido do documento depende do userType, por isso a validação é da requisição inteira
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DocumentValidator.class)
public @interface ValidDocument {

    String message() default "document inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
