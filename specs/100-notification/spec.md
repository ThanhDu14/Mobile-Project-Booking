# Feature Specification: Thông báo

**Feature Branch**: `feature/10-notification`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Feature 10 – Notification cho mobile app đặt sân cầu lông."

**Nhóm tính năng**: [10 — Thông báo](../../docs/features/10-notification/README.md) · **Phụ trách**: D · **Liên quan**: [đặc tả CSDL chung](../../docs/design/database/README.md), spec 011 (tài khoản và hồ sơ), spec 040/060/110 (đặt và quản lý lịch đặt), spec 050/112 (voucher), spec 070 (đánh giá và yêu thích), spec 080/081 (buổi vãng lai và hàng chờ), spec 091 (chat), spec 120 (admin)

## Mục tiêu

Giúp người dùng nhận biết các sự kiện nghiệp vụ liên quan đến đặt sân, lịch chơi, buổi vãng lai, khuyến mãi và các cập nhật được liệt kê trong danh mục thông báo chung; đồng thời xem lại thông báo trong trung tâm, đánh dấu đã đọc và bật/tắt loại thông báo.

## Actors

- **Người chơi**: người nhận thông báo đặt sân, nhắc lịch, buổi vãng lai, khuyến mãi, phản hồi đánh giá và tin nhắn; xem và quản lý trạng thái đọc/cài đặt của mình.
- **Người nộp hồ sơ chủ sân**: nhận kết quả duyệt/từ chối hồ sơ.
- **Chủ sân**: nguồn phát sinh một số sự kiện đặt sân, buổi vãng lai, voucher và phản hồi đánh giá; nếu tài khoản đang hoạt động thì nhận `SYSTEM` như các người dùng khác.
- **Admin**: nguồn phát sinh kết quả duyệt hồ sơ và thông báo hệ thống; quyền và quy trình gửi `SYSTEM` thuộc Feature 12.
- **Các tính năng nghiệp vụ**: nguồn phát sinh sự kiện; không tự ghi hộp thư thông báo từ client theo quy ước CSDL.

## Clarifications

### Session 2026-10-06

- Q: Khi nào cần gửi `BOOKING_REMINDER` cho một đơn có thể gồm nhiều khung giờ? → A: Gửi một lời nhắc, 2 giờ trước khung giờ bắt đầu sớm nhất, chỉ khi đơn đã `CONFIRMED`.
- Q: Khi người dùng tắt một loại thông báo, điều đó nên ngăn gửi push, ngăn tạo thông báo trong trung tâm, hay cả hai? → A: Ngăn push; vẫn tạo và hiển thị thông báo trong trung tâm.
- Q: Khi một đơn đặt sân bị hủy, ai nên nhận `BOOKING_CANCELLED`? → A: Người đặt nhận khi chủ sân hoặc hệ thống hủy; không nhận khi tự hủy.
- Q: Khi buổi vãng lai bị đổi giờ hoặc hủy, ai nên nhận `DROPIN_CHANGED`? → A: Chỉ người đã đăng ký buổi chơi.
- Q: Khi admin gửi thông báo `SYSTEM`, nhóm người dùng nào nên nhận? → A: Tất cả tài khoản người dùng đang hoạt động, gồm người chơi và chủ sân.
- Q: Khi sân đang được người chơi theo dõi phát hành voucher, ai nên nhận `PROMOTION`? → A: Chỉ người theo dõi đủ điều kiện sử dụng voucher.
- Q: Trung tâm thông báo có cần nút “Đánh dấu tất cả đã đọc” không? → A: Có, hỗ trợ đánh dấu từng thông báo và đánh dấu tất cả đã đọc.
- Q: Trung tâm thông báo nên sắp xếp các mục theo thứ tự nào? → A: Mới nhất trước.
- Q: Nếu người nhận đang mở cuộc trò chuyện, hệ thống nên xử lý thông báo `CHAT_MESSAGE` như thế nào? → A: Vẫn lưu trong trung tâm nhưng không gửi push khi người nhận đang xem cuộc trò chuyện đó.
- Q: Với tài khoản mới, loại thông báo nào nên bật mặc định? → A: Bật các loại nghiệp vụ; tắt `PROMOTION`.
- Q: Khi người dùng chạm vào thông báo có liên kết tới đơn đặt sân hoặc buổi chơi, màn hình nào nên mở nếu nội dung đó không còn khả dụng? → A: Mở nội dung liên quan nếu còn khả dụng; nếu không, mở chi tiết thông báo.
- Q: Nếu cùng một sự kiện nghiệp vụ được xử lý lại, có nên tạo thêm một thông báo giống hệt cho cùng người nhận không? → A: Không tạo bản trùng cho cùng sự kiện, người nhận và loại.
- Q: Người dùng có cần xóa thông báo khỏi trung tâm không? → A: Không có chức năng xóa thông báo.
- Q: Thông báo nên được lưu trong trung tâm bao lâu? → A: Giữ thông báo cho đến khi tài khoản bị xóa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhận thông báo đặt sân và nhắc lịch (Priority: P1)

