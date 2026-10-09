/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vkurse.platform.school.domain.SchoolErrors.OwnerCannotLeaveException;
import vkurse.platform.school.domain.SchoolMemberRepository;
import vkurse.platform.school.domain.SchoolStudentRepository;
import vkurse.platform.user.domain.UserDeleted;

/**
 * A deleted account leaves every school, as staff and as a student. An account that owns a school can't be deleted:
 * this is a plain {@code @EventListener}, so it runs in the transaction that deletes the user, and its exception rolls
 * the deletion back.
 */
@Component
class SchoolUserDeletedListener {
    private final SchoolMemberRepository members;
    private final SchoolStudentRepository students;

    SchoolUserDeletedListener(SchoolMemberRepository members, SchoolStudentRepository students) {
        this.members = members;
        this.students = students;
    }

    @EventListener
    @Transactional
    public void on(UserDeleted event) {
        if (members.ownsLiveSchool(event.userId())) {
            throw new OwnerCannotLeaveException();
        }
        members.deleteByUser(event.userId());
        students.deleteByUser(event.userId());
    }
}
