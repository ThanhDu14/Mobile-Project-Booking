# Feature Specification: Đăng ký làm chủ sân

**Feature Branch**: `110-owner-onboarding`

**Created**: 2026-10-07

**Status**: Draft

**Input**: Người chơi chọn “Tôi là chủ sân” để nộp hồ sơ cơ sở, theo dõi kết quả duyệt và nộp lại nếu bị từ chối.

## Actors

- **Người chơi/người nộp**: tài khoản đăng nhập hiện có; tài khoản vẫn mang `role = PLAYER` trong khi hồ sơ ở `pending` hoặc `rejected`.
- **Admin**: actor bên ngoài phạm vi màn hình của spec này, xem hồ sơ và giấy tờ, rồi duyệt hoặc từ chối theo điểm giao spec 120.
- **Hệ thống**: xác thực dữ liệu, lưu trạng thái, bảo vệ giấy tờ và áp dụng quyền.

## User Scenarios & Testing

### User Story 1 - Nộp hồ sơ chủ sân (Priority: P1)

Người chơi muốn đăng ký cơ sở để trở thành chủ sân. Họ nhập thông tin cơ sở và người đại diện, chọn vị trí trên bản đồ, tải ảnh cơ sở cùng giấy tờ chứng minh, xem lại rồi nộp. Trong lúc chờ duyệt, họ tiếp tục dùng ứng dụng với quyền người chơi.

**Why this priority**: Đây là lối vào cần thiết để người chơi gửi yêu cầu trở thành chủ sân, đồng thời giữ nguyên quyền hiện có cho tới khi được duyệt.

**Independent Test**: Đăng nhập bằng tài khoản người chơi chưa nộp; gửi bộ hồ sơ hợp lệ; xác nhận trạng thái là `pending`, người dùng vẫn có `role = PLAYER`, và không thể thực hiện thao tác quản lý sân.

**Acceptance Scenarios**:

1. **Given** tài khoản có trạng thái chưa nộp, **When** người chơi chọn “Tôi là chủ sân”, **Then** app mở biểu mẫu trống và giải thích quyền vẫn là người chơi cho tới khi admin duyệt.
2. **Given** biểu mẫu hợp lệ và đủ ảnh/giấy tờ bắt buộc, **When** người chơi nộp hồ sơ, **Then** hồ sơ được ghi nhận một lần, `ownerStatus` thành `pending`, `role` vẫn là `PLAYER`, và app hiện trạng thái chờ duyệt.
3. **Given** hồ sơ đã `pending`, **When** người dùng mở ứng dụng hoặc thử truy cập chức năng đăng/ quản lý sân, **Then** trạng thái chờ duyệt được hiển thị và thao tác quản lý bị từ chối.
4. **Given** biểu mẫu còn thiếu hoặc có trường không hợp lệ, **When** người chơi nộp, **Then** lỗi được chỉ rõ tại trường tương ứng và không tạo hồ sơ.
5. **Given** người chơi gửi yêu cầu nhưng mạng mất trước khi biết kết quả, **When** họ mở lại trạng thái hoặc thử lại, **Then** app xác định kết quả đã ghi hay chưa; không phát sinh hồ sơ trùng do bấm nhiều lần hoặc thử lại.

### User Story 2 - Theo dõi kết quả duyệt (Priority: P1)

Người nộp muốn xem trạng thái mới nhất trong ứng dụng để biết hồ sơ đang chờ, đã được duyệt hay bị từ chối và có thể hành động tiếp theo ra sao.

**Why this priority**: Người nộp cần hiểu trạng thái quyền của mình mà không phải suy đoán hoặc liên hệ ngoài ứng dụng.

**Independent Test**: Với từng trạng thái được máy chủ trả về, mở trang trạng thái và xác nhận nhãn, quyền và nội dung tương ứng khớp dữ liệu.

**Acceptance Scenarios**:

