package com.guilherme.crossborderapi.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class LedgerEndpointsTest extends ApiIntegrationTest {

    @Test
    void remittanceGeneratesDebitForSenderAndCreditForReceiver() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "1000");
        long remittanceId = createRemittance(sender, receiver, "100");

        getJson("/remittances/" + remittanceId + "/ledger-entries")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("DEBIT"))
                .andExpect(jsonPath("$[0].currency").value("BRL"))
                .andExpect(jsonPath("$[0].amount").value(100.0))
                .andExpect(jsonPath("$[0].referenceType").value("REMITTANCE"))
                .andExpect(jsonPath("$[0].referenceId").value(remittanceId))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].type").value("CREDIT"))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].amount").value(20.0));
    }

    @Test
    void userLedgerShowsOnlyOwnEntriesNewestFirst() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "1000");
        long first = createRemittance(sender, receiver, "100");
        long second = createRemittance(sender, receiver, "300");

        getJson("/users/" + sender + "/ledger-entries")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].referenceId").value(second))
                .andExpect(jsonPath("$[0].type").value("DEBIT"))
                .andExpect(jsonPath("$[1].referenceId").value(first));

        getJson("/users/" + receiver + "/ledger-entries")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("CREDIT"))
                .andExpect(jsonPath("$[0].amount").value(60.0));
    }

    @Test
    void currencyFilterKeepsOnlyThatCurrency() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "500");
        createRemittance(sender, receiver, "100");

        getJson("/users/" + sender + "/ledger-entries?currency=BRL")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currency").value("BRL"));
        getJson("/users/" + sender + "/ledger-entries?currency=USD")
                .andExpect(jsonPath("$.length()").value(0));
        getJson("/users/" + receiver + "/ledger-entries?currency=USD")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CREDIT"));
    }

    @Test
    void depositsDoNotAppearInTheLedger() throws Exception {
        long user = createUser("PF");
        deposit(user, "BRL", "100");

        getJson("/users/" + user + "/ledger-entries")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidCurrencyReturns400() throws Exception {
        long user = createUser("PF");

        getJson("/users/" + user + "/ledger-entries?currency=EUR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'currency'"));
    }

    @Test
    void unknownUserOrRemittanceReturns404() throws Exception {
        getJson("/users/999999999/ledger-entries").andExpect(status().isNotFound());
        getJson("/remittances/999999999/ledger-entries")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Remessa não encontrada: id=999999999"));
    }

    @Test
    void nonNumericIdsReturn400() throws Exception {
        getJson("/users/abc/ledger-entries").andExpect(status().isBadRequest());
        getJson("/remittances/abc/ledger-entries").andExpect(status().isBadRequest());
    }
}
