/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkurse.common.security.JwtResourceServer;
import ru.vkurse.core.auth.infrastructure.RsaJwtTokens;

/** The public keys our tokens are signed with, for the services that verify them. Public. */
@RestController
class JwksController {
    private final RsaJwtTokens tokens;

    JwksController(RsaJwtTokens tokens) {
        this.tokens = tokens;
    }

    @GetMapping(JwtResourceServer.JWKS_PATH)
    Map<String, Object> jwks() {
        return tokens.publicJwkSet();
    }
}