1. **Given** hồ sơ đang `pending`, **When** người nộp xem trạng thái, **Then** app hiển thị đang chờ duyệt, thời điểm nộp nếu có, và không cho quản lý sân.
2. **Given** hồ sơ được duyệt, **When** người nộp làm mới trạng thái, **Then** app hiển thị `approved`, tài khoản có `role = OWNER`, và chức năng quản lý sân được mở khóa theo các spec quản lý sân.
3. **Given** hồ sơ bị từ chối, **When** người nộp làm mới trạng thái, **Then** app hiển thị `rejected`, lý do từ chối và hành động sửa, nộp lại.
4. **Given** chưa có hồ sơ, **When** người dùng xem mục chủ sân, **Then** app hiển thị trạng thái chưa nộp tương ứng `ownerStatus = NONE` và lối bắt đầu đăng ký.

### User Story 3 - Sửa và nộp lại hồ sơ bị từ chối (Priority: P2)

Người nộp muốn xem lý do từ chối, sửa các nội dung chưa đạt và gửi lại để được xem xét.

**Why this priority**: Nộp lại giúp khắc phục thiếu sót mà không phải tạo tài khoản mới hay mất thông tin đã nhập.

**Independent Test**: Tạo hồ sơ bị từ chối có lý do; sửa thông tin; nộp lại; xác nhận hồ sơ được lưu với trạng thái chờ duyệt và không còn quyền quản lý sân.

**Acceptance Scenarios**:

1. **Given** hồ sơ `rejected`, **When** người nộp mở biểu mẫu sửa, **Then** app nạp dữ liệu đã gửi và hiển thị lý do từ chối.
2. **Given** người nộp sửa hồ sơ hợp lệ, **When** nộp lại, **Then** hồ sơ trở về `pending`, nội dung cập nhật được ghi nhận, và người dùng vẫn là `PLAYER` cho tới quyết định kế tiếp.
3. **Given** hồ sơ đã được nộp lại và đang `pending`, **When** người dùng thử sửa/nộp thêm, **Then** app không tạo hồ sơ thứ hai; áp dụng quy tắc sửa hồ sơ pending được chốt trong Clarifications.

### Edge Cases

- Vị trí bản đồ chưa được chọn hoặc tọa độ không hợp lệ; địa chỉ nhập không khớp vị trí ghim.
- Số sân bằng 0, âm, quá lớn hoặc không phải số nguyên; giờ mở cửa thiếu, sai định dạng, hoặc giờ kết thúc không sau giờ bắt đầu.
- Số điện thoại thiếu, sai định dạng hoặc người dùng nhập khoảng trắng/ký tự ngoài quy định.
- Ảnh cơ sở hoặc giấy tờ rỗng, tệp hỏng, định dạng không hỗ trợ, vượt giới hạn, tải lên một phần hoặc hết dung lượng lưu trữ.
- Người dùng bấm nộp liên tục, app bị đóng giữa chừng, hết phiên đăng nhập, hoặc gửi lại sau khi máy chủ đã nhận nhưng phản hồi bị mất.
- Admin đổi trạng thái trong lúc người nộp đang mở biểu mẫu; trạng thái mới phải được kiểm tra lại trước khi lưu/nộp.
- Hai thiết bị cùng sửa hồ sơ bị từ chối; app phải tránh ghi đè âm thầm dữ liệu mới hơn.
- Người dùng đã được duyệt không được coi là hồ sơ chưa nộp; việc sửa thông tin sau duyệt cần quy tắc riêng.
- Người khác đoán đường dẫn giấy tờ hoặc gọi trực tiếp dữ liệu/tệp phải không xem được giấy tờ.

## Trạng thái hồ sơ

| Giá trị dữ liệu `ownerStatus` | Cách hiển thị | Vai trò và hành động |
|---|---|---|
| `NONE` | Chưa nộp | `role = PLAYER`; có thể bắt đầu đăng ký |
| `PENDING` | Chờ duyệt (`pending`) | `role = PLAYER`; không được đăng hoặc quản lý sân |
| `APPROVED` | Đã duyệt (`approved`) | `role = OWNER`; được mở quyền quản lý sân theo phạm vi spec 111/112 |
| `REJECTED` | Bị từ chối (`rejected`) | `role = PLAYER`; hiển thị lý do và cho sửa/nộp lại |

