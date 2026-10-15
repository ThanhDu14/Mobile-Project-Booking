# Research: Feature 100 – Notification

## R1. Backend và vị trí lưu inbox

- **Decision**: Dùng Firestore và Supabase Edge Functions/pg_cron theo constitution. Inbox là `users/{uid}/notifications/{id}`; giữ chính xác `type`, `title`, `body`, `data`, `isRead`, `createdAt`. Preference và FCM tokens là `users/{uid}.notificationPrefs` và `.fcmTokens`.
- **Rationale**: DB README §4.1, §4.13, §8a xác định vị trí, fields, service `send-notification`/`booking-reminders`, quyền owner-read/update-isRead/server-create. Constitution cấm Cloud Functions/Blaze.
- **Alternatives considered**: top-level `/notifications` collection bị loại vì trái đường dẫn đã chốt; Cloud Functions bị loại theo stack.

## R2. Kiến trúc Android

- **Decision**: Feature package theo MVVM, Fragment/XML/ViewBinding; repository làm ranh giới Firebase; StateFlow UI state; Firestore listener theo vòng đời. Dùng Firebase Auth/Firestore/FCM hiện có trong version catalog. Runtime permission do UI/platform adapter xin lúc phù hợp; notification channel và OS permission không thay thế preference trong hồ sơ.
- **Rationale**: Constitution I/V/VI; app hiện chỉ có starter `MainActivity`, `FirstFragment`, `SecondFragment`, không có module notification hoặc DI setup.
- **Alternatives considered**: gọi SDK trực tiếp từ Fragment/ViewModel bị constitution cấm; lưu preference chỉ ở DataStore bị loại làm nguồn chuẩn vì spec/database yêu cầu map trong `users/{uid}`. DataStore (constitution) có thể là cache cục bộ, vai trò đồng bộ chưa được chốt.

## R3. Quyền, người nhận và nơi tạo notification

- **Decision**: Nguồn nghiệp vụ không tự ghi inbox. Server phía nhóm D tạo bản ghi và push. Notification client chỉ được đọc đúng hộp thư uid hiện tại, chỉ cập nhật `isRead`; admin/system gửi qua đường server.
- **Rationale**: Spec §Actors/FR-002/FR-004/FR-005; DB README §2, §7; nguyên tắc bảo mật II.
- **Alternatives considered**: client fan-out/ghi thay nhóm nguồn bị loại. CSDL tóm tắt Admin `C` notification có vẻ cho phép client admin, nhưng spec 120 xác định nội dung và gửi `SYSTEM` qua admin flow/server, database §4.13 ghi server tạo; cần đồng bộ quy ước, không mở client create trong plan.

## R4. Người nhận và hợp đồng sự kiện

- **Decision**: Áp dụng nguyên văn bảng type/recipient trong spec 100 và các clarifications: người đặt cho kết quả booking; chủ cơ sở cho `BOOKING_NEW` tại PENDING; một reminder khi CONFIRMED tại startAt sớm nhất trừ 2 giờ; người đăng ký, không phải waitlisted, cho DROPIN_CHANGED; follower đủ điều kiện voucher cho PROMOTION; người nhận tin nhắn cho CHAT_MESSAGE; mọi user account active cho SYSTEM. Loại tự thao tác không phát notification.
- **Rationale**: Spec 100 §Clarifications, FR-010–012, event catalog; DB README §4.13.
- **Alternatives considered**: tự tính người nhận bên client hoặc đổi nghiệp vụ nguồn không phù hợp phân quyền và phạm vi. Event contracts phải do nguồn phát với recipient/identifiers agreed; chưa bịa payload.

## R5. Push preference và OS permission

- **Decision**: Tạo inbox trước/độc lập với push eligibility. Preference chỉ bật/tắt push; default mọi type bật trừ PROMOTION; BOOKING_NEW không thể tắt. OS permission là lớp chặn cuối cùng. CHAT_MESSAGE vẫn lưu inbox khi chat đang được xem nhưng suppress push cho đúng chat đó.
- **Rationale**: Spec §Clarifications, FR-006–008, FR-011–012; FCM đang có trong app catalog.
- **Alternatives considered**: tắt preference làm mất inbox trái FR-007. Chưa chọn cách biểu diễn foreground chat-presence vì spec 091 không có presence contract.

## R6. Idempotency và delivery retry

- **Decision**: Bắt buộc bảo đảm một notification cho tuple (business event, recipient, type), nhưng chưa chọn khóa tất định vì schema không có event idempotency field và source specs chưa cung cấp một event key chung. Inbox commit phải độc lập với lỗi push; retry push chỉ sau khi quyết định cách lưu trạng thái/job retry mà không mở rộng schema ngầm.
- **Rationale**: FR-014; spec edge case nêu retry chưa quy định; DB README không định nghĩa notification ID hoặc delivery state.
- **Alternatives considered**: ID deterministic tổng quát hoặc thêm eventId/delivery status/collection là schema tự bịa, không dùng cho tới khi nhóm chốt.

