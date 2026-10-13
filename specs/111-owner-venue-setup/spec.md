# Đặc tả: Quản lý cơ sở và cấu hình sân

**Feature**: `111-owner-venue-setup`  
**Nguồn yêu cầu**: `docs/proposal/Proposal.html`, SmashNow Constitution và `docs/design/database/README.md`.

## Clarifications

### Session 2026-10-08

- Q: Nếu booking đi qua nhiều `priceRules` hoặc có slot không khớp rule nào, hệ thống nên tính giá thế nào? → A: Tính riêng từng slot 30 phút theo rule khớp với ngày và giờ của slot; nếu thiếu rule cho bất kỳ slot nào thì không cho tiếp tục đặt.
- Q: Khi owner ẩn venue hoặc court đang có booking tương lai, hệ thống nên xử lý booking đó thế nào? → A: Cho phép ẩn để chặn booking mới, nhưng giữ nguyên và tiếp tục thực hiện booking đã xác nhận.
- Q: Khoảng giờ owner khóa có bắt buộc khớp ranh giới slot 30 phút không? → A: Bắt buộc khớp ranh giới 30 phút; nếu khóa nhiều slot thì ghi nhận tất cả slot tương ứng.
- Q: Owner có thể tạo khung giờ hoạt động, giá hoặc khóa giờ kéo qua nửa đêm không? → A: Không cho một khoảng kéo qua nửa đêm; owner tạo hai khoảng riêng ở hai ngày liên tiếp.
- Q: Khi owner sửa `priceRules`, giá của booking đã tạo trước đó nhưng chưa hoàn tất nên được giữ hay tính lại? → A: Giữ giá đã ghi nhận cho booking hiện có; `priceRules` mới chỉ áp dụng cho booking tạo sau khi thay đổi.
- Q: Owner có nên được xóa `priceRules` không, và nếu có thì trong trường hợp nào? → A: Chỉ xóa khi xác định chắc chắn rule chưa được booking sử dụng; trong khi dữ liệu chưa xác định được quan hệ này thì tạm thời cấm xóa.
- Q: Giờ mở cửa và đóng cửa của một venue có giống nhau mọi ngày không, hay owner cần cấu hình riêng theo từng thứ? → A: Dùng cùng `openTime` và `closeTime` mỗi ngày theo schema hiện có.
- Q: Database README không định nghĩa liên kết trực tiếp giữa booking và `priceRules`; vậy hệ thống nên xác định rule đã được booking sử dụng như thế nào? → A: Tạm không cho xóa rule; chờ thống nhất hợp đồng CSDL/booking.
- Q: Nếu owner khóa một slot đồng thời với người chơi đặt slot đó, thao tác nào nên thành công? → A: Chỉ thao tác được ghi nhận trước trong transaction thành công; nếu khóa được ghi trước thì booking bị từ chối, còn nếu slot đã được giữ/đặt trước thì thao tác khóa bị từ chối.
- Q: Khi tạo venue, `photoPaths`, `amenityIds` và `phone` nên phải đáp ứng điều kiện cụ thể nào? → A: `photoPaths` có ít nhất 1 ảnh; `amenityIds` có thể để trống hoặc chỉ gồm ID tiện ích tồn tại; `phone` bắt buộc và hợp lệ theo định dạng số Việt Nam.
- Q: Khi owner muốn ngừng áp dụng một `priceRule`, hệ thống nên xử lý rule đó thế nào nếu chưa thể xác định booking nào đã sử dụng nó? → A: Tạm thời cấm xóa `priceRules`; chỉ cho sửa và thay đổi chỉ áp dụng cho booking mới. Chỉ mở xóa khi hợp đồng dữ liệu xác định được rule đã dùng.
- Q: Mục tiêu thời gian tải danh sách venue, lịch sân và phản hồi thao tác quản lý nên là bao lâu? → A: Tải danh sách và lịch dưới 3 giây trên mạng 4G; thao tác ghi có phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường.

### Session 2026-10-13 (đồng bộ với spec 040, 041)

