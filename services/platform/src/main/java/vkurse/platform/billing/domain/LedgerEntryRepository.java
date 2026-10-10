/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository {

    /** Whether the balance was changed for that already. */
    boolean exists(LedgerEntry.Kind kind, UUID referenceId);

    /** A school's entries, newest first. */
    List<LedgerEntry> findBySchool(UUID schoolId, int limit, int offset);

    LedgerEntry save(LedgerEntry entry);
}
