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
- Fallback key thiếu đã duyệt ngày 22/11; chính sách DataStore cache được người dùng duyệt ngày 23/11 như mục dưới.

## FCM token list

**Đường dẫn**: `users/{uid}.fcmTokens`, kiểu `Array<String>` theo DB §4.1.

- Client chủ tài khoản được ghi theo DB §4.1. Không dùng token như căn cứ phân quyền inbox.
- Logout đã có contract 011 research R5/P15 và FcmSignOutHook; registration/refresh/account-switch/device binding và invalid-token cleanup phía server vẫn chưa đủ lifecycle contract. Không tự đặt field thiết bị, token journal hoặc timestamp. Xem decision-review-2026-11-23.md.

## Notification type catalog

Type và sender/recipient/event criteria giữ nguyên theo spec §Danh mục type và clarifications. Danh mục kiểm tra trong [event integration contract](contracts/event-integration.md). Không có entity `delivery`, `outbox`, event ID hoặc receipt nào được thêm vào schema ở đây.

## Indexes

DB README chưa liệt kê index của notifications. Query `createdAt desc` với uid parent scope là mục tiêu. Xác nhận query shape và Emulator index error trước khi thêm `firestore.indexes.json`; không giả định thêm filter `isRead`/type nếu UI chưa yêu cầu.

## Approved preference behavior — 2026-11-22

User approved missing-key fallback for new/existing profiles: all types true except PROMOTION; BOOKING_NEW forced true. Group checkbox: ON if all enabled, OFF if all disabled, MIXED otherwise. Tapping MIXED enables the whole group. Persist individual keys and preserve unrelated types. Firestore authoritative; DataStore cache role remains outstanding.

User approval during Android continuation: add the three owner-application result types as one row in the existing owner group, and SYSTEM as a final row. The UI now exposes all 19 map keys through approved groups; no persisted schema changes.

## Approved cache policy — 2026-11-23

User explicitly approved Firestore as authority with a DataStore cache scoped to UID. Refresh cache only after successful SERVER reads or acknowledged server writes. On network/timeout read failure, use the current UID's saved values with a visible retry message and disabled editing; successful retry restores editing. Missing/forbidden profiles, auth changes, and cancellation do not become cached success. No offline mutation queue or merge-back policy is introduced.

Reuse the existing session DataStore. Cache keys use a `user_cache.notification.<sha256(uid)>.` prefix; the cache stores only resolved per-type booleans and a presence marker. A local `user_cache_epoch` invalidates operations started before sign-out. `DataStoreSessionPrefs.clearOnSignOut()` clears the user-cache namespace and increments the epoch. This path runs for ACCOUNT_DELETED even though authenticated SignOutHooks are skipped; welcome/session recovery behavior outside the cache remains unchanged. These are local cache keys, not Firestore fields or an FCM token store.

Preference writes use a Firestore transaction that reads the existing profile and updates only selected dotted preference keys plus updatedAt, instead of direct update(). Client transactions fail offline and do not persist an offline mutation queue ([Firebase transaction behavior](https://firebase.google.com/docs/firestore/manage-data/transactions)). Cache IO failure must not turn an acknowledged server success into a preference error. BOOKING_NEW remains true and PROMOTION defaults false even when restoring cached values.
