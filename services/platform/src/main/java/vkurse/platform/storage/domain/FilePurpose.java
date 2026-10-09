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

/** What a file is for. It decides who can read the file, how big it may be and which types it may have. */
public enum FilePurpose {
    /** A user's own picture. Anyone may see it. */
    USER_PICTURE(false, false, "picture", FilePurpose.MAX_IMAGE_SIZE, Types.IMAGES),
    /** A school's logo. Anyone may see it. */
    SCHOOL_LOGO(true, false, "logo", FilePurpose.MAX_IMAGE_SIZE, Types.IMAGES),
    /** A file in a school's library: lesson materials. Downloaded only with a signed link. */
    SCHOOL_FILE(true, true, "files", FilePurpose.MAX_LIBRARY_FILE_SIZE, Types.LIBRARY);

    /** 5 MB. */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 50 MB: what one plain upload to storage can take. Bigger files need resumable uploads. */
    public static final long MAX_LIBRARY_FILE_SIZE = 50L * 1024 * 1024;

    private final boolean ofSchool;
    private final boolean secret;
    private final String folder;
    private final long maxSize;
    private final Map<String, String> extensions;

    FilePurpose(boolean ofSchool, boolean secret, String folder, long maxSize, Map<String, String> extensions) {
        this.ofSchool = ofSchool;
        this.secret = secret;
        this.folder = folder;
        this.maxSize = maxSize;
        this.extensions = extensions;
    }

    /** Whether a file of this purpose belongs to a school; otherwise it belongs to the user who uploaded it. */
    public boolean isOfSchool() {
        return ofSchool;
    }

    /** Whether anyone may read a file of this purpose by its plain link. A file that is not public needs a signed one. */
    public boolean isPublic() {
        return !secret;
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

    /** The largest file among the purposes that are public, or among those that are not. */
    public static long maxSize(boolean isPublic) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.isPublic() == isPublic)
                .mapToLong(FilePurpose::maxSize)
                .max()
                .orElse(0);
    }

    /** Every content type of the purposes that are public, or of those that are not. */
    public static Set<String> contentTypes(boolean isPublic) {
        var types = new TreeSet<String>();
        Arrays.stream(values())
                .filter(purpose -> purpose.isPublic() == isPublic)
                .forEach(purpose -> types.addAll(purpose.contentTypes()));
        return types;
    }

    static String plain(String contentType) {
        var end = contentType.indexOf(';');
        return (end < 0 ? contentType : contentType.substring(0, end)).trim().toLowerCase(Locale.ROOT);
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
                Map.entry("audio/mpeg", "mp3"),
                Map.entry("video/mp4", "mp4"));
    }
}
