# Đặc tả: Vận hành cơ sở của chủ sân

**Feature**: `112-owner-operations`  
**Created**: 2026-10-08  
**Status**: Draft  
**Input**: Chủ sân đã được admin duyệt vận hành cơ sở: xử lý booking, xem lịch ngày/tuần, mở buổi drop-in, xử lý đăng ký và QR check-in, tạo voucher, xem thống kê. Không bao gồm onboarding, thiết lập venue/court/giá/khóa giờ và quy trình admin.

## Clarifications

### Session 2026-10-08

- Q: Với đăng ký drop-in, owner có cần chấp thuận từng người trước khi họ được tính là đã đăng ký không? → A: Không duyệt từng đăng ký; owner xem danh sách và check-in. Đăng ký được chấp nhận theo luồng Feature 08 và trạng thái hiện có.
- Q: Báo cáo doanh thu của owner nên tính các booking ở trạng thái thanh toán nào? → A: Tách riêng tiền cọc (`DEPOSITED`), thanh toán đủ (`PAID`) và hoàn tiền (`REFUNDED`); thanh toán `AT_VENUE` chỉ được tính sau khi trạng thái ghi nhận `PAID`.
- Q: Khi `paymentStatus = REFUNDED` nhưng dữ liệu không cho biết số tiền hoàn, báo cáo nên hiển thị khoản hoàn như thế nào? → A: Hiển thị số booking `REFUNDED`; chỉ hiển thị tiền hoàn khi dữ liệu hiện có xác định được số tiền, không giả định hoàn toàn bộ.
- Q: Báo cáo nên gom booking thành các khoảng dài bao lâu để xác định khung giờ cao điểm? → A: Gom từng khoảng 60 phút theo giờ địa phương `Asia/Ho_Chi_Minh`.

### Session 2026-10-13 (đồng bộ với spec 040–081, 100)

- Q: Hạn duyệt đơn `PENDING` là bao lâu và owner biết bằng cách nào? → A: `approvalDeadline = min(lúc sang PENDING + 60 phút, giờ bắt đầu)`; owner nhận `BOOKING_NEW`; quá hạn thì `booking-timeout` tự từ chối và hoàn tiền (spec 050, phương án 2.A).
- Q: Đơn đặt sân được check-in thế nào? → A: Quét QR hoặc nhập mã đặt sân, chỉ ghi `checkedInAt`; `COMPLETED`/`NO_SHOW` do server chuyển khi hết giờ (User Story 5).
- Q: Owner có hủy được đơn đã xác nhận, sửa hoặc hủy buổi vãng lai không? → A: Có (FR-004a, FR-010a); khách luôn được hoàn 100%.
- Q: Báo cáo hoàn tiền lấy số tiền từ đâu? → A: Trường `bookings.refundAmount` (đặc tả CSDL mục 4.5). Thay cho câu trả lời "chỉ đếm số booking" trước đây.

## Actors

- **Owner đã được duyệt**: người dùng đã đăng nhập có `role = OWNER` và `ownerStatus = APPROVED`. Chỉ được thao tác với venue do UID của mình sở hữu (`venues.ownerId`) và court thuộc venue đó.
- **Người chơi**: tạo booking, đăng ký drop-in và sử dụng voucher theo các tính năng tương ứng; không có quyền vận hành.
- **Hệ thống đặt lịch và thống kê**: cung cấp trạng thái booking/slot, đăng ký, dữ liệu tiền và số liệu thống kê theo dữ liệu nghiệp vụ đã ghi nhận.

## User Scenarios & Testing

### User Story 1 — Xử lý booking và xem lịch (Priority: P1)

Owner cần phản hồi các booking đang chờ và nắm lịch sử dụng sân để vận hành cơ sở, tránh nhận lịch trùng hoặc thay đổi đơn không còn hợp lệ.

**Independent Test**: Với booking thuộc venue của owner, kiểm tra danh sách, quyết định duyệt/từ chối và lịch theo ngày/tuần; xác minh booking và slot phản ánh cùng kết quả.

**Acceptance Scenarios**:

1. **Given** owner đã duyệt và có booking `PENDING` hợp lệ thuộc venue mình, **When** owner duyệt, **Then** booking chuyển sang `CONFIRMED` và các slot liên quan tiếp tục gắn với booking đó ở trạng thái `BOOKED`.
2. **Given** booking `PENDING` hợp lệ thuộc venue mình, **When** owner từ chối, **Then** booking chuyển sang `REJECTED` và slot của booking được giải phóng theo cùng giao dịch nghiệp vụ.
3. **Given** booking đã chuyển khỏi `PENDING`, hết hạn, đã bị hủy, hoặc slot không còn khớp booking, **When** owner gửi quyết định, **Then** yêu cầu bị từ chối, dữ liệu không bị ghi đè và owner nhận trạng thái hiện tại.
4. **Given** có booking thuộc venue mình ở nhiều trạng thái, **When** owner xem lịch ngày hoặc tuần, **Then** mỗi booking xuất hiện tại đúng ngày/giờ/court, cùng trạng thái liên quan; lịch phân biệt booking chờ xử lý với booking đã xác nhận và không xem `HOLD` chưa thành booking là lịch đã xác nhận.
5. **Given** owner cố mở hoặc quyết định booking thuộc venue của người khác, **When** yêu cầu được gửi, **Then** hệ thống từ chối mà không tiết lộ dữ liệu booking đó.

