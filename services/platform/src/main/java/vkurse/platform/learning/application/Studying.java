/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import vkurse.platform.course.application.CourseReader;
import vkurse.platform.learning.domain.Curriculum;
import vkurse.platform.learning.domain.Enrollment;

/** A course one student has: their enrollment, what the course holds, and which lessons are open for them. */
public record Studying(Enrollment enrollment, CourseReader.Material material, Curriculum curriculum) {}
