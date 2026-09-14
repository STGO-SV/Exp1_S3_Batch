package com.duoc.banco_legacy.web;

import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import com.duoc.banco_legacy.core.model.AccountBalance;
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
class WebJwtSecurityTests extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @MockBean LegacyAccountQueryService service;
    static final String URL = "/api/web/accounts/101/dashboard";
    @BeforeEach void data() {
        when(service.getBalance(101)).thenReturn(new AccountBalance(101L, "Ana", BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.TEN, "ahorro"));
    }
    @Test void validSignedToken() throws Exception {
        mvc.perform(get(URL).with(bearer("WEB"))).andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }
    @ParameterizedTest @ValueSource(strings = {"MOBILE", "ATM"})
    void rejectsOtherChannels(String role) throws Exception {
        mvc.perform(get(URL).with(bearer(role))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "issuer", "audience", "future", "signature", "missing-exp", "roles-type"})
    void rejectsInvalidTokens(String scenario) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token("WEB", scenario)))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsAlteredPayload() throws Exception {
        String jwt = token("WEB");
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
        mvc.perform(get("/error").with(bearer("WEB"))).andExpect(status().isForbidden());
    }
}
