# Ranh giới dữ liệu và nghiệp vụ: Owner Operations

Đây là bản đồ ranh giới cho planning, không phải hợp đồng HTTP mới. Chỉ ghi tên function đã có trong tài liệu nguồn; không tạo request/response hoặc mã lỗi mới.

## Nguồn dữ liệu hiện có

- `venues/{venueId}` và subcollections `courts`, `priceRules`, `vouchers`.
- `bookings/{bookingId}`, `slots/{slotId}`.
- `dropInSessions/{sessionId}` và registrations theo schema đã thống nhất.
- `venues/{venueId}/dailyStats/{yyyyMMdd}` (schema trường chưa được định nghĩa trong Database README).

Tham chiếu schema đến [Database README](../../../docs/design/database/README.md), model booking ở 040/050/060, drop-in ở 080/081 và model owner ở 111.

## Các quyết định đã có nguồn được duyệt

- **Danh tính và trạng thái owner (T003):** Firebase custom claim `appRole == OWNER` xác định vai trò; claim `role` dành cho Firebase third-party auth của Supabase. Mỗi lần server vận hành phải đọc trạng thái hiện hành `users/{uid}.ownerStatus == APPROVED`; không suy ra trạng thái từ claim cũ hoặc dữ liệu client. Đây là hợp đồng đã thống nhất ở [Feature 111](../../111-owner-venue-setup/contracts/firestore-boundary.md), [Feature 110](../../110-owner-onboarding/contracts/owner-application-api.md), [Feature 120](../../120-admin/contracts/admin-boundaries.md) và Constitution. Tài khoản owner phải đồng thời có role/status nhất quán theo Feature 110.
- **Ownership và venue/court (T003–T004, ĐÃ ĐÓNG):** kiểm tra UID lấy từ token, `venues/{venueId}.ownerId` đọc phía server và mọi court qua subcollection venue cha. Venue `LOCKED` **và `HIDDEN`** đều không cho owner vận hành — owner chỉ vận hành khi venue ở trạng thái `ACTIVE`. Court phải tồn tại và `isActive == true` khi tạo buổi. Trạng thái owner được đọc lại từ Firestore để quyền bị thu hồi có hiệu lực dù claim cũ còn trong token. Khi owner truy cập venue ngoài quyền sở hữu hoặc venue không tồn tại, server trả **lỗi chung chung không phân biệt** (403/404 giống nhau) — không tiết lộ sự tồn tại của venue. Các field này thuộc schema hiện hữu, không thêm field. **Quyết định ngày 2026-11-06 bởi Minh Nhựt.**
- **Feature 080/081 (T008, ĐÃ ĐÓNG):** contract 080/081 quy định owner CRUD session nằm trong transaction của caller; owner hủy session phải hoàn 100%, dùng `cancelRegistrationInTx`, sau đó đóng waitlist bằng `closeWaitlistInTx(..., "SESSION_CANCELLED")`; tăng capacity gọi `promoteFromWaitlist`. **Database README §4.9 là nguồn có thẩm quyền** — các enum `SESSION_CANCELLED`, `CLOSED`, `PROMOTED_GRACE` và field waitlist trong contract 080/081 **cần được cập nhật vào Database README trước khi Feature 112 dùng**. Feature 112 chỉ triển khai các enum/field đã có trong Database README §4.9; nếu enum cần thiết chưa được cập nhật, Feature 112 giữ gate trên phần code sử dụng enum đó. **Quyết định ngày 2026-11-06 bởi Minh Nhựt.**

## Quyết định đã đóng gates ngày 2026-11-06

Các quyết định dưới đây được **Minh Nhựt** chốt ngày 2026-11-06 với tư cách người phụ trách Feature 112. Các thành viên liên quan cần được thông báo để đồng bộ artifact.

### T004 — Quyền sở hữu venue & mã lỗi (ĐÃ ĐÓNG)

- Venue `HIDDEN` **chặn vận hành** giống `LOCKED`. Owner chỉ vận hành khi venue ở trạng thái `ACTIVE`.
- Response lỗi khi truy cập venue ngoài quyền: **không tiết lộ** sự tồn tại — trả lỗi chung chung (403/404 không phân biệt) cho cả "không có quyền" và "không tồn tại".

