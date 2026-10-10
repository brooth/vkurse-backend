/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;

/** A Google or Apple account a user signs in with, identified by the provider's stable {@code sub}. */
@Entity
@Table(name = "auth_linked_accounts")
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

    /** Links a provider account to the user it belongs to, so the next sign-in with it finds that user. */
    public static LinkedAccount link(UUID userId, ProviderAccount account, Instant now) {
        return new LinkedAccount(userId, account.provider(), account.subject(), account.email(), now);
    }

    public UUID getUserId() {
        return userId;
    }

    public SignInProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public @Nullable String getEmail() {
        return email;
    }
}
