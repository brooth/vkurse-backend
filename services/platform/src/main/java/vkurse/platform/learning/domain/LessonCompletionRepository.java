/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface LessonCompletionRepository {

    /** The lessons completed in an enrollment. */
    Set<UUID> findLessonIds(UUID enrollmentId);

    boolean exists(UUID enrollmentId, UUID lessonId);

    /** The lessons completed in each of those enrollments. */
    Map<UUID, Set<UUID>> findLessonIds(Collection<UUID> enrollmentIds);

    LessonCompletion save(LessonCompletion completion);
}
