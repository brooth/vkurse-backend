/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import java.util.regex.Pattern;

/** What a notification says: ready text, or a template of the service with its parameters. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Content.Text.class, name = "text"),
    @JsonSubTypes.Type(value = Content.Template.class, name = "template")
})
public sealed interface Content {

    static Content text(String title, String body) {
        return new Text(title, body);
    }

    static Content template(String name, String locale, Map<String, Object> params) {
        return new Template(name, locale, params);
    }

    /**
     * Plain text, sent as it is.
     *
     * @param title the email subject, and the first line of a Telegram message; may be empty
     * @param body the text
     */
    record Text(String title, String body) implements Content {
        public Text {
            if (body.isBlank()) {
                throw new IllegalArgumentException("A text notification needs a body");
            }
        }
    }

    /**
     * A template from the service's {@code templates/notification/} resources: {@code {name}.html} for email and
     * {@code {name}.txt} for the text form. Its subject is the message {@code notification.{name}.subject}.
     *
     * @param name template name: lowercase letters, digits and dashes ({@code otp-code})
     * @param locale language tag of the recipient ({@code ru})
     * @param params values the template reads. A stored notification keeps them as JSON, so use strings and numbers
     */
    record Template(String name, String locale, Map<String, Object> params) implements Content {
        private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

        public Template {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid notification template name " + name);
            }
            params = Map.copyOf(params);
        }
    }
}
