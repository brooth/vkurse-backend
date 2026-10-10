/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;

/**
 * What storage costs from a day on: the price of one GB kept for a month. The price in force on a day is the latest
 * one that started on or before it.
 */
@Entity
@Table(name = "billing_storage_prices")
public class StoragePrice extends AggregateRoot {
    /** One GB, in bytes. */
    public static final long GB = 1024L * 1024 * 1024;

    /** For one GB kept for a month. */
    @Embedded
    private Money amount;

    private LocalDate effectiveFrom;

    protected StoragePrice() {}

    public Money getAmount() {
        return amount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    /**
     * What keeping that many bytes costs for one day: the month's price for them, divided by the days of that month
     * and rounded to kopecks. Bytes too few to cost half a kopeck a day cost nothing.
     */
    public Money dailyCharge(long bytes, LocalDate day) {
        var monthly = amount.value().multiply(BigDecimal.valueOf(bytes));
        var divisor = BigDecimal.valueOf(GB).multiply(BigDecimal.valueOf(day.lengthOfMonth()));
        return new Money(monthly.divide(divisor, Money.SCALE, RoundingMode.HALF_UP), amount.currency());
    }
}
