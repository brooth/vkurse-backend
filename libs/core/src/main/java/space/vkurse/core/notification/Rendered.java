/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import org.jspecify.annotations.Nullable;

/**
 * A content made ready to send.
 *
 * @param subject the email subject, and the first line of a Telegram message; may be empty
 * @param text the plain-text form: the Telegram message, and the text part of an email
 * @param html the HTML form for email; absent for plain text
 */
record Rendered(String subject, String text, @Nullable String html) {}
