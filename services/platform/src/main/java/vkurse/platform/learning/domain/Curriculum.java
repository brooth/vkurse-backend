/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.Section;
import vkurse.platform.learning.domain.LearningErrors.LessonLockedException;

/**
 * A course as one student has it now: its published lessons in the course's order, and whether each is open.
 *
 * <p>A lesson is open when its drip time has come and every stop lesson before it is completed. A lesson the student
 * completed stays open whatever changes later.
 */
public final class Curriculum {
    private final List<Lesson> lessons;
    private final Map<UUID, LessonState> states;

    private Curriculum(List<Lesson> lessons, Map<UUID, LessonState> states) {
        this.lessons = lessons;
        this.states = states;
    }

    /**
     * @param sections the course's sections
     * @param published the course's published lessons
     * @param completed the lessons the student completed
     */
    public static Curriculum of(
            List<Section> sections, List<Lesson> published, Enrollment enrollment, Set<UUID> completed, Instant now) {
        var sectionOrder = new HashMap<UUID, Integer>();
        sections.stream()
                .sorted(Comparator.comparingInt(Section::getPosition).thenComparing(Section::getId))
                .forEach(section -> sectionOrder.put(section.getId(), sectionOrder.size()));
        var ordered = new ArrayList<>(published);
        ordered.sort(Comparator.comparingInt(
                        (Lesson lesson) -> sectionOrder.getOrDefault(lesson.getSectionId(), Integer.MAX_VALUE))
                .thenComparingInt(Lesson::getPosition)
                .thenComparing(Lesson::getId));

        var states = new LinkedHashMap<UUID, LessonState>();
        UUID stop = null;
        for (var lesson : ordered) {
            var done = completed.contains(lesson.getId());
            var opensAt = lesson.getDrip().opensAt(enrollment.getStartedAt());
            LessonState state;
            if (done) {
                state = LessonState.COMPLETED;
            } else if (stop != null) {
                state = new LessonState(LessonState.Kind.LOCKED, null, stop);
            } else if (now.isBefore(opensAt)) {
                state = new LessonState(LessonState.Kind.LOCKED, opensAt, null);
            } else {
                state = LessonState.OPEN;
            }
            states.put(lesson.getId(), state);
            if (stop == null && lesson.isStop() && !done) {
                stop = lesson.getId();
            }
        }
        return new Curriculum(List.copyOf(ordered), states);
    }

    /** The published lessons, in the course's order. */
    public List<Lesson> lessons() {
        return lessons;
    }

    /** The state of a published lesson of the course; {@code null} for any other lesson. */
    public @Nullable LessonState stateOf(UUID lessonId) {
        return states.get(lessonId);
    }

    public boolean isCompleted(UUID lessonId) {
        var state = states.get(lessonId);
        return state != null && state.kind() == LessonState.Kind.COMPLETED;
    }

    /** How many of the lessons the student completed. */
    public long completedCount() {
        return states.values().stream()
                .filter(state -> state.kind() == LessonState.Kind.COMPLETED)
                .count();
    }

    /** Checks that the student may read the lesson and work on it now. */
    public LessonState ensureOpen(UUID lessonId) {
        var state = states.get(lessonId);
        if (state == null) {
            throw new IllegalArgumentException("Lesson " + lessonId + " is not of this course");
        }
        if (state.kind() == LessonState.Kind.LOCKED) {
            throw new LessonLockedException(state.opensAt(), state.blockedBy());
        }
        return state;
    }

    /**
     * @param opensAt when a locked lesson opens, if time is what holds it
     * @param blockedBy the stop lesson to complete first, if that is what holds it
     */
    public record LessonState(
            Kind kind, @Nullable Instant opensAt, @Nullable UUID blockedBy) {
        static final LessonState OPEN = new LessonState(Kind.OPEN, null, null);
        static final LessonState COMPLETED = new LessonState(Kind.COMPLETED, null, null);

        public enum Kind {
            OPEN,
            COMPLETED,
            LOCKED
        }
    }
}