- Q: Bảng giá lưu giá theo slot hay theo giờ? → A: Theo giờ (`pricePerHour`), giá mỗi slot 30 phút bằng một nửa (phương án 1.A). Thay cho câu trả lời dùng `pricePerSlot` trước đây.
- Q: Có cho xóa `priceRules` không? → A: Có. Đơn đã tạo lưu giá từng khung trong `items[].price`, nên xóa rule không ảnh hưởng đơn cũ. Thay cho câu trả lời tạm cấm xóa trước đây.
- Q: Chủ sân có tắt được đặt cố định theo tuần (spec 041) không? → A: Có, bằng `venues.acceptsRecurring` cho từng venue.

## Actors

- **Owner đã được duyệt**: tài khoản đã đăng nhập, `role = OWNER` và `ownerStatus = APPROVED`; chỉ được quản lý venue/court/price rules thuộc quyền sở hữu của mình.
- **Người chơi**: xem venue đang hoạt động và dữ liệu sân/giá được công khai; không quản lý.
- **Admin**: có thể khóa venue theo quyền quản trị; thao tác quản trị thuộc spec 120.
- **Hệ thống đặt sân**: đọc `priceRules`, trạng thái venue/court và `slots` để xác định khả năng đặt cùng giá theo logic booking.

## User stories

- Là owner đã được duyệt, tôi muốn thêm, sửa và ẩn venue của mình để thông tin cơ sở luôn chính xác.
- Là owner đã được duyệt, tôi muốn quản lý court thuộc venue của mình để phản ánh số sân thực tế.
- Là owner đã được duyệt, tôi muốn cấu hình giá theo thứ và khung giờ để người chơi biết đúng giá trước khi đặt.
- Là owner đã được duyệt, tôi muốn khóa một hay nhiều khung giờ có lý do để ngăn booking trong thời gian bảo trì, sự kiện hoặc khách đặt trực tiếp.
- Là người chơi, tôi muốn thấy thông tin venue, giá và giờ không thể đặt chính xác để chọn lịch phù hợp.

## Acceptance scenarios

1. **Given** owner có `ownerStatus = APPROVED` và venue thuộc owner, **When** owner lưu venue với các dữ liệu bắt buộc hợp lệ, **Then** venue được tạo/cập nhật, giờ mở/đóng áp dụng mọi ngày và venue có thể xem theo trạng thái của venue.
2. **Given** owner chưa được duyệt hoặc venue không thuộc owner, **When** owner gửi yêu cầu quản lý venue/court/priceRules, **Then** yêu cầu bị từ chối ở phía server và dữ liệu không đổi.
3. **Given** court có booking đã xác nhận trong tương lai, **When** owner ngừng hoạt động court, **Then** booking đã xác nhận vẫn được giữ và thực hiện, còn booking mới bị chặn.
4. **Given** priceRules có khoảng ngày/thứ và giờ hợp lệ, **When** người chơi xem giá hoặc bắt đầu booking, **Then** giá được xác định từ `priceRules` theo cùng quy tắc của Feature 05 và được kiểm tra lại khi booking được xác nhận.
5. **Given** khoảng giờ của một court còn trống và khớp ranh giới slot 30 phút, **When** owner khóa khoảng đó với lý do hợp lệ, **Then** mọi slot 30 phút trong khoảng được biểu thị `slots.status = BLOCKED` cùng `blockReason`, và không thể được booking.
6. **Given** owner khóa một slot đồng thời với booking slot đó, **When** hai thao tác cạnh tranh được ghi nhận, **Then** thao tác được ghi nhận trước trong transaction thành công; thao tác sau bị từ chối và nhận trạng thái mới nhất.
7. **Given** slot đang có HOLD/BOOKED hoặc BLOCKED, **When** owner cố khóa slot đó, **Then** hệ thống từ chối xung đột và hiển thị trạng thái mới nhất.
8. **Given** owner mở khóa khoảng giờ đã khóa, **When** thao tác hợp lệ được chấp nhận, **Then** slot không còn bị coi là BLOCKED; khả năng đặt được đánh giá lại từ trạng thái booking hiện hành, không ghi đè HOLD/BOOKED.
9. **Given** một `priceRule` không còn dùng, **When** owner xóa rule, **Then** rule bị xóa; các đơn đã tạo không đổi giá vì giá từng khung đã lưu trong `bookings.items[].price`; khung giờ không còn rule nào thì không đặt được.
10. **Given** owner tắt "Nhận đặt cố định theo tuần" của một venue, **When** người chơi mở màn hình đặt sân của venue đó, **Then** không còn chế độ đặt cố định (spec 041); các chuỗi đã đặt trước đó vẫn giữ nguyên.
11. **Given** owner muốn nghỉ cả một ngày, **When** owner khóa toàn bộ khung giờ của mọi court trong ngày đó với lý do `MAINTENANCE` hoặc `EVENT`, **Then** lưới đặt sân (spec 040) hiện "Cơ sở đóng cửa vào ngày này".

