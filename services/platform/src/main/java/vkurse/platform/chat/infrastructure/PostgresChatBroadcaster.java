/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.infrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vkurse.platform.chat.application.ChatStreamService;
import vkurse.platform.chat.domain.ChatBroadcaster;
import vkurse.platform.chat.domain.ChatEvent;

/**
 * Tells every node what happens in chats, through Postgres {@code NOTIFY}: an announcement is sent in the caller's
 * transaction, so the nodes get it when that transaction is committed and never if it isn't.
 *
 * <p>Each node listens on a connection of its own, outside the pool, and passes what it hears to its streams. If that
 * connection drops, the node hears nothing until it has reconnected; a client makes up for it by reading the feed
 * after the last message it has. {@code LISTEN} needs a direct connection to Postgres: it does not work through a
 * pooler in transaction mode.
 */
@Component
class PostgresChatBroadcaster implements ChatBroadcaster, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(PostgresChatBroadcaster.class);

    private static final String CHANNEL = "vkurse_chat";
    private static final int POLL_MILLIS = 5_000;
    private static final long RECONNECT_MILLIS = 2_000;

    private final JdbcTemplate jdbc;
    private final DataSourceProperties database;
    private final ChatStreamService streams;

    private volatile boolean running;
    private volatile Thread listener;

    PostgresChatBroadcaster(JdbcTemplate jdbc, DataSourceProperties database, ChatStreamService streams) {
        this.jdbc = jdbc;
        this.database = database;
        this.streams = streams;
    }

    @Override
    public void announce(ChatEvent event) {
        var payload = event.kind().name() + ":" + event.chatId() + ":" + event.messageId();
        jdbc.query("SELECT pg_notify(?, ?)", result -> {}, CHANNEL, payload);
    }

    @Override
    public void start() {
        running = true;
        listener = Thread.ofPlatform().name("chat-listener").daemon(true).start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
        var thread = listener;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        while (running) {
            try (var connection = connect()) {
                try (var statement = connection.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                log.info("Listening for chat events");
                var postgres = connection.unwrap(PGConnection.class);
                while (running) {
                    var notifications = postgres.getNotifications(POLL_MILLIS);
                    if (notifications == null) {
                        continue;
                    }
                    for (var notification : notifications) {
                        handle(notification.getParameter());
                    }
                }
            } catch (SQLException e) {
                if (running) {
                    log.error("The chat listener lost its connection, reconnecting", e);
                    pause();
                }
            }
        }
    }

    private void handle(String payload) {
        try {
            var parts = payload.split(":");
            streams.onEvent(new ChatEvent(
                    ChatEvent.Kind.valueOf(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2])));
        } catch (RuntimeException e) {
            log.error("Failed to pass on chat event {}", payload, e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                database.determineUrl(), database.determineUsername(), database.determinePassword());
    }

    private void pause() {
        try {
            Thread.sleep(RECONNECT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
