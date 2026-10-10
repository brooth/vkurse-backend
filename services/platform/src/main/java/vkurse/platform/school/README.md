# School module

> Schools, their staff and students: who works in a school and in which role, invitations to join it, who studies there, and the school's logo and file library.

| Layer | File | Description |
|---|---|---|
| `domain` | `School` | The aggregate: `open`, `changeDetails`, `changeLogo`, `removeLogo`, `delete` |
| | `Slug` | Value object: the school's public address |
| | `SchoolMember`, `SchoolRole` | A user on the staff with a role; the role holds the permission matrix, the member enforces it (`ensureCan…`, `changeRole`, `handOverTo`) |
| | `SchoolInvitation` | A pending invitation: `issue`, `verify`, `accept` (returns the new `SchoolMember`) |
| | `SchoolStudent` | A user who studies in the school |
| | `Membership`, `MemberProfile`, `StudentProfile` | Read pairs: a school with the caller's member; a member or a student with their `User` |
| | `InvitationTokens` | Port: makes a link token and its hash |
| | `SchoolErrors` | The module's client errors |
| | `*Repository` | One interface per aggregate |
| `application` | `SchoolService` | `open`, `get`, `getBySlug`, `findMine`, `findStudying`, `update`, `delete` |
| | `SchoolMemberService` | `require` (the role check for every module), `list`, `changeRole`, `remove`, `transferOwnership` |
| | `SchoolInvitationService` | `invite`, `list`, `revoke`, `view`, `accept`, `decline` |
| | `SchoolStudentService` | `enroll` (for other modules), `add`, `list`, `remove` |
| | `SchoolLogoService` | `requestUpload`, `change`, `remove` |
| | `SchoolFileService` | The file library: `requestUpload`, `confirm`, `list`, `download`, `delete` |
| | `SchoolCleanupJob` | Removes expired invitations, hourly |
| | `SchoolUserDeletedListener` | A deleted account leaves every school; an owner's account can't be deleted |
| `infrastructure` | `Jpa*Repository`, `Sha256InvitationTokens` | |
| `web` | `SchoolController`, `SchoolMemberController`, `SchoolInvitationController`, `InvitationController`, `SchoolStudentController`, `SchoolLogoController`, `SchoolFileController` and their records | |

## Using it from another module

```java
var member = members.require(schoolId, session.userId());   // SchoolMemberService; 404 if not on the staff
member.ensureCanEditSchool();                               // 403 if the role doesn't allow it
```

Add a method to `SchoolRole` and an `ensureCan…` to `SchoolMember` when a module needs a new permission. `SchoolStudentService.enroll(schoolId, userId)` makes a user a student (it does nothing if they are one).

## Rules

**Schools**
- Any user may open a school and becomes its owner. The name has 1 to 100 characters, the description at most 2000.
- The slug is 3 to 40 lowercase latin letters, digits and single hyphens, not a reserved word, and unique among live schools (`schools_slug_key`). A taken slug is 409 `school/slug-taken`.
- Deleting a school sets `deleted_at`: nothing finds it any more and its slug is free. Its members and students keep their rows.
- A school is visible to its staff only. Everyone else, students too, gets 404 under `/schools/{id}`, as for a missing school. Anyone may read the public view by slug.

**Staff and roles**

| | OWNER | ADMIN | AUTHOR | CURATOR |
|---|---|---|---|---|
| See the school, its staff and students | ✓ | ✓ | ✓ | ✓ |
| Edit name, slug, description | ✓ | ✓ | | |
| Invite, change a role, remove a member | anyone but an owner | authors and curators | | |
| See and revoke invitations | ✓ | ✓ | | |
| Add and remove students | ✓ | ✓ | | |
| Change and remove the logo | ✓ | ✓ | | |
| List and download the files of the library | ✓ | ✓ | ✓ | ✓ |
| Upload files to the library, delete them | ✓ | ✓ | ✓ | |
| See courses and lessons | ✓ | ✓ | ✓ | ✓ |
| Create, edit and publish courses | ✓ | ✓ | ✓ | |
| Delete a course | ✓ | ✓ | | |
| See merchants and payments | ✓ | ✓ | | |
| Connect and change merchants, refund payments | ✓ | | | |
| Delete the school, transfer it | ✓ | | | |

- A school has exactly one owner (`school_members_owner_key`). The role is never invited or assigned: it moves only by a transfer, which makes the old owner an admin.
- Nobody changes their own role. Any member may leave (remove themselves), except the owner: 409 `school/owner-cannot-leave`.
- A user who owns a live school can't delete their account (the same 409). Deleting any other account removes its memberships and student rows.
- Roles are read from the database on every request; they are not in the access token.

**Invitations**
- An invitation goes to an email as a link, `{VKURSE_WEB_URL}/invitations/{id}#{token}`. Whoever is signed in and holds the link may accept, under any email. The API takes the token only in a request body.
- Only a SHA-256 of the token is stored, and no response ever contains it. The email is sent with `NotificationService.send` after the invitation is committed; if sending fails, the invitation is removed and the request fails.
- An invitation lives 7 days. A school has one per email (inviting again replaces it, and the old link stops working) and at most 50 live ones: 429 `school/too-many-invitations`.
- A wrong token is 404, like a missing invitation; an expired one is 400 `school/invitation-expired`. A user who is on the staff already can't be invited or accept: 409 `school/already-member`.
- A row exists only while the invitation is pending: accepting, declining and revoking remove it, and the hourly job removes the expired.

