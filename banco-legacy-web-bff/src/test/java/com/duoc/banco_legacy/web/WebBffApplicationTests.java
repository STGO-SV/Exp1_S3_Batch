package com.duoc.banco_legacy.web;

import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.model.AccountMovement;
import com.duoc.banco_legacy.core.model.ProcessedTransaction;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebBffApplicationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate rest;
    @MockBean LegacyAccountQueryService service;

    @BeforeEach
    void data() {
        when(service.getBalance(101)).thenReturn(new AccountBalance(101L, "Ana", new BigDecimal("1000"),
                new BigDecimal("0.01"), new BigDecimal("1010"), "ahorro"));
        when(service.getMovementsForKnownAccount(101, 20)).thenReturn(List.of(
                new AccountMovement(101L, LocalDate.of(2026, 1, 1), "deposito", new BigDecimal("100"), "Abono sueldo")));
        when(service.getRecentAnomalies(10)).thenReturn(List.of(
                new ProcessedTransaction(9L, LocalDate.of(2026, 1, 2), new BigDecimal("2500"), "debito", true)));
    }

    @Test
    void entregaDashboardRicoAlUsuarioWeb() throws Exception {
        mvc.perform(get("/api/web/accounts/101/dashboard").with(bearer("WEB")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.holderName").value("Ana"))
                .andExpect(jsonPath("$.originalBalance").value(1000))
                .andExpect(jsonPath("$.movements[0].description").value("Abono sueldo"))
                .andExpect(jsonPath("$.recentAnomalies[0].transactionId").value(9));
    }

    @Test void rechazaPeticionSinAutenticacion() throws Exception {
        mvc.perform(get("/api/web/accounts/101/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test void impideQueUsuarioMovilUseWeb() throws Exception {
        mvc.perform(get("/api/web/accounts/101/dashboard").with(bearer("MOBILE")))
                .andExpect(status().isForbidden());
    }

    @Test void falloInternoNoSeEnmascaraComoProhibido() {
        when(service.getBalance(101)).thenThrow(new IllegalStateException("Fallo de infraestructura simulado"));

        var response = rest.exchange("/api/web/accounts/101/dashboard", org.springframework.http.HttpMethod.GET, entity("WEB"), String.class);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.getStatusCode().value());
    }
}