### User Story 2 — Mở và quản lý buổi drop-in (Priority: P1)

Owner muốn đưa các chỗ chơi còn trống thành buổi vãng lai có sức chứa và giá rõ ràng, rồi theo dõi đăng ký và xác thực người đến chơi.

**Independent Test**: Tạo buổi hợp lệ trên court thuộc venue mình, kiểm tra giới hạn sức chứa và các slot bị chiếm, xem đăng ký và xác thực QR đúng người.

**Acceptance Scenarios**:

1. **Given** owner đã duyệt, venue/court thuộc owner còn hoạt động và các slot thời gian chưa bị chiếm, **When** owner tạo drop-in với ngày, giờ, sân, số slot và giá hợp lệ, **Then** buổi được tạo với `ownerId`, `venueId`, `courtIds`, `date`, `startAt`, `endAt`, `capacity`, `registeredCount`, `pricePerPerson` và `status` theo schema hiện có; các slot tương ứng được ghi `BLOCKED` với `blockReason = DROP_IN` nhất quán trong cùng thao tác nghiệp vụ.
2. **Given** buổi `OPEN` còn chỗ, **When** người chơi đăng ký thành công theo Feature 08, **Then** đăng ký gắn với người chơi và buổi, còn `registeredCount` và trạng thái buổi phản ánh chỗ đã nhận theo quy tắc transaction hiện có.
3. **Given** buổi đầy hoặc đăng ký nằm trong hàng chờ, **When** owner xem danh sách, **Then** trạng thái `REGISTERED`/`WAITLISTED` được trình bày đúng dữ liệu; owner không tự nâng người từ hàng chờ hoặc tự thay đổi `registeredCount`.
4. **Given** đăng ký có QR hợp lệ và thuộc buổi do owner quản lý, **When** owner quét QR tại buổi, **Then** đăng ký chuyển sang `CHECKED_IN` và `qrToken` không thể dùng lại để check-in lần nữa.
5. **Given** QR sai, đã dùng, thuộc buổi khác, người đăng ký đã hủy, hoặc buổi/đăng ký không thuộc owner, **When** owner quét QR, **Then** check-in bị từ chối và trạng thái đăng ký không đổi.
6. **Given** thời gian/court của buổi xung đột với slot `HOLD`, `BOOKED`, `BLOCKED` hoặc venue/court không còn đủ điều kiện, **When** owner tạo buổi, **Then** toàn bộ thao tác bị từ chối và không tạo buổi hoặc khóa một phần slot.

### User Story 3 — Tạo voucher (Priority: P2)

Owner muốn phát hành mã khuyến mãi cho khách đặt tại venue của mình để khuyến khích đặt sân, trong khi các điều kiện sử dụng và số lượt dùng được kiểm soát nhất quán.

**Independent Test**: Tạo voucher với các trường schema, kiểm tra khả năng áp dụng ngoài phạm vi này qua Feature 05 và xác minh owner không tạo/sửa voucher của venue khác hoặc voucher toàn hệ thống.

**Acceptance Scenarios**:

1. **Given** owner đã duyệt và nhập đủ trường voucher hợp lệ, **When** owner tạo voucher gắn venue mình, **Then** voucher được lưu với các trường schema hiện có và có thể được Feature 05 kiểm tra khi đặt booking.
2. **Given** mã voucher đã tồn tại, dữ liệu điều kiện không hợp lệ hoặc owner không sở hữu `venueId`, **When** owner lưu, **Then** yêu cầu bị từ chối, không ghi đè voucher ngoài quyền và lỗi được chỉ rõ.
3. **Given** voucher đã có lượt sử dụng, **When** owner xem hoặc cập nhật trường được phép, **Then** owner không thể tự sửa `usedCount` hoặc các redemption đã ghi nhận.

### User Story 4 — Xem thống kê vận hành (Priority: P2)

Owner muốn hiểu doanh thu, mức sử dụng sân và giờ cao điểm để điều chỉnh hoạt động cơ sở dựa trên booking thực tế.

**Independent Test**: So sánh khoảng thời gian thống kê với các booking thuộc venue có liên quan, bao gồm booking bị từ chối/hủy và booking chưa hoàn tất.

**Acceptance Scenarios**:

