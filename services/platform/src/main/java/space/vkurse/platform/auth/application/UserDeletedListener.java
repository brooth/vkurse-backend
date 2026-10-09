/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.platform.auth.domain.LinkedAccountRepository;
import space.vkurse.platform.user.domain.UserDeleted;

/**
 * A deleted account can't sign in any more: its sessions are revoked and its linked Google/Apple accounts removed.
 * A plain {@code @EventListener}, not {@code @ApplicationModuleListener}, so this runs in the transaction that deletes
 * the user and no session outlives the account.
 */
@Component
class UserDeletedListener {
    private final SessionService sessions;
    private final LinkedAccountRepository linkedAccounts;

    UserDeletedListener(SessionService sessions, LinkedAccountRepository linkedAccounts) {
        this.sessions = sessions;
        this.linkedAccounts = linkedAccounts;
    }

    @EventListener
    @Transactional
    public void on(UserDeleted event) {
        sessions.revokeAll(event.userId());
        linkedAccounts.deleteByUser(event.userId());
    }
}
