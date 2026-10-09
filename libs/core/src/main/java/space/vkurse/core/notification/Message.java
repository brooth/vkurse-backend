/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * A notification to deliver: one or more attempts, tried in order until one is delivered. An attempt is a content and
 * the targets to try it on, also in order. So "email, else Telegram" is one attempt with two targets, and a different
 * wording for the fallback is a second attempt.
 *
 * <pre>{@code
 * Message.of(Content.template("invitation", locale, params), Target.email(address))
 *         .orElse(Content.text("", "Вас пригласили в школу"), Target.telegram(chatId));
 * }</pre>
 */
public record Message(List<Attempt> attempts) {
    public Message {
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException("A message needs at least one attempt");
        }
        attempts = List.copyOf(attempts);
    }

    public static Message of(Content content, Target... targets) {
        return new Message(List.of(new Attempt(content, List.of(targets))));
    }

    /** Adds an attempt that is made when every earlier one failed. */
    public Message orElse(Content content, Target... targets) {
        var extended = new ArrayList<>(attempts);
        extended.add(new Attempt(content, List.of(targets)));
        return new Message(extended);
    }

    /** One content and the targets to try it on, in order. */
    public record Attempt(Content content, List<Target> targets) {
        public Attempt {
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("An attempt needs at least one target");
            }
            targets = List.copyOf(targets);
        }
    }
}
