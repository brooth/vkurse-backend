/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
    @JsonSubTypes.Type(value = LessonBlock.Image.class, name = "image"),
    @JsonSubTypes.Type(value = LessonBlock.File.class, name = "file")
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
}
