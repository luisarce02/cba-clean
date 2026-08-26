package com.cbclean.report.presentation.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Security boundary of the Report Service: JWT Bearer authentication via
 * Spring Security's OAuth2 resource server support.
 *
 * <p>Authorization model (roles claim mapped to {@code ROLE_*} authorities by
 * {@link RolesClaimAuthenticationConverter}):</p>
 * <ul>
 *   <li>{@code REPORTER} - create and retrieve waste reports.</li>
 *   <li>{@code OPERATOR} - operational access (actuator metrics/prometheus,
 *   future operational endpoints).</li>
 * </ul>
 *
 * <p>Endpoint policy:</p>
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} - public.</li>
 *   <li>{@code /actuator/metrics}, {@code /actuator/prometheus} - ROLE_OPERATOR.</li>
 *   <li>{@code /api/v1/reports/**} - ROLE_REPORTER or ROLE_OPERATOR.</li>
 * </ul>
 *
 * <p>Unknown paths fall through to normal MVC dispatch so they keep returning
 * plain 404s exactly as before; every real endpoint is covered by an explicit
 * matcher above. Sensitive actuator endpoints stay unavailable because they
 * are not exposed at all ({@code management.endpoints.web.exposure.include}).</p>
 *
 * <p>Stateless: CSRF is disabled (Bearer tokens only), sessions are never
 * created, and CORS is configured via {@code cbaclean.cors.allowed-origins}
 * to support the Angular development frontend while keeping production
 * locked down.</p>
 */
@Configuration
@EnableWebSecurity
public class ReportServiceSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceSecurityConfig.class);

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RolesClaimAuthenticationConverter rolesConverter;
    private final List<String> allowedOrigins;

    public ReportServiceSecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                                       RestAccessDeniedHandler accessDeniedHandler,
                                       RolesClaimAuthenticationConverter rolesConverter,
                                       @Value("${cbaclean.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.rolesConverter = rolesConverter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain reportServiceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Error/error-page dispatches must reach /error so that
                        // unknown or disabled endpoints keep rendering their
                        // original status (404) instead of a security rejection.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR,
                                jakarta.servlet.DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/prometheus").hasRole("OPERATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/reports").hasRole("REPORTER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/reports/*").hasAnyRole("REPORTER", "OPERATOR")
                        // Any other method on the API surface is explicitly
                        // denied rather than falling through.
                        .requestMatchers("/api/v1/reports/**").denyAll()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = allowedOrigins.stream()
                .filter(o -> o != null && !o.isBlank())
                .toList();
        if (origins.isEmpty()) {
            log.info("operation=cors-init result=no-allowed-origins");
        } else {
            config.setAllowedOrigins(origins);
            log.info("operation=cors-init result=configured origins={}", origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
        config.setExposedHeaders(List.of("X-Correlation-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * JWT decoder with externalized key material configuration. Exactly one
     * source must be configured:
     * <ul>
     *   <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
     *   (env {@code JWT_ISSUER_URI}) - full OIDC discovery; signature,
     *   expiry and issuer are validated, JWKS resolution is lazy so the
     *   service starts even while the issuer is temporarily unreachable;</li>
     *   <li>or {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     *   (env {@code JWT_JWK_SET_URI}) - direct JWKS endpoint.</li>
     * </ul>
     * When {@code cbaclean.security.jwt.audience} (env {@code JWT_AUDIENCE})
     * is set, the {@code aud} claim must contain that audience.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${" + ReportServiceSecurityConfig.ISSUER_URI_PROPERTY + ":}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${cbaclean.security.jwt.audience:}") String audience) {

        if ((issuerUri == null || issuerUri.isBlank()) && (jwkSetUri == null || jwkSetUri.isBlank())) {
            throw new IllegalStateException(
                    "JWT validation is not configured. Set JWT_ISSUER_URI (recommended) or "
                            + "JWT_JWK_SET_URI; refusing to start without any token signature verification.");
        }
        boolean useIssuer = issuerUri != null && !issuerUri.isBlank();
        if (useIssuer) {
            log.info("operation=security-init result=configured mode=issuer-uri");
        } else {
            log.info("operation=security-init result=configured mode=jwk-set-uri");
        }

        // Issuer-based decoders resolve the OIDC discovery document eagerly;
        // wrap them so the service starts (and keeps serving public endpoints)
        // even while the issuer is temporarily unreachable.
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (useIssuer) {
            validators.add(new JwtIssuerValidator(issuerUri.trim()));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(new JwtAudienceValidator(audience.trim()));
        }

        JwtDecoder decoder = useIssuer
                ? new LazyJwtDecoder(() -> {
                    NimbusJwtDecoder resolved = NimbusJwtDecoder.withIssuerLocation(issuerUri.trim()).build();
                    resolved.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
                    return resolved;
                })
                : NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).build();
        if (!useIssuer) {
            ((NimbusJwtDecoder) decoder).setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        }
        return decoder;
    }

    static final String ISSUER_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
}
