/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vkurse.core.domain.AggregateRoot;

/**
 * A sign-in that was sent to Google or Apple and hasn't come back yet. The service is stateless and may run on several
 * nodes, so this is kept in the database instead of the HTTP session. It is taken out once, when the provider
 * redirects back, and is useless after {@link #TTL}.
 */
@Entity
@Table(name = "auth_oauth_requests")
public class PendingOAuthRequest extends AggregateRoot {
    public static final Duration TTL = Duration.ofMinutes(10);

    /** The OAuth {@code state} the provider sends back. */
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    private AuthorizationRequest request;

    /** The frontend URL to redirect to when the sign-in ends. */
    @Convert(converter = CallbackUrl.Converter.class)
    private CallbackUrl callback;

    private Instant expiresAt;

    protected PendingOAuthRequest() {}

    private PendingOAuthRequest(String state, AuthorizationRequest request, CallbackUrl callback, Instant now) {
        super(now);
        this.state = state;
        this.request = request;
        this.callback = callback;
        this.expiresAt = now.plus(TTL);
    }

    public static PendingOAuthRequest start(
            String state, AuthorizationRequest request, CallbackUrl callback, Instant now) {
        return new PendingOAuthRequest(state, request, callback, now);
    }

    public String getState() {
        return state;
    }

    public AuthorizationRequest getRequest() {
        return request;
    }

    public CallbackUrl getCallback() {
        return callback;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isLive(Instant now) {
        return now.isBefore(expiresAt);
    }

    /** What to continue the sign-in with when the provider redirects back; nothing once this has expired. */
    public Optional<AuthorizationRequest> redeem(Instant now) {
        return isLive(now) ? Optional.of(request) : Optional.empty();
    }

    /**
     * What was sent to the provider, enough to continue when it redirects back: nonce, PKCE verifier and registration
     * id travel in {@code attributes} and {@code additionalParameters}.
     */
    public record AuthorizationRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri) {}
}
