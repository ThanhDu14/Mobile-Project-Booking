# Data Model: Feature 100 – Notification

Nguồn chuẩn: [CSDL chung §4.1, §4.13, §7–8a](../../docs/design/database/README.md), [spec.md](spec.md). Tài liệu này mô tả các document đã chốt; không đề xuất collection/field mới.

## Notification

**Đường dẫn**: `users/{uid}/notifications/{id}`. Collection là subcollection của user, dù shorthand bảng phân quyền gọi là `notifications`. ID document hiện chưa được chuẩn hóa.

| Field | Kiểu theo CSDL | Quy tắc |
|---|---|---|
| `type` | string/enum | Một trong 19 type ở spec FR-001. Server ghi. |
| `title` | string | Nội dung tiêu đề đã lưu để render inbox/fallback. Server ghi. |
| `body` | string | Nội dung đã lưu để render inbox/fallback. Server ghi. |
| `data` | Map | Dữ liệu liên quan nếu có; CSDL nêu ví dụ `bookingId`, `sessionId`. Chỉ dùng identifiers đã được nguồn xác nhận; không mở rộng field tự ý. Server ghi. |
| `isRead` | boolean | Khởi tạo chưa đọc theo nghiệp vụ; người nhận chỉ được đổi trường này. Giá trị khởi tạo cụ thể nên được thống nhất với quy ước CSDL chung. |
| `createdAt` | Timestamp | Server timestamp; dùng sắp xếp mới nhất trước và cursor pagination. |

Quan hệ: một user có nhiều notification; không xóa từng mục. Giữ đến khi `delete-account` xóa user và dữ liệu notification theo spec 011 FR-019/021, spec 100 FR-016. Không có update nội dung khi event nguồn thay đổi được xác định.

### Validation/behavior

- Client chỉ đọc subcollection của `request.auth.uid`.
- Client update chỉ được phép khi affected keys chỉ chứa `isRead`; không create/delete.
- Tạo qua server; duplicate invariant: một bản ghi trên mỗi `(business event, recipient uid, type)`. CSDL chưa quy định ID/event key để enforce nguyên tử, xem [research R6](research.md#r6-idempotency-và-delivery-retry).
- Query mới nhất trước theo `createdAt`; phân trang cursor. Composite index/page size cần xác nhận trước khi chốt cấu hình.

## User notification preferences

**Đường dẫn**: `users/{uid}.notificationPrefs`, kiểu `Map<String, Boolean>` theo CSDL §4.1.

- Key là các notification type được spec FR-001 hỗ trợ; value quyết định push eligibility, không điều khiển việc tạo/hiển thị inbox.
- Tài khoản mới: mọi loại bật trừ `PROMOTION`; `BOOKING_NEW` luôn bật và UI không cung cấp toggle.
- Người dùng chỉ sửa preference trong hồ sơ của chính họ, trong phạm vi field được Security Rules cho phép. Hiện DB README chỉ nói chủ tài khoản được ghi `notificationPrefs`; cần kiểm tra/cập nhật Rules tương ứng.
- Hành vi fallback cho user cũ khi key bị thiếu và việc DataStore cache map này chưa được chốt.

## FCM token list

**Đường dẫn**: `users/{uid}.fcmTokens`, kiểu `Array<String>` theo DB §4.1.

- Client chủ tài khoản được ghi theo DB §4.1. Không dùng token như căn cứ phân quyền inbox.
- Token registration/refresh/logout/device removal và invalid-token cleanup chưa có lifecycle contract; không tự đặt field thiết bị hoặc timestamp.

## Notification type catalog

Type và sender/recipient/event criteria giữ nguyên theo spec §Danh mục type và clarifications. Danh mục kiểm tra trong [event integration contract](contracts/event-integration.md). Không có entity `delivery`, `outbox`, event ID hoặc receipt nào được thêm vào schema ở đây.

## Indexes

DB README chưa liệt kê index của notifications. Query `createdAt desc` với uid parent scope là mục tiêu. Xác nhận query shape và Emulator index error trước khi thêm `firestore.indexes.json`; không giả định thêm filter `isRead`/type nếu UI chưa yêu cầu.
