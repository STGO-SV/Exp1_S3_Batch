package com.duoc.banco_legacy.web;

import com.duoc.banco_legacy.web.client.*;
import com.duoc.banco_legacy.web.dto.WebAccountDashboard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebBffApplicationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @MockBean AccountServiceClient client;

    @Test void dashboardPreservesContractAndRelaysBearer() throws Exception {
        String jwt = token("WEB");
        var dashboard = new WebAccountDashboard(101, "Ana", "ahorro", new BigDecimal("1000"),
                new BigDecimal("0.01"), new BigDecimal("1010"),
                List.of(new WebAccountDashboard.MovementDetail(LocalDate.of(2026, 1, 1),
                        "deposito", new BigDecimal("100"), "Abono sueldo")),
                List.of(new WebAccountDashboard.AnomalyDetail(9, LocalDate.of(2026, 1, 2),
                        "debito", new BigDecimal("2500"))));
        when(client.getDashboard(101, "Bearer " + jwt)).thenReturn(dashboard);
        mvc.perform(get("/api/web/accounts/101/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.holderName").value("Ana"))
                .andExpect(jsonPath("$.processedBalance").value(1010))
                .andExpect(jsonPath("$.movements[0].description").value("Abono sueldo"))
                .andExpect(jsonPath("$.recentAnomalies[0].transactionId").value(9));
        verify(client).getDashboard(101, "Bearer " + jwt);
    }

    @Test void missingAccountIs404() throws Exception {
        when(client.getDashboard(eq(999L), anyString())).thenThrow(new AccountNotFoundException(999));
        mvc.perform(get("/api/web/accounts/999/dashboard").with(bearer("WEB")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test void downstreamFailureIs503WithoutBankingData() throws Exception {
        when(client.getDashboard(eq(101L), anyString()))
                .thenThrow(new AccountServiceUnavailableException(new IllegalStateException("downstream")));
        mvc.perform(get("/api/web/accounts/101/dashboard").with(bearer("WEB")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.processedBalance").doesNotExist());
    }

    @Test void wrongChannelIs403() throws Exception {
        mvc.perform(get("/api/web/accounts/101/dashboard").with(bearer("MOBILE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
}
