/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;

/**
 * What a school stored on one day and what that day cost it. There is one per school and day, and it never changes:
 * it is what the day's charge in the ledger was computed from.
 */
@Entity
@Table(name = "billing_storage_usage")
public class StorageUsage extends AggregateRoot {
    private UUID schoolId;

    /** The day, by Moscow time. */
    private LocalDate day;

    /** The bytes the school stored when the day was measured. */
    private long bytes;

    /** The price of one GB for a month, as it was that day. */
    @Embedded
    private Money priceAmount;

    /** What the day cost. */
    @Embedded
    private Money chargeAmount;

    protected StorageUsage() {}

    private StorageUsage(UUID schoolId, LocalDate day, long bytes, StoragePrice price, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.day = day;
        this.bytes = bytes;
        this.priceAmount = price.getAmount();
        this.chargeAmount = price.dailyCharge(bytes, day);
    }

    /** Records a school's storage for a day, at the price in force that day. */
    public static StorageUsage measure(UUID schoolId, LocalDate day, long bytes, StoragePrice price, Instant now) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Bytes can't be negative");
        }
        return new StorageUsage(schoolId, day, bytes, price, now);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public LocalDate getDay() {
        return day;
    }

    public long getBytes() {
        return bytes;
    }

    public Money getPriceAmount() {
        return priceAmount;
    }

    public Money getChargeAmount() {
        return chargeAmount;
    }

    public boolean isCharged() {
        return !chargeAmount.isZero();
    }
}
