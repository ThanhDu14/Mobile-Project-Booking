# Nghiên cứu kỹ thuật: Feature 112

**Ngày**: 2026-10-16 · **Nguồn chính**: `spec.md`, Constitution, Database README, spec 110/111/040/041/050/060/080/081/100/120, hướng dẫn phân công W1–W2.

## R1 — Quyền owner và ranh giới server

**Quyết định:** Áp dụng kiểm tra server cho từng thao tác: danh tính token → vai trò owner đã duyệt → đọc venue từ Firestore → so `venues.ownerId` với UID → kiểm tra trạng thái venue/court và điều kiện nghiệp vụ. Không tin `ownerId` hay ID do app gửi nếu chưa đối chiếu dữ liệu.

**Cơ sở:** Constitution II, spec 112 FR-001/002, database README mục 7; 111 cũng cấm client trực tiếp ghi nghiệp vụ nhạy cảm.

**Lựa chọn khác:** Chỉ ẩn UI theo role hoặc dựa vào `ownerId` request. Bị loại vì không bảo vệ direct/replay request.

**Cần chốt:** Tài liệu dùng `role`, `appRole`, `ownerStatus` với khác biệt chữ hoa/thường. Dùng nguyên nghĩa nghiệp vụ nhưng chưa chọn nguồn claim/trường cho đến khi nhóm xác thực/database xác nhận.

## R2 — Booking và tính nguyên tử

**Quyết định:** Hàng đợi đọc booking `PENDING` theo `approvalDeadline`. Quyết định được server kiểm tra lại status, deadline, quyền và quan hệ slot trong transaction; approve giữ `BOOKED`, reject chỉ nhả slot khớp booking. Dùng máy trạng thái và timeout hiện có; không thêm trạng thái.

**Cơ sở:** Constitution III; schema booking/slot và `booking-timeout`; spec 112 FR-003–006. Báo stale/conflict và đọc lại trạng thái mới.

**Lựa chọn khác:** Client đọc rồi ghi booking/slot tách rời. Bị loại vì race condition và vi phạm nguyên tắc toàn vẹn.

**Cần chốt:** Tên và request/response của function quyết định booking chưa được database README định danh. FR-004a yêu cầu hủy booking đã xác nhận; phải thống nhất với nhóm B về function giao dịch và chính sách hoàn tiền đã nêu trong spec.

## R3 — Lịch hợp nhất

**Quyết định:** Tạo projection giao diện từ `bookings` và `slots` trong venue/khoảng ngày; dùng `startAt/endAt`, court và trạng thái booking làm thông tin nghiệp vụ. Quy đổi và nhóm ngày bằng `Asia/Ho_Chi_Minh`. Drop-in hiển thị từ `dropInSessions`, không giả thành booking. `HOLD` chỉ là cảnh báo tạm theo `holdExpiresAt`.

**Cơ sở:** FR-007, mục “Lịch tổng hợp ngày/tuần”, slot ID và các index booking/slot trong README.

**Lựa chọn khác:** Lưu lịch tổng hợp/collection lịch mới. Bị loại vì spec không yêu cầu schema mới và lịch là phép đọc các nguồn hiện có.

**Cần chốt:** Truy vấn tuần theo venue qua index hiện có; nếu Firestore không hỗ trợ truy vấn kết hợp cần thiết thì bổ sung index (không phải schema) sau khi xác nhận query thực tế.

## R4 — Drop-in và slot

**Quyết định:** Tái sử dụng schema và helper trong 080/081. Tạo/sửa/hủy buổi xử lý các slot 30 phút, registration, refund và promotion từ waitlist trong transaction server phù hợp. Owner đọc roster; đăng ký/hàng chờ vẫn theo luồng 080/081; check-in ghi trạng thái `CHECKED_IN` một lần.

**Cơ sở:** Database README mục 4.4/4.9; 080 data-model/contract; 081 waitlist contract; spec 112 FR-008–012.

**Lựa chọn khác:** Client ghi slot và buổi từng bước, hoặc owner tự chỉnh `registeredCount`/registration. Bị loại vì có thể khóa một phần hoặc vượt capacity.

**Cần chốt:** Chưa thấy function/hợp đồng check-in drop-in và thao tác owner create/update/cancel trong nguồn đã định danh đầy đủ. 080 xác nhận check-in thuộc 112, nhưng không đặt tên API cụ thể.

## R5 — Voucher

**Quyết định:** Dùng `venues/{venueId}/vouchers/{code}` và schema mục 4.6; giữ uniqueness toàn hệ thống, `usedCount`/redemptions do server, áp dụng voucher theo Feature 050. Không tạo voucher hệ thống.

**Cơ sở:** spec 112 FR-013/014, README 4.6/7, Feature 050.

**Lựa chọn khác:** Collection riêng cho Feature 112 hoặc cập nhật `systemVouchers`. Bị loại vì trái schema và quyền admin.

**Cần chốt:** README mô tả owner tạo voucher tại Feature 111 nhưng 112 nhận thao tác; làm rõ ownership giữa spec. Function server cho create/update chưa được định danh thống nhất.

## R6 — Thống kê

**Quyết định:** Tính các chỉ số booking thực tế theo định nghĩa FR-016–018; không dùng giá từ `priceRules` làm doanh thu. Ưu tiên `dailyStats` chỉ sau khi schema và freshness contract được xác nhận; nếu aggregate chưa có/cũ phải nói rõ và cho refresh theo spec, không gắn nhãn realtime.

**Cơ sở:** spec 112, README có `dailyStats`/`daily-stats` nhưng không có field schema; assignment giao `dailyStats` cho D.

**Lựa chọn khác:** Bịa field aggregate hoặc coi mọi `total` là tiền thu. Bị loại vì sai nguồn dữ liệu/spec.

**Cần chốt:** Cấu trúc `dailyStats`, nguồn mẫu số khả dụng, timezone/ngày của aggregate và ai cập nhật. `daily-stats` hiện được README mô tả là cron tổng hợp; chưa đủ định nghĩa để kết luận nó phục vụ được từng metric.

## R7 — Kiểm thử

**Quyết định:** Kiểm thử theo lớp: unit cho phép tính/bucket và UiState; repository mapping/query; Edge Function/Firestore Emulator cho quyền, trạng thái, xung đột và transaction cạnh tranh; Supabase local cho function; UI smoke cho luồng owner. Mỗi thay đổi Rules có ca cho phép và từ chối theo Constitution.

**Cơ sở:** Constitution IV, spec 112 acceptance/edge cases và mẫu 080/081.

**Lựa chọn khác:** Chỉ kiểm thử UI hoặc chỉ kiểm tra happy path. Bị loại vì không phát hiện lỗi quyền/race.

**Lưu ý:** Đây là chiến lược Plan; không chạy test và không đặt tên test/API chưa có nguồn.
