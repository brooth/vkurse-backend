/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.infrastructure;

import java.time.Duration;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import space.vkurse.platform.PlatformProperties;
import space.vkurse.platform.auth.domain.OtpMailer;
import space.vkurse.platform.user.domain.Email;

/** Sends the code as a plain-text email. Moves to a template in the notification service. */
@Component
class SmtpOtpMailer implements OtpMailer {
    private final MailSender mail;
    private final PlatformProperties properties;

    SmtpOtpMailer(MailSender mail, PlatformProperties properties) {
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
