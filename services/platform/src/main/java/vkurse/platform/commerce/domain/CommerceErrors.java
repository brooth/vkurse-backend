/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

/** The commerce module's client errors. */
public final class CommerceErrors {

    private CommerceErrors() {}

    /** 409 {@code commerce/already-bought}: the buyer has paid for this course already. */
    public static class AlreadyBoughtException extends ClientException {
        public AlreadyBoughtException() {
            super(HttpStatus.CONFLICT, "commerce/already-bought", "You already have this course");
        }
    }

    /** 409 {@code commerce/offer-unavailable}: the offer is not on sale, or its course is not published. */
    public static class OfferUnavailableException extends ClientException {
        public OfferUnavailableException() {
            super(HttpStatus.CONFLICT, "commerce/offer-unavailable", "This offer is not on sale");
        }
    }

    /**
     * 400 {@code commerce/promo-code-invalid}: the code can't be used for this offer. Whether it is unknown, switched
     * off, expired, used up or meant for other offers is not said, so codes can't be probed.
     */
    public static class PromoCodeInvalidException extends ClientException {
        public PromoCodeInvalidException() {
            super(HttpStatus.BAD_REQUEST, "commerce/promo-code-invalid", "This promo code can't be used here");
        }
    }

    /** 409 {@code commerce/promo-code-taken}: the school has this code already. */
    public static class PromoCodeTakenException extends ClientException {
        public PromoCodeTakenException() {
            super(HttpStatus.CONFLICT, "commerce/promo-code-taken", "The school already has this promo code");
        }
    }

    /** 409 {@code commerce/order-not-payable}: the order is not waiting for payment any more. */
    public static class OrderNotPayableException extends ClientException {
        public OrderNotPayableException() {
            super(HttpStatus.CONFLICT, "commerce/order-not-payable", "This order is not waiting for payment");
        }
    }

    /** 429 {@code commerce/too-many}: the course has too many offers, or the school too many promo codes. */
    public static class TooManyException extends ClientException {
        public TooManyException(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, "commerce/too-many", message);
        }
    }
}
