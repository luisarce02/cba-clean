package com.cbclean.incident.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Security boundary of the Incident Service.
 *
 * <p>The Incident Service consumes {@code ReportCreatedEvent} messages from RabbitMQ
 * (broker-authenticated) and exposes a REST API for OPERATOR incident management.</p>
 *
 * <p>What is secured here is:</p>
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} - public.</li>
 *   <li>{@code /actuator/metrics}, {@code /actuator/prometheus} - ROLE_OPERATOR.</li>
 *   <li>{@code /api/v1/incidents/**} - ROLE_OPERATOR.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class IncidentServiceSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceSecurityConfig.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${cbaclean.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain incidentServiceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/prometheus").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/incidents/**").hasRole("OPERATOR")
                        // Unknown/disabled paths keep plain 404 handling instead of security errors.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new RolesClaimAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler(accessDenied()));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = List.of(allowedOrigins.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
        if (origins.isEmpty()) {
            origins = List.of("http://localhost:4200");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
        config.setExposedHeaders(List.of("X-Correlation-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Mirrors the Report Service decoder configuration: exactly one token
     * signature source must be configured (issuer URI with OIDC discovery or
     * a direct JWK Set URI); optional audience validation.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${cbaclean.security.jwt.audience:}") String audience) {

        boolean hasIssuer = issuerUri != null && !issuerUri.isBlank();
        boolean hasJwk = jwkSetUri != null && !jwkSetUri.isBlank();

        if (!hasIssuer && !hasJwk) {
            throw new IllegalStateException(
                    "JWT validation is not configured. Set JWT_ISSUER_URI (recommended) or "
                            + "JWT_JWK_SET_URI; refusing to start without any token signature verification.");
        }

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (hasIssuer) {
            validators.add(new JwtIssuerValidator(issuerUri.trim()));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(new JwtAudienceValidator(audience.trim()));
        }
        DelegatingOAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(validators);

        if (hasJwk) {
            log.info("operation=security-init result=configured mode=jwk-set-uri issuer-validated={}", hasIssuer);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).build();
            decoder.setJwtValidator(validator);
            return decoder;
        }

        log.info("operation=security-init result=configured mode=issuer-uri");
        return new LazyJwtDecoder(() -> {
            NimbusJwtDecoder resolved = NimbusJwtDecoder.withIssuerLocation(issuerUri.trim()).build();
            resolved.setJwtValidator(validator);
            return resolved;
        });
    }

    /** Defers decoder construction (and OIDC discovery) to first token validation. */
    private static final class LazyJwtDecoder implements JwtDecoder {

        private final java.util.function.Supplier<JwtDecoder> factory;
        private volatile JwtDecoder delegate;

        private LazyJwtDecoder(java.util.function.Supplier<JwtDecoder> factory) {
            this.factory = factory;
        }

        @Override
        public org.springframework.security.oauth2.jwt.Jwt decode(String token)
                throws org.springframework.security.oauth2.jwt.JwtException {
            JwtDecoder decoder = this.delegate;
            if (decoder == null) {
                synchronized (this) {
                    decoder = this.delegate;
                    if (decoder == null) {
                        decoder = factory.get();
                        this.delegate = decoder;
                    }
                }
            }
            return decoder.decode(token);
        }
    }

    private AuthenticationEntryPoint unauthorized() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) -> {
            log.info("operation=http-security-rejection result=denied status=401 path={}",
                    request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Bearer");
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Authentication is required to access this resource");
        };
    }

    private AccessDeniedHandler accessDenied() {
        return (HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException accessDeniedException) -> {
            log.info("operation=http-security-rejection result=denied status=403 path={}",
                    request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            writeError(response, HttpStatus.FORBIDDEN, "Forbidden",
                    "You do not have permission to access this resource");
        };
    }

    private static void writeError(HttpServletResponse response,
                                   HttpStatus status,
                                   String error,
                                   String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        MAPPER.writeValue(response.getWriter(), body);
    }

    /** Maps the {@code roles} claim onto {@code ROLE_*} authorities (see README). */
    private static final class RolesClaimAuthenticationConverter
            implements org.springframework.core.convert.converter.Converter<Jwt,
                    org.springframework.security.authentication.AbstractAuthenticationToken> {

        private static final String ROLES_CLAIM = "roles";

        @Override
        public org.springframework.security.authentication.AbstractAuthenticationToken convert(Jwt jwt) {
            Object claim = jwt.getClaim(ROLES_CLAIM);
            List<String> roles = claim instanceof List<?> list
                    ? list.stream().filter(Objects::nonNull).map(Object::toString).toList()
                    : List.<String>of();
            List<GrantedAuthority> authorities = roles.stream()
                    .filter(role -> !role.isBlank())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();
            return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                    jwt, authorities);
        }
    }
}
