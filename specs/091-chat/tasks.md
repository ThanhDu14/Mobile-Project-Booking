---

description: "Danh sách task triển khai spec 091 – Chat nhóm và chat với chủ sân"
---

# Tasks: Chat nhóm và chat với chủ sân

**Input**: tài liệu thiết kế trong `specs/091-chat/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test:
- Security Rules trên Firebase Emulator (CR1–CR8) và Storage trên Supabase local (S1–S4); mỗi dòng phân quyền có ít nhất một `assertSucceeds` và một `assertFails`;
- Edge Function trên Emulator (C1–C8, D1–D4, R1–R2, H1–H2, M1–M2, A1), gồm **C3: 20 người gửi cùng lúc, lặp 10 lần** và **D1: hai bên mở chat riêng cùng lúc, lặp 20 lần**;
- test Android trên Emulator cho outbox khi mất mạng (SC-004); ViewModel, UseCase, outbox.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US5)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CH/` | `app/src/main/java/com/example/booking_app/feature/chat/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/chat/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/feature/chat/` |
| `FN/` | `supabase/functions/` |

Task của spec khác ghi là `010-T###`, `070-T###`, `090-T###`.

---

## Phase 1: Setup

**Purpose**: kiểm tra phần nền đã có, chỉ thêm phần riêng của chat. **Không tạo lại file đã có.**

- [ ] T001 Kiểm tra đã xong và dùng lại, không tạo lại:
  - 010-T001, T002, T004, T012, T015, T021: version catalog, plugin, `core/session`, `FirebaseModule`, `SupabaseModule` (có `Storage` từ 070-T005), `FN/_shared/auth.ts`;
  - 070-T006: `core/ui/UiState.kt`, `include_state_views.xml`;
  - 090-T006: `FN/_shared/groups.ts` có hook `onGroupMembershipChanged`; 090-T020: `APP/core/image/ImageCompressor.kt`;
  - trên main: `firebase.json`, `firestore.rules` nền, `firestore-tests/` (Vitest, `test/setup.ts`), `supabase/config.toml`.

  Phần nào chưa có thì làm theo task gốc, không viết lại ở spec này