1. **Given** venue có booking trong khoảng ngày được chọn, **When** owner xem doanh thu, **Then** số liệu tách các booking `DEPOSITED`, `PAID`, `REFUNDED`; `AT_VENUE` chỉ được tính vào nhóm `PAID` sau khi xác nhận đã thu. Booking `REFUNDED` được đếm riêng và chỉ có số tiền hoàn khi dữ liệu hiện có xác định được số tiền. Booking `REJECTED`, `CANCELLED`, `EXPIRED` và đơn chưa thanh toán không được tính như doanh thu đã thu.
2. **Given** venue/court có thời gian hoạt động và booking thực tế, **When** owner xem tỷ lệ lấp đầy, **Then** chỉ số phản ánh thời lượng slot đã được booking xác nhận trên tổng thời lượng slot có thể đặt trong cùng phạm vi và khoảng thời gian (booking xác nhận gồm `CONFIRMED`, `COMPLETED` và `NO_SHOW`, vì slot đã bị chiếm kể cả khi khách vắng mặt); slot `BLOCKED` không được coi là thời lượng có thể đặt.
3. **Given** có booking trải trên các khung giờ, **When** owner xem giờ cao điểm, **Then** hệ thống xếp hạng từng khoảng 60 phút theo giờ địa phương `Asia/Ho_Chi_Minh` dựa trên thời lượng sân được booking xác nhận; không đưa booking bị từ chối/hủy/hết hạn vào số liệu.
4. **Given** không có dữ liệu phù hợp trong khoảng chọn, **When** owner mở thống kê, **Then** hiển thị trạng thái chưa có dữ liệu, không trình bày số liệu giả hoặc tổng mặc định gây hiểu nhầm.

### User Story 5 — Check-in đơn đặt sân (Priority: P1)

Khi người chơi đến sân, owner quét mã QR trên đơn đặt sân (spec 060) hoặc nhập mã đặt sân dạng chữ để ghi nhận khách đã đến, và thu phần tiền còn lại nếu có. Không có check-in thì đơn sẽ thành `NO_SHOW` khi hết giờ và người chơi không được đánh giá (spec 060, 070).

**Independent Test**: Với một đơn `CONFIRMED` thuộc venue của owner, quét QR trong khung giờ cho phép, xác nhận đã thu tiền; kiểm tra `checkedInAt` được ghi, `paymentStatus = PAID`, mã không dùng lại được, và sau giờ kết thúc đơn chuyển `COMPLETED`. Lặp lại với một đơn không check-in và kiểm tra đơn chuyển `NO_SHOW`.

**Acceptance Scenarios**:

1. **Given** đơn `CONFIRMED` thuộc venue của owner, chưa check-in và đang trong khung giờ cho phép (từ 30 phút trước giờ bắt đầu sớm nhất tới giờ kết thúc muộn nhất của đơn), **When** owner quét QR, **Then** server ghi `checkedInAt`, đơn vẫn ở `CONFIRMED`, owner thấy tên khách, sân con, khung giờ và số tiền còn phải thu.
2. **Given** không quét được QR (camera lỗi, màn hình khách mờ), **When** owner nhập mã đặt sân dạng chữ, **Then** kết quả giống hệt quét QR.
3. **Given** QR hoặc mã sai, thuộc venue khác, đơn không ở `CONFIRMED`, đơn đã check-in, hoặc ngoài khung giờ cho phép, **When** owner gửi check-in, **Then** hệ thống từ chối, nêu lý do cụ thể và không thay đổi đơn.
4. **Given** đơn trả tại sân (`AT_VENUE`) hoặc đặt cọc (`DEPOSIT`), **When** check-in thành công, **Then** app hiện số tiền còn phải thu (`total` hoặc `total − depositAmount`); owner bấm "Đã thu tiền" thì `paymentStatus` chuyển `PAID`; chưa bấm thì giữ nguyên và owner xác nhận lại được trước giờ kết thúc.
5. **Given** đơn đã check-in, **When** qua giờ kết thúc muộn nhất của đơn, **Then** server chuyển đơn sang `COMPLETED`; đơn chưa check-in chuyển sang `NO_SHOW`. Owner không tự chuyển hai trạng thái này.
6. **Given** đơn thuộc chuỗi đặt cố định theo tuần (spec 041), **When** owner check-in một buổi, **Then** chỉ buổi đó được ghi nhận; mỗi buổi có QR riêng.

## Lịch tổng hợp ngày/tuần

- Lịch nghiệp vụ thể hiện các booking theo venue/court và thời gian bắt đầu/kết thúc, dùng múi giờ `Asia/Ho_Chi_Minh`.
- `PENDING` cần phân biệt rõ là chờ owner quyết định; `CONFIRMED` là booking đã được chấp thuận; `REJECTED`, `CANCELLED`, `EXPIRED`, `COMPLETED`, `NO_SHOW` vẫn có thể được phân biệt trong ngữ cảnh lịch sử nhưng không chiếm slot khả dụng. Đơn `CONFIRMED` đã check-in hiển thị nhãn "Đã đến".
- `HOLD` chưa phải booking đã xác nhận. Nếu được dùng để cảnh báo xung đột tạm thời, phải biểu thị riêng và tuân theo `holdExpiresAt`; không tính như booking bền vững.
- Slot `BLOCKED` do Feature 111, bao gồm `DROP_IN`, phải phản ánh đúng tình trạng bận/không thể đặt. Buổi drop-in được nhận diện từ `dropInSessions`, tránh hiển thị như một booking người chơi thông thường.
- Lịch tuần nhóm dữ liệu theo ngày trong tuần; không tự đặt quy tắc hiển thị hoặc bố cục giao diện.

