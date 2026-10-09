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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vkurse.core.error.ErrorHandler;
import vkurse.core.notification.NotificationConfiguration;
import vkurse.core.persistence.LockService;

/**
 * The beans a service gets by depending on this library: the error handler, the clock, cluster locks and
 * notifications. A service doesn't component-scan {@code vkurse.core}.
 */
@AutoConfiguration
@EnableConfigurationProperties(CoreProperties.class)
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@Import({ErrorHandler.class, LockService.class, NotificationConfiguration.class})
public class CoreAutoConfiguration {

    /** The application clock. Inject it instead of calling {@code Instant.now()}, so time can be controlled later. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
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
