/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

/**
 * A delivered notification.
 *
 * @param channel the channel that took it
 * @param messageId the id the mail server or Telegram gave the message; empty if it gave none
 */
public record Delivery(Channel channel, String messageId) {}
