# Feature Specification: Quản trị và kiểm duyệt

**Feature Branch**: `120-admin`
**Created**: 2026-10-08
**Status**: Draft
**Input**: Quản trị viên duyệt hồ sơ chủ sân, quản lý người dùng và xử lý báo cáo theo Proposal và các feature liên quan.

## Actors

- **Admin**: tài khoản được nhóm cấp thủ công, có quyền quản trị hiện hành. Chỉ Admin được truy cập và thực hiện thao tác trong phạm vi spec.
- **Người nộp hồ sơ**: người dùng đã đăng nhập, đang hoặc đã nộp `ownerApplications`; dùng spec 110 để nộp, theo dõi và nộp lại.
- **Người dùng bị báo cáo / chủ nội dung**: người dùng có nội dung hoặc tài khoản là đối tượng báo cáo. Không có quyền quản trị báo cáo.

## Phạm vi

Admin xem và quyết định hồ sơ đăng ký chủ sân; xem giấy tờ chứng minh; quản lý tài khoản; thu hồi quyền chủ sân và khóa venue vi phạm; xử lý `reports`; xem thống kê quản trị; quản lý danh mục và gửi thông báo hệ thống theo phạm vi nhóm 12 trong Proposal.

Không bao gồm luồng người dùng đăng ký/theo dõi hồ sơ (spec 110), quản lý venue/court/price rules của owner (spec 111), vận hành booking/drop-in/voucher của owner (spec 112), xác thực và hồ sơ cá nhân (spec 010/011). Không quy định thiết kế giao diện admin.

## Clarifications

### Session 2026-10-08

- Q: Khi Admin thu hồi quyền owner đã được duyệt, `users.ownerStatus` và hồ sơ `ownerApplications` nên chuyển sang trạng thái nào? → A: Chuyển `users.ownerStatus` về `NONE`, giữ `ownerApplications.status = APPROVED`.
- Q: Báo cáo “tin đăng” trong Proposal nên được hiểu là báo cáo về venue và dùng `targetType = VENUE`? → A: Có; dùng `targetType = VENUE`.
- Q: Khi xử lý báo cáo `targetType = VENUE`, Admin nên khóa venue bằng `LOCK_VENUE`? → A: Có; dùng `LOCK_VENUE` cho venue, còn `HIDE_CONTENT` dành cho nội dung như post, message hoặc review.
- Q: Khi Admin duyệt hồ sơ chủ sân, hệ thống có nên tạo venue từ `venueDraft` ngay lúc đó, còn spec 111 phụ trách chỉnh sửa venue sau này? → A: Có; tạo venue từ `venueDraft` khi duyệt, spec 111 quản lý các thay đổi sau đó.
- Q: Khi Admin gửi `SYSTEM` notification mà không chọn nhóm người nhận cụ thể, ai nên nhận thông báo? → A: Tất cả tài khoản đang hoạt động.
- Q: Khi Admin thu hồi quyền owner, các venue đang thuộc owner đó nên được xử lý thế nào? → A: Giữ nguyên trạng thái venue; Admin khóa riêng venue nếu cần bằng `LOCK_VENUE`.
- Q: Admin có được mở khóa venue đang ở trạng thái `LOCKED` không? → A: Có; chỉ Admin được mở khóa và đặt venue về `ACTIVE` hoặc `HIDDEN` theo trạng thái phù hợp.

## User Scenarios & Testing

### User Story 1 - Duyệt hồ sơ chủ sân (Priority: P1)

Là Admin, tôi muốn xem hồ sơ chờ duyệt cùng giấy tờ chứng minh và duyệt hoặc từ chối có lý do, để chỉ cấp quyền cho hồ sơ được xác minh và người bị từ chối có thể sửa, nộp lại.

**Why this priority**: Đây là quyền quản trị cốt lõi, mở quyền owner và ảnh hưởng trực tiếp tới dữ liệu nhạy cảm.

**Independent Test**: Với một hồ sơ `PENDING`, Admin có thể đọc hồ sơ và giấy tờ, đưa ra một quyết định hợp lệ, và xác nhận trạng thái/quyền tương ứng.

**Acceptance Scenarios**:

