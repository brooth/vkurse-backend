/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import java.time.Instant;
import java.util.UUID;
import space.vkurse.platform.school.domain.StudentProfile;

/** A student of a school. {@code createdAt} is when they joined. */
public record StudentResponse(UUID id, Instant createdAt, PersonResponse user) {

    public static StudentResponse of(StudentProfile profile) {
        var student = profile.student();
        return new StudentResponse(student.getId(), student.getCreatedAt(), PersonResponse.of(profile.user()));
    }
}
