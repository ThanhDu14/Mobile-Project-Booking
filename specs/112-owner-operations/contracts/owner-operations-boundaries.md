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
- **Ownership và venue/court (T003–T004, phần đã xác nhận):** kiểm tra UID lấy từ token, `venues/{venueId}.ownerId` đọc phía server và mọi court qua subcollection venue cha. Venue `LOCKED` không cho owner vận hành; venue phải `ACTIVE` để mở drop-in; court phải tồn tại và `isActive == true` khi tạo buổi. Trạng thái owner được đọc lại từ Firestore để quyền bị thu hồi có hiệu lực dù claim cũ còn trong token. Các field này thuộc schema hiện hữu, không thêm field.
- **Feature 080/081 (T008, đối chiếu):** contract 080/081 quy định owner CRUD session nằm trong transaction của caller; owner hủy session phải hoàn 100%, dùng `cancelRegistrationInTx`, sau đó đóng waitlist bằng `closeWaitlistInTx(..., "SESSION_CANCELLED")`; tăng capacity gọi `promoteFromWaitlist`. Tuy nhiên, contract trạng thái registration/waitlist của 080/081 có `SESSION_CANCELLED`, `CLOSED`, `PROMOTED_GRACE` và các field waitlist không có trong danh sách enum/field của [Database README §4.9](../../../docs/design/database/README.md). T008 vẫn mở cho tới khi database owner xác nhận schema hiện hành và các artifact có thẩm quyền được cập nhật; không triển khai các trạng thái/field này trong Feature 112.

## Open gates retained after audit

- **T004:** đã xác nhận ownership qua venue cha, trạng thái `LOCKED`/`ACTIVE`, `court.isActive` và kiểm tra lại trạng thái owner; chưa có quyết định riêng cho venue `HIDDEN` khi owner vận hành, hoặc mã lỗi/response không tiết lộ sự tồn tại của target. Không tự chọn hành vi hay thêm API error.
- **T005:** chưa có hợp đồng duyệt/từ chối và hủy booking của owner, transaction boundary hoặc function owner; `checkin-booking` chỉ có tên và phạm vi trong README, chưa có implementation trong checkout.
- **T006:** chưa có hợp đồng owner CRUD/check-in registration. Voucher có xung đột giữa spec 112 FR-013 (liệt kê `venueId` như field) và schema README §4.6 (đường dẫn venue làm context, field chung không liệt kê `venueId`); Feature 111 cũng gán việc tạo voucher cho chính nó. Cần Feature 111/050 và database owner xác nhận trước khi chọn.
- **T007 (nguồn dữ liệu được người dùng chọn ngày 2026-11-05):** dùng truy vấn trực tiếp booking/slot trong khoảng ngày có giới hạn, không thêm hoặc ghi `dailyStats`. Việc này cần adapter phía server có quyền venue-scoped và projection không chứa `qrToken`; không mở quyền đọc booking trực tiếp trên client. T007 vẫn mở vì Database README/Feature 120 chưa xác nhận endpoint/projection hoặc giới hạn kỳ, và chưa chốt lịch sử giờ khả dụng, mẫu số occupancy, phân bổ booking nhiều court/slot và cách thể hiện dữ liệu cùng ngày. Spec FR-015 hiện còn câu “dùng `dailyStats` khi có dữ liệu”; không tự sửa spec ở bước này, cần đồng bộ artifact nguồn khi chủ spec xác nhận lựa chọn mới.
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

### Hợp đồng cần thống nhất trước implementation

Nguồn đã đọc chưa xác định request/response, function name hoặc contract transaction cho approve/reject/cancel booking, tạo/sửa/hủy drop-in, check-in drop-in và CRUD voucher của owner. Các việc này phải được thống nhất với người phụ trách B/C/D rồi cập nhật contract; tài liệu Plan này không đặt tên endpoint, payload, mã lỗi hoặc idempotency field mới.

## Quy tắc đọc màn hình

- Danh sách booking owner chỉ lấy dữ liệu các venue đã xác minh. Hàng chờ PENDING sắp `approvalDeadline`; lịch tuần/ngày dùng booking, slot và session, múi giờ `Asia/Ho_Chi_Minh`.
- Roster trình bày status registration đã có; owner không duyệt đăng ký riêng, không chỉnh queue/counter.
- Voucher chỉ trong subcollection venue; không dùng `systemVouchers`.
- Thống kê chỉ hiển thị kết quả đúng venue/kỳ; freshness phải thấy được. Chưa thể quy định truy vấn `dailyStats` tới khi schema rõ.

## Luồng kiểm thử hợp đồng

1. Owner đã duyệt/sở hữu venue được đọc; người chưa duyệt, venue khác, venue locked và replay sau thu hồi bị từ chối ở server.
2. Quyết định booking đúng status/deadline/slot thành công; trạng thái cũ, slot lệch hoặc tranh chấp không ghi một phần.
3. Drop-in tạo/sửa/hủy cạnh tranh slot chỉ có một transaction thành công; lỗi bất kỳ slot không để lại session/slot một phần.
4. QR hợp lệ được chấp nhận một lần; QR sai, lặp, sai venue/buổi, registration cancelled và ngoài khung giờ bị từ chối.
5. Owner không thể sửa counter, token, amount, redemption hoặc voucher của venue khác/system.
6. Đối chiếu doanh thu, occupancy, peak hours với dữ liệu booking/slot đã biết; kỳ rỗng và aggregate cũ hiển thị đúng trạng thái.