Tên enum trong database giữ nguyên dạng IN HOA theo [đặc tả CSDL](../../docs/design/database/README.md); nhãn trạng thái thân thiện có thể hiển thị chữ thường như yêu cầu nghiệp vụ.

## Requirements

### Functional Requirements

- **FR-001**: Người dùng đã đăng nhập với `role = PLAYER` PHẢI có thể mở luồng “Tôi là chủ sân” từ khu vực tài khoản/hồ sơ.
- **FR-002**: Biểu mẫu PHẢI thu thập tên cơ sở, địa chỉ, vị trí ghim bản đồ (tọa độ), số sân, giờ mở cửa, tên người đại diện và số điện thoại người đại diện.
- **FR-003**: Biểu mẫu PHẢI cho người dùng đính kèm ảnh cơ sở và giấy tờ chứng minh trước khi nộp. Giấy tờ chứng minh chỉ nhận ảnh JPEG, PNG hoặc WebP, tối đa 2 MB mỗi tệp.
- **FR-004**: Trước khi gửi, app PHẢI cho người dùng xem lại thông tin và báo lỗi xác thực ngay tại trường liên quan.
- **FR-005**: Khi nộp thành công, hệ thống PHẢI ghi hồ sơ chủ sân và đặt `ownerStatus = PENDING`; `role` vẫn là `PLAYER` cho tới quyết định duyệt.
- **FR-006**: Lặp yêu cầu nộp do nhấn nhiều lần hoặc thử lại sau lỗi mạng PHẢI tạo tối đa một hồ sơ nộp cho cùng một lần nộp; kết quả không rõ phải được đối chiếu trước khi thử tạo mới.
- **FR-007**: Người nộp PHẢI xem được trạng thái hiện hành `NONE`, `PENDING`, `APPROVED` hoặc `REJECTED` ngay trong app; trạng thái phải phản ánh dữ liệu máy chủ khi tải mới.
- **FR-008**: Với `PENDING`, người dùng PHẢI tiếp tục sử dụng các chức năng người chơi và KHÔNG ĐƯỢC đăng hoặc quản lý sân.
- **FR-009**: Với `APPROVED`, trạng thái tài khoản PHẢI là `role = OWNER` và `ownerStatus = APPROVED`; chức năng quản lý sân được mở theo spec 111/112. Quyết định và thay đổi quyền thuộc luồng admin ngoài spec này.
- **FR-010**: Với `REJECTED`, app PHẢI hiển thị lý do bắt buộc do admin cung cấp, nạp lại dữ liệu đã nộp và cho phép người nộp sửa rồi nộp lại.
- **FR-011**: Nộp lại hợp lệ PHẢI chuyển hồ sơ sang `PENDING`, giữ `role = PLAYER` và không cấp quyền quản lý trước khi được duyệt.
- **FR-012**: Người nộp PHẢI xem được hồ sơ và giấy tờ của chính mình; chỉ admin có quyền xem giấy tờ của người nộp. Người dùng khác không được xem, kể cả khi biết đường dẫn tệp.
- **FR-013**: Mọi quyền đọc/ghi hồ sơ, chuyển trạng thái, đổi `role`/`ownerStatus`, tạo liên kết tệp riêng tư và mở chức năng quản lý PHẢI được kiểm tra ở phía server/Security Rules. Ẩn giao diện không được coi là kiểm soát quyền.
- **FR-014**: Người nộp KHÔNG ĐƯỢC tự sửa `role`, `ownerStatus`, trạng thái duyệt hoặc lý do từ chối bằng bất kỳ yêu cầu client nào.
- **FR-015**: App PHẢI có trạng thái tải, trạng thái chưa có hồ sơ, lỗi tải dữ liệu, lỗi xác thực, lỗi tải tệp và hành động thử lại phù hợp.
- **FR-016**: Khi tải tệp hoặc nộp hồ sơ đang xử lý, app PHẢI báo tiến trình/trạng thái, khóa thao tác gửi lặp và không làm mất dữ liệu biểu mẫu khi có lỗi có thể khôi phục.
- **FR-017**: Khi trạng thái hồ sơ thay đổi trong lúc biểu mẫu đang mở, app PHẢI làm mới trạng thái trước khi chấp nhận thao tác và thông báo nếu thao tác không còn hợp lệ.
- **FR-018**: Nếu người nộp đã được duyệt, luồng đăng ký mới không được tạo thêm hồ sơ. Chỉnh sửa vận hành cơ sở thuộc spec 111 và không yêu cầu duyệt lại; thay đổi người đại diện hoặc giấy tờ chứng minh PHẢI được admin duyệt lại trước khi có hiệu lực.
- **FR-019**: Khi `ownerStatus = PENDING`, người nộp KHÔNG ĐƯỢC sửa hoặc rút hồ sơ; app PHẢI giữ nội dung đã gửi cho tới khi có quyết định.

