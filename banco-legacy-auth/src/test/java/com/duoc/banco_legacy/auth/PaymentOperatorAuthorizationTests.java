package com.duoc.banco_legacy.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"server.ssl.enabled=false","oauth.clients.payment.secret=payment-client-secret-for-tests"})
@AutoConfigureMockMvc
class PaymentOperatorAuthorizationTests {
    private static final java.security.KeyPair KEYS=AuthApplicationTests.keys();
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        AuthApplicationTests.properties(registry);
        registry.add("security.jwt.public-key",()->java.util.Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.jwt.private-key",()->java.util.Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
    }
    @Test void operatorReceivesExplicitScopesAndTechnicalIdentity() throws Exception {
        var response=mvc.perform(post("/oauth2/token").with(httpBasic("banco-payment-operator","payment-client-secret-for-tests"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type","client_credentials")
                .param("scope","payments.read payments.write accounts.post accounts.post.read"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var token=json.readTree(response).get("access_token").asText();
        var jwt=NimbusJwtDecoder.withPublicKey((RSAPublicKey)KEYS.getPublic()).build().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("banco-payment-operator");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PAYMENT_OPERATOR");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactlyInAnyOrder(
                "payments.read","payments.write","accounts.post","accounts.post.read");
        assertThat(jwt.getClaims()).doesNotContainKeys("customer_id","rut","email");
    }
    @Test void scopesStaySeparatedFromChannelsAndFuturePayment() throws Exception {
        for(String scope:new String[]{"accounts.web","accounts.write","customers.write"}) {
            mvc.perform(post("/oauth2/token").with(httpBasic("banco-payment-operator","payment-client-secret-for-tests"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type","client_credentials").param("scope",scope))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/oauth2/token").with(httpBasic("banco-web-bff","web-client-secret-for-tests"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type","client_credentials")
                .param("scope","accounts.write")).andExpect(status().isBadRequest());
    }
}