/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.course.domain.Drip;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonBlock;
import vkurse.platform.course.domain.LessonContent;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;

/** A lesson's title, content and access rules, for the staff of its course's school. */
@Service
public class LessonService {
    private final LessonRepository lessons;
    private final CourseAccess access;
    private final StoredFileService files;
    private final TransactionTemplate transaction;

    public LessonService(
            LessonRepository lessons,
            CourseAccess access,
            StoredFileService files,
            PlatformTransactionManager transactionManager) {
        this.lessons = lessons;
        this.access = access;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public Lesson get(UUID userId, UUID lessonId) {
        var lesson = lesson(lessonId);
        access.readable(userId, lesson.getCourseId());
        return lesson;
    }

    /** Changes the title and who sees the lesson when. A {@code null} argument leaves that as it is. */
    @Transactional
    public Lesson update(
            UUID userId,
            UUID lessonId,
            @Nullable String title,
            @Nullable Boolean preview,
            @Nullable Drip drip,
            @Nullable Boolean stop) {
        var lesson = lesson(lessonId);
        access.editable(userId, lesson.getCourseId());
        if (title != null) {
            lesson.rename(title);
        }
        lesson.changeAccess(preview, drip, stop);
        return lessons.save(lesson);
    }

    /**
     * Replaces the lesson's content. Each file a block shows must be a confirmed file of the school's library, and of
     * the kind the block is for.
     */
    @Transactional
    public Lesson changeContent(UUID userId, UUID lessonId, List<LessonBlock> blocks) {
        var lesson = lesson(lessonId);
        var course = access.editable(userId, lesson.getCourseId());
        var content = LessonContent.of(blocks, UUID::randomUUID);
        content.ensureFits(files.getAll(course.getSchoolId(), FilePurpose.LIBRARY, content.fileIds()));
        lesson.changeContent(content);
        return lessons.save(lesson);
    }

    @Transactional
    public Lesson publish(UUID userId, UUID lessonId) {
        var lesson = lesson(lessonId);
        access.editableLocked(userId, lesson.getCourseId());
        lesson.publish();
        return lessons.save(lesson);
    }

    @Transactional
    public Lesson unpublish(UUID userId, UUID lessonId) {
        var lesson = lesson(lessonId);
        var course = access.editableLocked(userId, lesson.getCourseId());
        if (lesson.isPublished()) {
            course.ensureKeepsPublishedLesson(lessons.countPublishedByCourse(course.getId()) - 1);
        }
        lesson.unpublish();
        return lessons.save(lesson);
    }

    /** Signed links to the files the lesson's blocks show, by block id. They are signed outside a transaction. */
    public Map<UUID, Download> links(UUID userId, UUID lessonId) {
        var shown = Objects.requireNonNull(transaction.execute(status -> {
            var lesson = lesson(lessonId);
            var course = access.readable(userId, lesson.getCourseId());
            return new Shown(lesson.getContent(), filesOf(course.getSchoolId(), lesson.getContent()));
        }));
        return links(shown.content(), shown.files());
    }

    /** The stored files a lesson's blocks show, by file id. A file that is gone is left out. */
    Map<UUID, StoredFile> filesOf(UUID schoolId, LessonContent content) {
        var found = new HashMap<UUID, StoredFile>();
        for (var fileId : content.fileIds()) {
            try {
                found.put(fileId, files.get(schoolId, FilePurpose.LIBRARY, fileId));
            } catch (NotFoundException gone) {
                // The block then gets no link.
            }
        }
        return found;
    }

    /** Signs links to those files, and returns them by the id of the block that shows each. */
    Map<UUID, Download> links(LessonContent content, Map<UUID, StoredFile> shown) {
        var byFile = files.links(shown.values());
        var byBlock = new HashMap<UUID, Download>();
        content.filesByBlock().forEach((blockId, fileId) -> {
            var link = byFile.get(fileId);
            if (link != null) {
                byBlock.put(blockId, link);
            }
        });
        return byBlock;
    }

    private Lesson lesson(UUID lessonId) {
        return lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
    }

    private record Shown(LessonContent content, Map<UUID, StoredFile> files) {}
}
