# Learning module

> Students taking courses: who has which course, which lessons are open for them, what they completed, homework with its review by the staff, and quizzes.

| Layer | File | Description |
|---|---|---|
| `domain` | `Enrollment` | A student has a course: `open`, `renew`, `revoke`, `isActive` |
| | `AccessGrant` | How a course is given: by a paid order or by the staff, for some days or with no end |
| | `Curriculum` | A course as one student has it now: its lessons in order, and whether each is open |
| | `LessonCompletion` | A student completed a lesson |
| | `LessonWork` | What a lesson asks of a student (homework, quiz) and how far they are |
| | `Submission`, `SubmissionFiles` | One answer to a lesson's homework: `handIn`, `accept`, `giveBack` |
| | `QuizAttempt`, `AnswerSheet` | One graded try at a lesson's quiz |
| | `LessonCompleted`, `HomeworkReviewed` | Events |
| | `LearningErrors`, `*Repository` | |
| `application` | `EnrollmentService` | `listMine`; for the staff: `grant`, `revoke`, `list` |
| | `StudyService` | A student's `outline`, `lesson`, `links`, `complete` |
| | `HomeworkService` | A student's `requestUpload`, `confirmUpload`, `handIn`, `history`; the staff's `queue`, `accept`, `giveBack`; `get` and `links` for both |
| | `QuizService` | `answer`, `attempts` |
| | `EnrollmentListener` | Gives a course on `OrderPaid`; takes it away on `OrderRefunded`, when a student is removed from the school, and when an account is deleted |
| | `Enroller`, `Progress`, `LearningAccess`, `LearningNotifier` | Shared steps: giving and revoking, completing, who may reach what, the emails |
| `infrastructure` | `Jpa*Repository` | |
| `web` | `StudyController`, `HomeworkController`, `EnrollmentController` and their records | |

It depends on [`course`](../course/README.md) (what is studied), [`commerce`](../commerce/README.md) (its events), [`school`](../school/README.md) (roles, students), [`storage`](../storage/README.md) (homework files) and `user`. None of them depends on it.

## Rules

**Having a course**
- A student gets a course by a paid order (`OrderPaid`, handled a moment after the order is paid) or from the staff, by email. Either is for a number of days or with no end.
- There is one enrollment per student and course. Its access lasts until it is revoked or its days run out. **Getting the course again reuses the row, so the progress stays.**
- Getting a course one still has makes the access longer by the new days; it has no end if either has none. One order gives access once, however often its event is delivered.
- Access is taken away by a whole refund of the order that gave it, by the staff, when the student is removed from the school, and when their account is deleted. Nothing a student did is removed.
- Without access now, the course and everything in it is 404 for the student, as if it didn't exist.
- A student keeps a course that was unpublished (taken out of the catalog); a deleted course is gone. Only published lessons are shown and counted.

**Which lessons are open** (the lesson's `drip` and `stop` are set by the authors, in the [course module](../course/README.md#rules))
- Lessons go in the course's order: sections by position, then lessons by position.
- **A lesson is open when its drip time has come and every stop lesson before it is completed.** `AFTER_DAYS` counts from when the student first got the course; `AT_TIME` is the same for everyone.
- A lesson that isn't open is 403 `learning/lesson-locked`, with `opensAt` or `blockedBy` (the first stop lesson in the way). Its content, links, homework and quiz are all closed.
- A completed lesson stays open, whatever the authors change later.

**Completing a lesson**
- A lesson with homework is completed when the homework is accepted; one with a quiz, when the quiz is passed; one with both needs both. It then completes by itself.
- A lesson with neither is completed when the student marks it (`…/complete`). Marking a lesson whose work isn't done is 409 `learning/work-pending`. Video watching is not tracked.
- A completion is recorded once and never undone.

**Homework**
- An answer is text (Markdown, at most 20 000 characters), files (at most 10), or both. Files are uploaded in three steps, like the school's library, with the purpose `HOMEWORK_FILE` (50 MB, the library's types). They are kept for the school and are not in its library. Only the student's own confirmed uploads can be attached.
- A homework upload is refused with 402 `billing/payment-required` while the school owes Vkurse for storage, and links to lesson and homework files with 402 `billing/suspended` after 14 days of that ([billing](../billing/README.md)).
- A student answers when they haven't yet, or when their last answer was given back; otherwise 409 `learning/homework-not-expected`. An answer that waits for review can't be changed.
- **Every staff role reviews**, for every course of the school: accept (a comment is optional) or give back (a comment is required). A reviewed answer can't be reviewed again: 409 `learning/already-reviewed`.
- Each answer is kept, so a lesson's answers are the history of what was handed in and what the staff said. The student is emailed on each review (`homework-accepted`, `homework-returned`).
- An answer and its files are read by the student who handed it in and by the staff of the school; for anyone else it is 404.

**Quizzes**
- The student sends the chosen options of each question and is graded at once. A question is right when exactly its right options are chosen. The quiz is passed with the percent of right questions the author set.
- Attempts are unlimited and all kept. The result names the questions answered wrong, **never which options are right**: those leave the server only to the staff.
- Completion is judged by the lesson as it is now. If the authors change a quiz or remove a homework block, earlier attempts and answers stay as they were.

