/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.util.List;
import java.util.UUID;

/** The offers a promo code is for. An empty list means every offer of the school. Stored as one JSON value. */
public record OfferScope(List<UUID> offerIds) {
    public static final int MAX_OFFERS = 100;

    public static final OfferScope EVERY = new OfferScope(List.of());

    public OfferScope {
        offerIds = List.copyOf(offerIds);
    }

    public boolean covers(UUID offerId) {
        return offerIds.isEmpty() || offerIds.contains(offerId);
    }
}
