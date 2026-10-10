/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.course.web.LinkResponse;
import vkurse.platform.learning.application.HomeworkService;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.school.web.FileResponse;
import vkurse.platform.school.web.UploadResponse;
import vkurse.platform.storage.domain.StoredFile;

@RestController
class HomeworkController {
    private final HomeworkService homework;

    HomeworkController(HomeworkService homework) {
        this.homework = homework;
    }

    /** Step 1 of attaching a file to an answer: asks for a link to upload it with. */
    @PostMapping("/learning/lessons/{lessonId}/homework/files")
    UploadResponse requestUpload(
            @GetSession UserSession session, @PathVariable UUID lessonId, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                homework.requestUpload(session.userId(), lessonId, data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the file was sent to storage: makes it usable in an answer. */
    @PostMapping("/learning/lessons/{lessonId}/homework/files/{fileId}/confirm")
    FileResponse confirmUpload(
            @GetSession UserSession session, @PathVariable UUID lessonId, @PathVariable UUID fileId) {
        return FileResponse.of(homework.confirmUpload(session.userId(), lessonId, fileId));
    }

    /**
     * Hands in the caller's answer: text (Markdown), confirmed files, or both. Refused with 409
     * {@code learning/homework-not-expected} while an earlier answer waits for review or once one is accepted.
     */
    @PostMapping("/learning/lessons/{lessonId}/homework")
    SubmissionResponse handIn(
            @GetSession UserSession session, @PathVariable UUID lessonId, @Valid @RequestBody HandInRequest data) {
        return SubmissionResponse.of(homework.handIn(
                session.userId(), lessonId, data.text(), data.fileIds() == null ? List.of() : data.fileIds()));
    }

    /** The caller's answers to the lesson's homework, the latest first, with the staff's comments. */
    @GetMapping("/learning/lessons/{lessonId}/homework")
    List<SubmissionResponse> history(@GetSession UserSession session, @PathVariable UUID lessonId) {
        return homework.history(session.userId(), lessonId).stream()
                .map(SubmissionResponse::of)
                .toList();
    }

    /**
     * The school's answers, those that waited longest first. {@code status} keeps one status (the review queue is
     * {@code SUBMITTED}), {@code course} one course.
     */
    @GetMapping("/schools/{schoolId}/submissions")
    List<SubmissionResponse> queue(
            @GetSession UserSession session,
            @PathVariable UUID schoolId,
            @RequestParam(required = false) Submission.@Nullable Status status,
            @RequestParam(required = false) @Nullable UUID course,
            @RequestParam Map<String, String> params) {
        return homework.queue(session.userId(), schoolId, status, course, Paging.parse(params)).stream()
                .map(view -> SubmissionResponse.of(view, false))
                .toList();
    }

    /** One answer with its files, for the staff and for the student who handed it in. */
    @GetMapping("/submissions/{id}")
    SubmissionResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return SubmissionResponse.of(homework.get(session.userId(), id), true);
    }

    /** Signed links to the answer's files, by file id. */
    @PostMapping("/submissions/{id}/links")
    Map<UUID, LinkResponse> links(@GetSession UserSession session, @PathVariable UUID id) {
        return LinkResponse.of(homework.links(session.userId(), id));
    }

    /** Accepts the answer: the homework is done. */
    @PostMapping("/submissions/{id}/accept")
    SubmissionResponse accept(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody AcceptRequest data) {
        return SubmissionResponse.of(homework.accept(session.userId(), id, data.comment()), true);
    }

    /** Gives the answer back, saying what to change. The student hands in another. */
    @PostMapping("/submissions/{id}/return")
    SubmissionResponse giveBack(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody ReturnRequest data) {
        return SubmissionResponse.of(homework.giveBack(session.userId(), id, data.comment()), true);
    }

    record UploadRequest(
            @NotBlank @Size(max = StoredFile.MAX_NAME_LENGTH)
            String name,

            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Positive Long size) {}

    record HandInRequest(
            @Size(max = Submission.MAX_TEXT_LENGTH) @Nullable
            String text,

            @Size(max = Submission.MAX_FILES) @Nullable List<@NotNull UUID> fileIds) {}

    record AcceptRequest(
            @Size(max = Submission.MAX_COMMENT_LENGTH) @Nullable
            String comment) {}

    record ReturnRequest(
            @NotBlank @Size(max = Submission.MAX_COMMENT_LENGTH)
            String comment) {}
}