### T005 — Hợp đồng booking approve/reject/cancel (ĐÃ ĐÓNG)

- **Function ownership:** Feature 112 tự viết 2 Edge Function mới:
  - `owner-approve-booking` — duyệt booking PENDING → CONFIRMED.
  - `owner-reject-booking` — từ chối booking PENDING → REJECTED.
- **Vị trí:** `supabase/functions/owner-approve-booking/` và `supabase/functions/owner-reject-booking/`.
- **Transaction boundary:** Mỗi function đọc booking + slots trong Firestore transaction, xác minh owner/venue/status/deadline/slot hiện tại, ghi kết quả nguyên tử. Approve giữ slot `BOOKED`; reject nhả slot về `AVAILABLE`.
- **Owner hủy CONFIRMED chưa bắt đầu:** Refund **100% deposit**, nhả đúng slot về `AVAILABLE`, gửi notification cho người chơi qua cơ chế Feature 100.
- Owner phải cung cấp `reason` (không rỗng) khi reject hoặc cancel.
- Owner không tự chuyển booking sang `COMPLETED`/`NO_SHOW` — đó là việc của `booking-finalize`.

### T006 — Hợp đồng drop-in session CRUD, check-in registration & voucher (ĐÃ ĐÓNG)

**Drop-in session CRUD:**
- **Function ownership:** Feature 112 tự viết 3 Edge Function mới:
  - `owner-create-session` — tạo drop-in session + block slots `DROP_IN`.
  - `owner-update-session` — sửa session (giờ, court, capacity, price).
  - `owner-cancel-session` — hủy session + nhả slots + hoàn 100% đăng ký.
- Các function **import shared helpers** từ `_shared/dropin.ts` của Feature 080/081 (`cancelRegistrationInTx`, `closeWaitlistInTx`, `promoteFromWaitlist`).
- Vị trí: `supabase/functions/owner-create-session/`, `owner-update-session/`, `owner-cancel-session/`.

**Check-in registration drop-in (QR):**
- **Function ownership:** Feature 112 tự viết Edge Function mới:
  - `owner-checkin-dropin-registration` — quét QR/nhập mã → chuyển registration sang `CHECKED_IN` (một lần duy nhất).
- Vị trí: `supabase/functions/owner-checkin-dropin-registration/`.

**Voucher CRUD:**
- **Ownership:** Feature 112 sở hữu CRUD voucher venue. Feature 111 chỉ quản lý venue/court/priceRules.
- **venueId:** Là **path context** — voucher nằm tại `venues/{venueId}/vouchers/{code}`, **không cần** field `venueId` trong document. Xung đột với spec 112 FR-013 liệt kê `venueId` như field được giải quyết: FR-013 sẽ hiểu `venueId` từ đường dẫn cha, không phải document field.
- Feature 112 viết Edge Function voucher CRUD (tên cụ thể sẽ xác định khi implement).

### T007 — Nguồn dữ liệu thống kê (ĐÃ ĐÓNG)

- **Nguồn:** Truy vấn trực tiếp `bookings` và `slots` qua server projection venue-scoped. **Không dùng** `dailyStats`.
- **Giới hạn kỳ:** Tối đa **30 ngày** mỗi truy vấn.
- **Projection:** Server projection **không chứa `qrToken`** — chỉ trả các field cần cho doanh thu, occupancy và peak hours.
- **Mẫu số occupancy:** Tổng slot khả dụng trong giờ hoạt động các sân con (loại trừ `BLOCKED`), deduplicate `slotId`, bucket 60 phút theo `Asia/Ho_Chi_Minh`.
- Spec FR-015 câu "dùng `dailyStats` khi có dữ liệu" cần được đồng bộ với chủ spec — quyết định này chọn **không dùng** `dailyStats`.

### T008 — Schema drop-in registration (ĐÃ ĐÓNG)