Người chơi nhận thông tin khi đơn đặt sân được xác nhận hoặc bị từ chối, và được nhắc trước giờ chơi. Thông báo cũng nằm trong trung tâm thông báo để người chơi xem lại.

**Why this priority**: Đây là các sự kiện đặt sân được nêu trực tiếp trong phạm vi Feature 10 và cho người chơi biết kết quả đơn cũng như lịch đã đặt.

**Independent Test**: Tạo các sự kiện xác nhận, từ chối và đến mốc nhắc trên đơn kiểm thử; đối chiếu người nhận, loại thông báo và nội dung trong hộp thư/push theo quy tắc được các spec nguồn thống nhất.

**Acceptance Scenarios**:

1. **Given** đơn đặt sân của người chơi được xác nhận, **When** sự kiện xác nhận được phát sinh, **Then** hệ thống tạo thông báo `BOOKING_CONFIRMED` cho người nhận được quy định bởi luồng đặt sân.
2. **Given** đơn đặt sân bị từ chối, **When** sự kiện từ chối được phát sinh, **Then** hệ thống tạo `BOOKING_REJECTED` cho người nhận được quy định bởi luồng đặt sân.
3. **Given** đơn đặt sân ở trạng thái `CONFIRMED`, **When** còn 2 giờ trước khung giờ bắt đầu sớm nhất của đơn, **Then** hệ thống tạo một `BOOKING_REMINDER` cho người đặt.
4. **Given** một thông báo được tạo, **When** người nhận có quyền nhận push và bật loại thông báo đó, **Then** thông báo được gửi qua kênh đẩy; nếu push không tới được, thông báo trong trung tâm vẫn có thể được xem.
5. **Given** đơn của người chơi bị chủ sân hoặc hệ thống hủy, **When** việc hủy có hiệu lực, **Then** người đặt nhận `BOOKING_CANCELLED`; nếu người chơi tự hủy đơn của mình thì không nhận thông báo này.

### User Story 2 - Nhận cập nhật buổi vãng lai và khuyến mãi (Priority: P1)

Người chơi nhận thông tin đăng ký buổi vãng lai, được lên từ hàng chờ, buổi bị đổi/hủy và khuyến mãi từ sân đang theo dõi.

**Why this priority**: Các tình huống này được liệt kê cụ thể trong README Feature 10 và phụ thuộc vào trạng thái buổi, hàng chờ và sân người chơi theo dõi.

**Independent Test**: Phát sinh từng sự kiện buổi vãng lai/hàng chờ và voucher từ sân được theo dõi; kiểm tra loại thông báo và người nhận theo quy tắc của spec nguồn.

**Acceptance Scenarios**:

1. **Given** đăng ký buổi vãng lai thành công, **When** sự kiện đăng ký được xác nhận, **Then** tạo thông báo `DROPIN_REGISTERED`.
2. **Given** người chơi đang trong hàng chờ và được chuyển lên khi có chỗ, **When** trạng thái đăng ký được cập nhật, **Then** tạo thông báo `DROPIN_SPOT_OPENED` cho người được chuyển lên.
3. **Given** người chơi đã đăng ký buổi vãng lai bị đổi giờ hoặc hủy, **When** thay đổi có hiệu lực, **Then** người đã đăng ký nhận `DROPIN_CHANGED`; người chỉ ở hàng chờ không nhận loại này.
4. **Given** một người chơi theo dõi sân phát hành voucher, **When** người chơi đó đủ điều kiện sử dụng voucher theo quy tắc Feature 05, **Then** người chơi nhận `PROMOTION`; người theo dõi không đủ điều kiện không nhận loại thông báo này.

