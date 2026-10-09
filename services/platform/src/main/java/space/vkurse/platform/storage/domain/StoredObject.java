/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.storage.domain;

/** What storage really holds for a file: its size in bytes and the content type it was uploaded with. */
public record StoredObject(long size, String contentType) {}
