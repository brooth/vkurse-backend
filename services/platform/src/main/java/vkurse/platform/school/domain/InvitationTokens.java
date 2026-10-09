/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

/** Makes the secret token of an invitation link, and the hash it is stored as. */
public interface InvitationTokens {

    /** A random URL-safe token with 256 bits of entropy. */
    String random();

    /** A hash of {@code token}. A token is long and random, so a stored hash can't be guessed back. */
    String hash(String token);
}
