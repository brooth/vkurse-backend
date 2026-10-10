/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * Whether the provider issues a fiscal receipt (54-FZ) for each payment to a merchant, and with which VAT code. A
 * school turns it on when its shop has receipts set up; a school that issues receipts elsewhere leaves it off.
 *
 * @param vatCode the provider's code of the VAT rate; set exactly when receipts are on
 */
@Embeddable
public record ReceiptSettings(boolean enabled, @Nullable Integer vatCode) {
    public static final int MIN_VAT_CODE = 1;
    public static final int MAX_VAT_CODE = 12;

    public static final ReceiptSettings OFF = new ReceiptSettings(false, null);

    public static ReceiptSettings of(boolean enabled, @Nullable Integer vatCode) {
        if (!enabled) {
            return OFF;
        }
        if (vatCode == null || vatCode < MIN_VAT_CODE || vatCode > MAX_VAT_CODE) {
            throw new BadRequestException("Receipts need a VAT code from " + MIN_VAT_CODE + " to " + MAX_VAT_CODE);
        }
        return new ReceiptSettings(true, vatCode);
    }
}
