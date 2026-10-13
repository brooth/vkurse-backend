/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import vkurse.core.notification.Target.EmailTarget;

/** Sends an email through the mail server in {@code spring.mail.*}: text, plus the HTML form when there is one. */
class SmtpEmailSender {
    private final JavaMailSender mail;
    private final NotificationProperties properties;

    SmtpEmailSender(JavaMailSender mail, NotificationProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    /** The mail server emails go through, for the log: {@code 127.0.0.1:54325}. */
    String server() {
        return mail instanceof JavaMailSenderImpl smtp ? smtp.getHost() + ":" + smtp.getPort() : "the mail server";
    }

    /** Returns the email's {@code Message-ID}. Throws if the mail server did not take it. */
    String send(EmailTarget target, Rendered message) {
        var mime = mail.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mime, message.html() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mailFrom());
            helper.setTo(target.address());
            helper.setSubject(message.subject());
            if (message.html() != null) {
                helper.setText(message.text(), message.html());
            } else {
                helper.setText(message.text());
            }
            mail.send(mime);
            var id = mime.getMessageID();
            return id == null ? "" : id;
        } catch (MessagingException e) {
            throw new MailPreparationException("Failed to build the email", e);
        }
    }
}
