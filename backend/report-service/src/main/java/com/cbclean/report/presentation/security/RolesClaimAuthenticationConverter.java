package com.cbclean.report.presentation.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps the {@code roles} JWT claim (a JSON array such as
 * {@code ["REPORTER","OPERATOR"]}) onto Spring Security authorities named
 * {@code ROLE_REPORTER} / {@code ROLE_OPERATOR}.
 *
 * <p>Roles are the single authorization convention of CBA Clean; the standard
 * OAuth2 {@code scope} claim is intentionally not used. This converter is the
 * only security-specific mapping logic in the codebase and lives entirely at
 * the security boundary - nothing below the presentation layer ever sees a
 * JWT.</p>
 */
@Component
public class RolesClaimAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ROLES_CLAIM = "roles";
    static final String AUTHORITY_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = rolesOf(jwt).stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> new SimpleGrantedAuthority(AUTHORITY_PREFIX + role.trim().toUpperCase()))
                .map(GrantedAuthority.class::cast)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Jwt jwt) {
        Object roles = jwt.getClaim(ROLES_CLAIM);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }
}
