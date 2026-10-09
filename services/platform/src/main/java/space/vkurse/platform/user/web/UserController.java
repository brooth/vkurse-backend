/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.platform.user.application.UserService;

/** The caller's own profile. */
@RestController
@RequestMapping("/users")
class UserController {
    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserResponse getMe(@GetSession UserSession session) {
        return UserResponse.of(users.getMe(session.userId()));
    }

    @PatchMapping("/me")
    UserResponse updateMe(@GetSession UserSession session, @Valid @RequestBody UpdateProfileRequest data) {
        return UserResponse.of(users.updateMe(session.userId(), data.name(), data.locale(), data.timezone()));
    }

    /** Deletes the account. Its sessions and linked sign-in accounts are deleted with it. */
    @DeleteMapping("/me")
    ResponseEntity<Void> deleteMe(@GetSession UserSession session) {
        users.deleteMe(session.userId());
        return ResponseEntity.noContent().build();
    }
}
