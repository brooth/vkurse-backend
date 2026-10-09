/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

/**
 * Authentication: our own JWTs, sessions, email OTP, Google and Apple sign-in, and the security filter chain. Every
 * route is authenticated unless {@code SecurityConfig} lists it as public.
 */
@NullMarked
@ApplicationModule(displayName = "Auth")
package space.vkurse.platform.auth;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
