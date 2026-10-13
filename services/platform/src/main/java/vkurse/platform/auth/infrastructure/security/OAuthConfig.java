/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import vkurse.core.logging.HttpClients;

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

    private static final Logger log = LoggerFactory.getLogger(OAuthConfig.class);

    private static final String GOOGLE = "google";

    // What .env.example sets where no real OAuth client is needed.
    private static final String PLACEHOLDER = "placeholder";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

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
            AppleClientSecret appleClientSecret, HttpClients httpClients, ClientRegistrationRepository registrations) {
        for (var provider : List.of(GOOGLE, APPLE)) {
            var registration = registrations.findByRegistrationId(provider);
            if (registration != null && PLACEHOLDER.equals(registration.getClientId())) {
                log.warn("The {} OAuth client id is a placeholder, sign-in with {} will fail", provider, provider);
            }
        }
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        // Spring Security's own client, with its converters and error handler, plus timeouts and the log.
        client.setRestClient(httpClients
                .builder("oauth", CONNECT_TIMEOUT, READ_TIMEOUT)
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
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
