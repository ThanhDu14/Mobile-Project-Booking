# Mô hình dữ liệu: Owner Operations

**Ngày**: 2026-10-16 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

Feature này chỉ đọc/ghi schema hiện có trong [Database README](../../docs/design/database/README.md). Không thêm collection, field, enum, quan hệ hoặc ID convention. Trường 🔒 chỉ được ghi bởi server theo README.

## 1. Ngữ cảnh phân quyền (không phải entity mới)

Chủ thể là Firebase UID đã xác thực, có quyền owner được duyệt; venue cha phải xác nhận `venues.ownerId == uid`. Mọi court và dữ liệu con được truy cập qua venue đó. Feature 112 không tự sửa role, `ownerStatus`, venue/court status hoặc trạng thái admin. Tên claim/trường có mâu thuẫn tài liệu và cần chốt (xem [contract](contracts/owner-operations-boundaries.md)).

## 2. Booking — `bookings/{bookingId}`

Schema nguồn: README mục 4.5; tạo và trạng thái gốc thuộc 040/050/060. Feature 112 đọc booking của venue owner và chỉ thực hiện các chuyển trạng thái được spec cho phép:

| Thao tác | Điều kiện hiện có | Kết quả nghiệp vụ |
|---|---|---|
| Owner duyệt | `PENDING`, trước `approvalDeadline`, venue thuộc owner, booking/slot còn khớp | `CONFIRMED`; slot tương ứng giữ `BOOKED` và `bookingId` |
| Owner từ chối | Điều kiện như trên | `REJECTED`; nhả các slot đúng booking trong cùng giao dịch; refund/notification theo hợp đồng hiện có |
| Owner hủy | `CONFIRMED`, chưa bắt đầu, chưa check-in, có lý do | `CANCELLED`, `cancelledBy=OWNER`, `refundAmount`; nhả slot và thông báo trong cùng luồng nghiệp vụ |
| Check-in | `CONFIRMED`, chưa check-in, đúng cửa sổ và đúng booking | Ghi `checkedInAt`; thu tiền chỉ khi owner xác nhận `PAID`; không tự chuyển `COMPLETED` |

Các trạng thái tự động `REJECTED` timeout, `COMPLETED`, `NO_SHOW` thuộc server job hiện có; client không giả lập. Đơn tuần là từng booking riêng theo `recurringGroupId` (041).

## 3. Lịch ngày/tuần — projection, không phải entity

Nguồn: `bookings`, `slots`, `dropInSessions`, `venues/{venueId}/courts`. Thời gian hiển thị `Asia/Ho_Chi_Minh`; lịch nhóm theo ngày/court và dùng start/end nghiệp vụ hiện có.

- `PENDING` có nhãn chờ duyệt; `CONFIRMED` được chấp thuận; trạng thái lịch sử giữ phân biệt.
- `HOLD` không phải booking đã xác nhận; cảnh báo (nếu có) hết theo `holdExpiresAt`.
- `BLOCKED` thể hiện không khả dụng theo blockReason; `DROP_IN` hiển thị từ session, không phải booking người chơi.
- Không thêm document/calendar field hoặc quy tắc layout.

## 4. Slots — `slots/{courtId}_{yyyyMMdd}_{HHmm}`

Dùng schema README mục 4.4: `venueId`, `courtId`, `date`, `startAt`, `endAt`, `status`, `bookingId`, `holdBy`, `holdExpiresAt`, `blockReason`. Slot vắng document là trống. Feature 112 phối hợp ghi `BOOKED` của booking và `BLOCKED`/`DROP_IN` của session trong transaction; không ghi đè slot xung đột. Độ dài là 30 phút.

## 5. Buổi drop-in và đăng ký

### `dropInSessions/{id}`

Chỉ field theo README 4.9: `venueId`, `ownerId`, `districtId`, `courtIds`, `date`, `startAt`, `endAt`, `level`, `capacity`, `registeredCount` 🔒, `pricePerPerson`, `status`, `note`. Tạo buổi theo schema/enum hiện có; owner không tự sửa `registeredCount`. Các điều kiện thời gian, level và state machine kế thừa 080/081/spec 112. Không chép thêm các field mới đang được đề xuất riêng trong tài liệu 080 nếu chưa đồng bộ README chung.

### `dropInSessions/{id}/registrations/{uid}`

Dùng các field/enum đã định nghĩa ở README và spec 080/081: party size, status (`REGISTERED`, `WAITLISTED`, `CANCELLED`, `CHECKED_IN`), payment, amount, `qrToken`, `waitlistedAt`, `requestId` cùng các trường hiện có khác nếu README được đồng bộ chính thức. Owner đọc danh sách; không sửa party size, amount, counter, QR hay redemption. Check-in chỉ làm chuyển trạng thái hợp lệ sang `CHECKED_IN` một lần. Do model 080 có nhiều field `[MỚI]` chưa thấy trong README hiện tại, phải chốt nguồn schema trước implementation; không coi danh sách mở rộng đó là schema đã duyệt trong plan này.

## 6. Voucher venue — `venues/{venueId}/vouchers/{code}`

Schema README 4.6: `code`, `type` (`PERCENT`, `FIXED`), `value`, `maxDiscount`, `minOrderTotal`, `validFrom`, `validTo`, `usageLimit`, `usedCount` 🔒, `perUserLimit`, `isActive`; lượt đổi ở `redemptions/{bookingId}`. ID là code in hoa, duy nhất toàn hệ thống theo server. Quy tắc validation/cách áp dụng và field giá trị kế thừa Feature 050. Owner không ghi `usedCount`/redemptions và không truy cập `systemVouchers`.

## 7. Thống kê venue

Nguồn nghiệp vụ là booking và slot thực tế, cùng `venues/{venueId}/dailyStats/{yyyyMMdd}` nếu schema đó được xác nhận. Công thức và lọc trạng thái nằm trong spec FR-016–019. Database README hiện chưa liệt kê field/schema `dailyStats`; vì vậy không khai báo entity/field aggregate hoặc giả định query cụ thể tại đây. `priceRules` chỉ hỗ trợ nhận diện giờ có thể đặt, không thay thế tiền thực tế của booking.

## 8. Validation và bất biến

- Mọi truy vấn được giới hạn venue do server xác minh; court/session thuộc venue đó.
- Booking quyết định dựa vào trạng thái và deadline hiện hành; slot bị kiểm tra lại trong cùng giao dịch.
- Tạo/sửa/hủy drop-in thành công toàn phần hoặc không ghi gì; không vượt slot/capacity; promotion hàng chờ theo 081.
- QR chỉ chấp nhận token hợp lệ, đúng đối tượng/buổi, chưa dùng và trong trạng thái/thời gian hợp lệ; chi tiết check-in booking theo FR-012a.
- Doanh thu chỉ dùng trạng thái thanh toán và amount field theo FR-016; occupancy/peak theo FR-017/018, timezone địa phương.
- Không thêm validation nghiệp vụ ngoài spec hoặc feature/schema liên quan.
