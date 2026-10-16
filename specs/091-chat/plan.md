# Implementation Plan: Chat nhóm và chat với chủ sân

**Branch**: `feature/09-community-chat` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/091-chat/spec.md`

## Summary

Người dùng trò chuyện thời gian thực: mỗi nhóm/CLB của spec 090 có một chat nhóm với thành viên đúng bằng danh sách thành viên nhóm; người chơi nhắn riêng cho chủ sân từ chi tiết sân, chủ sân trả lời ở "Tin nhắn khách hàng". Có gửi ảnh, số chưa đọc, tắt thông báo từng cuộc, thu hồi tin trong 10 phút, báo cáo tin, chặn trong chat riêng.

**Cách làm** ([research.md](research.md)):
- **Gửi tin qua Edge Function `chat-send`**; app giữ hàng chờ gửi `ChatOutbox` (DataStore) để tự gửi lại khi có mạng và cho "Gửi lại"/"Xóa" tin lỗi. `messageId` tất định nên không bao giờ tạo tin trùng (R1).
- **`chat-send` chạy một transaction**: kiểm tra quyền (trong cuộc, bị chặn, nhóm bị khóa, chat chỉ đọc), ghi tin với `createdAt` theo giờ server, cập nhật `lastMessage` và `unreadCounts` của từng người; sau commit gửi `CHAT_MESSAGE` (R6, R7, R13).
- **Chat riêng** có ID `{uidNhỏ}_{uidLớn}`, tạo qua `chat-action openDirect` trong transaction, nên hai người mở cùng lúc chỉ có một cuộc (R3).
- **Chat nhóm** `group_{groupId}` do hook `onGroupMembershipChanged` của spec 090 đồng bộ trong cùng transaction thay đổi thành viên (R5).
- **Ảnh** ở bucket private `chat-media`, signed URL qua `chat-media-url`; tin thu hồi hoặc bị ẩn không cấp URL (R8–R10).
- **Rules:** chỉ người trong `memberIds` đọc; client chỉ được đặt `unreadCounts.{mình} = 0` và sửa `participants/{mình}` (R2, R6).

## Technical Context

**Language/Version**: Kotlin (Kotlin tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function; SQL cho chính sách Storage

**Primary Dependencies**: giống spec 090 (Fragment/Navigation + Safe Args, ViewBinding, Material 3, ViewModel/StateFlow, Coroutines, Hilt, Firebase Auth/Firestore, supabase-kt Functions/Storage, Coil, Photo Picker, DataStore). `ActivityResultContracts.TakePicture` cho camera. Không thêm thư viện mới.

**Storage**: Cloud Firestore: `chats` (+ `participants` **[MỚI]**, `messages`), `reports` (của D). Supabase Storage: `chat-media` (private). DataStore `chat_outbox` cho tin chưa gửi.

**Testing**: Vitest + `@firebase/rules-unit-testing` trong `firestore-tests/test/chats.test.ts` (CR1–CR8); `deno test` trên Firebase Emulator (C1–C8, D1–D4, R1–R2, H1–H2, M1–M2, A1, gồm **20 người gửi cùng lúc lặp 10 lần** và **hai bên mở chat riêng cùng lúc lặp 20 lần**); pgTAP S1–S4; JUnit4, MockK, Turbine; test Android trên Emulator cho outbox khi mất mạng (SC-004)

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime

**Project Type**: Mobile app (Android) + server function (Supabase)

**Performance Goals**: tin hiện bên nhận ≤ 3 giây ở 95% lần gửi (SC-001, listener realtime); gửi câu hỏi đầu cho chủ sân < 30 giây (SC-002); danh sách và 30 tin đầu < 3 giây (SC-006)

**Constraints**:
- Người ngoài cuộc (kể cả ngay sau khi rời nhóm) không đọc hay gửi được; bị chặn không gửi được; không thu hồi tin người khác (FR-004, FR-016, SC-003).
- Gửi lại khi mất mạng đúng một lần (SC-004); một cuộc cho mỗi cặp (SC-005).
- Thứ tự tin theo giờ server (Edge Cases).
- Ảnh ≤ 2 MB, JPEG/PNG/WebP, một ảnh mỗi tin; tin chữ 1–1000 ký tự.

**Scale/Scope**: quy mô đồ án (vài chục cuộc mỗi người, nhóm vài chục người). 3 màn hình/bottom sheet, 3 Edge Function, 1 bucket.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | `feature/chat/{ui,data,domain}`; không import `feature/community` (đọc nhóm qua Firestore, mở từ 090 bằng deep link); huy hiệu qua interface `core/chat/UnreadChatsReader` để `MainActivity` không import `feature/chat`; listener bọc `callbackFlow` |
| II. Bảo mật phía server | ✅ | Tin nhắn chỉ ghi qua `chat-send`; Rules: đọc theo `memberIds`, client chỉ ghi `unreadCounts.{mình} = 0` và `participants/{mình}`; bucket `chat-media` không có policy cho người dùng; test CR1–CR8, S1–S4 |
| III. Toàn vẹn dữ liệu | ✅ | Không có đặt chỗ, nhưng vẫn dùng transaction cho gửi tin, mở chat riêng, đồng bộ thành viên; `messageId` (= `requestId`) chống trùng; thời gian theo server; máy trạng thái tin ghi trong [data-model.md](data-model.md) |
| IV. Kiểm thử có trọng tâm | ✅ | Test đồng thời C3, D1; test phân quyền C4, C5, R1, CR6; test outbox khi mất mạng; unit test ViewModel/outbox |
| V. Giao diện XML nhất quán | ✅ | `fragment_chat*`, `item_message_*`, chuỗi `chat_*`; 4 trạng thái; DayNight; `w600dp`; `contentDescription` cho ảnh |
| VI. Quyền riêng tư | ✅ | Quyền camera xin lúc dùng, từ chối vẫn chọn ảnh/nhắn chữ được (AC 4.5); ảnh chỉ người trong cuộc xem; báo cáo được (FR-014); xóa tài khoản thì ẩn danh và chat riêng chỉ đọc (R16) |
| VII. Đơn giản, minh bạch AI | ✅ | Không thêm thư viện (không WorkManager, outbox dùng DataStore có sẵn); log AI trong `docs/ai-log/AI_log_3.md` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thêm collection con `participants` và các trường **[MỚI]** liệt kê cuối [data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd), cần nhóm đồng ý.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **A** (spec 010, 011, 030) | 1. Dùng `core/` của 010 và `_shared/auth.ts` (010-T021).<br>2. Spec 030: nút "Nhắn tin cho chủ sân" trên sân đang hoạt động, ẩn khi chủ sân xem sân của mình, mở `smashnow://chat/direct?venueId=`.<br>3. `MainActivity`: huy hiệu mục "Tin nhắn" qua `core/chat/UnreadChatsReader`.<br>4. `delete-account` gọi `anonymizeCommunityChats(uid)` (spec 011 research R12). | Họp 16/10 |
| **B** | `_shared/firestore.ts` hỗ trợ transform `REQUEST_TIME` khi commit (cho `createdAt`, `updatedAt`) và tăng số trong Map (`unreadCounts.{uid}`) trong transaction. | Họp 16/10 |
| **D** (đặc tả CSDL) | Đồng ý collection `participants` và các trường **[MỚI]** của `chats`, `messages`; sửa mục 7 dòng `chats`. | Họp 16/10 |
| **D** (spec 100) | `CHAT_MESSAGE` với `data = { chatId, messageId }`, deep link `smashnow://chat/{chatId}`; `chat-send` tự lọc người tắt thông báo và người đang mở cuộc. | Họp 16/10 |
| **D** (spec 111/112) | Mục "Tin nhắn khách hàng" trong menu chế độ chủ sân mở `smashnow://chat/owner`. | Khi D làm menu chủ sân |
| **D** (spec 120) | Ẩn tin theo báo cáo: `messages.status = HIDDEN`, nên ghi thêm `text = ""` (R10); `reportId = MESSAGE_{chatId}_{messageId}_{uid}`, danh sách `reason` chung với 090. | Họp 16/10 |
| C (mình, spec 090) | Hook `onGroupMembershipChanged` gọi `syncGroupChat`; 090-T060 gọi `purgeChat` sau khi xóa nhóm. | Khi làm 090-T006, T060 |

