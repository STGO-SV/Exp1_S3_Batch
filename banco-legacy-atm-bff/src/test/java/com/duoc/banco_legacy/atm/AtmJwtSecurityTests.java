package com.duoc.banco_legacy.atm;

import com.duoc.banco_legacy.atm.client.AccountServiceClient;
import com.duoc.banco_legacy.atm.dto.AtmBalance;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AtmJwtSecurityTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @MockBean AccountServiceClient service;
    static final String URL = "/api/atm/accounts/101/balance";
    @BeforeEach void data() {
        when(service.getBalance(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AtmBalance(101, BigDecimal.TEN));
    }
    @Test void validSignedToken() throws Exception {
        mvc.perform(get(URL).with(bearer("ATM"))).andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }
    @ParameterizedTest @ValueSource(strings = {"WEB", "MOBILE"})
    void rejectsOtherChannels(String role) throws Exception {
        mvc.perform(get(URL).with(bearer(role))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "issuer", "audience", "future", "signature", "missing-exp", "roles-type"})
    void rejectsInvalidTokens(String scenario) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token("ATM", scenario)))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsAlteredPayload() throws Exception {
        String jwt = token("ATM");
        String[] parts = jwt.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("test-user", "attacker").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get(URL).header("Authorization", "Bearer " + String.join(".", parts))).andExpect(status().isUnauthorized());
    }
    @Test void rejectsMalformedToken() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }
    @Test void rejectsMissingToken() throws Exception { mvc.perform(get(URL)).andExpect(status().isUnauthorized()); }
    @Test void rejectsBasic() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(
                "irrelevant:invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8)))).andExpect(status().isUnauthorized());
    }
    @Test void rejectsExternalErrorRequest() throws Exception {
        mvc.perform(get("/error").with(bearer("ATM"))).andExpect(status().isForbidden());
    }
}
