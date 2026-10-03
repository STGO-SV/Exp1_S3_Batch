package com.duoc.banco_legacy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "server.ssl.enabled=false")
@AutoConfigureMockMvc
class AuthApplicationTests {
    private static final String ISSUER = "https://localhost:8084";
    private static final String AUDIENCE = "banco-bff";
    private static final KeyPair KEYS = keys();
    private static final Map<String, String> SECRETS = Map.of(
            "banco-web-bff", "web-client-secret-for-tests",
            "banco-mobile-bff", "mobile-client-secret-for-tests",
            "banco-atm-bff", "atm-client-secret-for-tests");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.jwt.private-key", () -> Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        registry.add("security.jwt.issuer", () -> ISSUER);
        registry.add("security.jwt.audience", () -> AUDIENCE);
        registry.add("oauth.clients.web.secret", () -> SECRETS.get("banco-web-bff"));
        registry.add("oauth.clients.mobile.secret", () -> SECRETS.get("banco-mobile-bff"));
        registry.add("oauth.clients.atm.secret", () -> SECRETS.get("banco-atm-bff"));
    }

    static Stream<Arguments> clients() {
        return Stream.of(
                Arguments.of("banco-web-bff", "accounts.web", "WEB"),
                Arguments.of("banco-mobile-bff", "accounts.mobile", "MOBILE"),
                Arguments.of("banco-atm-bff", "accounts.atm", "ATM"));
    }

    @ParameterizedTest
    @MethodSource("clients")
    void clientCredentialsIssuesCompatibleJwt(String clientId, String scope, String role) throws Exception {
        JsonNode response = requestToken(clientId, SECRETS.get(clientId), "client_credentials", scope, 200);

        assertThat(response.get("access_token").asText()).isNotBlank();
        assertThat(response.get("token_type").asText()).isEqualToIgnoringCase("Bearer");
        assertThat(response.get("expires_in").asLong()).isBetween(295L, 300L);
        assertThat(response.get("scope").asText()).isEqualTo(scope);

        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        var jwt = decoder.decode(response.get("access_token").asText());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getHeaders().get("kid")).isNotNull().isNotEqualTo("local-demo");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getSubject()).isEqualTo(clientId);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly(scope);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(role);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getNotBefore()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(300));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims()).doesNotContainKeys("client_secret", "password", "secret");
    }

    @Test
    void wrongSecretReturnsInvalidClient() throws Exception {
        JsonNode response = requestToken("banco-web-bff", "wrong-client-secret-for-tests",
                "client_credentials", "accounts.web", 401);
        assertThat(response.get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    void clientCannotRequestAnotherClientsScope() throws Exception {
        JsonNode response = requestToken("banco-web-bff", SECRETS.get("banco-web-bff"),
                "client_credentials", "accounts.atm", 400);
        assertThat(response.get("error").asText()).isEqualTo("invalid_scope");
    }

    @Test
    void unregisteredGrantReturnsOAuthError() throws Exception {
        JsonNode response = requestToken("banco-web-bff", SECRETS.get("banco-web-bff"),
                "authorization_code", "accounts.web", 400);
        assertThat(response.get("error").asText())
                .isIn("unauthorized_client", "unsupported_grant_type", "invalid_request");
    }

    @Test
    void metadataAdvertisesTokenEndpointAndPublicJwkSet() throws Exception {
        JsonNode metadata = json.readTree(mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(metadata.get("issuer").asText()).isEqualTo(ISSUER);
        assertThat(metadata.get("token_endpoint").asText()).isEqualTo(ISSUER + "/oauth2/token");
        assertThat(metadata.get("grant_types_supported")).anySatisfy(node ->
                assertThat(node.asText()).isEqualTo("client_credentials"));
        String jwksUri = metadata.get("jwks_uri").asText();
        assertThat(jwksUri).startsWith(ISSUER);

        JsonNode jwks = json.readTree(mvc.perform(get(URI.create(jwksUri).getPath()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode key = jwks.get("keys").get(0);
        assertThat(key.get("kty").asText()).isEqualTo("RSA");
        assertThat(key.get("kid").asText()).isNotBlank().isNotEqualTo("local-demo");
        assertThat(key.get("n").asText()).isNotBlank();
        assertThat(key.get("e").asText()).isNotBlank();
        assertThat(key.has("d")).isFalse();
        assertThat(key.has("p")).isFalse();
        assertThat(key.has("q")).isFalse();
    }

    private JsonNode requestToken(String clientId, String secret, String grantType, String scope,
            int expectedStatus) throws Exception {
        var result = mvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, secret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", grantType)
                        .param("scope", scope))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
