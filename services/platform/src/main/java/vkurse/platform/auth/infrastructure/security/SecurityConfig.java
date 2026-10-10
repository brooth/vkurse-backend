/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure.security;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import vkurse.core.error.Errors.ClientException;
import vkurse.core.error.Errors.ForbiddenException;
import vkurse.core.error.Errors.UnauthorizedException;
import vkurse.core.security.JwtResourceServer;
import vkurse.core.security.UserSession;
import vkurse.platform.auth.AuthProperties;
import vkurse.platform.auth.domain.AuthErrors.InvalidSessionException;
import vkurse.platform.auth.domain.AuthErrors.ReloginRequiredException;
import vkurse.platform.auth.domain.CallbackUrl;
import vkurse.platform.auth.infrastructure.RsaJwtTokens;

/**
 * The security filter chain. The service is stateless: every request except the public ones below carries an access
 * token, checked by the OAuth2 resource server and turned into a {@link UserSession}. Google and Apple sign-in run
 * through {@code oauth2Login} under {@code /auth/oauth}.
 *
 * <p>Errors raised in the filter chain never reach a controller, so they are handed to Spring MVC's
 * {@link HandlerExceptionResolver}: {@code ErrorHandler} writes the same problem body as everywhere else.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
        "/auth/**", JwtResourceServer.JWKS_PATH, "/schools/by-slug/*", "/catalog/**", "/actuator/health", "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RsaJwtTokens tokens,
            SessionAuthenticationConverter sessionConverter,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver,
            OAuthRequestStore authorizationRequests,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenResponseClient,
            OAuthLoginHandler oauthLogin,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        AuthenticationEntryPoint entryPoint =
                (request, response, e) -> errors.resolveException(request, response, null, toClientException(e));

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(server -> server.authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.decoder(tokens.accessDecoder()).jwtAuthenticationConverter(sessionConverter)))
                .oauth2Login(login -> login
                        // A custom login page stops Spring from generating its own HTML page at /login.
                        .loginPage(OAuthConfig.BASE_URI)
                        .authorizationEndpoint(endpoint -> endpoint.baseUri(OAuthConfig.BASE_URI)
                                .authorizationRequestResolver(authorizationRequestResolver)
                                .authorizationRequestRepository(authorizationRequests))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(OAuthConfig.CALLBACK_URI))
                        .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenResponseClient))
                        .authorizedClientRepository(OAuthConfig.NO_AUTHORIZED_CLIENTS)
                        .successHandler(oauthLogin)
                        .failureHandler(oauthLogin)
                        .withObjectPostProcessor(new ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter>() {
                            @Override
                            public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(O filter) {
                                // Errors before the redirect to the provider (bad callback, unknown provider).
                                filter.setAuthenticationFailureHandler(oauthLogin::onAuthorizationRequestFailure);
                                return filter;
                            }
                        }))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, e) ->
                                errors.resolveException(request, response, null, new ForbiddenException())));
        return http.build();
    }

    /** CORS for the frontend origins in {@code vkurse.auth.web-origins}. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(
                properties.webOrigins().stream().map(CallbackUrl::origin).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static ClientException toClientException(AuthenticationException e) {
        if (e instanceof AuthFailure failure) {
            return failure.getError();
        }
        if (e instanceof InvalidBearerTokenException) {
            if (e.getCause() instanceof JwtValidationException validation
                    && validation.getErrors().stream()
                            .anyMatch(error -> JwtResourceServer.EXPIRED.equals(error.getErrorCode()))) {
                return new InvalidSessionException("The access token has expired");
            }
            return new ReloginRequiredException("Invalid access token");
        }
        return new UnauthorizedException("Authentication required");
    }
}
