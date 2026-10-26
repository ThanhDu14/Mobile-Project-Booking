# Implementation Plan: Nhóm/CLB và bảng tin cộng đồng

**Branch**: `feature/09-community-chat` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/090-community-group/spec.md`

## Summary

Người chơi tạo nhóm/CLB (công khai hoặc riêng tư), tìm và tham gia nhóm, đăng bài (chữ + tối đa 4 ảnh) và sự kiện giao lưu trên bảng tin chỉ thành viên xem, quản lý vai trò (chủ nhóm, quản trị viên, thành viên), ẩn và báo cáo bài vi phạm.

**Cách làm** ([research.md](research.md)):
- **Mọi thao tác ghi đi qua 3 Edge Function** `group-manage`, `group-post`, `group-media-url`; Rules chỉ mở quyền đọc (R1).
- **Thành viên, yêu cầu tham gia, người bị mời ra** là 3 collection con riêng của `groups/{groupId}`. Rules kiểm tra thành viên chỉ cần `exists(members/{uid})` (R2).
- **`memberCount` và `attendeeCount`** chỉ đổi trong transaction trên document nhóm/bài, nên nhiều người tham gia cùng lúc vẫn đếm đúng (R3).
- **Chống trùng:** `groupId` và `postId` là `requestId` client sinh (R4, R9).
- **Ảnh:** bảng tin ở bucket private `group-media`, đọc bằng signed URL cấp theo lô mỗi trang; ảnh đại diện nhóm ở bucket public mới `group-avatars` (R8).
- **Chat nhóm (spec 091):** hook `onGroupMembershipChanged` chạy trong cùng transaction mỗi khi danh sách thành viên đổi (R10).
- **Thông báo:** `GROUP_ACTIVITY` với `data.event` (R15). **Báo cáo:** ghi `reports` với ID tất định (R14).

## Technical Context

**Language/Version**: Kotlin (Kotlin tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function; SQL cho chính sách Storage

**Primary Dependencies**: Fragment/Navigation + Safe Args, ViewBinding, Material 3, ViewModel/StateFlow, Coroutines, Hilt, Firebase Auth/Firestore, supabase-kt (Functions, Storage), Coil, Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`). Tất cả đã có trong version catalog của 010/070, không thêm thư viện mới.

**Storage**: Cloud Firestore: `groups` (+ `members`, `joinRequests`, `removals`, `posts`, `posts/*/attendees`), `reports` (của D). Supabase Storage: `group-media` (private), `group-avatars` (public, **[MỚI]**).

**Testing**: Vitest + `@firebase/rules-unit-testing` trong `firestore-tests/test/groups.test.ts` (GR1–GR12); `deno test` trên Firebase Emulator (G1–G15, P1–P11, M1–M3, gồm **20 người tham gia nhóm và 20 người tham gia sự kiện cùng lúc, lặp 10 lần**); pgTAP cho Storage (S1–S5); JUnit4, MockK, Turbine cho ViewModel/UseCase

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime

**Project Type**: Mobile app (Android) + server function (Supabase)

**Performance Goals**: tạo nhóm < 1 phút (SC-001); tìm và vào nhóm < 30 giây (SC-002); bài mới hiện với thành viên khác ≤ 5 giây (SC-005, listener realtime); danh sách nhóm và trang đầu bảng tin < 3 giây trên 4G (SC-006, truy vấn có index, 20 mục/trang, 1 lời gọi signed URL mỗi trang)

**Constraints**:
- Người ngoài không đọc được bảng tin, ảnh, thành viên của bất kỳ nhóm nào (FR-010, FR-014, SC-004).
- `memberCount`, `attendeeCount` đúng khi thao tác đồng thời (FR-003, FR-016, SC-003).
- Ảnh ≤ 2 MB, JPEG/PNG/WebP, tối đa 4 ảnh mỗi bài; nén ở client.
- Firebase gói Spark, Supabase gói Free (constitution).