### Validation

- Tên cơ sở, địa chỉ, người đại diện và số điện thoại là bắt buộc; chỉ khoảng trắng được coi là trống.
- Tọa độ phải gồm vĩ độ và kinh độ hợp lệ trong phạm vi địa lý; vị trí ghim là bắt buộc.
- Số sân phải là số nguyên dương. Giới hạn trên chưa được tài liệu quy định và được đưa vào Open Questions.
- Giờ mở cửa phải có đủ giờ bắt đầu/kết thúc hợp lệ; quy tắc cho cơ sở mở qua nửa đêm hoặc nghỉ ngày nào chưa được quy định.
- Số điện thoại phải có định dạng số điện thoại hợp lệ; quy tắc chuẩn hóa và độ dài cụ thể cần chốt nếu không được kế thừa từ spec tài khoản.
- Ảnh cơ sở và ít nhất một giấy tờ chứng minh là bắt buộc trước khi nộp. Giấy tờ chứng minh phải là JPEG, PNG hoặc WebP, tối đa 2 MB mỗi tệp; ảnh cơ sở áp dụng giới hạn chung trong CSDL.
- Lỗi phải nêu trường cần sửa, giữ nguyên các giá trị hợp lệ khác và không tạo hồ sơ khi xác thực thất bại.

### Permission / Security

- Tài khoản mới tiếp tục khởi tạo với `role = PLAYER`; chỉ quyết định duyệt ở phía server mới chuyển thành `role = OWNER` và `ownerStatus = APPROVED`.
- Người nộp chỉ đọc hồ sơ của mình; tạo hồ sơ và sửa/nộp lại chỉ ở trạng thái `REJECTED`, theo bảng quyền trong database. Thao tác client không được ghi trường server-only.
- Giấy tờ chứng minh là dữ liệu nhạy cảm, lưu trong vùng riêng tư theo thiết kế dữ liệu; chỉ người nộp và admin được đọc qua cơ chế truy cập có thời hạn. Không tạo URL công khai hoặc đưa giấy tờ vào nội dung hiển thị cho người khác.
- Người dùng đang `PENDING` hoặc `REJECTED` không được cấp quyền sở hữu cơ sở, đăng sân, sửa sân hoặc thao tác quản trị chủ sân; mọi đường truy cập trực tiếp phải bị từ chối ở Rules/server.
- Quyền này phải dựa trên trạng thái và danh tính đã xác thực từ phía tin cậy; kiểm soát trên giao diện chỉ bổ trợ trải nghiệm.

### Error, Empty, Loading States

