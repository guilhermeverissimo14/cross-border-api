package com.guilherme.crossborderapi.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class InputNormalizer {

    // Dígitos e a pontuação usual de CPF/CNPJ (123.456.789-09, 12.345.678/0001-95)
    private static final Pattern DOCUMENT_CHARACTERS = Pattern.compile("[0-9.\\-/\\s]+");

    private InputNormalizer() {
    }

    public static String email(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean hasOnlyDocumentCharacters(String document) {
        return DOCUMENT_CHARACTERS.matcher(document).matches();
    }

    public static String document(String document) {
        return document.replaceAll("\\D", "");
    }
}
