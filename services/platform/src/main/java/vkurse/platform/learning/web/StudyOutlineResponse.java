/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.application.Studying;

/** A course as its student sees it. A section with no published lesson is left out. */
public record StudyOutlineResponse(
        UUID courseId,
        UUID schoolId,
        String title,
        @Nullable String summary,
        @Nullable String description,
        @Nullable String cover,
        Instant startedAt,
        @Nullable Instant expiresAt,
        long lessons,
        long completed,
        List<SectionItem> sections) {

    public static StudyOutlineResponse of(Studying studying) {
        var course = studying.material().course();
        var curriculum = studying.curriculum();
        var sections = studying.material().sections().stream()
                .map(section -> new SectionItem(
                        section.getId(),
                        section.getTitle(),
                        curriculum.lessons().stream()
                                .filter(lesson -> lesson.isIn(section.getId()))
                                .map(lesson -> LessonItem.of(lesson, studying))
                                .toList()))
                .filter(section -> !section.lessons().isEmpty())
                .toList();
        return new StudyOutlineResponse(
                course.getId(),
                course.getSchoolId(),
                course.getTitle(),
                course.getSummary(),
                course.getDescription(),
                course.getCover(),
                studying.enrollment().getStartedAt(),
                studying.enrollment().getExpiresAt(),
                curriculum.lessons().size(),
                curriculum.completedCount(),
                sections);
    }

    public record SectionItem(UUID id, String title, List<LessonItem> lessons) {}

    public record LessonItem(
            UUID id, String title, boolean stop, boolean homework, boolean quiz, LessonStateResponse state) {

        static LessonItem of(Lesson lesson, Studying studying) {
            return new LessonItem(
                    lesson.getId(),
                    lesson.getTitle(),
                    lesson.isStop(),
                    lesson.getContent().homework().isPresent(),
                    lesson.getContent().quiz().isPresent(),
                    LessonStateResponse.of(
                            Objects.requireNonNull(studying.curriculum().stateOf(lesson.getId()))));
        }
    }
}
