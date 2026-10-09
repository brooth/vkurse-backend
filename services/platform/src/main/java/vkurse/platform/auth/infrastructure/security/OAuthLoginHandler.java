/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import vkurse.core.error.Errors.ClientException;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.error.Errors.ServerException;
import vkurse.platform.auth.application.OAuthSignInService;
import vkurse.platform.auth.domain.ProviderAccount;
import vkurse.platform.auth.domain.SignInProvider;

/**
 * Finishes a Google or Apple sign-in on the HTTP side: reads what the provider said about the user, hands it to
 * {@link OAuthSignInService} and redirects the browser to the frontend {@code callback} with a one-time {@code code}
 * (or an {@code error} code). The frontend trades the code for tokens with {@code POST /auth/exchange}.
 */
@Component
class OAuthLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthLoginHandler.class);

    private final OAuthSignInService oauthSignIn;
    private final JsonMapper json;
    private final HandlerExceptionResolver errors;

    OAuthLoginHandler(
            OAuthSignInService oauthSignIn,
            JsonMapper json,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        this.oauthSignIn = oauthSignIn;
        this.json = json;
        this.errors = errors;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var callback = (String) request.getAttribute(OAuthRequestStore.CALLBACK_ATTRIBUTE);
        if (callback == null) {
            errors.resolveException(request, response, null, new ServerException("OAuth sign-in without a callback"));
            return;
        }
        try {
            var code = oauthSignIn.signIn(
                    account((OAuth2AuthenticationToken) authentication, request),
                    request.getHeader(HttpHeaders.USER_AGENT));
            redirect(response, callback, "code", code);
        } catch (ClientException e) {
            log.warn("OAuth sign-in rejected: {}", e.getMessage());
            redirect(response, callback, "error", e.getCode());
        } catch (RuntimeException e) {
            log.error("OAuth sign-in failed", e);
            redirect(response, callback, "error", "oauth-failed");
        }
    }

    /** The provider sign-in failed or was cancelled. */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("OAuth sign-in failed: {}", exception.getMessage());
        var callback = (String) request.getAttribute(OAuthRequestStore.CALLBACK_ATTRIBUTE);
        if (callback == null) {
            errors.resolveException(
                    request,
                    response,
                    null,
                    new ClientException(
                            HttpStatus.BAD_REQUEST, "oauth-failed", "The sign-in has expired, start again"));
            return;
        }
        redirect(response, callback, "error", "oauth-failed");
    }

    /** Starting a sign-in failed, before the redirect to the provider: answered with a problem body. */
    void onAuthorizationRequestFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        var error = switch (exception.getCause()) {
            case ClientException client -> client;
            // Spring's InvalidClientRegistrationIdException (not public) for an unknown /auth/oauth/{provider}.
            case IllegalArgumentException _ -> new NotFoundException("Unknown sign-in provider");
            case null, default -> exception;
        };
        errors.resolveException(request, response, null, error);
    }

    private ProviderAccount account(OAuth2AuthenticationToken token, HttpServletRequest request) {
        var oidcUser = (OidcUser) Objects.requireNonNull(token.getPrincipal());
        var provider =
                SignInProvider.valueOf(token.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT));
        // Google gives the name in the id token; Apple only in a `user` form field, and only on the first sign-in.
        var name = provider == SignInProvider.APPLE ? appleName(request.getParameter("user")) : oidcUser.getFullName();
        return new ProviderAccount(
                provider,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                name,
                oidcUser.getPicture());
    }

    private @Nullable String appleName(@Nullable String user) {
        if (user == null) {
            return null;
        }
        try {
            var name = json.readTree(user).path("name");
            return Stream.of(
                            name.path("firstName").asString(""),
                            name.path("lastName").asString(""))
                    .filter(part -> !part.isBlank())
                    .collect(Collectors.joining(" "));
        } catch (JacksonException e) {
            log.warn("Cannot read the Apple user field", e);
            return null;
        }
    }

    private static void redirect(HttpServletResponse response, String callback, String name, String value)
            throws IOException {
        var url = UriComponentsBuilder.fromUriString(callback)
                .queryParam(name, value)
                .encode()
                .build()
                .toUriString();
        response.sendRedirect(url);
    }
}