## Project Structure

### Documentation (this feature)

```text
specs/091-chat/
├── spec.md
├── plan.md                         # file này
├── research.md                     # Phase 0 (R1–R16)
├── data-model.md                   # Phase 1
├── quickstart.md                   # Phase 1
├── contracts/
│   ├── edge-functions-chat.md      # chat-send, chat-action, chat-media-url, _shared/chats.ts, test C/D/R/H/M/A
│   ├── security-rules.md           # Rules chats, bucket chat-media, test CR1–CR8, S1–S4
│   └── ui-screens.md               # màn hình, ViewModel, điểm nối
├── ui/                             # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                        # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/core/chat/UnreadChatsReader.kt       # interface cho MainActivity
app/src/main/java/com/example/booking_app/feature/chat/
├── domain/
│   ├── model/ (ChatSummary, ChatDetail, Participant, ChatMessage, OutboxEntry, enum)
│   ├── ChatRepository.kt, ChatMediaRepository.kt                             # interface
│   └── usecase/ (ObserveChats, OpenDirectChat, ObserveMessages, LoadOlderMessages, SendMessage, RetryMessage,
│                 DiscardMessage, MarkChatRead, RecallMessage, ReportMessage, SetMuted, SetBlocked)
├── data/
│   ├── dto/ (ChatDto, ParticipantDto, MessageDto, EdgeDtos, Mappers)
│   ├── FirestoreChatRepository.kt, SupabaseChatMediaRepository.kt
│   ├── ChatOutbox.kt                                                          # DataStore chat_outbox
│   ├── UnreadChatsReaderImpl.kt
│   └── di/ChatModule.kt
└── ui/
    ├── list/ (ChatListFragment, ChatListViewModel, ChatAdapter)
    ├── conversation/ (ChatFragment, ChatViewModel, MessageAdapter)
    └── report/ (ReportMessageBottomSheet, ReportMessageViewModel)
app/src/main/res/layout/ (fragment_chat_list.xml, item_chat.xml, fragment_chat.xml, item_message_mine.xml, item_message_other.xml, bottom_sheet_report_message.xml)
app/src/main/res/navigation/nav_chat.xml
app/src/main/res/values/strings_chat.xml

supabase/functions/
├── _shared/chats.ts, chats.test.ts, media.ts
├── chat-send/index.ts, index.test.ts
├── chat-action/index.ts, index.test.ts
└── chat-media-url/index.ts, index.test.ts
supabase/migrations/<timestamp>_chat_media_bucket.sql
supabase/tests/chat_media.test.sql

firestore.rules                                  # phần chats
firestore.indexes.json                           # index ở data-model mục 6
firestore-tests/test/chats.test.ts               # CR1–CR8
scripts/seed/091-chat.ts
```

**Structure Decision**:
- **App:** package `feature/chat`, tách khỏi `feature/community` (090). Hai package không import lẫn nhau; dữ liệu chung đi qua Firestore và deep link. Phần `MainActivity` cần (huy hiệu) đưa lên `core/chat`.
- **Server:** `_shared/chats.ts` chứa phần 090 và A gọi (`syncGroupChat`, `purgeChat`, `anonymizeCommunityChats`); `_shared/media.ts` dùng chung với `group-media-url` của 090.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
