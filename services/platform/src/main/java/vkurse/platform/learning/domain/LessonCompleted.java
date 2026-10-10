/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

public record LessonCompleted(UUID enrollmentId, UUID lessonId, UUID studentId) implements DomainEvent {}
