/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.monitoring;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import vkurse.core.security.JwtResourceServer;

/**
 * Only platform issues tokens. This service verifies access tokens with platform's published keys and makes no call to
 * platform per request. Everything but the health check needs one; what needs an admin says so with {@code @AdminOnly}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, MonitoringProperties properties, Clock clock)
            throws Exception {
        var platform = properties.platformUrl().toString();
        var decoder = JwtResourceServer.decoder(
                platform + JwtResourceServer.JWKS_PATH, platform, JwtResourceServer.ACCESS, clock);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(server -> server.jwt(
                        jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(JwtResourceServer.sessionConverter())));
        return http.build();
    }
}
