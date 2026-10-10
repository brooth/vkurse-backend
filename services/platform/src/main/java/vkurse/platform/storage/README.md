# Storage module

> Files: who uploaded what, what it is for and where it is kept. The bytes live in Supabase Storage and never pass through this service: a client uploads and downloads with links this module signs.

| Layer | File | Description |
|---|---|---|
| `domain` | `StoredFile` | The aggregate: `request` (an upload), `confirm`, `delete` |
| | `FilePurpose` | What a file is for: who may read it, its size limit, its content types, its path |
| | `StoredObject`, `Upload`, `ResumableUpload`, `Download` | What storage really holds; an upload with its link, or with what a resumable upload needs; a link to read a file with |
| | `StoredFileDeleted` | Event: a file was deleted. A module that still uses the file refuses by throwing |
| | `FileStorage` | Port: the storage server (`signUpload`, `signResumableUpload`, `signDownload`, `signLinks`, `publicUrl`, `stat`, `delete`) |
| | `StorageErrors` | The module's client errors |
| | `StoredFileRepository` | |
| `application` | `StoredFileService` | `requestUpload`, `confirm`, `get`, `getAll`, `findBySchool`, `download`, `links`, `delete` |
| | `StorageCleanupJob` | Removes deleted files and expired uploads from storage, every 10 minutes |
| `infrastructure` | `SupabaseFileStorage` | The port over the Supabase Storage REST API; creates the buckets at startup |
| | `JpaStoredFileRepository` | |

Root class: `StorageProperties` (`vkurse.storage.*`). There is no `web` layer: the module that owns a file has the endpoints.

## Using it from another module

The module knows nothing of schools or users beyond their ids. **Who may upload, see or delete a file is your module's check**, made before you call `StoredFileService`; it checks only what the file is.

```java
// 1. The client asks for an upload. Check the permission, then:
var upload = files.requestUpload(FilePurpose.SCHOOL_LOGO, schoolId, userId, name, contentType, size);
//    Return upload.url() and upload.file().getId(). The client PUTs the raw file to the link, with its content type.

// 2. The client says it is done. Outside a transaction (this calls storage):
var file = files.confirm(fileId, userId, FilePurpose.SCHOOL_LOGO, schoolId);   // 404 unless it is this user's upload
var url = files.download(file).url();   // the lasting link of a public file; a signed link of a private one

// 3. In your transaction: keep the file id (and the link, if the file is public), and delete what it replaces.
files.delete(replacedFileId);
```

`requestUpload`, `confirm`, `download` and `links` call the storage server, so call them outside a database transaction. `delete` joins yours.

`links(files)` signs links to show several private files in a page, in one call per bucket. To keep a file your module uses from being deleted, listen for `StoredFileDeleted` with a plain `@EventListener` and throw.

A new kind of file is a new `FilePurpose` constant: whether it belongs to a school, its bucket, its folder, size limit and content types. The bucket limits follow at the next startup.

## The gate

Before it signs an upload for a school, or a link to a school's private file, the service asks the `SchoolStorageGate` port (`storage/domain`). The [billing module](../billing/README.md) implements it and refuses a school that owes Vkurse: 402 `billing/payment-required` for uploads, 402 `billing/suspended` for links after the grace period. Public files and a user's own files are never asked about. `usageBySchool()` and `usageOf(schoolId)` say how many bytes a school stores: its confirmed files that aren't deleted.

## Rules

- **A file is used only after it is confirmed.** An upload starts `PENDING`. `confirm` reads the object's real size and content type from storage, and the file becomes `READY` only if the type is the declared one and the size fits the purpose. The stored `size` is then the real one. The content itself is not inspected.
- Only the user who started an upload may confirm it, and only for the purpose and school it was started for. Anything else is 404.
- The path is built from ids, never from client text: `schools/{schoolId}/{logo|files}/{fileId}.{ext}`, `users/{userId}/picture/{fileId}.{ext}`. The extension comes from the content type. The client's file name is kept as `name` (1 to 255 characters, no control characters) and offered as the name of a download.
- An upload must be confirmed within 2 hours (`StoredFile.UPLOAD_TTL`), or 400 `storage/upload-expired`. A user has at most 20 unfinished uploads: 429 `storage/too-many-uploads`.
- **A video is uploaded in pieces that can resume**, with the tus protocol. The upload then has `resumable` instead of a link: the client sends every request to its `endpoint` with `signature` in the `x-signature` header, and names `bucketName`, `objectName` and `contentType` in the upload's metadata. After a break it asks the server for the offset and goes on from there. The token works for 2 hours, like a plain upload link.
- A signed download link works for 1 hour and makes the browser save the file under its name. A link from `links` shows an image or a video in the page instead, and a video's works for 6 hours. Links are returned in a response body, never as a redirect.
- Deleting a file sets `deleted_at`: nothing finds it any more. `StorageCleanupJob` then removes the object from storage and the row; the same job removes uploads that expired unconfirmed. Until it runs (10 minutes at most) an old link still works.

| Purpose | Belongs to | Read by | Size | Content types |
|---|---|---|---|---|
| `USER_PICTURE` | the user | anyone, by a lasting link | 5 MB | png, jpeg, webp |
| `SCHOOL_LOGO` | a school | anyone, by a lasting link | 5 MB | png, jpeg, webp |
| `COURSE_COVER` | a school | anyone, by a lasting link | 5 MB | png, jpeg, webp |
| `SCHOOL_FILE` | a school | a signed link | 50 MB | png, jpeg, webp, pdf, txt, zip, docx, xlsx, pptx, mp3 |
| `HOMEWORK_FILE` | a school (uploaded by a student) | a signed link | 50 MB | as `SCHOOL_FILE` |
| `SCHOOL_VIDEO` | a school | a signed link | 2 GB | mp4, webm, mov |

There is no SVG or HTML: a browser would run the scripts in them.

| Error | When |
|---|---|
| 400 `storage/file-too-large` | The file is empty or bigger than its purpose allows |
| 400 `storage/file-type-not-allowed` | The content type is not one the purpose takes, or the uploaded file has another type than declared |
| 409 `storage/upload-incomplete` | Confirming before the file was sent to storage |
| 400 `storage/upload-expired` | Confirming too late |
| 429 `storage/too-many-uploads` | 20 unfinished uploads |

## Supabase Storage

- Three buckets, created or updated at startup: `vkurse.storage.public-bucket` (public), `private-bucket` and `video-bucket`. Each gets the largest size and all content types of the purposes it holds, so storage itself refuses an upload that breaks them. **If storage can't be reached at startup, the service does not start.**
- A bucket must not be named after a word in the API's paths (`public`, `sign`, `info`, `upload`, `authenticated`): storage then misreads the path.
- The service uses the service role key, which can do anything in storage. No client ever gets it.
- The storage server signs the upload links and sets how long they work. Set its `UPLOAD_SIGNED_URL_EXPIRATION_TIME` to 7200 seconds, to match `StoredFile.UPLOAD_TTL` (self-hosted storage defaults to 60 seconds).
- Storage answers every error with HTTP 400 and puts what happened in the body's `code` (`NoSuchKey`, `BucketAlreadyExists`).
- A plain upload takes at most 50 MB here. Video goes through the resumable endpoint for signed uploads, `/upload/resumable/sign`; the plain `/upload/resumable` wants a user's JWT.
- The storage server's own `FILE_SIZE_LIMIT` must be at least the largest purpose (2 GB).

## Table

`stored_files`: `purpose`, `school_id` (null for a user's own file), `uploaded_by`, `name`, `content_type`, `size`, `path`, `status`, `upload_expires_at`, `deleted_at`.
