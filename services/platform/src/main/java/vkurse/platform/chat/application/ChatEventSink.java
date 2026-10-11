/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.util.UUID;
import vkurse.platform.chat.domain.ChatEvent;

/** Port: the open streams of this node. What happens in a chat is handed to those that watch it. */
public interface ChatEventSink {

    /** Whether anyone on this node watches the chat. */
    boolean isWatched(UUID chatId);

    /** Hands an event to everyone on this node who watches its chat. */
    void deliver(ChatEvent event, MessageView message);
}
