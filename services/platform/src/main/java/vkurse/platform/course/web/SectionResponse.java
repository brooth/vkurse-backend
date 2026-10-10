/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.UUID;
import vkurse.platform.course.domain.Section;

public record SectionResponse(UUID id, UUID courseId, String title, int position) {

    public static SectionResponse of(Section section) {
        return new SectionResponse(section.getId(), section.getCourseId(), section.getTitle(), section.getPosition());
    }
}
