package com.guilherme.crossborderapi.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DocsAndHealthEndpointsTest extends ApiIntegrationTest {

    @Test
    void openApiDocumentListsTheApiEndpoints() throws Exception {
        getJson("/v3/api-docs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").exists())
                .andExpect(jsonPath("$.paths['/users']").exists())
                .andExpect(jsonPath("$.paths['/users/{id}/wallets/deposit']").exists())
                .andExpect(jsonPath("$.paths['/remittances']").exists())
                .andExpect(jsonPath("$.paths['/remittances/{id}/ledger-entries']").exists())
                .andExpect(jsonPath("$.paths['/exchange-rates/{data}']").exists());
    }

    @Test
    void healthAndProbesAreUp() throws Exception {
        getJson("/actuator/health").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        getJson("/actuator/health/liveness").andExpect(status().isOk());
        getJson("/actuator/health/readiness").andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        getJson("/actuator/env")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Rota não encontrada")));
    }
}
