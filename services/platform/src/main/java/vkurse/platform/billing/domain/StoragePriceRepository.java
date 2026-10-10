/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import java.time.LocalDate;
import java.util.Optional;

public interface StoragePriceRepository {

    /** The price in force on a day: the latest that started on or before it. */
    Optional<StoragePrice> findInForce(LocalDate day);
}
