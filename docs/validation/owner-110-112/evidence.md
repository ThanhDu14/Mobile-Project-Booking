# Bằng chứng thao tác thiết bị thật

Thiết bị vivo V2247, package `com.example.booking_app`. Tài khoản OWNER và cơ sở hiện tại được người dùng cho phép kiểm thử. Ảnh chụp là một thời điểm của giao diện; các xác nhận lưu/dọn dữ liệu bên dưới còn được đối chiếu bằng phiên CLI độc lập trong `logs/owner-hosted-data-verification.log`.

| Ca kiểm thử | Ảnh | Kết quả và giới hạn |
|---|---|---|
| Mở dashboard trước/sau sửa location cũ | `before-home-location-error.png`, `after-home-live.png` | Sau sửa đọc được cơ sở, hiển thị 0 booking thực |
| Tạo voucher | `before-voucher-rules-error.png`, `voucher-server-result.png` | Từ lỗi quyền/index tới mã AUDIT lưu trên Firestore thật |
| Tắt voucher | `voucher-disabled.png` | CLI xác nhận isActive=false, usedCount=0 |
| Danh sách và chi tiết cơ sở | `venue-list-live.png`, `venue-details-live.png` | Các lối vào cấu hình/kinh doanh có dữ liệu cơ sở thật |
| Chọn tọa độ | `venue-map-picker.png` | Mở bản đồ tương tác; không thay tọa độ gốc của cơ sở trong kiểm thử |
| Thêm sân | `court-created-real.png` | Court AUDIT lưu thật, sau kiểm thử ngừng hoạt động |
| Nút thao tác bị vỡ chữ | `before-court-buttons.png`, `after-court-buttons.png` | Trước: Xuống/Hoạt động lại tách giữa từ. Sau: chia hai hàng, đủ chữ trên thiết bị thật |
| Thêm bảng giá | `price-rule-created-real.png` | CLI xác nhận 100000/giờ và minPrice=100000; sau đó xóa, minPrice=null |
| Khóa/mở slot | `slot-locked-real.png`, `slot-unlocked-real.png` | CLI cuối xác nhận slots=[]; ảnh có thể được chụp trước listener hoàn tất, CLI là xác nhận cuối |
| Upload và lưu ảnh | `venue-photo-saved-real.png` | Từng lưu 2 ảnh; sau gỡ ảnh thử còn 1, HEAD ảnh gốc HTTP 200 |
| Tạo buổi vãng lai miễn phí | `dropin-created-real.png` | Chủ nhật 15/11 08:30–09:00, 2 chỗ, 0 VND, note AUDIT |
| Sửa sức chứa buổi | `dropin-updated-real.png` | Mở lại roster hiển thị 3 chỗ |
| Hủy buổi | `dropin-cancelled-real.png` | CLI xác nhận CANCELLED, capacity=3, slots=[] |
| Thống kê không có booking | `statistics-real-empty.png` | Empty thật; chưa chứng minh doanh thu dương bằng booking trả tiền trên hosted backend |
| Validation form PLAYER | `onboarding-empty-validation.png` | Để trống rồi Xem lại: lỗi từng trường, chưa tạo application |
| Chọn quận/huyện | `onboarding-district-before.png`, `onboarding-district-fixed.png` | Popup cũ không mở; dialog mới chọn được danh mục Firebase |
| Ghim vị trí | `onboarding-map-pin.png` | Chạm bản đồ, tọa độ được đưa vào draft |
| Upload fixture | `onboarding-files-uploaded.png` | Một ảnh cơ sở và một giấy tờ TEST đã upload; Storage đối chiếu 2 × 6788 bytes |
| Preview trước nộp | `onboarding-review-top.png`, `onboarding-review-files.png` | Đúng dữ liệu; quay lại sửa người đại diện và xem lại giữ tệp |
| Hồ sơ PENDING | `onboarding-pending-real.png` | Firestore độc lập xác nhận 1 application, status PENDING, user PLAYER/PENDING |
| Xem giấy tờ private | `onboarding-document-before.png`, `onboarding-document-fixed.png` | Sửa hộp thoại ảnh trắng; sau sửa thấy fixture thật, download trùng SHA-256 |
| PLAYER chưa được quản lý | `onboarding-player-home.png`, `onboarding-owner-deeplink-denied.png` | Trang chủ vẫn PLAYER; deep link venue bị từ chối quyền |

Ảnh tạm có hậu tố debug hoặc ghi nhận upload đang chờ không được dùng làm bằng chứng thành công. Luồng upload/hủy/ghi có thể mất hàng chục giây trên kết nối 4G trong phiên kiểm thử.

Đã kiểm thử ADMIN từ chối/duyệt, PLAYER sửa và nộp lại cùng application ID; ảnh `onboarding-admin-rejected.png`, `onboarding-resubmit-review.png`, `onboarding-approved-owner-home.png`.

| Ca bổ sung 15/11 | Ảnh/log | Kết quả thực tế |
|---|---|---|
| Giờ booking bị ép hẹp | `booking-time-before.png`, `booking-time-fixed.png` | Giờ và hạn xử lý riêng dòng; row không bị thay mỗi giây |
| Nhãn bộ lọc | `booking-filter-labels-fixed.png` | Chờ duyệt, Sắp tới, Lịch sử hiển thị đầy đủ trên APK cuối |
| Chi tiết booking | `booking-details-real.png` | Đúng mã TEST, 1 slot, tổng 2.000đ, cọc 0đ từ backend |
| Lý do từ chối | `booking-reject-reason-required.png` | Trống bị chặn; lý do AUDIT được gửi và đơn REJECTED |
| Preview thu tiền | `booking-checkin-payment-preview.png` | Chỉ preview, chưa ghi checkedInAt; đây là ảnh khi bàn phím còn mở |
| Check-in có xác nhận | `booking-checkin-success.png` | PAID, còn thu 0đ; CLI xác nhận thời gian server |
| Check-in trùng | `booking-checkin-duplicate.png` | Server chặn cùng booking |
| Thống kê có tiền TEST | `statistics-positive-real.png`, `statistics-after-restart.png` | 2.000đ, 50%, peak 23:00/30 phút; giữ sau restart |
| Kết thúc dữ liệu TEST | `audit-venue-hidden.png`, `logs/owner-onboarding-final-state.log` | Venue AUDIT HIDDEN, hồ sơ APPROVED, ảnh HEAD200 |
| Booking cuối | `logs/owner-bookings-final-state.log` | PAID/check-in, REJECTED và CANCELLED; hai đơn chưa thu hoàn 0 |

Chưa kiểm thử camera quét QR hợp lệ, hoàn tiền từ booking đã trả tiền trên hosted backend, hay usability với người tham gia. Check-in dương tính dùng mã booking nhập tay. Các booking TEST do script fixture quản trị tạo, không chứng minh PLAYER checkout.
