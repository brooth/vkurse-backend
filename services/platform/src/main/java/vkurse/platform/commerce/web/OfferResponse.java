/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.commerce.domain.Offer;

/** An offer. {@code accessDays} is for how long the buyer has the course; absent for no end. */
public record OfferResponse(
        UUID id,
        UUID courseId,
        String name,
        @Nullable String description,
        PriceResponse price,
        @Nullable Integer accessDays,
        boolean active,
        int position) {

    public static OfferResponse of(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getCourseId(),
                offer.getName(),
                offer.getDescription(),
                PriceResponse.of(offer.getPriceAmount()),
                offer.getAccessDays(),
                offer.isActive(),
                offer.getPosition());
    }
}
