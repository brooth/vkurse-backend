/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.platform.auth.application.SignInService;

/** Token endpoints. Public: the caller proves itself with the token in the body. */
@RestController
@RequestMapping("/auth")
class AuthController {
    private final SignInService signIn;

    AuthController(SignInService signIn) {
        this.signIn = signIn;
    }

    /** New tokens for a refresh token. The old refresh token stops working. */
    @PostMapping("/refresh")
    IdentityResponse refresh(@Valid @RequestBody RefreshRequest data) {
        return IdentityResponse.of(signIn.refresh(data.refreshToken()));
    }

    /** Tokens for the one-time code an OAuth sign-in redirected the browser with. */
    @PostMapping("/exchange")
    IdentityResponse exchange(@Valid @RequestBody ExchangeRequest data) {
        return IdentityResponse.of(signIn.exchange(data.code()));
    }

    record RefreshRequest(@NotBlank String refreshToken) {}

    record ExchangeRequest(@NotBlank String code) {}
}
