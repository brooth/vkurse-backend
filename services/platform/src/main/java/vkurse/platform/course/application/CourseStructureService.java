/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.course.domain.Arrangement;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.course.domain.Outline;
import vkurse.platform.course.domain.Section;
import vkurse.platform.course.domain.SectionRepository;

/**
 * The structure of a course: its sections, the lessons in them, and their order. Every change locks the course, so
 * two of them don't interleave.
 */
@Service
@Transactional
public class CourseStructureService {
    private final SectionRepository sections;
    private final LessonRepository lessons;
    private final CourseAccess access;
    private final Clock clock;

    public CourseStructureService(
            SectionRepository sections, LessonRepository lessons, CourseAccess access, Clock clock) {
        this.sections = sections;
        this.lessons = lessons;
        this.access = access;
        this.clock = clock;
    }

    /** The course with its sections and lessons, drafts too. */
    @Transactional(readOnly = true)
    public Outline outline(UUID userId, UUID courseId) {
        var course = access.readable(userId, courseId);
        return new Outline(course, sections.findByCourse(courseId), lessons.findByCourse(courseId));
    }

    /** Adds a section at the end of the course. */
    public Section addSection(UUID userId, UUID courseId, String title) {
        var course = access.editableLocked(userId, courseId);
        return sections.save(Section.add(
                course, title, sections.countByCourse(courseId), sections.nextPosition(courseId), clock.instant()));
    }

    public Section renameSection(UUID userId, UUID sectionId, String title) {
        var section = section(sectionId);
        access.editable(userId, section.getCourseId());
        section.rename(title);
        return sections.save(section);
    }

    public void removeSection(UUID userId, UUID sectionId) {
        var section = section(sectionId);
        access.editableLocked(userId, section.getCourseId());
        section.ensureRemovable(lessons.countBySection(sectionId));
        sections.remove(section);
    }

    /** Puts the sections of the course in the order of {@code sectionIds}, which must name each of them once. */
    public List<Section> arrangeSections(UUID userId, UUID courseId, List<UUID> sectionIds) {
        access.editableLocked(userId, courseId);
        var byId = sections.findByCourse(courseId).stream()
                .collect(Collectors.toMap(AggregateRoot::getId, Function.identity()));
        Arrangement.check(sectionIds, byId.keySet(), byId.keySet());
        for (var position = 0; position < sectionIds.size(); position++) {
            var section = byId.get(sectionIds.get(position));
            section.place(position);
            sections.save(section);
        }
        return sections.findByCourse(courseId);
    }

    /** Starts a lesson at the end of the section, as a draft. */
    public Lesson draftLesson(UUID userId, UUID sectionId, String title) {
        var section = section(sectionId);
        access.editableLocked(userId, section.getCourseId());
        return lessons.save(Lesson.draft(
                section,
                title,
                lessons.countByCourse(section.getCourseId()),
                lessons.nextPosition(sectionId),
                clock.instant()));
    }

    public void removeLesson(UUID userId, UUID lessonId) {
        var lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
        var course = access.editableLocked(userId, lesson.getCourseId());
        if (lesson.isPublished()) {
            course.ensureKeepsPublishedLesson(lessons.countPublishedByCourse(course.getId()) - 1);
        }
        lessons.remove(lesson);
    }

    /**
     * Puts the lessons of the section in the order of {@code lessonIds}. The list must name every lesson the section
     * has, and may name lessons of the course's other sections: those move into this one.
     */
    public List<Lesson> arrangeLessons(UUID userId, UUID sectionId, List<UUID> lessonIds) {
        var section = section(sectionId);
        access.editableLocked(userId, section.getCourseId());
        var byId = lessons.findByCourse(section.getCourseId()).stream()
                .collect(Collectors.toMap(AggregateRoot::getId, Function.identity()));
        var here = byId.values().stream()
                .filter(lesson -> lesson.isIn(sectionId))
                .map(AggregateRoot::getId)
                .toList();
        Arrangement.check(lessonIds, here, byId.keySet());
        for (var position = 0; position < lessonIds.size(); position++) {
            var lesson = byId.get(lessonIds.get(position));
            lesson.place(section, position);
            lessons.save(lesson);
        }
        return lessonIds.stream().map(byId::get).toList();
    }

    private Section section(UUID sectionId) {
        return sections.findById(sectionId).orElseThrow(() -> new NotFoundException("Section not found"));
    }
}
