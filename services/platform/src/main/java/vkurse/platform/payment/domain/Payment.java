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
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.money.Money;
import vkurse.platform.payment.domain.PaymentErrors.NotRefundableException;

/**
 * One payment of a payer to a merchant. We create it with the provider and then follow it: only what the provider
 * says about it ({@link #apply}) decides whether it was paid. It doesn't know what was bought: whoever started it
 * named a {@code purpose} and its own {@code referenceId}, and gets them back in the events.
 */
@Entity
@Table(name = "payments")
@Getter
public class Payment extends AggregateRoot {
    public static final int MAX_DESCRIPTION_LENGTH = 128;
    public static final int MAX_PURPOSE_LENGTH = 32;
    public static final int MAX_REASON_LENGTH = 250;

    /** The purpose of a payment an owner makes to check a merchant. */
    public static final String TEST_PURPOSE = "merchant-test";

    /** The largest test payment. */
    public static final Money MAX_TEST_AMOUNT = Money.rub(100);

    /** A payment the provider never took is canceled after this long. */
    public static final Duration UNACCEPTED_TTL = Duration.ofHours(1);

    /** A pending payment is checked after as long as it has existed, but not sooner or later than these. */
    public static final Duration MIN_CHECK_PAUSE = Duration.ofMinutes(1);

    public static final Duration MAX_CHECK_PAUSE = Duration.ofMinutes(30);

    /** A payment still pending after this long is no longer checked by the job. */
    public static final Duration GIVE_UP_AFTER = Duration.ofDays(7);

    private UUID merchantId;

    private UUID schoolId;

    /** The user who pays. */
    private UUID payerId;

    @Embedded
    private Money amount;

    /** What the payer sees on the provider's page. */
    private String description;

    private String purpose;

    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** The provider's id of the payment; {@code null} until the provider took it. */
    private @Nullable String providerPaymentId;

    /** The provider's page where the payer pays; only while the payment is pending. */
    private @Nullable String confirmationUrl;

    private String returnUrl;

    private @Nullable Instant paidAt;

    private @Nullable Instant canceledAt;

    /** The provider's word for why the payment was canceled. */
    private @Nullable String cancellationReason;

    /** What refunds have given back, or are giving back, of this payment. */
    @Embedded
    private Money refundedAmount;

    /** When the job next asks the provider about a pending payment; {@code null} once nothing is left to ask. */
    private @Nullable Instant nextCheckAt;

    protected Payment() {}

    private Payment(
            Merchant merchant,
            UUID schoolId,
            UUID payerId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            ReturnUrl returnUrl,
            Instant now) {
        super(now);
        this.merchantId = merchant.getId();
        this.schoolId = schoolId;
        this.payerId = payerId;
        this.amount = amount;
        this.description = description;
        this.purpose = purpose;
        this.referenceId = referenceId;
        this.returnUrl = returnUrl.value().toString();
        this.refundedAmount = new Money(BigDecimal.ZERO, amount.currency());
        this.nextCheckAt = now.plus(MIN_CHECK_PAUSE);
    }

    /** Starts a payment to a merchant that takes payments. It is pending until the provider says otherwise. */
    public static Payment start(
            Merchant merchant,
            UUID payerId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            ReturnUrl returnUrl,
            Instant now) {
        var schoolId = merchant.getSchoolId();
        if (schoolId == null) {
            throw new IllegalArgumentException("A payment to Vkurse's own shop names the school that pays");
        }
        return start(merchant, schoolId, payerId, amount, description, purpose, referenceId, returnUrl, now);
    }

    /**
     * Starts a payment of a school to Vkurse's own shop. It is the paying school's payment: its staff see it as they
     * see the payments to their own merchants.
     *
     * @param schoolId the school that pays
     */
    public static Payment toPlatform(
            Merchant platform,
            UUID schoolId,
            UUID payerId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            ReturnUrl returnUrl,
            Instant now) {
        if (!platform.isPlatform()) {
            throw new IllegalArgumentException("Not Vkurse's own shop");
        }
        return start(platform, schoolId, payerId, amount, description, purpose, referenceId, returnUrl, now);
    }

