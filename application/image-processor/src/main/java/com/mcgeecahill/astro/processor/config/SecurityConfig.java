package com.mcgeecahill.astro.processor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the Image Processor service. Configures basic authentication and
 * disables CSRF for API endpoints.
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
                                authz.requestMatchers(
                                                new AntPathRequestMatcher("/actuator/health"),
                                                new AntPathRequestMatcher("/actuator/prometheus"))
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // Enable HTTP Basic authentication
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