## Functional requirements

- **FR-001**: Chỉ tài khoản đã xác thực có `role = OWNER` và `ownerStatus = APPROVED` được tạo, sửa, ẩn venue; quản lý court, `priceRules` và khóa giờ.
- **FR-002**: Owner có thể xem danh sách venue của mình, trạng thái venue và danh sách court/giá thuộc từng venue.
- **FR-003**: Tạo venue yêu cầu tối thiểu `name`, `address`, `districtId`, `location`, `openTime`, `closeTime`, `phone`, `amenityIds`, `photoPaths`, `courtCount` và `acceptsRecurring` (mặc định bật) theo schema chung. Venue đầu tiên được tạo tự động từ hồ sơ khi admin duyệt (spec 110, 120); owner tạo thêm venue khác bằng biểu mẫu của spec này. `openTime` và `closeTime` áp dụng giống nhau mọi ngày. Các trường schema này là căn cứ dữ liệu; điều kiện bắt buộc chính xác được nêu ở Validation.
- **FR-004**: Owner được sửa thông tin nghiệp vụ venue của mình. Trường 🔒 như `minPricePerHour`, `ratingAvg`, `ratingCount` và trạng thái admin khóa không được owner tự ghi.
- **FR-005**: Ẩn venue sử dụng `venues.status = HIDDEN`; chặn booking mới nhưng không hủy hoặc làm mất booking đã xác nhận. Không xóa cứng venue có dữ liệu liên kết. Venue `LOCKED` do admin không thể được owner tự mở khóa.
- **FR-006**: Owner có thể thêm, sửa, ẩn và sắp xếp court trong venue của mình. Schema court hiện có `name`, `surfaceType`, `isActive`, `sortOrder`; spec không đặt thêm field hay trạng thái mới.
- **FR-007**: Khi owner ngừng hoạt động court bằng `isActive = false`, chặn booking mới nhưng giữ và tiếp tục thực hiện booking đã xác nhận; dữ liệu lịch và booking cũ không bị xóa.
- **FR-008**: Owner có thể tạo, sửa và xóa quy tắc giá với các trường schema: `daysOfWeek` (1–7), `startTime`, `endTime` (`HHmm`, bội số 30 phút), `pricePerHour` (Long VND/giờ, chia hết cho 2.000), `label` (`NORMAL`, `PEAK`, `WEEKEND`). Giá mỗi khung 30 phút = `pricePerHour / 2` (phương án 1.A, spec 040). Bảng giá áp dụng chung cho mọi court của venue. Thay đổi chỉ áp dụng cho booking được tạo sau thay đổi; booking đã tạo giữ nguyên giá vì giá từng khung đã lưu trong `bookings.items[].price`, nên xóa rule không ảnh hưởng đơn cũ.
- **FR-008a**: Owner có thể bật/tắt "Nhận đặt cố định theo tuần" (`venues.acceptsRecurring`) cho từng venue; tắt chỉ chặn chuỗi mới (spec 041), không hủy chuỗi đã đặt.
- **FR-009**: Giá hiển thị và giá booking phải dùng cùng một cách áp dụng `priceRules` của Feature 05. Tính riêng từng slot 30 phút theo rule khớp ngày và giờ (`pricePerHour / 2`); nếu bất kỳ slot nào không có rule phù hợp thì không cho tiếp tục booking. Booking phía server là nguồn quyết định cuối cùng. `minPricePerHour` được cập nhật theo trách nhiệm dữ liệu chung, không do client tự sửa.
- **FR-010**: Owner có thể khóa khoảng giờ của court mình chọn khi thời điểm bắt đầu/kết thúc khớp ranh giới slot 30 phút; khoảng khóa không kéo qua nửa đêm. Owner cung cấp lý do thuộc `blockReason` đã quy định: `MAINTENANCE`, `EVENT`, `WALK_IN` (các nguyên nhân DROP_IN thuộc luồng spec 112/080). Mỗi slot 30 phút trong khoảng bị khóa có `slots.status = BLOCKED` và `blockReason`. Không có cấu hình ngày nghỉ riêng: muốn đóng cả ngày, owner khóa toàn bộ khung giờ của ngày đó.
- **FR-011**: Slot BLOCKED không khả dụng cho HOLD/booking. Nếu owner khóa giờ cạnh tranh với booking, thao tác được ghi nhận trước trong transaction thành công; thao tác sau bị từ chối và phải tải lại trạng thái. Không được ghi đè slot đang HOLD/BOOKED hoặc BLOCKED bởi thao tác quản lý khác.
- **FR-012**: Việc mở khóa chỉ gỡ trạng thái BLOCKED do thao tác khóa tương ứng và không xóa/ghi đè booking. Hệ thống phải từ chối nếu trạng thái slot đã thay đổi kể từ lúc owner xem.
- **FR-013**: Danh sách, lịch giờ và chi tiết quản lý có trạng thái loading, nội dung, empty, error; thao tác ghi báo kết quả và giữ nội dung đang nhập khi thất bại có thể khôi phục.
- **FR-014**: Spec không bao gồm duyệt/từ chối booking, drop-in, voucher, thống kê vận hành, hay quy trình admin.

