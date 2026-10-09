/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import ru.vkurse.common.domain.AggregateRoot;

/** A Google or Apple account a user signs in with, identified by the provider's stable {@code sub}. */
@Entity
@Table(name = "auth_linked_accounts")
@Getter
public class LinkedAccount extends AggregateRoot {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private SignInProvider provider;

    /** The provider's user id ({@code sub} claim). Unique per provider. */
    private String subject;

    /** The email the provider gave when the account was linked. */
    private @Nullable String email;

    protected LinkedAccount() {}

    private LinkedAccount(UUID userId, SignInProvider provider, String subject, @Nullable String email, Instant now) {
        super(now);
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
    }

    public static LinkedAccount link(
            UUID userId, SignInProvider provider, String subject, @Nullable String email, Instant now) {
        if (!provider.isExternal()) {
            throw new IllegalArgumentException("Only an external provider's account can be linked, not " + provider);
        }
        return new LinkedAccount(userId, provider, subject, email, now);
    }
}
