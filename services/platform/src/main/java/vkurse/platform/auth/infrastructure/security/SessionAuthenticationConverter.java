/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vkurse.core.security.JwtResourceServer;
import vkurse.core.security.UserSession;
import vkurse.platform.auth.application.SessionService;
import vkurse.platform.auth.domain.AuthErrors.ReloginRequiredException;

/**
 * Turns a verified access token into the caller's {@link UserSession}, like every service does, and then checks what
 * only this service can: that the session is still live (one primary-key read). So on platform's own endpoints a sign-out
 * takes effect immediately, not when the access token expires.
 */
@Component
class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Converter<Jwt, AbstractAuthenticationToken> FROM_CLAIMS = JwtResourceServer.sessionConverter();

    private final SessionService sessions;

    SessionAuthenticationConverter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var authentication = FROM_CLAIMS.convert(jwt);
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSession session)) {
            throw new AuthFailure(new ReloginRequiredException("Invalid access token"));
        }
        if (!sessions.isLive(session.userId(), session.sessionId())) {
            throw new AuthFailure(new ReloginRequiredException("The session has ended"));
        }
        return authentication;
    }
}
