/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

/** The notification beans, loaded by {@code CoreAutoConfiguration}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
@Import({
    NotificationService.class,
    NotificationRepository.class,
    NotificationJobs.class,
    TemplateRenderer.class,
    SmtpEmailSender.class,
    TelegramBotSender.class
})
public class NotificationConfiguration {

    /**
     * Adds {@link Notification} to the entities Hibernate maps. A service scans only its own packages for entities,
     * and the list of those packages is read too early (by Spring Data's auto-configuration) for this library to add
     * its own to it.
     */
    @Bean
    static BeanPostProcessor notificationEntityRegistrar() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof PersistenceManagedTypes types ? withNotification(types) : bean;
            }
        };
    }

    private static PersistenceManagedTypes withNotification(PersistenceManagedTypes types) {
        var classNames = new ArrayList<>(types.getManagedClassNames());
        if (!classNames.contains(Notification.class.getName())) {
            classNames.add(Notification.class.getName());
        }
        return new PersistenceManagedTypes() {
            @Override
            public List<String> getManagedClassNames() {
                return classNames;
            }

            @Override
            public List<String> getManagedPackages() {
                return types.getManagedPackages();
            }

            @Override
            public @Nullable URL getPersistenceUnitRootUrl() {
                return types.getPersistenceUnitRootUrl();
            }
        };
    }
}
