# UI & Domain Contracts: Quản lý lịch đặt của tôi (spec 060)

**Nguồn quyết định**: [spec.md](../spec.md), đặc biệt Clarifications 2026-11-07. Bốn ảnh trong [ui/](../ui/) là tham chiếu chính. Đây là hợp đồng yêu cầu triển khai, không khẳng định API, endpoint hay tích hợp đã tồn tại; đối chiếu kiến trúc và hợp đồng thực tế trước khi cài đặt.

## Danh sách, chi tiết và điều hướng

- Năm tab: HOLD còn hạn; PENDING; CONFIRMED chưa qua giờ kết thúc; COMPLETED/NO_SHOW; CANCELLED/REJECTED. EXPIRED không xuất hiện. Trang ít nhất 10 đơn, phân trang ổn định cả khi khóa sắp xếp trùng.
- Thứ tự: HOLD/PENDING theo `createdAt` giảm; CONFIRMED theo `startAt` tăng; COMPLETED/NO_SHOW theo `startAt` giảm; CANCELLED/REJECTED theo `updatedAt` giảm. Đồng hồ HOLD/PENDING dựa trên giờ server và deadline hiện hữu.
- Thẻ và chi tiết hiển thị thông tin sân, slot, trạng thái, tiền và nhóm như FR-010/011. Dùng giá từng `items` theo schema thực tế, không tự chia đều giá slot. Có loading, dữ liệu, rỗng, lỗi và thử lại.
- Thanh toán tiếp điều hướng 050 với đơn HOLD và hạn hiện tại, không tạo HOLD hay reset 5 phút. Đặt lại từ lịch sử sang 040 với cơ sở đã chọn; nếu venue ẩn, khóa hoặc tạm ngưng thì báo lỗi và ở lại. Review sang 070 chỉ cho đơn COMPLETED đủ điều kiện, không cho NO_SHOW. Tên args và cấu trúc điều hướng phải đối chiếu implementation thực tế, không giả định đã tồn tại.

## QR và check-in

Chỉ chủ đơn được server cho đọc chi tiết và QR. Đơn CONFIRMED chưa check-in hiển thị QR sinh từ token server; sau check-in thay bằng thời gian check-in và ẩn nút hủy. Chi tiết đã tải có thể hiển thị QR cache offline **chỉ khi tài khoản hiện tại khớp chủ đơn**. Cảnh báo ngoại tuyến không khẳng định QR còn hiệu lực; server check-in 112 vẫn kiểm tra trạng thái và quyền. Không cho tài khoản khác đọc cache chứa token.

## Màn hình hủy riêng

Từ đơn PENDING hoặc CONFIRMED chưa bắt đầu và chưa check-in, mở màn hình riêng theo `ui/06_booking-management_buoc3_huy-dat-san.png`, không thay bằng hộp thoại. Hiển thị số đã trả, hoàn, phạt, chính sách và lý do; bắt buộc chọn lý do có sẵn hoặc nhập nội dung cho mục Khác trước khi xác nhận. Quote phía client chỉ là dự kiến; thông báo kết quả và số tiền thực tế do server trả về, kể cả khi qua mốc 4 giờ trong lúc xác nhận. Giao dịch được gắn nhãn giả lập phục vụ học tập.

### Chính sách tiền

Tính bằng số nguyên VND, dựa trên số tiền thực trả sau voucher và thời gian server:

| Điều kiện | Phạt | Hoàn |
|---|---:|---:|
| PENDING hoặc CONFIRMED còn ít nhất 240 phút | 0 | 100% thực trả |
| CONFIRMED trễ dưới 240 phút, ví giả lập | 30% thực trả, làm tròn xuống bội 1.000 VND | thực trả trừ phạt |
| CONFIRMED trễ, đặt cọc | toàn bộ cọc đã trả | 0 |
| Thanh toán tại sân, chưa trả | 0 | 0 |

