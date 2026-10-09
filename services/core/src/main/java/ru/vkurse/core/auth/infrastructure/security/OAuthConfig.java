/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Google and Apple sign-in on Spring Security's OAuth2 client. The registrations live in {@code application.yaml}
 * ({@code spring.security.oauth2.client}); {@code SecurityConfig} plugs these beans into {@code oauth2Login}.
 */
@Configuration(proxyBeanMethods = false)
class OAuthConfig {
    /** {@code GET /auth/oauth/{provider}?callback=…} starts a sign-in. */
    static final String BASE_URI = "/auth/oauth";

    /** The provider redirects back here: {@code /auth/oauth/{provider}/callback}. */
    static final String CALLBACK_URI = BASE_URI + "/*/callback";

    static final String APPLE = "apple";

    /**
     * We don't call provider APIs after sign-in, so provider tokens are not kept. Spring's default repository would
     * keep every signed-in user's tokens in memory forever.
     */
    static final OAuth2AuthorizedClientRepository NO_AUTHORIZED_CLIENTS = new OAuth2AuthorizedClientRepository() {
        @Override
        public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
                String clientRegistrationId, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient authorizedClient,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(
                String clientRegistrationId,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}
    };

    /**
     * Builds the authorization request. Spring Security adds PKCE to every client by default, which Google supports.
     * Apple doesn't document PKCE, so it is removed there, and Apple gets {@code response_mode=form_post}.
     */
    @Bean
    OAuth2AuthorizationRequestResolver oauthAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, BASE_URI);
        resolver.setAuthorizationRequestCustomizer(builder -> {
            var registrationId = new AtomicReference<@Nullable Object>();
            builder.attributes(attributes -> registrationId.set(attributes.get(OAuth2ParameterNames.REGISTRATION_ID)));
            if (APPLE.equals(registrationId.get())) {
                builder.attributes(attributes -> attributes.remove(PkceParameterNames.CODE_VERIFIER));
                builder.additionalParameters(parameters -> {
                    parameters.remove(PkceParameterNames.CODE_CHALLENGE);
                    parameters.remove(PkceParameterNames.CODE_CHALLENGE_METHOD);
                    // Apple requires form_post when asking for the name or email scope.
                    parameters.put("response_mode", "form_post");
                });
            }
        });
        return resolver;
    }

    /** Exchanges the authorization code for tokens, adding Apple's generated client secret. */
    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oauthTokenResponseClient(
            AppleClientSecret appleClientSecret) {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        // Parameters returned here replace the default ones with the same name.
        client.setParametersConverter(request -> {
            var parameters = new LinkedMultiValueMap<String, String>();
            var registration = request.getClientRegistration();
            if (APPLE.equals(registration.getRegistrationId())) {
                parameters.set(OAuth2ParameterNames.CLIENT_SECRET, appleClientSecret.get(registration.getClientId()));
            }
            return parameters;
        });
        return client;
    }
}
