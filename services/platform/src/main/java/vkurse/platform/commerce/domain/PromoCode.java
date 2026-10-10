/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.commerce.domain.CommerceErrors.PromoCodeInvalidException;
import vkurse.platform.commerce.domain.CommerceErrors.TooManyException;

/**
 * A school's promo code: a text a buyer types to pay less. It may end at some time, work a limited number of times,
 * and be for chosen offers only. A use is counted when an order is placed with the code, and given back if that order
 * is never paid.
 */
@Entity
@Table(name = "promo_codes")
@Getter
public class PromoCode extends AggregateRoot {
    /** How many promo codes a school may have. */
    public static final int MAX_PER_SCHOOL = 200;

    public static final int MAX_USES_LIMIT = 1_000_000;

    private UUID schoolId;

    /** Unique among the school's codes that are not archived. */
    @Convert(converter = PromoCodeText.Converter.class)
    private PromoCodeText code;

    @Embedded
    private Discount discount;

    /** The code stops working at this moment; {@code null} for no end. */
    private @Nullable Instant validUntil;

    /** How many orders may use the code; {@code null} for no limit. */
    private @Nullable Integer maxUses;

    /** How many orders use it now: placed with it, and paid or still waiting for payment. */
    private int usedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    private OfferScope scope;

    private boolean active = true;

    /** Set when the code is archived for good. Its row stays, because orders point to it. */
    private @Nullable Instant archivedAt;

    protected PromoCode() {}

    private PromoCode(
            UUID schoolId,
            PromoCodeText code,
            Discount discount,
            @Nullable Instant validUntil,
            @Nullable Integer maxUses,
            OfferScope scope,
            Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.code = code;
        this.discount = discount;
        this.validUntil = validUntil;
        this.maxUses = maxUses;
        this.scope = scope;
    }

    /**
     * @param existing how many promo codes the school has
     */
    public static PromoCode create(
            UUID schoolId,
            PromoCodeText code,
            Discount discount,
            @Nullable Instant validUntil,
            @Nullable Integer maxUses,
            OfferScope scope,
            long existing,
            Instant now) {
        if (existing >= MAX_PER_SCHOOL) {
            throw new TooManyException("The school has too many promo codes");
        }
        return new PromoCode(schoolId, code, discount, validUntil, checkedMaxUses(maxUses), checkedScope(scope), now);
    }

    public boolean belongsTo(UUID schoolId) {
        return this.schoolId.equals(schoolId);
    }

    /** Changes the discount and the offers the code is for. A {@code null} argument leaves that as it is. */
    public void change(@Nullable Discount discount, @Nullable OfferScope scope) {
        if (discount != null) {
            this.discount = discount;
        }
        if (scope != null) {
            this.scope = checkedScope(scope);
        }
    }

    /** Sets when the code ends and how many orders may use it; {@code null} removes that limit. */
    public void changeLimits(@Nullable Instant validUntil, @Nullable Integer maxUses) {
        this.validUntil = validUntil;
        this.maxUses = checkedMaxUses(maxUses);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    /** Stops the code for good, hides it from the staff's lists and frees its text for a new code. */
    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
            active = false;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /**
     * What that offer costs with this code.
     *
     * @throws PromoCodeInvalidException the code can't be used for the offer now, whatever the reason
     */
    public Quote quote(Offer offer, Instant now) {
        var usable = active
                && !isArchived()
                && schoolId.equals(offer.getSchoolId())
                && scope.covers(offer.getId())
                && (validUntil == null || now.isBefore(validUntil))
                && (maxUses == null || usedCount < maxUses);
        if (!usable) {
            throw new PromoCodeInvalidException();
        }
        return Quote.discounted(offer, discount);
    }

    /** Counts one more order that uses the code. Call it with the row locked, after {@link #quote}. */
    public void use() {
        usedCount++;
    }

    /** Gives a use back, when an order placed with the code was never paid. */
    public void giveBack() {
        if (usedCount > 0) {
            usedCount--;
        }
    }

    private static @Nullable Integer checkedMaxUses(@Nullable Integer maxUses) {
        if (maxUses != null && (maxUses < 1 || maxUses > MAX_USES_LIMIT)) {
            throw new BadRequestException("A promo code works 1 to " + MAX_USES_LIMIT + " times, or without a limit");
        }
        return maxUses;
    }

    private static OfferScope checkedScope(OfferScope scope) {
        if (scope.offerIds().size() > OfferScope.MAX_OFFERS) {
            throw new BadRequestException("A promo code is for at most " + OfferScope.MAX_OFFERS + " offers");
        }
        return scope;
    }
}
