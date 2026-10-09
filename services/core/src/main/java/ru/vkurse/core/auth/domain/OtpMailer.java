/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.domain;

import java.time.Duration;
import ru.vkurse.core.user.domain.Email;

/** Delivers a sign-in code to its owner. */
public interface OtpMailer {

    /** Sends the code. Throws if it could not be handed over, so the caller can tell the user. */
    void send(Email email, String code, Duration validFor);
}
