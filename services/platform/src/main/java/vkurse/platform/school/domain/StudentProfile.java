/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import vkurse.platform.user.domain.User;

/** A student of a school with the user they are. */
public record StudentProfile(SchoolStudent student, User user) {}
