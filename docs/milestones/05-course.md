# M5: Course

> Courses, sections and lessons with block content, publishing, and a public catalog with search, in the `course` module of `services/platform`. Storage gained video and resumable uploads for it. Designed fresh: there is no NestJS source. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/course/README.md).

## Scope

| Part | State |
|---|---|
| `Course`, `Section`, `Lesson`; drafts, publishing, soft delete of a course | Done |
| Lesson content: a list of blocks (text, video, image, file) as one JSONB value of a sealed type | Done |
| Ordering of sections and lessons, moving a lesson between sections | Done |
| Drip rules, stop lessons, free preview: stored | Done. Drip and stop are enforced in M8 |
| Course cover | Done |
| Public catalog, all schools and per school, with full-text search; course page; free preview lessons | Done |
| Storage: video (2 GB), resumable uploads, signed links for a lesson's media in one batch | Done |
| A library file used in a lesson can't be deleted (left open in M4) | Done |
| Students reading lessons; progress; homework and quiz blocks | Not done: M8 |
| Prices and offers | Not done: M7 |
| Plan limits, storage quota | Not done: M10. Until then 100 sections and 500 lessons per course |
| Videos on Rutube, VK Video and YouTube, by a link or the host's embed code (an `embed` block) | Done, after the milestone |
| Video transcoding, adaptive streaming, protection | Not done |
| Categories, tags, reviews; notifying anyone about a publication | Not done |

## Decisions

- **A module of `services/platform`** (ADR-0001), depending on `school` and `storage`. `storage` stays a leaf.
- **Three aggregates.** A lesson is big and edited on its own, so `Section` and `Lesson` are not children of `Course`. Changes to the structure lock the course row instead.
- **Content is a list of blocks in one JSONB column.** It is always read and written whole, and M8 adds block kinds without a schema change. The list is wrapped in a record (`LessonContent`), so the JSON mapper knows the type of what it holds.
- **Text is Markdown, never HTML**, so the server has nothing to sanitise.
- **Video is our own upload**, a plain file played through a signed link (6 hours). Nothing is transcoded, and nothing protects a video beyond the link's lifetime.
- **A big file is uploaded with the tus protocol**, to Supabase Storage's endpoint for signed uploads (`/upload/resumable/sign`), with the same signed token as a plain upload in the `x-signature` header of every request. The plain `/upload/resumable` endpoint wants a user's JWT, which our clients don't have.
- **A video on an outside host is a link we read, never HTML we keep.** The author pastes a link or the host's embed code; `VideoLink` accepts only the forms Rutube, VK Video and YouTube use, and the player's address is built from the id and key it checked. The embed code is accepted because VK's `hash` for a video that isn't public exists only there.
- **Video has its own bucket**, so the size limit storage enforces stays tight for every other file. The buckets follow `FilePurpose.Bucket`.
- **A block names its file by id; links are asked for separately**, signed in one call per bucket. A lesson response never carries links that expire.
- **An author edits every course of the school.** The role alone decides; there is no per-course author list.
- **The catalog shows published courses of live schools**, platform-wide or for one school.
- **Search is Postgres full-text search** (`russian`) on a generated column, plus a match on a piece of the title as typed. The column is not mapped by Hibernate.
- **"File in use" is a veto by event**: `StoredFile.delete` raises `StoredFileDeleted`, and `course` listens in the same transaction and throws. `storage` still knows nothing of courses.
- **A published course always has a published lesson.**
- **Drip and stop are stored now**, so authors can set a course up; nobody is held back until M8.

## Verified by hand

Against the environment from `compose.dev.yaml`:

- **Done when:** an author drafts a course → adds two sections and three lessons → sets a lesson's content (text, an uploaded video, an image, a file) → publishes two lessons and the course → `GET /catalog/courses` without a token lists it, with its school and the number of published lessons.
- Search: words of the title, summary and description in other grammatical forms find the course, as does a piece of the title; an unrelated word doesn't. After a rename the new title is found and the old one isn't.
- Video: a 200 MB upload with the tus protocol, stopped after two pieces and resumed from the offset storage reported; confirming it half-uploaded → 409 `storage/upload-incomplete`; a 6 GB video and an `.avi` are refused. The lesson's video link answers a `Range` request with 206 and is served to be shown; the file link is an attachment.
- Publishing: an empty course and an empty lesson → 409 `course/nothing-to-publish`; unpublishing, deleting or emptying the last published lesson of a published course → the same 409.
- Content: another school's file → 404; a file in a block of the wrong kind, a block without its text or file, two blocks with one id → 400; an unknown block type and 101 blocks → 400 `validation-error`. Blocks sent with their ids keep them.
- Files: deleting a library file or a video a lesson shows → 409 `course/file-in-use`, and the file stays; once the course is deleted the file can be deleted.
- Ordering: sections reordered; a lesson moved to another section; a list that leaves out a sibling, names one twice or names a foreign id → 400. The public page follows the new order and leaves out a section with no published lesson.
- Roles: a curator reads courses, lessons and links but can't change anything (403); an author can't delete a course (403); a non-member gets 404 everywhere; the staff endpoints need a token.
- Public: a free preview lesson and its links work without a token; a lesson that is not a preview, or not published, is 404.
- Catalog: a draft, an unpublished, a deleted course and a course of a deleted school are not listed, and their pages and preview lessons are 404. A deleted course frees its slug, and another school may use the same slug.
- Cover: set (it shows in the catalog), replaced (the old file is marked deleted), removed.
- Startup: V5 applies, Hibernate accepts the unmapped `search` column, and the video bucket is created.
- Not verified: a video near 2 GB, an upload that outlives its 2-hour token, the limits of 100 sections and 500 lessons, a link after it expires, a real video played in a browser, and two editors changing one course at once.
