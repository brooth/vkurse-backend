/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;

/**
 * Money of a payment given back to the payer, in whole or in part. Made only by {@link Payment#refund}, which keeps
 * the refunds of a payment within its amount.
 */
@Entity
@Table(name = "payment_refunds")
public class Refund extends AggregateRoot {
    private UUID paymentId;

    private UUID merchantId;

    @Embedded
    private Money amount;

    private @Nullable String reason;

    /** The user of the school's staff who asked for it. */
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** The provider's id of the refund; {@code null} until the provider took it. */
    private @Nullable String providerRefundId;

    protected Refund() {}

    private Refund(Payment payment, Money amount, @Nullable String reason, UUID requestedBy, Instant now) {
        super(now);
        this.paymentId = payment.getId();
        this.merchantId = payment.getMerchantId();
        this.amount = amount;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    static Refund request(Payment payment, Money amount, @Nullable String reason, UUID requestedBy, Instant now) {
        return new Refund(payment, amount, reason, requestedBy, now);
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Money getAmount() {
        return amount;
    }

    public @Nullable String getReason() {
        return reason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable String getProviderRefundId() {
        return providerRefundId;
    }

    public boolean belongsTo(UUID paymentId) {
        return this.paymentId.equals(paymentId);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isSucceeded() {
        return status == Status.SUCCEEDED;
    }

    /** What we ask the provider to give back. */
    public RefundOrder order(String providerPaymentId) {
        return new RefundOrder(getId(), providerPaymentId, amount, reason);
    }

    /** Takes the provider's word on the refund. Called by {@link Payment#settle}. */
    void apply(ProviderRefund state) {
        providerRefundId = state.id();
        status = switch (state.state()) {
            case PENDING -> Status.PENDING;
            case SUCCEEDED -> Status.SUCCEEDED;
            case CANCELED -> Status.CANCELED;
        };
    }

    /** The provider refused to start the refund. Called by {@link Payment#drop}. */
    void fail() {
        status = Status.CANCELED;
    }

    public enum Status {
        /** Asked of the provider; the outcome is not known yet. */
        PENDING,
        /** The money went back. */
        SUCCEEDED,
        /** The provider didn't give the money back. */
        CANCELED
    }
}
