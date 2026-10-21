/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.JdbcTemplate;
import vkurse.core.error.ErrorHandler;
import vkurse.core.logging.HttpClients;
import vkurse.core.logging.HttpLogFilter;
import vkurse.core.logging.JobLog;
import vkurse.core.logging.LogMasker;
import vkurse.core.logging.LogProperties;
import vkurse.core.logging.UserLogFilter;
import vkurse.core.notification.NotificationConfiguration;
import vkurse.core.persistence.LockService;
import vkurse.core.telemetry.Telemetry;
import vkurse.core.telemetry.TelemetryConfiguration;

/**
 * The beans a service gets by depending on this library: the error handler, the clock, cluster locks, logging and
 * telemetry of requests, outside calls and jobs, and notifications. A service doesn't component-scan {@code vkurse.core}.
 */
@AutoConfiguration
@EnableConfigurationProperties({CoreProperties.class, LogProperties.class})
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@Import({
    ErrorHandler.class,
    LockService.class,
    LogMasker.class,
    HttpLogFilter.class,
    UserLogFilter.class,
    HttpClients.class,
    NotificationConfiguration.class,
    TelemetryConfiguration.class
})
public class CoreAutoConfiguration {

    /** The application clock. Inject it instead of calling {@code Instant.now()}, so time can be controlled later. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Names each scheduled job in the log. An infrastructure bean, like the other advisors (ShedLock's, transactions). */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static JobLog jobLog(ObjectProvider<Telemetry> telemetry) {
        return new JobLog(telemetry);
    }

    /**
     * Carries the log context ({@code requestId}, {@code userId}) to work a request hands to another thread: an event
     * listener that runs after the commit logs under the request that caused it.
     */
    @Bean
    TaskDecorator logContextTaskDecorator() {
        return task -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }

    /** Locks for {@code @SchedulerLock} jobs and {@link LockService}, stored in the {@code shedlock} table. */
    @Bean
    LockProvider lockProvider(DataSource dataSource, CoreProperties properties) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(properties.database().schema() + ".shedlock")
                .usingDbTime()
                .build());
    }
}
