/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vkurse.common.security.GetSession;
import ru.vkurse.common.security.UserSession;
import ru.vkurse.common.web.Paging;
import ru.vkurse.core.auth.application.SessionService;

/** The caller's signed-in devices. Every operation is limited to the caller's own sessions. */
@RestController
@RequestMapping("/sessions")
class SessionController {
    private final SessionService sessions;

    SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    List<SessionResponse> findMine(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return sessions.findMine(session.userId(), Paging.parse(params)).stream()
                .map(SessionResponse::of)
                .toList();
    }

    /** Signs a device out. To sign out the current device, pass the {@code sessionId} from the sign-in response. */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@GetSession UserSession session, @PathVariable UUID id) {
        sessions.revoke(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Signs out every other device, keeping the current session. */
    @DeleteMapping
    ResponseEntity<Void> revokeOthers(@GetSession UserSession session) {
        sessions.revokeOthers(session.userId(), session.sessionId());
        return ResponseEntity.noContent().build();
    }
}