## Functional Requirements

- **FR-001**: Chỉ người dùng đã xác thực có `role = OWNER` và `ownerStatus = APPROVED` mới được thực hiện các thao tác vận hành trong spec này. Quyền được kiểm tra ở server, không chỉ dựa vào giao diện.
- **FR-002**: Mọi booking, court, drop-in, đăng ký, voucher và báo cáo/thống kê phải được giới hạn theo venue mà `venues.ownerId` khớp UID owner; court và dữ liệu con chỉ được truy cập qua venue cha thuộc owner.
- **FR-003**: Owner có thể xem các booking venue mình và xử lý booking chỉ khi `status = PENDING`, chưa quá `approvalDeadline` và booking/slot vẫn hợp lệ tại thời điểm quyết định. Danh sách đơn `PENDING` sắp theo `approvalDeadline` gần nhất, mỗi đơn có đồng hồ đếm ngược; owner nhận `BOOKING_NEW` khi có đơn mới (spec 100). Quá hạn thì `booking-timeout` tự chuyển `REJECTED` và hoàn tiền (spec 050); quyết định gửi tới sau hạn bị từ chối.
- **FR-004**: Duyệt booking chỉ cho phép chuyển `PENDING → CONFIRMED`; từ chối chỉ cho phép `PENDING → REJECTED`. Mọi chuyển trạng thái khác thuộc các luồng hiện có và không được owner giả lập.
- **FR-004a**: Owner có thể hủy một booking `CONFIRMED` của venue mình khi buổi chơi chưa bắt đầu và chưa check-in (ví dụ sân hỏng đột xuất), bắt buộc nhập lý do. Booking chuyển `CANCELLED` với `cancelledBy = OWNER`, khách được hoàn 100% số tiền đã trả vào ví giả lập (ghi `refundAmount`), slot được nhả trong cùng giao dịch, và khách nhận `BOOKING_CANCELLED` (spec 100).
- **FR-005**: Khi duyệt, hệ thống phải kiểm tra lại booking còn hiệu lực, slot còn gắn đúng booking và không bị `BLOCKED`/đơn khác chiếm. Khi từ chối, hệ thống giải phóng đúng slot của booking như máy trạng thái trong database README; không xóa slot của booking khác.
- **FR-006**: Nếu booking hết hạn, đã hủy/từ chối/xác nhận ở nơi khác, slot đã thay đổi, hoặc quyền owner/venue bị thu hồi/khóa trong lúc màn hình mở, quyết định phải thất bại an toàn, tải lại trạng thái mới và không ghi một phần.
- **FR-007**: Owner có thể xem lịch tổng hợp ngày/tuần của venue mình theo quy tắc nghiệp vụ ở mục “Lịch tổng hợp ngày/tuần”, kết hợp booking và trạng thái slot liên quan.
- **FR-008**: Owner có thể tạo buổi drop-in với dữ liệu sẵn có trong schema `dropInSessions`: `venueId`, `ownerId`, `districtId`, `courtIds`, `date`, `startAt`, `endAt`, `level`, `capacity`, `registeredCount`, `pricePerPerson`, `status`, `note`. Không thêm field hoặc enum mới.
- **FR-009**: Tạo drop-in phải xác minh venue/court thuộc owner, venue đang `ACTIVE`, court đang hoạt động, thời gian nằm trong giờ vận hành và không xung đột slot. Buổi chiếm các slot 30 phút tương ứng bằng `slots.status = BLOCKED`, `blockReason = DROP_IN`; thao tác phải toàn vẹn, không khóa một phần.
- **FR-010a**: Owner có thể sửa buổi drop-in chưa bắt đầu (giờ, court, giá, sức chứa, trình độ, ghi chú) hoặc hủy buổi. Sửa giờ hoặc court phải chiếm/nhả slot `DROP_IN` tương ứng trong cùng giao dịch và không xung đột slot khác. Tăng sức chứa thì đẩy người từ hàng chờ lên theo spec 081. Người đã đăng ký giữ nguyên số tiền đã chốt và được hủy hoàn 100% (spec 080). Hủy buổi: mọi đăng ký chuyển "Buổi bị hủy" và được hoàn 100%, mọi lượt chờ chuyển "Buổi bị hủy", slot `DROP_IN` được nhả. Người đã đăng ký nhận `DROPIN_CHANGED`, người đang chờ nhận `DROPIN_WAITLIST_CLOSED` (spec 100).
- **FR-010**: `capacity` là số nguyên dương; `pricePerPerson` là số nguyên VND dương hoặc bằng 0 nếu buổi miễn phí; `registeredCount` được hệ thống quản lý, không cho owner tự sửa. Sức chứa mới phải lớn hơn hoặc bằng số người đã đăng ký đang được tính vào sức chứa.
- **FR-011**: Owner có thể xem danh sách và trạng thái đăng ký thuộc buổi của mình theo các enum hiện có `REGISTERED`, `WAITLISTED`, `CANCELLED`, `CHECKED_IN`. Owner không duyệt riêng từng đăng ký; đăng ký và hàng chờ tuân theo transaction Feature 08. Owner không tự chỉnh `partySize`, `amount`, `registeredCount` hay `qrToken`.
- **FR-012**: Owner có thể xác thực QR check-in chỉ khi QR khớp đăng ký, người tham gia và buổi do owner quản lý; token chỉ được chấp nhận một lần. Kết quả ghi nhận trạng thái hiện có `CHECKED_IN`.
- **FR-012a**: Owner có thể check-in đơn đặt sân bằng QR (`bookings.qrToken`) hoặc mã đặt sân dạng chữ, chỉ khi đơn thuộc venue của mình, đang `CONFIRMED`, chưa check-in và trong khung giờ từ 30 phút trước giờ bắt đầu sớm nhất tới giờ kết thúc muộn nhất của đơn. Check-in chạy qua Edge Function `checkin-booking`, chỉ ghi `checkedInAt` 🔒 và không đổi trạng thái đơn; `COMPLETED`/`NO_SHOW` do server chuyển khi hết giờ (đặc tả CSDL mục 4.5, spec 060 FR-029). Mỗi mã chỉ check-in được một lần.
- **FR-012b**: Khi check-in đơn `AT_VENUE` hoặc `DEPOSIT`, app PHẢI hiện số tiền còn phải thu; owner xác nhận "Đã thu tiền" thì `paymentStatus` chuyển `PAID` qua cùng Edge Function. Đây là cách duy nhất để khoản thu tại sân được tính là doanh thu theo FR-016.
- **FR-013**: Owner có thể tạo voucher chỉ cho venue thuộc mình với schema hiện có: `venueId`, `type` (`PERCENT`, `FIXED`), `value`, `maxDiscount`, `minOrderTotal`, `validFrom`, `validTo`, `usageLimit`, `usedCount`, `perUserLimit`, `isActive`. Voucher lưu tại `venues/{venueId}/vouchers/{code}`; mã phải duy nhất trên toàn hệ thống (kiểm tra ở server, đặc tả CSDL mục 4.6). Owner không tạo voucher toàn hệ thống (`systemVouchers`, do admin tạo ở spec 120).
- **FR-014**: Owner không được ghi `usedCount` hoặc dữ liệu `redemptions`; các lượt dùng chỉ được cập nhật bởi luồng áp dụng voucher. Quy tắc tính giảm giá và áp dụng voucher phải theo Feature 05, không tự đặt lại chính sách áp dụng booking.
- **FR-015**: Owner có thể xem thống kê riêng venue mình gồm doanh thu, tỷ lệ lấp đầy và khung giờ cao điểm dựa trên booking thực tế; thống kê theo ngày lấy từ `venues/{venueId}/dailyStats/{yyyyMMdd}` khi có dữ liệu, và không tự sửa tài liệu thống kê 🔒.
- **FR-016**: Báo cáo phải tách riêng các booking `DEPOSITED`, `PAID` và `REFUNDED`; dùng `depositAmount` cho nhóm đã đặt cọc và `total` cho nhóm thanh toán đủ. Booking `REFUNDED` được đếm riêng, số tiền hoàn lấy từ `refundAmount`. Thanh toán `AT_VENUE` chỉ được tính sau khi `paymentStatus = PAID`. Loại booking `REJECTED`, `CANCELLED`, `EXPIRED` và đơn chưa thanh toán khỏi số tiền đã thu; không dùng giá ước tính từ `priceRules` thay cho tiền booking thực tế đã ghi.
- **FR-017**: Tỷ lệ lấp đầy dùng thời lượng các slot khả dụng trong phạm vi venue/court và khoảng ngày làm mẫu số; slot thuộc booking `CONFIRMED`, `COMPLETED` hoặc `NO_SHOW` là tử số. Loại trừ slot `BLOCKED` khỏi mẫu số và tránh đếm một slot nhiều lần.
- **FR-018**: Khung giờ cao điểm được xếp theo tổng thời lượng slot có booking `CONFIRMED`, `COMPLETED` hoặc `NO_SHOW` trong từng khoảng 60 phút của ngày, theo giờ địa phương `Asia/Ho_Chi_Minh`; cùng một định nghĩa thời gian phải được dùng xuyên suốt kỳ báo cáo. Chỉ báo cáo dữ liệu có nguồn booking thực tế.
- **FR-019**: Không có dữ liệu hoặc dữ liệu thống kê chưa cập nhật phải được thể hiện rõ; không trình bày số liệu cũ như thời gian thực. Owner có thể tải lại để lấy số liệu mới.
- **FR-020**: Các màn hình danh sách, lịch, đăng ký, voucher và thống kê phải có trạng thái loading, dữ liệu, empty, error; lỗi có hành động thử lại thích hợp và không làm mất quyết định đã được máy chủ xác nhận.

