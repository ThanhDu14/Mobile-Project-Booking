---
description: "Danh sách task triển khai spec 060 – Quản lý lịch đặt của tôi (My Bookings)"
---

# Tasks: Quản lý lịch đặt của tôi (Spec 060)

Nguồn quyết định: [spec.md](spec.md), đặc biệt Clarifications 2026-11-07; bốn ảnh trong [ui/](ui/) là tham chiếu thiết kế chính. Danh sách dưới đây là kế hoạch, **mọi task đều chưa nghiệm thu**; checkbox không chứng minh trạng thái code hiện có. Chỉ tích hợp tối thiểu với feature liên quan và báo phần bị chặn. Tài liệu plan, research, data-model và quickstart cũ cần đối chiếu với spec trước khi dùng.

## Phụ thuộc và điểm cần xác minh

- [ ] T045 Đối chiếu `docs/design/database/README.md` và dữ liệu thực tế 040/041/050: cấu trúc booking, `items.price`, nhóm, slot, thanh toán và phân bổ. Không tự chia đều giá slot đã có trong `items`; phân bổ tiền cọc/voucher theo tỷ lệ giá trị buổi, phần dư đồng vào buổi cuối, bảo toàn tổng.
- [ ] T046 Xác minh hợp đồng redemption 050 thực tế, đặc biệt cách liên kết voucher dùng chung với nhóm và hoàn lượt. Không giả định đường dẫn hoặc khóa redemption nhóm; chặn hoàn voucher chuỗi cho tới khi hợp đồng được xác nhận.
- [ ] T047 Xác minh hoặc bổ sung tích hợp server có thẩm quyền cho hủy, ví, slot và voucher trong cùng transaction; code cũ thiếu hoàn tiền không được coi là đã hoàn thành. Xác minh 050/112 thực sự cung cấp timeout PENDING, từ chối và hoàn tiền; tác vụ chuyển COMPLETED/NO_SHOW, check-in và khóa review 070 là phụ thuộc liên feature chưa được chứng minh hoàn tất.
- [ ] T048 Xác minh nguồn giờ server, phân trang ổn định, chỉ mục và hợp đồng điều hướng thực tế 040/050/070; không giả định Safe Args hoặc endpoint đã tồn tại. HOLD tiếp tục dùng hạn cũ, không reset; đặt lại kiểm tra venue đang hoạt động.

## Nền tảng và domain

- [ ] T001 Cấu hình chỉ mục/truy vấn theo schema thực tế: HOLD/PENDING `createdAt` giảm; CONFIRMED `startAt` tăng; COMPLETED/NO_SHOW `startAt` giảm; CANCELLED/REJECTED `updatedAt` giảm. Phân trang ổn định khi giá trị trùng; ẩn HOLD hết hạn và EXPIRED.
- [ ] T002 Tạo fixtures cho các trạng thái, thanh toán, voucher, chuỗi và ví.
- [ ] T003 Tạo domain models thuần Kotlin theo schema thực tế, gồm thông tin slot, tài chính, trạng thái, nhóm và QR; tiền là số nguyên VND.
- [ ] T004 Xác định repository interface thuần Kotlin cho phân trang, realtime, nhóm, quote dự kiến và yêu cầu hủy server; request có `requestId`, không gửi quote như dữ liệu có thẩm quyền.
- [ ] T005 Viết test quote: PENDING hoàn 100%; CONFIRMED đúng hoặc trên 240 phút hoàn 100%; trễ: cọc mất cọc, tại sân 0, ví phạt 30% thực trả sau voucher làm tròn **xuống bội 1.000 VND**, hoàn phần còn lại. Kiểm tra 111.111 → phạt 33.000, hoàn 78.111; 40.000 → phạt 12.000, hoàn 28.000; tổng phạt và hoàn bằng thực trả.
- [ ] T006 Kiểm thử sinh ma trận QR thuần Kotlin từ token.
- [ ] T007 Kiểm thử hủy: server quyết định theo giờ của mình; retry cùng `requestId` không hoàn hai lần; yêu cầu khác đồng thời không hoàn trùng.
- [ ] T008 Kiểm thử hủy từng buổi và tất cả buổi còn lại: phân bổ theo giá trị thực, bảo toàn tổng, chính sách áp dụng độc lập từng buổi; voucher nhóm chỉ hoàn khi tất cả buổi liên quan đã hủy và mỗi buổi đủ điều kiện hoàn 100%, không hoàn khi hủy một phần.
- [ ] T009 Kiểm thử 5 tab, lọc và thứ tự T001, phân trang khi khóa sắp xếp trùng.
- [ ] T010 Kiểm thử chi tiết realtime, check-in và quyền chủ đơn.
- [ ] T011 Cài đặt tính quote dự kiến bằng số nguyên theo T005; server tính lại kết quả cuối cùng, tránh tràn số.
- [ ] T012 Cài đặt sinh QR thuần Kotlin.
- [ ] T013 Cài đặt use case gửi yêu cầu hủy với lý do và `requestId` tới server.
- [ ] T014 Cài đặt điều phối hủy buổi lẻ và các buổi còn lại; tính toán có thẩm quyền và phân bổ do server thực hiện.
- [ ] T015 Cài đặt use case danh sách phân trang theo T001.
- [ ] T016 Cài đặt use case chi tiết và realtime.

