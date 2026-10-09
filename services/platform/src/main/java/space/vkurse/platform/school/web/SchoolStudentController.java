/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.application.SchoolStudentService;

/** The students of a school, for its staff. */
@RestController
@RequestMapping("/schools/{schoolId}/students")
class SchoolStudentController {
    private final SchoolStudentService students;

    SchoolStudentController(SchoolStudentService students) {
        this.students = students;
    }

    /** {@code $search} looks in the students' names and emails. */
    @GetMapping
    List<StudentResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return students.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(StudentResponse::of)
                .toList();
    }

    /** Adds a student by email. Someone without an account gets one. Adding a student twice changes nothing. */
    @PostMapping
    StudentResponse add(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody AddRequest data) {
        return StudentResponse.of(students.add(session.userId(), schoolId, data.email()));
    }

    @DeleteMapping("/{studentId}")
    ResponseEntity<Void> remove(
            @GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID studentId) {
        students.remove(session.userId(), schoolId, studentId);
        return ResponseEntity.noContent().build();
    }

    record AddRequest(
            @NotBlank @Email @Size(max = space.vkurse.platform.user.domain.Email.MAX_LENGTH)
            String email) {}
}
