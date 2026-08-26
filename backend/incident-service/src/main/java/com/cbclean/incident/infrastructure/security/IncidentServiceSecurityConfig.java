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
 * <p>The Incident Service exposes <strong>no REST API</strong> - it consumes
 * {@code ReportCreatedEvent} messages from RabbitMQ. RabbitMQ messages are
 * authenticated by the broker connection, not by JWTs; nothing about HTTP
 * security changes that flow.</p>
 *
 * <p>What is secured here is the Actuator surface:</p>
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} - public.</li>
 *   <li>{@code /actuator/metrics}, {@code /actuator/prometheus} -
 *   {@code ROLE_OPERATOR}.</li>
 * </ul>
 *
 * <p>Same resource-server setup as the Report Service: externalized issuer
 * ({@code JWT_ISSUER_URI}) or JWKS endpoint ({@code JWT_JWK_SET_URI}),
 * optional audience ({@code JWT_AUDIENCE}), roles claim convention,
 * stateless, CSRF disabled, no sessions, CORS disabled.</p>
 */
@Configuration
@EnableWebSecurity
public class IncidentServiceSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceSecurityConfig.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public SecurityFilterChain incidentServiceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/prometheus").hasRole("OPERATOR")
                        // No other endpoints exist; unknown/disabled paths keep
                        // their plain 404 handling instead of security errors.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new RolesClaimAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized()));
        return http.build();
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

        if ((issuerUri == null || issuerUri.isBlank()) && (jwkSetUri == null || jwkSetUri.isBlank())) {
            throw new IllegalStateException(
                    "JWT validation is not configured. Set JWT_ISSUER_URI (recommended) or "
                            + "JWT_JWK_SET_URI; refusing to start without any token signature verification.");
        }
        boolean useIssuer = issuerUri != null && !issuerUri.isBlank();
        log.info("operation=security-init result=configured mode={}",
                useIssuer ? "issuer-uri" : "jwk-set-uri");

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (useIssuer) {
            validators.add(new JwtIssuerValidator(issuerUri.trim()));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(new JwtAudienceValidator(audience.trim()));
        }

        if (useIssuer) {
            // Issuer-based decoders resolve the OIDC discovery document
            // eagerly; defer that until the first token arrives so the service
            // always starts.
            return new LazyJwtDecoder(() -> {
                NimbusJwtDecoder resolved = NimbusJwtDecoder.withIssuerLocation(issuerUri.trim()).build();
                resolved.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
                return resolved;
            });
        }
        NimbusJwtDecoder direct = NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).build();
        direct.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return direct;
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
