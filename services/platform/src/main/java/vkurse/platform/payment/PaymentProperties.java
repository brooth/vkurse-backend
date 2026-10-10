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
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Payment settings ({@code vkurse.payment.*}). A missing value stops startup.
 *
 * @param encryptionKey key that encrypts merchants' credentials in the database: 32 random bytes in Base64. Losing it
 *     loses every merchant's credentials; changing it needs them connected again
 * @param yookassa YooKassa API
 * @param platformShop Vkurse's own shop, which schools pay Vkurse through
 */
@Validated
@ConfigurationProperties("vkurse.payment")
public record PaymentProperties(
        @NotBlank String encryptionKey,
        @Valid @NotNull Yookassa yookassa,
        @Valid @NotNull PlatformShop platformShop) {

    /**
     * @param url base URL of the YooKassa API
     */
    public record Yookassa(@NotNull URI url) {}

    /**
     * @param shopId id of Vkurse's own YooKassa shop
     * @param secretKey secret key of that shop; a key that starts with {@code test_} is a test shop's
     * @param receiptsEnabled whether YooKassa issues a 54-FZ receipt for each payment to the shop; turn it on when the
     *     shop has receipts set up
     * @param receiptsVatCode YooKassa's VAT code for those receipts (1 to 12); used only when receipts are on
     */
    public record PlatformShop(
            @NotBlank String shopId,
            @NotBlank String secretKey,
            boolean receiptsEnabled,
            @Nullable Integer receiptsVatCode) {}
}
