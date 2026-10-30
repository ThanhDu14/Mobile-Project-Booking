# Nghiên cứu: Thiết lập cơ sở của chủ sân

## R1 — Schema hiện có và ranh giới quyền sở hữu

**Quyết định**: Dùng `docs/design/database/README.md` làm nguồn schema. Sử dụng `venues/{venueId}`, subcollection `courts/{courtId}` và `priceRules/{id}`, cùng `slots/{courtId}_{yyyyMMdd}_{HHmm}` ở cấp cao nhất. Không thêm trường hoặc enum. Giới hạn truy cập theo `appRole = OWNER` đã xác thực, trạng thái owner đã duyệt và quan hệ `venues.ownerId` được máy chủ đọc.

**Lý do**: Constitution và Feature 111 yêu cầu phân quyền phía server, không tin ID hay giá trị giống role do client gửi. `firestore.rules` hiện chỉ có helper dùng chung, chưa có match cụ thể cho collection. Database README mô tả ranh giới CRUD dự kiến, nhưng chưa có Rules chi tiết cho từng collection và chưa xác định `ownerStatus` được thể hiện thế nào trong claim.

**Phương án đã cân nhắc**: Chỉ kiểm tra ownership ở client (loại vì không an toàn); thêm trường schema để đơn giản hóa kiểm tra (loại vì ngoài phạm vi).

**Đã chốt khi triển khai**: dùng custom claim `appRole == OWNER` kết hợp đọc server-side `users/{uid}.ownerStatus == APPROVED`. Đây là trường 🔒 do Feature 110/120 cập nhật; Feature 111 không tin giá trị client gửi và không cho owner tự sửa trường này. Rules owner mutation đã áp dụng cả hai điều kiện.

## R2 — Giao dịch khóa/mở khóa slot

**Quyết định**: Khóa/mở khóa toàn bộ slot 30 phút đã chọn trong một giao dịch Firestore do server kiểm soát (Quyết định 1A & 4A). Giao dịch xác minh quan hệ owner/venue/court và trạng thái slot mới nhất; cạnh tranh với giao dịch booking phải chỉ có một bên thắng. Mở khóa chỉ áp dụng cho slot có `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`, không gỡ slot `DROP_IN` (Feature 112) hoặc `HOLD`/`BOOKED` (Feature 040).

**Lý do**: FR-010–012 của Feature 111 và Constitution II–III yêu cầu xử lý cạnh tranh nguyên tử và cấm thay thế slot `HOLD`/`BOOKED`/`BLOCKED`. Dùng Supabase Edge Function `manage-slot-blocks` chạy service role để đảm bảo nguyên tử và bảo vệ toàn vẹn dữ liệu đặt sân.

## R3 — Bảng giá và booking

**Quyết định**: Lưu giá bằng `pricePerHour` hiện có (`Long` VND); tính từng slot 30 phút theo rule khớp thứ/giờ với công thức `pricePerHour / 2`. Từ chối rule chồng giờ ở bất kỳ thứ nào. Khi sửa/xóa rule, không tính lại `bookings.items[].price` đã ghi; thiếu rule phù hợp thì slot mới không thể đặt. Cập nhật trường 🔒 `venues.minPricePerHour` thông qua Edge Function `sync-venue-price` theo giá trị nhỏ nhất của các rules, nếu xóa hết toàn bộ rule thì trường này nhận giá trị `null` (Quyết định 2A).

**Lý do**: Clarification Feature 111 ngày 2026-10-13 thay thế các câu trả lời trước đó trong phiên 2026-10-08; Feature 040 và database README xác nhận slot nửa giờ và giá lưu theo giờ. `specs/020-court-search/data-model.md` quy định `minPricePerHour = null` khi chưa có bảng giá để sắp xếp đưa cơ sở chưa có giá xuống cuối danh sách.

## R4 — Trạng thái cơ sở/sân và việc giữ booking

**Quyết định**: Ẩn venue bằng `status = HIDDEN`; ngừng hoạt động court bằng `isActive = false`. Các trạng thái này chặn booking mới nhưng không xóa dữ liệu hoặc đổi booking đã xác nhận. Owner không được đổi venue `LOCKED` do admin đặt.

**Lý do**: FR-005–007 của Feature 111 và FR-009 spec 120 phân biệt việc owner ẩn với admin khóa. Specs 040/041 dùng các trạng thái này khi xác định khả năng đặt.

**Phương án đã cân nhắc**: Xóa cứng venue/court hoặc hủy booking (bị spec loại trừ).

## R5 — Phạm vi và các tính năng liên quan

**Quyết định**: Feature 110 là nguồn trạng thái duyệt owner và tạo venue ban đầu; Feature 120 sở hữu nghiệp vụ khóa venue của admin; specs 040/041 đọc trạng thái venue/court/giá/slot; spec 112 sở hữu block `DROP_IN` và nghiệp vụ vận hành. Không triển khai duyệt booking, drop-in, voucher, thống kê vận hành, luồng admin hoặc onboarding.

**Lý do**: Bảng phân công tính năng và ranh giới phạm vi ghi rõ trong yêu cầu, Feature 111.

## R6 — Quy ước Android và kiểm thử

**Quyết định**: Dùng ứng dụng Android đơn hiện có và quy ước `feature/owner/{ui,data,domain}` trong Constitution. Đặt Firebase sau interface repository; cung cấp trạng thái màn hình qua `StateFlow`, sự kiện một lần qua `Channel`/`SharedFlow`, thu thập Flow theo lifecycle. UI dùng XML/ViewBinding và resource. Kiểm thử logic bằng JUnit4/MockK/coroutines-test/Turbine; Rules/giao dịch server trên Firebase Emulator.

## R7 — Hợp đồng giao tiếp bên ngoài

**Quyết định**: Ghi nhận ranh giới Firestore hiện có trong `contracts/firestore-boundary.md`. Các thao tác server nhạy cảm sử dụng Supabase Edge Functions (`sync-venue-price`, `manage-slot-blocks`) chạy với Service Role để đảm bảo quyền nguyên tử và cập nhật các trường được bảo vệ.

## R8 — Trạng thái quyết định sau phê duyệt (2026-10-30)

- **T008 / Price rules & minPricePerHour (Quyết định 2A)**: Đã chốt thẩm quyền cập nhật là Edge Function `sync-venue-price`, chính sách no-rule trả về `null`. Mở quyền CRUD bảng giá cho Owner trên Firestore và app.
- **T011 / Vòng đời cơ sở (Quyết định 3A)**: Đã chốt việc mở thêm cơ sở phải thông qua luồng nộp hồ sơ Onboarding (Feature 110) để Admin duyệt (Feature 120). Feature 111 chỉ quản lý các cơ sở đã duyệt.
- **T007 / Slot Lock & Unlock (Quyết định 1A & 4A)**: Đã chốt Server Atomic Transaction qua Edge Function `manage-slot-blocks`, nhận diện slot được mở bằng `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`, giữ nguyên schema không thêm field.
