package com.duoc.banco_legacy.auth;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyFactory;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthConfig {
    @Bean
    SecurityFilterChain authFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/token").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(@Value("${demo.web-password}") String web,
                                                @Value("${demo.mobile-password}") String mobile,
                                                @Value("${demo.atm-password}") String atm) {
        var encoder = new BCryptPasswordEncoder();
        for (String password : new String[]{web, mobile, atm}) {
            if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("Demo passwords must contain 12 to 72 UTF-8 bytes");
        }
        var users = new InMemoryUserDetailsManager(
                User.withUsername("web-user").password(encoder.encode(web)).roles("WEB").build(),
                User.withUsername("mobile-user").password(encoder.encode(mobile)).roles("MOBILE").build(),
                User.withUsername("atm-user").password(encoder.encode(atm)).roles("ATM").build());
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${security.jwt.private-key}") String privateValue,
                          @Value("${security.jwt.public-key}") String publicValue) throws Exception {
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateValue)));
        var publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue)));
        if (publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())
                || !publicKey.getPublicExponent().equals(privateKey.getPublicExponent()))
            throw new IllegalArgumentException("Invalid RSA key pair");
        var key = new com.nimbusds.jose.jwk.RSAKey.Builder(publicKey).privateKey(privateKey).keyID("local-demo").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
