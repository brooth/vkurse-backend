/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure.security;

import org.springframework.security.core.AuthenticationException;
import ru.vkurse.common.error.Errors.ClientException;

/**
 * Carries a {@link ClientException} through the security filter chain, which only passes on
 * {@link AuthenticationException}s. The entry point unwraps it.
 */
class AuthFailure extends AuthenticationException {
    private final ClientException error;

    AuthFailure(ClientException error) {
        super(error.getMessage());
        this.error = error;
    }

    ClientException getError() {
        return error;
    }
}
