/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Payment settings ({@code vkurse.payment.*}). A missing value stops startup.
 *
 * @param encryptionKey key that encrypts merchants' credentials in the database: 32 random bytes in Base64. Losing it
 *     loses every merchant's credentials; changing it needs them connected again
 * @param yookassa YooKassa API
 */
@Validated
@ConfigurationProperties("vkurse.payment")
public record PaymentProperties(
        @NotBlank String encryptionKey, @Valid @NotNull Yookassa yookassa) {

    /**
     * @param url base URL of the YooKassa API
     */
    public record Yookassa(@NotNull URI url) {}
}
