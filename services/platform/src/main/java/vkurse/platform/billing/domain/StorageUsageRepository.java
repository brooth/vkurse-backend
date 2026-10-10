/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StorageUsageRepository {

    /** The schools whose storage was measured for that day. */
    Set<UUID> findMeasuredSchools(LocalDate day);

    boolean exists(UUID schoolId, LocalDate day);

    /** A school's days, newest first. */
    List<StorageUsage> findBySchool(UUID schoolId, int limit, int offset);

    StorageUsage save(StorageUsage usage);
}
