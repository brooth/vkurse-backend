/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.school.domain.SchoolStudent;
import vkurse.platform.school.domain.SchoolStudentRepository;
import vkurse.platform.school.domain.StudentProfile;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.Email;

/** The students of a school: the staff's view of them, and how a user becomes one. */
@Service
@Transactional
public class SchoolStudentService {
    private final SchoolStudentRepository students;
    private final SchoolMemberService staff;
    private final UserService users;
    private final Clock clock;

    public SchoolStudentService(
            SchoolStudentRepository students, SchoolMemberService staff, UserService users, Clock clock) {
        this.students = students;
        this.staff = staff;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Makes a user a student of a school, if they aren't one. For the system, not for a caller: the module that sells
     * or opens a course calls it, and has checked the school itself.
     */
    public SchoolStudent enroll(UUID schoolId, UUID userId) {
        return enroll(schoolId, userId, null);
    }

    /** Adds a student by hand. Someone who has no account yet gets one, as at a first sign-in. */
    public StudentProfile add(UUID actorId, UUID schoolId, String address) {
        staff.require(schoolId, actorId).ensureCanManageStudents();
        var user = users.findOrRegister(new Email(address), null, null);
        return new StudentProfile(enroll(schoolId, user.getId(), actorId), user);
    }

    @Transactional(readOnly = true)
    public List<StudentProfile> list(UUID actorId, UUID schoolId, Paging paging) {
        staff.require(schoolId, actorId);
        return students.findBySchool(schoolId, paging.search(), paging.limit(), paging.offset());
    }

    public void remove(UUID actorId, UUID schoolId, UUID studentId) {
        staff.require(schoolId, actorId).ensureCanManageStudents();
        var student = students.findById(studentId)
                .filter(found -> found.belongsTo(schoolId))
                .orElseThrow(() -> new NotFoundException("Student not found"));
        students.remove(student);
    }

    private SchoolStudent enroll(UUID schoolId, UUID userId, @Nullable UUID addedBy) {
        return students.find(schoolId, userId)
                .orElseGet(() -> students.save(SchoolStudent.enroll(schoolId, userId, addedBy, clock.instant())));
    }
}
