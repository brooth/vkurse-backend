/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.storage.domain.StorageErrors.FileTooLargeException;
import vkurse.platform.storage.domain.StorageErrors.FileTypeNotAllowedException;
import vkurse.platform.storage.domain.StorageErrors.TooManyUploadsException;
import vkurse.platform.storage.domain.StorageErrors.UploadExpiredException;

/**
 * A file kept in storage: what it is for, whose it is and where it lies. It starts as an upload the client was given a
 * link for ({@link Status#PENDING}), and may be used only once it is confirmed ({@link Status#READY}): until then
 * nothing says the client uploaded what it declared.
 */
@Entity
@Table(name = "stored_files")
public class StoredFile extends AggregateRoot {
    /**
     * How long a client has to upload and confirm. The upload link itself is signed by the storage server, which must
     * be set to the same lifetime.
     */
    public static final Duration UPLOAD_TTL = Duration.ofHours(2);

    /** How long a signed link to a video works: long enough to watch it through. */
    public static final Duration VIDEO_LINK_TTL = Duration.ofHours(6);

    /** How long any other signed link works. */
    public static final Duration DOWNLOAD_TTL = Duration.ofHours(1);

    /** How many unfinished uploads one user may have. */
    public static final int MAX_PENDING = 20;

    public static final int MAX_NAME_LENGTH = 255;

    @Enumerated(EnumType.STRING)
    private FilePurpose purpose;

    /** The school the file belongs to; {@code null} for a file of the user who uploaded it. */
    private @Nullable UUID schoolId;

    private UUID uploadedBy;

    /** The file's name on the uploader's device. Shown, and offered as the name of a download; never part of a path. */
    private String name;

    private String contentType;

    /** In bytes: what the uploader declared, then what storage really holds. */
    private long size;

    /** Where the file lies in its bucket. */
    private String path;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** An upload not confirmed by then is dropped. */
    private Instant uploadExpiresAt;

    /** Set when the file is deleted. Nothing finds it any more; a job removes it from storage, then the row. */
    private @Nullable Instant deletedAt;

    protected StoredFile() {}

    private StoredFile(
            FilePurpose purpose,
            @Nullable UUID schoolId,
            UUID uploadedBy,
            String name,
            String contentType,
            long size,
            Instant now) {
        super(now);
        this.purpose = purpose;
        this.schoolId = schoolId;
        this.uploadedBy = uploadedBy;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.path =
                purpose.path((schoolId == null ? uploadedBy : schoolId).toString(), getId().toString(), contentType);
        this.uploadExpiresAt = now.plus(UPLOAD_TTL);
    }

    /**
     * Starts an upload: the file a client says it is about to send.
     *
     * @param schoolId the school the file is for; {@code null} exactly when the purpose is a user's own file
     * @param pending how many unfinished uploads the user has already
     */
    public static StoredFile request(
            FilePurpose purpose,
            @Nullable UUID schoolId,
            UUID uploadedBy,
            String name,
            String contentType,
            long size,
            long pending,
            Instant now) {
        if (purpose.isOfSchool() != (schoolId != null)) {
            throw new IllegalArgumentException(purpose + " and school " + schoolId + " don't go together");
        }
        var plainType = purpose.ensureAccepts(contentType, size);
        if (pending >= MAX_PENDING) {
            throw new TooManyUploadsException();
        }
        return new StoredFile(purpose, schoolId, uploadedBy, checkedName(name), plainType, size, now);
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public @Nullable UUID getSchoolId() {
        return schoolId;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Whether this is the upload of that user, for one of those purposes and that school ({@code null} for a user's own
     * file).
     */
    public boolean isUploadOf(UUID userId, Set<FilePurpose> purposes, @Nullable UUID schoolId) {
        return uploadedBy.equals(userId) && purposes.contains(purpose) && Objects.equals(this.schoolId, schoolId);
    }

    /** Whether this is a confirmed file of that school, for one of those purposes. */
    public boolean isReadyFileOf(UUID schoolId, Set<FilePurpose> purposes) {
        return isReady() && purposes.contains(purpose) && schoolId.equals(this.schoolId);
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    /**
     * Makes the file usable, given what storage really holds for it. The object must be what the upload declared: the
     * same type, and no bigger than the purpose allows. Confirming a confirmed file changes nothing.
     */
    public void confirm(StoredObject object, Instant now) {
        if (isReady()) {
            return;
        }
        if (!now.isBefore(uploadExpiresAt)) {
            throw new UploadExpiredException();
        }
        if (!FilePurpose.plain(object.contentType()).equals(contentType)) {
            throw new FileTypeNotAllowedException(Set.of(contentType));
        }
        if (object.size() <= 0 || object.size() > purpose.maxSize()) {
            throw new FileTooLargeException(purpose.maxSize());
        }
        size = object.size();
        status = Status.READY;
    }

    /** Deletes the file. Its row stays until a job has removed the object from storage. */
    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
            raise(new StoredFileDeleted(getId()));
        }
    }

    /** Whether a browser shows the file in the page (an image, a video) instead of saving it. */
    public boolean isShownInline() {
        return contentType.startsWith("image/") || contentType.startsWith("video/");
    }

    /** How long a signed link to this file works. */
    public Duration linkTtl() {
        return purpose == FilePurpose.SCHOOL_VIDEO ? VIDEO_LINK_TTL : DOWNLOAD_TTL;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String checkedName(String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > MAX_NAME_LENGTH
                || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException(
                    "The file name must have 1 to " + MAX_NAME_LENGTH + " characters and no control characters");
        }
        return trimmed;
    }

    public enum Status {
        /** The client has an upload link; nothing is known about what it uploaded. */
        PENDING,
        /** Storage holds the file, and it is what the upload declared. */
        READY
    }
}