**Students**
- A user is a student of a school at most once. Being on the staff is separate, and a user may be both.
- The staff add a student by email; someone without an account gets one, as at a first sign-in (with the welcome email). Adding twice changes nothing.

**Logo and file library** (the files are kept by the [storage module](../storage/README.md), which has the limits)
- An upload takes three steps: ask for an upload link, `PUT` the raw file to it with the declared content type, then confirm. Only a confirmed file is used.
- The logo is a public image (png, jpeg or webp, at most 5 MB): `logo` in the responses is its lasting link. Setting a logo deletes the one it replaces.
- The library holds private files (at most 50 MB) and videos (mp4, webm or mov, at most 2 GB) the staff use in lessons. A file is downloaded with a signed link that works for an hour. Only the uploader confirms an upload. A file of another school is 404.
- A video is uploaded in pieces that can resume: its `UploadResponse` has `resumable` instead of `uploadUrl` (see the [storage module](../storage/README.md#rules)).
- A file that a lesson shows can't be deleted: 409 `course/file-in-use`.
- Deleting a school keeps its logo and files.

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `POST /schools` | `{ name, slug, description? }` | any user | `SchoolResponse` |
| `GET /schools` | | any user | `SchoolResponse[]`: the schools I work in |
| `GET /schools/studying` | | any user | `SchoolPublicResponse[]`: the schools I study in |
| `GET /schools/by-slug/{slug}` | | public | `SchoolPublicResponse` |
| `GET /schools/{id}` | | staff | `SchoolResponse` |
| `PATCH /schools/{id}` | `{ name?, slug?, description? }` (`null` = unchanged, `""` removes the description) | owner, admin | `SchoolResponse` |
| `DELETE /schools/{id}` | | owner | `204` |
| `GET /schools/{id}/members` | | staff | `MemberResponse[]` |
| `PATCH /schools/{id}/members/{memberId}` | `{ role }` | see the table | `MemberResponse` |
| `DELETE /schools/{id}/members/{memberId}` | | see the table, or oneself | `204` |
| `POST /schools/{id}/owner` | `{ memberId }` | owner | `MemberResponse`: the new owner |
| `GET /schools/{id}/invitations` | | owner, admin | `InvitationResponse[]` |
| `POST /schools/{id}/invitations` | `{ email, role }` | see the table | `InvitationResponse` |
| `DELETE /schools/{id}/invitations/{invitationId}` | | owner, admin | `204` |
| `POST /invitations/{id}/view` | `{ token }` | any user | `{ school, role, inviterName?, expiresAt }` |
| `POST /invitations/{id}/accept` | `{ token }` | any user | `SchoolResponse` |
| `POST /invitations/{id}/decline` | `{ token }` | any user | `204` |
| `GET /schools/{id}/students` | | staff | `StudentResponse[]`; `$search` looks in names and emails |
| `POST /schools/{id}/students` | `{ email }` | owner, admin | `StudentResponse` |
| `DELETE /schools/{id}/students/{studentId}` | | owner, admin | `204` |
| `POST /schools/{id}/logo/upload` | `{ name, contentType, size }` | owner, admin | `UploadResponse` |
| `POST /schools/{id}/logo` | `{ fileId }` | owner, admin | `SchoolResponse` |
| `DELETE /schools/{id}/logo` | | owner, admin | `204` |
| `POST /schools/{id}/files` | `{ name, contentType, size }` | owner, admin, author | `UploadResponse` |
| `POST /schools/{id}/files/{fileId}/confirm` | | the uploader | `FileResponse` |
| `GET /schools/{id}/files` | | staff | `FileResponse[]`: confirmed files, newest first; `$search` looks in names |
| `POST /schools/{id}/files/{fileId}/download` | | staff | `{ url, expiresAt }` |
| `DELETE /schools/{id}/files/{fileId}` | | owner, admin, author | `204` |

Lists take `$limit` and `$skip`.

- `SchoolResponse`: `id`, `createdAt`, `updatedAt`, `name`, `slug`, `description`, `logo`, and the caller's own `memberId` and `role`.
- `SchoolPublicResponse`: `id`, `name`, `slug`, `description`, `logo`.
- `UploadResponse`: `file` (a `FileResponse`), `expiresAt`, and either `uploadUrl` or, for a video, `resumable { endpoint, signature, bucketName, objectName }`.
- `FileResponse`: `id`, `createdAt`, `purpose` (`SCHOOL_FILE`, `SCHOOL_VIDEO`), `name`, `contentType`, `size` (bytes), `status` (`PENDING`, `READY`), `uploadedBy`.
- `MemberResponse`: `id`, `createdAt`, `role`, `user { id, name, email, picture }`. `StudentResponse` is the same without `role`.
- `InvitationResponse`: `id`, `createdAt`, `email`, `role`, `invitedBy`, `expiresAt`.

## Tables

- `schools`: `name`, `slug`, `description`, `logo`, `logo_file_id`, `deleted_at`.
- The library's files are rows of `stored_files`, the storage module's table.
- `school_members`: `school_id`, `user_id`, `role`.
- `school_invitations`: `school_id`, `email`, `role`, `invited_by`, `token_hash`, `expires_at`.
- `school_students`: `school_id`, `user_id`, `added_by`.
