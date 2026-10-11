# Chat module

> Course chats. Every course has one chat, made with the course and shared by the course's students and the school's staff. The staff can write banners: messages that stand out, and can be pinned above the feed.

| Layer | File | Description |
|---|---|---|
| `domain` | `Chat` | The chat of a course |
| | `Message` | A message, a banner among them: `post`, `edit`, `delete`, `pin`, `unpin`, `isPinned` |
| | `BannerType` | `ANNOUNCE`, `INFO`, `WARNING`, `DANGER` |
| | `ChatMember` | Someone in a chat: a staff member with a role, or a student |
| | `MessageVote`, `VoteCount` | A person's vote on a message (+1 or −1); the votes on a message |
| | `ChatRead` | How far a person has read a chat |
| | `ChatEvent`, `ChatBroadcaster` | What a stream carries; the port that tells every node |
| | `ChatErrors`, `*Repository` | |
| `application` | `ChatService` | `forCourse`, `get`, `listMine`, `markRead` |
| | `MessageService` | `list`, `post`, `edit`, `delete`, `vote`, `pin`, `unpin`, `requestUpload`, `confirmUpload`, `links` |
| | `ChatStreamService`, `ChatEventSink` | Who may open a stream; passing events on to this node's streams |
| | `ChatAccess`, `MessageViews` | Who is in a chat; what is shown around a message |
| | `CourseChatListener` | Opens a course's chat on `CourseDrafted` |
| `infrastructure` | `PostgresChatBroadcaster`, `Jpa*Repository` | |
| `web` | `ChatController`, `MessageController`, `ChatStreamHub` and their records | |

It depends on [`course`](../course/README.md) (the course and its event), [`learning`](../learning/README.md) (who has a course), [`school`](../school/README.md) (roles), [`storage`](../storage/README.md) (attachments) and `user`. None of them depends on it.

## Rules

**Chats**
- A course has exactly one chat. It is opened when the course is drafted (`CourseDrafted`), and also on first access if it is still missing.
- **Who is in it:** every staff member of the course's school, and every student who has the course now. Nobody joins or leaves by hand. It is checked on every request: someone who loses the course loses the chat, and for anyone outside it the chat is 404.
- A deleted course's chat is gone with it. A draft or unpublished course has its chat.

**Messages**
- A message is text (Markdown, never HTML, at most 4000 characters), files (at most 10), or both. It may answer another message of the same chat.
- The feed is the chat's messages by id: ids are time-ordered. It is read in pages, the latest first, then `before` a message or `after` one.
- Only the author edits a message. The author and the staff delete one. **A deleted message keeps its place**, emptied and marked `deleted`, so replies and the order stay; its files are deleted. A deleted message can't be edited, voted on, pinned or answered: 409 `chat/message-deleted`.
- A person writes to a chat at most 30 times a minute: 429 `chat/too-many-messages`.
- Votes: one per person and message, up (1) or down (−1); it can be changed or taken back.
- Nothing in a chat sends an email.

**Banners**
- **A banner is a message with a type** (`ANNOUNCE`, `INFO`, `WARNING`, `DANGER`). Only the staff write banners (403 for a student), and a banner needs text.
- The staff pin a banner above the feed, with no end or until a time, and unpin it. Pinned banners come with the chat itself. A chat has at most 3 pinned at once: 409 `chat/too-many-pins`. Only a banner can be pinned.

**Attachments** (the files are kept by the [storage module](../storage/README.md))
- Uploaded in three steps, like the school's library, with the purpose `CHAT_FILE` (50 MB, the library's types). They are kept for the school, count toward its storage bill, and are not in its library.
- Only the uploader's own confirmed files can be attached, each to one message.
- A school that owes Vkurse for storage can't take uploads (402 `billing/payment-required`); text still works. After 14 days of that, links stop (402 `billing/suspended`).

**Read state**
- Each person has a read mark per chat: the last message they read. It only moves forward. Their unread messages are those after it that others wrote and that aren't deleted.

