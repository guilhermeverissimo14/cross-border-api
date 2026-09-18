package com.guilherme.crossborderapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Aplicação inteira com H2 real; só o BCB é mockado. O contexto é compartilhado: cada teste cria seus usuários.
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    protected static final BigDecimal DEFAULT_RATE = new BigDecimal("5.0000");

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    private static String callerToken;

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected BcbExchangeRateClient bcbClient;

    @BeforeEach
    void stubBcbWithDefaultRate() {
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.of(DEFAULT_RATE));
    }

    // Um usuário qualquer autenticado, reaproveitado por todos os testes
    protected synchronized String bearer() throws Exception {
        if (callerToken == null) {
            long n = SEQUENCE.incrementAndGet();
            mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(
                    ("{\"fullName\":\"Caller\",\"email\":\"caller%d@teste.com\",\"password\":\"senha123\","
                            + "\"userType\":\"PF\",\"document\":\"%011d\"}").formatted(n, n)));
            String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"caller%d@teste.com\",\"password\":\"senha123\"}".formatted(n)))
                    .andReturn().getResponse().getContentAsString();
            callerToken = JsonPath.read(body, "$.accessToken");
        }
        return "Bearer " + callerToken;
    }

    protected long createUser(String userType) throws Exception {
        long n = SEQUENCE.incrementAndGet();
        String document = "PF".equals(userType) ? "%011d".formatted(n) : "%014d".formatted(n);
        String body = """
                {"fullName":"Usuário %d","email":"user%d@teste.com","password":"senha123",
                 "userType":"%s","document":"%s"}""".formatted(n, n, userType, document);
        return idFrom(postJson("/users", body).andExpect(status().isCreated()));
    }

    protected void deposit(long userId, String currency, String amount) throws Exception {
        postJson("/users/" + userId + "/wallets/deposit",
                "{\"currency\":\"%s\",\"amount\":%s}".formatted(currency, amount))
                .andExpect(status().isOk());
    }

    protected ResultActions postRemittance(String idempotencyKey, long senderId, long receiverId, String amountBrl)
            throws Exception {
        return mockMvc.perform(post("/remittances")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senderId\":%d,\"receiverId\":%d,\"amountBrl\":%s}"
                        .formatted(senderId, receiverId, amountBrl)));
    }

    protected long createRemittance(long senderId, long receiverId, String amountBrl) throws Exception {
        String key = "key-" + SEQUENCE.incrementAndGet();
        return idFrom(postRemittance(key, senderId, receiverId, amountBrl).andExpect(status().isCreated()));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions getJson(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    protected BigDecimal walletBalance(long userId, String currency) throws Exception {
        String body = getJson("/users/" + userId + "/wallets").andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.<List<Object>>read(body, "$.balances[?(@.currency=='" + currency + "')].balance")
                .get(0).toString());
    }

    protected long idFrom(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
