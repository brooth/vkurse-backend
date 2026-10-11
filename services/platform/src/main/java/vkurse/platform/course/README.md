# Course module

> Courses: what a school teaches. A course has sections, a section has lessons, and a lesson's content is a list of blocks. The staff build and publish courses; anyone finds published ones in the catalog.

| Layer | File | Description |
|---|---|---|
| `domain` | `Course` | The aggregate: `draft`, `changeDetails`, `changeCover`, `publish`, `unpublish`, `delete` |
| | `CourseDrafted` | Event: a course was made. The [chat module](../chat/README.md) opens the course's chat on it |
| | `CourseSlug` | Value object: the course's address inside its school |
| | `Section` | A titled group of lessons at a position in the course |
| | `Lesson` | Title, content, status, and who sees it when (`preview`, `drip`, `stop`) |
| | `LessonContent`, `LessonBlock` | The content: a list of blocks of a sealed type, stored as one JSONB value |
| | `Drip` | Value object: when a lesson opens for a student |
| | `Arrangement` | The rule of reordering |
| | `CourseCard`, `Outline` | Read records: a published course with its school; a course with its sections and lessons |
| | `CourseErrors` | The module's client errors |
| | `*Repository` | One interface per aggregate |
| `application` | `CourseService` | `draft`, `get`, `list`, `update`, `publish`, `unpublish`, `delete` |
| | `CourseCoverService` | `requestUpload`, `change`, `remove` |
| | `CourseStructureService` | Sections, lessons and their order; `outline` |
| | `LessonService` | `get`, `update`, `changeContent`, `publish`, `unpublish`, `links` |
| | `CatalogService` | Public: `search`, `course`, `previewLesson`, `previewLinks` |
| | `CourseReader` | Courses as their students get them, for the modules that show a course to someone who has it |
| | `CourseAccess` | Who may reach a course: the staff of its school |
| | `FileInUseListener` | Refuses the deletion of a stored file a lesson shows |
| `infrastructure` | `Jpa*Repository` | The catalog search and "file in use" are native SQL |
| `web` | `CourseController`, `CourseCoverController`, `SectionController`, `LessonController`, `CatalogController` and their records | |

It depends on [`school`](../school/README.md) (who is on the staff, with which role) and [`storage`](../storage/README.md) (covers, and the files and videos lessons show).

## Rules

**Courses**
- A course belongs to one school. It starts as a draft, which only the staff see.
- The title has 1 to 150 characters, the summary at most 300, the description (Markdown) at most 10 000. The slug is 3 to 80 lowercase latin letters, digits and single hyphens, unique among the school's live courses: 409 `course/slug-taken`.
- **Publishing puts the course into the catalog.** It needs at least one published lesson, and a published course keeps at least one: unpublishing, deleting or emptying its last published lesson is 409 `course/nothing-to-publish`. Unpublish the course first.
- Deleting a course sets `deleted_at`: nothing finds it any more and its slug is free. Its sections and lessons keep their rows.
- A course has no price: offers arrive with commerce (M7).

**Sections and lessons**
- A new section goes to the end of the course, a new lesson to the end of its section. A course has at most 100 sections and 500 lessons (429).
- Reordering sends the full list of ids in the new order. It must name everything that is there now, each once. A lesson of another section of the same course named in a section's list moves into that section.
- A section is removed only when it has no lessons: 409 `course/section-not-empty`. Removed sections and lessons are removed rows.
- Changes to a course's structure and to what is published in it lock the course row, so two of them never interleave.

**Lesson content**
- A lesson is a list of at most 100 blocks:

| Block | Holds |
|---|---|
| `text` | `text`: Markdown, 1 to 50 000 characters. Never HTML |
| `video` | `fileId`: a video of the school's library |
| `image` | `fileId`: an image of the school's library |
| `file` | `fileId`: any other file of the school's library, to download |
| `homework` | `task`: Markdown, 1 to 50 000 characters. Students answer it and the staff review the answers |
| `quiz` | `passPercent` (1 to 100) and `questions[ { text, kind, options[ { text, correct } ] } ]`. Students are graded at once |

- Setting the content replaces all of it. Every block has an `id`: a block sent with its id keeps it, one sent without gets a new one.
- A lesson has at most one homework and one quiz. A quiz has 1 to 50 questions, a question 2 to 10 options. A `SINGLE` question has exactly one right option, a `MULTIPLE` one at least one. Questions and options get ids and keep them, like blocks.
- **Which options are right is shown only to the staff.** The public preview and a student's lesson leave `correct` out (`LessonContent.forStudents()`).
- A block's file must be a confirmed file of the same school's library (else 404), and of the kind the block is for (else 400).
- **A file a lesson shows can't be deleted from the library**: 409 `course/file-in-use`. Lessons of a deleted course don't count.
- Links are refused with 402 `billing/suspended` while the school has owed Vkurse for storage for more than 14 days ([billing](../billing/README.md)).
- A lesson is published on its own, and needs content for it. Only published lessons appear outside the staff.
- Blocks name files by id. The links are asked for separately (`…/links`), signed for a short time: a video for 6 hours, anything else for an hour. A video and an image are served to be shown, a file to be saved.

