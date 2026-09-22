package com.duoc.banco_legacy.atm;

import com.duoc.banco_legacy.core.model.LockedAccountBalance;
import com.duoc.banco_legacy.core.repository.LegacyAccountWithdrawalRepository;
import com.duoc.banco_legacy.atm.client.AccountServiceClient;
import com.duoc.banco_legacy.atm.dto.AtmBalance;
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
import java.util.Optional;

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
    @MockBean AccountServiceClient service;
    @MockBean LegacyAccountWithdrawalRepository withdrawalRepository;

    @BeforeEach
    void data() {
        when(service.getBalance(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AtmBalance(101, new BigDecimal("1010")));
        when(withdrawalRepository.lockLatestBalance(101))
                .thenReturn(Optional.of(new LockedAccountBalance(1, new BigDecimal("1010"))));
    }

    @Test
    void entregaSoloSaldoNecesarioParaAtm() throws Exception {
        mvc.perform(get("/api/atm/accounts/101/balance").with(bearer("ATM")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableBalance").value(1010))
                .andExpect(jsonPath("$.holderName").doesNotExist());
    }

    @Test
    void completaRetiroConNuevoSaldo() throws Exception {
        mvc.perform(post("/api/atm/accounts/101/withdrawals")
                        .with(bearer("ATM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balanceBefore").value(1010))
                .andExpect(jsonPath("$.balanceAfter").value(910))
                .andExpect(jsonPath("$.projectedBalance").doesNotExist());
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
        when(service.getBalance(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("Fallo de infraestructura simulado"));

        var response = rest.exchange("/api/atm/accounts/101/balance", org.springframework.http.HttpMethod.GET, entity("ATM"), String.class);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.getStatusCode().value());
    }
}
