# Implementation Plan: Thông báo

**Branch**: `feature/100-notification-plan` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: `specs/100-notification/spec.md`; checklist `checklists/requirements.md`

## Summary

Triển khai trung tâm thông báo Android, đọc danh sách riêng theo người dùng, đánh dấu từng mục/tất cả đã đọc, cài đặt push theo type và nhận FCM. Nguồn nghiệp vụ chỉ phát sự kiện; dịch vụ nền do nhóm D phụ trách xác định người nhận, ghi inbox và gửi push. Dùng đúng đường dẫn Firestore `users/{uid}/notifications/{id}`, các field đã chốt trong CSDL, `users.notificationPrefs`, `users.fcmTokens`; không thêm collection/field trong plan này. Push thất bại không làm mất inbox. Chi tiết idempotency, trigger/event transport và retry còn phụ thuộc các điểm cần chốt trong [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin, AGP 9.4.1, JVM target 11; TypeScript/Deno cho Supabase Edge Functions theo constitution.
**Primary Dependencies**: AndroidX Fragment/Navigation, ViewBinding, Material 3, ViewModel/StateFlow, Coroutines/Flow, Hilt theo constitution; Firebase Auth, Firestore, FCM theo Firebase BoM. Gradle catalog hiện có Firebase Auth/Firestore/Messaging/App Check nhưng chưa có Hilt, lifecycle, coroutines hoặc test libraries cần thiết; chỉ bổ sung qua `gradle/libs.versions.toml` sau quyết định chung của dự án.
**Storage**: Firestore `users/{uid}/notifications/{id}`; preference và token ở document `users/{uid}`. Supabase Edge Functions cho xử lý server và FCM HTTP v1; không dùng Cloud Functions/Blaze.
**Testing**: JUnit4, MockK, kotlinx-coroutines-test, Turbine; Firebase Emulator và `@firebase/rules-unit-testing`; Deno/Supabase local cho function. Hiện `firestore-tests` có scaffold Vitest, Android source mới ở mức starter và `supabase/functions` chưa được tạo.
**Target Platform**: Android minSdk 29, target/compileSdk 37; Supabase Edge Runtime.
**Project Type**: Android mobile app (single Activity) + Supabase Edge Functions.
**Performance Goals**: Không có số đo riêng trong spec; cần xác nhận kích thước trang/listener và mục tiêu tải trang. Constitution quy định danh sách chung <3 giây trên 4G.
**Constraints**: Firebase Spark và Supabase Free; không Cloud Functions. Push permission runtime, inbox hoạt động dù bị từ chối quyền. Ưu tiên truy vấn phân trang và listener có vòng đời rõ; spec chưa chốt page size/strategy. Không xóa notification, giữ tới lúc xóa tài khoản. `BOOKING_NEW` luôn push-eligible và không có toggle; OS vẫn có thể chặn hiển thị.
**Scale/Scope**: Quy mô demo của đồ án, chưa có số liệu notification/user. Một notification center, một settings screen/section, Android FCM token lifecycle, server-side fan-out/delivery, reminder job, Rules và indexes cần xác định.

## Constitution Check

| Nguyên tắc | Đánh giá | Cách đáp ứng |
|---|---|---|
| I. MVVM/package feature | Đạt theo thiết kế | `feature/notification/{ui,data,domain}`; Fragment → ViewModel → repository; UI không gọi Firebase trực tiếp; StateFlow đủ loading/content/empty/error; listener được đóng khi rời màn. |
| II. Server security | Đạt có điều kiện | Client chỉ đọc hộp thư của uid hiện tại và chỉ cập nhật `isRead`; tạo bản ghi và gửi FCM qua server. Rules phải chặn đọc/sửa chéo uid và mọi field ngoài isRead. Test Emulator bắt buộc. Database README tóm tắt Admin C notification nhưng Feature 120 nói Admin gửi qua server; chọn server-authoritative, cần xác nhận/đồng bộ bảng quyền. |
| III. Booking integrity | Không tác động | Không đổi nghiệp vụ booking. Reminder chỉ dựa trên booking đã `CONFIRMED`, 2 giờ trước startAt sớm nhất, một reminder/đơn; không sửa state máy booking. |
| IV. Focused tests | Đạt theo kế hoạch | Unit tests cho mapping, sort/page, preference, deep-link fallback, UI state; Rules Emulator allow/deny; test idempotency, recipient fan-out, reminder eligibility và delivery retry phía server. Không dùng project thật. |
| V. XML UI | Đạt theo kế hoạch | XML, Material 3, strings/resources tiếng Việt, RecyclerView/ListAdapter/DiffUtil, DayNight, bốn trạng thái và retry; kiểm tra accessibility/touch targets. |
| VI. Privacy | Đạt có điều kiện | Chỉ gửi payload push tối thiểu, không để nội dung nhạy cảm xuất hiện trên lock screen nếu chưa có quyết định; uid scoping; xóa notification/prefs khi delete-account; quyền thông báo xin lúc cần và từ chối không ảnh hưởng inbox. Nội dung push cần xác nhận chính sách privacy. |
| VII. YAGNI | Đạt có điều kiện | Dùng stack đã có trong constitution, không thêm dịch vụ Firebase có phí hoặc schema chưa xác nhận. Dependency Android còn thiếu sẽ chờ quyết định dùng chung; chưa có cơ sở chọn thư viện khác. |

**Gate trước Phase 0**: Đạt có điều kiện. Spec + checklist đã xác nhận quy tắc nghiệp vụ chính; một số giao kèo kỹ thuật còn thiếu được ghi rõ, không tự suy diễn.

**Re-check sau Phase 1**: Đạt có điều kiện với các điểm chưa chốt ở research vẫn là blocker cho implementation server/integration, không phải vi phạm được cho phép lờ đi.

## Project Structure

### Documentation (this feature)

```text
specs/100-notification/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── event-integration.md
│   └── firestore-security.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/feature/notification/
├── domain/       # Notification, NotificationType, Preferences; repository interfaces/use cases
├── data/         # Firestore DTO/repository; FCM token registration and notification service
└── ui/           # inbox/settings fragments, viewmodels, list adapter
app/src/main/res/{layout,navigation,values}/
app/src/test/java/com/example/booking_app/feature/notification/
app/src/androidTest/java/com/example/booking_app/feature/notification/
firestore.rules
firestore.indexes.json
firestore-tests/test/notification*.test.ts  # theo harness hiện tại; framework contract cần thống nhất
supabase/functions/send-notification/
supabase/functions/booking-reminders/
supabase/functions/_shared/               # chỉ khi có module dùng chung được B/D đồng thuận
```

**Structure Decision**: Theo package feature Android đã quy định; backend ở Supabase Edge Functions theo constitution và DB README. Đây là cấu trúc dự kiến để xác định tác động, không khẳng định thư mục backend/harness đã tồn tại. Không thay đổi package/module của feature nguồn; họ phát event theo contract được nhóm D thống nhất.

## Complexity Tracking

Không đề xuất vi phạm constitution, module mới hoặc collection mới. Nếu sau khi chốt event fan-out cần lưu trạng thái delivery/outbox để retry, đó sẽ là schema mới và phải được review/duyệt trong tài liệu CSDL trước khi đưa vào implementation.

