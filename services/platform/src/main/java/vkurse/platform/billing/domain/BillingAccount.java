/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;
import vkurse.platform.billing.domain.BillingErrors.PaymentRequiredException;
import vkurse.platform.billing.domain.BillingErrors.SuspendedException;

/**
 * A school's money at Vkurse. Top-ups add to the balance and each day of storage takes from it. What the balance
 * can't cover is a debt: the balance goes below zero, uploads stop at once, and after {@link #GRACE} the school's
 * files stop opening, until a top-up brings the balance back to zero or more.
 *
 * <p>Every change of the balance makes its {@link LedgerEntry}: load the account locked, call the method, save both.
 */
@Entity
@Table(name = "billing_accounts")
public class BillingAccount extends AggregateRoot {
    /** How long a school may owe before its files stop opening. */
    public static final Duration GRACE = Duration.ofDays(14);

    /** The owner is warned when the balance covers fewer days of storage than this. */
    public static final int LOW_BALANCE_DAYS = 7;

    private UUID schoolId;

    /** Below zero when the school owes. */
    @Embedded
    private Money balanceAmount;

    /** Since when the balance has been below zero. */
    private @Nullable Instant debtSince;

    /** When the owner was last told about each state; cleared when the state ends, so the next time tells again. */
    private @Nullable Instant lowBalanceNotifiedAt;

    private @Nullable Instant debtNotifiedAt;

    private @Nullable Instant suspensionNotifiedAt;

    protected BillingAccount() {}

    private BillingAccount(UUID schoolId, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.balanceAmount = Money.ZERO_RUB;
    }

    /** Opens a school's account, with nothing on it. */
    public static BillingAccount open(UUID schoolId, Instant now) {
        return new BillingAccount(schoolId, now);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public Money getBalanceAmount() {
        return balanceAmount;
    }

    public @Nullable Instant getDebtSince() {
        return debtSince;
    }

    public @Nullable Instant getLowBalanceNotifiedAt() {
        return lowBalanceNotifiedAt;
    }

    public @Nullable Instant getDebtNotifiedAt() {
        return debtNotifiedAt;
    }

    public @Nullable Instant getSuspensionNotifiedAt() {
        return suspensionNotifiedAt;
    }

    /** Adds a paid top-up to the balance. */
    public LedgerEntry topUp(Money amount, UUID paymentId, Instant now) {
        requirePositive(amount);
        return change(LedgerEntry.Kind.TOP_UP, amount, paymentId, now);
    }

    /** Takes a day of storage from the balance. */
    public LedgerEntry charge(StorageUsage usage, Instant now) {
        requirePositive(usage.getChargeAmount());
        return change(LedgerEntry.Kind.STORAGE_CHARGE, negated(usage.getChargeAmount()), usage.getId(), now);
    }

    /** Takes back a top-up's money that was given back to the payer. */
    public LedgerEntry takeBack(Money amount, UUID refundId, Instant now) {
        requirePositive(amount);
        return change(LedgerEntry.Kind.REFUND, negated(amount), refundId, now);
    }

    private LedgerEntry change(LedgerEntry.Kind kind, Money delta, UUID referenceId, Instant now) {
        balanceAmount = balanceAmount.plus(delta);
        if (balanceAmount.isNegative()) {
            if (debtSince == null) {
                debtSince = now;
            }
        } else {
            debtSince = null;
            debtNotifiedAt = null;
            suspensionNotifiedAt = null;
        }
        if (!delta.isNegative()) {
            lowBalanceNotifiedAt = null;
        }
        return new LedgerEntry(this, kind, delta, referenceId, now);
    }

    public Status status(Instant now) {
        if (debtSince == null) {
            return Status.ACTIVE;
        }
        return now.isBefore(debtSince.plus(GRACE)) ? Status.DEBT : Status.SUSPENDED;
    }

    /** When the school's files stop opening, if it stays in debt. */
    public @Nullable Instant suspendsAt() {
        return debtSince == null ? null : debtSince.plus(GRACE);
    }

    /** A school that owes can't upload. */
    public void ensureCanUpload() {
        if (debtSince != null) {
            throw new PaymentRequiredException();
        }
    }

    /** A school that has owed for longer than the grace period can't have its private files read. */
    public void ensureCanRead(Instant now) {
        if (status(now) == Status.SUSPENDED) {
            throw new SuspendedException();
        }
    }

    /**
     * For how many whole days the balance covers storage at that cost a day; {@code null} if storage costs nothing
     * now. Zero when the school owes.
     */
    public @Nullable Long daysLeft(Money dailyCharge) {
        if (dailyCharge.isZero()) {
            return null;
        }
        if (balanceAmount.isNegative()) {
            return 0L;
        }
        return balanceAmount
                .value()
                .divide(dailyCharge.value(), 0, RoundingMode.DOWN)
                .longValue();
    }

    /**
     * What the owner should be told now, if anything, and remembers that it was told: each state is announced once
     * until it ends.
     *
     * @param dailyCharge what a day of the school's storage costs now
     */
    public Optional<Notice> noticeDue(Money dailyCharge, Instant now) {
        switch (status(now)) {
            case SUSPENDED -> {
                if (suspensionNotifiedAt == null) {
                    suspensionNotifiedAt = now;
                    return Optional.of(Notice.SUSPENDED);
                }
            }
            case DEBT -> {
                if (debtNotifiedAt == null) {
                    debtNotifiedAt = now;
                    return Optional.of(Notice.DEBT);
                }
            }
            case ACTIVE -> {
                var days = daysLeft(dailyCharge);
                if (days != null && days < LOW_BALANCE_DAYS && lowBalanceNotifiedAt == null) {
                    lowBalanceNotifiedAt = now;
                    return Optional.of(Notice.LOW_BALANCE);
                }
            }
        }
        return Optional.empty();
    }

    private static void requirePositive(Money amount) {
        if (amount.isZero() || amount.isNegative()) {
            throw new IllegalArgumentException("The amount must be more than zero");
        }
    }

    private static Money negated(Money amount) {
        return new Money(amount.value().negate(), amount.currency());
    }

    public enum Status {
        /** Nothing owed. */
        ACTIVE,
        /** The balance is below zero: no uploads. */
        DEBT,
        /** In debt for longer than the grace period: the school's private files don't open. */
        SUSPENDED
    }

    public enum Notice {
        LOW_BALANCE,
        DEBT,
        SUSPENDED
    }
}
