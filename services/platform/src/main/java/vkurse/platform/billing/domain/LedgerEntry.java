/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;

/**
 * One change of a school's balance. An entry is made only by {@link BillingAccount}, together with the change, and
 * never changes after. A {@code kind} and a {@code referenceId} name one entry: a payment is credited once, a day is
 * charged once.
 */
@Entity
@Table(name = "billing_ledger")
@Getter
public class LedgerEntry extends AggregateRoot {
    private UUID accountId;

    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    /** How the balance changed: more than zero for money in, less for money out. */
    @Embedded
    private Money amount;

    /** The balance right after this change. */
    @Embedded
    private Money balanceAfterAmount;

    /** What caused it: the payment of a top-up, the usage of a storage charge, the refund. */
    private UUID referenceId;

    protected LedgerEntry() {}

    LedgerEntry(BillingAccount account, Kind kind, Money amount, UUID referenceId, Instant now) {
        super(now);
        this.accountId = account.getId();
        this.schoolId = account.getSchoolId();
        this.kind = kind;
        this.amount = amount;
        this.balanceAfterAmount = account.getBalanceAmount();
        this.referenceId = referenceId;
    }

    public enum Kind {
        /** The school paid money in. */
        TOP_UP,
        /** A day of storage. */
        STORAGE_CHARGE,
        /** A top-up's money was given back to the payer. */
        REFUND
    }
}
