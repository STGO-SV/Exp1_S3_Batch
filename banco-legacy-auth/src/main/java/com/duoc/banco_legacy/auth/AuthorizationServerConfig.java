package com.duoc.banco_legacy.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthorizationServerConfig {
    private static final Map<String, String> CLIENT_ROLES = Map.of(
            "banco-web-bff", "WEB",
            "banco-mobile-bff", "MOBILE",
            "banco-atm-bff", "ATM",
            "banco-domain-operator", "DOMAIN_OPERATOR",
            "banco-payment-operator", "PAYMENT_OPERATOR");

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain fallbackFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${oauth.clients.web.secret}") String webSecret,
            @Value("${oauth.clients.mobile.secret}") String mobileSecret,
            @Value("${oauth.clients.atm.secret}") String atmSecret,
            @Value("${oauth.clients.domain.secret:}") String domainSecret,
            @Value("${oauth.clients.payment.secret:}") String paymentSecret,
            @Value("${security.jwt.ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds < 30 || ttlSeconds > 900) {
            throw new IllegalArgumentException("Token TTL must be 30..900 seconds");
        }
        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(ttlSeconds))
                .build();
        var clients = new java.util.ArrayList<RegisteredClient>(List.of(
                client("banco-web-bff", webSecret, "accounts.web", passwordEncoder, tokenSettings),
                client("banco-mobile-bff", mobileSecret, "accounts.mobile", passwordEncoder, tokenSettings),
                client("banco-atm-bff", atmSecret, "accounts.atm", passwordEncoder, tokenSettings)));
        if (!domainSecret.isBlank()) {
            var domain = client("banco-domain-operator", domainSecret, "accounts.read", passwordEncoder, tokenSettings);
            clients.add(RegisteredClient.from(domain).scopes(scopes -> scopes.addAll(
                    List.of("accounts.write", "customers.read", "customers.write"))).build());
        }
        if (!paymentSecret.isBlank()) {
 var payment=client("banco-payment-operator",paymentSecret,"payments.write",passwordEncoder,tokenSettings);
 clients.add(RegisteredClient.from(payment).scopes(scopes -> scopes.addAll(
  List.of("payments.read","accounts.post","accounts.post.read"))).build());
}
return new InMemoryRegisteredClientRepository(clients);
    }

    private RegisteredClient client(String clientId, String secret, String scope,
            PasswordEncoder passwordEncoder, TokenSettings tokenSettings) {
        validateSecret(clientId, secret);
        return RegisteredClient.withId(UUID.nameUUIDFromBytes(clientId.getBytes(StandardCharsets.UTF_8)).toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(scope)
                .tokenSettings(tokenSettings)
                .build();
    }

    private void validateSecret(String clientId, String secret) {
        int bytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 16 || bytes > 72) {
            throw new IllegalArgumentException("OAuth client secret for " + clientId + " must contain 16 to 72 UTF-8 bytes");
        }
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${security.jwt.private-key}") String privateValue,
            @Value("${security.jwt.public-key}") String publicValue) throws Exception {
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateValue)));
        var publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue)));
        if (publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())
                || !publicKey.getPublicExponent().equals(privateKey.getPublicExponent())) {
            throw new IllegalArgumentException("Invalid RSA key pair");
        }
        String keyId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${security.jwt.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            @Value("${security.jwt.audience}") String audience) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            String clientId = context.getRegisteredClient().getClientId();
            String role = CLIENT_ROLES.get(clientId);
            if (role == null) {
                throw new IllegalStateException("No role mapping exists for OAuth client " + clientId);
            }
            context.getClaims()
                    .subject(clientId)
                    .audience(List.of(audience))
                    .notBefore(Instant.now())
                    .claim("roles", List.of(role));
        };
    }
}