**Scale/Scope**: quy mô đồ án (vài chục nhóm, mỗi nhóm vài chục thành viên). 10 màn hình/bottom sheet, 3 Edge Function, 2 bucket.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | `feature/community/{ui,data,domain}`; ViewModel cung cấp `StateFlow<UiState>`; listener Firestore bọc `callbackFlow`; không import `feature/chat` (091) mà mở chat bằng deep link; dùng `core/` của 010 (`SessionManager`, `IdTokenProvider`, `UserProfileReader`, `SupabaseModule`) |
| II. Bảo mật phía server | ✅ | Mọi ghi qua Edge Function kiểm tra vai trò; Rules `write: if false`, đọc theo thành viên; `memberCount`, `attendeeCount`, `status`, `hiddenBy` 🔒; bucket `group-media` không có policy cho người dùng, chỉ signed URL; test Rules GR1–GR12 và pgTAP S1–S5 |
| III. Toàn vẹn dữ liệu | ✅ | Tham gia/rời/duyệt/mời ra/tham gia sự kiện chạy trong transaction trên document cha; `requestId` làm ID nhóm và bài; ID thành viên, người tham gia là `uid`; máy trạng thái nhóm và bài ghi trong [data-model.md](data-model.md); thời gian `Timestamp`, hiển thị `Asia/Ho_Chi_Minh` |
| IV. Kiểm thử có trọng tâm | ✅ | Test đồng thời G5, G7, P6 (SC-003); test phân quyền G10, G11, P2, M1, M2; test ViewModel/UseCase; Espresso luồng tạo nhóm → đăng sự kiện |
| V. Giao diện XML nhất quán | ✅ | Layout tiền tố `fragment_community_*`, `item_group*`, chuỗi `community_*`; 4 trạng thái; Material 3 DayNight; `w600dp`; `contentDescription` cho ảnh |
| VI. Quyền riêng tư | ✅ | Photo Picker không cần quyền bộ nhớ; bảng tin chỉ thành viên; báo cáo bài được (FR-018, spec 120); xóa tài khoản thì ẩn danh bài và chuyển quyền chủ nhóm (R16) |
| VII. Đơn giản, minh bạch AI | ✅ | Không thư viện mới; 3 function theo action thay vì ~20 function; không job dọn ảnh mồ côi (R8); log AI trong `docs/ai-log/AI_log_3.md` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế thêm 3 collection con (`joinRequests`, `removals`, `attendees`), 1 bucket public và các trường **[MỚI]** liệt kê cuối [data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd); tất cả cần nhóm đồng ý ở bước cập nhật đặc tả CSDL, không vi phạm nguyên tắc nào.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **A** (spec 010, 011) | 1. Dùng `core/` của 010 (`SessionManager`, `IdTokenProvider`, `UserProfileReader`, `SupabaseModule`) và `_shared/auth.ts` (010-T021).<br>2. `delete-account` gọi `anonymizeCommunityGroups(uid)` (R16; spec 011 research R12 đã giao cho C).<br>3. Dùng chung hàm sinh `nameKeywords` với `venues` (đặt ở `_shared/keywords.ts`). | Họp 16/10 |
| **B** | `_shared/firestore.ts` có `runQueryInTx` (giống yêu cầu của spec 081) để đếm giới hạn 5 nhóm và lấy người tham gia sự kiện trong transaction. | Họp 16/10 |
| **D** (đặc tả CSDL, Storage) | 1. Đồng ý các trường, collection **[MỚI]** và sửa mục 7 dòng `groups` (danh sách cuối data-model).<br>2. Đồng ý bucket public `group-avatars` và function `group-media-url` (thay vì `sign-upload`/`sign-download` chung). | Họp 16/10 |
| **D** (spec 100) | Chốt danh sách `data.event` của `GROUP_ACTIVITY`: `JOIN_REQUESTED`, `JOIN_APPROVED`, `JOIN_REJECTED`, `REMOVED`, `EVENT_CREATED`, `EVENT_UPDATED`, `EVENT_DELETED`; `EVENT_CREATED` gửi `recipientUids` hay để D tự lấy thành viên (R15). Deep link `smashnow://community/group/{groupId}`. | Họp 16/10 |
| **D** (spec 120) | 1. Ẩn bài theo báo cáo: ghi `status = HIDDEN`, `hiddenBy = SYSTEM` (R7).<br>2. Khóa nhóm: `groups.status = LOCKED` (spec 120 chưa có action này, R17).<br>3. Đồng ý `reportId = POST_{groupId}_{postId}_{uid}` và danh sách `reason` dùng chung cho 070, 090, 091 (R14). | Họp 16/10 |
| C (mình, spec 091) | Cài `onGroupMembershipChanged` để đồng bộ `chats/{chatId}.memberIds`. | Khi viết plan 091 |
| C (mình, spec 070) | Sau khi D chốt danh sách `reason` báo cáo (R14), đồng bộ lý do báo cáo đánh giá của spec 070 và tin nhắn của 091 theo cùng danh sách. | Sau họp 16/10 |

