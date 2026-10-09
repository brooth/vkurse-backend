/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import space.vkurse.platform.auth.application.OAuthRequestService;
import space.vkurse.platform.auth.domain.PendingOAuthRequest;

/**
 * Plugs our pending sign-ins into Spring Security in place of the HTTP session: translates between Spring's
 * {@link OAuth2AuthorizationRequest} and {@link PendingOAuthRequest}, and passes on the frontend {@code callback}.
 */
@Component
class OAuthRequestStore implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Request attribute with the frontend callback, set when the provider redirects back. */
    static final String CALLBACK_ATTRIBUTE = OAuthRequestStore.class.getName() + ".callback";

    /** Query parameter of {@code GET /auth/oauth/{provider}} with the frontend URL to return to. */
    static final String CALLBACK_PARAMETER = "callback";

    private final OAuthRequestService requests;

    OAuthRequestStore(OAuthRequestService requests) {
        this.requests = requests;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        var state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        return requests.find(state)
                .map(pending -> toSpring(state, pending.getRequest()))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            @Nullable OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        requests.start(
                authorizationRequest.getState(),
                fromSpring(authorizationRequest),
                request.getParameter(CALLBACK_PARAMETER));
    }

    /** Takes the pending request out (it is used once) and exposes its callback as {@link #CALLBACK_ATTRIBUTE}. */
    @Override
    public @Nullable OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        var state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        var taken = requests.take(state).orElse(null);
        if (taken == null) {
            return null;
        }
        request.setAttribute(CALLBACK_ATTRIBUTE, taken.callback().value());
        return taken.request() == null ? null : toSpring(state, taken.request());
    }

    private static PendingOAuthRequest.AuthorizationRequest fromSpring(OAuth2AuthorizationRequest request) {
        return new PendingOAuthRequest.AuthorizationRequest(
                request.getAuthorizationUri(),
                request.getClientId(),
                request.getRedirectUri(),
                request.getScopes(),
                request.getAdditionalParameters(),
                request.getAttributes(),
                request.getAuthorizationRequestUri());
    }

    private static OAuth2AuthorizationRequest toSpring(String state, PendingOAuthRequest.AuthorizationRequest request) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(request.authorizationUri())
                .clientId(request.clientId())
                .redirectUri(request.redirectUri())
                .scopes(request.scopes())
                .state(state)
                .additionalParameters(request.additionalParameters())
                .attributes(request.attributes())
                .authorizationRequestUri(request.authorizationRequestUri())
                .build();
    }
}
