/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.money.Money;
import vkurse.platform.commerce.domain.CommerceErrors.AlreadyBoughtException;
import vkurse.platform.commerce.domain.CommerceErrors.OrderNotPayableException;

/**
 * A buyer's order of one offer. It keeps what was bought as it was when ordered: the names, the price, the discount
 * and the total don't follow later changes of the offer or the promo code. It waits for payment for a day; whenever
 * its payment succeeds it is paid, even if it had expired or was canceled by then, because the money was taken.
 */
@Entity
@Table(name = "orders")
@Getter
public class Order extends AggregateRoot {
    /** What a payment for an order names as its purpose. */
    public static final String PAYMENT_PURPOSE = "order";

    /** How long an order waits for payment. */
    public static final Duration TTL = Duration.ofHours(24);

    private UUID schoolId;

    private UUID buyerId;

    private UUID offerId;

    private UUID courseId;

    /** The offer's name when it was ordered. */
    private String offerName;

    /** The course's title when it was ordered. */
    private String courseTitle;

    /** For how many days the buyer has the course; {@code null} for no end. */
    private @Nullable Integer accessDays;

    /** The offer's price when it was ordered. */
    @Embedded
    private Money priceAmount;

    /** What the promo code took off. */
    @Embedded
    private Money discountAmount;

    /** What the buyer pays. */
    @Embedded
    private Money totalAmount;

    private @Nullable UUID promoCodeId;

    /** The promo code as it was typed. */
    private @Nullable String promoCode;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** The payment last started for this order. */
    private @Nullable UUID paymentId;

    private @Nullable Instant paidAt;

    /** An order still unpaid then is expired. */
    private Instant expiresAt;

    protected Order() {}

    private Order(UUID buyerId, Offer offer, String courseTitle, Quote quote, @Nullable PromoCode promo, Instant now) {
        super(now);
        this.schoolId = offer.getSchoolId();
        this.buyerId = buyerId;
        this.offerId = offer.getId();
        this.courseId = offer.getCourseId();
        this.offerName = offer.getName();
        this.courseTitle = courseTitle;
        this.accessDays = offer.getAccessDays();
        this.priceAmount = quote.price();
        this.discountAmount = quote.discount();
        this.totalAmount = quote.total();
        this.promoCodeId = promo == null ? null : promo.getId();
        this.promoCode = promo == null ? null : promo.getCode().value();
        this.expiresAt = now.plus(TTL);
    }

    /**
     * Places an order for an offer that can be bought. An order with nothing to pay is paid at once.
     *
     * @param quote what the offer costs this buyer, with {@code promo} if one was used
     * @param alreadyBought whether the buyer has a paid order for the same course
     */
    public static Order place(
            UUID buyerId,
            Offer offer,
            String courseTitle,
            Quote quote,
            @Nullable PromoCode promo,
            boolean alreadyBought,
            Instant now) {
        if (alreadyBought) {
            throw new AlreadyBoughtException();
        }
        var order = new Order(buyerId, offer, courseTitle, quote, promo, now);
        if (order.totalAmount.isZero()) {
            order.markPaid(now);
        }
        return order;
    }

    public boolean isOf(UUID buyerId) {
        return this.buyerId.equals(buyerId);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }

    /** Checks that a payment may be started for the order now. */
    public void ensurePayable(Instant now) {
        if (!isPending() || !now.isBefore(expiresAt)) {
            throw new OrderNotPayableException();
        }
    }

    /** Records the payment started for the order. */
    public void attach(UUID paymentId, Instant now) {
        ensurePayable(now);
        this.paymentId = paymentId;
    }

    /**
     * Makes the order paid, and says so with an event once. An order that had expired or was canceled is paid all the
     * same; one that is paid or refunded already is left as it is.
     *
     * @return whether the order became paid now
     */
    public boolean markPaid(Instant now) {
        if (status == Status.PAID || status == Status.REFUNDED) {
            return false;
        }
        status = Status.PAID;
        paidAt = now;
        raise(new OrderPaid(getId(), schoolId, buyerId, courseId, offerId, accessDays));
        return true;
    }

    /** The buyer gives the order up before paying. */
    public void cancel() {
        if (!isPending()) {
            throw new OrderNotPayableException();
        }
        status = Status.CANCELED;
    }

    /**
     * Expires an order that waited for payment too long.
     *
     * @return whether the order expired now
     */
    public boolean expire(Instant now) {
        if (!isPending() || now.isBefore(expiresAt)) {
            return false;
        }
        status = Status.EXPIRED;
        return true;
    }

    /** The whole payment went back to the buyer: the order no longer gives the course. */
    public boolean markRefunded() {
        if (status != Status.PAID) {
            return false;
        }
        status = Status.REFUNDED;
        raise(new OrderRefunded(getId(), schoolId, buyerId, courseId));
        return true;
    }

    public enum Status {
        /** Waits for payment. */
        PENDING,
        /** Paid: the buyer has the course. */
        PAID,
        /** Given up by the buyer before paying. */
        CANCELED,
        /** Not paid in time. */
        EXPIRED,
        /** Paid, and then the whole payment was given back. */
        REFUNDED
    }
}
