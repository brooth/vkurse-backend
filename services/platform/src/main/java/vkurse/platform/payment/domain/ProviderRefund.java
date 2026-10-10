/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

/** A refund as the provider has it. */
public record ProviderRefund(String id, State state) {

    public enum State {
        PENDING,
        SUCCEEDED,
        CANCELED
    }
}
