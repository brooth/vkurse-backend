# User module

> Users and their own profile.

| Layer | File | Description |
|---|---|---|
| `domain` | `User` | The aggregate: `register`, `changeProfile`, `delete`; platform `Role` |
| | `Email` | Value object: trimmed, lowercased, format-checked |
| | `UserRegistered`, `UserDeleted` | Events, published in the transaction that saved the user |
| | `UserRepository` | `findById`, `findByEmail`, `save`. Deleted users are never returned |
| `application` | `UserService` | `getMe`, `updateMe`, `deleteMe`, `findById`, `findOrRegister` |
| `infrastructure` | `JpaUserRepository` | |
| `web` | `UserController`, `UserResponse`, `UpdateProfileRequest` | |

## Rules

- A user is created only by `User.register`, at their first sign-in. Without a name they are named after the email's local part; a name longer than 100 characters is cut.
- The email is set at sign-up and never changes. It is unique among live users (`users_email_key`, a partial index).
- A user changes only `name`, `locale` and `timezone`. The role is not changed through the API.
- Deleting an account sets `deleted_at` and raises `UserDeleted`; `auth` listens in the same transaction and revokes the sessions and removes the linked accounts. The email is then free for a new account.
- `findOrRegister` signs a new user up in its own transaction: when two sign-ins race on a new email, the unique index rejects one, which then finds the other's user.

## Endpoints

| Endpoint | Body | Returns |
|---|---|---|
| `GET /users/me` | | `UserResponse` |
| `PATCH /users/me` | `{ name?, locale?, timezone? }` (`null` = unchanged) | `UserResponse` |
| `DELETE /users/me` | | `204` |

`UserResponse`: `id`, `createdAt`, `updatedAt`, `role`, `name`, `email`, `picture`, `locale`, `timezone`.

## Table

`users`: `role`, `name`, `email`, `picture`, `locale` (default `ru`), `timezone` (default `Europe/Moscow`), `deleted_at`.
