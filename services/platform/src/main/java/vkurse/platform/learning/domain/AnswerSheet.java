/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a student answered in one quiz attempt, and which questions that got wrong. Stored as one JSON value.
 *
 * @param chosen the option ids chosen, by question id
 */
public record AnswerSheet(Map<UUID, List<UUID>> chosen, List<UUID> wrongQuestionIds) {}
