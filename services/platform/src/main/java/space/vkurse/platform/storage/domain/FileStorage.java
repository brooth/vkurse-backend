/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.storage.domain;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * Port: the storage server that keeps the files' bytes. Every method but {@link #publicUrl} calls it over the network,
 * so use them outside a database transaction.
 */
public interface FileStorage {

    /**
     * A link the client uploads the file with: one {@code PUT} of the raw bytes with their content type. Storage
     * itself refuses a file that is bigger or of another type than its bucket takes.
     */
    URI signUpload(StoredFile file);

    /** A link that downloads the file under its own name, for {@code ttl}. For a file that is not public. */
    URI signDownload(StoredFile file, Duration ttl);

    /** The lasting link of a public file. Computed, with no call to storage. */
    URI publicUrl(StoredFile file);

    /** What storage holds for the file; empty if nothing was uploaded. */
    Optional<StoredObject> stat(StoredFile file);

    /** Removes the files' objects. A file that has no object is skipped. */
    void delete(Collection<StoredFile> files);
}
