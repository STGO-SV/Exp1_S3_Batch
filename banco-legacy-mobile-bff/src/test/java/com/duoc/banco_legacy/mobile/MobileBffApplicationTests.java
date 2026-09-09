package com.duoc.banco_legacy.mobile;

import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.model.AccountMovement;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MobileBffApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate rest;
    @MockBean LegacyAccountQueryService service;

    @BeforeEach
    void data() {
        when(service.getBalance(101)).thenReturn(new AccountBalance(101L, "Ana", new BigDecimal("1000"),
                new BigDecimal("0.01"), new BigDecimal("1010"), "ahorro"));
        when(service.getRecentMovements(101, 5)).thenReturn(List.of(
                new AccountMovement(101L, LocalDate.of(2026, 1, 1), "deposito", new BigDecimal("100"), "Detalle privado")));
    }

    @Test
    void entregaResumenLigeroSinDatosWeb() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary").with(httpBasic("mobile-user", "mobile-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1010))
                .andExpect(jsonPath("$.holderName").doesNotExist())
                .andExpect(jsonPath("$.originalBalance").doesNotExist());
    }

    @Test
    void movimientosNoExponenDescripcion() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/movements").with(httpBasic("mobile-user", "mobile-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[0].description").doesNotExist());
    }

    @Test void impideQueUsuarioWebUseMovil() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary").with(httpBasic("web-user", "web-pass")))
                .andExpect(status().isForbidden());
    }

    @Test void rechazaPeticionSinAutenticacion() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary")).andExpect(status().isUnauthorized());
    }

    @Test void falloInternoNoSeEnmascaraComoProhibido() {
        when(service.getBalance(101)).thenThrow(new IllegalStateException("Fallo de infraestructura simulado"));

        var response = rest.withBasicAuth("mobile-user", "mobile-pass")
                .getForEntity("/api/mobile/accounts/101/summary", String.class);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.getStatusCode().value());
    }
}
