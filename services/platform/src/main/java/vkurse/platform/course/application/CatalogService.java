/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.course.domain.CourseCard;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.course.domain.CourseSlug;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonContent;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.course.domain.Section;
import vkurse.platform.course.domain.SectionRepository;
import vkurse.platform.school.application.SchoolService;
import vkurse.platform.school.domain.Slug;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.StoredFile;

/**
 * What anyone may see, signed in or not: the published courses of live schools, their outlines, and the lessons
 * marked as a free preview.
 */
@Service
public class CatalogService {
    private final CourseRepository courses;
    private final SectionRepository sections;
    private final LessonRepository lessons;
    private final SchoolService schools;
    private final LessonService lessonMedia;
    private final TransactionTemplate transaction;

    public CatalogService(
            CourseRepository courses,
            SectionRepository sections,
            LessonRepository lessons,
            SchoolService schools,
            LessonService lessonMedia,
            PlatformTransactionManager transactionManager) {
        this.courses = courses;
        this.sections = sections;
        this.lessons = lessons;
        this.schools = schools;
        this.lessonMedia = lessonMedia;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Published courses: with {@code $search}, those that match it, best first; without, the latest first.
     *
     * @param schoolSlug only the courses of that school; {@code null} for every school
     */
    @Transactional(readOnly = true)
    public List<CourseCard> search(@Nullable String schoolSlug, Paging paging) {
        var schoolId = schoolSlug == null ? null : schools.getBySlug(schoolSlug).getId();
        return courses.findPublished(paging.search(), schoolId, paging.limit(), paging.offset());
    }

    /** A published course's page: the course, and its sections with their published lessons. */
    @Transactional(readOnly = true)
    public Page course(String schoolSlug, String courseSlug) {
        var card = courses.findPublished(new Slug(schoolSlug), new CourseSlug(courseSlug))
                .orElseThrow(CourseAccess::notFound);
        var courseId = card.course().getId();
        return new Page(card, sections.findByCourse(courseId), lessons.findPublishedByCourse(courseId));
    }

    /** A published course of a live school, by id; empty if there is none. For modules that sell or show courses. */
    @Transactional(readOnly = true)
    public Optional<CourseCard> publishedCourse(UUID courseId) {
        return courses.findPublished(courseId);
    }

    /** Those of the courses that are published, of live schools, by id. For modules that link to a course's page. */
    @Transactional(readOnly = true)
    public Map<UUID, CourseCard> publishedCourses(Collection<UUID> courseIds) {
        return courses.findPublished(Set.copyOf(courseIds)).stream()
                .collect(Collectors.toMap(card -> card.course().getId(), Function.identity()));
    }

    /** A lesson anyone may read: a free preview of a published course. */
    @Transactional(readOnly = true)
    public Lesson previewLesson(UUID lessonId) {
        return preview(lessonId).lesson();
    }

    /** Signed links to the files a free preview lesson shows, by block id. Signed outside a transaction. */
    public Map<UUID, Download> previewLinks(UUID lessonId) {
        var shown = Objects.requireNonNull(transaction.execute(status -> {
            var preview = preview(lessonId);
            var content = preview.lesson().getContent();
            return new Shown(
                    content, lessonMedia.filesOf(preview.card().course().getSchoolId(), content));
        }));
        return lessonMedia.links(shown.content(), shown.files());
    }

    private Preview preview(UUID lessonId) {
        var lesson = lessons.findById(lessonId)
                .filter(Lesson::isFreePreview)
                .orElseThrow(() -> new NotFoundException("Lesson not found"));
        var card = courses.findPublished(lesson.getCourseId())
                .orElseThrow(() -> new NotFoundException("Lesson not found"));
        return new Preview(lesson, card);
    }

    /** A published course's page: its card, its sections in order, and its published lessons. */
    public record Page(CourseCard card, List<Section> sections, List<Lesson> lessons) {}

    private record Preview(Lesson lesson, CourseCard card) {}

    private record Shown(LessonContent content, Map<UUID, StoredFile> files) {}
}
