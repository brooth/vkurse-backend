/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptRepository {

    /** The student's attempts at a lesson's quiz, the latest first. */
    List<QuizAttempt> findByLesson(UUID enrollmentId, UUID lessonId);

    boolean hasPassed(UUID enrollmentId, UUID lessonId);

    QuizAttempt save(QuizAttempt attempt);
}
