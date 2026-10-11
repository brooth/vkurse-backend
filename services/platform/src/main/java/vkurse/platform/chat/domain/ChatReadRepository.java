/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadRepository {

    Optional<ChatRead> find(UUID chatId, UUID userId);

    ChatRead save(ChatRead read);
}