## R7. Reminder scheduler

- **Decision**: Dựa vào `booking-reminders` (pg_cron) đã liệt kê trong DB README; chỉ chọn booking `CONFIRMED`, thời điểm 2h trước `items[].startAt` nhỏ nhất, phát một lần. Không đề xuất đổi booking state/schema.
- **Rationale**: Spec FR-010/clarification; DB README §8a.
- **Alternatives considered**: client local alarm không đảm bảo qua nhiều thiết bị/offline và đi ngược scheduler server đã xác định. Cách lấy/đánh dấu reminder đã xử lý còn cần chốt vì chưa có reminder state/key.

## R8. Phân trang và giữ dữ liệu

- **Decision**: Query theo `createdAt` giảm dần, cursor pagination, tải trang đầu trước; không dùng listener không giới hạn. Giữ dữ liệu đến khi xóa tài khoản, theo xóa server-side trong delete-account.
- **Rationale**: Spec FR-003/FR-016; DB subcollection và transaction/cost constraints. Không có collection index hoặc page size notification nào trong DB README.
- **Alternatives considered**: tải toàn bộ inbox không an toàn về cost/hiệu năng. Page size/index chưa thể được coi là đã chốt; cần quyết định và bổ sung index khi query được thống nhất.

## R9. Deep link và privacy

- **Decision**: `data` chỉ dùng keys/identifiers đã có trong spec/database (vd. bookingId/sessionId); khi nguồn thiếu hoặc quyền truy cập bị thu hồi, mở chi tiết notification từ title/body đã lưu. Push không được cấp quyền truy cập vào tài nguyên nguồn; app fetch/authorize nguồn khi mở.
- **Rationale**: Spec FR-013, key entity, edge case; DB README §4.13; Rules/server security.
- **Alternatives considered**: lưu snapshot fields tùy ý/URLs có thể rò dữ liệu hoặc tạo schema mới; chưa xác định.

## R10. Kiểm thử/harness hiện hữu

- **Decision**: Unit tests với JUnit4/MockK/coroutines-test/Turbine; Rules Emulator; Edge Function Deno tests và test fan-out/retry bằng fake FCM; UI/Espresso cho inbox/settings. Tích hợp test vào repo harness sau khi xác minh cấu hình.
- **Rationale**: Constitution IV. Repo hiện `firestore-tests` có Vitest setup, chưa có test rules notification; app source starter và chưa thấy Hilt/coroutines/test deps trong catalog; `supabase/functions` chưa tồn tại.
- **Alternatives considered**: Không chạy test trên Firebase/Supabase thật; không thêm test framework thứ hai nếu harness dùng chung được.

## Điểm cần làm rõ trước khi implementation

1. Định danh event/key idempotency và cách lưu trạng thái gửi/đánh dấu reminder đã xử lý mà không trùng khi retry.
2. Event transport, payload tối thiểu, và owner của từng adapter `send-notification` cho các nguồn 04/05/06/07/08/09/11/12. Chốt recipient tại producer hay resolver server; bảo đảm đúng actor trong spec.
3. `CHAT_MESSAGE`: contract presence/foreground để biết người nhận đang mở đúng chat; hành vi nhiều thiết bị.
4. Retry/backoff, FCM invalid-token cleanup, partial fan-out và dead-letter/manual recovery trong giới hạn Supabase Free.
5. Query page size, listener/polling strategy, composite index; giới hạn/hiệu năng với giữ inbox vô thời hạn.
6. `SYSTEM` fan-out và định nghĩa `accountStatus == ACTIVE`/tài khoản active; schema CSDL nói Admin C còn spec 120 và §4.13 nói server create.
7. Mâu thuẫn/khác tầng: constitution nhắc DataStore cho notification preferences trong khi spec/database lấy `users.notificationPrefs` làm nguồn chuẩn. Xác nhận DataStore chỉ cache; xác nhận default persisted/legacy-user fallback.
8. Token lifecycle khi đăng xuất, đổi tài khoản/thiết bị, nhiều token, permission denied; spec 011 chỉ nói logout stop push ở thiết bị đó nhưng database chỉ cho owner write fcmTokens.
9. Quyền Android POST_NOTIFICATIONS theo target SDK, thời điểm xin và UX từ chối; dự án target 37 nhưng chưa có policy app-specific beyond constitution.
10. Deep-link keys/behavior cho mọi type, đặc biệt `GROUP_ACTIVITY`, `OWNER_*`, `SYSTEM`; title/body lưu đủ chi tiết fallback và chính sách nội dung nhạy cảm trên lock screen.
11. Quy tắc cập nhật notification khi event nguồn bị đổi/hủy sau khi đã tạo; offline read/settings write conflict behavior.
12. Chính sách permission/data ownership: Admin notification create qua callable server hay Security Rules; cập nhật DB README/120 nếu cần do người sở hữu tài liệu.
