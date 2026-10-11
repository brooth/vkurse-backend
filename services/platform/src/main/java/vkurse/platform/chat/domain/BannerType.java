/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

/** What a banner is for; it decides how the message stands out. */
public enum BannerType {
    /** News of the course: a new lesson, a live session. */
    ANNOUNCE,
    /** Something worth knowing. */
    INFO,
    /** Something that needs attention: a deadline, a change. */
    WARNING,
    /** Something urgent or gone wrong. */
    DANGER
}
