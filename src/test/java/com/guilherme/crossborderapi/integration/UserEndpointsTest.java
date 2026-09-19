package com.guilherme.crossborderapi.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class UserEndpointsTest extends ApiIntegrationTest {

    private static final String NEW_USER = """
            {"fullName":"Maria Silva","email":"%s","password":"senha123","userType":"PF","document":"%s"}""";

    @Test
    void createUserReturns201WithoutPasswordAndProvisionsWallets() throws Exception {
        postJson("/users", NEW_USER.formatted("maria.create@teste.com", "90000000001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.create@teste.com"))
                .andExpect(jsonPath("$.userType").value("PF"))
                .andExpect(jsonPath("$.document").value("90000000001"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist());

        long id = createUser("PJ");
        getAs(id, "/users/" + id + "/wallets")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[?(@.currency=='BRL')].balance").value(0.0))
                .andExpect(jsonPath("$.balances[?(@.currency=='USD')].balance").value(0.0));
    }

    @Test
    void createUserWithDuplicateEmailReturns409() throws Exception {
        postJson("/users", NEW_USER.formatted("dup.email@teste.com", "90000000002")).andExpect(status().isCreated());

        postJson("/users", NEW_USER.formatted("dup.email@teste.com", "90000000003"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("dup.email@teste.com")));
    }

    @Test
    void createUserWithDuplicateDocumentReturns409() throws Exception {
        postJson("/users", NEW_USER.formatted("dup.doc1@teste.com", "90000000004")).andExpect(status().isCreated());

        postJson("/users", NEW_USER.formatted("dup.doc2@teste.com", "90000000004"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("90000000004")));
    }

    @Test
    void createUserWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
        postJson("/users", """
                {"fullName":"","email":"nao-e-email","password":"123","document":""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.userType").exists())
                .andExpect(jsonPath("$.fieldErrors.document").exists());
    }

    @Test
    void createUserWithUnknownUserTypeReturns400() throws Exception {
        postJson("/users", NEW_USER.formatted("tipo@teste.com", "90000000005").replace("\"PF\"", "\"PU\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("userType")));
    }

    @Test
    void documentLengthMustMatchTheUserType() throws Exception {
        String cnpjForPf = NEW_USER.formatted("len1@teste.com", "12345678000195");
        String cpfForPj = NEW_USER.formatted("len2@teste.com", "12345678909").replace("\"PF\"", "\"PJ\"");

        postJson("/users", cnpjForPf)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.document").value("document deve ser um CPF com 11 dígitos"));
        postJson("/users", cpfForPj)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.document").value("document deve ser um CNPJ com 14 dígitos"));
    }

    @Test
    void documentWithLettersIsRejected() throws Exception {
        postJson("/users", NEW_USER.formatted("abc1@teste.com", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.document").exists());
        postJson("/users", NEW_USER.formatted("abc2@teste.com", "12345678909abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.document").exists());
    }

    @Test
    void formattedAndPlainDocumentsAreTheSameUser() throws Exception {
        postJson("/users", NEW_USER.formatted("fmt1@teste.com", "123.456.789-09"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document").value("12345678909"));

        postJson("/users", NEW_USER.formatted("fmt2@teste.com", "12345678909"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("12345678909")));
    }

    @Test
    void formattedCnpjIsAcceptedForPj() throws Exception {
        postJson("/users", NEW_USER.formatted("cnpj@teste.com", "45.723.174/0001-10").replace("\"PF\"", "\"PJ\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document").value("45723174000110"));
    }

    @Test
    void emailIsNormalizedToLowercaseSoCaseDoesNotCreateDuplicates() throws Exception {
        postJson("/users", NEW_USER.formatted("Case.Test@Teste.COM", "90000000021"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("case.test@teste.com"));

        postJson("/users", NEW_USER.formatted("CASE.TEST@teste.com", "90000000022"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("case.test@teste.com")));
    }

    @Test
    void createUserWithMalformedJsonReturns400() throws Exception {
        postJson("/users", "{ isso não é json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"));
    }

    @Test
    void getUserByIdReturnsUser() throws Exception {
        long id = createUser("PF");

        getAs(id, "/users/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userType").value("PF"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserByIdReturns404WhenNotFound() throws Exception {
        getAs(999999999L, "/users/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado: id=999999999"));
    }

    @Test
    void getUserByIdWithNonNumericIdIsForbidden() throws Exception {
        getJson("/users/abc")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));
    }

    @Test
    void getAllUsersListsCreatedUsers() throws Exception {
        long first = createUser("PF");
        long second = createUser("PJ");

        getJson("/users")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) first)))
                .andExpect(jsonPath("$[*].id", hasItem((int) second)))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void getWalletsOfUnknownUserReturns404() throws Exception {
        getAs(999999999L, "/users/999999999/wallets").andExpect(status().isNotFound());
    }

    @Test
    void depositCreditsTheRequestedCurrencyOnly() throws Exception {
        long id = createUser("PF");

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":150.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.balances[?(@.currency=='BRL')].balance").value(150.5))
                .andExpect(jsonPath("$.balances[?(@.currency=='USD')].balance").value(0.0));

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"USD\",\"amount\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.currency=='USD')].balance").value(10.0));
    }

    @Test
    void depositsAccumulate() throws Exception {
        long id = createUser("PF");

        deposit(id, "BRL", "100");
        deposit(id, "BRL", "25.25");

        getAs(id, "/users/" + id + "/wallets")
                .andExpect(jsonPath("$.balances[?(@.currency=='BRL')].balance").value(125.25));
    }

    @Test
    void depositWithInvalidBodyReturns400() throws Exception {
        long id = createUser("PF");

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"amount\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"EUR\",\"amount\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("currency")));
    }

    @Test
    void depositBelowOneCentOrWithMoreThanTwoDecimalsReturns400() throws Exception {
        long id = createUser("PF");

        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":0.004}")
                .andExpect(status().isBadRequest());
        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":0.00}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount deve ser de no mínimo 0,01"));
        postAs(id, "/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":10.005}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount deve ter no máximo 2 casas decimais"));
    }

    @Test
    void depositForUnknownUserReturns404() throws Exception {
        postAs(999999999L, "/users/999999999/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":5}")
                .andExpect(status().isNotFound());
    }

    @Test
    void userRemittancesStartEmpty() throws Exception {
        long id = createUser("PF");

        getAs(id, "/users/" + id + "/remittances")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void userRemittancesListSentOnesNewestFirstAndFilterById() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "1000");
        long first = createRemittance(sender, receiver, "100");
        long second = createRemittance(sender, receiver, "200");

        getAs(sender, "/users/" + sender + "/remittances")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));

        getAs(sender, "/users/" + sender + "/remittances?remittanceId=" + first)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(first));

        getAs(receiver, "/users/" + receiver + "/remittances")
                .andExpect(jsonPath("$", hasSize(0)));

        getAs(receiver, "/users/" + receiver + "/remittances?remittanceId=" + first)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void userRemittancesReturn404ForUnknownUserAnd400ForBadFilter() throws Exception {
        getAs(999999999L, "/users/999999999/remittances").andExpect(status().isNotFound());

        long id = createUser("PF");
        getAs(id, "/users/" + id + "/remittances?remittanceId=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'remittanceId'"));
    }
}
