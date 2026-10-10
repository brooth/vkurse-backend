/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

public final class BillingErrors {

    private BillingErrors() {}

    /** 402 {@code billing/payment-required}: the school owes Vkurse, so it can't upload until it tops up. */
    public static class PaymentRequiredException extends ClientException {
        public PaymentRequiredException() {
            super(
                    HttpStatus.PAYMENT_REQUIRED,
                    "billing/payment-required",
                    "The school's balance is negative: top it up to upload files");
        }
    }

    /**
     * 402 {@code billing/suspended}: the school has owed Vkurse for longer than the grace period, so its files and
     * videos can't be opened until it tops up.
     */
    public static class SuspendedException extends ClientException {
        public SuspendedException() {
            super(
                    HttpStatus.PAYMENT_REQUIRED,
                    "billing/suspended",
                    "The school's files are unavailable until it tops up its balance");
        }
    }
}
