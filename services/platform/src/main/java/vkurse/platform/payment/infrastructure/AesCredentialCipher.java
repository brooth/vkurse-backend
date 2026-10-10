/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.infrastructure;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vkurse.platform.payment.PaymentProperties;
import vkurse.platform.payment.domain.CredentialCipher;
import vkurse.platform.payment.domain.MerchantCredentials;

/**
 * Encrypts credentials with AES-256-GCM under the key of {@code vkurse.payment.encryption-key}. A sealed value is
 * {@code v1:} and the Base64 of a random 12-byte nonce followed by the ciphertext with its tag; the version lets the
 * scheme or the key change later. GCM also detects a value that was changed in the database.
 */
@Component
class AesCredentialCipher implements CredentialCipher {
    private static final String VERSION = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKey key;
    private final JsonMapper json;
    private final SecureRandom random = new SecureRandom();

    AesCredentialCipher(PaymentProperties properties, JsonMapper json) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.encryptionKey().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("vkurse.payment.encryption-key must be Base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("vkurse.payment.encryption-key must be " + KEY_BYTES + " bytes in Base64");
        }
        this.key = new SecretKeySpec(bytes, "AES");
        this.json = json;
    }

    @Override
    public String seal(MerchantCredentials credentials) {
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        // Written as the sealed interface, so the JSON names the provider and can be read back.
        var plain = json.writerFor(MerchantCredentials.class).writeValueAsBytes(credentials);
        var sealed = crypt(Cipher.ENCRYPT_MODE, nonce, plain);
        var out = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed);
        return VERSION + Base64.getEncoder().encodeToString(out.array());
    }

    @Override
    public MerchantCredentials open(String sealed) {
        if (!sealed.startsWith(VERSION)) {
            throw new IllegalStateException("Unknown format of sealed credentials");
        }
        var bytes = Base64.getDecoder().decode(sealed.substring(VERSION.length()));
        var nonce = new byte[NONCE_BYTES];
        var buffer = ByteBuffer.wrap(bytes).get(nonce);
        var body = new byte[buffer.remaining()];
        buffer.get(body);
        return json.readValue(crypt(Cipher.DECRYPT_MODE, nonce, body), MerchantCredentials.class);
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            // A wrong key or a changed value; the message never carries the credentials.
            throw new IllegalStateException(
                    "Could not " + (mode == Cipher.ENCRYPT_MODE ? "seal" : "open") + " credentials", e);
        }
    }
}
