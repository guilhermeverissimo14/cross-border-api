package com.guilherme.crossborderapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.enums.LedgerEntryType;
import com.guilherme.crossborderapi.exception.ExchangeRateProviderUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class RemittanceEndpointsTest extends ApiIntegrationTest {

    @Test
    void createRemittanceMovesMoneyAndConvertsWithTheDayQuote() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PJ");
        deposit(sender, "BRL", "1000");

        postRemittance("ok-1", sender, receiver, "1000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.senderId").value(sender))
                .andExpect(jsonPath("$.receiverId").value(receiver))
                .andExpect(jsonPath("$.amountBrl").value(1000.0))
                .andExpect(jsonPath("$.amountUsd").value(200.0))
                .andExpect(jsonPath("$.exchangeRate").value(5.0))
                .andExpect(jsonPath("$.quoteDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.direction").value("BRL_TO_USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("0");
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("200");
        assertThat(walletBalance(sender, "USD")).isEqualByComparingTo("0");
        assertThat(walletBalance(receiver, "BRL")).isEqualByComparingTo("0");
    }

    @Test
    void remittanceOnADayWithoutQuoteRecordsTheDayOfTheQuoteActuallyUsed() throws Exception {
        LocalDate quoteDay = LocalDate.now().minusDays(2);
        clearQuoteCaches();
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.empty());
        when(bcbClient.fetchBuyRate(quoteDay)).thenReturn(Optional.of(new BigDecimal("4.0000")));
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "1000");

        long id = idFrom(postRemittance("fallback-day", sender, receiver, "1000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeRate").value(4.0))
                .andExpect(jsonPath("$.amountUsd").value(250.0))
                .andExpect(jsonPath("$.quoteDate").value(quoteDay.toString())));

        // O dia da cotação fica gravado: consulta e reenvio devolvem o mesmo valor
        getAs(sender, "/remittances/" + id).andExpect(jsonPath("$.quoteDate").value(quoteDay.toString()));
        postRemittance("fallback-day", sender, receiver, "1000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.quoteDate").value(quoteDay.toString()));
        getAs(sender, "/users/" + sender + "/remittances")
                .andExpect(jsonPath("$[0].quoteDate").value(quoteDay.toString()));
    }

    @Test
    void amountUsdIsRoundedToTwoDecimals() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");

        postRemittance("round-1", sender, receiver, "10.01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountBrl").value(10.01))
                .andExpect(jsonPath("$.amountUsd").value(2.0));
    }

    @Test
    void getRemittanceByIdReturnsItOr404() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");
        long id = createRemittance(sender, receiver, "50");

        getAs(sender, "/remittances/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amountBrl").value(50.0));

        getJson("/remittances/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Remessa não encontrada: id=999999999"));

        getJson("/remittances/abc").andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/remittances")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":2,\"amountBrl\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Header obrigatório ausente: Idempotency-Key"));
    }

    @Test
    void malformedIdempotencyKeyReturns400() throws Exception {
        postRemittance("chave com espaço!", 1, 2, "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key inválida")));

        postRemittance("k".repeat(65), 1, 2, "10").andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/remittances")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", "invalid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountBrl\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiverId").exists())
                .andExpect(jsonPath("$.fieldErrors.amountBrl").value("amountBrl deve ser de no mínimo 0,01"));

        postRemittance("invalid-body-2", 1, 2, "-10").andExpect(status().isBadRequest());
        postRemittance("invalid-body-3", 1, 2, "\"abc\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("amountBrl")));
    }

    @Test
    void amountsBelowOneCentOrWithMoreThanTwoDecimalsAreRejected() throws Exception {
        postRemittance("small-1", 1, 2, "0.004").andExpect(status().isBadRequest());
        postRemittance("small-2", 1, 2, "0.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountBrl").value("amountBrl deve ser de no mínimo 0,01"));
        postRemittance("small-3", 1, 2, "10.005")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountBrl").value("amountBrl deve ter no máximo 2 casas decimais"));
    }

    @Test
    void amountThatConvertsToZeroDollarsReturns422AndDebitsNothing() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "10");

        // R$ 0,01 a 5,00 por dólar são US$ 0,002, que arredonda para US$ 0,00
        postRemittance("zero-usd", sender, receiver, "0.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Valor muito baixo: R$ 0.01 converte para US$ 0,00"));

        assertNothingPersistedAndMoneyBack(sender, receiver, "10");
    }

    @Test
    void insufficientBalanceReturns422AndKeepsBalances() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "50");

        postRemittance("no-funds", sender, receiver, "50.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Saldo insuficiente para realizar a remessa"));

        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("50");
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("0");
        getAs(sender, "/users/" + sender + "/remittances").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void remittanceToSelfReturns422() throws Exception {
        long user = createUser("PF");
        deposit(user, "BRL", "100");

        postRemittance("self-1", user, user, "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("próprio usuário")));
    }

    @Test
    void unknownSenderOrReceiverReturns404() throws Exception {
        long user = createUser("PF");
        deposit(user, "BRL", "100");

        postRemittance("nf-sender", 999999999L, user, "10").andExpect(status().isNotFound());
        postRemittance("nf-receiver", user, 999999999L, "10").andExpect(status().isNotFound());
        assertThat(walletBalance(user, "BRL")).isEqualByComparingTo("100");
    }

    @Test
    void pfDailyLimitIsTenThousandReais() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "30000");

        postRemittance("pf-limit-1", sender, receiver, "10000.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("Limite diário")));

        postRemittance("pf-limit-2", sender, receiver, "6000").andExpect(status().isCreated());
        postRemittance("pf-limit-3", sender, receiver, "4000.01").andExpect(status().isUnprocessableContent());
        postRemittance("pf-limit-4", sender, receiver, "4000").andExpect(status().isCreated());
        postRemittance("pf-limit-5", sender, receiver, "0.01").andExpect(status().isUnprocessableContent());
    }

    @Test
    void pjDailyLimitIsFiftyThousandReais() throws Exception {
        long sender = createUser("PJ");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "60000");

        postRemittance("pj-limit-1", sender, receiver, "50000").andExpect(status().isCreated());
        postRemittance("pj-limit-2", sender, receiver, "0.01").andExpect(status().isUnprocessableContent());
    }

    @Test
    void dailyLimitOfOneSenderDoesNotAffectAnother() throws Exception {
        long richSender = createUser("PF");
        long otherSender = createUser("PF");
        long receiver = createUser("PF");
        deposit(richSender, "BRL", "10000");
        deposit(otherSender, "BRL", "10000");

        postRemittance("iso-1", richSender, receiver, "10000").andExpect(status().isCreated());
        postRemittance("iso-2", otherSender, receiver, "10000").andExpect(status().isCreated());
    }

    @Test
    void sameKeyAndSameDataReplaysTheOriginalWithoutDebitingAgain() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "500");

        long first = idFrom(postRemittance("replay-1", sender, receiver, "100").andExpect(status().isCreated()));
        long second = idFrom(postRemittance("replay-1", sender, receiver, "100.00").andExpect(status().isCreated()));

        assertThat(second).isEqualTo(first);
        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("400");
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("20");
        getAs(sender, "/users/" + sender + "/remittances").andExpect(jsonPath("$.length()").value(1));
        getAs(sender, "/remittances/" + first + "/ledger-entries").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void sameKeyWithDifferentDataReturns422() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        long another = createUser("PF");
        deposit(sender, "BRL", "500");
        postRemittance("reuse-1", sender, receiver, "100").andExpect(status().isCreated());

        postRemittance("reuse-1", sender, receiver, "150")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("dados diferentes")));
        postRemittance("reuse-1", sender, another, "100").andExpect(status().isUnprocessableContent());

        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("400");
    }

    @Test
    void sameKeyCanBeUsedByDifferentSenders() throws Exception {
        long senderA = createUser("PF");
        long senderB = createUser("PF");
        long receiver = createUser("PF");
        deposit(senderA, "BRL", "100");
        deposit(senderB, "BRL", "100");

        long a = idFrom(postRemittance("shared-key", senderA, receiver, "10").andExpect(status().isCreated()));
        long b = idFrom(postRemittance("shared-key", senderB, receiver, "10").andExpect(status().isCreated()));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void failedAttemptDoesNotBurnTheKey() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");

        postRemittance("retry-1", sender, receiver, "100").andExpect(status().isUnprocessableContent());
        deposit(sender, "BRL", "100");

        postRemittance("retry-1", sender, receiver, "100").andExpect(status().isCreated());
    }

    @Test
    void unavailableQuoteReturns422AndRollsBackEverything() throws Exception {
        clearQuoteCaches();
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.empty());
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");

        postRemittance("no-quote", sender, receiver, "100")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("Nenhuma cotação")));

        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("100");
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("0");
        getAs(sender, "/users/" + sender + "/ledger-entries").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bcbDownReturns503WithAClearMessageAndNothingIsDebited() throws Exception {
        clearQuoteCaches();
        when(bcbClient.fetchBuyRate(any()))
                .thenThrow(new ExchangeRateProviderUnavailableException("BCB fora do ar", null));
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");

        postRemittance("bcb-down", sender, receiver, "100")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Cotação do dólar temporariamente indisponível. Tente novamente em instantes."));

        assertNothingPersistedAndMoneyBack(sender, receiver, "100");
    }

    @Test
    void failureAfterDebitAndCreditRollsBackEverything() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");
        // O lançamento de débito é gravado de verdade; falha no de crédito, a última escrita da remessa
        doThrow(new IllegalStateException("falha simulada")).when(ledgerEntryRepository)
                .save(argThat((LedgerEntry entry) -> entry.getType() == LedgerEntryType.CREDIT));

        postRemittance("rollback-ledger", sender, receiver, "100")
                .andExpect(status().isInternalServerError());

        // Prova de que a falha veio depois das escritas: remessa e débito chegaram a ser gravados
        verify(remittanceRepository).save(any());
        verify(ledgerEntryRepository, times(2)).save(any());
        assertNothingPersistedAndMoneyBack(sender, receiver, "100");
    }

    @Test
    void failureWhileSavingTheRemittanceRollsBackTheWalletUpdates() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");
        doThrow(new IllegalStateException("falha simulada")).when(remittanceRepository).save(any());

        postRemittance("rollback-remittance", sender, receiver, "100")
                .andExpect(status().isInternalServerError());

        verify(remittanceRepository).save(any());
        assertNothingPersistedAndMoneyBack(sender, receiver, "100");
    }

    @Test
    void afterARolledBackFailureTheSameKeyCanBeRetriedAndMovesMoneyOnce() throws Exception {
        long sender = createUser("PF");
        long receiver = createUser("PF");
        deposit(sender, "BRL", "100");
        doThrow(new IllegalStateException("falha simulada")).when(ledgerEntryRepository).save(any());
        postRemittance("rollback-retry", sender, receiver, "100").andExpect(status().isInternalServerError());

        reset(ledgerEntryRepository);
        postRemittance("rollback-retry", sender, receiver, "100").andExpect(status().isCreated());

        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo("0");
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("20");
        getAs(sender, "/users/" + sender + "/remittances").andExpect(jsonPath("$.length()").value(1));
    }

    private void assertNothingPersistedAndMoneyBack(long sender, long receiver, String senderBalance) throws Exception {
        assertThat(walletBalance(sender, "BRL")).isEqualByComparingTo(senderBalance);
        assertThat(walletBalance(receiver, "USD")).isEqualByComparingTo("0");
        getAs(sender, "/users/" + sender + "/remittances").andExpect(jsonPath("$.length()").value(0));
        getAs(sender, "/users/" + sender + "/ledger-entries").andExpect(jsonPath("$.length()").value(0));
        getAs(receiver, "/users/" + receiver + "/ledger-entries").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void wrongContentTypeMethodAndRouteAreRejectedWithTheStandardBody() throws Exception {
        mockMvc.perform(post("/remittances")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", "ct-1")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        mockMvc.perform(delete("/remittances/1").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Método HTTP não permitido para esta rota"));

        getJson("/nao-existe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Rota não encontrada"));
    }
}
