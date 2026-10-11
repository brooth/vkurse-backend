# M11: Chat

> A chat for every course, shared by its students and the school's staff, with banners from the staff and live delivery, in the `chat` module of `services/platform`. It was added to the main plan after M10, and the later milestones moved up by one. Ported in its features from a NestJS chat of one user with an assistant; the many-people chat and the delivery are designed fresh. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/chat/README.md).

## Scope

| Part | State |
|---|---|
| `Chat`: one per course, opened with the course; a chat for every course that existed | Done |
| Messages: text, replies, edit, delete (kept as an empty row), a flood limit | Done |
| Banners of four types, written, pinned and unpinned by the staff | Done |
| Attachments through storage | Done |
| Votes; read marks and unread counts; "my chats" | Done |
| Live delivery: server-sent events per chat, Postgres `NOTIFY` between nodes | Done |
| Emails or push for messages and banners | Not done: nothing in a chat notifies outside the app |
| Typing indicators, who is online | Not done |
| Direct chats, a school-wide chat, several chats per course | Not done |
| A read-only mode; muting or banning a person; a report button | Not done |
| Mentions, search, emoji reactions beyond the two votes, voice messages | Not done |
| Closing an open stream the moment someone loses the course | Not done: it ends with their token, 15 minutes at most |

## Decisions

- **A new module**, depending on `course`, `learning`, `school`, `storage` and `user`. `course` raises `CourseDrafted`; it knows nothing of chats.
- **Membership is computed, not stored**: the school's staff and whoever has the course now. No member rows to keep in step with enrollments.
- **A banner is a message with a type**, so it is in the feed, can be answered and voted on like any message, and is pinned by a flag.
- **Every staff role writes banners, pins and deletes anyone's message.** Students write.
- **A deleted message keeps its row.** Replies point to it and the feed's order is the ids.
- **Ids are the feed's order and its cursor**: they are time-ordered (UUIDv7).
- **Server-sent events, one stream per chat.** Sending stays plain `POST`; a stream carries only what happens while it is open, and the client catches up with `after=`.
- **`NOTIFY` in the transaction that made the change.** A message is announced exactly when it is committed, with no broker to run. Each node reads the message once per event and sends it to its streams.
- **A stream ends with the access token**, so every reconnect is a fresh access check.
- **Attachments are a storage purpose of their own**, billed to the school like every file.
- **The reply quote is read with the feed**, not copied into the message: an edited or deleted original shows as it is now.
- **The Postgres driver became a compile dependency** of the service: reading notifications is the driver's own API.

## Deviations from the source

| Source | Vkurse |
|---|---|
| A conversation of one user with an assistant or with support | A chat of a course, with many people |
| A banner was one of 18 system codes, each with its own data | One of 4 types the staff choose, with the message's own text |
| Attachments were URLs | Stored files, linked with signed links |
| `replyTo` was a copy of the quoted message | The quoted message's id |
| Votes were a map of user to vote inside the message | A row per person and message |
| `read` was a flag on a message | A read mark per person and chat |
| Marking read and voting took a message id alone, with no check that it was the caller's conversation | Every message is reached through its chat, and the chat through its members |
| socket.io fed by Firestore listeners; a value on subscribe, then updates | Server-sent events fed by `NOTIFY`; the feed is read by request, the stream carries only what is new |
| Assistant roles, action buttons, pending actions, execution models, transient "thinking" banners | Not ported |
| Retitle and delete a conversation | Not ported |

## Verified by hand

Against the environment from `compose.dev.yaml`; streams read with `curl -N`.

- **Done when:** an author drafts two courses → each has a chat, made by the event → a student given the course and a curator both read it → the student writes, and the open streams of the curator and of a second student both get `message.created` with the message → the curator posts a pinned `WARNING` banner → the student sees it in the feed and in the chat's pinned banners, with an unread count → the student posting a banner is 403.
- **Access:** someone without the course gets 404 for the chat, its messages, a post, a vote, links, an upload and the stream; a student of one course can't reach another course's chat; no token → 401. A revoked enrollment → 404 and the chat leaves "my chats". A deleted course's chat → 404, for the staff too.
- **Messages:** a reply shows its quote; an empty message, a banner without text → 400; an answer to another chat's message, a message reached through the wrong chat → 404; someone else editing → 403; a student deleting someone else's → 403, the curator can. A deleted message is empty, its quote says deleted, and editing, voting on or answering it → 409 `chat/message-deleted`.
- **Banners:** all four types; a fourth pin → 409 `chat/too-many-pins`; pinning by a student, pinning an ordinary message, a pin that ends in the past → 403, 400, 400; unpin; a pin whose time has passed leaves the pinned list.
- **Votes:** up and down by two people, a change, a take-back; counts and `mine` are right for each reader; a vote of 5 → 400.
- **Read state:** unread counts for a student and a curator in "my chats"; the mark moves forward only; one's own message is not unread; the last message of each chat.
- **Paging:** the latest page, `before` and `after` give the right slices, oldest first.
- **Attachments:** upload, confirm, post a file-only message, a curator opens it by link; an unconfirmed file, another person's file, another person confirming → 404; a file in a second message → 400; the file is not in the school's library and is deleted with its message; a school in debt → 402 for the upload while text still posts.
- **Streams:** `text/event-stream`; created, updated and deleted events in order; another chat's stream gets nothing; the event's data carries no reader's vote; a fourth stream of one person → 429; a heartbeat comes; after the listener's database connection is killed it reconnects in about 2 seconds, the message sent in between is missing from the stream and comes back with `after=`.
- **Flood:** the 31st message in a minute → 429 `chat/too-many-messages`.
- Startup: V10 applies, every earlier course has a chat, and the listener starts.
- Not verified: a stream ending at the token's expiry; two nodes; a stream behind a proxy that buffers; the listener through a pooler; hundreds of streams on one chat; two votes or two pins at the same moment; a chat opened by first access racing the event.