- **Loading**: khi tải hồ sơ/trạng thái hoặc gửi tệp, hiển thị trạng thái đang xử lý; vô hiệu hóa hành động gửi lặp cho tới khi có kết quả.
- **Empty**: khi `ownerStatus = NONE` và chưa có hồ sơ, giải thích điều kiện đăng ký, hiển thị nút bắt đầu và biểu mẫu trống.
- **Validation**: chỉ rõ lỗi theo trường, giữ nguyên nội dung đã nhập, không gửi hồ sơ.
- **File error**: thông báo tệp không đọc được/không hợp lệ/tải thất bại; cho chọn lại hoặc thử tải lại, không đánh dấu hồ sơ đã nộp.
- **Read/network error**: giữ trạng thái cuối đã biết kèm cảnh báo chưa thể làm mới; có hành động thử lại, không giả định đã được duyệt.
- **Submit error**: nêu không xác định được kết quả hay gửi thất bại; khi kết quả chưa rõ, truy vấn trạng thái trước khi cho tạo lần nộp mới để tránh trùng.
- **Permission error**: thông báo quyền không đủ và không tiết lộ dữ liệu hồ sơ/tệp ngoài phạm vi người dùng.

### Offline / Network Failure

- Biểu mẫu chưa gửi phải giữ nội dung trên thiết bị trong phiên chỉnh sửa để người dùng tiếp tục sau khi mạng trở lại; trạng thái lưu cục bộ không được trình bày như hồ sơ đã nộp.
- Không xác nhận `PENDING` cho tới khi máy chủ xác nhận việc ghi. Nếu phản hồi bị mất sau khi máy chủ có thể đã nhận, app làm mới theo tài khoản trước khi cho phép gửi mới.
- Lỗi mạng khi tải ảnh/giấy tờ phải cho thử lại tệp lỗi mà không yêu cầu nhập lại toàn bộ hồ sơ; tệp chưa tải xong không được tính là tệp đính kèm hợp lệ.
- Lỗi mạng khi đọc trạng thái hiển thị trạng thái lỗi/lần cập nhật cuối nếu có; không cấp quyền quản lý dựa trên dữ liệu lưu cũ.

### Resubmission

- Chỉ hồ sơ `REJECTED` được sửa/nộp lại theo bảng quyền database.
- Hồ sơ `PENDING` không được sửa hoặc rút; người nộp phải chờ quyết định admin. Nội dung đang được xem xét không thay đổi trong thời gian chờ.
- Lý do từ chối phải được giữ và hiển thị cho người nộp cho tới khi có quyết định mới; lần nộp lại phải gắn với hồ sơ/chuỗi xét duyệt hiện tại thay vì tạo nhiều hồ sơ cạnh tranh.
- Sau lần nộp lại, trạng thái trở về `PENDING`, người dùng vẫn là `PLAYER`, và quyền quản lý không được cấp cho tới khi được duyệt.
- Sau khi được duyệt, các chỉnh sửa vận hành cơ sở thuộc spec 111; thay đổi người đại diện hoặc giấy tờ chứng minh phải chờ admin duyệt lại trước khi có hiệu lực.

### Dependencies

- **Spec 010 — account auth**: cung cấp đăng nhập, danh tính tài khoản và vai trò mặc định người chơi; không thay thế hay mở rộng luồng đăng nhập.
- **Spec 011 — account profile**: cung cấp lối vào đăng ký và hiển thị trạng thái chủ sân ở tài khoản. Spec này cung cấp biểu mẫu, hồ sơ và hành vi nộp/nộp lại; hai spec cần thống nhất nguồn trạng thái (users hay ownerApplications), nhãn và hành vi điều hướng.
- **Spec 120 — admin**: quyết định duyệt/từ chối, bắt buộc có lý do khi từ chối, cập nhật `role`/`ownerStatus` và có thể phát thông báo. `specs/120-admin/spec.md` chưa tồn tại tại thời điểm viết; các điểm trên là giả định về giao diện tích hợp, không phải thiết kế màn hình hay quy trình admin trong spec này.
- **Spec 111/112 — quản lý sân**: chỉ mở các chức năng thuộc phạm vi đó sau khi đã được duyệt; đăng và quản lý sân không thuộc spec này.
- **Spec 100 — thông báo**: thông báo đẩy kết quả không thuộc phạm vi; trạng thái vẫn phải xem được trong app.
- **Database README**: nguồn dữ liệu `users/{uid}` và `ownerApplications/{id}`, quyền đọc/ghi, private bucket giấy tờ và quy tắc trạng thái.

