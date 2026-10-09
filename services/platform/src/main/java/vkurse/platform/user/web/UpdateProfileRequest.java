/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.user.web;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import vkurse.core.validation.Validation.LanguageCode;
import vkurse.core.validation.Validation.TimeZone;
import vkurse.platform.user.domain.User;

/** What a user may change in their own profile. {@code null} leaves a field unchanged. */
record UpdateProfileRequest(
        @Size(min = 1, max = User.MAX_NAME_LENGTH) @Nullable String name,
        @LanguageCode @Nullable String locale,
        @TimeZone @Nullable String timezone) {}
