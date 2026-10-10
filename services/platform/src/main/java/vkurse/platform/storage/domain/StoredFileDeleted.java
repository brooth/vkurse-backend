/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/**
 * A file was deleted. Published in the transaction that deleted it, so a module that still uses the file can refuse
 * by throwing.
 */
public record StoredFileDeleted(UUID fileId) implements DomainEvent {}
