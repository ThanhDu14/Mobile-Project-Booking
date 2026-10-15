# Contract: màn hình hàng chờ

Spec 081 mở rộng các màn hình của spec 080 trong package `feature/dropin` và nav graph `nav_dropin.xml`. Spec này chỉ thêm một màn hình mới. Wireframe: [ui/README.md](../ui/README.md) (`08_drop-in_buoc1_hang-cho.png`).

## 1. Màn hình

| Màn hình | Loại | Story | Safe Args | Ghi chú |
|---|---|---|---|---|
| `DropInDetailFragment` (spec 080) | Mở rộng | US1 | `sessionId` | Vùng `waitlist_container`: khi buổi `FULL` và hàng chờ còn mở thì hiện "N người đang chờ" và nút "Vào hàng chờ". Nếu đang chờ thì hiện "Bạn đang ở vị trí thứ N" và nút "Xem lượt chờ" |
| `DropInConfirmFragment` (spec 080) | Mở rộng | US1 | `sessionId`, `mode: REGISTER \| WAITLIST` | Ở `WAITLIST`: tiêu đề "Vào hàng chờ"; ô số người tối đa 4 (không giới hạn theo chỗ trống); dòng "Chưa giữ chỗ, chưa thanh toán; bạn sẽ được tự động đăng ký khi có đủ chỗ" kèm số tiền sẽ ghi nhận (FR-002) |
| `WaitlistEntryFragment` | **Mới** (`fragment_dropin_waitlist.xml`) | US1, US3, US4 | `sessionId` | Vị trí N / tổng; số người; cách thanh toán; số tiền sẽ ghi nhận; nút "Rời hàng chờ" (hộp thoại xác nhận). Khi lượt đã được đẩy lên thì tự chuyển sang `DropInTicketFragment` |
| `DropInTicketFragment` (spec 080) | Mở rộng | US2 | `sessionId` | Nhãn "Được nhận từ hàng chờ" khi `promotedAt != null`; hiện hạn "Hủy miễn phí đến HH:mm" khi đang trong 30 phút `PROMOTED_GRACE` |
| `MyDropInsFragment` (spec 080) | Mở rộng | US2, US4 | — | Tab "Sắp tới": lượt `WAITLISTED` có nhãn "Đang chờ – vị trí N". Tab "Đã qua": các nhãn "Đã rời hàng chờ", "Không được nhận chỗ", "Buổi bị hủy" (FR-015) |

Mọi màn hình có đủ 4 trạng thái (loading, content, empty, error kèm nút "Thử lại"). Mất mạng thì giữ nguyên lựa chọn đang nhập (FR-016). Chuỗi thêm vào `strings_dropin.xml` với tiền tố `dropin_waitlist_`.

## 2. ViewModel

| ViewModel | Trạng thái và sự kiện |
|---|---|
| `DropInDetailViewModel` | `actions` thêm `JOIN_WAITLIST`, `VIEW_WAITLIST`; `waitlist: WaitlistPosition?` tính từ document buổi (realtime) và lượt của mình |
| `DropInConfirmViewModel` | `mode`; khi `WAITLIST` thì `maxParty = 4`. Nhận `SESSION_HAS_SPOTS` → sự kiện chuyển sang `mode = REGISTER`. Nhận `WAITLIST_CLOSED` → `Failed` |
| `WaitlistEntryViewModel` | `Content(position, total, partySize, amount, paymentMethod)`, `Leaving`, `Left`; sự kiện `Promoted` → mở vé. Nhận `ALREADY_PROMOTED` → báo "Bạn đã được nhận vào buổi" rồi mở vé |
| `MyDropInsViewModel` | Mỗi lượt `WAITLISTED` tính vị trí từ `session.waitlistTickets` (spec 080 đã đọc buổi theo lô) |

## 3. Điểm nối cho nhóm khác

| Nhóm | Việc của họ | Spec 081 cung cấp |
|---|---|---|
| D, spec 112 | Tăng sức chứa, hủy buổi | `promoteFromWaitlist`, `closeWaitlistInTx` trong `_shared/waitlist.ts` ([edge-functions-waitlist.md](edge-functions-waitlist.md#5-hàm-dùng-chung-_sharedwaitlistts)); màn hình chủ sân hiển thị `waitlistCount` và đọc danh sách lượt chờ (`status == WAITLISTED`, sắp theo `waitlistTicket`) |
| D, spec 100 | Gửi `DROPIN_SPOT_OPENED`, `DROPIN_WAITLIST_CLOSED` | Deep link: `smashnow://dropin/ticket/{sessionId}` (được đẩy lên), `smashnow://dropin/session/{sessionId}` (hàng chờ đóng) |
| A, `delete-account` | Xóa tài khoản | `leaveWaitlistInTx(..., "LEFT", "SYSTEM")` cho các lượt đang chờ |
