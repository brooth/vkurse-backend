/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import space.vkurse.core.domain.AggregateRoot;

/**
 * A signed-in device of one user. It lives until it expires or is revoked (sign-out, a replayed refresh token, a
 * deleted account); each refresh moves the expiry forward.
 */
@Entity
@Table(name = "auth_sessions")
@Getter
public class Session extends AggregateRoot {
    /** How long the one-time code of an OAuth sign-in can be traded for tokens. */
    public static final Duration EXCHANGE_CODE_TTL = Duration.ofSeconds(60);

    public static final int MAX_USER_AGENT_LENGTH = 500;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    /** How the user signed in. */
    @Enumerated(EnumType.STRING)
    private SignInProvider provider;

    /** The {@code User-Agent} of the sign-in request, to tell devices apart in the session list. */
    private @Nullable String userAgent;

    private Instant lastActivityAt;

    /** The session ends here unless refreshed. */
    private Instant expiresAt;

    /** Generation of the only refresh token that is still valid. */
    private int refreshGeneration;

    /** Hash of the one-time code an OAuth sign-in redirects with, until it is traded for tokens. */
    private @Nullable String exchangeCodeHash;

    private @Nullable Instant exchangeExpiresAt;

    /** Set when the session is ended before it expires. */
    private @Nullable Instant revokedAt;

    protected Session() {}

    private Session(UUID userId, SignInProvider provider, @Nullable String userAgent, Instant now, Duration ttl) {
        super(now);
        this.userId = userId;
        this.provider = provider;
        this.userAgent = userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH
                ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        this.lastActivityAt = now;
        this.expiresAt = now.plus(ttl);
    }

    /** Starts a session for a user who just proved who they are. */
    public static Session start(
            UUID userId, SignInProvider provider, @Nullable String userAgent, Instant now, Duration ttl) {
        return new Session(userId, provider, userAgent, now, ttl);
    }

    public boolean isLive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    /** Ends the session. Its tokens stop working. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            exchangeCodeHash = null;
            exchangeExpiresAt = null;
        }
    }

    /** Attaches the hash of a one-time code that can be traded for this session's tokens for a short while. */
    public void issueExchangeCode(String codeHash, Instant now) {
        exchangeCodeHash = codeHash;
        exchangeExpiresAt = now.plus(EXCHANGE_CODE_TTL);
    }

    /**
     * Uses up the one-time code. Returns whether {@code codeHash} was this session's code and still valid; either
     * way a code never works twice.
     */
    public boolean redeemExchangeCode(String codeHash, Instant now) {
        var valid = isLive(now)
                && exchangeCodeHash != null
                && exchangeExpiresAt != null
                && now.isBefore(exchangeExpiresAt)
                && MessageDigest.isEqual(
                        exchangeCodeHash.getBytes(StandardCharsets.UTF_8), codeHash.getBytes(StandardCharsets.UTF_8));
        if (valid) {
            exchangeCodeHash = null;
            exchangeExpiresAt = null;
        }
        return valid;
    }

    /**
     * Uses the refresh token of generation {@code presentedGeneration}. Each refresh token works once: the current one
     * moves the session to the next generation and extends it. An older one means someone else has a copy, so the
     * session is revoked and both parties must sign in again.
     */
    public Rotation rotate(int presentedGeneration, Instant now, Duration ttl) {
        if (!isLive(now)) {
            revoke(now);
            return Rotation.ENDED;
        }
        if (presentedGeneration != refreshGeneration) {
            revoke(now);
            return Rotation.REPLAYED;
        }
        refreshGeneration++;
        lastActivityAt = now;
        expiresAt = now.plus(ttl);
        return Rotation.ROTATED;
    }

    /** What {@link #rotate} did. */
    public enum Rotation {
        /** The session moved to the next generation: issue new tokens. */
        ROTATED,
        /** An already used refresh token was presented: the session is revoked. */
        REPLAYED,
        /** The session had expired or was revoked. */
        ENDED
    }
}
