/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository {

    Optional<Section> findById(UUID id);

    /** The sections of a course, in order. */
    List<Section> findByCourse(UUID courseId);

    long countByCourse(UUID courseId);

    /** The position after the course's last section. */
    int nextPosition(UUID courseId);

    Section save(Section section);

    void remove(Section section);
}
