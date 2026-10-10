/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.List;
import java.util.UUID;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonBlock;

/** A free preview lesson as anyone reads it. The links to its files come from {@code POST /catalog/lessons/{id}/links}. */
public record PreviewLessonResponse(UUID id, UUID courseId, String title, List<LessonBlock> blocks) {

    public static PreviewLessonResponse of(Lesson lesson) {
        return new PreviewLessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getTitle(),
                lesson.getContent().blocks());
    }
}
