/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vkurse.core.domain.AggregateRoot;

/**
 * The chat of a course. There is one per course, made with it. Nobody joins or leaves: whoever is on the staff of the
 * course's school, or has the course now, is in it.
 */
@Entity
@Table(name = "chats")
public class Chat extends AggregateRoot {
    private UUID schoolId;

    private UUID courseId;

    protected Chat() {}

    private Chat(UUID schoolId, UUID courseId, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.courseId = courseId;
    }

    /** Opens the chat of a course. */
    public static Chat open(UUID schoolId, UUID courseId, Instant now) {
        return new Chat(schoolId, courseId, now);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getCourseId() {
        return courseId;
    }
}
