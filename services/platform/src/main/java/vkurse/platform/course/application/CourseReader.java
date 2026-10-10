/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.course.domain.Section;
import vkurse.platform.course.domain.SectionRepository;
import vkurse.platform.storage.domain.Download;

/**
 * Courses as their students get them, for the modules that show a course to someone who has it. It checks nothing
 * about the caller: who has which course is the calling module's rule.
 *
 * <p>A course is shown whether it is published or not, so that taking a course out of the catalog doesn't take it
 * away from its students; a deleted course is gone. Only published lessons are shown.
 */
@Service
public class CourseReader {
    private final CourseRepository courses;
    private final SectionRepository sections;
    private final LessonRepository lessons;
    private final LessonService lessonMedia;

    public CourseReader(
            CourseRepository courses, SectionRepository sections, LessonRepository lessons, LessonService lessonMedia) {
        this.courses = courses;
        this.sections = sections;
        this.lessons = lessons;
        this.lessonMedia = lessonMedia;
    }

    /** A course that isn't deleted. */
    @Transactional(readOnly = true)
    public Optional<Course> course(UUID courseId) {
        return courses.findById(courseId).filter(course -> !course.isDeleted());
    }

    /** A course that isn't deleted, with its sections in order and its published lessons. */
    @Transactional(readOnly = true)
    public Optional<Material> material(UUID courseId) {
        return course(courseId)
                .map(course ->
                        new Material(course, sections.findByCourse(courseId), lessons.findPublishedByCourse(courseId)));
    }

    /** A published lesson. */
    @Transactional(readOnly = true)
    public Optional<Lesson> publishedLesson(UUID lessonId) {
        return lessons.findById(lessonId).filter(Lesson::isPublished);
    }

    /** A lesson, published or not. */
    @Transactional(readOnly = true)
    public Optional<Lesson> lesson(UUID lessonId) {
        return lessons.findById(lessonId);
    }

    /** How many published lessons a course has. */
    @Transactional(readOnly = true)
    public long countPublishedLessons(UUID courseId) {
        return lessons.countPublishedByCourse(courseId);
    }

    /**
     * Signed links to the files a lesson's blocks show, by block id. Call it outside a transaction: the links are
     * signed by storage.
     */
    public Map<UUID, Download> links(UUID schoolId, Lesson lesson) {
        var content = lesson.getContent();
        return lessonMedia.links(content, lessonMedia.filesOf(schoolId, content));
    }

    /** A course, its sections in order, and its published lessons (each section's in order). */
    public record Material(Course course, List<Section> sections, List<Lesson> lessons) {}
}
