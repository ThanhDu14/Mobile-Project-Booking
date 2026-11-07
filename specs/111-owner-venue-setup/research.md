# Nghiên cứu: Thiết lập cơ sở của chủ sân

## R1 — Schema hiện có và ranh giới quyền sở hữu

**Quyết định**: Dùng `docs/design/database/README.md` làm nguồn schema. Sử dụng `venues/{venueId}`, subcollection `courts/{courtId}` và `priceRules/{id}`, cùng `slots/{courtId}_{yyyyMMdd}_{HHmm}` ở cấp cao nhất. Không thêm trường hoặc enum. Giới hạn truy cập theo `appRole = OWNER` đã xác thực, trạng thái owner đã duyệt và quan hệ `venues.ownerId` được máy chủ đọc.

**Lý do**: Constitution và Feature 111 yêu cầu phân quyền phía server, không tin ID hay giá trị giống role do client gửi. `firestore.rules` hiện chỉ có helper dùng chung, chưa có match cụ thể cho collection. Database README mô tả ranh giới CRUD dự kiến, nhưng chưa có Rules chi tiết cho từng collection và chưa xác định `ownerStatus` được thể hiện thế nào trong claim.

**Phương án đã cân nhắc**: Chỉ kiểm tra ownership ở client (loại vì không an toàn); thêm trường schema để đơn giản hóa kiểm tra (loại vì ngoài phạm vi).

**Cần làm rõ**: Backend đáng tin cậy có claim thể hiện trạng thái owner đã duyệt không, hay phải đọc `users/{uid}.ownerStatus` cho mỗi thao tác nhạy cảm? Rules hiện chỉ expose `appRole`, trong khi spec owner yêu cầu cả role và ownerStatus ở trạng thái đã duyệt. Cần thống nhất nguồn đáng tin cậy trước khi triển khai.

## R2 — Giao dịch khóa/mở khóa slot

**Quyết định**: Khóa/mở khóa toàn bộ slot 30 phút đã chọn trong một giao dịch Firestore do server kiểm soát. Giao dịch xác minh quan hệ owner/venue/court và trạng thái slot mới nhất; cạnh tranh với giao dịch booking phải chỉ có một bên thắng. Không dùng client đọc rồi ghi hoặc nhiều lần ghi riêng có thể tạo trạng thái một phần.

**Lý do**: FR-010–012 của Feature 111 và Constitution II–III yêu cầu xử lý cạnh tranh nguyên tử và cấm thay thế slot `HOLD`/`BOOKED`/`BLOCKED`. Giao dịch Firestore chỉ chạy phía client không tạo được ranh giới phân quyền đáng tin cậy cho thao tác nhạy cảm này.

**Phương án đã cân nhắc**: Ghi trực tiếp từ client, chỉ dựa vào UI (loại); ghi tuần tự từng slot (loại vì có thể khóa một phần); thêm schema slot/lý do (loại vì database đã định nghĩa `status` và `blockReason`).

**Cần làm rõ**: Repo chưa có Edge Function khóa slot cho owner hoặc hợp đồng callable tương ứng. Cần thống nhất ranh giới thao tác/tên, request/response/lỗi và cách server phân biệt block mà owner được phép gỡ, chỉ dùng schema hiện tại. Cũng cần thống nhất cách xác minh ownership và trạng thái hoạt động của venue/court trong giao dịch. Không tự đặt public API trong lúc triển khai khi chưa có thỏa thuận.

## R3 — Bảng giá và booking

**Quyết định**: Lưu giá bằng `pricePerHour` hiện có (`Long` VND); tính từng slot 30 phút theo rule khớp thứ/giờ với công thức `pricePerHour / 2`. Từ chối rule chồng giờ ở bất kỳ thứ nào. Khi sửa/xóa rule, không tính lại `bookings.items[].price` đã ghi; thiếu rule phù hợp thì slot mới không thể đặt.

**Lý do**: Clarification Feature 111 ngày 2026-10-13 thay thế các câu trả lời trước đó trong phiên 2026-10-08; Feature 040 và database README xác nhận slot nửa giờ và giá lưu theo giờ.

