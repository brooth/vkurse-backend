/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.List;

/** A course with its sections and lessons, each list in the order it is shown. */
public record Outline(Course course, List<Section> sections, List<Lesson> lessons) {}
