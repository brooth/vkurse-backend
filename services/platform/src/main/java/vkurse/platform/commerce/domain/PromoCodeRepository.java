/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Promo codes. An archived code is found only by id. */
public interface PromoCodeRepository {

    Optional<PromoCode> findById(UUID id);

    /** Loads and locks the code, so its uses are counted one order at a time. */
    Optional<PromoCode> findByIdForUpdate(UUID id);

    /** The school's code with that text, if it is not archived. */
    Optional<PromoCode> findBySchoolAndCode(UUID schoolId, PromoCodeText code);

    /** A school's codes that are not archived, newest first. */
    List<PromoCode> findBySchool(UUID schoolId, int limit, int offset);

    long countBySchool(UUID schoolId);

    PromoCode save(PromoCode promoCode);
}
