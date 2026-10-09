/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.application;

import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.platform.auth.AuthProperties;
import space.vkurse.platform.auth.domain.CallbackUrl;
import space.vkurse.platform.auth.domain.PendingOAuthRequest;
import space.vkurse.platform.auth.domain.PendingOAuthRequestRepository;

/** Keeps the OAuth sign-ins that are at the provider, so any node can finish one that another node started. */
@Service
@Transactional
public class OAuthRequestService {
    private final PendingOAuthRequestRepository requests;
    private final AuthProperties properties;
    private final Clock clock;

    public OAuthRequestService(PendingOAuthRequestRepository requests, AuthProperties properties, Clock clock) {
        this.requests = requests;
        this.properties = properties;
        this.clock = clock;
    }

    /** Remembers a sign-in that is about to be sent to the provider. Rejects a {@code callback} we don't allow. */
    public void start(String state, PendingOAuthRequest.AuthorizationRequest request, @Nullable String callback) {
        var allowed = CallbackUrl.of(callback, properties.webOrigins());
        requests.save(PendingOAuthRequest.start(state, request, allowed, clock.instant()));
    }

    /** The pending sign-in with this {@code state}, unless it has expired. */
    @Transactional(readOnly = true)
    public Optional<PendingOAuthRequest> find(String state) {
        var now = clock.instant();
        return requests.findByState(state).filter(request -> request.isLive(now));
    }

    /** Takes a pending sign-in out: it is used once, expired or not. */
    public Optional<Taken> take(String state) {
        var now = clock.instant();
        return requests.findByState(state).map(pending -> {
            requests.remove(pending);
            return new Taken(pending.getCallback(), pending.redeem(now).orElse(null));
        });
    }

    /**
     * A pending sign-in that was taken out.
     *
     * @param callback where to send the browser, whether the sign-in goes on or not
     * @param request what to continue with, or {@code null} if the sign-in had expired
     */
    public record Taken(CallbackUrl callback, PendingOAuthRequest.@Nullable AuthorizationRequest request) {}
}
