/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import vkurse.platform.chat.domain.Chat;
import vkurse.platform.chat.domain.ChatMember;
import vkurse.platform.course.domain.Course;

/** A chat with the caller in it: the chat, its course, and who the caller is there. */
public record InChat(Chat chat, Course course, ChatMember member) {}