    private static Payment start(
            Merchant merchant,
            UUID schoolId,
            UUID payerId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            ReturnUrl returnUrl,
            Instant now) {
        merchant.ensureTakesPayments();
        if (amount.isZero() || amount.isNegative()) {
            throw new BadRequestException("The amount must be more than zero");
        }
        if (purpose.isBlank() || purpose.length() > MAX_PURPOSE_LENGTH) {
            throw new IllegalArgumentException("A payment needs a purpose of at most " + MAX_PURPOSE_LENGTH);
        }
        var text = description.trim();
        if (text.isEmpty() || text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("The description must have 1 to " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return new Payment(merchant, schoolId, payerId, amount, text, purpose, referenceId, returnUrl, now);
    }

    /** Starts a small payment an owner makes to check a merchant end to end. */
    public static Payment test(
            Merchant merchant, UUID payerId, Money amount, String description, ReturnUrl returnUrl, Instant now) {
        if (!MAX_TEST_AMOUNT.isAtLeast(amount)) {
            throw new BadRequestException("A test payment is at most " + MAX_TEST_AMOUNT.value() + " RUB");
        }
        return start(merchant, payerId, amount, description, TEST_PURPOSE, merchant.getId(), returnUrl, now);
    }

    public boolean isPaidBy(UUID userId) {
        return payerId.equals(userId);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /** What we ask the provider to charge. */
    public PaymentOrder order(PaymentOrder.@Nullable Receipt receipt) {
        return new PaymentOrder(getId(), amount, description, URI.create(returnUrl), receipt);
    }

    /** Records that the provider took the payment, and what it says about it. */
    public void accept(ProviderPayment created, Instant now) {
        if (providerPaymentId != null && !providerPaymentId.equals(created.id())) {
            throw new IllegalStateException("Payment " + getId() + " already has another provider payment");
        }
        providerPaymentId = created.id();
        apply(created, now);
    }

    /**
     * Takes the provider's word on the payment. A pending payment becomes paid or canceled once, and says so with an
     * event once; the same word again, or any word about a payment that is no longer pending, changes nothing.
     */
    public void apply(ProviderPayment state, Instant now) {
        if (!state.id().equals(providerPaymentId)) {
            throw new IllegalStateException("Provider payment " + state.id() + " is not payment " + getId());
        }
        if (!isPending()) {
            return;
        }
        switch (state.state()) {
            case PENDING ->
                confirmationUrl = state.confirmationUrl() == null
                        ? confirmationUrl
                        : state.confirmationUrl().toString();
            case SUCCEEDED -> {
                if (!state.amount().equals(amount)) {
                    throw new IllegalStateException(
                            "Provider payment " + state.id() + " was paid " + state.amount() + ", not " + amount);
                }
                status = Status.SUCCEEDED;
                paidAt = now;
                finish();
                raise(new PaymentSucceeded(getId(), purpose, referenceId));
            }
            case CANCELED -> cancel(state.cancellationReason(), now);
        }
    }

    /**
     * Notes that the provider was asked about the payment, and decides when to ask again. A payment the provider
     * never took is canceled once it is old enough.
     */
    public void checked(Instant now) {
        if (!isPending()) {
            return;
        }
        var age = Duration.between(getCreatedAt(), now);
        if (providerPaymentId == null && age.compareTo(UNACCEPTED_TTL) >= 0) {
            cancel("not-accepted", now);
        } else if (age.compareTo(GIVE_UP_AFTER) >= 0) {
            nextCheckAt = null;
        } else {
            var pause = age.compareTo(MIN_CHECK_PAUSE) < 0
                    ? MIN_CHECK_PAUSE
                    : age.compareTo(MAX_CHECK_PAUSE) > 0 ? MAX_CHECK_PAUSE : age;
            nextCheckAt = now.plus(pause);
        }
    }

    /**
     * Starts giving money back. Only a paid payment can be refunded, and all refunds together never exceed it. The
     * amount is set aside at once, so two refunds asked at the same time can't both take the last of it.
     *
     * @param refundAmount what to give back; {@code null} for all that is left
     */
    public Refund refund(@Nullable Money refundAmount, @Nullable String reason, UUID requestedBy, Instant now) {
        if (status != Status.SUCCEEDED) {
            throw new NotRefundableException("Only a paid payment can be refunded");
        }
        var left = amount.minus(refundedAmount);
        var asked = refundAmount == null ? left : refundAmount;
        if (asked.isZero() || asked.isNegative() || !left.isAtLeast(asked)) {
            throw new NotRefundableException("At most " + left.value() + " " + left.currency() + " can be refunded");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("The reason must have at most " + MAX_REASON_LENGTH + " characters");
        }
        refundedAmount = refundedAmount.plus(asked);
        return Refund.request(this, asked, reason == null || reason.isBlank() ? null : reason.trim(), requestedBy, now);
    }

    /**
     * Takes the provider's word on a refund of this payment. A refund that succeeded says so with an event once; one
     * the provider canceled gives its amount back to what can be refunded.
     */
    public void settle(Refund refund, ProviderRefund state) {
        if (!refund.belongsTo(getId())) {
            throw new IllegalArgumentException("Refund " + refund.getId() + " is of another payment");
        }
        if (!refund.isPending()) {
            return;
        }
        refund.apply(state);
        if (refund.isSucceeded()) {
            raise(new PaymentRefunded(
                    getId(), purpose, referenceId, refund.getId(), refund.getAmount(), refundedAmount.equals(amount)));
        } else if (!refund.isPending()) {
            release(refund);
        }
    }

    /** Gives up a refund the provider refused to start. */
    public void drop(Refund refund) {
        if (refund.belongsTo(getId()) && refund.isPending()) {
            refund.fail();
            release(refund);
        }
    }

    private void release(Refund refund) {
        refundedAmount = refundedAmount.minus(refund.getAmount());
    }

    private void cancel(@Nullable String reason, Instant now) {
        status = Status.CANCELED;
        canceledAt = now;
        cancellationReason = reason;
        finish();
        raise(new PaymentCanceled(getId(), purpose, referenceId));
    }

    private void finish() {
        confirmationUrl = null;
        nextCheckAt = null;
    }

    public enum Status {
        /** Waits for the payer, or for the provider. */
        PENDING,
        /** Paid: the money is the merchant's. */
        SUCCEEDED,
        /** Will never be paid. */
        CANCELED
    }
}
