/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vkurse.core.logging.HttpClients;
import vkurse.core.notification.Target.TelegramTarget;

/** Sends a text message to a chat through the Telegram Bot API, as our bot. */
class TelegramBotSender {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotSender.class);

    // What .env.example sets where no real bot is needed.
    private static final String PLACEHOLDER = "placeholder";

    private static final String API_URL = "https://api.telegram.org";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String token;

    TelegramBotSender(NotificationProperties properties, HttpClients httpClients) {
        this.client = httpClients
                .builder("telegram", CONNECT_TIMEOUT, READ_TIMEOUT)
                .baseUrl(API_URL)
                .build();
        this.token = properties.telegram().botToken();
        if (PLACEHOLDER.equals(token)) {
            log.warn("The Telegram bot token is a placeholder, Telegram messages will fail");
        }
    }

    /** Returns the id of the sent message. Throws if Telegram did not take it. */
    String send(TelegramTarget target, Rendered message) {
        var text = message.subject().isBlank() ? message.text() : message.subject() + "\n\n" + message.text();
        Reply reply;
        try {
            reply = client.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(new SendMessage(target.chatId(), text))
                    .retrieve()
                    // Telegram explains an error in the body, which is read below.
                    .onStatus(HttpStatusCode::isError, (request, response) -> {})
                    .body(Reply.class);
        } catch (RestClientException e) {
            // The exception names the request URL, which holds the bot token: keep only the root cause's message.
            throw new IllegalStateException("Telegram request failed: "
                    + NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        }
        if (reply == null || !reply.ok() || reply.result() == null) {
            throw new IllegalStateException(
                    "Telegram rejected the message: " + (reply == null ? "empty reply" : reply.description()));
        }
        return Long.toString(reply.result().messageId());
    }

    private record SendMessage(@JsonProperty("chat_id") String chatId, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Reply(
            boolean ok, @Nullable Result result, @Nullable String description) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Result(@JsonProperty("message_id") long messageId) {}
    }
}
