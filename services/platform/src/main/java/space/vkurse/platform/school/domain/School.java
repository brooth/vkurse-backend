/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import space.vkurse.core.domain.AggregateRoot;
import space.vkurse.core.error.Errors.BadRequestException;

/**
 * An online school: what courses, staff and students belong to. Who owns it is not kept here: the owner is the
 * {@link SchoolMember} with the role {@link SchoolRole#OWNER}.
 */
@Entity
@Table(name = "schools")
@Getter
public class School extends AggregateRoot {
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private String name;

    /** Unique among live schools. */
    @Convert(converter = Slug.Converter.class)
    private Slug slug;

    private @Nullable String description;

    /** The public link of the logo. */
    private @Nullable String logo;

    /** The stored file that link leads to. */
    private @Nullable UUID logoFileId;

    /** Set when the school is deleted. A deleted school can't be found, and frees its slug. */
    private @Nullable Instant deletedAt;

    protected School() {}

    private School(String name, Slug slug, @Nullable String description, Instant now) {
        super(now);
        this.name = name;
        this.slug = slug;
        this.description = description;
    }

    /** Opens a school. Its owner is added next to it, with {@link SchoolMember#owner}. */
    public static School open(String name, Slug slug, @Nullable String description, Instant now) {
        return new School(checkedName(name), slug, checkedDescription(description), now);
    }

    /** Changes what the staff may change. A {@code null} argument leaves that field as it is; a blank description removes it. */
    public void changeDetails(@Nullable String name, @Nullable Slug slug, @Nullable String description) {
        if (name != null) {
            this.name = checkedName(name);
        }
        if (slug != null) {
            this.slug = slug;
        }
        if (description != null) {
            this.description = checkedDescription(description);
        }
    }

    /**
     * Sets the logo: a public stored file and its link. Returns the file of the logo it replaces, which the caller
     * deletes; {@code null} if there was none.
     */
    public @Nullable UUID changeLogo(UUID fileId, String url) {
        if (fileId.equals(logoFileId)) {
            return null;
        }
        var replaced = logoFileId;
        logo = url;
        logoFileId = fileId;
        return replaced;
    }

    /** Removes the logo. Returns its file, which the caller deletes; {@code null} if there was none. */
    public @Nullable UUID removeLogo() {
        var removed = logoFileId;
        logo = null;
        logoFileId = null;
        return removed;
    }

    /** Deletes the school. Its row stays, with its members and students, but nothing finds it any more. */
    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String checkedName(String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("The name must have 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static @Nullable String checkedDescription(@Nullable String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        var trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException(
                    "The description must have at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }
}
