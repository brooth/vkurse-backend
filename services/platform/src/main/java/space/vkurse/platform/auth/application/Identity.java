/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.application;

import java.util.UUID;
import space.vkurse.platform.user.domain.User;

/** Tokens for a new or refreshed session, and whose they are. The result of every sign-in. */
public record Identity(String accessToken, String refreshToken, UUID sessionId, User user) {}