1. **Given** Admin đã đăng nhập và có hồ sơ `PENDING`, **When** Admin mở hàng đợi và chọn hồ sơ, **Then** hệ thống hiển thị thông tin hồ sơ, trạng thái và giấy tờ mà chỉ người nộp hoặc Admin được xem.
2. **Given** hồ sơ `PENDING` hợp lệ, **When** Admin duyệt, **Then** hồ sơ thành `APPROVED`, `users.role` thành `OWNER`, `users.ownerStatus` thành `APPROVED`, venue được tạo từ `venueDraft`, quyền owner được cập nhật ở phía máy chủ và người nộp được thông báo.
3. **Given** hồ sơ `PENDING`, **When** Admin từ chối với lý do không rỗng, **Then** hồ sơ thành `REJECTED`, `rejectReason` được lưu, `users.role` là `PLAYER`, `users.ownerStatus` là `REJECTED`, và người nộp nhận thông báo cùng lý do.
4. **Given** hồ sơ đã `REJECTED`, **When** người nộp xem hồ sơ theo spec 110, **Then** họ thấy lý do đã lưu và có thể sửa, nộp lại; trạng thái trở về `PENDING`, `role` vẫn là `PLAYER` và lý do cũ không còn được trình bày như quyết định hiện hành.
5. **Given** hồ sơ đã được quyết định hoặc thay đổi bởi một yêu cầu đồng thời, **When** Admin gửi quyết định lần nữa dựa trên trạng thái cũ, **Then** hệ thống từ chối quyết định lỗi thời và giữ dữ liệu nhất quán.

### User Story 2 - Xử lý báo cáo và vi phạm (Priority: P1)

Là Admin, tôi muốn xem báo cáo đang mở và nội dung liên quan, rồi đóng báo cáo bằng hành động phù hợp, để xử lý vi phạm trong phạm vi được phân quyền.

**Why this priority**: Proposal yêu cầu kiểm duyệt nội dung do người dùng tạo và quản lý tài khoản/cơ sở vi phạm.

**Independent Test**: Với báo cáo `OPEN`, Admin đọc được đối tượng được phép, chọn hành động thuộc danh sách hiện có và hoàn tất xử lý; báo cáo có trạng thái, người xử lý và thời điểm xử lý tương ứng.

**Acceptance Scenarios**:

1. **Given** có báo cáo `OPEN`, **When** Admin xem danh sách và mở báo cáo, **Then** hệ thống trình bày loại đối tượng, lý do, chi tiết và đối tượng liên quan trong giới hạn quyền Admin.
2. **Given** báo cáo cần xử lý và có căn cứ, **When** Admin áp dụng action tương thích (`HIDE_CONTENT` cho nội dung, `LOCK_USER` cho user, `LOCK_VENUE` cho venue), **Then** hành động chỉ tác động đúng đối tượng tương ứng, báo cáo chuyển `RESOLVED` và lưu `handledBy`, `handledAt`.
3. **Given** báo cáo không xác đáng hoặc không cần hành động, **When** Admin chọn bỏ qua, **Then** báo cáo chuyển `DISMISSED`, action là `NONE`, và lưu người/thời điểm xử lý.
4. **Given** báo cáo đã đóng hoặc đối tượng không còn tồn tại, **When** Admin thao tác từ bản dữ liệu cũ, **Then** hệ thống không thực hiện hành động lặp hoặc sai đối tượng và hiển thị trạng thái hiện tại.

### User Story 3 - Quản lý tài khoản và venue vi phạm (Priority: P2)

Là Admin, tôi muốn tra cứu người dùng và khóa/mở khóa tài khoản, hoặc khóa venue vi phạm, để bảo vệ hệ thống và thực thi kết quả kiểm duyệt.

**Why this priority**: Khóa tài khoản/venue là biện pháp thực thi được nêu trong Proposal và database.

**Independent Test**: Admin khóa/mở khóa tài khoản và venue rồi xác nhận trạng thái được áp dụng, người dùng thường và owner không thể tự thay đổi.

**Acceptance Scenarios**:

