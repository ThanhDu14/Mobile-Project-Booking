# Data Model: Feature 120 Admin

Mô hình này chỉ ánh xạ các collection/field hiện có trong `docs/design/database/README.md` và `specs/120-admin/spec.md`. Các field chưa có schema chi tiết không được suy đoán ở đây. Timestamp là Firestore `Timestamp` UTC; enum viết IN HOA; trường 🔒 chỉ server ghi.

## Entities và quan hệ

### `users/{uid}`

Admin đọc và cập nhật các trường quản trị đã được xác định; không tạo hồ sơ user mới.

| Field | Kiểu/giá trị theo nguồn | Quyền/ghi chú |
|---|---|---|
| `role` 🔒 | `PLAYER`, `OWNER`, `ADMIN` | Bản sao custom claim `appRole`; chỉ server cập nhật. |
| `ownerStatus` 🔒 | `NONE`, `PENDING`, `APPROVED`, `REJECTED` | Thu hồi đưa về `NONE`; người nộp không tự ghi. |
| `accountStatus` 🔒 | `ACTIVE`, `LOCKED` | Admin mutation có hiệu lực với đăng nhập theo spec 010. |
| `ownerRevokedReason` | String | Bắt buộc khi thu hồi; spec 011 hiển thị cho người dùng. Spec không đánh dấu 🔒 ở bảng fields của README nhưng server-authoritative theo spec 120. |
| `createdAt`, `updatedAt` | Timestamp | Server timestamps theo quy ước CSDL. |

Claim Firebase `appRole` là phần authorization, không phải field Firestore. Giá trị `ADMIN` do nhóm cấp thủ công; không có luồng tự đăng ký admin.

### `ownerApplications/{id}`

Quan hệ: application thuộc một người nộp; chứa `venueDraft` làm nguồn tạo venue khi duyệt.

| Field | Schema được xác nhận | Quy tắc |
|---|---|---|
| `applicantId` | String | Người nộp. |
| `status` 🔒 | `PENDING`, `APPROVED`, `REJECTED` | Chỉ quyết định từ `PENDING`; resubmit từ `REJECTED` về `PENDING` do Feature 110. |
| `submittedAt` | Timestamp | Dùng với index hàng đợi. |
| `rejectReason` 🔒 | String? | Bắt buộc không rỗng khi từ chối; user xem theo spec 110. |
| `reviewedBy` 🔒 | String | Admin UID thực hiện quyết định. |
| `reviewedAt` 🔒 | Timestamp | Thời điểm xét duyệt phía server. |
| `venueDraft` | Map | Dùng tạo venue khi duyệt; field cấu trúc chi tiết nằm ở DB README 4.2/Feature 110. |

`reviewedBy`/`reviewedAt` là metadata đã có trong README. Không thêm decision token/version hay field audit khác.

### `venues/{venueId}`

Được tạo từ `venueDraft` trong decision approve; liên kết tới owner qua `ownerId`. Feature 120 chỉ quản lý `status` trong phạm vi khóa/mở khóa.

| Field | Giá trị liên quan | Quy tắc |
|---|---|---|
| `ownerId` | Firebase UID | Owner association; tạo từ application. |
| `status` 🔒 | `ACTIVE`, `HIDDEN`, `LOCKED` | Admin có thể khóa; mở khóa về `ACTIVE` hoặc `HIDDEN`; owner không thể đặt `LOCKED`/mở khóa. |

Các field còn lại theo venue schema hiện hành; không dùng thu hồi quyền để đổi trạng thái venue.

### `reports/{reportId}`

Được tạo bởi feature nguồn; admin chỉ đọc và xử lý.

| Field | Kiểu/enum | Quy tắc |
|---|---|---|
| `reporterId` | String | UID người báo cáo. |
| `targetType` | `POST`, `MESSAGE`, `REVIEW`, `VENUE`, `USER`, `DROP_IN` | Không thêm target type. |
| `targetPath` | String | Đường dẫn document đối tượng; resolve trước mutation. |
| `reason`, `detail` | String | Nội dung báo cáo. |
| `status` 🔒 | `OPEN`, `RESOLVED`, `DISMISSED` | Chỉ xử lý từ `OPEN`. |
| `action` 🔒 | `NONE`, `HIDE_CONTENT`, `LOCK_USER`, `LOCK_VENUE` | Chỉ action phù hợp target. |
| `handledBy`, `handledAt` 🔒 | String, Timestamp | Ghi theo Admin xác thực và server time. |