## Validation

- Ngày giờ phải hợp lệ, `startAt < endAt`, theo ngày `date` dạng `yyyyMMdd` và múi giờ nghiệp vụ `Asia/Ho_Chi_Minh`; thời gian mở buổi tuân theo giờ hoạt động venue và ranh giới slot 30 phút hiện có.
- Drop-in không kéo qua nửa đêm trong một khoảng; nếu cần qua ngày phải tạo khoảng riêng theo quy ước Feature 111. `courtIds` phải có ít nhất một court thuộc venue owner.
- `capacity` phải là số nguyên dương; giá là `Long` VND không âm. `level` chỉ được nhận giá trị đã được Feature 08/database chốt; không tự thêm enum.
- Không tạo drop-in nếu slot tương ứng đã có `HOLD`, `BOOKED` hoặc `BLOCKED`; nếu một slot xung đột, toàn bộ yêu cầu thất bại.
- Booking chỉ được quyết định khi vẫn `PENDING` và dữ liệu slot/booking nhất quán; client phải làm mới thay vì quyết định trên dữ liệu cũ.
- Mã voucher phải không rỗng, duy nhất và phù hợp định danh `venues/{venueId}/vouchers/{code}`, không trùng mã nào trên toàn hệ thống; `type` phải là `PERCENT` hoặc `FIXED`; `value`, `maxDiscount`, `minOrderTotal`, `usageLimit`, `perUserLimit`, thời hạn phải hợp lệ theo Feature 05. Hạn mức/ý nghĩa chính xác cho `value` và `maxDiscount` kế thừa Feature 05, không suy diễn thành field mới.
- `validFrom` không được sau `validTo`; `usageLimit` và `perUserLimit` nếu có phải là số nguyên dương; `minOrderTotal` và `maxDiscount` không âm; `isActive` là boolean. Giá trị `usedCount` không được nhập/sửa bởi owner.
- Thống kê chỉ nhận phạm vi thời gian hợp lệ, không vượt ra ngoài venue owner; mọi kết quả cần phản ánh khoảng thời gian/múi giờ được chọn.

