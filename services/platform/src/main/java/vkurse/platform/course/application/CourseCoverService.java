/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.Upload;

/**
 * A course's cover: a public image. The client asks for an upload, sends the image to storage with the link, and then
 * sets it as the cover.
 */
@Service
public class CourseCoverService {
    private final CourseRepository courses;
    private final CourseAccess access;
    private final StoredFileService files;
    private final TransactionTemplate transaction;

    public CourseCoverService(
            CourseRepository courses,
            CourseAccess access,
            StoredFileService files,
            PlatformTransactionManager transactionManager) {
        this.courses = courses;
        this.access = access;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Upload requestUpload(UUID userId, UUID courseId, String name, String contentType, long size) {
        var schoolId = editable(userId, courseId).getSchoolId();
        return files.requestUpload(FilePurpose.COURSE_COVER, schoolId, userId, name, contentType, size);
    }

    /**
     * Makes an image the caller uploaded the course's cover; the cover it replaces is deleted. The upload is checked
     * against storage first, outside the transaction that changes the course.
     */
    public Course change(UUID userId, UUID courseId, UUID fileId) {
        var schoolId = editable(userId, courseId).getSchoolId();
        var file = files.confirm(fileId, userId, FilePurpose.COURSE_COVER, schoolId);
        var url = files.download(file).url().toString();
        return Objects.requireNonNull(transaction.execute(status -> {
            var course = access.editable(userId, courseId);
            var replaced = course.changeCover(file.getId(), url);
            courses.save(course);
            if (replaced != null) {
                files.delete(replaced);
            }
            return course;
        }));
    }

    @Transactional
    public void remove(UUID userId, UUID courseId) {
        var course = access.editable(userId, courseId);
        var removed = course.removeCover();
        courses.save(course);
        if (removed != null) {
            files.delete(removed);
        }
    }

    private Course editable(UUID userId, UUID courseId) {
        return Objects.requireNonNull(transaction.execute(status -> access.editable(userId, courseId)));
    }
}
