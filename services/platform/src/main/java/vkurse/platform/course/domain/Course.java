/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.course.domain.CourseErrors.NothingToPublishException;

/**
 * A course of a school: what the catalog shows, and what its {@link Section}s and {@link Lesson}s belong to. Only a
 * published course is seen outside its school's staff.
 */
@Entity
@Table(name = "courses")
public class Course extends AggregateRoot {
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_SUMMARY_LENGTH = 300;
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;

    /** How many sections and lessons one course may have. */
    public static final int MAX_SECTIONS = 100;

    public static final int MAX_LESSONS = 500;

    private UUID schoolId;

    private String title;

    /** Unique among the school's live courses. */
    @Convert(converter = CourseSlug.Converter.class)
    private CourseSlug slug;

    /** A line or two for the catalog card. */
    private @Nullable String summary;

    /** The full text of the course's page, in Markdown. */
    private @Nullable String description;

    /** The public link of the cover. */
    private @Nullable String cover;

    /** The stored file that link leads to. */
    private @Nullable UUID coverFileId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    /** When the course was last published; {@code null} while it is a draft. */
    private @Nullable Instant publishedAt;

    /** Set when the course is deleted. A deleted course can't be found, and frees its slug. */
    private @Nullable Instant deletedAt;

    protected Course() {}

    private Course(
            UUID schoolId,
            String title,
            CourseSlug slug,
            @Nullable String summary,
            @Nullable String description,
            Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.description = description;
    }

    /** Starts a course as a draft, which only the school's staff see. */
    public static Course draft(
            UUID schoolId,
            String title,
            CourseSlug slug,
            @Nullable String summary,
            @Nullable String description,
            Instant now) {
        var course = new Course(
                schoolId,
                checkedTitle(title),
                slug,
                checked(summary, MAX_SUMMARY_LENGTH, "summary"),
                checked(description, MAX_DESCRIPTION_LENGTH, "description"),
                now);
        course.raise(new CourseDrafted(course.getId(), schoolId));
        return course;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getTitle() {
        return title;
    }

    public CourseSlug getSlug() {
        return slug;
    }

    public @Nullable String getSummary() {
        return summary;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable String getCover() {
        return cover;
    }

    public @Nullable UUID getCoverFileId() {
        return coverFileId;
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable Instant getPublishedAt() {
        return publishedAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean belongsTo(UUID schoolId) {
        return this.schoolId.equals(schoolId);
    }

    /**
     * Changes what the staff may change. A {@code null} argument leaves that field as it is; a blank summary or
     * description removes it.
     */
    public void changeDetails(
            @Nullable String title, @Nullable CourseSlug slug, @Nullable String summary, @Nullable String description) {
        if (title != null) {
            this.title = checkedTitle(title);
        }
        if (slug != null) {
            this.slug = slug;
        }
        if (summary != null) {
            this.summary = checked(summary, MAX_SUMMARY_LENGTH, "summary");
        }
        if (description != null) {
            this.description = checked(description, MAX_DESCRIPTION_LENGTH, "description");
        }
    }

    /**
     * Sets the cover: a public stored file and its link. Returns the file of the cover it replaces, which the caller
     * deletes; {@code null} if there was none.
     */
    public @Nullable UUID changeCover(UUID fileId, String url) {
        if (fileId.equals(coverFileId)) {
            return null;
        }
        var replaced = coverFileId;
        cover = url;
        coverFileId = fileId;
        return replaced;
    }

    /** Removes the cover. Returns its file, which the caller deletes; {@code null} if there was none. */
    public @Nullable UUID removeCover() {
        var removed = coverFileId;
        cover = null;
        coverFileId = null;
        return removed;
    }

    /**
     * Shows the course to everyone: it appears in the catalog. Publishing a published course changes nothing.
     *
     * @param publishedLessons how many of its lessons are published
     */
    public void publish(long publishedLessons, Instant now) {
        if (isPublished()) {
            return;
        }
        if (publishedLessons == 0) {
            throw new NothingToPublishException("Publish at least one lesson of the course first");
        }
        status = Status.PUBLISHED;
        publishedAt = now;
    }

    /** Hides the course from everyone but the staff again. */
    public void unpublish() {
        status = Status.DRAFT;
        publishedAt = null;
    }

    /**
     * Checks that the course may be left with that many published lessons: a published course keeps at least one.
     */
    public void ensureKeepsPublishedLesson(long remaining) {
        if (isPublished() && remaining == 0) {
            throw new NothingToPublishException(
                    "A published course needs a published lesson: unpublish the course first");
        }
    }

    public boolean isPublished() {
        return status == Status.PUBLISHED;
    }

    /** Deletes the course. Its row stays, with its sections and lessons, but nothing finds it any more. */
    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    static String checkedTitle(String title) {
        var trimmed = title.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("The title must have 1 to " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static @Nullable String checked(@Nullable String text, int maxLength, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var trimmed = text.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException("The " + what + " must have at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public enum Status {
        /** Seen only by the school's staff. */
        DRAFT,
        /** In the catalog. */
        PUBLISHED
    }
}