Action mapping: `VENUE → LOCK_VENUE`; `USER → LOCK_USER`; `POST/MESSAGE/REVIEW` có thể `HIDE_CONTENT` khi đối tượng hỗ trợ ẩn. `DROP_IN` chưa rõ có trường/trạng thái ẩn phù hợp; không cho phép trước khi làm rõ. Bỏ qua báo cáo ghi `DISMISSED` + `NONE`.

### Danh mục, thống kê, voucher, notification

- **`districts/{id}`, `amenities/{id}`**: collections được giao nhóm 12 quyền quản lý; database README không cung cấp field schema/CRUD validation. Không định nghĩa field mới trong model này.
- **`systemStats/{yyyyMMdd}`**, **`venues/{venueId}/dailyStats/{yyyyMMdd}`**: Admin đọc số liệu có sẵn; schema metrics/aggregation time chưa được chốt. Hiển thị nguồn và thời điểm chỉ nếu đã có field tương ứng; không suy diễn metric.
- **`systemVouchers/{code}`**: dùng cùng schema với voucher Feature 050: `code`, `type` (`PERCENT`, `FIXED`), `value`, `maxDiscount`, `minOrderTotal`, `validFrom`, `validTo`, `usageLimit`, `usedCount` 🔒, `perUserLimit`, `isActive`. Admin CRUD voucher nhưng `usedCount`/`redemptions` server-owned. Mã duy nhất toàn hệ thống cùng `venues/{venueId}/vouchers/{code}`; cần server kiểm tra trùng trước ghi.
- **`users/{uid}/notifications/{id}`**: schema hiện có `type`, `title`, `body`, `data`, `isRead`, `createdAt`. Feature 120 yêu cầu `SYSTEM`, `OWNER_APPROVED`, `OWNER_REJECTED`, `OWNER_REVOKED`; việc ghi/fan-out thuộc server path Feature 100. Không thêm trường.

## Trạng thái và transition

```text
ownerApplications: PENDING → APPROVED | REJECTED
ownerApplications: REJECTED → PENDING (resubmit bởi spec 110)
users.accountStatus: ACTIVE ↔ LOCKED (Admin)
users owner revocation: role OWNER → PLAYER; ownerStatus APPROVED → NONE
reports: OPEN → RESOLVED | DISMISSED
venues: ACTIVE/HIDDEN → LOCKED; LOCKED → ACTIVE | HIDDEN (Admin)
```

Thu hồi owner không đổi application `APPROVED` hoặc venue status. Đối tượng report không tồn tại/đã đổi trạng thái không được áp dụng action lặp. Quyết định đồng thời phải có đúng một kết quả; cơ chế versioning/idempotency chưa có schema và cần chốt server design.

## Validation, query và indexes

- Application chỉ quyết định khi status hiện tại `PENDING`, applicant tồn tại; reject reason trim không rỗng.
- Report chỉ kết thúc khi status hiện tại `OPEN`; target type/action tương thích và target còn đủ điều kiện.
- Account status nhận `ACTIVE` hoặc `LOCKED`; venue unlock nhận `ACTIVE` hoặc `HIDDEN`.
- Voucher phải theo validation của spec 050; code uniqueness xuyên hai collection cần kiểm tra ở server. Cách atomic bảo đảm uniqueness giữa query và ghi chưa được mô tả.
- Index dự kiến đã ghi trong README: `ownerApplications(status, submittedAt)`; `reports(status, createdAt)`. Query user search, paging, filtering và các index khác cần xác định từ use case, không tự thêm index thiếu căn cứ.

## Security boundary

- User/applicant chỉ đọc hồ sơ của mình; Admin đọc hàng đợi và xử lý theo quyền. Trường 🔒 và state transition được client Rules chặn.
- `owner-docs` là private; applicant của application và Admin mới được cấp signed URL, hết hạn 5 phút.
- Mọi thao tác quản trị xác minh Firebase identity, `appRole == ADMIN`, account hợp lệ và target hiện hành ở server. UI visibility không phải security control.
- Security Rules change cần allow/deny tests cho mỗi collection; Storage RLS cần Supabase local test. Hiện `firestore.rules` chưa có các match cụ thể.

## Known schema gaps

1. Field schema cho `districts`, `amenities`, `systemStats`, `dailyStats` chưa có trong README; metrics, aggregate timestamp/source và CRUD category semantics chưa xác định.
2. `DROP_IN` report target chưa có hide behavior được nêu.
3. Claim + Firestore multi-document writes + notification không có transaction chung; server recovery/idempotency chưa được đặc tả.
