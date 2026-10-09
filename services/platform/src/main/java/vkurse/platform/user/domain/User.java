/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.user.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;

/** A person who signed in: a student, a school member or a platform admin. */
@Entity
@Table(name = "users")
@Getter
public class User extends AggregateRoot {
    public static final String DEFAULT_LOCALE = "ru";
    public static final String DEFAULT_TIMEZONE = "Europe/Moscow";
    public static final int MAX_NAME_LENGTH = 100;

    /** Platform role. School roles (owner, author, …) belong to school membership, not here. */
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private String name;

    /** Unique among live users. Set at sign-up and never changed. */
    @Convert(converter = Email.Converter.class)
    private Email email;

    /** Picture URL: the one a sign-in provider gave, or the public link of a picture the user uploaded. */
    private @Nullable String picture;

    /** The stored file behind an uploaded picture; {@code null} for a provider's picture. */
    private @Nullable UUID pictureFileId;

    private String locale = DEFAULT_LOCALE;

    /** IANA time zone id. */
    private String timezone = DEFAULT_TIMEZONE;

    /** Set when the account is deleted. A deleted user can't be found or sign in, and frees the email. */
    private @Nullable Instant deletedAt;

    protected User() {}

    private User(Email email, String name, @Nullable String picture, Instant now) {
        super(now);
        this.email = email;
        this.name = name;
        this.picture = picture;
    }

    /**
     * Signs a new user up. Without a usable {@code name} (an email code sign-in, or a provider that gave none) the
     * user is named after the email; a name that is too long is cut.
     */
    public static User register(Email email, @Nullable String name, @Nullable String picture, Instant now) {
        var displayName = name == null || name.isBlank() ? email.localPart() : name.trim();
        if (displayName.length() > MAX_NAME_LENGTH) {
            displayName = displayName.substring(0, MAX_NAME_LENGTH);
        }
        var user = new User(email, displayName, picture, now);
        user.raise(new UserRegistered(user.getId()));
        return user;
    }

    /** Changes what a user may change in their own profile. A {@code null} argument leaves that field as it is. */
    public void changeProfile(@Nullable String name, @Nullable String locale, @Nullable String timezone) {
        if (name != null) {
            if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
                throw new BadRequestException("The name must have 1 to " + MAX_NAME_LENGTH + " characters");
            }
            this.name = name;
        }
        if (locale != null) {
            if (locale.isBlank()) {
                throw new BadRequestException("The locale must not be blank");
            }
            this.locale = locale;
        }
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown time zone");
            }
            this.timezone = timezone;
        }
    }

    /**
     * Sets a picture the user uploaded: a public stored file and its link. Returns the file of the uploaded picture it
     * replaces, which the caller deletes; {@code null} if there was none.
     */
    public @Nullable UUID changePicture(UUID fileId, String url) {
        if (fileId.equals(pictureFileId)) {
            return null;
        }
        var replaced = pictureFileId;
        picture = url;
        pictureFileId = fileId;
        return replaced;
    }

    /** Removes the picture, whatever it came from. Returns its file, which the caller deletes; {@code null} if none. */
    public @Nullable UUID removePicture() {
        var removed = pictureFileId;
        picture = null;
        pictureFileId = null;
        return removed;
    }

    /** Deletes the account. The sessions and linked sign-in accounts go with it (they listen for the event). */
    public void delete(Instant now) {
        if (deletedAt != null) {
            return;
        }
        deletedAt = now;
        raise(new UserDeleted(getId()));
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public enum Role {
        USER,
        ADMIN
    }
}
