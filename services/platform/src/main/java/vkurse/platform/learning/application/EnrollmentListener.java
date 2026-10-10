/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import vkurse.platform.commerce.domain.OrderPaid;
import vkurse.platform.commerce.domain.OrderRefunded;
import vkurse.platform.learning.domain.AccessGrant;
import vkurse.platform.school.domain.SchoolStudentRemoved;
import vkurse.platform.user.domain.UserDeleted;

/**
 * Gives and takes courses as things happen elsewhere. Each event is handled after the change that raised it is
 * committed, in a transaction of its own, and delivered again if handling fails.
 */
@Component
class EnrollmentListener {
    private final Enroller enroller;

    EnrollmentListener(Enroller enroller) {
        this.enroller = enroller;
    }

    /** A paid order gives its course. */
    @ApplicationModuleListener
    public void on(OrderPaid event) {
        enroller.give(
                event.schoolId(),
                event.courseId(),
                event.buyerId(),
                AccessGrant.byOrder(event.orderId(), event.accessDays()));
    }

    /** A refunded order takes its course back. The student's progress stays. */
    @ApplicationModuleListener
    public void on(OrderRefunded event) {
        enroller.takeBack(event.buyerId(), event.courseId(), event.orderId());
    }

    @ApplicationModuleListener
    public void on(SchoolStudentRemoved event) {
        enroller.revokeAll(event.schoolId(), event.userId());
    }

    @ApplicationModuleListener
    public void on(UserDeleted event) {
        enroller.revokeAll(event.userId());
    }
}
