package com.guilherme.crossborderapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guilherme.crossborderapi.client.BcbExchangeRateClient;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Aplicação inteira com H2 real; só o BCB é mockado. O contexto é compartilhado: cada teste cria seus usuários.
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    protected static final BigDecimal DEFAULT_RATE = new BigDecimal("5.0000");

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    protected BcbExchangeRateClient bcbClient;

    // Spies para forçar falhas no meio da remessa (os repositórios continuam reais)
    @MockitoSpyBean
    protected LedgerEntryRepository ledgerEntryRepository;

    @MockitoSpyBean
    protected RemittanceRepository remittanceRepository;

    @BeforeEach
    void stubBcbWithDefaultRate() {
        when(bcbClient.fetchBuyRate(any())).thenReturn(Optional.of(DEFAULT_RATE));
    }

    // Token forjado direto com o encoder, sem passar pelo login
    protected String tokenFor(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId)).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // Usuário qualquer autenticado (id inexistente): serve para rotas que não dependem de quem chama
    protected String bearer() {
        return tokenFor(-1L);
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
        postAs(userId, "/users/" + userId + "/wallets/deposit",
                "{\"currency\":\"%s\",\"amount\":%s}".formatted(currency, amount))
                .andExpect(status().isOk());
    }

    // O remetente é o dono do token
    protected ResultActions postRemittance(String idempotencyKey, long senderId, long receiverId, String amountBrl)
            throws Exception {
        return mockMvc.perform(post("/remittances")
                .header(HttpHeaders.AUTHORIZATION, tokenFor(senderId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":%d,\"amountBrl\":%s}".formatted(receiverId, amountBrl)));
    }

    protected long createRemittance(long senderId, long receiverId, String amountBrl) throws Exception {
        String key = "key-" + SEQUENCE.incrementAndGet();
        return idFrom(postRemittance(key, senderId, receiverId, amountBrl).andExpect(status().isCreated()));
    }

    protected ResultActions postAs(long userId, String url, String json) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, tokenFor(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions getAs(long userId, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, tokenFor(userId)));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions getJson(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    protected BigDecimal walletBalance(long userId, String currency) throws Exception {
        String body = getAs(userId, "/users/" + userId + "/wallets").andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.<List<Object>>read(body, "$.balances[?(@.currency=='" + currency + "')].balance")
                .get(0).toString());
    }

    protected long idFrom(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
