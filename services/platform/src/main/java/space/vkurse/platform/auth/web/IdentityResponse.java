/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.web;

import java.util.UUID;
import space.vkurse.platform.auth.application.Identity;
import space.vkurse.platform.user.web.UserResponse;

/** Tokens for a new or refreshed session, returned by every sign-in endpoint. */
record IdentityResponse(String accessToken, String refreshToken, UUID sessionId, UserResponse user) {

    static IdentityResponse of(Identity identity) {
        return new IdentityResponse(
                identity.accessToken(),
                identity.refreshToken(),
                identity.sessionId(),
                UserResponse.of(identity.user()));
    }
}
