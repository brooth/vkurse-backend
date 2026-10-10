/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.learning.domain.QuizAttempt;
import vkurse.platform.learning.domain.QuizAttemptRepository;

/** A student takes a lesson's quiz. */
@Service
@Transactional
public class QuizService {
    private final LearningAccess access;
    private final Progress progress;
    private final QuizAttemptRepository attempts;
    private final Clock clock;

    public QuizService(LearningAccess access, Progress progress, QuizAttemptRepository attempts, Clock clock) {
        this.access = access;
        this.progress = progress;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * Grades the caller's answers to the quiz of a lesson that is open for them, and keeps the attempt. A passed quiz
     * completes the lesson, if nothing else of it is left to do.
     *
     * @param chosen the option ids chosen, by question id
     */
    public QuizAttempt answer(UUID userId, UUID lessonId, Map<UUID, List<UUID>> chosen) {
        var studying = access.lesson(userId, lessonId, true);
        studying.ensureOpen();
        var lesson = studying.lesson();
        var quiz = lesson.getContent().quiz().orElseThrow(() -> new NotFoundException("Quiz not found"));
        var attempt = attempts.save(QuizAttempt.take(studying.enrollment(), lessonId, quiz, chosen, clock.instant()));
        progress.completeIfWorkDone(studying.enrollment(), lesson);
        return attempt;
    }

    /** The caller's attempts at a lesson's quiz, the latest first. */
    @Transactional(readOnly = true)
    public List<QuizAttempt> attempts(UUID userId, UUID lessonId) {
        var studying = access.lesson(userId, lessonId, false);
        return attempts.findByLesson(studying.enrollment().getId(), lessonId);
    }
}
