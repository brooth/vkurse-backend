/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

/** The payment module's client errors. */
public final class PaymentErrors {

    private PaymentErrors() {}

    /** 400 {@code payment/merchant-rejected}: the provider doesn't accept the merchant's credentials. */
    public static class MerchantRejectedException extends ClientException {
        public MerchantRejectedException() {
            super(
                    HttpStatus.BAD_REQUEST,
                    "payment/merchant-rejected",
                    "The payment provider doesn't accept these credentials");
        }
    }

    /** 400 {@code payment-failed}: the provider refused to create the payment or the refund. */
    public static class PaymentFailedException extends ClientException {
        public PaymentFailedException() {
            super(HttpStatus.BAD_REQUEST, "payment-failed", "Payment failed");
        }

        protected PaymentFailedException(String code, String message) {
            super(HttpStatus.BAD_REQUEST, code, message);
        }
    }

    /** 400 {@code payment-failed/low-amount}: the amount is below what the provider takes. */
    public static class LowAmountException extends PaymentFailedException {
        public LowAmountException() {
            super("payment-failed/low-amount", "The amount is smaller than the payment provider's minimum");
        }
    }

    /** 409 {@code payment/merchant-unavailable}: the merchant doesn't take payments now. */
    public static class MerchantUnavailableException extends ClientException {
        public MerchantUnavailableException() {
            super(HttpStatus.CONFLICT, "payment/merchant-unavailable", "This payment method is not available now");
        }
    }

    /** 409 {@code payment/not-refundable}: the payment isn't paid, or less is left of it than asked back. */
    public static class NotRefundableException extends ClientException {
        public NotRefundableException(String message) {
            super(HttpStatus.CONFLICT, "payment/not-refundable", message);
        }
    }

    /** 429 {@code payment/too-many-merchants}: the school has as many merchants as one may have. */
    public static class TooManyMerchantsException extends ClientException {
        public TooManyMerchantsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "payment/too-many-merchants", "The school has too many merchants");
        }
    }
}