1. **Given** Admin đã xác thực quyền, **When** tìm một tài khoản và khóa tài khoản, **Then** `accountStatus` thành `LOCKED` và spec 010 chặn đăng nhập/phiên kế tiếp.
2. **Given** tài khoản đang `LOCKED`, **When** Admin mở khóa, **Then** tài khoản trở về trạng thái hoạt động (`ACTIVE`) và có thể đăng nhập theo spec 010.
3. **Given** venue vi phạm, **When** Admin khóa venue, **Then** `venues.status` thành `LOCKED` và venue không khả dụng theo các spec phụ thuộc.
4. **Given** venue đang `LOCKED`, **When** Admin mở khóa, **Then** venue trở về `ACTIVE` hoặc `HIDDEN` theo trạng thái Admin chọn; owner không thể tự mở khóa.
5. **Given** Admin thu hồi quyền chủ sân, **When** việc thu hồi được chấp thuận, **Then** quyền quản lý của người đó bị gỡ, `users.ownerStatus` chuyển về `NONE`, `users.role` chuyển về `PLAYER`, hồ sơ đã duyệt giữ `ownerApplications.status = APPROVED`, còn trạng thái các venue không tự thay đổi.

### User Story 4 - Theo dõi tình hình và công cụ quản trị (Priority: P3)

Là Admin, tôi muốn xem số liệu toàn hệ thống, quản lý danh mục và phát thông báo hệ thống, để duy trì hoạt động chung theo phạm vi Proposal.

**Why this priority**: Proposal và hướng dẫn phân công đưa thống kê, danh mục và thông báo hệ thống vào nhóm 12, nhưng schema/chính sách chi tiết chưa hoàn chỉnh.

**Independent Test**: Admin đọc được số liệu có sẵn, thực hiện một thay đổi danh mục hoặc gửi thông báo hệ thống hợp lệ; dữ liệu không bị lộ cho tài khoản không phải Admin.

**Acceptance Scenarios**:

1. **Given** có dữ liệu `systemStats`, **When** Admin mở thống kê, **Then** hệ thống chỉ hiển thị số liệu có nguồn và thời điểm rõ ràng; không có dữ liệu thì hiển thị trạng thái rỗng.
2. **Given** danh mục được hệ thống sử dụng, **When** Admin thêm hoặc cập nhật một mục hợp lệ, **Then** nội dung danh mục được lưu và có thể được các feature liên quan sử dụng.
3. **Given** nội dung thông báo hệ thống hợp lệ, **When** Admin gửi thông báo, **Then** thông báo được phát tới tất cả tài khoản đang hoạt động và có thể xem trong hộp thư notification.

## Trạng thái xử lý

- Hồ sơ: `PENDING` → `APPROVED` hoặc `REJECTED`. Nộp lại từ `REJECTED` trở về `PENDING`. Hồ sơ `APPROVED` không nhận quyết định duyệt/từ chối lần nữa qua hàng đợi này.
- Tài khoản: `ACTIVE` ↔ `LOCKED` theo database README.
- Báo cáo: `OPEN` → `RESOLVED` hoặc `DISMISSED`; action trong tập `NONE`, `HIDE_CONTENT`, `LOCK_USER`, `LOCK_VENUE`.
- Venue: Admin có thể chuyển sang `LOCKED` và sau đó mở khóa về `ACTIVE` hoặc `HIDDEN`; owner không thể tự mở khóa.
- Thu hồi owner: `users.ownerStatus` chuyển về `NONE`, `users.role` về `PLAYER`; hồ sơ đã duyệt giữ `ownerApplications.status = APPROVED`.

## Requirements

### Functional Requirements