## Validation

- Trường văn bản bắt buộc không được rỗng hoặc chỉ chứa khoảng trắng; địa chỉ, số liên hệ theo định dạng số Việt Nam, vị trí, giờ hoạt động và thông tin phân loại venue phải hợp lệ theo các kiểu dữ liệu hiện có. Venue phải có ít nhất một ảnh trong `photoPaths`; `amenityIds` được để trống, nếu có thì từng ID phải tồn tại trong danh mục.
- `location` phải có tọa độ hợp lệ; `districtId` phải tham chiếu danh mục hiện có. Không cho nhập giá trị danh mục không tồn tại.
- `openTime`, `closeTime`, `startTime`, `endTime` phải là giờ `HHmm`; khoảng thời gian phải có điểm kết thúc sau điểm bắt đầu và không kéo qua nửa đêm. Khoảng qua nửa đêm phải được tách thành hai khoảng thuộc hai ngày liên tiếp.
- `openTime` và `closeTime` là cùng một cặp giờ hoạt động cho mọi ngày trong tuần; không cấu hình lịch đóng cửa theo thứ trong feature này.
- `courtCount` phải là số nguyên dương và nhất quán với số court hoạt động theo quy tắc sản phẩm; giới hạn tối đa chưa được quy định.
- Court yêu cầu `name`, `surfaceType` trong `WOOD`, `PU`, `MAT`; `sortOrder` hợp lệ theo thứ tự hiển thị. Không được tạo court rỗng hoặc trùng hiển thị gây nhầm lẫn trong cùng venue.
- `daysOfWeek` phải chứa ngày hợp lệ 1–7; `pricePerHour` là số nguyên VND dương, chia hết cho 2.000; `label` chỉ nhận `NORMAL`, `PEAK`, `WEEKEND`.
- Thời điểm bắt đầu và kết thúc khóa giờ phải nằm trên mốc 00 hoặc 30 phút; khoảng được quy đổi đầy đủ thành các slot 30 phút liên tiếp, không làm tròn âm thầm.
- Các `priceRules` không được chồng giờ trong cùng ngày theo database README. Thời gian phải hợp lệ, không có khoảng đảo/ngang bằng và không kéo qua nửa đêm.
- Xóa `priceRules` hợp lệ bất cứ lúc nào; trước khi xóa, app cảnh báo nếu việc xóa làm một số khung giờ không còn giá (các khung đó sẽ không đặt được).
- Lý do khóa là bắt buộc và phải ánh xạ chính xác sang `MAINTENANCE`, `EVENT` hoặc `WALK_IN`; không chấp nhận chuỗi tuỳ ý vì schema chỉ liệt kê các enum này.
- Trước ghi, hệ thống kiểm tra lại quyền owner, quyền sở hữu và trạng thái mới nhất của venue/court/slot; lỗi validation gắn với trường tương ứng và không lưu một phần.

## Permission / Security

