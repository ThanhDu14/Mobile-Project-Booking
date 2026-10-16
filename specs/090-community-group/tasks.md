---

description: "Danh sách task triển khai spec 090 – Nhóm/CLB và bảng tin cộng đồng"
---

# Tasks: Nhóm/CLB và bảng tin cộng đồng

**Input**: tài liệu thiết kế trong `specs/090-community-group/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test:
- Security Rules trên Firebase Emulator (GR1–GR12) và chính sách Storage trên Supabase local (S1–S5); mỗi dòng phân quyền có ít nhất một `assertSucceeds` và một `assertFails`;
- Edge Function trên Emulator (G1–G15, P1–P11, M1–M3), gồm **G5: 20 người tham gia nhóm cùng lúc** và **P6: 20 người tham gia sự kiện cùng lúc**, mỗi ca lặp 10 lần;
- ViewModel, UseCase và logic phân quyền hiển thị.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US6)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CM/` | `app/src/main/java/com/example/booking_app/feature/community/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/community/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/feature/community/` |
| `FN/` | `supabase/functions/` |

Task của spec khác ghi là `010-T###`, `070-T###`, `081-T###`.

---

## Phase 1: Setup

**Purpose**: kiểm tra phần nền đã có, chỉ thêm phần riêng của nhóm 09. **Không tạo lại file đã có.**

