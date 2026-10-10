/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vkurse.platform.course.domain.CourseErrors.FileInUseException;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.storage.domain.StoredFileDeleted;

/**
 * A stored file that a lesson shows can't be deleted. This is a plain {@code @EventListener}, so it runs in the
 * transaction that deletes the file, and its exception rolls the deletion back.
 */
@Component
class FileInUseListener {
    private final LessonRepository lessons;

    FileInUseListener(LessonRepository lessons) {
        this.lessons = lessons;
    }

    @EventListener
    @Transactional
    public void on(StoredFileDeleted event) {
        if (lessons.usesFile(event.fileId())) {
            throw new FileInUseException();
        }
    }
}
