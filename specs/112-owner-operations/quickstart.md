# Hướng dẫn xác nhận thiết kế: Feature 112

Đây là kế hoạch kịch bản kiểm chứng để triển khai sau này, không phải lệnh test có thể chạy ngay: source hiện chưa có module owner hoặc Edge Functions nghiệp vụ của Feature 112. Không có test nào được chạy trong bước Plan.

## Điều kiện chuẩn bị khi triển khai

- Android app, Firebase Auth/Firestore Emulator, Supabase local/Edge Functions và test dependencies theo Constitution.
- Fixture venues/courts/priceRules, owner được duyệt, booking/slots, drop-in registrations, voucher và thống kê. Chỉ dùng field/enum đã được schema chung duyệt.
- Hợp đồng server và schema `dailyStats` được nhóm phụ trách chốt; không điền bằng mock API tự chế.

## Kịch bản xác nhận

1. **Booking queue và lịch**: tạo PENDING hợp lệ + slot BOOKED; kiểm tra sắp theo deadline và lịch ngày/tuần. Duyệt thành công giữ slot; từ chối giải phóng đúng slot. Gửi lại sau deadline hoặc sau khi status/slot đổi phải không ghi đè và UI tải trạng thái mới.
2. **Quyền**: thử cùng đọc/ghi với owner không duyệt, venue khác, court ngoài venue, venue locked và quyền thu hồi trong lúc màn hình mở. Server từ chối, không rò dữ liệu chéo.
3. **Drop-in**: tạo trên slot trống trong giờ vận hành; xác nhận toàn bộ slot 30 phút `BLOCKED/DROP_IN`. Tạo đồng thời hoặc có một slot xung đột phải thất bại toàn phần. Sửa giờ/court và hủy phải đồng bộ slot; tăng capacity phải tuân thủ queue 081.
4. **Rosters và QR**: xem đủ status registration; quét QR hợp lệ chỉ check-in một lần. QR sai, lặp, sai session, cancelled hoặc ngoài quyền không đổi trạng thái.
5. **Booking check-in**: trong cửa sổ hợp lệ, quét QR/nhập booking code và xác nhận thu tiền theo FR-012a; kiểm tra server ghi `checkedInAt`/`PAID` theo hành động. `COMPLETED`/`NO_SHOW` do flow server hiện có.
6. **Voucher**: tạo voucher venue hợp lệ; duplicate code toàn hệ thống, sai điều kiện hoặc venue ngoài quyền bị từ chối. Xác minh owner không ghi `usedCount`/redemptions; áp dụng/giá giảm do Feature 050.
7. **Thống kê**: đối chiếu từng nhóm `DEPOSITED`, `PAID`, `REFUNDED`, canceled/rejected/expired; occupancy loại BLOCKED và không đếm slot hai lần; peak theo bucket 60 phút địa phương. Empty/stale/error không hiển thị thành số liệu mới.
8. **Trạng thái UI/network**: kiểm tra loading, empty, validation, stale/conflict, permission, retry và mất mạng; sau kết quả không xác định tải lại trước khi gửi lại để tránh trùng.

## Lệnh kiểm thử

Chưa liệt kê lệnh runnable vì implementation, tên function owner và test targets chưa tồn tại trong source. Khi implementation có test harness, dùng mẫu đã thiết lập ở `specs/080-drop-in-session/quickstart.md` và `specs/081-drop-in-waitlist/quickstart.md`, bổ sung Firebase Emulator/Supabase local và cập nhật các lệnh thực tế tại đây. Không chạy `/speckit-tasks` hoặc implementation trong bước này.
