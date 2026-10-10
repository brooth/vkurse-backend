/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/** The staff removed a student from a school. Not raised when a deleted account leaves its schools. */
public record SchoolStudentRemoved(UUID schoolId, UUID userId) implements DomainEvent {}
