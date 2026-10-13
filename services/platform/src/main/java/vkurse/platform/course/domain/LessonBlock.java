/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * One piece of a lesson. A block has an id of its own, so a client can tell blocks apart when the lesson changes; a
 * block that comes without one is given one (see {@link LessonContent#of}).
 *
 * <p>The records check nothing themselves, because they are also read back from the database: a list of blocks is
 * checked as a whole by {@link LessonContent}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LessonBlock.Text.class, name = "text"),
    @JsonSubTypes.Type(value = LessonBlock.Video.class, name = "video"),
    @JsonSubTypes.Type(value = LessonBlock.Embed.class, name = "embed"),
    @JsonSubTypes.Type(value = LessonBlock.Image.class, name = "image"),
    @JsonSubTypes.Type(value = LessonBlock.File.class, name = "file"),
    @JsonSubTypes.Type(value = LessonBlock.Homework.class, name = "homework"),
    @JsonSubTypes.Type(value = LessonBlock.Quiz.class, name = "quiz")
})
public sealed interface LessonBlock {

    @Nullable
    UUID id();

    /** The same block with that id. */
    LessonBlock withId(UUID id);

    /** The stored file the block shows; {@code null} for a block without one. */
    default @Nullable UUID fileId() {
        return null;
    }

    /** Text in Markdown. */
    record Text(@Nullable UUID id, String text) implements LessonBlock {
        @Override
        public Text withId(UUID id) {
            return new Text(id, text);
        }
    }

    /** A video from the school's library. */
    record Video(@Nullable UUID id, UUID fileId) implements LessonBlock {
        @Override
        public Video withId(UUID id) {
            return new Video(id, fileId);
        }
    }

    /**
     * A video on an outside host, shown in that host's player. An author sends only {@code url}: a link to the video,
     * or the host's code to embed it. The rest is read from it (see {@link VideoLink}); {@code url} is kept as the
     * link, never as code.
     */
    record Embed(
            @Nullable UUID id,
            String url,
            VideoLink.@Nullable Provider provider,
            @Nullable String videoId,
            @Nullable String key)
            implements LessonBlock {
        @Override
        public Embed withId(UUID id) {
            return new Embed(id, url, provider, videoId, key);
        }

        /** The block as an author may save it: with what its link says, whatever came with it. */
        Embed checked() {
            var link = VideoLink.parse(url);
            return new Embed(id, link.url(), link.provider(), link.videoId(), link.key());
        }

        /**
         * The address an {@code iframe} shows. It is written with the block and never read back: it is worked out
         * again each time, so what a client sends as one is ignored.
         */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public @Nullable String embedUrl() {
            return provider == null || videoId == null ? null : new VideoLink(provider, videoId, key, url).embedUrl();
        }
    }

    /** An image from the school's library, shown in the lesson. */
    record Image(@Nullable UUID id, UUID fileId) implements LessonBlock {
        @Override
        public Image withId(UUID id) {
            return new Image(id, fileId);
        }
    }

    /** A file from the school's library, to download. */
    record File(@Nullable UUID id, UUID fileId) implements LessonBlock {
        @Override
        public File withId(UUID id) {
            return new File(id, fileId);
        }
    }

    /** A task the student answers with text and files, and the staff review. {@code task} is Markdown. */
    record Homework(@Nullable UUID id, String task) implements LessonBlock {
        @Override
        public Homework withId(UUID id) {
            return new Homework(id, task);
        }
    }

    /**
     * Questions the student answers by choosing options, checked at once. The quiz is passed when at least
     * {@code passPercent} of its questions are answered right.
     */
    record Quiz(@Nullable UUID id, int passPercent, List<Question> questions) implements LessonBlock {
        public static final int MAX_QUESTIONS = 50;
        public static final int MIN_OPTIONS = 2;
        public static final int MAX_OPTIONS = 10;
        public static final int MAX_QUESTION_LENGTH = 2000;
        public static final int MAX_OPTION_LENGTH = 500;

        @Override
        public Quiz withId(UUID id) {
            return new Quiz(id, passPercent, questions);
        }

