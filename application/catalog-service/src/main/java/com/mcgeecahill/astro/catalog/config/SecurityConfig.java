package com.mcgeecahill.astro.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Catalog service. Configures basic authentication and disables CSRF
 * for REST APIs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF - REST APIs using token/basic auth are not vulnerable to CSRF
                .csrf(csrf -> csrf.disable())
                // Configure authorization
                .authorizeHttpRequests(
                        authz ->
                                authz.requestMatchers("/actuator/health", "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
