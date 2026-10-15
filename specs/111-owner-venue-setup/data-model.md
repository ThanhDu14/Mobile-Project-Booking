# Mô hình dữ liệu: Thiết lập cơ sở của chủ sân

Tính năng chỉ dùng mô hình Firestore hiện có trong `docs/design/database/README.md`; không thêm collection, trường, enum hoặc quan hệ mới.

## Venue — `venues/{venueId}`

Các trường owner quản lý trong phạm vi tính năng: `name`, `address`, `districtId`, `location` (GeoPoint), `openTime`, `closeTime`, `phone`, `amenityIds`, `photoPaths`, `courtCount`, `acceptsRecurring` và `status` (`ACTIVE`, `HIDDEN`) trong phạm vi owner. `ownerId` do logic server đáng tin cậy gán/xác minh và owner không được sửa. `minPricePerHour`, `ratingAvg`, `ratingCount` và trạng thái `LOCKED` do admin đặt là các trường được bảo vệ. Tuân theo quy tắc hiện có cho `createdAt`/`updatedAt`.

**Quan hệ**: Thuộc `users/{uid}` qua `ownerId`; chứa subcollection sân và bảng giá; được booking và slot tham chiếu.

**Validation**: Chuỗi bắt buộc không rỗng; district/amenity phải tồn tại; vị trí hợp lệ; có ít nhất một đường dẫn ảnh; số điện thoại Việt Nam hợp lệ; `openTime < closeTime` theo HHmm, không qua nửa đêm; giờ giống nhau mọi ngày; `courtCount` là số nguyên dương và nhất quán với số sân hoạt động theo quy tắc sản phẩm. Giới hạn tối đa và cách xác định nhất quán chính xác chưa được quy định.

**Trạng thái/hành vi**: Owner có thể ẩn venue hoạt động bằng `HIDDEN`. Không xóa cứng dữ liệu có liên kết. `LOCKED` do admin quản lý. Venue ẩn/khóa không nhận booking mới; booking đã xác nhận giữ nguyên. Quy tắc đọc của người chơi trong README hiện chỉ nêu `ACTIVE`; cần xác nhận cách owner đọc venue ẩn/khóa khi thiết kế Rules.

## Court — `venues/{venueId}/courts/{courtId}`

Các trường hiện có: `name`, `surfaceType` (`WOOD`, `PU`, `MAT`), `isActive`, `sortOrder`.

**Quan hệ**: Venue cha quyết định quyền sở hữu; court được booking item và ID slot xác định tham chiếu.

**Validation**: Tên không rỗng; surface type thuộc tập cho phép; thứ tự hợp lệ; tránh tên hiển thị trùng gây nhầm lẫn trong một venue. Quy tắc tên trùng chính xác chưa được định nghĩa.

**Trạng thái/hành vi**: Ngừng hoạt động bằng `isActive = false`; giữ dữ liệu và booking đã xác nhận, đồng thời chặn booking mới. Không tự suy ra trạng thái court mới hoặc hành vi xóa cứng.

## PriceRule — `venues/{venueId}/priceRules/{id}`

Các trường hiện có: `daysOfWeek` (số nguyên 1–7), `startTime`, `endTime` (HHmm, mốc 30 phút), `pricePerHour` (`Long` VND/giờ, dương và chia hết cho 2.000), `label` (`NORMAL`, `PEAK`, `WEEKEND`). Áp dụng cho tất cả court trong venue.

**Quan hệ**: Venue cha quyết định quyền sở hữu. `bookings.items[].price` hiện lưu snapshot giá đã áp dụng; mô hình booking không ghi quan hệ tới ID rule.

**Validation**: Giờ kết thúc sau giờ bắt đầu, không qua nửa đêm, không chồng giờ ở các thứ chung, label và thứ hợp lệ. Rule khớp tính giá mỗi slot 30 phút bằng `pricePerHour / 2`. Khoảng thiếu rule làm slot đó không đặt được. Tạo/sửa/xóa chỉ ảnh hưởng booking tương lai; theo clarification mới nhất của Feature 111, được phép xóa rule và không đổi giá booking cũ.

**Quyết định dữ liệu còn mở**: Chưa phân công rõ bên đáng tin cậy tính/cập nhật `venues.minPricePerHour`, đặc biệt khi venue không còn rule.

## CourtSlot — `slots/{courtId}_{yyyyMMdd}_{HHmm}`

Các trường hiện có của document slot đang bị chiếm: `venueId`, `courtId`, `date` (`yyyyMMdd`), `startAt`, `endAt`, `status` (`HOLD`, `BOOKED`, `BLOCKED`), `bookingId`, `holdBy`, `holdExpiresAt`, `blockReason` (`MAINTENANCE`, `EVENT`, `WALK_IN`, `DROP_IN` khi bị khóa). Slot trống không có document.

**Quan hệ**: Venue và court xác định quyền sở hữu; lưới booking và luồng đặt dùng chung ID slot xác định.

**Validation và chuyển trạng thái**: Khoảng phải theo mốc 30 phút và không qua nửa đêm. Khóa tạo tất cả ID tương ứng với `BLOCKED` và một lý do owner hợp lệ (`MAINTENANCE`, `EVENT`, `WALK_IN`). Nếu bất kỳ slot nào `HOLD`, `BOOKED` hoặc `BLOCKED`, hủy toàn bộ thao tác. Chỉ mở khóa bản ghi `BLOCKED` khớp sau khi xác minh trạng thái vẫn như mong đợi; không xóa/ghi đè booking hoặc hold. Owner không dùng `DROP_IN`; spec 112/080 sở hữu lý do này. Mọi ghi phải nguyên tử và tương thích với luồng ghi slot booking để giao dịch cạnh tranh chỉ có một bên thắng.

## Ngữ cảnh phân quyền (không phải entity mới)

Người dùng Firebase đã xác thực; claim role đáng tin cậy `appRole = OWNER`; trạng thái owner đã duyệt; và `venues.ownerId == uid` được server đọc. `ownerStatus` là trường được bảo vệ. Rules hiện chưa xác định nguồn có thẩm quyền cho trạng thái này; cần làm rõ trước khi triển khai.