### User Story 3 - Xem trung tâm thông báo và đánh dấu đã đọc (Priority: P1)

Người dùng xem các thông báo thuộc hộp thư của mình, phân biệt mục đã đọc/chưa đọc và đánh dấu thông báo đã đọc.

**Why this priority**: Trung tâm và đánh dấu đã đọc là chức năng cốt lõi được nêu trong README Feature 10 và lưu trạng thái đọc trong CSDL chung.

**Independent Test**: Tạo các thông báo cho một tài khoản, mở trung tâm, đánh dấu một thông báo rồi đánh dấu tất cả đã đọc; kiểm tra `isRead` sau khi tải lại.

**Acceptance Scenarios**:

1. **Given** người dùng đã đăng nhập và hộp thư có thông báo, **When** mở trung tâm, **Then** chỉ thấy thông báo thuộc tài khoản của mình theo thứ tự mới nhất trước và nhận biết được trạng thái đọc/chưa đọc.
2. **Given** người dùng có thông báo chưa đọc, **When** đánh dấu thông báo đó đã đọc, **Then** trạng thái `isRead` của mục đó được cập nhật.
3. **Given** người dùng có ít nhất một thông báo chưa đọc, **When** chọn “Đánh dấu tất cả đã đọc”, **Then** mọi thông báo thuộc hộp thư của người dùng được đánh dấu đã đọc.
4. **Given** người dùng chưa có thông báo, **When** mở trung tâm, **Then** trung tâm thể hiện trạng thái không có thông báo.
5. **Given** thao tác tải hoặc cập nhật trạng thái gặp lỗi, **When** lỗi xảy ra, **Then** ứng dụng báo lỗi, không crash và cho phép người dùng thử lại.
6. **Given** người dùng không sở hữu một thông báo, **When** cố xem hoặc sửa trạng thái đọc của thông báo đó, **Then** thao tác bị từ chối.
7. **Given** người dùng chạm vào thông báo có liên kết, **When** nội dung liên quan còn tồn tại và người dùng còn quyền truy cập, **Then** mở nội dung đó; nếu không, mở chi tiết thông báo với nội dung đã lưu.

### User Story 4 - Bật/tắt loại thông báo (Priority: P2)

Người dùng điều chỉnh các loại thông báo muốn nhận.

**Why this priority**: Cài đặt theo loại nằm trong phạm vi Feature 10 và spec 070 đã xác định có thể tắt thông báo khuyến mãi từ cài đặt này.

**Independent Test**: Thay đổi cài đặt của một loại thông báo, sau đó phát sinh sự kiện loại đó và một loại khác; kiểm tra việc gửi tuân theo cài đặt đã chốt.

**Acceptance Scenarios**:

1. **Given** người dùng mở cài đặt thông báo, **When** xem các loại, **Then** mỗi loại thông báo có cài đặt riêng; với tài khoản mới, các loại đều bật mặc định ngoại trừ `PROMOTION`.
2. **Given** người dùng tắt một loại thông báo, **When** một sự kiện thuộc loại đó phát sinh, **Then** thông báo vẫn xuất hiện trong trung tâm nhưng không gửi push; các loại khác giữ nguyên cài đặt.
3. **Given** người dùng bật lại một loại thông báo, **When** một sự kiện thuộc loại đó phát sinh, **Then** thông báo được lưu trong trung tâm và đủ điều kiện gửi push nếu quyền hệ điều hành cho phép.
4. **Given** lưu cài đặt thất bại, **When** thao tác hoàn tất, **Then** người dùng được báo lỗi và có thể thử lại.

### User Story 5 - Nhận thông báo về hồ sơ, đánh giá, chat và hệ thống (Priority: P2)

Người dùng liên quan nhận cập nhật kết quả hồ sơ chủ sân, phản hồi đánh giá, tin nhắn và thông báo hệ thống theo các sự kiện đã quy định.

**Why this priority**: Các loại này nằm trong danh mục chung CSDL; một số phụ thuộc vào các feature khác hoặc phạm vi admin cần được xác nhận.

**Independent Test**: Phát sinh các sự kiện nguồn và xác nhận bản ghi có đúng type (`OWNER_APPROVED`, `OWNER_REJECTED`, `REVIEW_REPLIED`, `CHAT_MESSAGE`, `SYSTEM`) cho đúng tài khoản theo quy tắc của feature nguồn.

**Acceptance Scenarios**:

