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
        getJson("/users/" + id + "/wallets")
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
    void createUserWithMalformedJsonReturns400() throws Exception {
        postJson("/users", "{ isso não é json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"));
    }

    @Test
    void getUserByIdReturnsUser() throws Exception {
        long id = createUser("PF");

        getJson("/users/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userType").value("PF"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserByIdReturns404WhenNotFound() throws Exception {
        getJson("/users/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado: id=999999999"));
    }

    @Test
    void getUserByIdWithNonNumericIdReturns400() throws Exception {
        getJson("/users/abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'id'"));
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
        getJson("/users/999999999/wallets").andExpect(status().isNotFound());
    }

    @Test
    void depositCreditsTheRequestedCurrencyOnly() throws Exception {
        long id = createUser("PF");

        postJson("/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":150.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.balances[?(@.currency=='BRL')].balance").value(150.5))
                .andExpect(jsonPath("$.balances[?(@.currency=='USD')].balance").value(0.0));

        postJson("/users/" + id + "/wallets/deposit", "{\"currency\":\"USD\",\"amount\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.currency=='USD')].balance").value(10.0));
    }

    @Test
    void depositsAccumulate() throws Exception {
        long id = createUser("PF");

        deposit(id, "BRL", "100");
        deposit(id, "BRL", "25.25");

        getJson("/users/" + id + "/wallets")
                .andExpect(jsonPath("$.balances[?(@.currency=='BRL')].balance").value(125.25));
    }

    @Test
    void depositWithInvalidBodyReturns400() throws Exception {
        long id = createUser("PF");

        postJson("/users/" + id + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        postJson("/users/" + id + "/wallets/deposit", "{\"amount\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());

        postJson("/users/" + id + "/wallets/deposit", "{\"currency\":\"EUR\",\"amount\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("currency")));
    }

    @Test
    void depositForUnknownUserReturns404() throws Exception {
        postJson("/users/999999999/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":5}")
                .andExpect(status().isNotFound());
    }

    @Test
    void userRemittancesStartEmpty() throws Exception {
        long id = createUser("PF");

        getJson("/users/" + id + "/remittances")
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

        getJson("/users/" + sender + "/remittances")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));

        getJson("/users/" + sender + "/remittances?remittanceId=" + first)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(first));

        getJson("/users/" + receiver + "/remittances")
                .andExpect(jsonPath("$", hasSize(0)));

        getJson("/users/" + receiver + "/remittances?remittanceId=" + first)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void userRemittancesReturn404ForUnknownUserAnd400ForBadFilter() throws Exception {
        getJson("/users/999999999/remittances").andExpect(status().isNotFound());

        long id = createUser("PF");
        getJson("/users/" + id + "/remittances?remittanceId=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'remittanceId'"));
    }
}