- **Nguồn có thẩm quyền:** Database README §4.9.
- Các enum `SESSION_CANCELLED`, `CLOSED`, `PROMOTED_GRACE` của Feature 080/081 **cần được cập nhật vào Database README §4.9 trước khi Feature 112 triển khai** code sử dụng chúng.
- Feature 112 chỉ dùng enum/field đã xuất hiện trong Database README phiên bản hiện hành. Nếu database owner chưa cập nhật, phần code tương ứng giữ gate.

## Open gates retained after audit

- **Android owner booking work (T011/T014/T017–T019):** Feature 112 now has normalized owner booking domain types, a queue filter, deadline countdown, StateFlow ViewModel, Owner navigation, list/detail UI and an Android layout test. This is not a backend contract. `ContractPendingOwnerBookingRepository` returns an explicit error; no raw booking reads, guessed field mapper or client write is enabled. Approve/reject controls remain disabled until T005 supplies an owner-safe server projection and transaction endpoint.
- **Booking check-in UI (T031/T033–T037):** Feature 112 now has manual/QR entry UI using Google Play Services Code Scanner, explicit owner payment confirmation input, ViewModel/repository abstraction and fail-closed repository. Database README 8a only names `checkin-booking`; it does not specify request/response fields. No client request mapping is frozen, and no check-in success is claimed until Feature 060/database owner publishes that contract.
- **Read isolation:** Firestore Rules hiện không cho owner đọc booking/session/roster và booking document có `qrToken` nhạy cảm; chưa có contract cách tách dữ liệu QR khỏi các trường hiển thị. Không mở quyền đọc rộng từ client trong Feature 112.
- **Rules handoff to Feature 111:** static audit of `firestore.rules` found that venue document updates reject `LOCKED`, but nested court create/update predicates use `ownsVenue(venueId)` without also requiring the parent venue status to differ from `LOCKED`. The new Feature 112 Rules test asserts that a court under a locked venue is denied; this predicate needs correction and Emulator verification by the Feature 111 owner before that case can pass. Feature 112 does not edit the shared Rules file or change Feature 111 behavior in this implementation.
- **Booking schema reconciliation (T005):** Database README §4.4 calls a held slot `HOLD`, while the current shared `firestore.rules` booking-slot predicates use `HELD`. Database README §4.5 defines booking `items`, `subtotal`, `discount`, and `total`; Feature 060 `data-model.md` instead defines `slotIds`, `subtotalAmount`, `discountAmount`, `paidAmount`, and `remainingAmount`. Feature 040/041 own booking/slot creation and Feature 060 owns player booking/payment data; Feature 112 must not normalize or write either shape until those owners/database owner identify the authoritative deployed schema. This also affects calendar projections and revenue/check-in mappings.
- **Notification handoff (T005/T006):** Feature 100 maps the required booking/drop-in event types and recipients, but explicitly leaves event transport, payload, event identifier, retry, and idempotency envelope open. Feature 112 cannot directly write notification documents or claim after-commit delivery until the producer contract is agreed with Feature 100.

## Yêu cầu với ranh giới server

Mỗi lệnh ghi phải xác minh ID token và quyền owner đã duyệt; lấy ownership từ venue cha, kiểm tra trạng thái venue/court và trạng thái nghiệp vụ hiện tại. Không nhận client làm nguồn tin cậy cho owner UID, tiền, QR, bộ đếm, status, redemption hay quyền sở hữu. Các lần ghi booking/slot và session/slot/registration phải nguyên tử trên Firestore. Không rò rỉ sự tồn tại hoặc nội dung venue khác.

### Function đã có tên trong tài liệu nguồn

| Function | Dữ liệu liên quan | Phạm vi đã nêu |
|---|---|---|
| `checkin-booking` | `bookings` | Check-in booking bằng QR/mã, kiểm tra owner/trạng thái/cửa sổ và ghi nhận thanh toán khi owner xác nhận (Database README 8a, spec 112). |
| `booking-timeout` | `bookings`, `slots` | Tự xử lý booking PENDING quá hạn; owner không thay thế cron. |
| `booking-finalize` | `bookings` | Chuyển COMPLETED/NO_SHOW; owner không tự chuyển. |
| `register-drop-in`, `cancel-drop-in` | session/registration | Luồng người chơi 080/081; không phải API owner CRUD. |
| `send-notification` | notifications | Cơ chế gửi thông báo hiện có theo Feature 100. |

