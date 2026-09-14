package com.duoc.banco_legacy.auth;

import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "server.ssl.enabled=false")
@AutoConfigureMockMvc
class AuthApplicationTests {
    static final KeyPair KEYS = keys();
    static final String PASSWORD = UUID.randomUUID().toString();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static KeyPair keys() {
        try { var g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); return g.generateKeyPair(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        r.add("security.jwt.private-key", () -> Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        r.add("demo.web-password", () -> PASSWORD);
        r.add("demo.mobile-password", () -> PASSWORD);
        r.add("demo.atm-password", () -> PASSWORD);
    }
    @ParameterizedTest @ValueSource(strings={"web","mobile","atm"})
    void authenticatesAndSignsEachChannel(String channel) throws Exception {
        var result = mvc.perform(post("/auth/token").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",channel+"-user","password",PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$.token_type").value("Bearer")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        var jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build().decode(body.get("access_token").asText());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("banco-legacy-auth");
        assertThat(jwt.getAudience()).containsExactly("banco-bff");
        assertThat(jwt.getSubject()).isEqualTo(channel+"-user");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(channel.toUpperCase());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(300));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims()).doesNotContainKeys("password");
    }
    @Test void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/auth/token").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username","web-user","password",UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    @Test void unknownUserIs401() throws Exception {
        mvc.perform(post("/auth/token").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username","unknown","password",PASSWORD))))
                .andExpect(status().isUnauthorized());
    }
    @Test void missingCredentialsIs400() throws Exception {
        mvc.perform(post("/auth/token").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
}
