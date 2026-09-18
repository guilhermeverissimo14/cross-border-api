package com.guilherme.crossborderapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthorizationEndpointsTest extends ApiIntegrationTest {

    @Test
    void userCannotAccessAnotherUsersResources() throws Exception {
        long alice = createUser("PF");
        long bob = createUser("PF");
        deposit(bob, "BRL", "100");

        getAs(alice, "/users/" + bob).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acesso negado"));
        getAs(alice, "/users/" + bob + "/wallets").andExpect(status().isForbidden());
        getAs(alice, "/users/" + bob + "/remittances").andExpect(status().isForbidden());
        getAs(alice, "/users/" + bob + "/ledger-entries").andExpect(status().isForbidden());
        postAs(alice, "/users/" + bob + "/wallets/deposit", "{\"currency\":\"BRL\",\"amount\":999}")
                .andExpect(status().isForbidden());

        assertThat(walletBalance(bob, "BRL")).isEqualByComparingTo("100");
    }

    @Test
    void userCanAccessOwnResources() throws Exception {
        long alice = createUser("PF");

        getAs(alice, "/users/" + alice).andExpect(status().isOk());
        getAs(alice, "/users/" + alice + "/wallets").andExpect(status().isOk());
        getAs(alice, "/users/" + alice + "/remittances").andExpect(status().isOk());
        getAs(alice, "/users/" + alice + "/ledger-entries").andExpect(status().isOk());
    }

    @Test
    void senderIsAlwaysTheTokenOwnerEvenIfTheBodyClaimsAnother() throws Exception {
        long alice = createUser("PF");
        long bob = createUser("PF");
        long carol = createUser("PF");
        deposit(alice, "BRL", "100");
        deposit(bob, "BRL", "100");

        mockMvc.perform(post("/remittances")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(bob))
                        .header("Idempotency-Key", "impersonation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":%d,\"receiverId\":%d,\"amountBrl\":30}".formatted(alice, carol)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(bob));

        assertThat(walletBalance(alice, "BRL")).isEqualByComparingTo("100");
        assertThat(walletBalance(bob, "BRL")).isEqualByComparingTo("70");
    }

    @Test
    void remittanceIsVisibleOnlyToSenderAndReceiver() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        long stranger = createUser("PF");
        deposit(sender, "BRL", "100");
        long id = createRemittance(sender, receiver, "50");

        getAs(sender, "/remittances/" + id).andExpect(status().isOk());
        getAs(receiver, "/remittances/" + id).andExpect(status().isOk());
        getAs(sender, "/remittances/" + id + "/ledger-entries").andExpect(status().isOk());
        getAs(receiver, "/remittances/" + id + "/ledger-entries").andExpect(status().isOk());

        getAs(stranger, "/remittances/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Remessa não encontrada: id=" + id));
        getAs(stranger, "/remittances/" + id + "/ledger-entries").andExpect(status().isNotFound());
    }
}