**Who may do what** (roles are in the [school module](../school/README.md#rules))

| | OWNER | ADMIN | AUTHOR | CURATOR |
|---|---|---|---|---|
| See who has a course and their progress | ✓ | ✓ | ✓ | ✓ |
| See, accept and give back homework | ✓ | ✓ | ✓ | ✓ |
| Give a course by hand, take it away | ✓ | ✓ | | |

| Error | When |
|---|---|
| 403 `learning/lesson-locked` | The lesson isn't open for the student yet |
| 409 `learning/work-pending` | Marking a lesson completed while its homework or quiz is not done |
| 409 `learning/homework-not-expected` | The lesson has no homework, or the student's answer waits for review or is accepted |
| 409 `learning/already-reviewed` | The answer was accepted or given back already |

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `GET /learning/courses` | | any user | The courses I have now: `{ enrollmentId, courseId, schoolId, title, summary, cover, startedAt, expiresAt, lessons, completed }[]` |
| `GET /learning/courses/{courseId}` | | the student | The course, and `sections[ { id, title, lessons[ { id, title, stop, homework, quiz, state } ] } ]` |
| `GET /learning/lessons/{id}` | | the student, if open | `{ id, courseId, sectionId, title, stop, state, blocks, work, homework }` |
| `POST /learning/lessons/{id}/links` | | the student, if open | `{ blockId: { url, expiresAt } }` |
| `POST /learning/lessons/{id}/complete` | | the student, if open | The lesson |
| `POST /learning/lessons/{id}/homework/files` | `{ name, contentType, size }` | the student, if open | `UploadResponse` |
| `POST /learning/lessons/{id}/homework/files/{fileId}/confirm` | | the uploader | `FileResponse` |
| `POST /learning/lessons/{id}/homework` | `{ text?, fileIds? }` | the student, if open | `SubmissionResponse` |
| `GET /learning/lessons/{id}/homework` | | the student | `SubmissionResponse[]`, the latest first |
| `POST /learning/lessons/{id}/quiz` | `{ answers: { questionId: [ optionId ] } }` | the student, if open | `{ id, createdAt, lessonId, right, total, passed, answers, wrongQuestionIds }` |
| `GET /learning/lessons/{id}/quiz` | | the student | The attempts, the latest first |
| `GET /courses/{id}/enrollments` | | staff | `EnrollmentResponse[]`, the latest first |
| `POST /courses/{id}/enrollments` | `{ email, accessDays? }` | owner, admin | `EnrollmentResponse` |
| `DELETE /enrollments/{id}` | | owner, admin | `204` |
| `GET /schools/{id}/submissions` | `status`, `course` | staff | `SubmissionResponse[]`, those that waited longest first |
| `GET /submissions/{id}` | | staff, the student | `SubmissionResponse` with `files` |
| `POST /submissions/{id}/links` | | staff, the student | `{ fileId: { url, expiresAt } }` |
| `POST /submissions/{id}/accept` | `{ comment? }` | staff | `SubmissionResponse` |
| `POST /submissions/{id}/return` | `{ comment }` | staff | `SubmissionResponse` |

Lists take `$limit` and `$skip`.

- `state`: `{ kind, opensAt?, blockedBy? }`, with `kind` one of `OPEN`, `COMPLETED`, `LOCKED`.
- `work`: `{ hasHomework, homework (the status of the last answer), hasQuiz, quizPassed, done }`.
- `SubmissionResponse`: `id`, `createdAt`, `schoolId`, `courseId`, `lessonId`, `lessonTitle`, `studentId`, `student { id, name, email, picture }` (for the staff), `text`, `fileIds`, `files` (when one answer is read), `status` (`SUBMITTED`, `ACCEPTED`, `RETURNED`), `reviewerId`, `comment`, `reviewedAt`.
- `EnrollmentResponse`: `id`, `createdAt`, `courseId`, `student`, `source` (`ORDER`, `GRANT`), `orderId`, `grantedBy`, `startedAt`, `expiresAt`, `revokedAt`, `active`, `lessons`, `completed`.
- In a student's `blocks`, a quiz option has no `correct`.

## Tables

- `enrollments`: `school_id`, `course_id`, `student_id` (unique with `course_id`), `source`, `order_id`, `granted_by`, `started_at`, `expires_at`, `revoked_at`.
- `lesson_completions`: `enrollment_id`, `course_id`, `lesson_id` (unique with `enrollment_id`), `student_id`.
- `homework_submissions`: `enrollment_id`, `school_id`, `course_id`, `lesson_id`, `student_id`, `text`, `files` (JSONB), `status`, `reviewer_id`, `comment`, `reviewed_at`.
- `quiz_attempts`: `enrollment_id`, `course_id`, `lesson_id`, `student_id`, `answers` (JSONB), `right_count`, `question_count`, `passed`.

`lesson_id` has no foreign key: a removed lesson is a removed row, and what students did in it stays.
