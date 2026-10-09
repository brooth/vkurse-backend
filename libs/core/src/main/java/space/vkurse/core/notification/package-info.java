/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
/**
 * Notifications by email and Telegram: {@code NotificationService.send} delivers at once, {@code submit} stores the
 * message and a job delivers it with retries. A service adds its own templates and the {@code notifications} table.
 */
@NullMarked
package space.vkurse.core.notification;

import org.jspecify.annotations.NullMarked;
