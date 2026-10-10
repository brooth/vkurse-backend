# M8: Learning

> Students get the courses they bought: enrollments, lessons that open by drip and stop, progress, homework reviewed by the staff, and quizzes, in the `learning` module of `services/platform`. Designed fresh: there is no NestJS source for it. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/learning/README.md).

## Scope

| Part | State |
|---|---|
| `Enrollment`: given by a paid order or by the staff, for days or with no end; revoked; renewed with the progress kept | Done |
| Lesson opening by drip (after days, at a time) and stop lessons | Done |
| Progress: completed lessons, "my courses", a course's students for the staff | Done |
| Homework block; answers with text and files; review by the staff (accept, give back); history; emails | Done |
| Quiz block with single- and multiple-choice questions, graded by the server | Done |
| Access taken away by a refund, by removal from the school, by deleting the account | Done |
| Buying a course again after its access ran out (a change in commerce) | Done |
| A course-level "strictly in order" setting | Not done: `stop` on each lesson does it |
| Free-text quiz questions, a limit on attempts, a timer | Not done |
| A conversation between student and reviewer beyond one comment per review; changing an answer that waits for review | Not done |
| Assigning curators to courses or students | Not done: every staff role reviews everything |
| Notifying the staff of new homework; reminders to students; a warning before access runs out | Not done |
| Certificates, comments under lessons, video watch tracking | Not done |
| Progress and sales reports | Not done: M11 |

## Decisions

- **A new module**, depending on `course`, `commerce`, `school`, `storage` and `user`. None of them depends on it.
- **How lessons open is set per lesson, with drip and stop**; there is no course-level mode. Everything open at once is the default, a schedule is drip, "only after the homework is accepted" is `stop`, and the two combine. A course taken strictly in order has `stop` on every lesson.
- **A stop lesson holds back what follows until it is completed**, whatever that takes for it: homework accepted, quiz passed, or, with neither, marked by the student.
- **Homework and quiz are lesson blocks** in the `course` module, at most one of each per lesson: authors edit them with the rest of the content, and the JSONB column needed no schema change.
- **Quiz answers leave the server only to the staff.** The student's lesson and the public preview go through `LessonContent.forStudents()`; a result names wrong questions, never right options.
- **One enrollment per student and course**, reused when the course is given again, so progress survives a refund and a new purchase.
- **`OrderPaid` gives the course after the order's transaction is committed**, through the event publication registry. One order gives access once, so a redelivered event changes nothing.
- **Buying again is decided in commerce**, by the order itself (`Order.givesCourse`): commerce can't ask `learning`, which depends on it. V7's unique index on one paid order per buyer and course is dropped for it.
- **A student keeps an unpublished course** and loses a deleted one.
- **Every staff role reviews homework**; owner and admin give and take courses by hand (the permission to manage students).
- **Each handed-in answer is its own row**, never edited after review.
- **Homework files are a new purpose of the storage module**, private, kept for the school.
- **The student is emailed** on a review and on a grant, with `submit`: no secret is in these messages.
- **`school` raises `SchoolStudentRemoved`** so that removing a student takes their courses away without `school` knowing of `learning`.

## Verified by hand

Against the environment from `compose.dev.yaml`, with the local stand-in for the YooKassa API from M6 for payments.

- **Done when:** an author makes lesson 1 a stop lesson with homework, with more lessons after it → a student gets the course by an order → it is in `GET /learning/courses` → lesson 1 is open, lesson 2 is 403 `learning/lesson-locked` naming lesson 1 → the student uploads a file and hands in → a curator sees it in the queue, downloads the file, gives it back with a comment (email; lesson 2 still locked) → the student hands in again → an author accepts (email) → lesson 1 is completed and lesson 2 opens. The history shows both answers.
- **Opening:** sections reordered after they were created are followed; a stop lesson with no work opens the next one once marked; `AFTER_DAYS` is locked with its `opensAt` and opens when the enrollment's start is moved back; `AT_TIME` in the future stays locked; a draft lesson is 404; a locked lesson's links, homework and quiz are 403 too.
- **Homework:** an empty answer → 400; an unconfirmed file and another student's file → 404; a second answer while one waits, and one after acceptance → 409 `learning/homework-not-expected`; a review of a reviewed answer → 409 `learning/already-reviewed`; a blank comment on giving back → 400. The file is not in the school's library. A comment with HTML arrives in the email as text.
- **Quiz:** content rules (two homeworks, no questions, one option, no right option, two right in a single-choice, pass percent 0) → 400; ids of questions and options are given and kept on resave. A failed attempt leaves the lesson open and uncompleted, a passed one completes it. Neither the student's lesson, an attempt's result nor the public preview holds `correct`.
- **Access:** before buying everything is 404. A grant by email makes the account, the student row and the email; a second grant extends the same enrollment; a grant with no end removes the end. Revoke → 404 for the student, and the staff list shows it as not active. Days running out → 404. A paid order gives the course with the offer's days; buying again while it lasts → 409 `commerce/already-bought`; a whole refund → order `REFUNDED`, course 404, homework kept; buying again reopens the same enrollment with its history; after the days ran out a new order is allowed. Removing the student from the school and deleting the account revoke.
- **Course states:** an unpublished course stays for its student and leaves the catalog; an unpublished lesson leaves the outline; a deleted course is 404.
- **Roles:** an author and a curator can't grant or revoke (403) but see the students and review; a user outside the staff gets 404 for the queue, an answer, its links and a review; a student can't review their own answer (404).
- **Events:** `OrderPaid` reached the listener for a free order and for a paid one; no event publication was left incomplete.
- Startup: V8 applies, and lessons saved before M8 still read.
- Not verified: an event that fails and is delivered again; two requests at the same moment (two answers, two completions, a grant racing an order); a quiz edited after attempts; the limits of 10 files and 50 questions; a course with hundreds of lessons in "my courses".
