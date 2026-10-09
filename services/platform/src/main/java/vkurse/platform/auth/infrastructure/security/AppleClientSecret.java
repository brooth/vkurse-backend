/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import vkurse.platform.auth.AuthProperties;

/**
 * Apple's {@code client_secret}: a JWT signed (ES256) with the team's {@code .p8} key. Apple accepts one valid for up
 * to 6 months; we sign one valid for 30 days and replace it daily.
 *
 * <p>The key is parsed on first use, so the service starts without real Apple credentials (local development); only Apple
 * sign-in fails then.
 */
@Component
class AppleClientSecret {
    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration LIFETIME = Duration.ofDays(30);
    private static final Duration RENEW_AFTER = Duration.ofDays(1);

    private final AuthProperties.Apple apple;
    private final Clock clock;

    private @Nullable String secret;
    private @Nullable String secretClientId;
    private Instant issuedAt = Instant.EPOCH;

    AppleClientSecret(AuthProperties properties, Clock clock) {
        this.apple = properties.apple();
        this.clock = clock;
    }

    synchronized String get(String clientId) {
        var now = clock.instant();
        if (secret == null || !clientId.equals(secretClientId) || now.isAfter(issuedAt.plus(RENEW_AFTER))) {
            secret = sign(clientId, now);
            secretClientId = clientId;
            issuedAt = now;
        }
        return secret;
    }

    private String sign(String clientId, Instant now) {
        var header =
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(apple.keyId()).build();
        var claims = new JWTClaimsSet.Builder()
                .issuer(apple.teamId())
                .subject(clientId)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .build();
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new ECDSASigner(privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign the Apple client secret", e);
        }
        return jwt.serialize();
    }

    private ECPrivateKey privateKey() {
        var pem = apple.privateKey()
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem));
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("APPLE_PRIVATE_KEY is not a valid .p8 EC private key", e);
        }
    }
}
