package com.duoc.banco_legacy.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TokenController {
    private final AuthenticationManager authentication;
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long ttl;

    public TokenController(AuthenticationManager authentication, JwtEncoder encoder,
                           @Value("${security.jwt.issuer}") String issuer,
                           @Value("${security.jwt.audience}") String audience,
                           @Value("${security.jwt.ttl-seconds}") long ttl) {
        if (ttl < 30 || ttl > 900) throw new IllegalArgumentException("Token TTL must be 30..900 seconds");
        this.authentication = authentication;
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody LoginRequest request) {
        var user = authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        var roles = user.getAuthorities().stream().map(a -> a.getAuthority().substring(5)).toList();
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getName()).audience(List.of(audience))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString()).claim("roles", roles).build();
        var jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("local-demo").build(), claims));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new TokenResponse(jwt.getTokenValue(), "Bearer", ttl));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Credenciales inválidas"));
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("code", "INVALID_REQUEST", "message", "Solicitud de autenticación inválida"));
    }

    public record LoginRequest(@NotBlank @Size(max=120) String username,
                               @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record TokenResponse(String access_token, String token_type, long expires_in) {
        @Override public String toString() { return "TokenResponse[REDACTED]"; }
    }
}
