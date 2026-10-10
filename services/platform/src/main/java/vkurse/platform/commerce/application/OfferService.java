/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.money.Money;
import vkurse.platform.commerce.domain.Offer;
import vkurse.platform.commerce.domain.OfferRepository;
import vkurse.platform.course.application.CatalogService;
import vkurse.platform.course.application.CourseService;

/** A course's offers: managed by the school's owner and admins, and shown to anyone while they are on sale. */
@Service
@Transactional
public class OfferService {
    private final OfferRepository offers;
    private final CourseService courses;
    private final CatalogService catalog;
    private final SalesAccess access;
    private final Clock clock;

    public OfferService(
            OfferRepository offers, CourseService courses, CatalogService catalog, SalesAccess access, Clock clock) {
        this.offers = offers;
        this.courses = courses;
        this.catalog = catalog;
        this.access = access;
        this.clock = clock;
    }

    /** Adds an offer to a course. It is not on sale until it is activated. */
    public Offer create(
            UUID userId,
            UUID courseId,
            String name,
            @Nullable String description,
            Money priceAmount,
            @Nullable Integer accessDays) {
        var course = courses.get(userId, courseId);
        access.ensureManages(userId, course.getSchoolId(), "Course");
        return offers.save(Offer.create(
                course,
                name,
                description,
                priceAmount,
                accessDays,
                offers.countByCourse(courseId),
                offers.nextPosition(courseId),
                clock.instant()));
    }

    /** A course's offers, on sale or not, for the staff who manage sales. */
    @Transactional(readOnly = true)
    public List<Offer> list(UUID userId, UUID courseId) {
        var course = courses.get(userId, courseId);
        access.ensureManages(userId, course.getSchoolId(), "Course");
        return offers.findByCourse(courseId);
    }

    /** Public: the offers of a published course that are on sale. A course that isn't published has none. */
    @Transactional(readOnly = true)
    public List<Offer> findOnSale(UUID courseId) {
        if (catalog.publishedCourse(courseId).isEmpty()) {
            return List.of();
        }
        return offers.findActiveByCourse(courseId);
    }

    /**
     * Changes an offer. A {@code null} name, description or price leaves it as it is.
     *
     * @param changeAccess whether to set the access length to {@code accessDays} ({@code null} then means no end)
     */
    public Offer update(
            UUID userId,
            UUID offerId,
            @Nullable String name,
            @Nullable String description,
            @Nullable Money priceAmount,
            boolean changeAccess,
            @Nullable Integer accessDays) {
        var offer = manageable(userId, offerId);
        offer.changeDetails(name, description, priceAmount);
        if (changeAccess) {
            offer.changeAccess(accessDays);
        }
        return offers.save(offer);
    }

    /** Puts the offer on sale. */
    public Offer activate(UUID userId, UUID offerId) {
        var offer = manageable(userId, offerId);
        offer.activate();
        return offers.save(offer);
    }

    public Offer deactivate(UUID userId, UUID offerId) {
        var offer = manageable(userId, offerId);
        offer.deactivate();
        return offers.save(offer);
    }

    /** Takes the offer off sale for good. */
    public void archive(UUID userId, UUID offerId) {
        var offer = manageable(userId, offerId);
        offer.archive(clock.instant());
        offers.save(offer);
    }

    private Offer manageable(UUID userId, UUID offerId) {
        var offer = offers.findById(offerId)
                .filter(found -> !found.isArchived())
                .orElseThrow(() -> new NotFoundException("Offer not found"));
        access.ensureManages(userId, offer.getSchoolId(), "Offer");
        return offer;
    }
}
