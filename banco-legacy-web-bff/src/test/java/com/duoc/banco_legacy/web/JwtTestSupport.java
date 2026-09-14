package com.duoc.banco_legacy.web;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

abstract class JwtTestSupport {
    static final KeyPair KEYS = keys();
    static final JwtEncoder ENCODER = encoder(KEYS);
    static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static JwtEncoder encoder(KeyPair pair) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).build())));
    }
    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.jwt.issuer", () -> "banco-legacy-auth");
        registry.add("security.jwt.audience", () -> "banco-bff");
    }
    static String token(String role) { return token(role, "valid"); }
    static String token(String role, String scenario) {
        var now = Instant.now();
        var builder = JwtClaimsSet.builder().issuer(scenario.equals("issuer") ? "wrong" : "banco-legacy-auth")
                .subject("test-user").audience(List.of(scenario.equals("audience") ? "wrong" : "banco-bff"))
                .issuedAt(now.minusSeconds(600)).notBefore(scenario.equals("future") ? now.plusSeconds(600) : now.minusSeconds(600))
                .claim("roles", scenario.equals("roles-type") ? 42 : List.of(role));
        if (!scenario.equals("missing-exp")) builder.expiresAt(scenario.equals("expired") ? now.minusSeconds(120) : now.plusSeconds(300));
        return (scenario.equals("signature") ? encoder(keys()) : ENCODER).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), builder.build())).getTokenValue();
    }
    static RequestPostProcessor bearer(String role) {
        return request -> { request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)); return request; };
    }
    static HttpEntity<Void> entity(String role) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token(role)); return new HttpEntity<>(headers);
    }
}
