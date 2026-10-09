# M3: School

> Schools, their staff (owner, admins, authors, curators), invitations and students, in the `school` module of `services/platform`. Designed fresh: there is no NestJS source for it. How it works is in the [module README](../../services/platform/src/main/java/space/vkurse/platform/school/README.md).

## Scope

| Part | State |
|---|---|
| `School` with a unique public slug; open, edit, delete | Done |
| `SchoolMember` with a role (OWNER, ADMIN, AUTHOR, CURATOR), the permission matrix, role change, removal, leaving | Done |
| Ownership transfer | Done |
| Invitations by email with a secret link; view, accept, decline, revoke; hourly cleanup of expired ones | Done |
| `SchoolStudent`: staff list with search, add by email, remove; the student's own list; `enroll` for M7/M8 | Done |
| `SchoolMemberService.require(schoolId, userId)`: the role check later modules use | Done |
| School logo | Not done: needs storage (M4) |
| Plan limits on schools and members | Not done: billing (M10) |
| IP rate limiting on invitations | Not done: M14. Until then a school has at most 50 pending invitations |
| Admin endpoints | Not done: M11 |
| Notifying staff when someone joins or is removed | Not done |

## Decisions

- **In `services/platform`**, as a module. ADR-0001 keeps platform a modular monolith, and the module needs `user`.
- **An invitation is accepted with a secret link.** Whoever is signed in and holds the link may accept, under any email: the address only says where the link was sent.
- **The link is `{VKURSE_WEB_URL}/invitations/{id}#{token}`.** The token is in the fragment, so it reaches neither server logs nor referrers; the web app posts it in a JSON body.
- **Only a SHA-256 of the token is stored**, through the module's own port (`InvitationTokens`). The token has 256 bits of entropy, so it needs no key and no attempt limit, and `school` does not depend on `auth` for a hasher.
- **The invitation email is sent with `send`, never `submit`**, because it carries a secret. It goes out after the invitation is committed; if sending fails, the invitation is removed and the request fails.
- **The owner is a member with role `OWNER`**, not a column of `schools`. A partial unique index allows one owner per school.
- **School roles are read from the database on every request.** They are not in the access token, which is trusted for 15 minutes: a removed member loses access at once.
- **A user who owns a live school can't delete their account** (409 `school/owner-cannot-leave`): they transfer the school or delete it first. Otherwise deleting an account removes its memberships and student rows.
- **What ending means:** a deleted school keeps its row (`deleted_at`) and frees its slug. A member, a student and an invitation (accepted, declined, revoked or expired) are removed rows.
- **A foreign school is a missing school.** Someone who is not on the staff gets 404 for everything under `/schools/{id}`, as for a school that doesn't exist.
- **Lists of people join `users`** in the repository query. It is the same service and schema, and the students list is searched by name and email.
- **No domain events yet**: nothing listens. They arrive with their first listener.

## Verified by hand

- **Done when:** A signs in and opens a school → invites B's email as `AUTHOR` → the email (text and HTML, with the link) is in Mailpit → B signs in, views and accepts with the token → B's `GET /schools` lists the school with role `AUTHOR`, and `GET …/members` shows both.
- Invitations: a wrong token → 404; accepting twice → 404; inviting a member → 409 `school/already-member`; inviting as `OWNER` → 400; inviting an email again replaces the invitation and the old link is 404.
- Mail server unreachable → `POST …/invitations` is a 500 and leaves no row in `school_invitations`.
- Roles: an author can't invite, list invitations, edit or delete the school (403); the owner and an admin can't change their own role (403); only the owner transfers.
- Ownership: the owner can't leave or delete their account (409 `school/owner-cannot-leave`); after a transfer the old owner is an admin, may leave, and may delete the account.
- Slugs: a taken one → 409 `school/slug-taken`; a reserved one → 400; a malformed one → 400 `validation-error`; after a rename the old slug is 404; after the school is deleted its slug can be taken again.
- Visibility: a non-member and a student get 404 on `/schools/{id}`; `GET /schools/by-slug/{slug}` works without a token.
- Students: adding by email signs a new user up; adding twice returns the same student; `$search` finds by name and by email, and treats `%` and `_` as plain characters; `GET /schools/studying` lists the school for the student; removing works.
- Not verified: an invitation that expires (7 days), the limit of 50 pending invitations, the hourly cleanup job, and that deleting an account removes the memberships and student rows it still has (the accounts deleted in the check had none left). Two requests racing for one slug, or for the same member, were not tried either.
