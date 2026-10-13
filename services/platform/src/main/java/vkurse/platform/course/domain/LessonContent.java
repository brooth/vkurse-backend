/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;

/** What a lesson shows: its blocks, in order. Stored as one JSON value. */
public record LessonContent(List<LessonBlock> blocks) {
    public static final int MAX_BLOCKS = 100;
    public static final int MAX_TEXT_LENGTH = 50_000;

    public static final LessonContent EMPTY = new LessonContent(List.of());

    /**
     * Makes a lesson's content from the blocks a client sent: checks them, and gives an id to each block that has
     * none.
     *
     * @param newId makes a block id
     */
    public static LessonContent of(List<LessonBlock> blocks, Supplier<UUID> newId) {
        if (blocks.size() > MAX_BLOCKS) {
            throw new BadRequestException("A lesson has at most " + MAX_BLOCKS + " blocks");
        }
        var ids = new HashSet<UUID>();
        var checked = new ArrayList<LessonBlock>();
        var homeworks = 0;
        var quizzes = 0;
        for (var sent : blocks) {
            var block = sent;
            // A client's JSON can leave out what the types call non-null.
            switch (block) {
                case null -> throw new BadRequestException("A block is missing");
                case LessonBlock.Text text -> {
                    if (Objects.isNull(text.text())
                            || text.text().isBlank()
                            || text.text().length() > MAX_TEXT_LENGTH) {
                        throw new BadRequestException("A text block must have 1 to " + MAX_TEXT_LENGTH + " characters");
                    }
                }
                case LessonBlock.Homework homework -> {
                    if (Objects.isNull(homework.task())
                            || homework.task().isBlank()
                            || homework.task().length() > MAX_TEXT_LENGTH) {
                        throw new BadRequestException(
                                "A homework task must have 1 to " + MAX_TEXT_LENGTH + " characters");
                    }
                    if (++homeworks > 1) {
                        throw new BadRequestException("A lesson has at most one homework");
                    }
                }
                case LessonBlock.Quiz quiz -> {
                    if (++quizzes > 1) {
                        throw new BadRequestException("A lesson has at most one quiz");
                    }
                    block = quiz.checked(newId);
                }
                case LessonBlock.Embed embed -> block = embed.checked();
                default -> {
                    if (block.fileId() == null) {
                        throw new BadRequestException("A block needs its file");
                    }
                }
            }
            var id = block.id();
            var identified = id == null ? block.withId(newId.get()) : block;
            if (!ids.add(Objects.requireNonNull(identified.id()))) {
                throw new BadRequestException("Two blocks have the same id");
            }
            checked.add(identified);
        }
        return new LessonContent(List.copyOf(checked));
    }

    // Not a part of the stored value: Jackson would otherwise write it as a property named `empty`.
    @JsonIgnore
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** The lesson's homework, if it has one. */
    public Optional<LessonBlock.Homework> homework() {
        return blocks.stream()
                .filter(LessonBlock.Homework.class::isInstance)
                .map(LessonBlock.Homework.class::cast)
                .findFirst();
    }

    /** The lesson's quiz, if it has one. */
    public Optional<LessonBlock.Quiz> quiz() {
        return blocks.stream()
                .filter(LessonBlock.Quiz.class::isInstance)
                .map(LessonBlock.Quiz.class::cast)
                .findFirst();
    }

    /** The content as anyone outside the staff sees it: a quiz doesn't say which options are right. */
    public LessonContent forStudents() {
        return new LessonContent(blocks.stream()
                .map(block -> block instanceof LessonBlock.Quiz quiz ? quiz.withoutAnswers() : block)
                .toList());
    }

    /** The stored files the blocks show. */
    public Set<UUID> fileIds() {
        var ids = new HashSet<UUID>();
        for (var block : blocks) {
            var fileId = block.fileId();
            if (fileId != null) {
                ids.add(fileId);
            }
        }
        return ids;
    }

    /** The file of each block that shows one, by block id, in the order of the blocks. */
    public Map<UUID, UUID> filesByBlock() {
        var files = new LinkedHashMap<UUID, UUID>();
        for (var block : blocks) {
            var fileId = block.fileId();
            if (fileId != null && block.id() != null) {
                files.put(block.id(), fileId);
            }
        }
        return files;
    }

    /**
     * Checks that each block shows a file of the right kind: a video block a video, an image block an image, a file
     * block anything but a video.
     *
     * @param files the stored files of {@link #fileIds()}, by id
     */
    public void ensureFits(Map<UUID, StoredFile> files) {
        for (var block : blocks) {
            var fileId = block.fileId();
            if (fileId == null) {
                continue;
            }
            var file = files.get(fileId);
            if (file == null) {
                throw new IllegalArgumentException("No stored file for block " + block.id());
            }
            var isVideo = file.getPurpose() == FilePurpose.SCHOOL_VIDEO;
            var fits = switch (block) {
                case LessonBlock.Video video -> isVideo;
                case LessonBlock.Image image ->
                    !isVideo && file.getContentType().startsWith("image/");
                case LessonBlock.File other -> !isVideo;
                case LessonBlock.Embed embed -> true;
                case LessonBlock.Text text -> true;
                case LessonBlock.Homework homework -> true;
                case LessonBlock.Quiz quiz -> true;
            };
            if (!fits) {
                throw new BadRequestException("The file \"" + file.getName() + "\" doesn't fit its block");
            }
        }
    }
}
