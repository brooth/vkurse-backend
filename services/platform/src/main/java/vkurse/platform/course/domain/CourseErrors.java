/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

/** The course module's client errors. */
public final class CourseErrors {

    private CourseErrors() {}

    /** 409 {@code course/slug-taken}: another course of the school has this address. */
    public static class SlugTakenException extends ClientException {
        public SlugTakenException() {
            super(HttpStatus.CONFLICT, "course/slug-taken", "This address is taken by another course of the school");
        }
    }

    /**
     * 409 {@code course/nothing-to-publish}: a course with no published lesson, or a lesson with no content, can't be
     * published; and a published course can't lose its last published lesson.
     */
    public static class NothingToPublishException extends ClientException {
        public NothingToPublishException(String message) {
            super(HttpStatus.CONFLICT, "course/nothing-to-publish", message);
        }
    }

    /** 409 {@code course/section-not-empty}: a section that has lessons can't be removed. */
    public static class SectionNotEmptyException extends ClientException {
        public SectionNotEmptyException() {
            super(HttpStatus.CONFLICT, "course/section-not-empty", "Move or remove the section's lessons first");
        }
    }

    /** 409 {@code course/file-in-use}: a file that a lesson shows can't be deleted. */
    public static class FileInUseException extends ClientException {
        public FileInUseException() {
            super(
                    HttpStatus.CONFLICT,
                    "course/file-in-use",
                    "A lesson uses this file, remove it from the lesson first");
        }
    }

    /**
     * 400 {@code course/unsupported-video-link}: what was pasted is not a link to a video on Rutube, VK Video or
     * YouTube, nor their code to embed one.
     */
    public static class UnsupportedVideoLinkException extends ClientException {
        public UnsupportedVideoLinkException() {
            super(
                    HttpStatus.BAD_REQUEST,
                    "course/unsupported-video-link",
                    "Paste a link to a video on Rutube, VK Video or YouTube, or the code to embed it");
        }
    }

    /** 429 {@code course/too-many-sections}: the course has as many sections as one may have. */
    public static class TooManySectionsException extends ClientException {
        public TooManySectionsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "course/too-many-sections", "The course has too many sections");
        }
    }

    /** 429 {@code course/too-many-lessons}: the course has as many lessons as one may have. */
    public static class TooManyLessonsException extends ClientException {
        public TooManyLessonsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "course/too-many-lessons", "The course has too many lessons");
        }
    }
}
