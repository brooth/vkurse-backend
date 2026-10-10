/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.util.UUID;

/**
 * Port: whether a school may use storage now. Storage asks it before it signs an upload for a school and before it
 * signs a link to a school's private file; the module that knows why a school may not (billing) implements it and
 * throws its own client error. Public files and a user's own files are never asked about.
 */
public interface SchoolStorageGate {

    /** Throws if the school may not upload now. */
    void ensureCanUpload(UUID schoolId);

    /** Throws if the school's private files may not be read now. */
    void ensureCanRead(UUID schoolId);
}
