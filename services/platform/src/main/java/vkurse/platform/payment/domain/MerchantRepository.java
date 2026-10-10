/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository {

    Optional<Merchant> findById(UUID id);

    /** A school's merchants that are still connected, oldest first. */
    List<Merchant> findConnectedBySchool(UUID schoolId);

    /** A school's merchants that take payments now, oldest first. */
    List<Merchant> findActiveBySchool(UUID schoolId);

    long countConnectedBySchool(UUID schoolId);

    Merchant save(Merchant merchant);
}
