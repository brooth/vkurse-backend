/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import vkurse.platform.school.domain.School;

/** A published course as the catalog shows it: with its school, and how many lessons one can take. */
public record CourseCard(Course course, School school, long lessons) {}
