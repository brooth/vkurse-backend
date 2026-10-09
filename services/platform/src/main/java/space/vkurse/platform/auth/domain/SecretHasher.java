/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.domain;

/** Makes one-time secrets, and the keyed hashes they are stored as. */
public interface SecretHasher {

    /** A keyed hash of {@code value}. Without the key, a stored hash can't be checked against guesses. */
    String hash(String value);

    /** A random URL-safe secret with 256 bits of entropy, e.g. a one-time exchange code. */
    String randomSecret();

    /** A random code of {@code length} decimal digits, e.g. an email sign-in code. */
    String randomDigits(int length);
}
