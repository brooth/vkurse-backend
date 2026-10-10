/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.application;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p>{@link #requestUpload}, {@link #confirm}, {@link #download} and {@link #links} talk to the storage server: call them outside a
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
        var upload = purpose.isResumable()
                ? new Upload(file, null, storage.signResumableUpload(file))
                : new Upload(file, storage.signUpload(file), null);
        transaction.executeWithoutResult(status -> files.save(file));
        return upload;
    }

    /**
     * Makes an uploaded file usable, after checking what storage really holds for it. Only the user who started the
     * upload may confirm it, and only as what it was started for.
     *
     * @throws NotFoundException there is no such upload of this user, for this purpose and school
     * @throws UploadIncompleteException storage has no file yet
     */
    public StoredFile confirm(UUID fileId, UUID uploadedBy, FilePurpose purpose, @Nullable UUID schoolId) {
        return confirm(fileId, uploadedBy, Set.of(purpose), schoolId);
    }

    /** As {@link #confirm(UUID, UUID, FilePurpose, UUID)}, for an upload that may be for any of several purposes. */
    public StoredFile confirm(UUID fileId, UUID uploadedBy, Set<FilePurpose> purposes, @Nullable UUID schoolId) {
        var upload = files.findById(fileId)
                .filter(file -> file.isUploadOf(uploadedBy, purposes, schoolId))
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

    /** A confirmed file of a school, for one of those purposes. */
    @Transactional(readOnly = true)
    public StoredFile get(UUID schoolId, Set<FilePurpose> purposes, UUID fileId) {
        return files.findById(fileId)
                .filter(file -> file.isReadyFileOf(schoolId, purposes))
                .orElseThrow(StoredFileService::notFound);
    }

    /**
     * Confirmed files of a school, for those purposes, by id.
     *
     * @throws NotFoundException one of the ids is not such a file
     */
    @Transactional(readOnly = true)
    public Map<UUID, StoredFile> getAll(UUID schoolId, Set<FilePurpose> purposes, Collection<UUID> fileIds) {
        var found = files.findByIds(fileIds).stream()
                .filter(file -> file.isReadyFileOf(schoolId, purposes))
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        if (!found.keySet().containsAll(fileIds)) {
            throw notFound();
        }
        return found;
    }

    /** The confirmed files of a school, newest first; {@code search} looks in their names. */
    @Transactional(readOnly = true)
    public List<StoredFile> findBySchool(
            UUID schoolId, Set<FilePurpose> purposes, @Nullable String search, int limit, int offset) {
        return files.findBySchool(schoolId, purposes, search, limit, offset);
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
     * Signed links to show private files in a page, by file id: a video for 6 hours, anything else for an hour. An
     * image or a video is served to be shown, any other file to be saved. A file storage doesn't hold gets no link.
     */
    public Map<UUID, Download> links(Collection<StoredFile> shown) {
        var now = clock.instant();
        var links = new HashMap<UUID, Download>();
        shown.stream()
                .collect(Collectors.groupingBy(StoredFile::linkTtl))
                .forEach((ttl, group) -> storage.signLinks(group, ttl)
                        .forEach((fileId, url) -> links.put(fileId, new Download(url, now.plus(ttl)))));
        return links;
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