**Phương án đã cân nhắc**: Lưu giá theo slot (bị clarification mới nhất thay thế); tiếp tục cấm xóa rule tạm thời (bị câu trả lời mới nhất thay thế vì cho phép xóa và booking lưu snapshot giá).

**Cần làm rõ**: Xóa hoặc đánh dấu các câu trả lời mâu thuẫn ngày 2026-10-08 trong Feature 111 là đã bị thay thế: các câu cấm xóa rule và cho rằng không thể xác định rule đã dùng trong booking. Đồng thời thống nhất yêu cầu cập nhật `minPricePerHour` được bảo vệ với ghi chú database nói trường này tính lại khi đổi price rule; chưa xác định bên ghi đáng tin cậy và hành vi khi không còn rule.

## R4 — Trạng thái cơ sở/sân và việc giữ booking

**Quyết định**: Ẩn venue bằng `status = HIDDEN`; ngừng hoạt động court bằng `isActive = false`. Các trạng thái này chặn booking mới nhưng không xóa dữ liệu hoặc đổi booking đã xác nhận. Owner không được đổi venue `LOCKED` do admin đặt.

**Lý do**: FR-005–007 của Feature 111 và FR-009 spec 120 phân biệt việc owner ẩn với admin khóa. Specs 040/041 dùng các trạng thái này khi xác định khả năng đặt.

**Phương án đã cân nhắc**: Xóa cứng venue/court hoặc hủy booking (bị spec loại trừ).

## R5 — Phạm vi và các tính năng liên quan

**Quyết định**: Feature 110 là nguồn trạng thái duyệt owner và tạo venue ban đầu; Feature 120 sở hữu nghiệp vụ khóa venue của admin; specs 040/041 đọc trạng thái venue/court/giá/slot; spec 112 sở hữu block `DROP_IN` và nghiệp vụ vận hành. Không triển khai duyệt booking, drop-in, voucher, thống kê vận hành, luồng admin hoặc onboarding.

**Lý do**: Bảng phân công tính năng và ranh giới phạm vi ghi rõ trong yêu cầu, Feature 111.

**Phương án đã cân nhắc**: Không có; đây là ranh giới phạm vi đã chốt.

## R6 — Quy ước Android và kiểm thử

**Quyết định**: Dùng ứng dụng Android đơn hiện có và quy ước `feature/owner/{ui,data,domain}` trong Constitution. Đặt Firebase sau interface repository; cung cấp trạng thái màn hình qua `StateFlow`, sự kiện một lần qua `Channel`/`SharedFlow`, thu thập Flow theo lifecycle. UI dùng XML/ViewBinding và resource. Kiểm thử logic bằng JUnit4/MockK/coroutines-test/Turbine; Rules/giao dịch server trên Firebase Emulator.

**Lý do**: Đây là quy ước bắt buộc của dự án. Mã hiện tại mới là skeleton, chưa có package owner hay pattern màn hình tính năng có thể kế thừa.

**Phương án đã cân nhắc**: Gọi Firebase trực tiếp từ Fragment/ViewModel (bị cấm); Java hoặc Gradle module/dependency mới (không cần thiết).

## R7 — Hợp đồng giao tiếp bên ngoài

**Quyết định**: Chỉ ghi lại đường dẫn Firestore hiện có, trường dữ liệu, yêu cầu phân quyền và bất biến giao dịch trong `contracts/firestore-boundary.md`. Không công bố endpoint REST/callable tự đặt. Hoãn chi tiết endpoint đến khi giải quyết clarification R2.

**Lý do**: Ứng dụng có giao tiếp bên ngoài với Firestore và Edge Functions, nhưng chưa có endpoint khóa slot của Feature 111. Viết hợp đồng API cụ thể lúc này sẽ tự tạo hành vi trái yêu cầu không suy đoán.

**Phương án đã cân nhắc**: Tự đặt tên function và payload (loại); bỏ qua toàn bộ ranh giới giao tiếp (loại vì dữ liệu/phân quyền ảnh hưởng trực tiếp đến an toàn triển khai).
