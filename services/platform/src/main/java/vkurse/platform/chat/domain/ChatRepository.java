/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository {

    Optional<Chat> findById(UUID id);

    Optional<Chat> findByCourse(UUID courseId);

    /** The chats of those courses and of every course of those schools, the newest first. */
    List<Chat> findByCoursesOrSchools(Collection<UUID> courseIds, Collection<UUID> schoolIds, int limit, int offset);

    Chat save(Chat chat);
}
