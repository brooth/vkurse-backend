/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

/**
 * What a provider's notification says, as far as we read it: that something happened to an object of theirs. It is
 * never trusted further: we ask the provider for the object's state.
 *
 * @param event the provider's name of what happened ({@code payment.succeeded})
 * @param objectId the provider's id of the payment or the refund
 */
public record ProviderNotification(String event, Kind kind, String objectId) {

    public enum Kind {
        PAYMENT,
        REFUND,
        /** Something we don't follow. */
        OTHER
    }
}