Luôn bảo đảm `penaltyAmount + refundAmount == paidAmount`. Ví dụ 111.111đ trả ví: phạt 33.000đ, hoàn 78.111đ. Không hiểu 70% là phép làm tròn độc lập.

### Chuỗi cố định và voucher

Hiển thị từng buổi với trạng thái riêng; cho chọn hủy một/vài buổi hoặc tất cả buổi còn lại đủ điều kiện. Phân bổ cọc/voucher theo tỷ lệ giá trị thực của buổi trên tổng chuỗi, làm tròn đồng và dồn phần dư vào buổi cuối, bảo toàn tổng. Áp dụng chính sách hủy độc lập cho phần tiền đã phân bổ của từng buổi: ví trễ phạt theo quy tắc trên; cọc trễ mất phần cọc buổi đó. Tổng hợp số hoàn khi hủy các buổi còn lại.

Voucher đơn chỉ hoàn lượt khi hủy đủ điều kiện hoàn 100%. Voucher dùng chung của chuỗi chỉ hoàn lượt khi **tất cả buổi liên quan đã hủy và từng buổi đều đủ điều kiện hoàn 100%**; hủy một phần không hoàn lượt. Việc xác định redemption nhóm phụ thuộc hợp đồng dữ liệu 050 thực tế, không tự giả định khóa hay đường dẫn.

## Hợp đồng domain và server

Repository cung cấp đọc danh sách phân trang, quan sát chi tiết realtime, đọc nhóm, lấy giờ server và gửi yêu cầu hủy. Domain có thể tính quote dự kiến thuần Kotlin, nhưng không dùng quote client làm thẩm quyền. Request hủy bao gồm định danh đơn hoặc các buổi cần hủy, lý do và `requestId` duy nhất; danh tính lấy từ phiên xác thực server. Retry sau timeout dùng lại cùng `requestId`. Phản hồi chứa trạng thái và số tiền hoàn/phạt thực tế hoặc lỗi xung đột/quyền; không coi API cụ thể đã tồn tại.

Edge Function hoặc dịch vụ server có thẩm quyền thực hiện transaction nguyên tử: xác thực chủ đơn, kiểm tra trạng thái, chưa check-in, giờ bắt đầu theo giờ server và idempotency; đọc dữ liệu thanh toán/nhóm/voucher cần thiết, tính lại chính sách, cập nhật hủy, hoàn ví và lịch sử, nhả slot và hoàn voucher khi đủ điều kiện. Với chuỗi áp dụng độc lập cho từng buổi và quy tắc voucher nhóm trên. Race với duyệt/từ chối/check-in phải được kiểm tra trong transaction; retry không tạo hoàn tiền trùng. Không cho client tự ghi hủy, ví, giao dịch hoàn hoặc slot để thay thế transaction.

Security Rules giới hạn dữ liệu nhạy cảm cho chủ đơn và các quyền hợp lệ theo đúng cơ sở, không cấp mọi venue chỉ vì role chủ sân. Rules không thay thế tính toán hoặc transaction server; việc xác minh thời gian và số tiền cuối cùng thuộc server.

## Phụ thuộc và trạng thái

- Schema chung `docs/design/database/README.md` và dữ liệu thực tế 040/041/050 cần đối chiếu: giá `items`, cấu trúc nhóm, thanh toán, phân bổ và slot.
- Hợp đồng redemption voucher nhóm 050, endpoint hủy và transaction ví/slot/voucher, nguồn giờ server và điều hướng thực tế cần được xác minh hoặc bổ sung. Chưa có bằng chứng hoàn tất thì để bị chặn; không coi code hủy cũ thiếu hoàn tiền là hoàn thiện.
- Timeout/từ chối và hoàn tiền 050/112, check-in 112, chuyển COMPLETED/NO_SHOW phía server và khóa review 070 là các phụ thuộc cần xác minh tích hợp.
- Danh sách công việc và kiểm thử chưa nghiệm thu xem [tasks.md](../tasks.md).