## Permission / Security

- Server phải xác minh danh tính đã đăng nhập, `role = OWNER`, `ownerStatus = APPROVED`, UID khớp `venues.ownerId`, và quyền sở hữu qua venue cha ở mọi lần đọc/ghi.
- Không tin `ownerId`, `venueId`, `courtId`, `bookingId`, `sessionId` hoặc mã QR do client cung cấp nếu chưa kiểm tra quan hệ trong dữ liệu đáng tin cậy.
- Owner chỉ đọc booking, drop-in/registration, voucher và `dailyStats` của venue mình. Không tiết lộ sự tồn tại hoặc dữ liệu của venue khác khi từ chối quyền.
- Các trường chỉ server ghi gồm trạng thái booking, tổng tiền/thanh toán, `registeredCount`, `qrToken`, `usedCount`, redemption và dữ liệu thống kê; owner không được sửa trực tiếp các trường này ngoài hành động nghiệp vụ được ủy quyền.
- Kiểm tra quyền không được dựa vào ẩn nút. Mọi yêu cầu trực tiếp hoặc replay phải bị từ chối nếu owner chưa duyệt, bị thu hồi quyền, venue/court không thuộc họ hoặc trạng thái đã đổi.
- QR không được phép tiết lộ thông tin cá nhân dư thừa; chỉ dùng token để đối chiếu đúng đăng ký và buổi, không cho check-in lặp.

## Error, Empty, Loading States

- **Loading**: thể hiện đang tải danh sách/lịch/thống kê hoặc đang xử lý quyết định; ngăn gửi lặp trong khi chưa có kết quả.
- **Empty**: nêu rõ chưa có booking, drop-in, đăng ký, voucher hoặc thống kê trong phạm vi đã chọn; cung cấp hành động phù hợp như đổi ngày hoặc tạo buổi/voucher nếu owner đủ quyền.
- **Validation**: chỉ rõ dữ liệu cần sửa; không lưu một phần buổi/slot hoặc voucher.
- **Conflict / stale state**: thông báo booking/slot/đăng ký đã thay đổi, tải lại dữ liệu mới nhất và không ghi đè trạng thái hiện hành.
- **QR error**: phân biệt QR không hợp lệ, đã dùng, không đúng buổi, đăng ký đã hủy hoặc ngoài quyền; không thay đổi đăng ký.
- **Permission error**: thông báo quyền không đủ mà không tiết lộ dữ liệu venue khác.
- **Read/write error**: giữ dữ liệu đã tải hoặc nội dung nhập được khi an toàn, nêu rõ kết quả thao tác chưa xác định và cho tải lại/đối soát trước khi thử lại.

