/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import vkurse.core.CoreProperties;

/**
 * The {@link Telemetry} every service has. The bean is always there; the flag decides which one it is, so callers never
 * look at the flag.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelemetryProperties.class)
public class TelemetryConfiguration {

    @Bean
    Telemetry telemetry(
            TelemetryProperties properties,
            CoreProperties core,
            Environment environment,
            ObjectProvider<BuildProperties> build,
            Clock clock,
            DataSource dataSource) {
        if (!properties.enabled()) {
            return new NoopTelemetry();
        }
        var service = environment.getProperty("spring.application.name", "unknown");
        var version = build.getIfAvailable();
        var source = new Source(service, core.nodeId(), version == null ? "unknown" : version.getVersion());
        return new KafkaTelemetry(properties, source, clock, dataSource);
    }
}