- [ ] T002 [P] Tạo `RES/values/strings_chat.xml`, tiền tố `chat_`: tiêu đề "Tin nhắn", "Tin nhắn khách hàng", "Chat nhóm"; "Đang gửi", "Gửi lỗi", "Gửi lại", "Xóa"; "[Hình ảnh]", "Tin nhắn đã được thu hồi", "Tin nhắn đã bị ẩn", "Người dùng đã xóa", "Cuộc trò chuyện chỉ đọc", "Nhóm đã bị tạm khóa", "Bạn không thể gửi tin trong cuộc trò chuyện này"; menu "Tắt/Bật thông báo", "Chặn/Bỏ chặn", "Thu hồi", "Báo cáo"; 5 lý do báo cáo (dùng chung chữ với 090); rỗng "Tham gia một nhóm", "Tìm sân để hỏi chủ sân"; giải thích quyền camera; thông báo cho mọi mã lỗi ở [contract mục 5](contracts/edge-functions-chat.md#5-mã-lỗi)
- [ ] T003 [P] Viết `scripts/seed/091-chat.ts` theo [quickstart mục 1](quickstart.md#1-chuẩn-bị), chạy sau `scripts/seed/090-community.ts`: `owner1` (`ownerStatus = APPROVED`) có `V1`, `V2` `ACTIVE`, `V3` `HIDDEN`; `owner2` chưa duyệt; chat `player1`–`owner1` 40 tin (1 ảnh, 1 `HIDDEN`, 1 `RECALLED`); `group_G1` khớp thành viên `G1`, 35 tin; chat `player2`–`owner1` với `participants/owner1.blockedOther = true`. Mọi chat có `unreadCounts`, `participants` khớp `memberIds`

**Checkpoint**: seed 090 + 091 chạy được trên Emulator.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: hàm dùng chung (có đồng bộ chat nhóm cho 090), outbox, Rules, Storage, index, model, điều hướng.

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào.

### Server dùng chung

- [ ] T004 Kiểm tra `FN/_shared/firestore.ts` (B) hỗ trợ trong transaction: transform `REQUEST_TIME` khi commit (cho `createdAt`, `updatedAt`) và `increment` theo field path `unreadCounts.<uid>`. Thiếu thì thêm bản tối thiểu (REST `commit` có `updateTransforms` với `setToServerValue: REQUEST_TIME` và `increment`) rồi báo B gộp
- [ ] T005 [P] Tạo `FN/_shared/media.ts` + `FN/_shared/media.test.ts`: `validateImage(contentType, size)` (chỉ `image/jpeg`, `image/png`, `image/webp`; `size ≤ 2097152`), `signedUpload(bucket, path)`, `signedDownloads(bucket, paths, 3600)`, `objectExists(bucket, path)`, `deletePrefix(bucket, prefix)`. Nếu 090-T045 đã viết các hàm này trong `group-media-url` thì chuyển sang đây và để `group-media-url` gọi lại (không đổi hành vi)
- [ ] T006 [P] Tạo `FN/_shared/chats_fixtures.ts`: `seedDirectChat(player, owner, venueId, messages)`, `seedGroupChat(groupId, memberIds, messages)`; `assertChatInvariant(chatId)`: khóa của `unreadCounts` đúng bằng `memberIds`, có đúng một `participants/{uid}` cho mỗi `memberIds`, `lastMessage.messageId` là tin có `createdAt` lớn nhất; mock `send-notification` ghi lại lời gọi
- [ ] T007 Viết test `FN/_shared/chats.test.ts` cho **H1** (`syncGroupChat` với `created`, `added`, `removed`, `deleted` theo bảng [research R5](research.md#r5-đồng-bộ-chat-nhóm-với-danh-sách-thành-viên-fr-002); người mới có `lastReadAt = joinedAt`) và **H2** (`purgeChat` xóa hết `messages`, `participants`, ảnh `chat-media/{chatId}/`); chạy thấy fail
- [ ] T008 Cài `FN/_shared/chats.ts`: `syncGroupChat(tx, groupId, change)` (`chatId = group_{groupId}`; `participants/{uid}` có `userId`, `chatId`, `userSnapshot`, `joinedAt`, `lastReadAt = joinedAt`, `muted = false`); `purgeChat(chatId)` (xóa theo lô ≤ 500, rồi `deletePrefix`); khung `anonymizeCommunityChats(uid)` (cài ở T053). Điền thân hook `onGroupMembershipChanged` trong `FN/_shared/groups.ts` (090-T006) để gọi `syncGroupChat`. Sau đó T007 phải xanh và test G1, G5 của 090 vẫn xanh
- [ ] T009 Tạo khung 3 function `FN/chat-send/index.ts`, `FN/chat-action/index.ts`, `FN/chat-media-url/index.ts`: xác thực `_shared/auth.ts`, `accountStatus == ACTIVE`, router `action` (cho `chat-action`, `chat-media-url`), body lỗi `{ error: { code, message } }`, helper `loadChatForMember(tx, chatId, uid)` (`CHAT_NOT_FOUND` khi không có hoặc `DELETED`, `NOT_PARTICIPANT` khi không trong `memberIds`)

### Rules, Storage và index

- [ ] T010 Viết test Rules `firestore-tests/test/chats.test.ts` cho **CR1–CR8** theo [security-rules.md mục 2](contracts/security-rules.md#2-bảng-test-rules-mỗi-dòng-có-cả-ca-được-phép-và-ca-bị-chặn), cấu trúc giống `firestore-tests/test/base.test.ts` (`createTestEnv()`, `withSecurityRulesDisabled` để seed, claim `appRole`). Mỗi dòng phân quyền có ít nhất một `assertSucceeds` và một `assertFails`; dòng `if false` chỉ có ca bị chặn. Chạy thấy fail
- [ ] T011 Thêm phần `chats` vào `firestore.rules` đúng [bản phác](contracts/security-rules.md#1-bản-phác-rules): đọc theo `memberIds`; `update` chỉ `unreadCounts.{mình} = 0`; `participants/{uid}` chỉ chính người đó sửa `lastReadAt` (`== request.time`), `muted`, `blockedOther`, `viewingUntil` (`null` hoặc `≤ request.time + 10 phút`, nới rộng để chịu được đồng hồ điện thoại lệch); `messages` chỉ đọc; collection group `participants` chỉ `list` khi `userId == request.auth.uid`. Sau đó T010 phải xanh
- [ ] T012 [P] Viết test pgTAP `supabase/tests/chat_media.test.sql` cho **S1–S4** ([security-rules.md mục 3](contracts/security-rules.md#3-storage-chat-media)); chạy thấy fail
- [ ] T013 Tạo migration `supabase/migrations/<timestamp>_chat_media_bucket.sql`: bucket `chat-media` (`public = false`, `file_size_limit = 2097152`, `allowed_mime_types = {image/jpeg,image/png,image/webp}`), không có policy cho `authenticated`, `anon`. Nếu bucket đã có thì chỉ chỉnh cấu hình. Sau đó T012 phải xanh
- [ ] T014 [P] Thêm vào `firestore.indexes.json` các index ở [data-model mục 6](data-model.md#6-index-cần-thêm-vào-firestoreindexesjson): `chats` (`memberIds` array-contains, `updatedAt desc`), `chats` (`memberIds` array-contains, `ownerId`, `type`, `updatedAt desc`), collection group `messages` (`senderId`), collection group `participants` (`userId`)

### Domain, data và outbox (app)

- [ ] T015 [P] Tạo model trong `CH/domain/model/` đúng [data-model mục 7](data-model.md#7-mô-hình-phía-app-kotlin-featurechatdomain): `ChatSummary`, `ChatDetail`, `Participant`, `ChatMessage` (`canRecall(now)` khi `isMine && status == NORMAL && now < createdAt + 10 phút`), `OutboxEntry`; enum `ChatType` (`GROUP`, `DIRECT`), `ChatStatus` (`ACTIVE`, `READ_ONLY`, `DELETED`), `MessageStatus` (`NORMAL`, `RECALLED`, `HIDDEN`), `Delivery` (`PENDING`, `FAILED`, `SENT`), `ReportReason`. Tin chữ "1–1000 ký tự, không chỉ khoảng trắng"
- [ ] T016 [P] Tạo `CH/data/dto/ChatDto.kt`, `ParticipantDto.kt`, `MessageDto.kt`, `EdgeDtos.kt` (request/response của 3 function) và `CH/data/dto/Mappers.kt`; `CH/domain/ChatError.kt` ánh xạ đủ mã lỗi ở [contract mục 5](contracts/edge-functions-chat.md#5-mã-lỗi)
- [ ] T017 Tạo interface `CH/domain/ChatRepository.kt` (`observeChats(mode)`, `observeChat`, `openDirect(venueId)`, `observeMessages(chatId)` 30 tin mới nhất, `loadOlder(chatId, before)`, `send(entry)`, `markRead(chatId)`, `setViewing(chatId, until)`, `setMuted`, `setBlocked`, `recall`, `report`), `CH/domain/ChatMediaRepository.kt` (`upload(chatId, messageId, uri): Flow<UploadState>`, `signedUrls(paths)`, `discard`) và `APP/core/chat/UnreadChatsReader.kt` (`observeUnreadChatCount(): Flow<Int>`)
- [ ] T018 Tạo `CH/data/di/ChatModule.kt` (bind repository, `UnreadChatsReader`) và nav graph lồng `RES/navigation/nav_chat.xml` (`ChatListFragment` với `mode: PLAYER / OWNER`, `ChatFragment` với `chatId`, `ReportMessageBottomSheet`); `include` vào graph chính của 010-T016; deep link `smashnow://chat/{chatId}`, `smashnow://chat/group/{groupId}`, `smashnow://chat/direct?venueId={venueId}`, `smashnow://chat/owner`
- [ ] T019 [P] Unit test `TEST/data/ChatOutboxTest.kt` (fake DataStore, fake clock, fake `chat-send`): thêm tin → `PENDING` ngay; gửi thành công → bỏ khỏi outbox; mất mạng → giữ, có mạng lại tự gửi cùng `messageId`; quá 60 giây → `FAILED` nhưng **vẫn tự gửi lại khi có mạng** (lỗi do mạng); lỗi không thử lại được thì chờ người dùng bấm; "Gửi lại" dùng lại `messageId`; "Xóa" khi server đã có tin → thành `SENT`, không xóa; `BUSY` → thử lại; lỗi không thử lại được (`BLOCKED`, `NOT_PARTICIPANT`, `INVALID_ARGUMENT`) → `FAILED` ngay; outbox còn sau khi tạo lại instance (giả lập app bị tắt)
- [ ] T020 Cài `CH/data/ChatOutbox.kt` (DataStore `chat_outbox`, JSON theo `chatId`; lắng nghe `ConnectivityManager` để gửi lại; gửi tuần tự từng cuộc để giữ thứ tự). Cho T019 xanh

**Checkpoint**: H1, H2, CR1–CR8, S1–S4 xanh; tạo nhóm ở 090 là có ngay `chats/group_{groupId}`; app build được với `nav_chat`.

---

## Phase 3: User Story 1 – Nhắn tin riêng với chủ sân (Priority: P1) 🎯 MVP

**Goal**: người chơi mở chat với chủ sân từ chi tiết sân và gửi tin; chủ sân thấy ở "Tin nhắn khách hàng" kèm cơ sở được hỏi và trả lời; tin tới bên kia trong ≤ 3 giây.

**Independent Test**: A mở chi tiết sân của chủ sân B, gửi "Tối thứ 7 còn sân không?"; B (chế độ quản lý sân) thấy cuộc mới kèm tên cơ sở, trả lời; A thấy trả lời trong vài giây mà không tải lại (quickstart Q1–Q4, Q11; C1, C2, C4, C7, C8, D1–D4).

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T021 [P] [US1] Test Deno `FN/chat-action/index.test.ts` cho `openDirect`:
  - **D1:** người chơi và chủ sân gọi `openDirect` (và tạo qua cả hai phía) cùng lúc lần đầu, **lặp 20 lần** → mỗi lần đúng 1 document `chats/{uidNhỏ}_{uidLớn}`; in `runs=20 chats=1`;
  - **D2:** mở lần 2 từ `V2` cùng chủ → cùng `chatId`, `venueId`/`venueSnapshot` đổi, `created = false`;
  - **D3:** chủ sân mở với sân của mình → `OWN_VENUE`; sân `HIDDEN` → `VENUE_INACTIVE`; chủ sân chưa duyệt → `OWNER_NOT_APPROVED`;
  - **D4:** chat đã có, sân bị khóa sau đó → `openDirect` vẫn trả `chatId`; chủ sân bị thu hồi quyền (`ownerStatus = NONE`) → `chat-send` từ người chơi vẫn thành công (Edge Cases);
  - tạo mới có đủ `type = DIRECT`, `memberIds`, `ownerId`, `playerId`, `memberSnapshots`, 2 `participants`, `unreadCounts` = 0; `assertChatInvariant`.
- [ ] T022 [P] [US1] Test Deno `FN/chat-send/index.test.ts`:
  - **C1:** tin chữ hợp lệ → `NORMAL`, `createdAt` theo giờ server, `senderSnapshot` khớp `participants`; `lastMessage` đúng; `unreadCounts` người nhận +1, người gửi không đổi;
  - **C2:** cùng `messageId` 5 lần (cả song song) → 1 tin, `unreadCounts` +1 đúng một lần, các lần sau `200`;
  - **C4:** người ngoài cuộc → `NOT_PARTICIPANT`;
  - **C7 (phần chữ):** tin rỗng, chỉ khoảng trắng, 1001 ký tự → `INVALID_ARGUMENT`;
  - **C8:** người `muted`, người `viewingUntil > now`, người còn lại → chỉ người còn lại nhận `CHAT_MESSAGE` với `data = { chatId, messageId }`;
  - chat `READ_ONLY` → `CHAT_READ_ONLY`; `assertChatInvariant` sau mỗi ca.
- [ ] T023 [P] [US1] Unit test `TEST/ui/conversation/ChatViewModelTest.kt` (MockK + Turbine): `Loading → Content` gộp tin server và outbox (outbox ở cuối, đúng trạng thái); gửi → tin hiện ngay `PENDING`; ô nhập rỗng/chỉ khoảng trắng không gửi, > 1000 ký tự chặn và hiện bộ đếm; khi tin mới nhất hiển thị → gọi `markRead`; gia hạn `viewingUntil` mỗi 45 giây khi đang mở, `null` khi rời; `viewingUntil` tính theo **giờ server ước lượng** (độ lệch đo từ lần ghi `lastReadAt = serverTimestamp()` gần nhất), không theo giờ điện thoại; đầu màn hình chat riêng có tên và ảnh cơ sở; `READ_ONLY` ẩn ô nhập; cuộn lên tải thêm 30 tin
- [ ] T024 [P] [US1] Test Android `ATEST/data/ChatOutboxOfflineTest.kt` trên Firebase Emulator + Supabase local (**SC-004**): tắt mạng (`FirebaseFirestore.disableNetwork` và chặn HTTP tới Supabase), gửi tin, đợi 20 giây, bật mạng → đúng 1 document tin trên server; lặp 5 lần

### Implementation cho User Story 1

- [ ] T025 [US1] Cài `openDirect` trong `FN/chat-action/index.ts` theo [research R3, R4](research.md#r3-id-cuộc-trò-chuyện-tất-định-fr-003-sc-005): trong `runTransaction` đọc `venues/{venueId}`, `users/{ownerId}`; chưa có chat thì kiểm tra điều kiện R4 và tạo chat + 2 `participants` (`userSnapshot` từ `users`); luôn cập nhật `venueId`, `venueSnapshot` (`name`, `photoPath`). Sau đó T021 phải xanh (**D1 20/20 lần**)
- [ ] T026 [US1] Cài `FN/chat-send/index.ts` cho tin chữ theo [contract mục 1](contracts/edge-functions-chat.md#1-chat-send) bước 1, 2, 4, 5 (bước 3 chặn/khóa nhóm làm ở T033, T050): `text` sau trim 1–1000 ký tự; ghi tin và cập nhật `chats` trong một transaction (`createdAt`, `updatedAt` bằng `REQUEST_TIME`, `unreadCounts.<uid>` increment cho mọi người trừ người gửi); sau commit gửi `CHAT_MESSAGE` theo [research R7](research.md#r7-thông-báo-tin-nhắn-mới-fr-013-fr-018). Sau đó T022 phải xanh
- [ ] T027 [US1] Cài `CH/data/FirestoreChatRepository.kt`: `openDirect` (gọi `chat-action`), `observeChat`, `observeMessages` (listener `orderBy createdAt desc limit 30` bọc `callbackFlow`), `loadOlder` (`startAfter`, một lần đọc), `send` (qua `ChatOutbox` → `chat-send`, làm mới token khi `401`), `markRead` (batch `participants/{mình}.lastReadAt = serverTimestamp()` và `chats.unreadCounts.{mình} = 0`), `setViewing`
- [ ] T028 [US1] Cài UseCase `CH/domain/usecase/OpenDirectChat.kt`, `ObserveMessages.kt`, `LoadOlderMessages.kt`, `SendMessage.kt` (sinh `messageId` UUID), `RetryMessage.kt`, `DiscardMessage.kt`, `MarkChatRead.kt`
- [ ] T029 [US1] Cài `CH/ui/conversation/ChatViewModel.kt`, `CH/ui/conversation/ChatFragment.kt` + `RES/layout/fragment_chat.xml`, `CH/ui/conversation/MessageAdapter.kt` (`ListAdapter` + `DiffUtil`) + `RES/layout/item_message_mine.xml`, `RES/layout/item_message_other.xml` (thời gian theo `Asia/Ho_Chi_Minh`, trạng thái "Đang gửi"/"Gửi lỗi" với nút "Gửi lại"/"Xóa"). Theo wireframe `ui/09_community-chat_buoc3_chat-voi-chu-san.png`, `buoc5_chu-san-tro-chuyen-voi-khach.png`. Cho T023 xanh
- [ ] T030 [US1] Cài khung `CH/ui/list/ChatListViewModel.kt`, `CH/ui/list/ChatListFragment.kt` + `RES/layout/fragment_chat_list.xml`, `CH/ui/list/ChatAdapter.kt` + `RES/layout/item_chat.xml` với 2 chế độ theo [research R15](research.md#r15-chủ-sân-tin-nhắn-khách-hàng-fr-005) (`OWNER`: `memberIds array-contains`, `ownerId == uid`, `type == DIRECT`; `PLAYER`: `memberIds array-contains`, bỏ cuộc có `ownerId == uid`); xử lý deep link `smashnow://chat/direct?venueId=` (gọi `OpenDirectChat` rồi mở `ChatFragment`, khách chưa đăng nhập thì mời đăng nhập) và `smashnow://chat/owner`. Số chưa đọc và rỗng làm ở US3

**Checkpoint**: US1 chạy độc lập; **D1 xanh 20/20 lần**; quickstart Q1–Q4, Q11 đúng.

---

## Phase 4: User Story 2 – Chat nhóm của CLB (Priority: P1)

**Goal**: mỗi nhóm có một chat; thành viên vào nhóm thì có trong chat và đọc được lịch sử, rời hoặc bị mời ra thì mất quyền ngay; nhóm bị khóa thì chỉ đọc.

**Independent Test**: nhóm có A, B, C; A gửi tin, B và C thấy ngay; D vào nhóm và đọc được tin cũ; C bị mời ra thì không mở và không gửi được (quickstart Q5–Q7; C3, C4, C6).

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T031 [P] [US2] Bổ sung vào `FN/chat-send/index.test.ts`:
  - **C3:** chat nhóm 20 thành viên, **20 người gửi song song, lặp 10 lần** → đủ 20 tin; `unreadCounts[u]` = số tin của người khác; `lastMessage` là tin có `createdAt` lớn nhất; in `runs=10 messages=20 unreadOk=true lastOk=true`;
  - **C6 (phần nhóm):** nhóm `LOCKED` → `423 GROUP_LOCKED`, đọc vẫn được;
  - **tích hợp với 090:** gọi `group-manage join` rồi `chat-send` → thành công; gọi `group-manage remove` rồi `chat-send` từ người bị mời ra → `NOT_PARTICIPANT` (C4 sau khi rời nhóm).
- [ ] T032 [P] [US2] Bổ sung `ChatViewModelTest`: chat nhóm hiện tên và ảnh người gửi theo `senderSnapshot`, tin của mình bên phải; `senderDeleted` → "Người dùng đã xóa" và ảnh mặc định; nhóm `LOCKED` → ẩn ô nhập, hiện "Nhóm đã bị tạm khóa"; mất quyền (listener trả `PERMISSION_DENIED` sau khi bị mời ra) → đóng màn hình, báo "Bạn không còn trong cuộc trò chuyện"

### Implementation cho User Story 2

- [ ] T033 [US2] Bổ sung `FN/chat-send/index.ts` bước 3 cho chat nhóm: đọc `groups/{groupId}` trong transaction, `status != ACTIVE` → `423 GROUP_LOCKED`. Sau đó T031 phải xanh (**C3 10/10 lần**)
- [ ] T034 [US2] Xử lý deep link `smashnow://chat/group/{groupId}` → mở `ChatFragment` với `chatId = group_{groupId}`; đầu màn hình chat nhóm hiện tên và ảnh nhóm (đọc `groups/{groupId}`). Báo C/090 nối nút "Chat nhóm" trên `GroupDetailFragment` (090 `ui-screens.md` mục 3)
- [ ] T035 [US2] `MessageAdapter` hiện tên, ảnh người gửi trong chat nhóm và "Người dùng đã xóa"; `ChatListViewModel` đọc tên/ảnh nhóm theo lô ≤ 10 bằng `whereIn(documentId)` **kèm `where status in [ACTIVE, LOCKED]`** (bài học F1 của spec 090); bỏ khỏi danh sách cuộc có `status == DELETED`. Theo wireframe `ui/09_community-chat_buoc2_chat-nhom.png`. Cho T032 xanh

**Checkpoint**: US1 + US2 là MVP; **C3 xanh 10/10 lần**; quickstart Q5–Q7 đúng.

---

## Phase 5: User Story 3 – Danh sách trò chuyện và tin chưa đọc (Priority: P2)

**Goal**: mục "Tin nhắn" sắp theo tin mới nhất, có số chưa đọc và huy hiệu trên thanh điều hướng; tắt thông báo từng cuộc.

**Independent Test**: A có 3 cuộc; B gửi 2 tin vào một cuộc; danh sách của A đưa cuộc đó lên đầu với số "2"; A mở cuộc đó thì số biến mất (quickstart Q8, Q9; CR2, C8).

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T036 [P] [US3] Unit test `TEST/ui/list/ChatListViewModelTest.kt`: sắp `updatedAt desc`; số chưa đọc từ `unreadCounts[uid]`, > 99 hiện "99+"; tin cuối là ảnh hiện "[Hình ảnh]", tin thu hồi hiện "Tin nhắn đã được thu hồi"; tin mới tới thì cuộc nhảy lên đầu (AC 3.2); `Empty` chế độ `PLAYER` có "Tham gia một nhóm" và "Tìm sân để hỏi chủ sân" (AC 3.4), chế độ `OWNER` có thông báo riêng; `Error` + thử lại
- [ ] T037 [P] [US3] Unit test `TEST/data/UnreadChatsReaderImplTest.kt`: đếm số cuộc có `unreadCounts[uid] > 0` (không phải tổng số tin); cập nhật khi chat đổi; đăng xuất thì về 0

### Implementation cho User Story 3

- [ ] T038 [US3] Hoàn thiện `ChatListViewModel`, `ChatAdapter`, `item_chat.xml` (ảnh, tên nhóm hoặc tên người/cơ sở, tin cuối, thời gian, huy hiệu số chưa đọc, nhãn "Chỉ đọc"), 4 trạng thái. Theo wireframe `ui/09_community-chat_buoc1_tin-nhan.png`, `buoc4_chu-san-tin-nhan-khach.png`. Cho T036 xanh
- [ ] T039 [US3] Cài `CH/data/UnreadChatsReaderImpl.kt` (listener `chats where memberIds array-contains uid`) và bind vào `UnreadChatsReader` trong `ChatModule`. Báo A gắn huy hiệu cho mục "Tin nhắn" trong `MainActivity` qua interface này. Cho T037 xanh
- [ ] T040 [US3] Menu "Tắt thông báo"/"Bật thông báo" trong `ChatFragment` ghi `participants/{mình}.muted` (UseCase `CH/domain/usecase/SetMuted.kt`); biểu tượng chuông gạch trên dòng của cuộc đã tắt trong danh sách (AC 3.5)

**Checkpoint**: US3 chạy độc lập; quickstart Q8, Q9 đúng.

---

## Phase 6: User Story 4 – Gửi ảnh trong chat (Priority: P2)

**Goal**: chụp hoặc chọn một ảnh để gửi; ảnh chỉ người trong cuộc xem được; ảnh lỗi không ai khác thấy.

**Independent Test**: A gửi một ảnh vào chat với chủ sân B; B thấy và xem lớn được; C không thuộc cuộc không mở được ảnh kể cả khi có đường dẫn (quickstart Q10, Q15; M1, M2, C7).

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T041 [P] [US4] Test Deno `FN/chat-media-url/index.test.ts` và bổ sung `FN/chat-send/index.test.ts`:
  - **M1:** `upload` khi không trong cuộc → `NOT_PARTICIPANT`; `image/gif` → `INVALID_ARGUMENT`; 3 MB → `INVALID_ARGUMENT`; hợp lệ → path `{chatId}/{messageId}.{ext}`;
  - **M2:** `download` path của tin `RECALLED`, tin `HIDDEN`, chat khác → không có URL; tin `NORMAL` của chat mình → có URL; 31 path → `INVALID_ARGUMENT`;
  - `discard` khi chưa có tin → xóa ảnh; khi tin đã có → không xóa;
  - **C7 (phần ảnh):** `chat-send` có `imagePath` chưa tải → `PHOTO_MISSING`, không có tin; path sai tiền tố → `INVALID_ARGUMENT`; ảnh hợp lệ kèm chú thích rỗng → thành công, `lastMessage.type = IMAGE`.
- [ ] T042 [P] [US4] Bổ sung `ChatViewModelTest` và `ChatOutboxTest`: tin ảnh hiện ngay `PENDING` với ảnh cục bộ; tải ảnh lỗi → `FAILED`, "Gửi lại" tải lại rồi gửi cùng `messageId`, "Xóa" gọi `discard`; ảnh quá 2 MB sau nén hoặc không phải ảnh → báo lỗi, không vào outbox (AC 4.3); từ chối quyền camera → hiện giải thích, vẫn mở Photo Picker được (AC 4.5)

### Implementation cho User Story 4

- [ ] T043 [US4] Cài `FN/chat-media-url/index.ts` theo [contract mục 3](contracts/edge-functions-chat.md#3-chat-media-url) dùng `_shared/media.ts` (T005). Sau đó phần M1, M2 của T041 phải xanh
- [ ] T044 [US4] Bổ sung `FN/chat-send/index.ts`: `imagePath` phải đúng `{chatId}/{messageId}.{jpg,png,webp}` và `objectExists` trong `chat-media`; tin ảnh cho phép `text` 0–1000 ký tự; `lastMessage.type = IMAGE`. Sau đó phần C7 của T041 phải xanh
- [ ] T045 [US4] Cài `CH/data/SupabaseChatMediaRepository.kt` (nén bằng `ImageCompressor`, xin signed URL, PUT, phát trạng thái) và nối vào `ChatOutbox` (tải ảnh trước rồi mới gọi `chat-send`; tải lỗi → `FAILED`). Dùng `SignedUrlCache` cho ảnh hiển thị: nếu 090-T047 đã tạo trong `feature/community/data` thì chuyển lên `APP/core/media/SignedUrlCache.kt` (chỉ đổi package, cập nhật import của 090) để không import chéo feature. Cho T042 (phần outbox) xanh
- [ ] T046 [US4] Nút ảnh trong `ChatFragment`: chọn "Chụp ảnh" (`ActivityResultContracts.TakePicture`, xin quyền camera lúc dùng, có giải thích) hoặc "Chọn ảnh" (Photo Picker `PickVisualMedia`); bong bóng ảnh trong `item_message_*.xml` (ảnh qua signed URL, `contentDescription`), bấm mở `PhotoViewerFragment` (`core/ui`). Cho T042 xanh

**Checkpoint**: US4 chạy độc lập; quickstart Q10, Q15 đúng.

---

## Phase 7: User Story 5 – Thu hồi, báo cáo tin nhắn và chặn người dùng (Priority: P3)

**Goal**: thu hồi tin của mình trong 10 phút; báo cáo tin của người khác (một lần mỗi tin); chặn/bỏ chặn trong chat riêng; tin bị admin ẩn hiện "Tin nhắn đã bị ẩn".

**Independent Test**: A gửi rồi thu hồi trong 10 phút, B thấy "Tin nhắn đã được thu hồi"; B báo cáo một tin của A với lý do "Lừa đảo", báo cáo vào `reports`; chủ sân chặn A thì A không gửi được (quickstart Q12–Q14; R1, R2, C5).

### Tests cho User Story 5 ⚠️ viết trước, phải fail

- [ ] T047 [P] [US5] Bổ sung `FN/chat-action/index.test.ts` và `FN/chat-send/index.test.ts`:
  - **R1:** `recall` sau 5 phút → `RECALLED`, `text = ""`, `imagePath = null`, ảnh bị xóa khỏi `chat-media`, `lastMessage.preview` thành "Tin nhắn đã được thu hồi" nếu là tin cuối; sau 11 phút → `RECALL_WINDOW_PASSED`; tin người khác → `FORBIDDEN` (SC-003);
  - **R2:** `report` lần 1 → `201`, `reports/MESSAGE_{chatId}_{messageId}_{uid}` có `targetType = MESSAGE`, `targetPath`, `status = OPEN`, `action = NONE`; lần 2 → `ALREADY_REPORTED`; tin của mình → `OWN_MESSAGE`; tin đã thu hồi → `INVALID_ARGUMENT`; `OTHER` không có `detail` hoặc 501 ký tự → `INVALID_ARGUMENT`;
  - **C5:** `participants/{người nhận}.blockedOther = true` → `403 BLOCKED`; người chặn gửi → thành công; bỏ chặn rồi gửi → thành công.
- [ ] T048 [P] [US5] Unit test: `TEST/domain/ChatMessageTest.kt` (`canRecall` đúng tại mốc 10 phút theo `createdAt` server); `TEST/ui/report/ReportMessageViewModelTest.kt` (giống `ReportPostViewModelTest` của 090); bổ sung `ChatViewModelTest`: menu giữ tin (tin của mình ≤ 10 phút có "Thu hồi", tin người khác có "Báo cáo", tin đã thu hồi/ẩn không có menu); `BLOCKED` khi gửi → ô nhập thành "Bạn không thể gửi tin trong cuộc trò chuyện này"; tin `HIDDEN` hiện "Tin nhắn đã bị ẩn", không xin URL ảnh

### Implementation cho User Story 5

- [ ] T049 [US5] Cài `recall` và `report` trong `FN/chat-action/index.ts` theo [research R9, R12](research.md#r9-thu-hồi-tin-fr-010): `recall` trong transaction kiểm tra người gửi, `status == NORMAL`, `now < createdAt + 10 phút`; sau commit `deletePrefix`/xóa ảnh; `report` ghi `reports/{reportId}` với điều kiện "chưa tồn tại"
- [ ] T050 [US5] Bổ sung `FN/chat-send/index.ts` bước 3 cho chat riêng: đọc `participants/{người nhận}.blockedOther` trong transaction, `true` → `403 BLOCKED`. Sau đó T047 phải xanh
- [ ] T051 [US5] Cài `recall`, `report`, `setBlocked` trong `CH/data/FirestoreChatRepository.kt` (`setBlocked` ghi `participants/{mình}.blockedOther`); UseCase `CH/domain/usecase/RecallMessage.kt`, `ReportMessage.kt`, `SetBlocked.kt`
- [ ] T052 [US5] Menu giữ tin trong `ChatFragment` (hộp thoại xác nhận "Thu hồi"); `CH/ui/report/ReportMessageViewModel.kt`, `CH/ui/report/ReportMessageBottomSheet.kt` + `RES/layout/bottom_sheet_report_message.xml`; menu "Chặn"/"Bỏ chặn" chỉ ở chat riêng; hiển thị tin `RECALLED` và `HIDDEN`; trạng thái ô nhập khi bị chặn. Cho T048 xanh

**Checkpoint**: cả 5 user story chạy độc lập; quickstart Q12–Q14 đúng.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T053 Cài `anonymizeCommunityChats(uid)` trong `FN/_shared/chats.ts` theo [research R16](research.md#r16-người-dùng-xóa-tài-khoản-edge-cases-spec-011) kèm test **A1** trong `FN/_shared/chats.test.ts`: chat riêng có uid → `status = READ_ONLY`, `memberSnapshots.{uid} = null`; tin của uid (collection group `messages where senderId == uid`, theo lô) → `senderSnapshot = null`, `senderDeleted = true`; test thêm `chat-send` vào chat đó → `CHAT_READ_ONLY` (C6). Báo A gọi hàm này trong `delete-account` (sau `anonymizeCommunityGroups` của 090)
- [ ] T054 [P] Espresso `ATEST/PlayerOwnerChatFlowTest.kt` (Emulator + seed): người chơi mở chi tiết `V1` → "Nhắn tin cho chủ sân" (giả lập deep link) → gửi tin → đăng nhập `owner1` → "Tin nhắn khách hàng" thấy cuộc kèm tên `V1`
- [ ] T055 [P] Kiểm tra 4 trạng thái trên `ChatListFragment`, `ChatFragment`, `ReportMessageBottomSheet` (FR-017); mất mạng không crash, giữ nội dung đang soạn; chế độ tối; bố cục `w600dp` (bong bóng tin tối đa 70% chiều ngang); `contentDescription` cho ảnh, vùng chạm ≥ 48dp
- [ ] T056 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (mục 4.11, 4.12, 5, 6, 7, 8, 8a), **sau khi D đồng ý**
- [ ] T057 [P] Cập nhật `docs/features/09-community-chat/README.md` (link plan, tasks spec 091; trạng thái) và cột *Spec* nhóm 09 trong `docs/features/README.md`
- [ ] T058 Chạy toàn bộ [quickstart.md](quickstart.md): test tự động mục 2; Q1–Q16; đo SC-001, SC-002, SC-006; hỏi 5 người thử cho SC-007; chụp ảnh hoặc quay video minh chứng
- [ ] T059 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `./gradlew connectedDebugAndroidTest` (Emulator), `deno test -A supabase/functions`, `npx supabase test db`, `npm test` trong `firestore-tests/`; không còn cảnh báo Lint mức Error; ktlint sạch

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)** → **Foundational (Phase 2)**: chặn mọi user story. T008 cần 090-T006 (`_shared/groups.ts`).
- **US1 (Phase 3)**: sau Phase 2.
- **US2 (Phase 4)**: cần `chat-send` (T026) và màn hình chat (T029); test tích hợp T031 cần `group-manage` của 090 (090-T024, T033, T059).
- **US3 (Phase 5)**: cần danh sách (T030).
- **US4 (Phase 6)**: cần `chat-send` (T026) và outbox (T020); T045 cần 090-T047 nếu dùng chung `SignedUrlCache`.
- **US5 (Phase 7)**: cần US1.
- **Polish (Phase 8)**: T053 cần T008.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T001, T009 | **A:** `core/` của 010 và `_shared/auth.ts` (010-T021) | Làm theo task gốc của 010 rồi báo A |
| T030 | **A, spec 030:** nút "Nhắn tin cho chủ sân" mở `smashnow://chat/direct?venueId=` | Test bằng `adb shell am start` deep link; báo A thêm nút |
| T039 | **A:** `MainActivity` gắn huy hiệu qua `core/chat/UnreadChatsReader` | Interface và bản cài đã có; huy hiệu chưa hiện tới khi A nối |
| T053 | **A:** `delete-account` gọi `anonymizeCommunityChats(uid)` | Hàm sẵn trong `_shared/chats.ts`; báo A |
| T004, T026 | **B:** `REQUEST_TIME` và `increment` theo field path trong `_shared/firestore.ts` | Viết bản tối thiểu trong T004, báo B gộp |
| T011, T013, T056 | **D:** đồng ý `participants` và các trường **[MỚI]**, sửa mục 7 dòng `chats` | Code và seed vẫn theo data-model; chỉ hoãn T056 |
| T026 | **D, spec 100:** `send-notification` nhận `CHAT_MESSAGE` với `data = { chatId, messageId }` | Gửi sau commit, lỗi bỏ qua; test dùng mock |
| T030 | **D, spec 111/112:** mục "Tin nhắn khách hàng" trong menu chế độ chủ sân mở `smashnow://chat/owner` | Test bằng deep link; báo D |
| T052 | **D, spec 120:** ẩn tin bằng `status = HIDDEN` (nên ghi `text = ""`); `reportId` và `reason` chung với 090 | Seed tạo sẵn tin `HIDDEN` để test giao diện |
| T008, T031 | **C, spec 090:** `_shared/groups.ts` (090-T006), `group-manage` (090-T024, T033, T059), 090-T060 gọi `purgeChat` | Làm 090 Phase 2 trước; test tích hợp T031 chờ 090 US2 |
| T045 | **C, spec 090:** `SignedUrlCache` (090-T047), `ImageCompressor` (090-T020) | Tạo thẳng ở `core/` nếu 090 chưa làm, 090 dùng lại |

### Trong mỗi user story

Test (fail) → Edge Function → repository → UseCase → ViewModel (test xanh) → layout/Fragment.

### Cơ hội làm song song

- Phase 1: T002, T003.
- Phase 2: T005, T006, T012, T014, T015, T016, T019 khác file nhau.
- US1: T021–T024 song song; server T025–T026 song song với app T027–T030 khi đã có DTO (T016).
- US2: T031, T032 song song.
- US3: T036, T037 song song.
- US4, US5: test (T041–T042, T047–T048) viết song song sau khi có T026.

## Parallel Example: User Story 1

```bash
# Viết test trước, cùng lúc:
Task: "T021 Test openDirect D1 (x20), D2–D4 trong supabase/functions/chat-action/index.test.ts"
Task: "T022 Test chat-send C1, C2, C4, C7, C8 trong supabase/functions/chat-send/index.test.ts"
Task: "T023 Unit test ChatViewModel trong app/src/test/.../chat/ui/conversation/ChatViewModelTest.kt"
Task: "T024 Test Android outbox khi mất mạng trong app/src/androidTest/.../chat/data/ChatOutboxOfflineTest.kt"

# Sau đó, song song:
Task: "T025 Cài openDirect trong supabase/functions/chat-action/index.ts"
Task: "T029 Cài ChatFragment trong app/src/main/java/.../feature/chat/ui/conversation/"
```

## Implementation Strategy

### MVP (US1 + US2)

1. Xong Phase 1, 2 (sau Phase 2 của spec 090, vì cần `_shared/groups.ts`).
2. US1 (chat với chủ sân): **D1 xanh 20/20 lần**, test outbox mất mạng xanh.
3. US2 (chat nhóm): **C3 xanh 10/10 lần**.
4. **Dừng và kiểm tra**: quickstart Q1–Q7, Q11.

### Tăng dần

5. US3 (danh sách, chưa đọc, huy hiệu) → US4 (ảnh).
6. US5 (thu hồi, báo cáo, chặn) → sau khi D chốt quy ước với spec 120.
7. Polish → trước khi đóng băng tính năng (27/11).

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `chat` (ví dụ `feat(chat): chat-send trong transaction`).
- Ghi log AI vào `docs/ai-log/AI_log_3.md` cho mỗi lần dùng AI.
- Không commit `supabase/.env`, `local.properties`, `google-services.json`.
