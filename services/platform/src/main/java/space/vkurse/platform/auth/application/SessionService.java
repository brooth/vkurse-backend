/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.auth.domain.Session;
import space.vkurse.platform.auth.domain.SessionRepository;

/** A user's signed-in devices. Every operation takes the caller's user id and only touches their own sessions. */
@Service
@Transactional
public class SessionService {
    private final SessionRepository sessions;
    private final Clock clock;

    public SessionService(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Session> findMine(UUID userId, Paging paging) {
        return sessions.findLiveByUser(userId, clock.instant(), paging.limit(), paging.offset());
    }

    /** Whether the session exists, belongs to the user and hasn't expired or been revoked. */
    @Transactional(readOnly = true)
    public boolean isLive(UUID userId, UUID sessionId) {
        var now = clock.instant();
        return sessions.findById(sessionId)
                .filter(session -> session.belongsTo(userId) && session.isLive(now))
                .isPresent();
    }

    /** Signs one device out. */
    public void revoke(UUID userId, UUID sessionId) {
        var now = clock.instant();
        var session = sessions.findById(sessionId)
                .filter(s -> s.belongsTo(userId) && s.isLive(now))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        session.revoke(now);
        sessions.save(session);
    }

    /** Signs out every device of the user except {@code currentSessionId}. */
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        var now = clock.instant();
        for (var session : sessions.findLiveByUser(userId, now)) {
            if (!session.getId().equals(currentSessionId)) {
                session.revoke(now);
                sessions.save(session);
            }
        }
    }

    /** Signs out every device of the user. */
    public void revokeAll(UUID userId) {
        var now = clock.instant();
        for (var session : sessions.findLiveByUser(userId, now)) {
            session.revoke(now);
            sessions.save(session);
        }
    }
}
