/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import vkurse.platform.storage.domain.StorageErrors.FileTooLargeException;
import vkurse.platform.storage.domain.StorageErrors.FileTypeNotAllowedException;

/** What a file is for. It decides who can read the file, how big it may be, which types it may have and how it is uploaded. */
public enum FilePurpose {
    /** A user's own picture. Anyone may see it. */
    USER_PICTURE(false, Bucket.PUBLIC, "picture", FilePurpose.MAX_IMAGE_SIZE, Types.IMAGES),
    /** A school's logo. Anyone may see it. */
    SCHOOL_LOGO(true, Bucket.PUBLIC, "logo", FilePurpose.MAX_IMAGE_SIZE, Types.IMAGES),
    /** A course's cover. Anyone may see it. */
    COURSE_COVER(true, Bucket.PUBLIC, "covers", FilePurpose.MAX_IMAGE_SIZE, Types.IMAGES),
    /** A file in a school's library: lesson materials. Read only with a signed link. */
    SCHOOL_FILE(true, Bucket.PRIVATE, "files", FilePurpose.MAX_LIBRARY_FILE_SIZE, Types.LIBRARY),
    /** A file a student hands in with homework. It is kept for the school; read only with a signed link. */
    HOMEWORK_FILE(true, Bucket.PRIVATE, "homework", FilePurpose.MAX_LIBRARY_FILE_SIZE, Types.LIBRARY),
    /** A video in a school's library. Read only with a signed link; too big for one request, so its upload resumes. */
    SCHOOL_VIDEO(true, Bucket.VIDEO, "videos", FilePurpose.MAX_VIDEO_SIZE, Types.VIDEOS);

    /** 5 MB. */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 50 MB: what one plain upload to storage can take. */
    public static final long MAX_LIBRARY_FILE_SIZE = 50L * 1024 * 1024;

    /** 2 GB. */
    public static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024;

    /** What a school's library holds. */
    public static final Set<FilePurpose> LIBRARY = Set.of(SCHOOL_FILE, SCHOOL_VIDEO);

    private final boolean ofSchool;
    private final Bucket bucket;
    private final String folder;
    private final long maxSize;
    private final Map<String, String> extensions;

    FilePurpose(boolean ofSchool, Bucket bucket, String folder, long maxSize, Map<String, String> extensions) {
        this.ofSchool = ofSchool;
        this.bucket = bucket;
        this.folder = folder;
        this.maxSize = maxSize;
        this.extensions = extensions;
    }

    /** What a file of that content type is in a school's library: a video, or any other file. */
    public static FilePurpose inLibrary(String contentType) {
        return plain(contentType).startsWith("video/") ? SCHOOL_VIDEO : SCHOOL_FILE;
    }

    /** Whether a file of this purpose belongs to a school; otherwise it belongs to the user who uploaded it. */
    public boolean isOfSchool() {
        return ofSchool;
    }

    /** Which bucket keeps the files of this purpose. */
    public Bucket bucket() {
        return bucket;
    }

    /** Whether anyone may read a file of this purpose by its plain link. A file that is not public needs a signed one. */
    public boolean isPublic() {
        return bucket == Bucket.PUBLIC;
    }

    /** Whether a file of this purpose is uploaded in pieces that can resume, not in one request. */
    public boolean isResumable() {
        return bucket == Bucket.VIDEO;
    }

    /** The largest file of this purpose, in bytes. */
    public long maxSize() {
        return maxSize;
    }

    /** The content types a file of this purpose may have. */
    public Set<String> contentTypes() {
        return extensions.keySet();
    }

    /**
     * Checks a file's type and size against this purpose, and returns the type in its plain form: lowercase and without
     * parameters ({@code text/plain; charset=utf-8} is {@code text/plain}).
     */
    public String ensureAccepts(String contentType, long size) {
        var plain = plain(contentType);
        if (!extensions.containsKey(plain)) {
            throw new FileTypeNotAllowedException(contentTypes());
        }
        if (size <= 0 || size > maxSize) {
            throw new FileTooLargeException(maxSize);
        }
        return plain;
    }

    /** Where a file of this purpose is kept: built from ids only, never from what the client sent. */
    String path(String owner, String fileId, String contentType) {
        return (ofSchool ? "schools/" : "users/") + owner + "/" + folder + "/" + fileId + "."
                + extensions.get(contentType);
    }

    static String plain(String contentType) {
        var end = contentType.indexOf(';');
        return (end < 0 ? contentType : contentType.substring(0, end)).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Where files are kept. Each bucket has its own size limit and content types in storage itself: those of the
     * purposes it holds.
     */
    public enum Bucket {
        /** Read by anyone, with a lasting link. */
        PUBLIC,
        /** Read with a signed link. */
        PRIVATE,
        /** Video: read with a signed link, and big. */
        VIDEO;

        /** The largest file this bucket takes: the largest of its purposes. */
        public long maxSize() {
            return Arrays.stream(FilePurpose.values())
                    .filter(purpose -> purpose.bucket == this)
                    .mapToLong(FilePurpose::maxSize)
                    .max()
                    .orElse(0);
        }

        /** Every content type of this bucket's purposes. */
        public Set<String> contentTypes() {
            var types = new TreeSet<String>();
            Arrays.stream(FilePurpose.values())
                    .filter(purpose -> purpose.bucket == this)
                    .forEach(purpose -> types.addAll(purpose.contentTypes()));
            return types;
        }
    }

    /** Content type -> file extension. No SVG or HTML: a browser would run scripts in them. */
    private static final class Types {
        static final Map<String, String> IMAGES = Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");
        static final Map<String, String> LIBRARY = Map.ofEntries(
                Map.entry("image/png", "png"),
                Map.entry("image/jpeg", "jpg"),
                Map.entry("image/webp", "webp"),
                Map.entry("application/pdf", "pdf"),
                Map.entry("text/plain", "txt"),
                Map.entry("application/zip", "zip"),
                Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
                Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
                Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
                Map.entry("audio/mpeg", "mp3"));
        static final Map<String, String> VIDEOS =
                Map.of("video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");
    }
}