        /**
         * The quiz as an author may save it: checked, and with an id for each question and option that has none.
         *
         * @param newId makes an id
         */
        Quiz checked(Supplier<UUID> newId) {
            if (passPercent < 1 || passPercent > 100) {
                throw new BadRequestException("A quiz is passed with 1 to 100 percent of its questions");
            }
            // A client's JSON can leave out what the types call non-null.
            if (questions == null || questions.isEmpty() || questions.size() > MAX_QUESTIONS) {
                throw new BadRequestException("A quiz has 1 to " + MAX_QUESTIONS + " questions");
            }
            var ids = new HashSet<UUID>();
            var checked = new ArrayList<Question>();
            for (var question : questions) {
                if (question == null) {
                    throw new BadRequestException("A question is missing");
                }
                var identified = question.checked(newId);
                if (!ids.add(identified.id())) {
                    throw new BadRequestException("Two questions have the same id");
                }
                checked.add(identified);
            }
            return new Quiz(id, passPercent, List.copyOf(checked));
        }

        /** The quiz as a student sees it: no option says whether it is right. */
        public Quiz withoutAnswers() {
            return new Quiz(
                    id,
                    passPercent,
                    questions.stream().map(Question::withoutAnswers).toList());
        }

        /**
         * Checks a student's answers. A question is right when exactly its right options are chosen; one left out is
         * wrong.
         *
         * @param chosen the option ids chosen, by question id
         */
        public Grade grade(Map<UUID, ? extends Set<UUID>> chosen) {
            var wrong = new ArrayList<UUID>();
            for (var question : questions) {
                Set<UUID> answer = chosen.get(question.id());
                if (answer == null || !answer.equals(question.rightOptions())) {
                    wrong.add(question.id());
                }
            }
            var right = questions.size() - wrong.size();
            return new Grade(
                    right, questions.size(), right * 100 >= passPercent * questions.size(), List.copyOf(wrong));
        }
    }

    /** One question of a quiz. {@code SINGLE} has one right option; {@code MULTIPLE} has one or more. */
    record Question(@Nullable UUID id, String text, Kind kind, List<Option> options) {

        Question checked(Supplier<UUID> newId) {
            if (text == null || text.isBlank() || text.length() > Quiz.MAX_QUESTION_LENGTH) {
                throw new BadRequestException("A question must have 1 to " + Quiz.MAX_QUESTION_LENGTH + " characters");
            }
            if (kind == null) {
                throw new BadRequestException("A question needs its kind");
            }
            if (options == null || options.size() < Quiz.MIN_OPTIONS || options.size() > Quiz.MAX_OPTIONS) {
                throw new BadRequestException(
                        "A question has " + Quiz.MIN_OPTIONS + " to " + Quiz.MAX_OPTIONS + " options");
            }
            var ids = new HashSet<UUID>();
            var checked = new ArrayList<Option>();
            var right = 0;
            for (var option : options) {
                if (option == null
                        || option.text() == null
                        || option.text().isBlank()
                        || option.text().length() > Quiz.MAX_OPTION_LENGTH) {
                    throw new BadRequestException("An option must have 1 to " + Quiz.MAX_OPTION_LENGTH + " characters");
                }
                var identified = new Option(
                        option.id() == null ? newId.get() : option.id(),
                        option.text(),
                        Boolean.TRUE.equals(option.correct()));
                if (!ids.add(identified.id())) {
                    throw new BadRequestException("Two options have the same id");
                }
                if (Boolean.TRUE.equals(identified.correct())) {
                    right++;
                }
                checked.add(identified);
            }
            if (right == 0 || (kind == Kind.SINGLE && right != 1)) {
                throw new BadRequestException(
                        kind == Kind.SINGLE
                                ? "A single-choice question has exactly one right option"
                                : "A question needs a right option");
            }
            return new Question(id == null ? newId.get() : id, text, kind, List.copyOf(checked));
        }

        Question withoutAnswers() {
            return new Question(
                    id,
                    text,
                    kind,
                    options.stream()
                            .map(option -> new Option(option.id(), option.text(), null))
                            .toList());
        }

        Set<UUID> rightOptions() {
            var right = new HashSet<UUID>();
            for (var option : options) {
                if (Boolean.TRUE.equals(option.correct()) && option.id() != null) {
                    right.add(option.id());
                }
            }
            return right;
        }

        public enum Kind {
            SINGLE,
            MULTIPLE
        }
    }

    /** One option of a question. {@code correct} is {@code null} in what a student is shown. */
    record Option(@Nullable UUID id, String text, @Nullable Boolean correct) {}

    /** What a student's answers to a quiz came to. */
    record Grade(int right, int total, boolean passed, List<UUID> wrongQuestionIds) {}
}