## Offline / Network Failure

- Khi offline, dữ liệu đã tải có thể xem kèm dấu thời điểm cập nhật gần nhất; không coi cache là trạng thái mới nhất để duyệt/từ chối booking, mở buổi, cập nhật voucher hoặc check-in.
- Mọi thao tác cần xác nhận phía server. Nếu mạng mất sau khi gửi và chưa rõ kết quả, tải lại trạng thái booking/buổi/đăng ký/voucher trước khi cho thử lại; không tạo trùng buổi hoặc ghi quyết định hai lần.
- Tạo buổi và chiếm các slot phải thành công hoặc thất bại toàn bộ; lỗi mạng không được để lại slot `BLOCKED` một phần.
- Lỗi khi tải thống kê hiển thị dữ liệu cache nếu có kèm nhãn thời điểm cũ và trạng thái chưa cập nhật; không trình bày như báo cáo mới nhất.
- Khi mất mạng lúc quét QR, chưa báo check-in thành công cho tới khi server xác nhận; lần quét lại phải kiểm tra trạng thái hiện hành để tránh check-in trùng.

## Edge Cases

- Owner bị thu hồi `APPROVED`, đăng xuất hoặc venue bị `LOCKED` trong lúc đang thao tác.
- Booking được người chơi hủy, hết hạn hoặc được owner khác/thiết bị khác xử lý trước khi quyết định đến server.
- Booking không còn các slot tương ứng, slot bị `BLOCKED`, hoặc trạng thái slot không khớp với trạng thái booking.
- Hai yêu cầu tạo drop-in cùng court/slot đồng thời; chỉ một thao tác được phép ghi nhận và thao tác còn lại không tạo dữ liệu dở dang.
- Buổi nhiều court hoặc nhiều slot có một court không thuộc owner, không hoạt động, đã bị chiếm hoặc không có trong venue.
- Sức chứa bị giảm thấp hơn số người đã đăng ký; không làm mất đăng ký đã ghi nhận.
- Đăng ký chuyển sang `CHECKED_IN` trong khi bị hủy hoặc chuyển trạng thái khác trên thiết bị khác.
- QR được chụp lại, dùng cho buổi khác, hết hiệu lực nghiệp vụ hoặc được gửi hai lần đồng thời.
- Voucher trùng mã giữa hai owner, hết hạn, chưa đến thời gian hiệu lực, bị vô hiệu hóa, hết lượt, hoặc có `usedCount` thay đổi đồng thời.
- Booking có thanh toán giả lập chưa hoàn tất, đã hoàn tiền hoặc có giá trị tổng khác snapshot; báo cáo không được nhầm doanh thu đã thu với giá booking.
- Khoảng báo cáo không có booking, chỉ có booking bị từ chối/hủy, hoặc thống kê tổng hợp chưa chạy/còn cũ.
- Venue/court có giờ mở cửa qua nửa đêm hoặc cấu hình không hợp lệ; áp dụng ràng buộc giờ đã chốt trong Feature 111.

## Dependencies

- **Spec 010/011**: xác thực tài khoản, vai trò và chuyển chế độ; không cấp quyền owner thay cho `ownerStatus = APPROVED`.
- **Spec 110 — owner onboarding**: nguồn quyết định `ownerStatus`/`role`; chỉ owner được duyệt mới được vào nghiệp vụ vận hành.
- **Spec 111 — owner venue setup**: nguồn venue/court, giờ hoạt động, `priceRules`, slot 30 phút và `BLOCKED`/`blockReason`; đặc biệt `DROP_IN` phải nhất quán với khóa giờ.
- **Spec 040/041/050/060**: booking, máy trạng thái booking, hạn duyệt `approvalDeadline`, thanh toán/voucher, hủy và QR booking.
- **Spec 080/081**: đăng ký drop-in, thanh toán, QR, hàng chờ và hủy; phản ứng của đăng ký và hàng chờ khi owner sửa/hủy buổi (FR-010a).
- **Spec 100**: `BOOKING_NEW` cho owner; `BOOKING_REJECTED`, `BOOKING_CANCELLED`, `DROPIN_CHANGED`, `DROPIN_WAITLIST_CLOSED` cho khách khi owner thao tác.
- **Spec 120 — admin**: thu hồi quyền, khóa venue và tạo `systemVouchers`.
- **Database README**: nguồn schema collections `users`, `venues`, `courts`, `slots`, `bookings`, `vouchers`, `dropInSessions`, `registrations`, `dailyStats`, `reports` và `systemStats`.
- **Proposal.html và docs/guides/w1-w2-spec-assignment.md**: nguồn phạm vi nhóm 11, lịch tổng hợp, drop-in, voucher và thống kê.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% quyết định booking hợp lệ từ owner đã duyệt chuyển đúng `PENDING → CONFIRMED` hoặc `PENDING → REJECTED`; không có chuyển trạng thái ngoài luồng này.
- **SC-002**: 100% yêu cầu vận hành từ owner không được duyệt hoặc với venue/court không thuộc quyền sở hữu bị chặn ở server, không rò rỉ dữ liệu liên quan.
- **SC-003**: 100% buổi drop-in được tạo có sức chứa/giá hợp lệ và slot `DROP_IN` đồng bộ toàn bộ; không có tình huống chiếm slot một phần.
- **SC-004**: 100% QR hoặc mã check-in (buổi drop-in và đơn đặt sân) sai, đã dùng, đã hủy, ngoài khung giờ cho phép hoặc ngoài quyền bị từ chối; mã hợp lệ chỉ ghi nhận check-in một lần.
- **SC-005**: 100% voucher do owner tạo gắn với venue thuộc quyền sở hữu, tuân thủ schema hiện có và không cho owner sửa bộ đếm/lượt dùng.
- **SC-006**: Các tổng doanh thu, tỷ lệ lấp đầy và khung giờ cao điểm có thể đối chiếu về dữ liệu booking thực tế trong đúng venue và khoảng thời gian; không tính booking bị từ chối/hủy/hết hạn như booking hiệu lực.
- **SC-007**: Ít nhất 90% owner thử nghiệm có thể hoàn tất các thao tác duyệt/từ chối booking, mở drop-in, check-in QR và tạo voucher hợp lệ mà không cần hỗ trợ trực tiếp.
- **SC-008**: Danh sách và lịch theo ngày/tuần tải trong dưới 3 giây trên mạng 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường, theo mục tiêu vận hành đã dùng ở Feature 111.
- **SC-009**: Mọi luồng có trạng thái loading, empty, error và mất mạng rõ ràng; thao tác có kết quả chưa xác định không tạo dữ liệu trùng khi người dùng thử lại.

