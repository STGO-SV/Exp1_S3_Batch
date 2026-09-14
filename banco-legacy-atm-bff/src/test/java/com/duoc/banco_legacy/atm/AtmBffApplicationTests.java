package com.duoc.banco_legacy.atm;

import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AtmBffApplicationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate rest;
    @MockBean LegacyAccountQueryService service;

    @BeforeEach
    void data() {
        when(service.getAvailableBalance(101)).thenReturn(new BigDecimal("1010"));
    }

    @Test
    void entregaSoloSaldoNecesarioParaAtm() throws Exception {
        mvc.perform(get("/api/atm/accounts/101/balance").with(bearer("ATM")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableBalance").value(1010))
                .andExpect(jsonPath("$.holderName").doesNotExist());
    }

    @Test
    void simulaRetiroSinAfirmarPersistencia() throws Exception {
        mvc.perform(post("/api/atm/accounts/101/withdrawals")
                        .with(bearer("ATM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIMULATED"))
                .andExpect(jsonPath("$.projectedBalance").value(910));
    }

    @Test void rechazaRetiroSobreElSaldo() throws Exception {
        mvc.perform(post("/api/atm/accounts/101/withdrawals")
                        .with(bearer("ATM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000}"))
                .andExpect(status().isBadRequest());
    }

    @Test void impideQueUsuarioMovilUseAtm() throws Exception {
        mvc.perform(get("/api/atm/accounts/101/balance").with(bearer("MOBILE")))
                .andExpect(status().isForbidden());
    }

    @Test void rechazaPeticionSinAutenticacion() throws Exception {
        mvc.perform(get("/api/atm/accounts/101/balance")).andExpect(status().isUnauthorized());
    }

    @Test void falloInternoNoSeEnmascaraComoProhibido() {
        when(service.getAvailableBalance(101)).thenThrow(new IllegalStateException("Fallo de infraestructura simulado"));

        var response = rest.exchange("/api/atm/accounts/101/balance", org.springframework.http.HttpMethod.GET, entity("ATM"), String.class);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.getStatusCode().value());
    }
}