## Data và server

- [ ] T017 Định nghĩa DTO theo dữ liệu thực tế và schema chung đã đối chiếu ở T045.
- [ ] T018 Cài mapper, giữ nguyên giá từng `items` và các giá trị tài chính đã lưu; kiểm thử dữ liệu thiếu và phân bổ.
- [ ] T019 Cài repository đọc phân trang và realtime; lọc quyền chủ đơn và dùng giờ server cho countdown.
- [ ] T020 Cài endpoint/Edge Function hủy đơn có xác thực: transaction server kiểm tra chủ đơn, trạng thái PENDING/CONFIRMED, chưa check-in, giờ bắt đầu và idempotency; tính lại tiền theo giờ server, cập nhật đơn, ví, lịch sử, nhả slot và hoàn voucher đủ điều kiện nguyên tử. Trả kết quả thực tế, kể cả khi vượt mốc kể từ quote.
- [ ] T021 Mở rộng transaction server cho buổi chuỗi và hủy các buổi còn lại: kiểm tra từng buổi, phân bổ và tính độc lập; tổng hợp hoàn tiền, cập nhật slot và voucher chung theo T008. Không giả định hợp đồng nhóm khi T045/T046 chưa xong.
- [ ] T022 Kết nối repository qua DI theo kiến trúc hiện có.
- [ ] T023 Cập nhật rules tối thiểu: chỉ chủ đơn đọc dữ liệu/QR của mình; ngăn client trực tiếp sửa trạng thái hủy, số dư, refund, slot hoặc redemption để vượt transaction server. Quyền chủ sân phải kiểm tra đúng cơ sở, không cấp toàn bộ theo role.

## UI và điều hướng

- [ ] T024 Tạo màn danh sách 5 tab theo ảnh tham chiếu.
- [ ] T025 Tạo trạng thái loading, dữ liệu, rỗng, lỗi và phân trang.
- [ ] T026 Tạo thẻ đơn, trạng thái thanh toán, nhãn cố định, countdown và hành động tương ứng.
- [ ] T027 Tạo chi tiết đơn, thông tin cơ sở, từng slot, tài chính, QR và trạng thái check-in.
- [ ] T028 Tạo danh sách buổi của chuỗi với hủy riêng và hủy các buổi còn lại.
- [ ] T029 Tạo **màn hình hủy riêng** theo `ui/06_booking-management_buoc3_huy-dat-san.png`: thông tin hoàn/phạt, lý do có sẵn, nhập Khác và xác nhận; không dùng dialog thay thế.
- [ ] T030 Render QR nền trắng, rõ và đủ lớn khi dark mode.
- [ ] T031 Tạo adapters danh sách, nhóm và countdown đồng bộ.
- [ ] T032 Định nghĩa UI state/effect cho phân trang, chi tiết, màn hủy và kết quả server.
- [ ] T033 Cài ViewModel danh sách, đếm ngược theo giờ server và cập nhật realtime.
- [ ] T034 Cài ViewModel chi tiết và màn hủy: quote chỉ dự kiến, bắt buộc lý do, hiển thị kết quả cuối cùng server.
- [ ] T035 Cài màn danh sách và hành động theo tab.
- [ ] T036 Cài chi tiết và điều hướng sang màn hủy riêng, khóa xác nhận khi thiếu lý do.
- [ ] T037 Cài màn nhóm và thao tác hủy từng buổi/các buổi còn lại.
- [ ] T038 Tích hợp điều hướng thực tế 040/050/070 sau T048; ẩn/khóa review NO_SHOW.
- [ ] T039 Cache QR offline chỉ khi tài khoản hiện tại khớp chủ đơn; cảnh báo ngoại tuyến không khẳng định QR còn hiệu lực, server check-in vẫn kiểm tra trạng thái và quyền. Kiểm thử đổi tài khoản và mất mạng.
- [ ] T040 Khóa nút khi gửi, tái sử dụng `requestId` khi retry timeout; server vẫn chịu trách nhiệm idempotency.

## Nghiệm thu

- [ ] T041 Unit test ViewModel, countdown, QR và quote.
- [ ] T042 Integration test transaction server: đồng thời, retry, race với duyệt/check-in, mốc giờ, hoàn ví và nhả slot nguyên tử, rollback khi lỗi, voucher đơn và chuỗi; kiểm thử rules ngăn client gian lận.
- [ ] T043 UI test năm tab, chi tiết, màn hủy riêng, lý do, QR offline và NO_SHOW.
- [ ] T044 Đối chiếu FR-001–FR-029 và SC-001–SC-007 với spec; ghi rõ phụ thuộc bị chặn và kết quả kiểm thử thực tế, không đánh dấu hoàn tất khi chưa được nghiệm thu.

Thứ tự: T045–T048 xác minh phụ thuộc song song với fixtures và thiết kế; domain/tests trước data/server, sau đó UI/tích hợp và nghiệm thu. Phần nào bị chặn bởi hợp đồng liên feature phải giữ chưa xong.
