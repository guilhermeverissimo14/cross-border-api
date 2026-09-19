package com.guilherme.crossborderapi.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

class AuthEndpointsTest extends ApiIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(
                ("{\"fullName\":\"Ana\",\"email\":\"%s\",\"password\":\"senha123\","
                        + "\"userType\":\"PF\",\"document\":\"%s\"}").formatted(email, "%011d".formatted(email.hashCode() & 0x7fffffffL))));
        String body = login(email, "senha123").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void loginReturnsATokenThatOpensProtectedEndpoints() throws Exception {
        String token = registerAndLogin("ana.login@teste.com");

        login("ana.login@teste.com", "senha123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginIgnoresTheCaseOfTheEmail() throws Exception {
        registerAndLogin("ana.case@teste.com");

        login("  ANA.Case@Teste.COM ", "senha123").andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGenericError() throws Exception {
        registerAndLogin("ana.wrong@teste.com");

        login("ana.wrong@teste.com", "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
        login("nao.existe@teste.com", "senha123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401InTheStandardFormat() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária: envie o header Authorization: Bearer <token>"));

        mockMvc.perform(get("/users/1/wallets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/remittances/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/1/ledger-entries")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/remittances").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokensReturn401() throws Exception {
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer isso.nao.e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String good = registerAndLogin("ana.tamper@teste.com");
        String tampered = good.substring(0, good.length() - 4) + "AAAA";
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Basic YTpi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("1").issuedAt(past.minusSeconds(60)).expiresAt(past).build();
        String expired = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsWorkWithoutToken() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"fullName\":\"Aberto\",\"email\":\"aberto@teste.com\",\"password\":\"senha123\","
                                + "\"userType\":\"PF\",\"document\":\"81000000001\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/exchange-rates/2025-07-01")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
