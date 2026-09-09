package com.duoc.banco_legacy.mobile.security;

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
public class MobileSecurityConfig {
    @Bean
    SecurityFilterChain mobileSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/mobile/**").hasRole("MOBILE")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    UserDetailsService mobileUsers() {
        return new InMemoryUserDetailsManager(
                User.withUsername("web-user").password("{noop}web-pass").roles("WEB").build(),
                User.withUsername("mobile-user").password("{noop}mobile-pass").roles("MOBILE").build(),
                User.withUsername("atm-user").password("{noop}atm-pass").roles("ATM").build());
    }
}