## Open Questions / Assumptions

- **Assumption**: “số slot” khi mở buổi chỉ rõ số chỗ/người tối đa (`capacity`), không thêm field mới; thời lượng và court(s) xác định số slot lịch 30 phút cần chiếm.
- **Assumption**: Buổi drop-in tạo trực tiếp ở trạng thái `OPEN`; hệ thống quản lý `registeredCount` và chuyển trạng thái theo sức chứa như quy tắc Feature 08.
- **Assumption**: Giá drop-in là `pricePerPerson` (Long VND) và không âm để hỗ trợ buổi miễn phí; thanh toán/hoàn tiền người tham gia thuộc Feature 08.
- **Assumption**: Owner tạo voucher venue-specific; voucher toàn hệ thống (`systemVouchers`) chỉ do admin tạo. Áp dụng, giảm giá chính xác và quy tắc cộng dồn kế thừa Feature 05.
- **Assumption**: Booking `REFUNDED` được báo cáo thành nhóm hoàn tiền riêng, tổng tiền hoàn cộng từ `refundAmount` (có cả hoàn một phần khi hủy trễ, spec 060).
- **Assumption**: Tỷ lệ lấp đầy tính từ thời lượng slot của booking `CONFIRMED`, `COMPLETED` hoặc `NO_SHOW` chia tổng thời lượng slot có thể đặt; loại slot `BLOCKED` và ngoài giờ mở cửa khỏi mẫu số, tính theo venue/court và khoảng thời gian đã chọn.
- **Open Question**: Database README có `dailyStats` nhưng chưa liệt kê schema trường/chỉ số; cần thống nhất hợp đồng báo cáo với phụ trách database trước planning. Độ dài khoảng giờ cao điểm đã được chốt là 60 phút.
- **Open Question**: Database README liệt kê `reports` trong phạm vi dữ liệu nhưng nhóm 11 proposal không giao xử lý report cho owner; spec này không thêm luồng báo cáo vi phạm cho owner.

## Mâu thuẫn tài liệu

- Proposal và `docs/guides/w1-w2-spec-assignment.md` nói owner “duyệt danh sách đăng ký” drop-in; đã làm rõ “duyệt” là xem danh sách, không phê duyệt từng người, để giữ các trạng thái hiện có `REGISTERED`, `WAITLISTED`, `CANCELLED`, `CHECKED_IN`.
- Database README cho biết owner đọc `dailyStats` và có tác vụ `daily-stats` tổng hợp hằng đêm, nhưng chưa định nghĩa schema thống kê; proposal yêu cầu thống kê doanh thu/tỷ lệ lấp đầy/khung giờ đông nhất. Spec nêu định nghĩa nghiệp vụ, phân nhóm thanh toán và khoảng giờ cao điểm 60 phút từ booking thực tế; trường/chỉ số tổng hợp vẫn cần chốt trong hợp đồng CSDL.
- `specs/111-owner-venue-setup/spec.md` đã xác định `DROP_IN` là loại `blockReason` thuộc spec vận hành và việc ghi slot phải khớp mô hình 30 phút. Không thấy mâu thuẫn; spec này giữ nguyên quy tắc đó.
- Đã đối chiếu với spec 040, 041, 050, 060, 080, 081, 100, 111, 120 ngày 2026-10-13.
