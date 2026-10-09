/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.domain;

import org.jspecify.annotations.Nullable;
import space.vkurse.platform.auth.domain.AuthErrors.OAuthEmailRequiredException;
import space.vkurse.platform.user.domain.Email;

/** What a provider told us about the person who signed in. */
public record ProviderAccount(
        SignInProvider provider,
        String subject,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String name,
        @Nullable String picture) {

    public ProviderAccount {
        if (!provider.isExternal()) {
            throw new IllegalArgumentException("Not an external provider: " + provider);
        }
    }

    /**
     * The email that may identify a user of ours: an account we haven't seen before is matched to a user by email only
     * if the provider checked that the person owns it.
     *
     * @throws OAuthEmailRequiredException the provider gave no email, or one it hasn't verified
     */
    public Email verifiedEmail() {
        if (email == null || !emailVerified) {
            throw new OAuthEmailRequiredException();
        }
        return new Email(email);
    }
}
