/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.chat.application.ChatService;
import vkurse.platform.chat.application.ChatStreamService;

@RestController
class ChatController {
    /** How long a stream stays open when the token doesn't say when it expires: an access token's lifetime. */
    private static final Duration MAX_STREAM = Duration.ofMinutes(15);

    private final ChatService chats;
    private final ChatStreamService streams;
    private final ChatStreamHub hub;
    private final Clock clock;

    ChatController(ChatService chats, ChatStreamService streams, ChatStreamHub hub, Clock clock) {
        this.chats = chats;
        this.streams = streams;
        this.hub = hub;
        this.clock = clock;
    }

    /**
     * The chats the caller is in: of the courses they have, and of the courses of the schools they work in. Each has
     * its last message and the caller's unread count.
     */
    @GetMapping("/chats")
    List<ChatResponse> listMine(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return chats.listMine(session.userId(), Paging.parse(params)).stream()
                .map(ChatResponse::of)
                .toList();
    }

    /** The chat of a course, with its pinned banners. */
    @GetMapping("/courses/{courseId}/chat")
    ChatResponse forCourse(@GetSession UserSession session, @PathVariable UUID courseId) {
        return ChatResponse.of(chats.forCourse(session.userId(), courseId));
    }

    @GetMapping("/chats/{id}")
    ChatResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return ChatResponse.of(chats.get(session.userId(), id));
    }

    /** Marks the chat read up to a message. The mark only moves forward. */
    @PostMapping("/chats/{id}/read")
    ChatResponse markRead(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody ReadRequest data) {
        return ChatResponse.of(chats.markRead(session.userId(), id, data.messageId()));
    }

    /**
     * A stream of what happens in the chat from now on, as server-sent events named {@code message.created},
     * {@code message.updated} and {@code message.deleted}, each with the message as its data (without the caller's
     * own vote). The stream ends when the access token expires: read the messages after the last one you have, then
     * open a new stream with a fresh token. The token goes in the {@code Authorization} header, never in the URL.
     */
    @GetMapping(value = "/chats/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@GetSession UserSession session, @PathVariable UUID id, Authentication authentication) {
        streams.ensureMayWatch(session.userId(), id);
        var lifetime = MAX_STREAM;
        if (authentication.getCredentials() instanceof Jwt token && token.getExpiresAt() != null) {
            var left = Duration.between(clock.instant(), token.getExpiresAt());
            lifetime = left.compareTo(MAX_STREAM) < 0 ? left : MAX_STREAM;
        }
        return hub.open(id, session.userId(), lifetime.isPositive() ? lifetime : Duration.ofSeconds(1));
    }

    record ReadRequest(@NotNull UUID messageId) {}
}
