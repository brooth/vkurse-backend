/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.UUID;
import vkurse.platform.course.domain.Lesson;

/** A lesson in an outline, for the staff: everything but its content. */
public record LessonSummaryResponse(
        UUID id,
        UUID sectionId,
        String title,
        int position,
        Lesson.Status status,
        boolean preview,
        DripResponse drip,
        boolean stop) {

    public static LessonSummaryResponse of(Lesson lesson) {
        return new LessonSummaryResponse(
                lesson.getId(),
                lesson.getSectionId(),
                lesson.getTitle(),
                lesson.getPosition(),
                lesson.getStatus(),
                lesson.isPreview(),
                DripResponse.of(lesson.getDrip()),
                lesson.isStop());
    }
}
