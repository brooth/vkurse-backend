/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import vkurse.core.error.Errors.ServerException;

/** No target of a notification took it. The cause is the last failure. */
public class DeliveryFailedException extends ServerException {
    public DeliveryFailedException(Throwable lastFailure) {
        super("Notification was not delivered: " + lastFailure.getMessage(), lastFailure);
    }
}
