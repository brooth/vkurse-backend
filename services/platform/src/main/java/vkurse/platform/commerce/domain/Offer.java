/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.money.Money;
import vkurse.platform.commerce.domain.CommerceErrors.OfferUnavailableException;
import vkurse.platform.commerce.domain.CommerceErrors.TooManyException;
import vkurse.platform.course.domain.Course;

/**
 * One way to buy a course: a name, a price, and how long the buyer has the course. A course may have several (its
 * tariffs). An offer is new as inactive, so its price can be set up before anyone sees it.
 */
@Entity
@Table(name = "offers")
public class Offer extends AggregateRoot {
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_ACCESS_DAYS = 3650;

    /** How many offers a course may have. */
    public static final int MAX_PER_COURSE = 20;

    /** The largest price. */
    public static final Money MAX_PRICE = Money.rub(10_000_000);

    private UUID schoolId;

    private UUID courseId;

    private String name;

    private @Nullable String description;

    /** What the buyer pays without a promo code. Zero makes the offer free. */
    @Embedded
    private Money priceAmount;

    /** For how many days the buyer has the course; {@code null} for no end. */
    private @Nullable Integer accessDays;

    /** Whether the offer is on sale. */
    private boolean active;

    /** Offers of a course are shown in the order of their positions. */
    private int position;

    /** Set when the offer is archived for good. Its row stays, because orders point to it. */
    private @Nullable Instant archivedAt;

    protected Offer() {}

    private Offer(
            Course course,
            String name,
            @Nullable String description,
            Money priceAmount,
            @Nullable Integer accessDays,
            int position,
            Instant now) {
        super(now);
        this.schoolId = course.getSchoolId();
        this.courseId = course.getId();
        this.name = name;
        this.description = description;
        this.priceAmount = priceAmount;
        this.accessDays = accessDays;
        this.position = position;
    }

    /**
     * Adds an offer to a course, not yet on sale.
     *
     * @param existing how many offers the course has
     * @param position the position after the course's last offer
     */
    public static Offer create(
            Course course,
            String name,
            @Nullable String description,
            Money priceAmount,
            @Nullable Integer accessDays,
            long existing,
            int position,
            Instant now) {
        if (existing >= MAX_PER_COURSE) {
            throw new TooManyException("The course has too many offers");
        }
        return new Offer(
                course,
                checkedName(name),
                checkedDescription(description),
                checkedPrice(priceAmount),
                checkedAccess(accessDays),
                position,
                now);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public Money getPriceAmount() {
        return priceAmount;
    }

    public @Nullable Integer getAccessDays() {
        return accessDays;
    }

    public boolean isActive() {
        return active;
    }

    public int getPosition() {
        return position;
    }

    public @Nullable Instant getArchivedAt() {
        return archivedAt;
    }

    /**
     * Changes what is sold. A {@code null} name, description or price leaves it as it is; a blank description removes
     * it. Orders already placed keep what they were placed with.
     */
    public void changeDetails(@Nullable String name, @Nullable String description, @Nullable Money priceAmount) {
        if (name != null) {
            this.name = checkedName(name);
        }
        if (description != null) {
            this.description = checkedDescription(description);
        }
        if (priceAmount != null) {
            this.priceAmount = checkedPrice(priceAmount);
        }
    }

    /** Sets for how long a buyer has the course; {@code null} for no end. */
    public void changeAccess(@Nullable Integer accessDays) {
        this.accessDays = checkedAccess(accessDays);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    /** Takes the offer off sale for good and hides it from the staff's lists. */
    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
            active = false;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isFree() {
        return priceAmount.isZero();
    }

    /**
     * Checks that the offer can be bought now.
     *
     * @param coursePublished whether the offer's course is in the catalog
     */
    public void ensureSellable(boolean coursePublished) {
        if (!active || isArchived() || !coursePublished) {
            throw new OfferUnavailableException();
        }
    }

    private static String checkedName(String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("The name must have 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static @Nullable String checkedDescription(@Nullable String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        var trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException(
                    "The description must have at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static Money checkedPrice(Money price) {
        if (price.isNegative() || !MAX_PRICE.isAtLeast(price)) {
            throw new BadRequestException("The price must be from 0 to " + MAX_PRICE.value());
        }
        return price;
    }

    private static @Nullable Integer checkedAccess(@Nullable Integer accessDays) {
        if (accessDays != null && (accessDays < 1 || accessDays > MAX_ACCESS_DAYS)) {
            throw new BadRequestException("Access lasts 1 to " + MAX_ACCESS_DAYS + " days, or has no end");
        }
        return accessDays;
    }
}
