# User module

> Users and their own profile.

| Layer | File | Description |
|---|---|---|
| `domain` | `User` | The aggregate: `register`, `changeProfile`, `changePicture`, `removePicture`, `delete`; platform `Role` |
| | `Email` | Value object: trimmed, lowercased, format-checked |
| | `UserRegistered`, `UserDeleted` | Events, published in the transaction that saved the user |
| | `UserRepository` | `findById`, `findByEmail`, `save`. Deleted users are never returned |
| `application` | `UserService` | `getMe`, `updateMe`, `deleteMe`, `findById`, `findByEmail`, `findOrRegister` |
| | `UserPictureService` | `requestUpload`, `change`, `remove`: the user's own picture, kept by the [storage module](../storage/README.md) |
| | `WelcomeListener` | Submits the `welcome` email when a user registers |
| `infrastructure` | `JpaUserRepository` | |
| `web` | `UserController`, `UserPictureController`, `UserResponse`, `UpdateProfileRequest` | |

## Rules

- A user is created only by `User.register`, at their first sign-in. Without a name they are named after the email's local part; a name longer than 100 characters is cut.
- The email is set at sign-up and never changes. It is unique among live users (`users_email_key`, a partial index).
- A new user gets a welcome email in their locale. `WelcomeListener` runs after the sign-up is committed and submits it to the notification job, so a mail failure never fails a sign-up.
- A user changes only `name`, `locale`, `timezone` and the picture. The role is not changed through the API.
- `picture` is a link: the one a sign-in provider gave at sign-up, or the public link of an image the user uploaded (png, jpeg or webp, at most 5 MB). An upload takes three steps: ask for an upload link, `PUT` the image to it, set it as the picture. Setting a picture deletes the uploaded one it replaces; removing works for a provider's picture too.
- Deleting an account sets `deleted_at` and raises `UserDeleted`; `auth` listens in the same transaction and revokes the sessions and removes the linked accounts. The email is then free for a new account. The uploaded picture is deleted from storage. `school` listens too: it removes the user from every school, and refuses the deletion (409 `school/owner-cannot-leave`) while the user owns one.
- `findOrRegister` signs a new user up in its own transaction: when two sign-ins race on a new email, the unique index rejects one, which then finds the other's user.

## Endpoints

| Endpoint | Body | Returns |
|---|---|---|
| `GET /users/me` | | `UserResponse` |
| `PATCH /users/me` | `{ name?, locale?, timezone? }` (`null` = unchanged) | `UserResponse` |
| `DELETE /users/me` | | `204` |
| `POST /users/me/picture/upload` | `{ name, contentType, size }` | `{ fileId, uploadUrl, expiresAt }`: `PUT` the raw image to `uploadUrl` with that content type |
| `POST /users/me/picture` | `{ fileId }` | `UserResponse` |
| `DELETE /users/me/picture` | | `204` |

`UserResponse`: `id`, `createdAt`, `updatedAt`, `role`, `name`, `email`, `picture`, `locale`, `timezone`.

## Table

`users`: `role`, `name`, `email`, `picture`, `picture_file_id`, `locale` (default `ru`), `timezone` (default `Europe/Moscow`), `deleted_at`.
