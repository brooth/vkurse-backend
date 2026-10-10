/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.List;
import java.util.UUID;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.Outline;
import vkurse.platform.course.domain.Section;

/** A course with its sections in order, each with its lessons in order, drafts too. */
public record OutlineResponse(CourseResponse course, List<SectionOutline> sections) {

    public static OutlineResponse of(Outline outline) {
        return new OutlineResponse(
                CourseResponse.of(outline.course()),
                outline.sections().stream()
                        .map(section -> SectionOutline.of(section, outline.lessons()))
                        .toList());
    }

    public record SectionOutline(UUID id, String title, int position, List<LessonSummaryResponse> lessons) {

        static SectionOutline of(Section section, List<Lesson> lessons) {
            return new SectionOutline(
                    section.getId(),
                    section.getTitle(),
                    section.getPosition(),
                    lessons.stream()
                            .filter(lesson -> lesson.isIn(section.getId()))
                            .map(LessonSummaryResponse::of)
                            .toList());
        }
    }
}
