# M4: Storage

> Files in Supabase Storage, uploaded and downloaded with signed links, in the `storage` module of `services/platform`. Its first users are the school logo, the user's own picture and a school's file library. Designed fresh: there is no NestJS source for it. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/storage/README.md).

## Scope

| Part | State |
|---|---|
| `StoredFile` and `FilePurpose`: an upload, its confirmation, limits per purpose, per-school paths | Done |
| `FileStorage` port and the Supabase Storage adapter: signed upload, signed download, public links, buckets | Done |
| Cleanup job: deleted files and uploads that were never confirmed | Done |
| School logo (`/schools/{id}/logo`), deferred from M3 | Done |
| The user's own picture (`/users/me/picture`) | Done |
| School file library (`/schools/{id}/files`): upload, list, signed download, delete | Done |
| The local environment as one `compose.dev.yaml`: Postgres, Supabase Storage, Mailpit. No Supabase CLI | Done |
| Files over 50 MB, resumable uploads, video | Not done: M5, with lesson content |
| Storage quota per plan | Not done: billing (M10) |
| Image resizing; scanning what was uploaded | Not done |
| Refusing to delete a library file a lesson uses | Not done: M5 |
| Removing the files of a deleted school | Not done: a deleted school keeps its row and its data |

## Decisions

- **The adapter speaks the Supabase Storage REST API**, with the service role key. Moving to an S3 store means writing a second adapter of `FileStorage`.
- **A module of `services/platform`**, not `libs/core`: one service stores files so far.
- **`storage` is a leaf module.** It knows schools and users only by id and has no endpoints. `school` and `user` depend on it, own the endpoints and check their own permissions. The other direction would be a module cycle.
- **The bytes never pass through our service.** The client asks for an upload, `PUT`s the file to storage with the signed link, then confirms.
- **A file is trusted only after confirm.** The service reads the object's real size and type from storage, and the aggregate checks them against the purpose. The content itself is not inspected.
- **Two buckets by visibility.** Each carries a size limit and a list of content types in Supabase itself, as a second guard: storage refuses an upload that breaks them. The service creates or updates both at startup, and does not start if it can't.
- **The buckets are `vkurse-public` and `vkurse-private`.** A bucket named `public` breaks the API: storage reads `/object/info/public/…` as another route.
- **Consumers keep the public link next to the file id** (`School.logo` + `logoFileId`, `User.picture` + `pictureFileId`). `picture` was already a link (from Google), and lists of schools and people need no lookup. Moving the storage domain later means one `UPDATE`.
- **Deleting a file marks it (`deleted_at`)** in the caller's transaction. A job removes the object, then the row, so a failed call to storage is retried and never loses a file that is still in use.
- **The upload link's lifetime is set on the storage server**, not per request. It must be 2 hours there, as `StoredFile.UPLOAD_TTL` (hosted Supabase uses that; self-hosted storage defaults to 60 seconds).
- **Limits:** png, jpeg and webp up to 5 MB for a logo and a picture; up to 50 MB from an allowlist for the library. No SVG or HTML. At most 20 unfinished uploads per user, the only flood guard until M15.
- **In the library**, every staff role lists and downloads; owner, admin and author upload and delete.
- **Locally, one Postgres serves both** the services (schema `platform`) and Storage (schema `storage`), as in a real Supabase project. It is the Supabase Postgres image, because Storage needs its roles.
- **A signed link is returned in a JSON body**, never as a redirect.

## Verified by hand

Against the environment from `compose.dev.yaml` (the done-when, the rules, the logo and the picture were run again on its fresh database):

- **Done when:** an author asks for a library upload → `PUT`s 200 KB to the signed link → confirms → the curator's `GET …/files` lists it → `POST …/download` gives a signed link → it returns the same bytes, as an attachment under the file's (Cyrillic) name. The same link without its token, or with a wrong one, is refused, and the private file has no public link.
- Startup: V4 applies; the two buckets are created with their limits, and updated on a later start.
- Confirm: before the `PUT` → 409 `storage/upload-incomplete`; by another member → 404; twice → the same file; after the upload expired → 400 `storage/upload-expired`.
- Lying uploads: another content type than declared → storage takes it, confirm refuses it (400 `storage/file-type-not-allowed`); a type outside the bucket's list → storage refuses the `PUT`; more bytes than declared → confirmed with the real size.
- Limits: a 6 MB logo and a 50 MB + 1 library file → 400 `storage/file-too-large`; SVG, HTML, a PDF as a logo → 400 `storage/file-type-not-allowed`; a blank name, a zero size → 400 `validation-error`; the 21st unfinished upload → 429 `storage/too-many-uploads`.
- Roles: a curator can't upload or delete (403) but lists and downloads; an author can't touch the logo (403); a non-member gets 404; a file reached through another school → 404; no token → 401.
- Logo: set → `logo` is in `GET /schools`, `/schools/{id}` and the public `/schools/by-slug/{slug}`, and the link opens without a token; a second logo replaces it; removing clears it. A library file can't become a logo, nor a logo a picture (404).
- Picture: set → `picture` in `/users/me` and in the school's member list; another user can't set my upload (404); removing clears it. Deleting an account marks its picture deleted.
- Cleanup job: replaced and removed logos and pictures, a deleted library file and uploads whose `upload_expires_at` was moved into the past (some with an object, some without) were all removed, object and row. Until then a deleted file's old link still worked.
- Storage stopped: asking for an upload or a download link is a 500 and leaves no row; lists still work; the service does not start.
- Not verified: an upload that expires by itself after 2 hours, a download link that expires after an hour, a file near 50 MB, the full Supabase stack behind its gateway (`/storage/v1`, the `apikey` header), and two requests confirming or replacing the same file at once.
