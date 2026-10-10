/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.util.Map;
import org.springframework.stereotype.Component;
import vkurse.core.notification.Content;
import vkurse.core.notification.Message;
import vkurse.core.notification.NotificationService;
import vkurse.core.notification.Target;
import vkurse.platform.PlatformProperties;
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.school.application.SchoolStudentService;
import vkurse.platform.user.application.UserService;

/**
 * What follows an order becoming paid, in the same transaction: the buyer becomes a student of the school, and gets an
 * email. (The course itself is given by whoever listens for {@code OrderPaid}.)
 */
@Component
class OrderFulfilment {
    private final SchoolStudentService students;
    private final UserService users;
    private final NotificationService notifications;
    private final PlatformProperties properties;

    OrderFulfilment(
            SchoolStudentService students,
            UserService users,
            NotificationService notifications,
            PlatformProperties properties) {
        this.students = students;
        this.users = users;
        this.notifications = notifications;
        this.properties = properties;
    }

    /** Call it once, in the transaction that made the order paid. */
    void fulfil(Order order) {
        students.enroll(order.getSchoolId(), order.getBuyerId());
        users.findById(order.getBuyerId())
                .ifPresent(buyer -> notifications.submit(Message.of(
                        Content.template(
                                "order-paid",
                                buyer.getLocale(),
                                Map.of(
                                        "name", buyer.getName(),
                                        "courseTitle", order.getCourseTitle(),
                                        "offerName", order.getOfferName(),
                                        "total", order.getTotalAmount().value().toPlainString(),
                                        "link", properties.webUrl().toString())),
                        Target.email(buyer.getEmail().value()))));
    }
}