1. **Given** admin duyệt hoặc từ chối hồ sơ chủ sân, **When** kết quả được ghi nhận, **Then** loại tương ứng là `OWNER_APPROVED` hoặc `OWNER_REJECTED`.
2. **Given** chủ sân phản hồi đánh giá, **When** phản hồi được ghi nhận, **Then** loại thông báo là `REVIEW_REPLIED` và người nhận được xác định theo Feature 07.
3. **Given** phát sinh tin nhắn mới trong một cuộc trò chuyện, **When** một thành viên nhận được tin nhắn, **Then** tạo `CHAT_MESSAGE` trong trung tâm; nếu người đó đang xem chính cuộc trò chuyện ấy thì không gửi push, còn các trường hợp khác gửi push theo cài đặt và quyền hệ điều hành.
4. **Given** admin phát thông báo hệ thống thuộc phạm vi Feature 12, **When** thông báo được gửi, **Then** hộp thư của mọi tài khoản người dùng đang hoạt động, gồm người chơi và chủ sân, có thông báo `SYSTEM`; push được gửi theo cài đặt loại và quyền hệ điều hành của từng tài khoản/thiết bị.

### Edge Cases

- Người dùng chưa cấp quyền thông báo của hệ điều hành: push không hiển thị; hộp thư trong ứng dụng vẫn hoạt động.
- Thiết bị ngoại tuyến hoặc push thất bại: CSDL chung xác định hộp thư thông báo; chính sách retry và thời điểm đồng bộ chưa được quy định.
- Một sự kiện nguồn được xử lý lặp: cùng sự kiện, người nhận và type chỉ có một bản ghi thông báo.
- Người dùng đăng xuất trên một thiết bị: thiết bị đó ngừng nhận push cho tài khoản theo spec 011; khi xóa tài khoản, thông báo bị xóa theo FR-016.
- Sự kiện nguồn bị hủy/đổi sau khi thông báo đã tạo: quy tắc cập nhật hoặc giữ nguyên thông báo cũ chưa được quy định.
- Thông báo nguồn liên kết tới đối tượng không còn tồn tại hoặc người nhận không còn quyền truy cập: mở chi tiết thông báo bằng nội dung đã lưu, không mở nội dung nguồn.
- Thao tác đọc/cài đặt khi mất mạng: phải báo lỗi và không làm ứng dụng crash; cách xử lý thay đổi offline chưa được quy định.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI hỗ trợ các type được khai báo trong CSDL chung: `BOOKING_CONFIRMED`, `BOOKING_REJECTED`, `BOOKING_CANCELLED`, `BOOKING_REMINDER`, `DROPIN_REGISTERED`, `DROPIN_SPOT_OPENED`, `DROPIN_CHANGED`, `PROMOTION`, `OWNER_APPROVED`, `OWNER_REJECTED`, `REVIEW_REPLIED`, `CHAT_MESSAGE` và `SYSTEM`.
- **FR-002**: Mỗi loại thông báo PHẢI được phát sinh từ sự kiện và nhóm nguồn được quy định trong bảng danh mục dưới đây; nhóm phát sinh chịu trách nhiệm thống nhất người nhận và dữ liệu sự kiện với D.
- **FR-003**: Hộp thư của người dùng PHẢI hiển thị các trường thông báo đã được CSDL chung quy định: loại, tiêu đề, nội dung, dữ liệu liên quan nếu có, trạng thái đã đọc và thời điểm tạo; danh sách PHẢI sắp xếp mới nhất trước.
- **FR-004**: Người dùng PHẢI xem được thông báo thuộc hộp thư của mình và KHÔNG ĐƯỢC xem hoặc cập nhật trạng thái thông báo của tài khoản khác.
- **FR-005**: Người dùng PHẢI đánh dấu được từng thông báo của mình là đã đọc và PHẢI có thể đánh dấu tất cả thông báo thuộc hộp thư của mình là đã đọc; trường duy nhất người dùng được sửa trên các bản ghi thông báo là `isRead`.
- **FR-006**: Tính năng PHẢI cung cấp trung tâm thông báo, hành vi bật/tắt từng loại và thông báo đẩy FCM theo phạm vi Feature 10.
- **FR-007**: Cài đặt bật/tắt theo loại PHẢI điều khiển việc gửi push. Tắt một loại KHÔNG ĐƯỢC ngăn tạo hoặc hiển thị bản tin tương ứng trong trung tâm thông báo.
- **FR-008**: Hệ thống PHẢI xin quyền runtime cho thông báo theo constitution; từ chối quyền không được làm các chức năng khác của ứng dụng bị lỗi.
- **FR-009**: Màn hình trung tâm và cài đặt PHẢI xử lý trạng thái đang tải, có dữ liệu, rỗng và lỗi theo constitution; lỗi/mất mạng không được làm app crash.
- **FR-010**: Với mỗi đơn đặt sân ở trạng thái `CONFIRMED`, hệ thống PHẢI tạo một `BOOKING_REMINDER` cho người đặt vào thời điểm 2 giờ trước khung giờ bắt đầu sớm nhất trong đơn. Đơn ở trạng thái khác KHÔNG ĐƯỢC nhận lời nhắc này.
- **FR-011**: `BOOKING_CANCELLED` PHẢI gửi cho người đặt khi đơn bị chủ sân hoặc hệ thống hủy; KHÔNG ĐƯỢC gửi loại này cho người đặt khi chính họ chủ động hủy. `DROPIN_CHANGED` PHẢI gửi cho người đã đăng ký buổi bị đổi giờ hoặc hủy và KHÔNG ĐƯỢC gửi cho người chỉ ở hàng chờ. `PROMOTION` CHỈ ĐƯỢC gửi cho người đang theo dõi sân và đủ điều kiện sử dụng voucher theo quy tắc Feature 05. `SYSTEM` PHẢI được tạo trong hộp thư của tất cả tài khoản người dùng đang hoạt động, gồm người chơi và chủ sân; push vẫn tuân theo cài đặt loại và quyền hệ điều hành. Với `CHAT_MESSAGE`, hệ thống PHẢI tạo mục trong trung tâm cho thành viên nhận tin; KHÔNG gửi push nếu người nhận đang xem chính cuộc trò chuyện đó, còn lại gửi theo cài đặt loại và quyền hệ điều hành.
- **FR-012**: Tài khoản mới PHẢI có cài đặt push bật mặc định cho mọi type ngoại trừ `PROMOTION`, type này mặc định tắt. Người dùng PHẢI có thể bật/tắt riêng từng type; quyền thông báo của hệ điều hành vẫn do người dùng cấp riêng.
- **FR-013**: Khi người dùng mở một thông báo có liên kết, ứng dụng PHẢI mở nội dung liên quan nếu nội dung còn tồn tại và người dùng còn quyền truy cập; nếu không, PHẢI mở chi tiết thông báo bằng nội dung đã lưu.
- **FR-014**: Hệ thống KHÔNG ĐƯỢC tạo nhiều bản ghi cho cùng một sự kiện nghiệp vụ, cùng một người nhận và cùng một type khi sự kiện đó được xử lý lại.
- **FR-015**: Trong phạm vi Feature 10, người dùng KHÔNG ĐƯỢC có chức năng xóa thông báo khỏi trung tâm.
- **FR-016**: Thông báo PHẢI được giữ trong trung tâm cho đến khi tài khoản của người nhận bị xóa; khi xóa tài khoản, thông báo của tài khoản được xóa theo spec 011.

