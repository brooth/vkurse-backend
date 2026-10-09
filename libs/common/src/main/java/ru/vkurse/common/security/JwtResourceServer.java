/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.common.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Verifies the tokens the core service issues. All tokens are RS256 with {@code iss} = core's public URL; the
 * {@code aud} claim says what a token is for, so an access token can never be used as a refresh token or the other way
 * round.
 *
 * <p>A service other than core checks access tokens with core's published keys, without calling core per request:
 *
 * <pre>{@code
 * var decoder = JwtResourceServer.decoder(coreUrl + JwtResourceServer.JWKS_PATH, coreUrl, JwtResourceServer.ACCESS, clock);
 * http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(decoder)
 *         .jwtAuthenticationConverter(JwtResourceServer.sessionConverter())));
 * }</pre>
 *
 * Such a service trusts a token until it expires: a sign-out or a role change reaches it with the next access token.
 */
public final class JwtResourceServer {
    /** Where core publishes its public keys (a JWK set). */
    public static final String JWKS_PATH = "/.well-known/jwks.json";

    /** {@code aud} of an access token. */
    public static final String ACCESS = "access";

    /** {@code aud} of a refresh token. Only core accepts these. */
    public static final String REFRESH = "refresh";

    /** Claim with the session id. */
    public static final String SESSION_ID = "sid";

    /** Claim with the user's platform role. */
    public static final String ROLE = "role";

    /** {@link OAuth2Error} code of an expired token, so an entry point can tell it from a bad one. */
    public static final String EXPIRED = "token-expired";

    private JwtResourceServer() {}

    /** A decoder that fetches the signing keys from {@code jwkSetUri} and caches them. */
    public static JwtDecoder decoder(String jwkSetUri, String issuer, String audience, Clock clock) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(issuer, audience, clock));
        return decoder;
    }

    /** A decoder for a service that holds the public key itself (core). */
    public static JwtDecoder decoder(RSAPublicKey publicKey, String issuer, String audience, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(issuer, audience, clock));
        return decoder;
    }

    /** Turns a verified access token into an authentication whose principal is the {@link UserSession}. */
    public static Converter<Jwt, AbstractAuthenticationToken> sessionConverter() {
        return jwt -> {
            var session = session(jwt);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + session.role()));
            return UsernamePasswordAuthenticationToken.authenticated(session, null, authorities);
        };
    }

    /** Reads the caller from a verified access token. */
    public static UserSession session(Jwt jwt) {
        var role = jwt.getClaimAsString(ROLE);
        if (role == null || role.isBlank()) {
            throw new InvalidBearerTokenException("The access token has no role");
        }
        return new UserSession(uuid(jwt.getSubject()), uuid(jwt.getClaimAsString(SESSION_ID)), role);
    }

    private static UUID uuid(@Nullable String value) {
        try {
            if (value != null) {
                return UUID.fromString(value);
            }
        } catch (IllegalArgumentException e) {
            // falls through to the failure below
        }
        throw new InvalidBearerTokenException("The access token has no valid subject or session");
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, Clock clock) {
        return new DelegatingOAuth2TokenValidator<>(
                expiryValidator(clock),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
    }

    /** Like Spring's timestamp validator, but without clock skew and with a code of its own for an expired token. */
    private static OAuth2TokenValidator<Jwt> expiryValidator(Clock clock) {
        return jwt -> {
            var expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(EXPIRED, "The token has expired", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
