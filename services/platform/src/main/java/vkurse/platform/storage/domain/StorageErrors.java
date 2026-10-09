/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

/** The storage module's client errors. */
public final class StorageErrors {

    private StorageErrors() {}

    /** 400 {@code storage/file-too-large}: the file is empty or bigger than its purpose allows. */
    public static class FileTooLargeException extends ClientException {
        public FileTooLargeException(long maxSize) {
            super(HttpStatus.BAD_REQUEST, "storage/file-too-large", "The file must have 1 to " + maxSize + " bytes");
        }
    }

    /** 400 {@code storage/file-type-not-allowed}: the file's content type is not one its purpose takes. */
    public static class FileTypeNotAllowedException extends ClientException {
        public FileTypeNotAllowedException(Set<String> allowed) {
            super(
                    HttpStatus.BAD_REQUEST,
                    "storage/file-type-not-allowed",
                    "The file must be one of: " + String.join(", ", new TreeSet<>(allowed)));
        }
    }

    /** 409 {@code storage/upload-incomplete}: the file was not uploaded to storage yet. */
    public static class UploadIncompleteException extends ClientException {
        public UploadIncompleteException() {
            super(HttpStatus.CONFLICT, "storage/upload-incomplete", "Upload the file with the upload link first");
        }
    }

    /** 400 {@code storage/upload-expired}: the upload was not finished in time. */
    public static class UploadExpiredException extends ClientException {
        public UploadExpiredException() {
            super(HttpStatus.BAD_REQUEST, "storage/upload-expired", "The upload has expired, start a new one");
        }
    }

    /** 429 {@code storage/too-many-uploads}: the user has too many uploads they never finished. */
    public static class TooManyUploadsException extends ClientException {
        public TooManyUploadsException() {
            super(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "storage/too-many-uploads",
                    "Too many unfinished uploads, finish them or try later");
        }
    }
}
