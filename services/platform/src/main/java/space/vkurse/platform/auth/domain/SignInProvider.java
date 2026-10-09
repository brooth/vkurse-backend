/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.domain;

/** How a user proved who they are. */
public enum SignInProvider {
    /** A code sent by email. */
    OTP,
    GOOGLE,
    APPLE;

    /** Whether an outside identity provider vouches for the user, whose account can then be linked. */
    public boolean isExternal() {
        return this != OTP;
    }
}
