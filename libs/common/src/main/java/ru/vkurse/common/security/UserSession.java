/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.common.security;

import java.util.UUID;

/**
 * The authenticated caller, read from the access token: who they are, which signed-in device the token belongs to,
 * and their platform role at the time the token was issued. Controllers take it as {@code @GetSession UserSession}.
 */
public record UserSession(UUID userId, UUID sessionId, String role) {
    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
