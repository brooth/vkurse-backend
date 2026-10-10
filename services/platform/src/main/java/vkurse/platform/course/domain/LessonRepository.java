/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository {

    Optional<Lesson> findById(UUID id);

    /** The lessons of a course, each section's in order. */
    List<Lesson> findByCourse(UUID courseId);

    /** The published lessons of a course, each section's in order. */
    List<Lesson> findPublishedByCourse(UUID courseId);

    long countByCourse(UUID courseId);

    long countPublishedByCourse(UUID courseId);

    long countBySection(UUID sectionId);

    /** The position after the section's last lesson. */
    int nextPosition(UUID sectionId);

    /** Whether a lesson of a live course shows that stored file. */
    boolean usesFile(UUID fileId);

    Lesson save(Lesson lesson);

    void remove(Lesson lesson);
}
