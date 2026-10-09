/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth settings ({@code vkurse.auth.*}). A missing value stops startup.
 *
 * @param privateKey RSA private key that signs our tokens (PKCS#8 PEM, at least 2048 bits); newlines may be written
 *     as \n. The public half is published at {@code /.well-known/jwks.json}
 * @param keyId id of that key ({@code kid}), so other services pick the right public key
 * @param secret HMAC key for OTP code hashes and exchange code hashes; at least 32 characters
 * @param accessTtl access token lifetime. Other services trust a token until it expires, so keep it short
 * @param refreshTtl refresh token lifetime; each refresh extends the session by this much
 * @param webOrigins frontend origins: allowed OAuth callbacks and CORS origins
 * @param apple Sign in with Apple key
 */
@Validated
@ConfigurationProperties("vkurse.auth")
public record AuthProperties(
        @NotBlank String privateKey,
        @NotBlank String keyId,
        @NotNull @Size(min = 32) String secret,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotEmpty List<URI> webOrigins,
        @Valid @NotNull Apple apple) {

    /**
     * Sign in with Apple key, used to sign the client secret. The client id is in the OAuth registration.
     *
     * @param teamId Apple developer team id
     * @param keyId id of the Sign in with Apple key
     * @param privateKey content of the .p8 key (PKCS#8 PEM); newlines may be written as \n
     */
    public record Apple(
            @NotBlank String teamId,
            @NotBlank String keyId,
            @NotBlank String privateKey) {}
}
