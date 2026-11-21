# Implementation mapping: Feature 112

Cập nhật 2026-11-21. Đối chiếu production DI trong OwnerModule; các ContractPending repository không được bind. Tên OwnerHomePlaceholderFragment là dashboard thật, không phải bằng chứng thiếu tính năng.

| Luồng | Android → repository | Boundary dữ liệu/ghi |
|---|---|---|
| Dashboard/chọn venue | OwnerHomeDashboardViewModel; owner venue repository | Đọc danh sách owned venue từ server; truyền context venue cho các màn vận hành |
| Booking và chi tiết | OwnerBookingsFragment/ViewModel → OwnerBookingRepositoryImpl | Firestore query theo venue được Rules giới hạn; chi tiết Source.SERVER; owner-approve/reject/cancel-booking dùng owner-booking-command transaction |
| Check-in booking | OwnerBookingCheckInFragment → BookingCheckInViewModel → BookingCheckInRepositoryImpl | checkin-booking; preview số tiền còn lại, xác nhận thu tiền, checkedInAt/PAID nguyên tử; chống replay |
| Lịch ngày/tuần | OwnerCalendarViewModel → FirestoreOwnerCalendarRepository | Slots venue/date; giữ court inactive cho lịch sử; không ghi booking từ calendar |
| Drop-in | Form/list/roster → OwnerDropInViewModel → OwnerDropInRepositoryImpl | owner-create/update/cancel-session; slot DROP_IN nguyên tử; helper waitlist 081 và dropin-close được giữ |
| Roster/check-in | OwnerDropInRosterFragment → cùng repository | Session phải khớp venue; roster đọc server; owner-checkin-dropin-registration xác minh QR/ticket, quyền và trạng thái trong transaction |
| Voucher | OwnerVoucherViewModel → FirestoreOwnerVoucherRepository | Read scoped Firestore; owner-create-voucher xử lý CREATE/UPDATE/SET_ACTIVE; không client ghi counters/redemptions |
| Thống kê | OwnerStatisticsViewModel → FirestoreOwnerStatisticsRepository | owner-statistics-source projection không QR/identity; cấu hình availability cùng transaction; tính tiền từ bookings, occupancy/peak từ slots thực |

## Sửa qua convergence

- Context venue được truyền từ dashboard và giữ trong SavedStateHandle. Refresh khi mở booking không hủy lượt tải danh sách venue; voucher không tự trở về venue đầu tiên.
- Form drop-in giữ trường nhập, lựa chọn sân và trạng thái populate qua recreation; form voucher giữ bản sửa. Loading/duplicate-submit guards ngăn gửi trước khi context sẵn sàng.
- Drop-in CREATE lặp cùng nội dung trả session thật đã tạo; payload khác hoặc session đã đổi trả conflict. Không dựng session ID thành công ở client.
- Voucher CREATE lặp cùng payload ở đúng venue không tạo bản mới hoặc reset usedCount. Mã đã đổi/thuộc venue khác vẫn bị từ chối.
- Helper quyền đọc profile hiện tại và claims, kể cả ADMIN, trong transaction; tài khoản xóa/khóa/thu hồi không được dùng token cũ để vận hành.
- Check-in drop-in áp dụng quyết định người dùng 2026-11-21: từ startAt - 30 phút đến trước endAt, QR và ticket đều dùng cùng boundary.
- Thống kê trả cấu hình mới mỗi snapshot, có computedAt/partial period; chọn custom mở form trước khi xác nhận; range không hợp lệ hủy observation cũ.
- Lịch giữ sân inactive để không làm mất hàng chứa booking cũ.

## Kiểm chứng

Nguồn kết quả hiện tại: [báo cáo Feature 11](../../docs/validation/feature11-2026-11-21/README.md). Chạy đủ ba job theo ci.yml và APK instrumentation riêng trên điện thoại thật với Firebase Emulator/Supabase local. Không dùng kết quả lịch sử để khẳng định bản hiện tại đạt.

SLA 4G và usability với người tham gia cần đo riêng; không suy ra từ unit test hoặc thao tác instrumentation.