## Open Questions / Assumptions

- **Assumption**: “chưa nộp” dùng `ownerStatus = NONE` như enum database hiện có; `pending`, `approved`, `rejected` là nhãn nghiệp vụ tương ứng các giá trị IN HOA.
- **Assumption**: mỗi người dùng chỉ có một hồ sơ đăng ký chủ sân đang hiệu lực; nộp lại cập nhật quy trình xét duyệt hồ sơ hiện có thay vì tạo nhiều hồ sơ đồng thời, phù hợp mô tả bảng quyền C/R/U khi `REJECTED`.
- **Assumption**: quyết định duyệt/từ chối là nguồn duy nhất thay đổi trạng thái và quyền; nội dung hồ sơ không tự làm đổi role.
- **Assumption**: khi được duyệt, việc tạo cơ sở từ `venueDraft` thuộc luồng review ngoài phạm vi UI admin; spec 111 quản lý cơ sở sau đó.
- **Open question**: giới hạn tối đa số sân và quy tắc giờ mở cửa qua ngày/chọn ngày nghỉ chưa được xác định trong schema/tài liệu liên quan.
- **Open question**: địa chỉ là văn bản nhập tay hay phải được xác nhận từ dịch vụ bản đồ; spec yêu cầu cả địa chỉ và vị trí ghim nhưng chưa quy định quan hệ giữa chúng.
- **Open question**: hành vi khi admin duyệt/từ chối đồng thời với sửa biểu mẫu cần quy tắc đồng bộ/hiển thị rõ ở giai đoạn clarify.

## Clarifications

### Session 2026-10-07

- Q: Bạn muốn chấp nhận loại tệp và giới hạn nào cho giấy tờ chứng minh? → A: Chỉ ảnh JPEG, PNG hoặc WebP, tối đa 2 MB mỗi tệp.
- Q: Khi hồ sơ đang `PENDING`, người nộp có được sửa hoặc rút hồ sơ trước khi admin ra quyết định không? → A: Không cho sửa hoặc rút khi đang chờ; đợi quyết định rồi mới nộp lại nếu bị từ chối.
- Q: Sau khi hồ sơ được `APPROVED`, loại thay đổi nào cần admin duyệt lại? → A: Chỉnh sửa vận hành theo spec 111 không cần duyệt lại; đổi người đại diện hoặc giấy tờ chứng minh cần admin duyệt lại.


## Success Criteria

### Measurable Outcomes

- **SC-001**: Ít nhất 90% người thử hoàn tất và gửi hồ sơ hợp lệ trong vòng 5 phút, không cần hướng dẫn trực tiếp.
- **SC-002**: 100% hồ sơ nộp thành công hiển thị trạng thái `PENDING` trong ứng dụng và vẫn giữ `role = PLAYER` cho tới khi có quyết định duyệt.
- **SC-003**: 100% trường hợp kiểm thử bị từ chối hiển thị đúng lý do và cho phép nộp lại hồ sơ đã sửa.
- **SC-004**: Trong mọi kịch bản kiểm thử quyền, không có người dùng chưa được duyệt nào thực hiện thành công thao tác đăng hoặc quản lý sân bằng giao diện hay yêu cầu trực tiếp.
- **SC-005**: Trong mọi kịch bản kiểm thử riêng tư, chỉ người nộp và admin xem được giấy tờ chứng minh; người dùng khác không thể đọc tệp kể cả khi biết đường dẫn.
- **SC-006**: 100% tình huống nhấn gửi lặp hoặc thử lại sau mất mạng tạo tối đa một hồ sơ cho cùng lần nộp.
- **SC-007**: Khi lỗi mạng hoặc tải tệp, người dùng nhận được hướng xử lý và có thể tiếp tục mà không nhập lại toàn bộ hồ sơ.