- **FR-001**: Mọi chức năng trong spec này chỉ truy cập được khi tài khoản đã đăng nhập và quyền Admin được xác nhận ở phía máy chủ; ẩn giao diện không được tính là kiểm soát quyền.
- **FR-002**: Admin có thể xem danh sách `ownerApplications` theo trạng thái và thời điểm nộp, ưu tiên hồ sơ `PENDING`, và mở chi tiết hồ sơ.
- **FR-003**: Admin chỉ được xem giấy tờ trong `owner-docs` cho hồ sơ cần xét; quyền xem giới hạn Admin và chính người nộp. Người khác không thể đọc tệp kể cả khi biết path.
- **FR-004**: Admin chỉ được duyệt/từ chối hồ sơ `PENDING`. Quyết định duyệt cập nhật hồ sơ, `users.role = OWNER`, `users.ownerStatus = APPROVED` và quyền máy chủ như một kết quả nhất quán; venue được tạo từ `venueDraft` theo database README.
- **FR-005**: Quyết định từ chối bắt buộc có `rejectReason` không rỗng; cập nhật hồ sơ `REJECTED`, `users.role = PLAYER`, `users.ownerStatus = REJECTED`; lý do được lưu để người nộp xem, sửa và nộp lại theo spec 110.
- **FR-006**: Nộp lại hồ sơ được phép sau `REJECTED`, đặt hồ sơ về `PENDING`, giữ `role = PLAYER`; quyền owner chỉ được cấp sau lần duyệt hợp lệ.
- **FR-007**: Việc duyệt/từ chối và nộp lại phải xử lý xung đột đồng thời an toàn; không cho quyết định cũ ghi đè trạng thái mới.
- **FR-008**: Admin có thể tra cứu người dùng và đổi `accountStatus` giữa `ACTIVE`/`LOCKED`; trạng thái khóa có hiệu lực cho xác thực theo spec 010.
- **FR-009**: Admin có thể thu hồi quyền owner và khóa venue vi phạm; thu hồi đặt `users.role = PLAYER`, `users.ownerStatus = NONE`, giữ hồ sơ đã duyệt là `APPROVED` và không tự thay đổi trạng thái venue; Admin có thể khóa riêng venue bằng `LOCK_VENUE`. Venue `LOCKED` chỉ Admin mới được mở khóa về `ACTIVE` hoặc `HIDDEN`.
- **FR-010**: Admin có thể xem báo cáo `reports` và thông tin đối tượng theo `targetType`/`targetPath`, trong phạm vi quyền Admin.
- **FR-011**: Khi xử lý báo cáo, Admin chỉ chọn các action đã định nghĩa; hệ thống cập nhật `status`, `action`, `handledBy`, `handledAt` nhất quán với kết quả. Bỏ qua báo cáo dùng `DISMISSED` và `NONE`.
- **FR-012**: Hành động `HIDE_CONTENT`, `LOCK_USER`, `LOCK_VENUE` chỉ áp dụng cho đối tượng tương ứng nếu tồn tại và còn đủ điều kiện; không biến `RESOLVED` thành bằng chứng rằng nội dung đã bị xóa vĩnh viễn.
- **FR-013**: Admin có thể đọc `systemStats` và `venues/{venueId}/dailyStats` theo quyền quản trị. Chỉ trình bày dữ liệu tồn tại, không suy diễn chỉ số chưa được định nghĩa.
- **FR-014**: Admin có thể quản lý danh mục và gửi `SYSTEM` notification tới tất cả tài khoản đang hoạt động; nội dung tuân theo dữ liệu notification hiện có, không tạo trường/trạng thái mới trong spec này.
- **FR-015**: Hệ thống gửi thông báo `OWNER_APPROVED` hoặc `OWNER_REJECTED` phù hợp, trong đó thông báo từ chối cho người nộp biết lý do và cách tiếp tục theo spec 110.
- **FR-016**: Hệ thống không cho Admin thao tác nếu quyền admin đã bị thu hồi, phiên hết hạn hoặc tài khoản không còn hợp lệ; mọi yêu cầu trực tiếp không có quyền đều bị từ chối.

### Validation

- Quyết định chỉ hợp lệ khi hồ sơ đang `PENDING` và gắn với người nộp có tồn tại.
- Từ chối yêu cầu lý do có nội dung sau khi bỏ khoảng trắng đầu/cuối; không cho lưu lý do rỗng.
- Hồ sơ đã `APPROVED`/`REJECTED`, báo cáo đã đóng hoặc đối tượng đã đổi trạng thái không được xử lý lại từ snapshot cũ.
- Mọi `targetType` và action phải thuộc tập giá trị trong database README; `VENUE` chỉ dùng `LOCK_VENUE`, `USER` chỉ dùng `LOCK_USER`, còn `HIDE_CONTENT` chỉ dùng cho `POST`, `MESSAGE`, `REVIEW`, `DROP_IN` khi đối tượng hỗ trợ ẩn.
- Việc khóa/mở khóa tài khoản chỉ nhận trạng thái `ACTIVE`/`LOCKED`; việc mở khóa venue chỉ do Admin thực hiện và chỉ đặt về `ACTIVE` hoặc `HIDDEN`.
- Dữ liệu tệp giấy tờ không hiển thị nếu thiếu quyền hoặc signed access đã hết hạn.

