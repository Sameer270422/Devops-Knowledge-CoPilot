package com.copilot.config;

import com.copilot.auth.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Baseline security posture:
 * - Stateless sessions — auth is carried per-request by the JWT filter, not a server session.
 * - Default-deny: every endpoint requires authentication except health checks and auth routes.
 * - Method-level security enabled so services can use @PreAuthorize for resource-ownership checks.
 * - CORS is an explicit allowlist of the frontend origin — never "*" — because the API also
 *   accepts a credentialed cookie (the refresh token), and wildcard origin + credentials is
 *   rejected by browsers anyway (and would be dangerous if it weren't).
 * - CSRF is disabled: the access token lives in a header (immune to CSRF by construction), and
 *   the one cookie-authenticated flow (refresh/logout) uses SameSite=Strict, which browsers
 *   already refuse to attach cross-site — a second CSRF layer would be redundant here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String allowedOrigin;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            // Spring Security's defaults already cover X-Content-Type-Options: nosniff,
            // X-Frame-Options: DENY, and (on HTTPS requests) Strict-Transport-Security —
            // this only adds what isn't already on by default. CSP mainly guards against
            // Spring Boot's own HTML error pages (e.g. an unmapped path's Whitelabel Error
            // Page) rather than JSON API responses, but it's essentially free defense in
            // depth and matches what frontend/nginx.conf sets for the pages that actually
            // render in a browser.
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true); // required so the refresh-token cookie is sent
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
