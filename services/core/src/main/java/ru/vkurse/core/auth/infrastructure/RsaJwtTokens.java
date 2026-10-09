/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import ru.vkurse.common.security.JwtResourceServer;
import ru.vkurse.core.CoreProperties;
import ru.vkurse.core.auth.AuthProperties;
import ru.vkurse.core.auth.domain.TokenIssuer;

/**
 * Our JWTs, signed RS256 with the key in {@code vkurse.auth.private-key}. The claims and their checks are defined in
 * {@link JwtResourceServer}, which every service uses to verify them:
 *
 * <ul>
 *   <li>Access: {@code aud=access}, {@code sub} = user id, {@code sid} = session id, {@code role}.
 *   <li>Refresh: {@code aud=refresh}, {@code sub}, {@code sid}, {@code gen} = the session's refresh generation.
 * </ul>
 *
 * The public half of the key is published as a JWK set, so other services verify access tokens without a shared
 * secret.
 */
@Component
public class RsaJwtTokens implements TokenIssuer {
    private static final Logger log = LoggerFactory.getLogger(RsaJwtTokens.class);
    private static final String GENERATION = "gen";
    private static final int MIN_KEY_BITS = 2048;

    private final String keyId;
    private final String issuer;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder refreshDecoder;
    private final Map<String, Object> publicJwkSet;

    public RsaJwtTokens(AuthProperties properties, CoreProperties core, Clock clock) {
        var privateKey = privateKey(properties.privateKey());
        var publicKey = publicKey(privateKey);
        var jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        this.keyId = properties.keyId();
        this.issuer = core.publicUrl().toString();
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        this.accessDecoder = JwtResourceServer.decoder(publicKey, issuer, JwtResourceServer.ACCESS, clock);
        this.refreshDecoder = JwtResourceServer.decoder(publicKey, issuer, JwtResourceServer.REFRESH, clock);
        // toJSONObject() leaves the private parts out.
        this.publicJwkSet = new JWKSet(jwk).toJSONObject();
    }

    @Override
    public String access(UUID userId, UUID sessionId, String role, Instant expiresAt) {
        var claims =
                claims(JwtResourceServer.ACCESS, userId, sessionId, expiresAt).claim(JwtResourceServer.ROLE, role);
        return encode(claims.build());
    }

    @Override
    public String refresh(UUID userId, UUID sessionId, int generation, Instant expiresAt) {
        var claims =
                claims(JwtResourceServer.REFRESH, userId, sessionId, expiresAt).claim(GENERATION, generation);
        return encode(claims.build());
    }

    @Override
    public Optional<RefreshClaims> parseRefresh(String refreshToken) {
        try {
            var jwt = refreshDecoder.decode(refreshToken);
            var sessionId = jwt.getClaimAsString(JwtResourceServer.SESSION_ID);
            if (sessionId != null && jwt.getClaim(GENERATION) instanceof Number generation) {
                return Optional.of(new RefreshClaims(UUID.fromString(sessionId), generation.intValue()));
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected refresh token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Decodes access tokens for this service's own resource server. */
    public JwtDecoder accessDecoder() {
        return accessDecoder;
    }

    /** The public key as a JWK set, served at {@link JwtResourceServer#JWKS_PATH}. */
    public Map<String, Object> publicJwkSet() {
        return publicJwkSet;
    }

    private JwtClaimsSet.Builder claims(String audience, UUID userId, UUID sessionId, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(clock.instant())
                .expiresAt(expiresAt)
                .claim(JwtResourceServer.SESSION_ID, sessionId.toString());
    }

    private String encode(JwtClaimsSet claims) {
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAPrivateCrtKey privateKey(String pem) {
        var base64 = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
            var key = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            if (key.getModulus().bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException("VKURSE_AUTH_PRIVATE_KEY must have at least " + MIN_KEY_BITS + " bits");
            }
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("VKURSE_AUTH_PRIVATE_KEY is not a valid PKCS#8 RSA private key", e);
        }
    }

    private static RSAPublicKey publicKey(RSAPrivateCrtKey privateKey) {
        try {
            var spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive the public key of VKURSE_AUTH_PRIVATE_KEY", e);
        }
    }
}
