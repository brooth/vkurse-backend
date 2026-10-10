/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Offers. An archived offer is found only by {@link #findById}. */
public interface OfferRepository {

    Optional<Offer> findById(UUID id);

    /** A course's offers that are not archived, in order. */
    List<Offer> findByCourse(UUID courseId);

    /** A course's offers that are on sale, in order. */
    List<Offer> findActiveByCourse(UUID courseId);

    long countByCourse(UUID courseId);

    /** The position after the course's last offer. */
    int nextPosition(UUID courseId);

    Offer save(Offer offer);
}
