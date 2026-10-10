/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.school.application.SchoolMemberService;

/**
 * Who may manage what a school sells: those of its staff whose role allows it. A school the caller doesn't work in is
 * "not found", like something that doesn't exist. Call it in a transaction.
 */
@Component
class SalesAccess {
    private final SchoolMemberService staff;

    SalesAccess(SchoolMemberService staff) {
        this.staff = staff;
    }

    /**
     * Checks that the caller may manage the sales of that school.
     *
     * @param what the thing the caller asked for, to name in "… not found"
     */
    void ensureManages(UUID userId, UUID schoolId, String what) {
        try {
            staff.require(schoolId, userId).ensureCanManageSales();
        } catch (NotFoundException e) {
            throw new NotFoundException(what + " not found");
        }
    }
}
