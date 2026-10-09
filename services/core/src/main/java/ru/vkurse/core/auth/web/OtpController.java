/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkurse.core.auth.application.OtpSignInService;

/** Email code sign-in. Public. */
@RestController
@RequestMapping("/auth/otp")
class OtpController {
    private final OtpSignInService otp;

    OtpController(OtpSignInService otp) {
        this.otp = otp;
    }

    /** Emails a code. The returned {@code token} goes with the code to {@code /auth/otp/login}. */
    @PostMapping
    SendResponse send(@Valid @RequestBody SendRequest data) {
        var ticket = otp.send(data.email());
        return new SendResponse(ticket.token(), ticket.expiresAt());
    }

    @PostMapping("/login")
    IdentityResponse signIn(
            @Valid @RequestBody LoginRequest data,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) @Nullable String userAgent) {
        return IdentityResponse.of(otp.signIn(data.token(), data.code(), userAgent));
    }

    record SendRequest(
            @NotBlank @Email @Size(max = ru.vkurse.core.user.domain.Email.MAX_LENGTH)
            String email) {}

    /** {@code token} identifies the code in {@code POST /auth/otp/login}. */
    record SendResponse(UUID token, Instant expiresAt) {}

    record LoginRequest(
            @NotNull UUID token,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
}
