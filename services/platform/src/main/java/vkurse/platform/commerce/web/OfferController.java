/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.money.Money;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.commerce.application.OfferService;
import vkurse.platform.commerce.domain.Offer;
import vkurse.platform.course.web.CatalogCourseResponse;

/** A course's offers: for the school's owner and admins, and, while on sale, for anyone. */
@RestController
class OfferController {
    private final OfferService offers;

    OfferController(OfferService offers) {
        this.offers = offers;
    }

    /** Adds an offer to a course. It is not on sale until it is activated. */
    @PostMapping("/courses/{courseId}/offers")
    OfferResponse create(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody CreateRequest data) {
        return OfferResponse.of(offers.create(
                session.userId(),
                courseId,
                data.name(),
                data.description(),
                Money.rub(data.price()),
                data.accessDays()));
    }

    /** The course's offers, on sale or not. */
    @GetMapping("/courses/{courseId}/offers")
    List<OfferResponse> list(@GetSession UserSession session, @PathVariable UUID courseId) {
        return offers.list(session.userId(), courseId).stream()
                .map(OfferResponse::of)
                .toList();
    }

    /** Public: what a published course can be bought for. */
    @GetMapping("/catalog/courses/{courseId}/offers")
    List<OfferResponse> onSale(@PathVariable UUID courseId) {
        return offers.findOnSale(courseId).stream().map(OfferResponse::of).toList();
    }

    /** Public: an offer on sale with its course and school, for the page where it is bought. */
    @GetMapping("/catalog/offers/{id}")
    CatalogOfferResponse offerOnSale(@PathVariable UUID id) {
        var found = offers.getOnSale(id);
        return new CatalogOfferResponse(OfferResponse.of(found.offer()), CatalogCourseResponse.of(found.course()));
    }

    @PatchMapping("/offers/{id}")
    OfferResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        var access = data.access();
        return OfferResponse.of(offers.update(
                session.userId(),
                id,
                data.name(),
                data.description(),
                data.price() == null ? null : Money.rub(data.price()),
                access != null,
                access == null ? null : access.days()));
    }

    /** Puts the offer on sale. */
    @PostMapping("/offers/{id}/activate")
    OfferResponse activate(@GetSession UserSession session, @PathVariable UUID id) {
        return OfferResponse.of(offers.activate(session.userId(), id));
    }

    @PostMapping("/offers/{id}/deactivate")
    OfferResponse deactivate(@GetSession UserSession session, @PathVariable UUID id) {
        return OfferResponse.of(offers.deactivate(session.userId(), id));
    }

    /** Takes the offer off sale for good. Orders already placed keep it. */
    @DeleteMapping("/offers/{id}")
    ResponseEntity<Void> archive(@GetSession UserSession session, @PathVariable UUID id) {
        offers.archive(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** An offer on sale, with its course as the catalog shows it ({@code course.school} is its school). */
    record CatalogOfferResponse(OfferResponse offer, CatalogCourseResponse course) {}

    /** {@code price} is in rubles, zero for a free offer. Without {@code accessDays}, access has no end. */
    record CreateRequest(
            @NotBlank @Size(max = Offer.MAX_NAME_LENGTH) String name,

            @Size(max = Offer.MAX_DESCRIPTION_LENGTH) @Nullable
            String description,

            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2)
            BigDecimal price,

            @Nullable Integer accessDays) {}

    /**
     * {@code null} leaves a field unchanged; an empty {@code description} removes it. To change how long access lasts,
     * send {@code access}: {@code { days: 90 }}, or {@code { days: null }} for no end.
     */
    record UpdateRequest(
            @Size(min = 1, max = Offer.MAX_NAME_LENGTH) @Nullable
            String name,

            @Size(max = Offer.MAX_DESCRIPTION_LENGTH) @Nullable
            String description,

            @DecimalMin("0") @Digits(integer = 10, fraction = 2) @Nullable
            BigDecimal price,

            @Nullable AccessRequest access) {}

    record AccessRequest(@Nullable Integer days) {}
}