- Bắt buộc kiểm tra ở server/Security Rules: danh tính đã xác thực, `role = OWNER`, `ownerStatus = APPROVED`, và quan hệ sở hữu. Client ẩn nút không thay cho kiểm tra quyền.
- Venue phải có `ownerId` bằng UID owner. Mọi court và `priceRules` truy cập qua venue cha; với mọi thao tác phải xác minh venue cha có `ownerId` đúng UID. Không tin `venueId`, `courtId` hay `ownerId` do client gửi nếu không đối chiếu dữ liệu server.
- Owner không được sửa dữ liệu của venue khác, kể cả khi đoán ID document hoặc gọi trực tiếp API. Tạo court/priceRule cũng phải xác thực quan hệ cha, không chỉ role.
- Người chơi chỉ đọc venue `ACTIVE` và dữ liệu sân/giá được phép công khai theo database README. Không có quyền ghi quản lý.
- Owner không được thay đổi `role`, `ownerStatus`, `ownerId` sau tạo, trường 🔒, trạng thái `LOCKED` do admin, hoặc các trường hệ thống `createdAt`/`updatedAt` trái quy tắc server.
- Khóa/mở khóa `slots` chỉ áp dụng cho court thuộc venue của owner; kiểm tra xung đột trạng thái phải bảo toàn bất biến booking và đồng bộ với luồng ghi slot của Feature 04.

## Error / Empty / Loading states

- **Loading**: hiển thị đang tải danh sách/chi tiết/lịch; thao tác ghi đang xử lý có tiến trình và chống gửi lặp.
- **Empty**: chưa có venue, venue chưa có court, hoặc chưa có `priceRules`; hiển thị hướng dẫn thêm tương ứng. Không diễn giải empty do lỗi quyền thành danh sách rỗng.
- **Validation**: thông báo ngay cạnh trường sai; giữ dữ liệu hợp lệ đã nhập.
- **Conflict**: giá trùng khung, slot vừa được booking/khóa, court đang được sử dụng hoặc venue vừa đổi trạng thái; tải trạng thái mới và giải thích thao tác cần làm lại.
- **Permission**: báo không đủ quyền hoặc dữ liệu không thuộc owner; không tiết lộ thông tin cơ sở khác.
- **Server/network error**: thông báo không lưu được hoặc chưa rõ kết quả; cho tải lại/thử lại an toàn.

## Offline / network failure

- Có thể xem dữ liệu cache kèm dấu hiệu dữ liệu chưa được làm mới; không coi dữ liệu cache là bằng chứng quyền hiện hành.
- Không xác nhận thao tác tạo/sửa/ẩn/khóa cho tới khi server xác nhận. Nếu phản hồi mất sau khi gửi, tải dữ liệu hiện tại trước khi cho gửi lại để tránh thao tác lặp hoặc trạng thái không xác định.
- Không cho thực hiện thao tác khóa giờ offline dựa trên lưới cũ; cần trạng thái slot mới nhất để tránh xung đột với booking.
- Giữ dữ liệu biểu mẫu chưa lưu khi mất mạng và cho thử lại sau khi kết nối lại; không đưa thay đổi chưa đồng bộ vào dữ liệu công khai.

## Edge cases

- Owner bị thu hồi `APPROVED` hoặc đăng xuất trong khi màn hình quản lý mở.
- Venue bị admin chuyển `LOCKED` khi owner đang sửa; owner không thể vượt trạng thái quản trị.
- Venue/court/priceRule bị sửa đồng thời trên thiết bị khác; phát hiện dữ liệu mới hơn và không ghi đè âm thầm.
- Ẩn venue/court có booking tương lai, lịch cũ, đánh giá hoặc slot; bảo toàn lịch sử và xử lý khả năng đặt mới theo trạng thái được chốt.
- Booking đang được tạo đồng thời khi owner khóa giờ; transaction ghi nhận trước quyết định trạng thái cuối, transaction sau bị từ chối và phía booking phải thấy BLOCKED nếu thao tác khóa thắng.
- Yêu cầu khóa nhiều slot chỉ thành công một phần do slot xung đột/mạng lỗi; không để lịch ở trạng thái nửa vời.
- Rule giá trùng tại ranh giới thời gian, thiếu rule cho một khoảng, hoặc một booking kéo qua hai rule; cần cùng quy tắc chọn giá giữa các màn hình và Feature 05.
- Owner xóa `priceRules` trong khi có booking đang giữ chỗ dùng rule đó; booking giữ nguyên giá đã lưu trong `items[].price`, rule mới chỉ áp dụng cho booking tạo sau.
- Owner sửa `priceRules` trong khi booking đang chờ xử lý; booking đó tiếp tục giữ giá đã ghi nhận lúc tạo, rule mới chỉ áp dụng cho booking mới.
- venue được ẩn nhưng có đường dẫn trực tiếp/cache hoặc hiển thị trong tìm kiếm; chỉ status công khai phù hợp mới được hiển thị/đặt.