- [ ] T001 Kiểm tra đã xong và dùng lại, không tạo lại:
  - 010-T001, 010-T002, 010-T004: version catalog, plugin, `APP/SmashNowApp.kt`;
  - 010-T012, 010-T015: `APP/core/session/UserProfileReader.kt`, `APP/core/di/FirebaseModule.kt`, `APP/core/di/SupabaseModule.kt` (070-T005 đã thêm plugin `Storage`);
  - 010-T021: `FN/_shared/auth.ts`;
  - 070-T006: `APP/core/ui/UiState.kt`, `RES/layout/include_state_views.xml`;
  - trên main (PR #22, #23): `firebase.json`, `firestore.rules` nền, `firestore-tests/` (Vitest, `test/setup.ts`), `supabase/config.toml`.

  Phần nào chưa có thì làm theo task gốc, không viết lại ở spec này
- [ ] T002 [P] Tạo `RES/values/strings_community.xml`, tiền tố `community_`: tiêu đề 10 màn hình ở [ui-screens.md](contracts/ui-screens.md#1-màn-hình); nhãn "Công khai", "Riêng tư", "Chủ nhóm", "Quản trị viên", "Thành viên", "Đang chờ duyệt", "Sự kiện", "Đã diễn ra", "Vượt số dự kiến", "Đã chỉnh sửa", "Đã bị ẩn", "Người dùng đã xóa", "Nhóm đã bị tạm khóa", "Từng bị mời ra"; plurals "N thành viên", "N/M người tham gia"; 5 lý do báo cáo; thông báo cho mọi mã lỗi ở [contract mục 5](contracts/edge-functions-community.md#5-mã-lỗi); màn hình trống "Hãy là người đầu tiên đăng bài", "Tạo nhóm của bạn"
- [ ] T003 [P] Viết `scripts/seed/090-community.ts` theo [quickstart mục 1](quickstart.md#1-chuẩn-bị): `player1`…`player25`, `admin1` (`appRole: ADMIN`); `G1` công khai (25 bài, 1 bài `hiddenBy = GROUP`, 1 bài `hiddenBy = SYSTEM`, 1 sự kiện sắp tới dự kiến 8, 1 sự kiện đã qua); `G2` riêng tư có 2 yêu cầu (1 `wasRemoved = true`); `G3` `LOCKED`; `player5` làm chủ 5 nhóm. Mọi document có đủ trường ở [data-model.md](data-model.md) (`nameKeywords`, `memberCount` khớp số `members`, `userSnapshot`)

**Checkpoint**: seed chạy được trên Emulator; app build được.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: hàm dùng chung phía server (có hook cho spec 091 và A), Rules, Storage, index, model, điều hướng.

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào.

### Server dùng chung

- [ ] T004 Kiểm tra `FN/_shared/firestore.ts` (B) có `runTransaction` (thử lại tối đa 8 lần, backoff 50–400 ms) và `runQueryInTx`. Nếu chưa có `runQueryInTx` thì dùng bản tối thiểu của 081-T005 (cùng hàm, không viết hai lần); nếu 081-T005 cũng chưa làm thì viết theo mô tả ở đó rồi báo B
- [ ] T005 [P] Viết test `FN/_shared/keywords.test.ts` rồi cài `FN/_shared/keywords.ts` `nameKeywords(name)`: chữ thường, bỏ dấu tiếng Việt (cả `đ` → `d`), tách từ, mọi tiền tố dài 1–20 ký tự của từng từ, không trùng ("CLB Cầu Lông Q7" → có `cau`, `long`, `q7`, `clb`). Nếu A đã có hàm sinh `venues.nameKeywords` thì dùng lại hàm đó, chỉ chuyển vào `_shared/keywords.ts` nếu đang nằm chỗ khác
- [ ] T006 Tạo `FN/_shared/groups.ts` theo [contract mục 4](contracts/edge-functions-community.md#4-hàm-dùng-chung-_sharedgroupsts), làm **sớm** để spec 091 và A dùng được:
  - `getMembership(tx, groupId, uid)` đọc `members/{uid}` trả `OWNER`, `ADMIN`, `MEMBER` hoặc `null`;
  - `onGroupMembershipChanged(tx, groupId, change)` **thân trống** (spec 091 cài), có JSDoc ghi rõ phải gọi trong cùng transaction;
  - `anonymizeCommunityGroups(uid)` khung trả `{ transferredGroups: [], deletedGroups: [] }` (cài thật ở T069);
  - `loadGroupForWrite(tx, groupId, { allowLocked })`: không có hoặc `DELETED` → `404 GROUP_NOT_FOUND`; `LOCKED` mà `allowLocked = false` → `423 GROUP_LOCKED`;
  - `userSnapshot(uid)` đọc `users/{uid}` trả `{ displayName, avatarPath }`.

  Kèm test `FN/_shared/groups.test.ts` cho `getMembership` và `loadGroupForWrite`
- [ ] T007 [P] Tạo `FN/_shared/groups_fixtures.ts` dùng chung cho mọi test Deno của spec này:
  - `seedGroup({ visibility, owner, admins, members, pending, removed, status })`, `seedPost({ type, eventAt, expectedCount, attendees, status, hiddenBy })`;
  - `assertGroupInvariant(groupId)`: `memberCount == số document members`; đúng một `members` có `role = OWNER` và trùng `groups.ownerId`; `pendingRequestCount == số joinRequests`; với mỗi sự kiện `attendeeCount == số attendees`;
  - mock `send-notification` ghi lại các lời gọi để test đếm; mock `onGroupMembershipChanged` ghi lại `change`.
- [ ] T008 Tạo khung 3 function `FN/group-manage/index.ts`, `FN/group-post/index.ts`, `FN/group-media-url/index.ts`: parse `action`, xác thực bằng `_shared/auth.ts`, kiểm tra `users/{uid}.accountStatus == ACTIVE` (`403 ACCOUNT_LOCKED`), router tới handler trống (`501`), body lỗi `{ error: { code, message, ...extra } }`, gửi thông báo sau commit qua helper `notifyGroupActivity(event, recipients, data)` (lỗi bỏ qua)

### Rules, Storage và index

- [ ] T009 Viết test Rules `firestore-tests/test/groups.test.ts` cho **GR1–GR12** theo [security-rules.md mục 2](contracts/security-rules.md#2-bảng-test-rules-mỗi-dòng-có-cả-ca-được-phép-và-ca-bị-chặn), cấu trúc giống `firestore-tests/test/base.test.ts` (`createTestEnv()`, `clearFirestore()` trong `beforeEach`, dữ liệu mẫu ghi bằng `withSecurityRulesDisabled`, vai trò qua claim `appRole`). Mỗi dòng phân quyền có ít nhất một `assertSucceeds` và một `assertFails`; dòng `write: if false` chỉ có ca bị chặn. Chạy `cd firestore-tests && npm test` thấy fail
- [ ] T010 Thêm phần `groups` vào `firestore.rules` đúng [bản phác](contracts/security-rules.md#1-bản-phác-rules): hàm `isMember()`, `myRole()`, `isMod()` trong `match /groups/{groupId}`; `members`, `joinRequests`, `removals`, `posts`, `attendees` chỉ đọc theo bảng; mọi `write: if false`; collection group `members` chỉ `list` khi `resource.data.userId == request.auth.uid`. Dùng lại `isSignedIn()`, `isSelf()`, `isAdmin()` của Rules nền. Sau đó T009 phải xanh
- [ ] T011 [P] Viết test pgTAP `supabase/tests/group_buckets.test.sql` cho **S1–S5** ở [security-rules.md mục 3](contracts/security-rules.md#3-storage) (đặt `request.jwt.claims` với `sub`, `role = authenticated`); chạy `npx supabase test db` thấy fail
- [ ] T012 Tạo migration `supabase/migrations/<timestamp>_group_buckets.sql`: bucket `group-media` (`public = false`) và `group-avatars` (`public = true`), cả hai `file_size_limit = 2097152`, `allowed_mime_types = {image/jpeg,image/png,image/webp}`; **không** tạo policy `select`/`insert`/`update`/`delete` cho `authenticated`, `anon` trên `storage.objects` của hai bucket này (chỉ signed URL). Nếu D đã tạo `group-media` thì chỉ thêm `group-avatars`. Sau đó T011 phải xanh
- [ ] T013 [P] Thêm vào `firestore.indexes.json` 12 index ở [data-model mục 9](data-model.md#9-index-cần-thêm-vào-firestoreindexesjson) (`groups` 5 index, `members` 2, `posts` 3, `attendees` 1, `joinRequests` 1)

### Domain và data dùng chung (app)

- [ ] T014 [P] Tạo model trong `CM/domain/model/` đúng [data-model mục 10](data-model.md#10-mô-hình-phía-app-kotlin-featurecommunitydomain): `Group`, `GroupMembership`, `GroupMember`, `JoinRequest`, `Post`, `EventInfo` (`isPast(now)` khi `at ≤ now`, `isOverExpected` khi `attendeeCount > expectedCount`), `PostDraft`, `DraftPhoto` (`PENDING`, `UPLOADING`, `UPLOADED`, `FAILED`); enum `GroupVisibility` (`PUBLIC`, `PRIVATE`), `GroupRole` (`OWNER`, `ADMIN`, `MEMBER`), `GroupStatus` (`ACTIVE`, `LOCKED`, `DELETED`), `PostType` (`POST`, `EVENT`), `PostStatus` (`VISIBLE`, `HIDDEN`), `HiddenBy` (`GROUP`, `SYSTEM`), `ReportReason` (`SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER`). Thời gian là `Instant`
- [ ] T015 [P] Unit test `TEST/domain/GroupPermissionsTest.kt` theo bảng [data-model mục 8](data-model.md#8-quy-tắc-kiểm-tra-chính): `canEdit` chỉ `OWNER`; `canManageRoles` chỉ `OWNER`; `canRemove(target)`: `OWNER` mời mọi người trừ mình, `ADMIN` chỉ mời `MEMBER`; `canModerate` `OWNER`/`ADMIN`; `canUnhide` chỉ khi `hiddenBy == GROUP`; `canReport(post)` khi không phải bài của mình; `canEditPost` chỉ người đăng; nhóm `LOCKED` thì mọi quyền ghi trừ rời nhóm là `false`
- [ ] T016 Cài `CM/domain/GroupPermissions.kt` cho T015 xanh (chỉ dùng để ẩn/hiện nút; server vẫn kiểm tra)
- [ ] T017 [P] Tạo `CM/data/dto/GroupDto.kt`, `MemberDto.kt`, `JoinRequestDto.kt`, `PostDto.kt`, `AttendeeDto.kt`, `EdgeDtos.kt` (`@Serializable` request/response cho mọi action ở [contract](contracts/edge-functions-community.md)) và `CM/data/dto/Mappers.kt`; `CM/domain/CommunityError.kt` ánh xạ đủ mã lỗi ở [contract mục 5](contracts/edge-functions-community.md#5-mã-lỗi) (`OwnerMustTransfer(soleMember)`, `Busy`, `Network`, `Unknown`…)
- [ ] T018 Tạo interface `CM/domain/GroupRepository.kt` (`observeGroup`, `observeMembership`, `observeMyGroups`, `search`, `create`, `update`, `delete`, `join`, `cancelRequest`, `leave`, `approve`, `reject`, `setRole`, `transferOwner`, `remove`, `observeMembers`, `observeJoinRequests`), `CM/domain/PostRepository.kt` (`feed(groupId, isMod, cursor)`, `observeNewPosts`, `create`, `update`, `delete`, `hide`, `unhide`, `attend`, `unattend`, `observeAttendees`, `report`), `CM/domain/GroupMediaRepository.kt` (`requestUploads`, `upload(uri, signedUrl): Flow<DraftPhoto>`, `signedUrls(paths)`, `discard`, `uploadAvatar`)
- [ ] T019 Tạo `CM/data/di/CommunityModule.kt` (Hilt bind repository) và nav graph lồng `RES/navigation/nav_community.xml` với 10 destination và Safe Args ở [ui-screens.md mục 1](contracts/ui-screens.md#1-màn-hình); `include` vào graph chính của 010-T016; deep link `smashnow://community/group/{groupId}` và `smashnow://community/group/{groupId}/requests`; thêm mục "Cộng đồng" vào điều hướng chính (thống nhất vị trí với A)
- [ ] T020 [P] Đưa phần nén ảnh lên `APP/core/image/ImageCompressor.kt` (cạnh dài ≤ 1600 px, JPEG chất lượng 80, > 2 MB sau nén thì lỗi) để 070 và 090 dùng chung, không import chéo `feature/review` (constitution I). Nếu 070-T043 đã viết trong `feature/review/data/ReviewPhotoUploader.kt` thì tách phần nén ra, giữ nguyên chữ ký của `ReviewPhotoUploader`; unit test `app/src/test/java/com/example/booking_app/core/image/ImageCompressorTest.kt`

**Checkpoint**: GR1–GR12, S1–S5 xanh; `_shared/groups.ts` import được; app build được với `nav_community`.

---

## Phase 3: User Story 1 – Tạo nhóm/CLB (Priority: P1) 🎯 MVP

**Goal**: người chơi tạo nhóm công khai hoặc riêng tư (tên, mô tả, ảnh, khu vực), trở thành chủ nhóm; chủ nhóm sửa được thông tin nhóm.

**Independent Test**: tạo một nhóm công khai và một nhóm riêng tư; cả hai hiện trong "Nhóm của tôi" với vai trò chủ nhóm, số thành viên là 1, thông tin đúng như đã nhập (quickstart Q1–Q3; G1–G4).

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T021 [P] [US1] Test Deno `FN/group-manage/index.test.ts` (fixture T007):
  - **G1:** `create` hợp lệ → `members/{uid}.role = OWNER`, `memberCount = 1`, `pendingRequestCount = 0`, `status = ACTIVE`, `nameKeywords` đúng; hook nhận `{ created: true, added: [uid] }` đúng 1 lần;
  - **G2:** cùng `requestId` 5 lần (cả song song) → 1 nhóm, 4 lần sau `200`;
  - **G3:** người đã làm chủ 5 nhóm → `409 OWNER_LIMIT_REACHED`;
  - **G4:** tên 2 và 51 ký tự (sau trim), mô tả 501 ký tự, `districtId` không có, `visibility` lạ → `400 INVALID_ARGUMENT`;
  - `hasAvatar: true` → có `avatarUpload` vào `group-avatars/{groupId}/avatar.*`; `update avatar = CONFIRM` khi object chưa có → `PHOTO_MISSING`, khi đã có → ghi `avatarPath`; `REMOVE` → `avatarPath = null` và xóa object;
  - `update` bởi chủ nhóm → đổi tên thì `nameKeywords` đổi theo; bởi thành viên thường → `403 FORBIDDEN` (AC 1.5);
  - sau mỗi ca gọi `assertGroupInvariant`.
- [ ] T022 [P] [US1] Unit test `TEST/ui/form/GroupFormViewModelTest.kt` (MockK + Turbine): lỗi từng ô (tên trống, < 3, > 50 ký tự; mô tả > 500; chưa chọn khu vực) (AC 1.2); `requestId` giữ qua `SavedStateHandle` khi thử lại (AC 1.6); tạo xong mà tải ảnh lỗi → `AvatarUploadFailed`, giữ form, "Thử lại" hoặc "Dùng ảnh mặc định" (AC 1.3); `OwnerLimitReached` → thông báo, không điều hướng (AC 1.4); `Network` giữ nội dung; thành công → sự kiện mở `GroupDetailFragment`; chế độ sửa nạp sẵn dữ liệu nhóm
- [ ] T023 [P] [US1] Unit test `TEST/ui/home/CommunityHomeViewModelTest.kt`: "Nhóm của tôi" `Loading → Content` (vai trò, số thành viên), bỏ nhóm `DELETED`, nhóm `LOCKED` có nhãn; `Empty` có nút "Tạo nhóm"; `Error` + thử lại

### Implementation cho User Story 1

- [ ] T024 [US1] Cài `create` và `update` (trừ phần tự duyệt khi đổi sang công khai, làm ở T034) trong `FN/group-manage/index.ts` theo [contract mục 1](contracts/edge-functions-community.md#1-group-manage) và [data-model mục 1–2](data-model.md#1-groupsgroupid):
  - `create` trong `runTransaction`: đọc `groups/{requestId}` (có và cùng `ownerId` → trả cũ); đếm nhóm `ownerId == uid, status in [ACTIVE, LOCKED]` bằng `runQueryInTx` (≥ 5 → `OWNER_LIMIT_REACHED`); ghi `groups/{requestId}` (`name` 3–50 ký tự, `description` 0–500, `districtId` có trong `districts`, `visibility` `PUBLIC`/`PRIVATE`, `nameKeywords`, `ownerId`, `memberCount = 1`, `pendingRequestCount = 0`, `status = ACTIVE`, `avatarPath = null`) và `members/{uid}` (`userId`, `groupId`, `role = OWNER`, `userSnapshot`, `joinedAt`); gọi `onGroupMembershipChanged(tx, groupId, { created: true, added: [uid] })`;
  - cấp signed upload URL `group-avatars/{groupId}/avatar.{ext}` khi `hasAvatar`;
  - `update` (chỉ `OWNER`): đổi trường, sinh lại `nameKeywords`; xử lý `avatar` `KEEP`/`REPLACE`/`CONFIRM`/`REMOVE`.

  Sau đó T021 phải xanh
- [ ] T025 [US1] Cài `create`, `update`, `observeGroup`, `observeMyGroups` (collection group `members where userId == uid orderBy joinedAt desc`, đọc nhóm theo lô ≤ 10 bằng `whereIn(documentId)`) trong `CM/data/FirestoreGroupRepository.kt`; gọi `supabase.functions.invoke("group-manage")`, làm mới token và thử lại 1 lần khi `401`, `Busy` tự gửi lại cùng `requestId` tối đa 2 lần. Cài `uploadAvatar` trong `CM/data/SupabaseGroupMediaRepository.kt` (nén bằng `ImageCompressor`, PUT lên signed URL)
- [ ] T026 [US1] Cài UseCase `CM/domain/usecase/CreateGroup.kt` (tạo → tải ảnh → `update avatar = CONFIRM`; ảnh lỗi trả `AvatarUploadFailed` kèm `groupId`), `UpdateGroup.kt`, `ObserveMyGroups.kt`
- [ ] T027 [US1] Cài `CM/ui/form/GroupFormViewModel.kt`, `CM/ui/form/GroupFormFragment.kt` + `RES/layout/fragment_group_form.xml`: ô tên (đếm ký tự 50), mô tả (500), chọn ảnh bằng Photo Picker (`PickVisualMedia`), chọn khu vực (danh mục `districts`), chế độ Công khai/Riêng tư kèm giải thích; nút khóa khi đang gửi. Cho T022 xanh
- [ ] T028 [US1] Cài `CM/ui/home/CommunityHomeViewModel.kt`, `CM/ui/home/CommunityHomeFragment.kt` + `RES/layout/fragment_community_home.xml` (tab "Nhóm của tôi" và "Khám phá", nút "Tạo nhóm") và `CM/ui/search/GroupAdapter.kt` + `RES/layout/item_group.xml` (ảnh từ `group-avatars` hoặc ảnh mặc định, tên, khu vực, số thành viên, nhãn chế độ). Theo wireframe `ui/09_community-chat_buoc1_nhom.png`. Cho T023 xanh
- [ ] T029 [US1] Tạo khung `CM/ui/detail/GroupDetailViewModel.kt`, `CM/ui/detail/GroupDetailFragment.kt` + `RES/layout/fragment_group_detail.xml`: phần giới thiệu (ảnh, tên, mô tả, khu vực, số thành viên, chế độ), nút "Sửa nhóm" chỉ khi `GroupPermissions.canEdit`; chừa vùng nút tham gia (US2) và bảng tin (US3)

**Checkpoint**: US1 chạy độc lập; G1–G4 xanh; quickstart Q1–Q3 đúng.

---

## Phase 4: User Story 2 – Tìm và tham gia nhóm (Priority: P1)

**Goal**: tìm nhóm theo tên và khu vực, vào nhóm công khai ngay, xin vào nhóm riêng tư và được duyệt; rời nhóm. **Số thành viên luôn đúng khi nhiều người tham gia cùng lúc.**

**Independent Test**: tài khoản B tìm thấy nhóm công khai của A theo tên và theo khu vực, tham gia và thấy ngay bảng tin; B xin vào nhóm riêng tư của A, A duyệt, B thành thành viên; số thành viên mỗi nhóm tăng đúng 1 (quickstart Q4–Q6; G5–G9, G13).

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T030 [P] [US2] Bổ sung vào `FN/group-manage/index.test.ts`:
  - **G5:** nhóm công khai, **20 tài khoản gọi `join` song song (`Promise.all`), lặp 10 lần** (mỗi lần seed lại) → `memberCount` = trước + 20, đúng 20 document `members` mới, hook nhận đủ 20 uid trong `added`, không ai bị tính hai lần; in `runs=10 expected=20 actual=20 duplicates=0` (SC-003);
  - **G6:** nhóm riêng tư `join` → `REQUESTED`, `pendingRequestCount` +1, gửi `JOIN_REQUESTED` cho chủ nhóm và quản trị viên; `approve` → thành viên, `memberCount` +1, `pendingRequestCount` −1, gửi `JOIN_APPROVED`; `join` → `reject` → yêu cầu bị xóa, gửi `JOIN_REJECTED`; `cancelRequest` → yêu cầu bị xóa, không gửi thông báo;
  - **G7:** `approve` cùng một người 2 lần song song → `memberCount` tăng đúng 1;
  - **G8:** người có `removals/{uid}` gọi `join` nhóm công khai → `REQUESTED`, `wasRemoved = true`;
  - **G9:** chủ nhóm `leave` khi còn người → `409 OWNER_MUST_TRANSFER` (`soleMember: false`); khi chỉ còn một mình → `soleMember: true`; thành viên `leave` → `memberCount` −1, hook nhận `removed`;
  - **G13:** chủ nhóm đổi `PRIVATE → PUBLIC` khi có 3 yêu cầu (1 người có `removals`) → 2 người thành thành viên, 1 vẫn chờ, `memberCount` +2, `autoApproved = 2`, 2 thông báo `JOIN_APPROVED`;
  - `join` lặp → `ALREADY_MEMBER` / `ALREADY_REQUESTED`; `approve` khi là thành viên thường → `FORBIDDEN`;
  - sau mỗi ca gọi `assertGroupInvariant`.
- [ ] T031 [P] [US2] Unit test `TEST/ui/search/GroupSearchViewModelTest.kt`: chuẩn hóa từ khóa (bỏ dấu, chữ thường, lấy từ đầu tiên làm `array-contains`, lọc thêm ở client theo các từ còn lại); điền sẵn khu vực từ `UserProfileReader.favoriteDistrictIds` (AC 2.1); debounce 300 ms; tải trang tiếp 20; `Empty(hasFilter)` có "Bỏ bớt bộ lọc" và "Tạo nhóm của bạn" (AC 2.6)
- [ ] T032 [P] [US2] Unit test `TEST/ui/detail/GroupDetailMembershipTest.kt` và `TEST/ui/members/JoinRequestsViewModelTest.kt`: nút theo `GroupMembership` ("Tham gia" cho công khai, "Xin tham gia" cho riêng tư hoặc người từng bị mời ra, "Đang chờ duyệt" + "Hủy yêu cầu", ẩn khi đã là thành viên); khách chưa đăng nhập bấm tham gia → sự kiện mở đăng nhập kèm `returnTo`; chủ nhóm bấm "Rời nhóm" → `OwnerMustTransfer` mở chọn người nhận quyền (hoặc gợi ý xóa nhóm khi `soleMember`); duyệt/từ chối lạc quan, lỗi thì hoàn lại

### Implementation cho User Story 2

- [ ] T033 [US2] Cài `join`, `cancelRequest`, `approve`, `reject`, `leave` trong `FN/group-manage/index.ts` theo [data-model mục 8](data-model.md#8-quy-tắc-kiểm-tra-chính) và [research R2, R3, R11](research.md#r3-số-thành-viên-và-số-người-tham-gia-sự-kiện-đúng-khi-nhiều-người-thao-tác-cùng-lúc-fr-003-fr-016-sc-003):
  - mọi thao tác chạy trong `runTransaction`, đọc và ghi `groups/{groupId}` (`memberCount`, `pendingRequestCount`) để tranh chấp đúng một document;
  - `join`: `PUBLIC` và không có `removals/{uid}` → tạo `members/{uid}` (`role = MEMBER`), `memberCount += 1`, hook `added`; còn lại → tạo `joinRequests/{uid}` (`wasRemoved`, `requestId`, `userSnapshot`), `pendingRequestCount += 1`;
  - `approve`: xóa `joinRequests/{uid}` và `removals/{uid}`, tạo `members/{uid}`, cập nhật 2 bộ đếm, hook `added`;
  - `leave`: chặn `OWNER` (`OWNER_MUST_TRANSFER`, `soleMember = memberCount == 1`); xóa `members/{uid}`, `memberCount -= 1`, hook `removed` (bỏ tham gia sự kiện làm ở T059);
  - gửi thông báo sau commit theo [research R15](research.md#r15-thông-báo-fr-021).

  Sau đó G5–G9 trong T030 phải xanh (**G5 10/10 lần**)
- [ ] T034 [US2] Cài phần tự duyệt khi `update` đổi `PRIVATE → PUBLIC` theo [research R12](research.md#r12-đổi-chế-độ-nhóm-edge-cases): trong cùng transaction lấy `joinRequests` bằng `runQueryInTx`, bỏ qua người có `removals`, chuyển thành `members`; > 200 yêu cầu → `409 TOO_MANY_PENDING`. Sau đó G13 phải xanh
- [ ] T035 [US2] Cài `search` (truy vấn ở [research R5](research.md#r5-tìm-nhóm-theo-tên-và-khu-vực-fr-005): `status == ACTIVE`, `nameKeywords array-contains`, `districtId ==` (một quận; chọn nhiều quận thì `whereIn` ≤ 10), `orderBy memberCount desc`, `limit 20`, `startAfter`), `observeMembership` (gộp `members/{uid}` và `joinRequests/{uid}`), `join`, `cancelRequest`, `leave`, `approve`, `reject`, `observeJoinRequests` trong `CM/data/FirestoreGroupRepository.kt`
- [ ] T036 [US2] Cài UseCase `CM/domain/usecase/SearchGroups.kt`, `JoinGroup.kt`, `LeaveGroup.kt`, `ReviewJoinRequest.kt`
- [ ] T037 [US2] Cài `CM/ui/search/GroupSearchViewModel.kt`, `CM/ui/search/GroupSearchFragment.kt` + `RES/layout/fragment_group_search.xml` (ô tìm, chip khu vực, danh sách dùng `GroupAdapter`, tải thêm khi cuộn). Cho T031 xanh
- [ ] T038 [US2] Bổ sung `GroupDetailViewModel`/`GroupDetailFragment`: nút tham gia theo `GroupMembership`, "Rời nhóm" (hộp thoại xác nhận), khách chưa đăng nhập chuyển sang đăng nhập (spec 010) rồi quay lại; người ngoài chỉ thấy giới thiệu, không thấy bảng tin (AC 3.7). Cho T032 (phần nút) xanh
- [ ] T039 [US2] Cài `CM/ui/members/JoinRequestsViewModel.kt`, `CM/ui/members/JoinRequestsFragment.kt` + `RES/layout/fragment_join_requests.xml`, `RES/layout/item_join_request.xml` (tên, ảnh, thời gian, nhãn "Từng bị mời ra", nút Duyệt/Từ chối); nút "Yêu cầu tham gia (N)" trên trang nhóm chỉ khi `canModerate`. Cho T032 xanh

**Checkpoint**: US1 + US2 là MVP; **G5 xanh 10/10 lần**; quickstart Q4–Q6 đúng.

---

## Phase 5: User Story 3 – Đăng bài trên bảng tin nhóm (Priority: P2)

**Goal**: thành viên đăng bài chữ kèm tối đa 4 ảnh; bài chỉ hiện khi mọi ảnh đã tải xong; người đăng sửa/xóa bài; bảng tin chỉ thành viên xem.

**Independent Test**: thành viên đăng bài có 2 ảnh; thành viên khác thấy bài ở đầu bảng tin với đủ ảnh; người đăng sửa (hiện "Đã chỉnh sửa") rồi xóa, bài và ảnh biến mất (quickstart Q5, Q7, Q8; P1–P4, M1–M2).

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T040 [P] [US3] Test Deno `FN/group-media-url/index.test.ts`:
  - **M1:** `upload` khi không phải thành viên → `NOT_MEMBER`; `size` 3 MB → `INVALID_ARGUMENT`; `image/gif` → `INVALID_ARGUMENT`; 5 file → `INVALID_ARGUMENT`; hợp lệ → path đúng `{groupId}/{postId}/{index}.{ext}`, tải lên được bằng signed URL;
  - **M2:** `download` 3 path, 1 path thuộc nhóm không là thành viên → trả đúng 2 URL; 41 path → `INVALID_ARGUMENT`;
  - `discard` khi chưa có `posts/{postId}` → xóa ảnh; khi bài đã tạo → không xóa gì.
- [ ] T041 [P] [US3] Test Deno `FN/group-post/index.test.ts`:
  - **P1:** `create` `POST` 2 ảnh đã tải → `VISIBLE`, `photoPaths` đúng thứ tự, `authorSnapshot` có; 1 ảnh chưa tải → `PHOTO_MISSING`, không có document; path sai tiền tố `{groupId}/{postId}/` → `INVALID_ARGUMENT`;
  - `content` 0 và 2001 ký tự, 5 ảnh → `INVALID_ARGUMENT`;
  - **P2:** không phải thành viên `create` → `NOT_MEMBER`; thành viên `update`/`delete` bài người khác → `FORBIDDEN`; chủ nhóm sửa nội dung bài người khác → `FORBIDDEN` (FR-013);
  - **P3:** `create` cùng `requestId` 3 lần → 1 bài;
  - **P4:** `delete` bài có ảnh → document và ảnh trong `group-media` đều mất; `update` bỏ 1 ảnh → ảnh đó bị xóa khỏi Storage, `editedAt` có giá trị.
- [ ] T042 [P] [US3] Unit test `TEST/ui/composer/PostComposerViewModelTest.kt`: chọn ảnh thứ 5 → báo tối đa 4 (AC 3.2); trạng thái từng ảnh; ảnh lỗi → "Thử lại"/"Bỏ ảnh", nút "Đăng" khóa tới khi không còn ảnh `UPLOADING`/`FAILED` (AC 3.3); `requestId` (= `postId`) giữ qua `SavedStateHandle`; thoát khi có nội dung → hỏi "Bỏ bài đang viết?", đồng ý thì gọi `discard`; `Network` giữ nội dung
- [ ] T043 [P] [US3] Unit test `TEST/ui/detail/GroupFeedTest.kt` (phần bảng tin của `GroupDetailViewModel`): thành viên thường gộp bài `VISIBLE` với bài `HIDDEN` của chính mình (nhãn "Đã bị ẩn"), sắp `createdAt desc`; quản trị viên thấy mọi bài; tải trang 20; `Empty` "Hãy là người đầu tiên đăng bài" (AC 3.6); bài mới từ listener chèn lên đầu; tác giả đã xóa → "Người dùng đã xóa"
- [ ] T044 [P] [US3] Unit test `TEST/data/SignedUrlCacheTest.kt`: trả URL đã cache khi chưa hết hạn (trừ 5 phút an toàn); gom path còn thiếu thành một lời gọi ≤ 40 path; hết hạn thì xin lại

### Implementation cho User Story 3

- [ ] T045 [US3] Cài `FN/group-media-url/index.ts` theo [contract mục 3](contracts/edge-functions-community.md#3-group-media-url): kiểm tra thành viên bằng `getMembership`; `upload` dùng `createSignedUploadUrl` (service role); `download` dùng `createSignedUrls` hết hạn 3600 giây; `discard` chỉ khi chưa có `posts/{postId}`. Sau đó T040 phải xanh
- [ ] T046 [US3] Cài `create`, `update`, `delete` cho bài `POST` trong `FN/group-post/index.ts` theo [research R9](research.md#r9-bài-đăng-chỉ-hiện-khi-mọi-ảnh-đã-tải-lên-fr-011-ac-33): `postId = requestId`; kiểm tra thành viên (`loadGroupForWrite`); `content` 1–2000 ký tự; `photoPaths` 0–4, đúng tiền tố, object tồn tại (`storage.list`); ghi `groupId`, `authorId`, `authorSnapshot`, `authorDeleted = false`, `type`, `status = VISIBLE`; `update` chỉ người đăng, ghi `editedAt`, xóa ảnh bị bỏ; `delete` chỉ người đăng, xóa document và ảnh. Sau đó T041 phải xanh
- [ ] T047 [US3] Cài `requestUploads`, `upload`, `signedUrls`, `discard` trong `CM/data/SupabaseGroupMediaRepository.kt` (nén bằng `ImageCompressor`, PUT lên signed URL, phát trạng thái từng ảnh) và `CM/data/SignedUrlCache.kt`. Cho T044 xanh
- [ ] T048 [US3] Cài `feed` (thành viên: `where status == VISIBLE orderBy createdAt desc limit 20` + `where authorId == uid, status == HIDDEN`; quản trị viên: không lọc `status`), `observeNewPosts`, `create`, `update`, `delete` trong `CM/data/FirestorePostRepository.kt`; UseCase `CM/domain/usecase/UploadPostPhotos.kt`, `PublishPost.kt`, `EditPost.kt`, `DeletePost.kt`
- [ ] T049 [US3] Cài `CM/ui/composer/PostComposerViewModel.kt`, `CM/ui/composer/PostComposerFragment.kt` + `RES/layout/fragment_post_composer.xml` (ô nội dung đếm 2000 ký tự, lưới ảnh có trạng thái, Photo Picker `PickMultipleVisualMedia(4)`); chế độ sửa nạp sẵn bài. Cho T042 xanh
- [ ] T050 [US3] Bổ sung bảng tin vào `GroupDetailFragment`: `CM/ui/detail/PostAdapter.kt` (`ListAdapter` + `DiffUtil`) + `RES/layout/item_post.xml` (ảnh và tên người đăng, thời gian, nội dung, lưới ảnh dùng URL từ `SignedUrlCache`, nhãn "Đã chỉnh sửa", "Đã bị ẩn"); bấm ảnh mở `PhotoViewerFragment` (dùng lại của 070 nếu đã ở `core/ui`, nếu chưa thì tạo `CM/ui/detail/PhotoViewerFragment.kt`); nút "Đăng bài"; menu "Sửa"/"Xóa" cho bài của mình. Theo wireframe `ui/09_community-chat_buoc2_bang-tin-nhom.png`. Cho T043 xanh

**Checkpoint**: US3 chạy độc lập; P1–P4, M1–M2 xanh; quickstart Q5, Q7, Q8 đúng.

---

## Phase 6: User Story 4 – Sự kiện giao lưu (Priority: P2)

**Goal**: thành viên đăng sự kiện; thành viên khác "Tham gia sự kiện"/"Bỏ tham gia"; số người tham gia đúng khi nhiều người bấm cùng lúc.

**Independent Test**: A đăng sự kiện thứ Bảy 19:00, dự kiến 8 người; B và C tham gia; mọi thành viên thấy "2/8 người tham gia" và tên B, C; B bỏ tham gia thì còn 1 (quickstart Q9; P5–P8).

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T051 [P] [US4] Bổ sung vào `FN/group-post/index.test.ts`:
  - **P5:** `EVENT` thiếu `eventTitle` hoặc `eventLocation`, `eventTitle` 101 ký tự, `eventLocation` 201 ký tự, `eventAt` quá khứ, `expectedCount` 1 và 101 → `INVALID_ARGUMENT`; hợp lệ → `attendeeCount = 0`, gửi `EVENT_CREATED` cho mọi thành viên trừ người đăng;
  - **P6:** **20 thành viên gọi `attend` song song, lặp 10 lần** → `attendeeCount` = 20, đúng 20 document `attendees`; 1 người bấm `attend` 5 lần → tính 1; in `runs=10 expected=20 actual=20 duplicates=0` (SC-003, FR-016);
  - **P7:** `attend` khi `attendeeCount == expectedCount` → thành công (`attendeeCount > expectedCount`); sự kiện đã qua → `409 EVENT_PAST`; không phải thành viên → `NOT_MEMBER`;
  - **P8:** `update` đổi `eventAt` → `editedAt` có giá trị, mọi `attendees.eventAt` cập nhật, gửi `EVENT_UPDATED` cho người tham gia trừ người sửa; `update` chỉ đổi mô tả → không gửi; `delete` sự kiện chưa diễn ra → xóa `attendees`, gửi `EVENT_DELETED`;
  - sau mỗi ca gọi `assertGroupInvariant`.
- [ ] T052 [P] [US4] Unit test `TEST/domain/EventInfoTest.kt` (`isPast` đúng tại mốc `eventAt`, `isOverExpected`) và bổ sung `PostComposerViewModelTest`: chế độ `EVENT` kiểm tra tiêu đề (bắt buộc, ≤ 100), thời gian trong tương lai, địa điểm (bắt buộc, ≤ 200), số dự kiến 2–100 (AC 4.2); `TEST/ui/event/EventAttendeesViewModelTest.kt`: danh sách tên, `Empty`

### Implementation cho User Story 4

- [ ] T053 [US4] Cài nhánh `EVENT` trong `create`, `update`, `delete` của `FN/group-post/index.ts` theo [data-model mục 5–6](data-model.md#5-groupsgroupidpostspostid): `eventTitle` 1–100 ký tự, `eventAt` > giờ server, `eventLocation` 1–200 ký tự, `expectedCount` 2–100, `content` (mô tả) 0–2000; `attendeeCount = 0`; sửa giờ thì cập nhật `eventAt` của mọi `attendees` trong cùng transaction; thông báo `EVENT_CREATED`, `EVENT_UPDATED`, `EVENT_DELETED` sau commit
- [ ] T054 [US4] Cài `attend`, `unattend` trong `FN/group-post/index.ts`: trong `runTransaction` đọc bài và `attendees/{uid}`; bài phải `EVENT`, `VISIBLE`, `eventAt > now` (`EVENT_PAST`); đã có thì trả kết quả cũ; ghi `attendees/{uid}` (`userId`, `groupId`, `postId`, `eventAt`, `userSnapshot`, `joinedAt`) và `attendeeCount ± 1` trên document bài. Sau đó T051 phải xanh (**P6 10/10 lần**)
- [ ] T055 [US4] Cài `attend`, `unattend`, `observeAttendees` trong `CM/data/FirestorePostRepository.kt` và UseCase `CM/domain/usecase/ToggleAttendance.kt` (cập nhật lạc quan, lỗi thì hoàn lại)
- [ ] T056 [US4] Thêm `RES/layout/item_event.xml` vào `PostAdapter` (nhãn "Sự kiện", tiêu đề, thời gian theo `Asia/Ho_Chi_Minh`, địa điểm, "N/M người tham gia", nhãn "Vượt số dự kiến"/"Đã diễn ra", nút "Tham gia sự kiện"/"Bỏ tham gia" ẩn khi đã diễn ra); trường sự kiện trong `PostComposerFragment` (chọn ngày giờ, địa điểm, số dự kiến); nút "Tạo sự kiện" trên trang nhóm; `CM/ui/event/EventAttendeesBottomSheet.kt` + `RES/layout/bottom_sheet_event_attendees.xml`. Cho T052 xanh

**Checkpoint**: US4 chạy độc lập; **P6 xanh 10/10 lần**; quickstart Q9 đúng.

---

## Phase 7: User Story 5 – Quản lý thành viên (Priority: P2)

**Goal**: chủ nhóm phong/gỡ quản trị viên, chuyển quyền chủ nhóm, mời thành viên ra, xóa nhóm; quản trị viên mời thành viên thường ra.

**Independent Test**: chủ nhóm A phong B làm quản trị viên; B mời C (thành viên thường) ra; B không mời được A; A chuyển quyền cho B rồi rời nhóm; B là chủ nhóm mới (quickstart Q10, Q11, Q14; G10–G12, G14, G15).

### Tests cho User Story 5 ⚠️ viết trước, phải fail

- [ ] T057 [P] [US5] Bổ sung vào `FN/group-manage/index.test.ts`:
  - **G10:** quản trị viên `remove` chủ nhóm / quản trị viên khác → `FORBIDDEN`; `remove` thành viên thường → xóa `members`, tạo `removals`, `memberCount` −1, hook `removed`, gửi `REMOVED`;
  - **G11:** thành viên thường `setRole`, `update`, `delete`, `approve`, `transferOwner` → `FORBIDDEN`; quản trị viên `setRole` → `FORBIDDEN`;
  - **G12:** `transferOwner` → `groups.ownerId` đổi, người nhận `OWNER`, chủ cũ `ADMIN`; sau đó chủ cũ `leave` thành công;
  - **G14:** `delete` gõ sai tên → `CONFIRM_NAME_MISMATCH`; đúng tên → `status = DELETED` ngay (không còn trong tìm kiếm), hook `deleted`, rồi hết `posts`, `attendees`, `members`, `joinRequests`, `removals`, ảnh trong `group-media` và `group-avatars`, cuối cùng hết document nhóm; giả lập lỗi giữa chừng rồi gọi lại `delete` → chạy tiếp tới hết;
  - **G15:** thành viên đang tham gia 1 sự kiện sắp tới và 1 sự kiện đã qua `leave` (và trường hợp bị `remove`) → bị bỏ khỏi sự kiện sắp tới (`attendeeCount` −1), giữ sự kiện đã qua;
  - sau mỗi ca gọi `assertGroupInvariant`.
- [ ] T058 [P] [US5] Unit test `TEST/ui/members/GroupMembersViewModelTest.kt`: sắp chủ nhóm, quản trị viên, rồi thành viên theo `joinedAt` (AC 5.1); menu theo `GroupPermissions` của người xem; thao tác thành công làm mới danh sách; lỗi `FORBIDDEN` hiện thông báo; xóa nhóm yêu cầu gõ đúng tên

### Implementation cho User Story 5

- [ ] T059 [US5] Cài `setRole`, `transferOwner`, `remove` trong `FN/group-manage/index.ts` theo bảng [research R11](research.md#r11-vai-trò-và-các-thao-tác-quản-lý-thành-viên-fr-007-fr-009); `remove` ghi `removals/{uid}` (`removedBy`, `removedAt`). Cả `remove` và `leave` (T033) trong cùng transaction bỏ người đó khỏi `attendees` của các sự kiện chưa diễn ra trong nhóm (collection group `attendees where userId == uid, groupId == g, eventAt > now` bằng `runQueryInTx`), giảm `attendeeCount` tương ứng. Sau đó G10–G12, G15 phải xanh
- [ ] T060 [US5] Cài `delete` trong `FN/group-manage/index.ts` theo [research R13](research.md#r13-xóa-nhóm-fr-002-ac-56): transaction đặt `status = DELETED` và gọi hook `{ deleted: true }`; sau commit xóa theo lô ≤ 500 các collection con và thư mục `{groupId}/` của 2 bucket, cuối cùng xóa document nhóm; gọi lại khi đang `DELETED` thì chạy tiếp. Sau đó G14 phải xanh
- [ ] T061 [US5] Cài `setRole`, `transferOwner`, `remove`, `delete`, `observeMembers` trong `CM/data/FirestoreGroupRepository.kt`; UseCase `CM/domain/usecase/ManageMember.kt`, `DeleteGroup.kt`
- [ ] T062 [US5] Cài `CM/ui/members/GroupMembersViewModel.kt`, `CM/ui/members/GroupMembersFragment.kt` + `RES/layout/fragment_group_members.xml`, `RES/layout/item_group_member.xml` (ảnh, tên, vai trò; menu: Phong/Gỡ quản trị viên, Chuyển quyền chủ nhóm, Mời ra khỏi nhóm, mỗi thao tác có hộp thoại xác nhận). Cho T058 xanh
- [ ] T063 [US5] Nút "Xóa nhóm" trong `GroupFormFragment` (hộp thoại bắt gõ lại tên nhóm, AC 5.6); luồng chủ nhóm "Rời nhóm" khi nhận `OwnerMustTransfer` mở `GroupMembersFragment` ở chế độ chọn người nhận quyền, hoặc gợi ý xóa nhóm khi `soleMember` (AC 5.5)

**Checkpoint**: US5 chạy độc lập; G10–G12, G14, G15 xanh; quickstart Q10, Q11, Q14 đúng.

---

## Phase 8: User Story 6 – Báo cáo và ẩn bài vi phạm (Priority: P3)

**Goal**: người xem báo cáo bài vi phạm (một lần mỗi bài); chủ nhóm/quản trị viên ẩn và hiện lại bài; bài admin hệ thống ẩn thì nhóm không hiện lại được; nhóm bị khóa thì không đăng bài được.

**Independent Test**: B báo cáo bài của C với lý do "Spam"; báo cáo có trong `reports`; quản trị viên ẩn bài, thành viên khác không còn thấy, C thấy nhãn "Đã bị ẩn" (quickstart Q12, Q13; P9–P11).

### Tests cho User Story 6 ⚠️ viết trước, phải fail

- [ ] T064 [P] [US6] Bổ sung vào `FN/group-post/index.test.ts`:
  - **P9:** quản trị viên `hide` → `HIDDEN`, `hiddenBy = GROUP`, `hiddenByUid`; `unhide` → `VISIBLE`; bài `hiddenBy = SYSTEM` → `unhide` trả `403 HIDDEN_BY_SYSTEM`; thành viên thường `hide` → `FORBIDDEN`;
  - **P10:** `report` lần 1 → `201`, document `reports/POST_{groupId}_{postId}_{uid}` có `targetType = POST`, `targetPath`, `reason`, `status = OPEN`, `action = NONE`; lần 2 → `409 ALREADY_REPORTED`; bài của mình → `OWN_POST`; không phải thành viên → `NOT_MEMBER`; `reason = OTHER` không có `detail` hoặc `detail` 501 ký tự → `INVALID_ARGUMENT`;
  - **P11:** nhóm `LOCKED`: `group-post create`, `group-manage join` → `423 GROUP_LOCKED`; `leave` vẫn thành công.
- [ ] T065 [P] [US6] Unit test `TEST/ui/report/ReportPostViewModelTest.kt`: chọn lý do; "Khác" bắt buộc mô tả ≤ 500 ký tự; thành công → "Đã gửi báo cáo"; `AlreadyReported` → "Bạn đã báo cáo bài này" (AC 6.2); `Network` giữ lựa chọn

### Implementation cho User Story 6

- [ ] T066 [US6] Cài `hide`, `unhide`, `report` trong `FN/group-post/index.ts` theo [research R7](research.md#r7-ẩn-bài-phân-biệt-nhóm-ẩn-và-admin-hệ-thống-ẩn-fr-019-ac-64) và [R14](research.md#r14-báo-cáo-bài-đăng-fr-018): `report` ghi `reports/{reportId}` bằng `create` có điều kiện "chưa tồn tại" (`currentDocument.exists = false`) để chống trùng. Kiểm tra `loadGroupForWrite` (nhóm `LOCKED` → `GROUP_LOCKED`) ở mọi action ghi của cả hai function, trừ `leave`. Sau đó T064 phải xanh
- [ ] T067 [US6] Cài `hide`, `unhide`, `report` trong `CM/data/FirestorePostRepository.kt`; UseCase `CM/domain/usecase/ModeratePost.kt`, `ReportPost.kt`
- [ ] T068 [US6] Cài `CM/ui/report/ReportPostViewModel.kt`, `CM/ui/report/ReportPostBottomSheet.kt` + `RES/layout/bottom_sheet_report_post.xml` (5 lý do, ô mô tả khi "Khác"; wireframe `ui/09_community-chat_buoc3_bao-cao-bai-dang.png`); menu bài trong `item_post.xml`/`item_event.xml` theo `GroupPermissions`: "Báo cáo" (không có với bài của mình, AC 6.5), "Ẩn bài"/"Hiện lại" cho chủ nhóm/quản trị viên (ẩn "Hiện lại" khi `hiddenBy = SYSTEM`); nhóm `LOCKED` hiện banner "Nhóm đã bị tạm khóa" và ẩn nút đăng bài, tạo sự kiện. Cho T065 xanh

**Checkpoint**: cả 6 user story chạy độc lập; quickstart Q12, Q13 đúng.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T069 Cài `anonymizeCommunityGroups(uid)` trong `FN/_shared/groups.ts` theo [research R16](research.md#r16-chủ-nhóm-hoặc-thành-viên-xóa-tài-khoản-edge-cases-spec-011) kèm test **M3** trong `FN/_shared/groups.test.ts`: bài của người đó `authorSnapshot = null`, `authorDeleted = true`; mỗi nhóm người đó là thành viên chạy như `leave`; nếu là chủ nhóm thì chuyển quyền cho quản trị viên tham gia lâu nhất, không có thì thành viên tham gia lâu nhất, không còn ai thì xóa nhóm như T060; xóa `joinRequests/{uid}`; bỏ tham gia sự kiện chưa diễn ra. Báo A gọi hàm này trong `delete-account`
- [ ] T070 [P] Espresso `ATEST/CreateGroupAndEventFlowTest.kt` (Emulator + seed): tạo nhóm → mở trang nhóm → tạo sự kiện → tài khoản khác tham gia → thấy "1/N người tham gia"
- [ ] T071 [P] Kiểm tra 4 trạng thái (loading, content, empty, error + "Thử lại") trên 10 màn hình/bottom sheet (FR-020); mất mạng không crash, giữ nội dung đang soạn; chế độ tối; bố cục `w600dp` (`RES/layout-w600dp/` nếu cần, bảng tin tối đa 600dp chiều ngang); `contentDescription` cho ảnh và icon, vùng chạm ≥ 48dp
- [ ] T072 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (mục 4.10, 4.12, 5, 6, 7, 8, 8a), **sau khi D đồng ý**
- [ ] T073 [P] Cập nhật `docs/features/09-community-chat/README.md` (link plan, tasks spec 090; trạng thái) và cột *Spec* nhóm 09 trong `docs/features/README.md`
- [ ] T074 Chạy toàn bộ [quickstart.md](quickstart.md): test tự động mục 2; Q1–Q15; đo SC-001, SC-002, SC-005, SC-006; hỏi 5 người thử cho SC-008; chụp ảnh hoặc quay video minh chứng
- [ ] T075 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `deno test -A supabase/functions`, `npx supabase test db`, `npm test` trong `firestore-tests/`; không còn cảnh báo Lint mức Error; ktlint sạch

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)** → **Foundational (Phase 2)**: chặn mọi user story.
- **US1 (Phase 3)**: sau Phase 2. Các story sau cần có nhóm để thử.
- **US2 (Phase 4)**: cần `create` (T024) để có nhóm; phần server (T030, T033, T034) làm song song với app US1 được.
- **US3 (Phase 5)**: cần thành viên (T033) và trang nhóm (T029, T038).
- **US4 (Phase 6)**: xây trên bài đăng của US3 (T046, T050).
- **US5 (Phase 7)**: cần US2 (thành viên, `leave`); T059 phần bỏ tham gia sự kiện cần US4 (`attendees`).
- **US6 (Phase 8)**: cần bảng tin US3.
- **Polish (Phase 9)**: T069 cần T033, T059, T060.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T001, T008 | **A:** `core/` của 010 (010-T012, T015) và `_shared/auth.ts` (010-T021) | Làm theo đúng task gốc của 010 rồi báo A; không tạo bản khác tên |
| T005 | **A:** hàm sinh `nameKeywords` cho `venues` | Viết `_shared/keywords.ts` theo mô tả T005, báo A dùng chung |
| T069 | **A:** `delete-account` gọi `anonymizeCommunityGroups(uid)` | Hàm sẵn trong `_shared/groups.ts`; ghi chú trong PR và báo A |
| T004, T024, T034, T059 | **B:** `runQueryInTx` trong `_shared/firestore.ts` | Dùng bản tối thiểu của 081-T005 (một chỗ duy nhất), báo B gộp |
| T010, T012, T024, T072 | **D:** đồng ý trường và collection **[MỚI]**, sửa mục 7 dòng `groups`, bucket public `group-avatars` | Code và seed vẫn theo data-model; chỉ hoãn T072. Nếu D không đồng ý `group-avatars` thì ảnh đại diện chuyển vào `group-media` và `group-media-url download` cho phép mọi người xin URL của `*/avatar.*` (sửa T012, T045) |
| T045 | **D:** đồng ý `group-media-url` riêng thay vì `sign-upload`/`sign-download` chung | Vẫn làm `group-media-url`; nếu D muốn gộp thì chuyển handler vào function của D |
| T008, T033, T053 | **D, spec 100:** `send-notification` nhận `GROUP_ACTIVITY` với 7 giá trị `data.event` | Gửi sau commit, lỗi bỏ qua; test dùng mock đếm lời gọi |
| T066, T068 | **D, spec 120:** quy ước `hiddenBy = SYSTEM`, `groups.status = LOCKED`, `reportId` tất định và danh sách `reason` | Seed tạo sẵn bài `SYSTEM` và nhóm `LOCKED` để test; báo D khi viết `HIDE_CONTENT` |
| T006, T024, T033 | **C, spec 091:** cài thân `onGroupMembershipChanged` | Thân trống; test 090 chỉ kiểm tra hook được gọi đúng tham số |

### Trong mỗi user story

Test (fail) → Edge Function → repository → UseCase → ViewModel (test xanh) → layout/Fragment.

### Cơ hội làm song song

- Phase 1: T002, T003.
- Phase 2: T005, T007, T011, T013, T014, T015, T017, T020 khác file nhau.
- US1: T021–T023 song song; server T024 song song với T025–T029 khi đã có DTO (T017).
- US2: T030–T032 song song; server T033–T034 song song với app T035–T039.
- US3: T040–T044 song song; T045 song song với T046.
- US4, US6: test (T051–T052, T064–T065) viết song song sau khi có T046.

## Parallel Example: User Story 2

```bash
# Viết test trước, cùng lúc:
Task: "T030 Test G5 (20 người join x 10 lần), G6–G9, G13 trong supabase/functions/group-manage/index.test.ts"
Task: "T031 Unit test GroupSearchViewModel trong app/src/test/.../community/ui/search/GroupSearchViewModelTest.kt"
Task: "T032 Unit test nút tham gia và JoinRequestsViewModel trong app/src/test/.../community/ui/"

# Sau đó, song song:
Task: "T033 Cài join/approve/leave trong supabase/functions/group-manage/index.ts"
Task: "T037 Cài GroupSearchFragment trong app/src/main/java/.../feature/community/ui/search/"
```

## Implementation Strategy

### MVP (US1 + US2)

1. Xong Phase 1, 2.
2. US1 (tạo nhóm): demo được "Nhóm của tôi".
3. US2 (tìm và tham gia): **chạy T030 (G5) 10/10 lần xanh** trước khi demo.
4. **Dừng và kiểm tra**: quickstart Q1–Q6. Spec 091 (chat nhóm) bắt đầu được từ đây vì đã có danh sách thành viên và hook.

### Tăng dần

5. US3 (bảng tin) → US4 (sự kiện, **P6 10/10 lần**).
6. US5 (quản lý thành viên, xóa nhóm).
7. US6 (báo cáo, ẩn bài) → sau khi D chốt quy ước với spec 120.
8. Polish → trước khi đóng băng tính năng (27/11).

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `community` (ví dụ `feat(community): group-manage join trong transaction`).
- Ghi log AI vào `docs/ai-log/AI_log_3.md` cho mỗi lần dùng AI.
- Không commit `supabase/.env`, `local.properties`, `google-services.json`.
