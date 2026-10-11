/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.chat.domain.ChatEvent;
import vkurse.platform.chat.domain.MessageRepository;

/** What a chat's open streams need: who may open one, and passing on what happens in the chat. */
@Service
public class ChatStreamService {
    private final ChatAccess access;
    private final MessageRepository messages;
    private final MessageViews views;
    private final ChatEventSink sink;
    private final TransactionTemplate transaction;

    public ChatStreamService(
            ChatAccess access,
            MessageRepository messages,
            MessageViews views,
            ChatEventSink sink,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.messages = messages;
        this.views = views;
        this.sink = sink;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
    }

    /** Checks that the caller is in the chat, and so may watch it. */
    public void ensureMayWatch(UUID userId, UUID chatId) {
        transaction.executeWithoutResult(status -> access.chat(userId, chatId));
    }

    /**
     * Passes an event that some node announced on to this node's streams. The message is read here, once, and only if
     * somebody watches its chat; it is shown as to nobody in particular, so it tells no one's own vote.
     */
    public void onEvent(ChatEvent event) {
        if (!sink.isWatched(event.chatId())) {
            return;
        }
        var view = transaction.execute(status -> messages.findById(event.messageId())
                .filter(message -> message.isIn(event.chatId()))
                .map(message -> views.of(message, null))
                .orElse(null));
        if (view != null) {
            sink.deliver(event, view);
        }
    }
}