### Function mới do Feature 112 sở hữu (quyết định 2026-11-06)

| Function | Dữ liệu liên quan | Phạm vi |
|---|---|---|
| `owner-approve-booking` | `bookings`, `slots` | Duyệt booking PENDING → CONFIRMED; giữ slot BOOKED; xác minh owner/venue/deadline/status. |
| `owner-reject-booking` | `bookings`, `slots` | Từ chối booking PENDING → REJECTED; nhả slot → AVAILABLE; yêu cầu `reason` không rỗng. |
| `owner-create-session` | `dropInSessions`, `slots` | Tạo drop-in session + block slots `DROP_IN`; xác minh court ACTIVE, venue ACTIVE. |
| `owner-update-session` | `dropInSessions`, `slots` | Sửa session (giờ, court, capacity, price); xung đột slot rollback. |
| `owner-cancel-session` | `dropInSessions`, `slots`, registrations | Hủy session + nhả slots + hoàn 100% đăng ký; dùng shared helpers 080/081. |
| `owner-checkin-dropin-registration` | registrations | QR/mã → `CHECKED_IN` một lần; xác minh owner/session/registration. |
| Voucher CRUD (tên TBD) | `venues/{venueId}/vouchers` | CRUD voucher venue; mã duy nhất toàn hệ thống; không chạm `systemVouchers`. |

### Hợp đồng đã thống nhất

Các hợp đồng dưới đây thay thế mục "Hợp đồng cần thống nhất trước implementation" trước đây. Quyết định được chốt ngày 2026-11-06 bởi Minh Nhựt.

- **Approve/reject/cancel booking:** Feature 112 sở hữu function `owner-approve-booking` và `owner-reject-booking`. Cancel CONFIRMED refund 100%, nhả slot, notification. `reason` bắt buộc khi reject/cancel.
- **Drop-in session CRUD:** Feature 112 sở hữu `owner-create-session`, `owner-update-session`, `owner-cancel-session`; import `_shared/dropin.ts`.
- **Check-in drop-in registration:** Feature 112 sở hữu `owner-checkin-dropin-registration`.
- **Voucher CRUD:** Feature 112 sở hữu; voucher path context `venues/{venueId}/vouchers/{code}`; không thêm `venueId` document field.
- **Thống kê:** Truy vấn trực tiếp booking/slot, giới hạn 30 ngày, projection không chứa `qrToken`.

## Quy tắc đọc màn hình

- Danh sách booking owner chỉ lấy dữ liệu các venue đã xác minh. Hàng chờ PENDING sắp `approvalDeadline`; lịch tuần/ngày dùng booking, slot và session, múi giờ `Asia/Ho_Chi_Minh`.
- Roster trình bày status registration đã có; owner không duyệt đăng ký riêng, không chỉnh queue/counter.
- Voucher chỉ trong subcollection venue; không dùng `systemVouchers`.
- Thống kê chỉ hiển thị kết quả đúng venue/kỳ; freshness phải thấy được. Dùng truy vấn trực tiếp booking/slot, giới hạn 30 ngày.

## Luồng kiểm thử hợp đồng

1. Owner đã duyệt/sở hữu venue được đọc; người chưa duyệt, venue khác, venue locked/hidden và replay sau thu hồi bị từ chối ở server.
2. Quyết định booking đúng status/deadline/slot thành công; trạng thái cũ, slot lệch hoặc tranh chấp không ghi một phần.
3. Drop-in tạo/sửa/hủy cạnh tranh slot chỉ có một transaction thành công; lỗi bất kỳ slot không để lại session/slot một phần.
4. QR hợp lệ được chấp nhận một lần; QR sai, lặp, sai venue/buổi, registration cancelled và ngoài khung giờ bị từ chối.
5. Owner không thể sửa counter, token, amount, redemption hoặc voucher của venue khác/system.
6. Đối chiếu doanh thu, occupancy, peak hours với dữ liệu booking/slot đã biết; kỳ rỗng và aggregate cũ hiển thị đúng trạng thái.
