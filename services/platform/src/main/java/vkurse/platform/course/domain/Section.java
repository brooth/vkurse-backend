/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import vkurse.core.domain.AggregateRoot;
import vkurse.platform.course.domain.CourseErrors.SectionNotEmptyException;
import vkurse.platform.course.domain.CourseErrors.TooManySectionsException;

/** A part of a course: a titled group of lessons, at a position among the course's sections. */
@Entity
@Table(name = "course_sections")
@Getter
public class Section extends AggregateRoot {
    private UUID courseId;

    private String title;

    /** Sections are shown in the order of their positions. */
    private int position;

    protected Section() {}

    private Section(UUID courseId, String title, int position, Instant now) {
        super(now);
        this.courseId = courseId;
        this.title = title;
        this.position = position;
    }

    /**
     * Adds a section at the end of a course.
     *
     * @param existing how many sections the course has
     * @param position the position after the last section
     */
    public static Section add(Course course, String title, long existing, int position, Instant now) {
        if (existing >= Course.MAX_SECTIONS) {
            throw new TooManySectionsException();
        }
        return new Section(course.getId(), Course.checkedTitle(title), position, now);
    }

    public boolean belongsTo(UUID courseId) {
        return this.courseId.equals(courseId);
    }

    public void rename(String title) {
        this.title = Course.checkedTitle(title);
    }

    /** Moves the section to a position among its course's sections. */
    public void place(int position) {
        this.position = position;
    }

    /** Checks that the section may be removed: it must have no lessons. */
    public void ensureRemovable(long lessons) {
        if (lessons > 0) {
            throw new SectionNotEmptyException();
        }
    }
}
