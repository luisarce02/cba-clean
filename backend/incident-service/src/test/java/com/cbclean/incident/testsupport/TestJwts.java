package com.cbclean.incident.testsupport;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Test support: generates an in-memory RSA key pair, exposes a
 * {@link NimbusJwtDecoder} validating against the matching public key, and
 * mints signed JWTs with a {@code roles} claim - no external identity
 * provider required. Test-only.
 */
public final class TestJwts {

    public static final String KEY_ID = "test-key";

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwts() {
    }

    /** Decoder accepting only tokens signed with the test key pair. */
    public static NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
    }

    /** A signed, non-expired token for the given roles (claim: {@code roles}). */
    public static String token(String subject, List<String> roles) {
        return token(subject, roles, Instant.now().plusSeconds(3600));
    }

    /** A signed token expiring at the given instant. */
    public static String token(String subject, List<String> roles, Instant expiresAt) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KEY_ID)
                    .build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .subject(subject)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiresAt))
                    .claim("roles", roles)
                    .build();
            SignedJWT signed = new SignedJWT(header, claims);
            signed.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint test JWT", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }
}
