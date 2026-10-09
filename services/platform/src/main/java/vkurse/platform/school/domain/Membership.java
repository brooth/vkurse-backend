/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

/** A school together with what a user is in it: how the staff see their school. */
public record Membership(School school, SchoolMember member) {}
