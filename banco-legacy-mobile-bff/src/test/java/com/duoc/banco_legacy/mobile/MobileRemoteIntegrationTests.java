package com.duoc.banco_legacy.mobile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duoc.banco_legacy.mobile.client.AccountNotFoundException;
import com.duoc.banco_legacy.mobile.client.AccountServiceClient;
import com.duoc.banco_legacy.mobile.client.AccountServiceUnavailableException;
import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MobileRemoteIntegrationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @MockBean AccountServiceClient client;

    @Test
    void forwardsBearerAndMapsRemoteSummary() throws Exception {
        String token = token("MOBILE");
        when(client.getSummary(101, "Bearer " + token))
                .thenReturn(new MobileAccountSummary(101, new BigDecimal("1010"), "ahorro"));

        mvc.perform(get("/api/mobile/accounts/101/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1010));
        verify(client).getSummary(101, "Bearer " + token);
    }

    @Test
    void preservesNotFound() throws Exception {
        when(client.getSummary(org.mockito.ArgumentMatchers.eq(999L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new AccountNotFoundException(999));
        mvc.perform(get("/api/mobile/accounts/999/summary").with(bearer("MOBILE")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void fallbackDoesNotInventFinancialData() throws Exception {
        when(client.getSummary(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new AccountServiceUnavailableException(new IllegalStateException("downstream")));
        mvc.perform(get("/api/mobile/accounts/101/summary").with(bearer("MOBILE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.balance").doesNotExist());
    }
}
