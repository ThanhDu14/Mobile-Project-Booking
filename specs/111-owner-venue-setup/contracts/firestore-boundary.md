# Ranh giới Firestore: Thiết lập cơ sở của chủ sân

Đây là hợp đồng ranh giới dữ liệu/bảo mật, không phải API HTTP/callable mới. Giao diện server cho thao tác đổi slot chưa được thống nhất; không suy diễn endpoint từ tài liệu này.

## Đường dẫn và trường hiện có

- `venues/{venueId}` — schema và trường được bảo vệ theo mục 4.3 database README.
- `venues/{venueId}/courts/{courtId}` — `name`, `surfaceType`, `isActive`, `sortOrder`.
- `venues/{venueId}/priceRules/{id}` — `daysOfWeek`, `startTime`, `endTime`, `pricePerHour`, `label`.
- `slots/{courtId}_{yyyyMMdd}_{HHmm}` — trường và enum hiện có theo mục 4.4 database README.

Không thêm trường, collection, endpoint, enum hoặc quan hệ booking mới.

## Yêu cầu phân quyền

Mọi thao tác đọc/ghi owner phải giới hạn theo UID đã xác thực và ownership venue do server xác minh. Nguồn đáng tin cậy đã chốt theo schema Feature 110/120 là `users/{uid}.ownerStatus == APPROVED`, kết hợp với custom claim `appRole == OWNER`; Rules đọc document `users` phía server cho mỗi mutation. Không dùng `ownerId` client gửi làm căn cứ quyền. Không cho owner sửa aggregate/trường được bảo vệ, role/status, trạng thái `LOCKED` của admin hoặc dữ liệu owner khác. Quyền hiển thị công khai cho player tuân theo chính sách status hiện có.

Implementation note: venue đầu tiên vẫn do lifecycle duyệt của Feature 110/120 tạo. Feature 111 không mở quyền client tự tạo venue cho tới khi boundary server cho thao tác tạo được thống nhất.

## Bất biến khi đổi slot

- `MAINTENANCE`, `EVENT`, `WALK_IN` là các lý do owner được dùng; `DROP_IN` thuộc Feature 112/080.
- Khoảng yêu cầu ánh xạ chính xác sang ID slot xác định theo bước 30 phút, trong cùng một ngày.
- Toàn bộ thay đổi thành công hoặc thất bại cùng nhau.
- Chỉ tạo block ở slot chưa bị chiếm. Nếu có `HOLD`, `BOOKED` hoặc `BLOCKED`, toàn bộ yêu cầu bị xung đột.
- Chỉ mở khóa các bản ghi `BLOCKED` owner được phép gỡ sau khi kiểm tra lại trạng thái; không xóa/ghi đè `HOLD` hoặc `BOOKED`.
- Booking và khóa sân phải dùng đường ghi phía server tương thích giao dịch; giao dịch commit trước là bên thắng.
- Khi dữ liệu cũ/xung đột, bên gọi nhận đủ thông tin không nhạy cảm để tải lại và thử lại. Không tiết lộ dữ liệu venue khác.

## Hợp đồng đã thống nhất (Quyết định 1A, 2A, 3A, 4A — 2026-10-30)

1. **Đồng bộ bảng giá và `minPricePerHour` (Quyết định 2A — T008, T022, T023)**:
   - Approved Owner tạo, sửa, xóa `priceRules` trực tiếp trên subcollection `venues/{venueId}/priceRules/{id}` tuân thủ Security Rules hiện có.
   - Trường bảo vệ `venues.minPricePerHour` được cập nhật đồng bộ bởi Edge Function `sync-venue-price` chạy với quyền service role:
     - Giá trị = `min(priceRules.pricePerHour)` nếu cơ sở có ít nhất một bảng giá.
     - Giá trị = `null` nếu cơ sở xóa hết toàn bộ bảng giá (khớp với hợp đồng tại `specs/020-court-search/data-model.md`).
     - Client không tự ghi trường 🔒 này; sau khi mutation `priceRules` thành công, client gọi `sync-venue-price` để đồng bộ.

2. **Vòng đời cơ sở và thêm cơ sở mới (Quyết định 3A — T011)**:
   - Feature 111 chỉ quản lý các cơ sở đã được duyệt và có quyền sở hữu hợp lệ (`venues.ownerId == uid`).
   - Việc mở thêm cơ sở mới (ngoài cơ sở ban đầu) yêu cầu nộp hồ sơ thẩm định qua luồng Đăng ký cơ sở của Feature 110 để Ban quản trị (Feature 120) phê duyệt. Nút "Thêm cơ sở" trong giao diện Feature 111 điều hướng sang luồng đăng ký của Feature 110. Feature 111 không tự cấp quyền tạo venue trực tiếp.

3. **Cơ chế khóa/mở khóa slot và nhận diện block an toàn (Quyết định 1A & 4A — T007, T028, T029)**:
   - Khóa/mở khóa slot sử dụng Supabase Edge Function `manage-slot-blocks` (chạy Firestore transaction với service role).
   - Nhận diện slot owner-removable bằng `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`. Tuyệt đối không xóa/mở khóa các slot `DROP_IN` (Feature 112/080) hoặc các slot `HOLD`/`BOOKED` (Feature 040). Giữ nguyên schema `slots` mà không thêm trường.

## Trạng thái triển khai Feature 111

- T008, T022, T023: Đã giải tỏa blocker qua Quyết định 2A (Edge Function `sync-venue-price` và chính sách `null` khi xóa hết rule). Mở các phương thức CRUD bảng giá trên Repository và giao diện Quản lý bảng giá.
- T011: Đã giải tỏa blocker qua Quyết định 3A (chuyển tiếp luồng đăng ký cơ sở sang Feature 110).
- T007, T028, T029, T031: Đã thống nhất Quyết định 1A & 4A; triển khai theo Roadmap Phase 3.
