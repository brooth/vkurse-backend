/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import ru.vkurse.core.auth.AuthProperties;
import ru.vkurse.core.auth.domain.SecretHasher;

/** HMAC-SHA256 with {@code vkurse.auth.secret}, as hex. */
@Component
class HmacSecretHasher implements SecretHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    HmacSecretHasher(AuthProperties properties) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String hash(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    @Override
    public String randomSecret() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String randomDigits(int length) {
        var digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        return digits.toString();
    }
}
