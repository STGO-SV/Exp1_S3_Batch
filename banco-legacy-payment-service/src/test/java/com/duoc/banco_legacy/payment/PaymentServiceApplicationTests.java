package com.duoc.banco_legacy.payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class PaymentServiceApplicationTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Test
    void healthIsAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
    @Test
    void businessAndManagementPathsAreClosedUntilContractsAreDefined() throws Exception {
        for (String path : new String[]{"/internal/payment", "/actuator/env", "/actuator/info"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : new String[]{"WEB", "MOBILE", "ATM"}) {
                mvc.perform(get(path).with(bearer(role))).andExpect(status().isForbidden());
            }
        }
    }
    @Test
    void rejectsExpiredAndMalformedTokens() throws Exception {
        mvc.perform(get("/internal/payment").with(bearerExpired("WEB")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/payment").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}