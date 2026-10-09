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

    @Transactional(readOnly = true)
    public Optional<PendingOAuthRequest> find(String state) {
        return requests.findByState(state);
    }

    /** Takes a pending sign-in out: it is used once. The caller checks {@link #isLive}. */
    public Optional<PendingOAuthRequest> take(String state) {
        var request = requests.findByState(state);
        request.ifPresent(requests::remove);
        return request;
    }

    public boolean isLive(PendingOAuthRequest request) {
        return request.isLive(clock.instant());
    }
}
