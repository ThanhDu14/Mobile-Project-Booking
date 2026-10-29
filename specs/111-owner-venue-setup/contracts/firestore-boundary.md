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

## Hợp đồng chưa được tài liệu nguồn quy định

Chưa có tên callable/Edge Function, request/response, mã idempotency hoặc dấu hiệu trong schema để xác định block thuộc owner nào. Schema không có trường owner hay tham chiếu thao tác cho block; cần thống nhất cách chứng minh quyền mở đúng block của mình mà không thêm schema. Phải xác định hợp đồng thao tác server cùng nhóm phụ trách booking trước khi triển khai. Client đọc Firestore rồi ghi riêng không phải phương án thay thế chấp nhận được.

## Trạng thái triển khai Feature 111

T007 chưa được chốt ở mức cho phép ghi: Rules hiện tại không cấp quyền owner tạo `BLOCKED` slot, và repo chưa có server boundary để thực thi transaction/kiểm tra ownership/court/latest state. Feature 111 chỉ cung cấp UI preview với typed gate `SLOT_ATOMIC_SERVER_CONTRACT`; không có lock/unlock write fallback.

T008 chưa được chốt: client không ghi `venues.minPricePerHour`, không tự suy ra giá khi không còn rule và không bật price-rule mutation có thể làm thay đổi field được bảo vệ. T011 giữ lifecycle tạo venue đầu tiên ở Feature 110/120; UI Feature 111 chỉ quản lý venue đã tồn tại và hiển thị thông báo server lifecycle khi chưa có create boundary.