### Danh mục type và sự kiện nguồn

| Type | Nguồn theo CSDL chung | Nội dung nghiệp vụ đã biết |
|---|---|---|
| `BOOKING_CONFIRMED` | 04, 06, 11 | Xác nhận đặt sân thành công |
| `BOOKING_REJECTED` | 04, 06, 11 | Đơn đặt sân bị từ chối |
| `BOOKING_CANCELLED` | 04, 06, 11 | Gửi cho người đặt khi chủ sân hoặc hệ thống hủy; không gửi khi người đặt tự hủy |
| `BOOKING_REMINDER` | 04, 06, 11 | Một lời nhắc cho người đặt, 2 giờ trước khung giờ bắt đầu sớm nhất của đơn `CONFIRMED` |
| `DROPIN_REGISTERED` | 08, 11 | Đăng ký buổi vãng lai thành công |
| `DROPIN_SPOT_OPENED` | 08, 11 | Có chỗ từ hàng chờ |
| `DROPIN_CHANGED` | 08, 11 | Buổi vãng lai bị hủy hoặc đổi giờ; gửi cho người đã đăng ký, không gửi cho người chỉ ở hàng chờ |
| `PROMOTION` | 05, 11 | Voucher/khuyến mãi của sân đang theo dõi; chỉ gửi cho follower đủ điều kiện sử dụng theo quy tắc Feature 05 |
| `OWNER_APPROVED` | 12 | Hồ sơ chủ sân được duyệt |
| `OWNER_REJECTED` | 12 | Hồ sơ chủ sân bị từ chối |
| `REVIEW_REPLIED` | 07, 11 | Chủ sân phản hồi đánh giá |
| `CHAT_MESSAGE` | 09 | Có tin nhắn mới; mục trung tâm vẫn được tạo khi người nhận đang xem chat nhưng không gửi push |
| `SYSTEM` | 12 | Thông báo hệ thống do admin gửi cho mọi tài khoản người dùng đang hoạt động, gồm người chơi và chủ sân |

