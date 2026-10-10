/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.platform.course.domain.CourseErrors.NothingToPublishException;
import vkurse.platform.course.domain.CourseErrors.TooManyLessonsException;

/**
 * A lesson of a course, in one of its sections: a title, the content, and who may see it when. Only a published
 * lesson is seen outside the school's staff.
 */
@Entity
@Table(name = "course_lessons")
@Getter
public class Lesson extends AggregateRoot {
    private UUID courseId;

    private UUID sectionId;

    private String title;

    /** Lessons of a section are shown in the order of their positions. */
    private int position;

    @JdbcTypeCode(SqlTypes.JSON)
    private LessonContent content = LessonContent.EMPTY;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    /** A free preview: anyone may read the lesson once it and its course are published. */
    private boolean preview;

    /** When the lesson opens for a student who has the course. */
    @Embedded
    private Drip drip = Drip.IMMEDIATELY;

    /** A stop lesson: the lessons after it stay closed for a student until their work on this one is accepted. */
    private boolean stop;

    protected Lesson() {}

    private Lesson(UUID courseId, UUID sectionId, String title, int position, Instant now) {
        super(now);
        this.courseId = courseId;
        this.sectionId = sectionId;
        this.title = title;
        this.position = position;
    }

    /**
     * Starts a lesson as a draft at the end of a section.
     *
     * @param existing how many lessons the course has
     * @param position the position after the section's last lesson
     */
    public static Lesson draft(Section section, String title, long existing, int position, Instant now) {
        if (existing >= Course.MAX_LESSONS) {
            throw new TooManyLessonsException();
        }
        return new Lesson(section.getCourseId(), section.getId(), Course.checkedTitle(title), position, now);
    }

    public boolean belongsTo(UUID courseId) {
        return this.courseId.equals(courseId);
    }

    public boolean isIn(UUID sectionId) {
        return this.sectionId.equals(sectionId);
    }

    public void rename(String title) {
        this.title = Course.checkedTitle(title);
    }

    public void changeContent(LessonContent content) {
        if (content.isEmpty() && isPublished()) {
            throw new NothingToPublishException("A published lesson needs content: unpublish the lesson first");
        }
        this.content = content;
    }

    /** Changes who sees the lesson when. A {@code null} argument leaves that setting as it is. */
    public void changeAccess(@Nullable Boolean preview, @Nullable Drip drip, @Nullable Boolean stop) {
        if (preview != null) {
            this.preview = preview;
        }
        if (drip != null) {
            this.drip = drip;
        }
        if (stop != null) {
            this.stop = stop;
        }
    }

    /** Shows the lesson outside the staff. Publishing a published lesson changes nothing. */
    public void publish() {
        if (content.isEmpty()) {
            throw new NothingToPublishException("Add content to the lesson first");
        }
        status = Status.PUBLISHED;
    }

    public void unpublish() {
        status = Status.DRAFT;
    }

    public boolean isPublished() {
        return status == Status.PUBLISHED;
    }

    /** Whether anyone may read the lesson, given that its course is published. */
    public boolean isFreePreview() {
        return isPublished() && preview;
    }

    /** Moves the lesson to a position in a section of its course. */
    public void place(Section section, int position) {
        if (!section.belongsTo(courseId)) {
            throw new IllegalArgumentException("The section is of another course");
        }
        this.sectionId = section.getId();
        this.position = position;
    }

    public enum Status {
        DRAFT,
        PUBLISHED
    }
}
