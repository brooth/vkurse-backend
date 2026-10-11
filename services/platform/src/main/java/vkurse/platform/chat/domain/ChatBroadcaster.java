/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

/**
 * Port: tells every node of the service that something happened in a chat. Call it in the transaction that made the
 * change: the nodes hear of it when, and only if, that transaction is committed.
 */
public interface ChatBroadcaster {

    void announce(ChatEvent event);
}