### Key Entities *(include if feature involves data)*

- **Thông báo (Notification)**: mục thuộc hộp thư một người dùng, các trường theo CSDL chung là `type`, `title`, `body`, `data`, `isRead`, `createdAt`; người dùng chỉ sửa `isRead`; mục được giữ tới khi tài khoản bị xóa.
- **Cài đặt thông báo (Notification Preferences)**: map `notificationPrefs` trên hồ sơ người dùng, bật/tắt push theo type; tài khoản mới bật mọi type ngoại trừ `PROMOTION`; trạng thái này không ngăn lưu bản tin trong trung tâm.
- **Token FCM**: danh sách `fcmTokens` trên hồ sơ người dùng; CSDL chung quy định chủ tài khoản có thể ghi. Vòng đời token khi đăng xuất/đổi thiết bị chưa được quy định ở tài liệu Feature 10.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Với từng type trong danh mục, bộ kiểm thử xác nhận sự kiện nguồn tạo đúng type và đúng người nhận theo quy tắc đã được feature nguồn thống nhất.
- **SC-002**: Người dùng xem được trạng thái đã đọc/chưa đọc và sau thao tác đánh dấu đọc, `isRead` được cập nhật đúng trên thông báo của chính họ.
- **SC-003**: Trong kiểm thử phân quyền, 100% yêu cầu đọc/cập nhật thông báo của tài khoản khác bị từ chối.
- **SC-004**: Trong kiểm thử lỗi mạng, mở trung tâm hoặc cài đặt không làm app crash và người dùng nhận được trạng thái lỗi có thể thử lại.
- **SC-005**: Trong kiểm thử xử lý lại cùng một sự kiện, mỗi người nhận chỉ có một bản ghi cho type thông báo tương ứng.

## Dependencies

- **Spec 040/060/110 — Đặt sân và quản lý lịch đặt**: xác nhận/từ chối/hủy đơn, dữ liệu lịch chơi và sự kiện nhắc (`BOOKING_*`).
- **Spec 050/112 — Thanh toán/voucher và vận hành chủ sân**: phát sinh voucher/khuyến mãi từ sân đang theo dõi (`PROMOTION`).
- **Spec 070 — Đánh giá và yêu thích**: xác định sân yêu thích đồng thời là sân theo dõi; sự kiện phản hồi đánh giá (`REVIEW_REPLIED`). Clarification của spec 070 xác nhận hai danh sách là một và khuyến mãi điều khiển từ cài đặt Feature 10.
- **Spec 080/081/112 — Buổi vãng lai và hàng chờ**: đăng ký thành công, được lên từ hàng chờ, buổi đổi/hủy (`DROPIN_*`).
- **Spec 091 — Chat**: tin nhắn mới (`CHAT_MESSAGE`), và trạng thái người nhận có đang xem cuộc trò chuyện hay không để quyết định gửi push.
- **Spec 120 — Admin**: duyệt/từ chối hồ sơ chủ sân và phát thông báo hệ thống (`OWNER_*`, `SYSTEM`).
- **Spec 010/011 — Tài khoản và hồ sơ**: uid/người nhận, quyền thông báo hệ điều hành, token FCM, cài đặt theo tài khoản và xóa tài khoản; spec 011 đã nêu thông báo/cài đặt bị xóa khi xóa tài khoản.
- **CSDL chung, mục 4.13**: quy định collection hộp thư, các trường, quyền client chỉ sửa `isRead`, danh mục type và nguồn phát sinh; D phụ trách collection `notifications` và dịch vụ nền gửi thông báo.
