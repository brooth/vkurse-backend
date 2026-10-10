/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/** The staff accepted a student's homework, or gave it back. */
public record HomeworkReviewed(UUID submissionId, UUID lessonId, UUID studentId, boolean accepted)
        implements DomainEvent {}