**Who sees a lesson when** (for students it is enforced by the [learning module](../learning/README.md#rules))
- `preview`: a free preview. Anyone may read it once it and its course are published.
- `drip`: when the lesson opens for a student: `IMMEDIATELY`, `AFTER_DAYS` (`days` after they got the course, 1 to 3650), or `AT_TIME` (`at`, for everyone).
- `stop`: a stop lesson. The lessons after it stay closed until the student completes it: its homework is accepted and its quiz passed, or, with neither, the student marks it. A course taken strictly in order has `stop` on every lesson.
- With the defaults (`IMMEDIATELY`, no stop) every lesson is open at once.

**Staff** (roles are in the [school module](../school/README.md#rules))

| | OWNER | ADMIN | AUTHOR | CURATOR |
|---|---|---|---|---|
| See courses, lessons and their files | ✓ | ✓ | ✓ | ✓ |
| Create and edit courses, sections and lessons; publish | ✓ | ✓ | ✓ | |
| Delete a course | ✓ | ✓ | | |

An author edits every course of the school. A course of a school the caller doesn't work in is 404.

**Catalog**
- Public, no token. It shows published courses of live schools: a course of a deleted school is gone from it.
- `$search` finds a course by the words of its title, summary and description in any grammatical form (Postgres full-text search, `russian`), or by a piece of its title as typed. The best match comes first; without `$search`, the latest published.
- A course's public page shows its sections with the titles of their published lessons. Only free preview lessons can be read.
- Unpublishing a course takes it out of the catalog. Students who have it keep it.

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `POST /schools/{schoolId}/courses` | `{ title, slug, summary?, description? }` | editors | `CourseResponse` |
| `GET /schools/{schoolId}/courses` | | staff | `CourseResponse[]`, drafts too |
| `GET /courses/{id}` | | staff | `CourseResponse` |
| `GET /courses/{id}/outline` | | staff | `{ course, sections[ { id, title, position, lessons[] } ] }` |
| `PATCH /courses/{id}` | `{ title?, slug?, summary?, description? }` (`null` = unchanged, `""` removes) | editors | `CourseResponse` |
| `POST /courses/{id}/publish`, `/unpublish` | | editors | `CourseResponse` |
| `DELETE /courses/{id}` | | owner, admin | `204` |
| `POST /courses/{id}/cover/upload` | `{ name, contentType, size }` | editors | `{ fileId, uploadUrl, expiresAt }` |
| `POST /courses/{id}/cover` | `{ fileId }` | editors | `CourseResponse` |
| `DELETE /courses/{id}/cover` | | editors | `204` |
| `POST /courses/{id}/sections` | `{ title }` | editors | `SectionResponse` |
| `POST /courses/{id}/sections/order` | `{ ids }` | editors | `SectionResponse[]` |
| `PATCH /sections/{id}` | `{ title }` | editors | `SectionResponse` |
| `DELETE /sections/{id}` | | editors | `204` |
| `POST /sections/{id}/lessons` | `{ title }` | editors | `LessonResponse` |
| `POST /sections/{id}/lessons/order` | `{ ids }` | editors | lesson summaries |
| `GET /lessons/{id}` | | staff | `LessonResponse` |
| `PATCH /lessons/{id}` | `{ title?, preview?, drip?: { kind, days?, at? }, stop? }` | editors | `LessonResponse` |
| `POST /lessons/{id}/content` | `{ blocks }` | editors | `LessonResponse` |
| `POST /lessons/{id}/publish`, `/unpublish` | | editors | `LessonResponse` |
| `POST /lessons/{id}/links` | | staff | `{ blockId: { url, expiresAt } }` |
| `DELETE /lessons/{id}` | | editors | `204` |
| `GET /catalog/courses` | `$search`, `$limit`, `$skip`, `school` (a school's slug) | public | `CatalogCourseResponse[]` |
| `GET /catalog/schools/{schoolSlug}/courses/{courseSlug}` | | public | `{ course, description, sections[ { id, title, lessons[ { id, title, preview } ] } ] }` |
| `GET /catalog/lessons/{id}` | | public | `{ id, courseId, title, blocks }`: free preview lessons only |
| `POST /catalog/lessons/{id}/links` | | public | `{ blockId: { url, expiresAt } }` |

"Editors" are owner, admin and author.

- `CourseResponse`: `id`, `createdAt`, `updatedAt`, `schoolId`, `title`, `slug`, `summary`, `description`, `cover`, `status` (`DRAFT`, `PUBLISHED`), `publishedAt`.
- `LessonResponse`: `id`, `createdAt`, `updatedAt`, `courseId`, `sectionId`, `title`, `position`, `status`, `preview`, `drip`, `stop`, `blocks`. A lesson summary is the same without the dates, `courseId` and `blocks`.
- `CatalogCourseResponse`: `id`, `title`, `slug`, `summary`, `cover`, `publishedAt`, `lessons` (how many are published), `school { id, name, slug, logo }`.
- The cover is uploaded like a school's logo: ask for a link, `PUT` the image, set it. Videos and files are uploaded to the [school's library](../school/README.md).

## Tables

- `courses`: `school_id`, `title`, `slug`, `summary`, `description`, `cover`, `cover_file_id`, `status`, `published_at`, `deleted_at`, and `search` (a `tsvector` the database keeps; Hibernate doesn't map it).
- `course_sections`: `course_id`, `title`, `position`.
- `course_lessons`: `course_id`, `section_id`, `title`, `position`, `content` (JSONB), `status`, `preview`, `drip_kind`, `drip_days`, `drip_at`, `stop`.
