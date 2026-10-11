/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vkurse.platform.chat.application.ChatEventSink;
import vkurse.platform.chat.application.MessageView;
import vkurse.platform.chat.domain.ChatErrors.TooManyStreamsException;
import vkurse.platform.chat.domain.ChatEvent;

/**
 * The chat streams open on this node. A stream gets every event of its chat from the moment it is opened, and a
 * comment line now and then so that proxies keep it open. It knows nothing of what happened before it was opened.
 */
@Component
class ChatStreamHub implements ChatEventSink {
    private static final Logger log = LoggerFactory.getLogger(ChatStreamHub.class);

    /** How many streams one person may have open on a node. */
    static final int MAX_PER_USER = 3;

    private final Map<UUID, Set<Stream>> byChat = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> byUser = new ConcurrentHashMap<>();

    /**
     * Opens a stream of a chat for a person who may watch it.
     *
     * @param lifetime how long the stream stays open at most
     */
    SseEmitter open(UUID chatId, UUID userId, Duration lifetime) {
        var open = byUser.merge(userId, 1, Integer::sum);
        if (open > MAX_PER_USER) {
            release(userId);
            throw new TooManyStreamsException();
        }
        var emitter = new SseEmitter(lifetime.toMillis());
        var stream = new Stream(chatId, userId, emitter);
        byChat.computeIfAbsent(chatId, id -> ConcurrentHashMap.newKeySet()).add(stream);
        emitter.onCompletion(() -> close(stream));
        emitter.onError(error -> close(stream));
        // The stream's time is up: end it as a finished response, not as an error.
        emitter.onTimeout(emitter::complete);
        send(stream, SseEmitter.event().comment("open"));
        return emitter;
    }

    @Override
    public boolean isWatched(UUID chatId) {
        var streams = byChat.get(chatId);
        return streams != null && !streams.isEmpty();
    }

    @Override
    public void deliver(ChatEvent event, MessageView message) {
        var streams = byChat.get(event.chatId());
        if (streams == null) {
            return;
        }
        var data = MessageResponse.of(message);
        for (var stream : streams) {
            send(
                    stream,
                    SseEmitter.event()
                            .id(event.messageId().toString())
                            .name(event.kind().eventName())
                            .data(data, MediaType.APPLICATION_JSON));
        }
    }

    /** Keeps the streams open through proxies that close a silent connection. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        byChat.values()
                .forEach(streams -> streams.forEach(
                        stream -> send(stream, SseEmitter.event().comment("ping"))));
    }

    private void send(Stream stream, SseEmitter.SseEventBuilder event) {
        try {
            // One writer at a time: the heartbeat and an event may come together.
            synchronized (stream) {
                stream.emitter().send(event);
            }
        } catch (IOException | IllegalStateException gone) {
            log.debug("A chat stream of user {} is gone", stream.userId());
            stream.emitter().completeWithError(gone);
            close(stream);
        }
    }

    private void close(Stream stream) {
        var streams = byChat.get(stream.chatId());
        if (streams != null && streams.remove(stream)) {
            release(stream.userId());
            byChat.computeIfPresent(stream.chatId(), (id, left) -> left.isEmpty() ? null : left);
        }
    }

    private void release(UUID userId) {
        byUser.computeIfPresent(userId, (id, open) -> open > 1 ? open - 1 : null);
    }

    private record Stream(UUID chatId, UUID userId, SseEmitter emitter) {}
}