## Dependencies

- **Spec 010 — account auth**: xác thực danh tính, vai trò ứng dụng.
- **Spec 011 — account profile**: trạng thái tài khoản và chế độ quản lý; chỉ hỗ trợ điều hướng, không cấp quyền.
- **Spec 110 — owner onboarding**: admin duyệt owner và khởi tạo venue từ `venueDraft`; spec này quản lý venue sau duyệt.
- **Specs 040/041/050 — booking**: đọc trạng thái court/slot và `priceRules`; tiền phải khớp logic đặt sân. Quy tắc tính giá đã thống nhất ở spec 040 (phương án 1.A); spec 041 đọc `acceptsRecurring`.
- **Spec 030 — court detail**: hiển thị thông tin công khai và bảng giá.
- **Spec 120 — admin**: khóa venue và quản trị danh mục.
- **Database README**: nguồn schema, enum, quy tắc slot và phân quyền; đang ghi trạng thái bản nháp.

## Success criteria

- 100% thao tác quản lý thành công chỉ áp dụng với `role = OWNER`, `ownerStatus = APPROVED` và venue có `ownerId` khớp UID.
- 0 trường hợp owner sửa được venue/court/priceRules của owner khác qua giao diện hoặc yêu cầu trực tiếp trong kịch bản phân quyền.
- 100% giá được hiển thị/đưa vào booking từ cùng quy tắc `priceRules`; giá booking được kiểm tra lại theo logic server của Feature 05.
- 100% slot owner khóa được ghi nhận là `slots.status = BLOCKED` với `blockReason` hợp lệ và không thể booking trong khi còn BLOCKED.
- Không có thao tác ẩn venue/court nào làm mất booking/lịch sử liên quan hoặc xóa cứng dữ liệu có liên kết.
- Danh sách venue và lịch sân tải trong dưới 3 giây trên mạng 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường.
- Mọi luồng danh sách và ghi đều có loading, empty/error, xử lý offline và thông báo kết quả rõ ràng.

## Open Questions / Assumptions

- **Assumption**: giá mỗi slot 30 phút = `pricePerHour / 2`; một booking gồm nhiều slot tính tổng các mức giá slot tương ứng. Slot không có rule khớp làm booking không khả dụng.
- **Đã giải quyết (2026-10-13)**: xóa `priceRules` được phép vì `bookings.items[].price` lưu giá đã chốt của từng khung (đặc tả CSDL mục 4.5).
- **Assumption**: Ẩn venue dùng `status = HIDDEN`; court ngừng nhận booking dùng `isActive = false`; không thêm `status` cho court.
- **Assumption**: `WALK_IN` là lý do khóa giờ dành cho khách đặt trực tiếp như README đã liệt kê; `DROP_IN` thuộc hoạt động mở buổi vãng lai ngoài phạm vi.
- **Assumption**: dữ liệu `ownerId` là quan hệ sở hữu chuẩn của venue; court và priceRules kế thừa quyền qua venue cha, đúng cấu trúc subcollection trong database README.

## Mâu thuẫn tài liệu

- Constitution nguyên tắc III quy định mỗi slot có document với ID tất định và slot trống có thể không có document; database README mục 4.4 cũng nói chỉ tạo document cho slot đang bị chiếm. Spec này dựa theo mô hình đó.
- Database README nói `priceRules` cùng ngày không được chồng giờ, nhưng chưa quy định cách tính khi booking chạm nhiều rule/thiếu rule; đã thống nhất ở spec 040: tính từng slot 30 phút, thiếu rule thì không đặt được.
- Proposal mô tả lock giờ gồm bảo trì, sự kiện, khách đặt trực tiếp; enum database bổ sung `DROP_IN` cho luồng buổi vãng lai. Phạm vi spec này dùng các loại khớp schema và để `DROP_IN` cho spec vận hành.
- Đã đối chiếu với spec 040, 041, 050, 120 ngày 2026-10-13.
