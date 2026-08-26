package com.cbclean.report.presentation.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Defers construction of the real {@link JwtDecoder} until the first token is
 * decoded.
 *
 * <p>Necessary because building a decoder from an OIDC issuer URI resolves
 * the provider's discovery document eagerly, which would prevent the service
 * from starting whenever the issuer is temporarily unreachable. With this
 * wrapper the service always starts; tokens simply cannot be validated (401)
 * until the issuer becomes reachable.</p>
 */
final class LazyJwtDecoder implements JwtDecoder {

    private final DecoderFactory factory;
    private volatile JwtDecoder delegate;

    @FunctionalInterface
    interface DecoderFactory {
        JwtDecoder create();
    }

    LazyJwtDecoder(DecoderFactory factory) {
        this.factory = factory;
    }

    @Override
    public org.springframework.security.oauth2.jwt.Jwt decode(String token) throws JwtException {
        JwtDecoder decoder = this.delegate;
        if (decoder == null) {
            synchronized (this) {
                decoder = this.delegate;
                if (decoder == null) {
                    decoder = factory.create();
                    this.delegate = decoder;
                }
            }
        }
        return decoder.decode(token);
    }
}