**Streams**
- `GET /chats/{id}/stream` is a server-sent event stream of what happens in the chat **from the moment it is opened**: `message.created`, `message.updated` (an edit, a vote, a pin) and `message.deleted`, each with the message as data and the message's id as the event id. Events don't tell the reader's own vote.
- A stream knows nothing of what came before. **After a break, read `…/messages?after=` the last message you have, then open a new stream.**
- A stream ends when the access token expires, so access is checked again at each reconnect. A comment line (`:ping`) comes every 25 seconds. A person has at most 3 open streams per node: 429 `chat/too-many-streams`.
- **The token goes in the `Authorization` header, never in the URL.** A browser's `EventSource` can't send a header, so a web app reads the stream with `fetch`.
- Between nodes, events travel by Postgres `NOTIFY`, sent in the transaction that made the change: they are heard when it is committed, and never if it isn't. **Each node listens on a connection of its own, which must be a direct one**: `LISTEN` does not work through a pooler in transaction mode. While that connection is down the node's streams hear nothing; it reconnects by itself, and clients catch up as after any break.

**Who may do what**

| | Staff (any role) | Student with the course |
|---|---|---|
| Read the chat, write, answer, vote, attach files | ✓ | ✓ |
| Edit own messages | ✓ | ✓ |
| Delete messages | anyone's | own |
| Write banners, pin and unpin | ✓ | |

| Error | When |
|---|---|
| 409 `chat/message-deleted` | Changing, voting on, pinning or answering a deleted message |
| 409 `chat/too-many-pins` | A fourth pinned banner |
| 429 `chat/too-many-messages` | More than 30 messages a minute from one person in one chat |
| 429 `chat/too-many-streams` | A fourth open stream |

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `GET /chats` | | any user | `ChatResponse[]` with `lastMessage` and `unread`: my chats |
| `GET /courses/{courseId}/chat` | | members | `ChatResponse` with `pinned` |
| `GET /chats/{id}` | | members | `ChatResponse` with `pinned` |
| `POST /chats/{id}/read` | `{ messageId }` | members | `ChatResponse` |
| `GET /chats/{id}/stream` | | members | `text/event-stream` |
| `GET /chats/{id}/messages` | `before`, `after`, `limit` (50; 100 at most) | members | `MessageResponse[]`, oldest first |
| `POST /chats/{id}/messages` | `{ text?, fileIds?, replyToId?, banner?: { type, pinned?, pinnedUntil? } }` | members; `banner` staff | `MessageResponse` |
| `PATCH /chats/{id}/messages/{messageId}` | `{ text }` | the author | `MessageResponse` |
| `DELETE /chats/{id}/messages/{messageId}` | | the author, staff | `MessageResponse`, deleted |
| `POST /chats/{id}/messages/{messageId}/vote` | `{ vote: 1 \| -1 \| null }` | members | `MessageResponse` |
| `POST /chats/{id}/messages/{messageId}/pin` | `{ until? }` | staff | `MessageResponse` |
| `POST /chats/{id}/messages/{messageId}/unpin` | | staff | `MessageResponse` |
| `POST /chats/{id}/messages/{messageId}/links` | | members | `{ fileId: { url, expiresAt } }` |
| `POST /chats/{id}/files` | `{ name, contentType, size }` | members | `UploadResponse` |
| `POST /chats/{id}/files/{fileId}/confirm` | | the uploader | `FileResponse` |

- `ChatResponse`: `id`, `course { id, schoolId, title, cover }`, `staffRole` (the caller's; absent for a student), `pinned` (the pinned banners, newest first), `lastMessage`, `unread`, `lastReadMessageId`.
- `MessageResponse`: `id`, `createdAt`, `chatId`, `author { id, name, email, picture }`, `staffRole` (the author's now; absent for a student), `text`, `attachments[ { id, name, contentType, size } ]`, `replyTo { id, author, text (its first 200 characters), deleted }`, `banner`, `pinned`, `pinnedUntil`, `editedAt`, `deleted`, `votes { up, down, mine }`.
- `GET /chats` covers the courses the caller has and every course of the schools they work in; within a page, the chat written to last comes first.

## Tables

- `chats`: `school_id`, `course_id` (unique).
- `chat_messages`: `chat_id`, `school_id`, `author_id`, `text`, `attachments` (JSONB), `reply_to_id`, `banner`, `pinned`, `pinned_until`, `edited_at`, `deleted_at`.
- `chat_message_votes`: `message_id`, `chat_id`, `user_id`, `vote`; unique on (`message_id`, `user_id`).
- `chat_reads`: `chat_id`, `user_id`, `last_read_message_id`; unique on (`chat_id`, `user_id`).
