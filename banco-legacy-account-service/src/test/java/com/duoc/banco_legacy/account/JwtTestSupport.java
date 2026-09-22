package com.duoc.banco_legacy.account;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

abstract class JwtTestSupport {
    static final KeyPair KEYS = keys();
    static final JwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).privateKey((RSAPrivateKey) KEYS.getPrivate()).build())));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.jwt.issuer", () -> "banco-legacy-auth");
        registry.add("security.jwt.audience", () -> "banco-bff");
    }

    static RequestPostProcessor bearer(String role) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("banco-legacy-auth").subject("test-user")
                .audience(List.of("banco-bff")).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role)).build();
        String token = ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return request -> { request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token); return request; };
    }

    static RequestPostProcessor bearerExpired(String role) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("banco-legacy-auth").subject("test-user")
                .audience(List.of("banco-bff")).issuedAt(now.minusSeconds(600))
                .notBefore(now.minusSeconds(600)).expiresAt(now.minusSeconds(120))
                .claim("roles", List.of(role)).build();
        String token = ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return request -> { request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token); return request; };
    }

    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
