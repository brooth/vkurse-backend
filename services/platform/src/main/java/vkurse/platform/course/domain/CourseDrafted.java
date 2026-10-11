/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/** A course was made, as a draft. */
public record CourseDrafted(UUID courseId, UUID schoolId) implements DomainEvent {}