## Project Structure

### Documentation (this feature)

```text
specs/090-community-group/
├── spec.md
├── plan.md                              # file này
├── research.md                          # Phase 0 (R1–R18)
├── data-model.md                        # Phase 1
├── quickstart.md                        # Phase 1
├── contracts/
│   ├── edge-functions-community.md      # group-manage, group-post, group-media-url, _shared/groups.ts, test G/P/M
│   ├── security-rules.md                # Rules groups + bucket, test GR1–GR12, S1–S5
│   └── ui-screens.md                    # màn hình, ViewModel, điểm nối
├── ui/                                  # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/feature/community/
├── domain/
│   ├── model/ (Group, GroupMembership, GroupMember, JoinRequest, Post, EventInfo, PostDraft, DraftPhoto, enum)
│   ├── GroupPermissions.kt
│   ├── GroupRepository.kt, PostRepository.kt, GroupMediaRepository.kt     # interface
│   └── usecase/ (CreateGroup, UpdateGroup, DeleteGroup, SearchGroups, ObserveMyGroups, JoinGroup, LeaveGroup,
│                 ManageMember, ReviewJoinRequest, PublishPost, EditPost, DeletePost, ToggleAttendance,
│                 ModeratePost, ReportPost, UploadPostPhotos)
├── data/
│   ├── dto/ (GroupDto, MemberDto, PostDto, AttendeeDto, EdgeDtos, Mappers)
│   ├── FirestoreGroupRepository.kt, FirestorePostRepository.kt, SupabaseGroupMediaRepository.kt
│   ├── SignedUrlCache.kt
│   └── di/CommunityModule.kt
└── ui/
    ├── home/ (CommunityHomeFragment, CommunityHomeViewModel)
    ├── search/ (GroupSearchFragment, GroupSearchViewModel, GroupAdapter)
    ├── form/ (GroupFormFragment, GroupFormViewModel)
    ├── detail/ (GroupDetailFragment, GroupDetailViewModel, PostAdapter)
    ├── composer/ (PostComposerFragment, PostComposerViewModel)
    ├── event/ (EventAttendeesBottomSheet)
    ├── members/ (GroupMembersFragment, GroupMembersViewModel, JoinRequestsFragment, JoinRequestsViewModel)
    └── report/ (ReportPostBottomSheet)
app/src/main/res/layout/ (fragment_community_*, fragment_group_*, item_group*, item_post.xml, item_event.xml, bottom_sheet_*)
app/src/main/res/navigation/nav_community.xml
app/src/main/res/values/strings_community.xml

supabase/functions/
├── _shared/groups.ts, groups.test.ts, keywords.ts
├── group-manage/index.ts, index.test.ts
├── group-post/index.ts, index.test.ts
└── group-media-url/index.ts, index.test.ts
supabase/migrations/<timestamp>_group_buckets.sql
supabase/tests/group_buckets.test.sql

firestore.rules                                  # phần groups
firestore.indexes.json                           # index ở data-model mục 9
firestore-tests/test/groups.test.ts              # GR1–GR12
scripts/seed/090-community.ts
```

**Structure Decision**:
- **App:** package `feature/community` cho spec 090; spec 091 dùng `feature/chat`. Hai package không import lẫn nhau: 090 mở chat bằng deep link, 091 đọc danh sách thành viên qua Firestore.
- **Server:** logic quyền và hook dùng chung đặt ở `_shared/groups.ts` để spec 091 và `delete-account` (A) gọi được mà không phụ thuộc phần còn lại của 090.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
