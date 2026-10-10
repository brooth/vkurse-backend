/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.course.domain.LessonBlock;

/** One try of a student at a lesson's quiz, graded when it was made. It never changes. */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt extends AggregateRoot {
    private UUID enrollmentId;

    private UUID courseId;

    private UUID lessonId;

    private UUID studentId;

    @JdbcTypeCode(SqlTypes.JSON)
    private AnswerSheet answers;

    /** How many questions were answered right. */
    private int rightCount;

    /** How many questions the quiz had. */
    private int questionCount;

    private boolean passed;

    protected QuizAttempt() {}

    private QuizAttempt(
            Enrollment enrollment, UUID lessonId, AnswerSheet answers, LessonBlock.Grade grade, Instant now) {
        super(now);
        this.enrollmentId = enrollment.getId();
        this.courseId = enrollment.getCourseId();
        this.lessonId = lessonId;
        this.studentId = enrollment.getStudentId();
        this.answers = answers;
        this.rightCount = grade.right();
        this.questionCount = grade.total();
        this.passed = grade.passed();
    }

    /**
     * Grades a student's answers to a quiz.
     *
     * @param chosen the option ids chosen, by question id
     */
    public static QuizAttempt take(
            Enrollment enrollment, UUID lessonId, LessonBlock.Quiz quiz, Map<UUID, List<UUID>> chosen, Instant now) {
        if (chosen.size() > LessonBlock.Quiz.MAX_QUESTIONS) {
            throw new BadRequestException("Too many answers");
        }
        var sets = new HashMap<UUID, Set<UUID>>();
        var kept = new HashMap<UUID, List<UUID>>();
        chosen.forEach((questionId, optionIds) -> {
            // A client's JSON can leave out what the types call non-null.
            if (questionId == null || optionIds == null || optionIds.size() > LessonBlock.Quiz.MAX_OPTIONS) {
                throw new BadRequestException("An answer is a list of the chosen options of a question");
            }
            var options = new HashSet<>(optionIds);
            sets.put(questionId, options);
            kept.put(questionId, List.copyOf(options));
        });
        var grade = quiz.grade(sets);
        return new QuizAttempt(
                enrollment, lessonId, new AnswerSheet(Map.copyOf(kept), grade.wrongQuestionIds()), grade, now);
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public AnswerSheet getAnswers() {
        return answers;
    }

    public int getRightCount() {
        return rightCount;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public boolean isPassed() {
        return passed;
    }
}
