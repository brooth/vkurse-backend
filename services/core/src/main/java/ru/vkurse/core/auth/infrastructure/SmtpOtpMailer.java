/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import java.time.Duration;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import ru.vkurse.core.CoreProperties;
import ru.vkurse.core.auth.domain.OtpMailer;
import ru.vkurse.core.user.domain.Email;

/** Sends the code as a plain-text email. Moves to a template in the notification service. */
@Component
class SmtpOtpMailer implements OtpMailer {
    private final MailSender mail;
    private final CoreProperties properties;

    SmtpOtpMailer(MailSender mail, CoreProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public void send(Email email, String code, Duration validFor) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email.value());
        message.setSubject("VKurse — код для входа");
        message.setText("""
                Ваш код для входа в VKurse: %s

                Код действует %d минут. Если вы не запрашивали код, просто проигнорируйте это письмо.
                """.formatted(code, validFor.toMinutes()));
        mail.send(message);
    }
}
