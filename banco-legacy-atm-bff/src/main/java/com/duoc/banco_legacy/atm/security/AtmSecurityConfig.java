package com.duoc.banco_legacy.atm.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AtmSecurityConfig {
    @Bean
    SecurityFilterChain atmSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/atm/**").hasRole("ATM")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    UserDetailsService atmUsers() {
        return new InMemoryUserDetailsManager(
                User.withUsername("web-user").password("{noop}web-pass").roles("WEB").build(),
                User.withUsername("mobile-user").password("{noop}mobile-pass").roles("MOBILE").build(),
                User.withUsername("atm-user").password("{noop}atm-pass").roles("ATM").build());
    }
}
