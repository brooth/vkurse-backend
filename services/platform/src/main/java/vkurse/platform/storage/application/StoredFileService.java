/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.FileStorage;
import vkurse.platform.storage.domain.StorageErrors.UploadIncompleteException;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.StoredFileRepository;
import vkurse.platform.storage.domain.Upload;

/**
 * Files, for the modules that own them. This service checks what a file is (its purpose, size, type, uploader and
 * school); <b>who may upload, see or delete it is the calling module's check</b>, made before it calls here.
 *
 * <p>{@link #requestUpload}, {@link #confirm} and {@link #download} talk to the storage server: call them outside a
 * database transaction.
 */
@Service
public class StoredFileService {
    private final StoredFileRepository files;
    private final FileStorage storage;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public StoredFileService(
            StoredFileRepository files,
            FileStorage storage,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Starts an upload and signs its link. Nothing is stored if storage can't sign.
     *
     * @param schoolId the school the file is for; {@code null} for a user's own file
     */
    public Upload requestUpload(
            FilePurpose purpose, @Nullable UUID schoolId, UUID uploadedBy, String name, String contentType, long size) {
        var now = clock.instant();
        var file = StoredFile.request(
                purpose,
                schoolId,
                uploadedBy,
                name,
                contentType,
                size,
                files.countPendingByUploader(uploadedBy, now),
                now);
        var url = storage.signUpload(file);
        transaction.executeWithoutResult(status -> files.save(file));
        return new Upload(file, url);
    }

    /**
     * Makes an uploaded file usable, after checking what storage really holds for it. Only the user who started the
     * upload may confirm it, and only as what it was started for.
     *
     * @throws NotFoundException there is no such upload of this user, for this purpose and school
     * @throws UploadIncompleteException storage has no file yet
     */
    public StoredFile confirm(UUID fileId, UUID uploadedBy, FilePurpose purpose, @Nullable UUID schoolId) {
        var upload = files.findById(fileId)
                .filter(file -> file.isUploadOf(uploadedBy, purpose, schoolId))
                .orElseThrow(StoredFileService::notFound);
        if (upload.isReady()) {
            return upload;
        }
        var object = storage.stat(upload).orElseThrow(UploadIncompleteException::new);
        return Objects.requireNonNull(transaction.execute(status -> {
            var file = files.findByIdForUpdate(fileId).orElseThrow(StoredFileService::notFound);
            file.confirm(object, clock.instant());
            return files.save(file);
        }));
    }

    /** A confirmed file of a school. */
    @Transactional(readOnly = true)
    public StoredFile get(UUID schoolId, FilePurpose purpose, UUID fileId) {
        return files.findById(fileId)
                .filter(file -> file.isReadyFileOf(schoolId, purpose))
                .orElseThrow(StoredFileService::notFound);
    }

    /** The confirmed files of a school, newest first; {@code search} looks in their names. */
    @Transactional(readOnly = true)
    public List<StoredFile> findBySchool(
            UUID schoolId, FilePurpose purpose, @Nullable String search, int limit, int offset) {
        return files.findBySchool(schoolId, purpose, search, limit, offset);
    }

    /** A link to read the file with: its lasting link if it is public, a signed one that soon expires if not. */
    public Download download(StoredFile file) {
        if (file.getPurpose().isPublic()) {
            return new Download(storage.publicUrl(file), null);
        }
        var expiresAt = clock.instant().plus(StoredFile.DOWNLOAD_TTL);
        return new Download(storage.signDownload(file, StoredFile.DOWNLOAD_TTL), expiresAt);
    }

    /**
     * Deletes a file: nothing finds it any more, and a job removes it from storage. It joins the caller's transaction,
     * so the file goes only if the caller's own change is committed. A missing file is ignored.
     */
    @Transactional
    public void delete(UUID fileId) {
        files.findById(fileId).ifPresent(file -> {
            file.delete(clock.instant());
            files.save(file);
        });
    }

    private static NotFoundException notFound() {
        return new NotFoundException("File not found");
    }
}
