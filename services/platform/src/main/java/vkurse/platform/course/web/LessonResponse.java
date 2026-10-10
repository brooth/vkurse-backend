/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonBlock;

/**
 * A lesson with its content, for the staff. A block that shows a file names it by {@code fileId}; the links come
 * from {@code POST /lessons/{id}/links}.
 */
public record LessonResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        UUID courseId,
        UUID sectionId,
        String title,
        int position,
        Lesson.Status status,
        boolean preview,
        DripResponse drip,
        boolean stop,
        List<LessonBlock> blocks) {

    public static LessonResponse of(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt(),
                lesson.getCourseId(),
                lesson.getSectionId(),
                lesson.getTitle(),
                lesson.getPosition(),
                lesson.getStatus(),
                lesson.isPreview(),
                DripResponse.of(lesson.getDrip()),
                lesson.isStop(),
                lesson.getContent().blocks());
    }
}