### Permission / Security

- Chỉ Admin được đọc hàng đợi hồ sơ, giấy tờ của người khác, danh sách báo cáo quản trị, số liệu quản trị và thực hiện quyết định quản trị.
- Quyền phải kiểm tra ở server cho từng lần đọc/ghi; không dựa vào role do client gửi hoặc chỉ ẩn nút.
- `role`, `ownerStatus`, trạng thái hồ sơ, lý do từ chối và metadata xét duyệt là dữ liệu được bảo vệ; người dùng không thể tự ghi.
- Giấy tờ là dữ liệu nhạy cảm; chỉ Admin và người nộp đúng hồ sơ được đọc. Không dùng URL công khai; quyền truy cập tệp có thời hạn theo database README.
- Mọi hành động phải gắn với đúng Admin đang xác thực và đúng đối tượng; người dùng thường không được truy cập chức năng kể cả qua đường dẫn trực tiếp.
- Admin được cấp thủ công; không có chức năng đăng ký hoặc tự nâng quyền Admin.

### Error / Empty / Loading States

- **Loading**: hàng đợi, chi tiết, báo cáo và thống kê thể hiện đang tải; khóa thao tác quyết định lặp cho đến khi có kết quả.
- **Empty**: thông báo riêng khi không có hồ sơ `PENDING`, không có báo cáo cần xử lý hoặc chưa có dữ liệu thống kê.
- **Error**: nêu việc tải/lưu thất bại bằng ngôn ngữ dễ hiểu và cho phép thử lại; không xóa nội dung lý do đang nhập.
- **Permission denied**: thông báo không đủ quyền, kết thúc truy cập và không tiết lộ chi tiết hồ sơ/giấy tờ.
- **Conflict**: thông báo trạng thái đã thay đổi, tải trạng thái mới nhất và không áp dụng một phần quyết định.
- **File unavailable**: thông báo giấy tờ không thể mở hoặc quyền truy cập hết hạn; cho Admin thử tải lại khi còn quyền.

### Offline / Network Failure

- Không cho xác nhận duyệt/từ chối, khóa tài khoản/venue hay xử lý báo cáo khi chưa nhận được xác nhận từ máy chủ.
- Nếu mất mạng khi xem dữ liệu, dữ liệu cache nếu có phải được đánh dấu cũ và không được dùng làm cơ sở cho quyết định; giấy tờ không khả dụng ngoại tuyến.
- Khi gửi quyết định bị timeout, Admin tải lại trạng thái trước khi thử lại; hệ thống không tạo tác động lặp hoặc thông báo trùng.
- Dữ liệu biểu mẫu từ chối đang nhập được giữ lại để thử lại trong phiên hiện tại.

### Edge Cases

- Hai Admin cùng mở một hồ sơ/báo cáo và quyết định gần như đồng thời: chỉ một quyết định dựa trên trạng thái `PENDING`/`OPEN` được chấp nhận.
- Người nộp sửa/nộp lại ngay khi Admin đang xét: quyết định cũ không áp dụng lên phiên hồ sơ mới.
- Hồ sơ thiếu giấy tờ, mất file, path không hợp lệ hoặc signed access hết hạn: không duyệt dựa trên tài liệu không xem được; báo lỗi có thể phục hồi.
- Admin bị mất quyền trong lúc màn hình đang mở: mọi thao tác tiếp theo bị từ chối ở phía máy chủ.
- Báo cáo trỏ đến nội dung đã xóa, tài khoản đã khóa hoặc venue đã `LOCKED`: Admin vẫn có thể kết thúc báo cáo phù hợp mà không lặp tác động phụ.
- Đối tượng báo cáo và action không tương thích: từ chối thao tác và giữ nguyên báo cáo. Báo cáo `VENUE` không được xử lý bằng `HIDE_CONTENT`.
- Nhiều báo cáo cùng trỏ một đối tượng: xử lý một báo cáo không được làm mất nội dung các báo cáo còn lại.
- Không có dữ liệu thống kê/danh mục: hiển thị trạng thái rỗng, không báo thành công giả.

## Dependencies

