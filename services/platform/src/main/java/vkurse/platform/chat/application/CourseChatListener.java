/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import vkurse.platform.course.domain.CourseDrafted;

/**
 * Opens a course's chat when the course is made. The event is handled after the course is committed, and delivered
 * again if handling fails; a chat that is still missing is opened when someone first asks for it.
 */
@Component
class CourseChatListener {
    private final ChatAccess access;

    CourseChatListener(ChatAccess access) {
        this.access = access;
    }

    @ApplicationModuleListener
    public void on(CourseDrafted event) {
        access.open(event.schoolId(), event.courseId());
    }
}
