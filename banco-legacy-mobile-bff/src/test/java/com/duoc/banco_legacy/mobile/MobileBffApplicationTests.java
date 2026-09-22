package com.duoc.banco_legacy.mobile;

import com.duoc.banco_legacy.mobile.client.AccountServiceClient;
import com.duoc.banco_legacy.mobile.client.AccountServiceUnavailableException;
import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import com.duoc.banco_legacy.mobile.dto.MobileMovement;
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
class MobileBffApplicationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate rest;
    @MockBean AccountServiceClient service;

    @BeforeEach
    void data() {
        when(service.getSummary(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new MobileAccountSummary(101, new BigDecimal("1010"), "ahorro"));
        when(service.getMovements(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(
                new MobileMovement(LocalDate.of(2026, 1, 1), "deposito", new BigDecimal("100"))));
    }

    @Test
    void entregaResumenLigeroSinDatosWeb() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary").with(bearer("MOBILE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1010))
                .andExpect(jsonPath("$.holderName").doesNotExist())
                .andExpect(jsonPath("$.originalBalance").doesNotExist());
    }

    @Test
    void movimientosNoExponenDescripcion() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/movements").with(bearer("MOBILE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[0].description").doesNotExist());
    }

    @Test void impideQueUsuarioWebUseMovil() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary").with(bearer("WEB")))
                .andExpect(status().isForbidden());
    }

    @Test void rechazaPeticionSinAutenticacion() throws Exception {
        mvc.perform(get("/api/mobile/accounts/101/summary")).andExpect(status().isUnauthorized());
    }

    @Test void falloInternoNoSeEnmascaraComoProhibido() {
        when(service.getSummary(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new AccountServiceUnavailableException(new IllegalStateException("Fallo simulado")));

        var response = rest.exchange("/api/mobile/accounts/101/summary", org.springframework.http.HttpMethod.GET, entity("MOBILE"), String.class);

        org.junit.jupiter.api.Assertions.assertEquals(503, response.getStatusCode().value());
    }
}