- **Spec 010**: đăng nhập admin, `accountStatus` và hiệu lực khóa tài khoản.
- **Spec 011**: hồ sơ tài khoản và hiển thị thay đổi vai trò/chuyển chế độ.
- **Spec 110**: người nộp xem trạng thái/lý do, sửa và nộp lại hồ sơ; nguồn hồ sơ và trạng thái owner.
- **Spec 111**: venue/court/price rules và trạng thái `LOCKED` do Admin đặt.
- **Spec 112**: quyền owner đã duyệt; mất quyền/venue bị khóa phải chặn thao tác vận hành.
- **Spec 070, 090, 091**: nội dung review, post, message có thể được báo cáo và kiểm duyệt.
- **Spec 100**: hộp thư và thông báo `OWNER_APPROVED`, `OWNER_REJECTED`, `SYSTEM`.
- **Database README**: `users`, `ownerApplications`, `venues`, `reports`, `notifications`, `dailyStats`, `systemStats`, `districts`, `amenities`, quyền truy cập và tập trạng thái hiện hành.
- **Proposal.html**: chức năng nhóm 12 gồm duyệt hồ sơ, thu hồi quyền owner, khóa venue, quản lý tài khoản, xử lý báo cáo, danh mục, thống kê và thông báo hệ thống.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% ca kiểm thử quyết định hồ sơ hợp lệ tạo ra đồng nhất trạng thái application, `users.role` và `users.ownerStatus` như quy định.
- **SC-002**: 100% ca từ chối yêu cầu lý do không rỗng; 100% người nộp có thể xem đúng lý do hiện hành và nộp lại sau khi sửa.
- **SC-003**: 100% kịch bản kiểm tra riêng tư chỉ cho Admin và đúng người nộp xem giấy tờ; mọi vai trò khác bị từ chối.
- **SC-004**: 100% yêu cầu quản trị không có quyền Admin bị từ chối dù gọi trực tiếp chức năng.
- **SC-005**: 100% báo cáo được kết thúc có trạng thái cuối, action, người xử lý và thời điểm khớp với kết quả đã chọn.
- **SC-006**: Admin có thể hoàn tất duyệt hoặc từ chối một hồ sơ đầy đủ trong tối đa 2 phút khi dữ liệu và kết nối sẵn sàng.

## Open Questions / Assumptions

- Đã chốt: khi thu hồi quyền owner đã duyệt, `users.role = PLAYER`, `users.ownerStatus = NONE`; `ownerApplications.status` giữ `APPROVED` để lưu lịch sử hồ sơ được duyệt, còn venue không tự đổi trạng thái.
- Proposal yêu cầu quản lý danh mục, thống kê và thông báo hệ thống nhưng chưa quy định thao tác quản lý danh mục, nội dung thông báo hay chỉ số thống kê. Người nhận `SYSTEM` notification đã chốt là tất cả tài khoản đang hoạt động.
- Đã chốt: Admin có thể mở khóa venue `LOCKED` về `ACTIVE` hoặc `HIDDEN`; owner không có quyền này.
- Đã chốt: “tin đăng” trong báo cáo được hiểu là venue và dùng `reports.targetType = VENUE`, không thêm `targetType` mới.
- Đã chốt: duyệt hồ sơ tạo venue từ `venueDraft`; mọi chỉnh sửa venue/court/price rules sau đó thuộc spec 111.
- Các từ `pending`, `approved`, `rejected` trong thông báo là nhãn dễ đọc; dữ liệu vẫn dùng enum viết hoa theo database.

## Mâu thuẫn đối chiếu

- `docs/design/database/README.md` mục 4.2 quy định duyệt application tạo `venues` từ `venueDraft`; spec 110 mục Assumptions nói tạo venue từ draft thuộc luồng review ngoài phạm vi UI admin, trong khi spec 111 bao gồm tạo venue sau duyệt. Đã chốt: tạo venue từ `venueDraft` là tác động của quyết định duyệt; spec 111 quản lý các thay đổi tiếp theo.
- Proposal mô tả `role = owner/player` dạng chữ thường, trong khi database README và các spec 010/110/111/112 dùng enum `OWNER`/`PLAYER`; spec dùng enum database.
- Proposal nhắc Cloud Functions trong đoạn bảo mật nhưng constitution hiện hành yêu cầu Supabase Edge Functions cho thao tác nhạy cảm. Spec chỉ nêu quyền kiểm tra phía máy chủ, không chọn cách triển khai.















