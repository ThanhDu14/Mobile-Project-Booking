# Ranh giới dữ liệu và nghiệp vụ: Owner Operations

Đây là bản đồ ranh giới cho planning, không phải hợp đồng HTTP mới. Chỉ ghi tên function đã có trong tài liệu nguồn; không tạo request/response hoặc mã lỗi mới.

## Nguồn dữ liệu hiện có

- `venues/{venueId}` và subcollections `courts`, `priceRules`, `vouchers`.
- `bookings/{bookingId}`, `slots/{slotId}`.
- `dropInSessions/{sessionId}` và registrations theo schema đã thống nhất.
- `venues/{venueId}/dailyStats/{yyyyMMdd}` (schema trường chưa được định nghĩa trong Database README).

Tham chiếu schema đến [Database README](../../../docs/design/database/README.md), model booking ở 040/050/060, drop-in ở 080/081 và model owner ở 111.

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
