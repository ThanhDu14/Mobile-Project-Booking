# Feature Specification: Hồ sơ và quản lý tài khoản

**Feature Branch**: `feature/01-account-specs`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Người dùng đã đăng nhập quản lý tài khoản của mình. Xem và sửa hồ sơ: ảnh đại diện (bucket avatars của Supabase), tên, trình độ, khu vực hay chơi. Đổi mật khẩu, đăng xuất, xóa tài khoản và dữ liệu cá nhân (Edge Function delete-account, theo Nghị định 13/2023). Chủ sân đã được duyệt (appRole=OWNER) chuyển qua lại giữa chế độ "Người chơi" và "Quản lý sân". Người đã nộp hồ sơ chủ sân xem trạng thái: chờ duyệt, đã duyệt, bị từ chối kèm lý do, và nút nộp lại. Không bao gồm: màn hình nộp hồ sơ chủ sân (spec 110), màn hình quản lý sân (spec 111). Dữ liệu: users, ownerApplications theo docs/design/database/README.md."

**Nhóm tính năng**: [01 — Tài khoản](../../docs/features/01-account/README.md) · **Phụ trách**: A · **Liên quan**: [spec 010](../010-account-auth/spec.md) (đăng ký, đăng nhập, điều hướng theo vai trò)

## Clarifications

### Session 2026-10-05

- Q: Có cho xóa tài khoản ngay khi người dùng còn đơn đặt sân sắp tới hoặc là chủ sân đang có cơ sở hoạt động không? → A: Cho xóa (ưu tiên quyền của người dùng). Hệ thống tự hủy đơn sắp tới của người dùng theo chính sách hủy; nếu là chủ sân thì tự ẩn cơ sở, hủy các đơn và buổi vãng lai sắp tới của khách ở cơ sở đó (hoàn tiền giả lập toàn bộ cho khách) và thông báo cho khách.

### Session 2026-10-13

- Q: Số dư ví giả lập (spec 050) hiển thị và nạp ở đâu trong trang tài khoản? → A: Trang "Tài khoản" hiện số dư `walletBalance` kèm nhãn giả lập và nút "Nạp ví giả lập" (dùng chức năng nạp của spec 050); số dư bị xóa cùng tài khoản.
- Q: Trang tài khoản hiển thị gì khi admin thu hồi quyền chủ sân (spec 120)? → A: Hiện "Quyền chủ sân đã bị thu hồi" kèm lý do của admin và cho nộp hồ sơ mới; dữ liệu là `ownerStatus = NONE`, hồ sơ cũ giữ `APPROVED`.

### Session 2026-10-15 (sau `/speckit-plan`, theo research R16)

- Q: App có báo được "email đã được sử dụng" khi đổi email không? → A: Không. Email enumeration protection đang bật nên Firebase không báo lỗi; app luôn hiện cùng một thông báo (US7-2).
- Q: Ảnh đại diện công khai tới mức nào? → A: Bucket `avatars` public, ai có đường dẫn cũng xem được; đường dẫn chứa `uid` và mốc thời gian nên khó đoán (FR-004).
- Q: "Toàn bộ hoặc không" của FR-021 áp dụng tới đâu? → A: Tới bước chốt: mọi thay đổi ảnh hưởng người khác nằm trong một lần ghi. Phần xóa dữ liệu cá nhân sau đó do hệ thống tự hoàn tất và thử lại.
- Q: Tiền hoàn cho đơn của chính người xóa đi đâu? → A: Vào ví giả lập rồi bị xóa cùng ví; màn hình xác nhận ghi rõ (FR-022).
- Q: Khi nào phải xác nhận lại danh tính? → A: Luôn luôn, cho cả 3 thao tác đổi mật khẩu, đổi email, xóa tài khoản (FR-017).
- Q: Admin có tự xóa tài khoản được không? → A: Không. Admin không có nút "Xóa tài khoản"; tài khoản admin do nhóm xóa thủ công bằng script.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem và sửa hồ sơ cá nhân (Priority: P1)

Người dùng đã đăng nhập mở trang "Tài khoản", xem ảnh đại diện, họ tên, email/số điện thoại, trình độ chơi và khu vực hay chơi. Người dùng sửa họ tên, chọn trình độ, chọn một hoặc nhiều khu vực, đổi ảnh đại diện (chụp mới hoặc chọn từ thư viện) rồi lưu.

**Why this priority**: Trang hồ sơ là nơi chứa mọi chức năng còn lại của spec này (đăng xuất, đăng ký chủ sân, chuyển chế độ). Trình độ và khu vực còn được các tính năng khác dùng để gợi ý sân và buổi vãng lai.

**Independent Test**: Đăng nhập, sửa họ tên, trình độ, khu vực và ảnh đại diện, lưu; thoát hẳn app, mở lại và thấy thông tin mới vẫn còn.

**Acceptance Scenarios**:

1. **Given** người dùng đã đăng nhập, **When** mở trang "Tài khoản", **Then** thấy ảnh đại diện (hoặc ảnh mặc định theo chữ cái đầu tên), họ tên, email hoặc số điện thoại, trình độ, khu vực hay chơi, trạng thái xác minh email (nếu đăng ký bằng email).
2. **Given** người dùng ở màn hình sửa hồ sơ, **When** sửa họ tên hợp lệ, chọn trình độ "Trung bình", chọn 2 khu vực và bấm "Lưu", **Then** thông tin mới được lưu, hiển thị ngay trên trang "Tài khoản" và có thông báo "Đã lưu".
3. **Given** người dùng chọn ảnh đại diện mới từ thư viện hoặc chụp bằng camera, **When** cắt ảnh vuông và xác nhận, **Then** ảnh được tải lên, thay ảnh cũ, và hiển thị ở mọi nơi có ảnh đại diện của người đó.
4. **Given** người dùng từ chối quyền camera hoặc quyền truy cập ảnh, **When** bấm đổi ảnh đại diện, **Then** app giải thích vì sao cần quyền, vẫn cho chọn cách còn lại, và các chức năng khác của trang vẫn dùng được.
5. **Given** người dùng đã sửa nhưng chưa lưu, **When** bấm quay lại, **Then** app hỏi "Bỏ các thay đổi chưa lưu?" trước khi thoát.

---

### User Story 2 - Đăng xuất (Priority: P1)

Người dùng bấm "Đăng xuất" ở trang "Tài khoản", xác nhận, và được đưa về Trang chủ ở chế độ khách (spec 010 FR-025).

**Why this priority**: Bắt buộc để dùng chung máy hoặc đổi tài khoản, và là điều kiện để kiểm thử mọi luồng đăng nhập của spec 010.

**Independent Test**: Đăng nhập, đăng xuất, mở lại app thì vào Trang chủ ở chế độ khách (tab Tài khoản hiện lời mời đăng nhập); không còn thông báo đẩy của tài khoản cũ trên máy đó.

**Acceptance Scenarios**:

1. **Given** người dùng đã đăng nhập, **When** bấm "Đăng xuất" và xác nhận, **Then** người dùng về màn hình đăng nhập và không thể quay lại các màn hình trước bằng nút quay lại.
2. **Given** người dùng vừa đăng xuất, **When** mở lại app, **Then** app hiển thị màn hình đăng nhập.
3. **Given** người dùng đã đăng xuất trên một máy, **When** có thông báo mới cho tài khoản đó, **Then** máy đó không nhận thông báo đẩy nữa.

---

### User Story 3 - Đăng ký làm chủ sân và theo dõi trạng thái duyệt (Priority: P1)

Người chơi muốn đưa sân của mình lên SmashNow bấm "Đăng ký làm chủ sân" trong trang "Tài khoản" để mở màn hình nộp hồ sơ (spec 110). Sau khi nộp, trang "Tài khoản" hiển thị trạng thái hồ sơ: chờ duyệt, đã duyệt, hoặc bị từ chối kèm lý do và nút nộp lại.

**Why this priority**: Theo quyết định ở spec 010, đây là **lối vào duy nhất** để trở thành chủ sân. Không có nó thì không ai dùng được các tính năng chủ sân (nhóm 11).

**Independent Test**: Với 5 tài khoản ở 5 trạng thái (chưa nộp, chờ duyệt, đã duyệt, bị từ chối, bị thu hồi quyền), mỗi tài khoản thấy đúng nội dung và đúng nút tương ứng ở trang "Tài khoản".

**Acceptance Scenarios**:

1. **Given** người chơi chưa từng nộp hồ sơ, **When** mở trang "Tài khoản", **Then** thấy mục "Đăng ký làm chủ sân" kèm mô tả ngắn; bấm vào thì mở màn hình nộp hồ sơ chủ sân.
2. **Given** hồ sơ đang chờ duyệt, **When** mở trang "Tài khoản", **Then** thấy trạng thái "Đang chờ duyệt" và ngày nộp, không có nút nộp hồ sơ mới; người dùng vẫn dùng app như người chơi.
3. **Given** hồ sơ bị từ chối, **When** mở trang "Tài khoản", **Then** thấy trạng thái "Bị từ chối", lý do admin ghi, và nút "Sửa và nộp lại" mở màn hình nộp hồ sơ với thông tin cũ đã điền sẵn.
4. **Given** hồ sơ đã được duyệt, **When** mở trang "Tài khoản", **Then** thấy trạng thái "Đã là chủ sân" và mục chuyển chế độ (User Story 4), không còn mục "Đăng ký làm chủ sân".
5. **Given** admin vừa đổi trạng thái hồ sơ trong lúc người dùng đang mở app, **When** người dùng quay lại trang "Tài khoản", **Then** trạng thái mới được hiển thị mà không cần đăng xuất.

---

### User Story 4 - Chủ sân chuyển giữa chế độ "Người chơi" và "Quản lý sân" (Priority: P2)

Chủ sân đã được duyệt có một tài khoản nhưng hai giao diện. Khi đăng nhập, chủ sân vào giao diện "Quản lý sân" (theo spec 010). Từ trang tài khoản, chủ sân bấm "Chuyển sang chế độ người chơi" để tự đặt sân như người chơi, và bấm "Chuyển sang quản lý sân" để quay lại.

**Why this priority**: Chủ sân cũng là người chơi, nhưng chỉ cần khi đã có chủ sân được duyệt, nên xếp sau User Story 3.

**Independent Test**: Với tài khoản chủ sân đã duyệt, chuyển qua lại hai chế độ nhiều lần, mỗi lần thấy đúng giao diện; tài khoản người chơi thường không thấy nút chuyển.

**Acceptance Scenarios**:

1. **Given** chủ sân đã duyệt đang ở giao diện quản lý sân, **When** bấm "Chuyển sang chế độ người chơi" ở trang tài khoản, **Then** app chuyển sang giao diện người chơi trong vòng 1 giây, không phải đăng nhập lại.
2. **Given** chủ sân đang ở giao diện người chơi, **When** bấm "Chuyển sang quản lý sân", **Then** app quay về giao diện quản lý sân.
3. **Given** tài khoản là người chơi (kể cả đang chờ duyệt hoặc bị từ chối), **When** mở trang tài khoản, **Then** không có nút chuyển chế độ.
4. **Given** quyền chủ sân vừa bị admin thu hồi, **When** người dùng mở app hoặc bấm chuyển sang quản lý sân, **Then** app không cho vào giao diện quản lý sân, đưa về giao diện người chơi và thông báo quyền chủ sân đã bị thu hồi.

---

### User Story 5 - Đổi mật khẩu (Priority: P2)

Người dùng đăng nhập bằng email/mật khẩu đổi mật khẩu bằng cách nhập mật khẩu hiện tại và mật khẩu mới.

**Why this priority**: Cần cho bảo mật tài khoản, nhưng người quên mật khẩu đã có luồng đặt lại ở spec 010.

**Independent Test**: Đổi mật khẩu, đăng xuất, đăng nhập được bằng mật khẩu mới, không đăng nhập được bằng mật khẩu cũ.

**Acceptance Scenarios**:

1. **Given** người dùng có mật khẩu, **When** nhập đúng mật khẩu hiện tại và mật khẩu mới hợp lệ (nhập lại khớp) rồi bấm "Đổi mật khẩu", **Then** mật khẩu được đổi, người dùng vẫn đăng nhập trên máy này và nhận thông báo thành công.
2. **Given** người dùng nhập sai mật khẩu hiện tại, **When** bấm "Đổi mật khẩu", **Then** app báo "Mật khẩu hiện tại không đúng" và không đổi gì.
3. **Given** mật khẩu mới trùng mật khẩu hiện tại, **When** bấm "Đổi mật khẩu", **Then** app báo mật khẩu mới phải khác mật khẩu cũ.
4. **Given** người dùng chỉ đăng nhập bằng Google hoặc số điện thoại (không có mật khẩu), **When** mở trang tài khoản, **Then** không thấy mục "Đổi mật khẩu".

---

### User Story 6 - Xóa tài khoản và dữ liệu cá nhân (Priority: P2)

Người dùng muốn rời SmashNow vào "Xóa tài khoản", đọc những gì sẽ bị xóa và những gì được giữ lại ở dạng ẩn danh, xác nhận lại danh tính, rồi xóa. Tài khoản và dữ liệu cá nhân bị xóa vĩnh viễn, người dùng về màn hình chào.

**Why this priority**: Bắt buộc theo Nghị định 13/2023 và constitution (nguyên tắc VI), nhưng ít dùng.

**Independent Test**: Tạo tài khoản có ảnh đại diện, lịch sử tìm kiếm, sân yêu thích; xóa tài khoản; kiểm tra không đăng nhập lại được, ảnh và dữ liệu cá nhân không còn, đánh giá cũ hiển thị tên "Người dùng đã xóa".

**Acceptance Scenarios**:

1. **Given** người dùng bấm "Xóa tài khoản", **When** màn hình xác nhận hiện ra, **Then** thấy danh sách dữ liệu sẽ bị xóa (hồ sơ, ảnh đại diện, lịch sử tìm kiếm, sân yêu thích, thông báo, hồ sơ chủ sân và giấy tờ) và dữ liệu được giữ ở dạng ẩn danh (đơn đặt sân đã qua, đánh giá, tin nhắn đã gửi).
2. **Given** người dùng ở màn hình xác nhận, **When** chưa xác nhận lại danh tính (nhập mật khẩu, đăng nhập lại Google hoặc nhập OTP tùy cách đăng nhập) và chưa gõ chữ "XÓA", **Then** nút "Xóa vĩnh viễn" bị vô hiệu hóa.
3. **Given** người dùng đã xác nhận đủ, **When** bấm "Xóa vĩnh viễn", **Then** tài khoản bị xóa, người dùng về màn hình chào, và không đăng nhập lại được bằng thông tin cũ.
4. **Given** tài khoản đã bị xóa, **When** người khác xem đánh giá hoặc tin nhắn người đó từng viết, **Then** thấy tên "Người dùng đã xóa" và ảnh mặc định, không thấy thông tin cá nhân.
5. **Given** người dùng có đơn đặt sân hoặc đăng ký vãng lai sắp tới, **When** bấm "Xóa tài khoản", **Then** màn hình xác nhận hiển thị số đơn/lượt sẽ bị hủy và số tiền được hoàn theo chính sách hủy; sau khi xóa, các đơn/lượt đó chuyển sang "Đã hủy" và khung giờ được nhả cho người khác đặt.
6. **Given** người dùng là chủ sân đang có cơ sở hoạt động, **When** bấm "Xóa tài khoản", **Then** màn hình xác nhận cảnh báo rõ số cơ sở sẽ bị ẩn, số đơn và buổi vãng lai sắp tới của khách sẽ bị hủy; sau khi xóa, cơ sở không còn hiển thị khi tìm kiếm, mọi khách bị ảnh hưởng nhận thông báo hủy kèm hoàn tiền toàn bộ (giả lập).

---

### User Story 7 - Đổi email đăng nhập (Priority: P3)

Người dùng đăng ký bằng email nhưng gõ nhầm hoặc muốn đổi email, nhập email mới và mật khẩu hiện tại, rồi bấm link xác minh gửi tới email mới.

**Why this priority**: Spec 010 ghi rằng người gõ nhầm email sửa ở đây; ít người dùng nên ưu tiên thấp.

**Independent Test**: Đổi email, bấm link trong email mới, đăng xuất rồi đăng nhập được bằng email mới, không đăng nhập được bằng email cũ.

**Acceptance Scenarios**:

1. **Given** người dùng có email và mật khẩu, **When** nhập email mới chưa được dùng và đúng mật khẩu hiện tại, **Then** app gửi email xác minh tới địa chỉ mới và báo "Kiểm tra hộp thư mới để hoàn tất"; email đăng nhập chỉ đổi sau khi bấm link.
2. **Given** email mới đã thuộc tài khoản khác, **When** gửi yêu cầu, **Then** app hiện cùng thông báo như kịch bản 1 ("Nếu email này chưa được dùng cho tài khoản khác, bạn sẽ nhận được thư xác minh"), không có thư xác minh nào được gửi và email đăng nhập không đổi. App không báo email đã được sử dụng để không lộ email nào đã có tài khoản.

---

### Edge Cases

- **Mất mạng** khi lưu hồ sơ, tải ảnh, đổi mật khẩu hoặc xóa tài khoản: app báo lỗi kèm nút thử lại, không mất nội dung đang sửa, không để trạng thái nửa vời (ảnh tải lên dở không thay ảnh cũ; xóa tài khoản không thành công thì tài khoản vẫn dùng được bình thường).
- **Ảnh quá lớn hoặc sai định dạng**: ảnh lớn được tự thu nhỏ trước khi tải lên; tệp không phải ảnh (JPG, PNG, WebP) bị từ chối kèm thông báo.
- **Danh mục khu vực không tải được**: vẫn sửa được các trường khác; mục khu vực hiển thị lỗi và nút thử lại.
- **Bấm "Lưu" nhiều lần**: chỉ một lần lưu được thực hiện; nút bị khóa khi đang lưu.
- **Hồ sơ đang mở trên hai máy**: lần lưu sau cùng được giữ; máy còn lại thấy dữ liệu mới khi mở lại trang.
- **Xác nhận lại danh tính** khi đổi mật khẩu, đổi email hoặc xóa tài khoản: app luôn yêu cầu (FR-017) rồi tiếp tục thao tác, không bắt đăng xuất.
- **Đổi mật khẩu hoặc email khi đang đăng nhập trên nhiều máy**: máy đang thao tác vẫn dùng tiếp (đổi email thì đăng nhập lại bằng email mới sau khi bấm link); các máy khác bị đăng xuất và báo "Phiên đăng nhập đã hết hạn".
- **Tài khoản bị khóa trong lúc đang ở trang tài khoản**: thao tác tiếp theo bị từ chối, app đăng xuất và báo tài khoản bị khóa (theo spec 010).
- **Chưa xác minh email**: trang tài khoản có nút "Gửi lại email xác minh" (dùng lại hành vi ở spec 010).
- **Đang có đơn ở trạng thái giữ chỗ hoặc đang thanh toán** khi xóa tài khoản: đơn đó bị hủy như đơn sắp tới, khung giờ được nhả ngay.
- **Khách của chủ sân bị xóa đang mở màn hình chi tiết đơn**: lần làm mới tiếp theo thấy đơn "Đã hủy" với lý do "Cơ sở ngừng hoạt động".
- **Người dùng là admin**: trang tài khoản không có mục đăng ký chủ sân, không có nút chuyển chế độ và không có nút "Xóa tài khoản". Tài khoản admin do nhóm xóa thủ công bằng script; máy chủ từ chối yêu cầu admin tự xóa.

## Requirements *(mandatory)*

### Functional Requirements

**Hồ sơ**

- **FR-001**: Người dùng đã đăng nhập PHẢI xem được hồ sơ của mình gồm: ảnh đại diện, họ tên, email và/hoặc số điện thoại, trạng thái xác minh email, trình độ, khu vực hay chơi, trạng thái chủ sân, số dư ví giả lập.
- **FR-001a**: Trang tài khoản PHẢI hiển thị số dư ví giả lập (`walletBalance`) kèm nhãn "Giả lập, không phải tiền thật" và nút "Nạp ví giả lập" mở chức năng nạp ví của spec 050. Người dùng không sửa trực tiếp số dư; mọi thay đổi số dư do máy chủ ghi (spec 050, 060).
- **FR-002**: Người dùng PHẢI sửa được họ tên (2–50 ký tự), trình độ (một trong: Mới chơi, Trung bình, Khá/Nâng cao) và khu vực hay chơi (chọn 0–5 khu vực từ danh mục khu vực của hệ thống).
- **FR-003**: Người dùng PHẢI đổi được ảnh đại diện bằng cách chụp hoặc chọn ảnh, cắt vuông trước khi tải lên. Ảnh sau xử lý KHÔNG ĐƯỢC vượt quá 2 MB; chỉ chấp nhận JPG, PNG, WebP. Người dùng cũng PHẢI xóa được ảnh để về ảnh mặc định.
- **FR-004**: Ảnh đại diện PHẢI công khai qua đường dẫn khó đoán (ai có đường dẫn đều xem được, kể cả người chưa đăng nhập), nhưng chỉ chủ tài khoản được thay hoặc xóa.
- **FR-005**: Người dùng KHÔNG ĐƯỢC sửa vai trò, trạng thái chủ sân, trạng thái tài khoản hay trạng thái xác minh từ trang hồ sơ; các trường này chỉ hiển thị.
- **FR-006**: Màn hình sửa hồ sơ PHẢI hỏi xác nhận khi người dùng thoát mà còn thay đổi chưa lưu.

**Đăng xuất**

- **FR-007**: Người dùng PHẢI đăng xuất được từ trang tài khoản sau một bước xác nhận. Sau khi đăng xuất, app PHẢI về Trang chủ ở chế độ khách (spec 010 FR-025), xóa lịch sử điều hướng, và máy đó PHẢI ngừng nhận thông báo đẩy của tài khoản.

**Chủ sân**

- **FR-008**: Trang tài khoản PHẢI hiển thị mục chủ sân theo trạng thái:

  | Trạng thái chủ sân | Hiển thị | Hành động |
  |---|---|---|
  | Chưa đăng ký | "Đăng ký làm chủ sân" + mô tả ngắn | Mở màn hình nộp hồ sơ (spec 110) |
  | Chờ duyệt | "Đang chờ duyệt" + ngày nộp | Không có |
  | Bị từ chối | "Bị từ chối" + lý do của admin | "Sửa và nộp lại" (mở spec 110 với dữ liệu cũ) |
  | Đã duyệt | "Đã là chủ sân" | Nút chuyển chế độ (FR-010) |
  | Bị thu hồi (`ownerStatus = NONE`, hồ sơ gần nhất vẫn `APPROVED`) | "Quyền chủ sân đã bị thu hồi" + lý do admin ghi | "Đăng ký làm chủ sân" (mở spec 110 với form trống để nộp hồ sơ mới) |

- **FR-009**: Trạng thái chủ sân hiển thị PHẢI là trạng thái hiện tại trên máy chủ và PHẢI cập nhật khi người dùng quay lại trang tài khoản, không cần đăng xuất.
- **FR-010**: Chỉ tài khoản có vai trò chủ sân đã duyệt mới thấy và dùng được chức năng chuyển chế độ "Người chơi" ↔ "Quản lý sân". Việc chuyển KHÔNG yêu cầu đăng nhập lại.
- **FR-011**: Mỗi lần mở app, chủ sân vào giao diện quản lý sân (giữ đúng FR-015 của spec 010); chế độ đã chọn chỉ có hiệu lực trong phiên dùng hiện tại.
- **FR-012**: Trước khi vào giao diện quản lý sân, app PHẢI kiểm tra lại vai trò do máy chủ cấp; nếu quyền chủ sân đã bị thu hồi, app PHẢI đưa người dùng về giao diện người chơi và thông báo lý do.
- **FR-013**: Admin KHÔNG thấy mục đăng ký chủ sân và nút chuyển chế độ.

**Mật khẩu và email**

- **FR-014**: Người dùng có mật khẩu PHẢI đổi được mật khẩu bằng cách nhập mật khẩu hiện tại, mật khẩu mới và nhập lại. Mật khẩu mới PHẢI theo cùng yêu cầu như spec 010 (tối thiểu 8 ký tự, có chữ và số) và khác mật khẩu hiện tại.
- **FR-015**: Mục đổi mật khẩu và đổi email KHÔNG ĐƯỢC hiển thị với tài khoản không có mật khẩu (chỉ dùng Google hoặc số điện thoại).
- **FR-016**: Người dùng có email và mật khẩu PHẢI đổi được email đăng nhập; email mới chỉ có hiệu lực sau khi người dùng bấm link xác minh gửi tới email mới.
- **FR-017**: Các thao tác đổi mật khẩu, đổi email và xóa tài khoản PHẢI luôn yêu cầu xác nhận lại danh tính (nhập mật khẩu, đăng nhập lại Google hoặc nhập OTP tùy cách đăng nhập). Với xóa tài khoản, máy chủ PHẢI từ chối nếu lần xác nhận đã quá 5 phút.

**Xóa tài khoản**

- **FR-018**: Người dùng (trừ admin) PHẢI xóa được tài khoản của mình. Trước khi xóa, app PHẢI liệt kê dữ liệu sẽ bị xóa và dữ liệu được giữ ở dạng ẩn danh, yêu cầu xác nhận lại danh tính và gõ chữ "XÓA".
- **FR-019**: Khi xóa tài khoản, hệ thống PHẢI xóa vĩnh viễn: hồ sơ cá nhân (họ tên, email, số điện thoại, ảnh đại diện, trình độ, khu vực), lịch sử tìm kiếm, danh sách sân yêu thích, thông báo, cài đặt thông báo, số dư ví giả lập, hồ sơ chủ sân và ảnh giấy tờ, và phương thức đăng nhập.
- **FR-020**: Dữ liệu liên quan tới người khác (đơn đặt sân đã qua, đánh giá, tin nhắn, bài đăng) PHẢI được giữ lại nhưng ẩn danh: hiển thị "Người dùng đã xóa" và ảnh mặc định, không còn liên kết tới thông tin cá nhân.
- **FR-021**: Việc xóa PHẢI được thực hiện ở phía máy chủ theo hai bước:
  - **Chốt**: mọi thay đổi ảnh hưởng tới người khác (hủy đơn, nhả khung giờ, hoàn tiền, ẩn cơ sở) và việc đánh dấu tài khoản đang bị xóa PHẢI xảy ra cùng lúc hoặc không xảy ra. Nếu bước này thất bại, tài khoản vẫn dùng được bình thường và người dùng được báo để thử lại.
  - **Hoàn tất**: sau khi chốt, tài khoản không đăng nhập hay thao tác được nữa; hệ thống PHẢI tự hoàn tất việc gửi thông báo, ẩn danh và xóa dữ liệu cá nhân (theo FR-019, FR-020), tự thử lại khi lỗi, và xóa phương thức đăng nhập sau cùng.
- **FR-022**: Xóa tài khoản KHÔNG bị chặn bởi đơn sắp tới hay cơ sở đang hoạt động. Trước khi xác nhận, app PHẢI hiển thị tác động: số đơn đặt sân và lượt vãng lai sắp tới của người dùng sẽ bị hủy, số tiền hoàn theo chính sách hủy; với chủ sân, thêm số cơ sở sẽ bị ẩn và số đơn, buổi vãng lai của khách sẽ bị hủy. Màn hình xác nhận PHẢI ghi rõ tiền hoàn cho đơn của chính người dùng được cộng vào ví giả lập và bị xóa cùng ví, không quy đổi được. Nếu tác động thay đổi giữa lúc xem và lúc xác nhận (ví dụ có đơn mới), app PHẢI hiển thị tác động mới và yêu cầu xác nhận lại.
- **FR-022a**: Khi xóa, hệ thống PHẢI trong bước chốt của FR-021: (1) hủy các đơn đặt sân và lượt vãng lai sắp tới của người dùng theo chính sách hủy thông thường (người hủy ghi là "hệ thống"); (2) nếu là chủ sân: ẩn mọi cơ sở, hủy mọi đơn đặt sân và buổi vãng lai sắp tới tại các cơ sở đó với hoàn tiền giả lập toàn bộ cho khách; (3) nhả mọi khung giờ đã bị chiếm bởi các đơn bị hủy. Nếu bất kỳ bước nào thất bại, theo FR-021 không có gì bị thay đổi. Thông báo hủy kèm lý do "Cơ sở ngừng hoạt động" cho từng khách PHẢI được gửi ở bước hoàn tất, mỗi khách đúng một lần dù hệ thống phải thử lại.

**Trải nghiệm chung**

- **FR-023**: Mọi thao tác ghi (lưu hồ sơ, tải ảnh, đổi mật khẩu, đổi email, xóa tài khoản) PHẢI khóa nút trong lúc xử lý, báo thành công hoặc lỗi bằng tiếng Việt, và cho thử lại khi mất mạng mà không mất dữ liệu đang nhập (trừ mật khẩu).
- **FR-024**: Quyền camera và truy cập ảnh PHẢI chỉ được xin khi người dùng bấm đổi ảnh; từ chối quyền không làm hỏng các chức năng khác.
- **FR-025**: Các màn hình PHẢI có đủ trạng thái đang tải, có dữ liệu và lỗi kèm thử lại; hiển thị đúng ở chế độ sáng/tối, trên điện thoại và máy tính bảng; ảnh và nút có nhãn cho trình đọc màn hình.

### Key Entities *(include if feature involves data)*

- **Người dùng (User)**: hồ sơ của một tài khoản. Trường người dùng sửa được: họ tên, ảnh đại diện, trình độ, khu vực hay chơi. Trường chỉ xem: email, số điện thoại, trạng thái xác minh email, vai trò, trạng thái chủ sân, trạng thái tài khoản, số dư ví giả lập. Chi tiết theo [đặc tả CSDL, mục 4.1](../../docs/design/database/README.md#41-usersuid--a).
- **Hồ sơ chủ sân (Owner application)**: hồ sơ người dùng nộp để trở thành chủ sân. Spec này chỉ đọc: trạng thái (chờ duyệt / đã duyệt / bị từ chối), ngày nộp, lý do từ chối. Tạo và sửa thuộc spec 110; duyệt thuộc spec 120. Chi tiết theo [đặc tả CSDL, mục 4.2](../../docs/design/database/README.md#42-ownerapplicationsid--d).
- **Khu vực (District)**: danh mục quận/huyện do admin quản lý; người dùng chọn từ danh sách, không tự nhập.
- **Ảnh đại diện (Avatar)**: một ảnh cho mỗi người dùng, công khai qua đường dẫn khó đoán (FR-004).
- **Chế độ hiển thị (Mode)**: "Người chơi" hoặc "Quản lý sân", chỉ áp dụng cho chủ sân đã duyệt, chỉ có hiệu lực trong phiên dùng hiện tại.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người dùng sửa và lưu xong hồ sơ (tên, trình độ, khu vực) trong dưới 1 phút.
- **SC-002**: Ảnh đại diện mới hiển thị trên trang tài khoản trong vòng 5 giây sau khi xác nhận, trên mạng 4G thông thường, ở ít nhất 95% số lần thử.
- **SC-003**: Chủ sân chuyển giữa hai chế độ trong 1 lần chạm từ trang tài khoản, giao diện mới hiện ra trong vòng 1 giây.
- **SC-004**: 100% tài khoản trong kiểm thử thấy đúng mục chủ sân theo 5 trạng thái ở FR-008.
- **SC-005**: Sau khi xóa tài khoản, 0 thông tin cá nhân của người đó (tên, email, số điện thoại, ảnh) còn xem được ở bất kỳ màn hình nào trong các kịch bản kiểm thử.
- **SC-006**: 0 trường hợp người dùng tự đổi được vai trò, trạng thái chủ sân hoặc trạng thái tài khoản trong các kịch bản kiểm thử phân quyền.
- **SC-007**: Trong kiểm thử mất mạng ở mọi thao tác ghi, app không crash và không để dữ liệu ở trạng thái nửa vời.
- **SC-008**: Sau khi xóa tài khoản chủ sân trong kiểm thử, 100% khách có đơn hoặc lượt vãng lai sắp tới tại cơ sở đó nhận thông báo hủy, và 0 khung giờ của các đơn bị hủy còn bị chiếm.
- **SC-009**: Ít nhất 4/5 người thử tự tìm được và dùng được chức năng "Đăng ký làm chủ sân" mà không cần hướng dẫn.

## Assumptions

- Spec 010 đã có: đăng nhập, điều hướng theo vai trò khi mở app, banner xác minh email, yêu cầu mật khẩu.
- Màn hình nộp và sửa hồ sơ chủ sân thuộc spec 110 (D); spec này chỉ có lối vào và phần hiển thị trạng thái. Giao diện quản lý sân thuộc spec 111, 112.
- Khi admin thu hồi quyền chủ sân (spec 120), `ownerStatus` về `NONE` và hồ sơ cũ giữ `APPROVED` để lưu lịch sử; trang tài khoản hiện dòng "Quyền chủ sân đã bị thu hồi" kèm lý do, người dùng nhận thông báo `OWNER_REVOKED` (spec 100) và được nộp hồ sơ mới (spec 110 FR-018).
- Danh mục khu vực do admin quản lý (spec 120) và có dữ liệu mẫu từ tuần 4.
- Việc hủy đơn khi xóa tài khoản dùng lại chính sách hủy của spec 060 (B), cách ẩn cơ sở của spec 111 (D), hủy buổi vãng lai của spec 080/112 (C, D) và loại thông báo `BOOKING_CANCELLED`, `DROPIN_CHANGED` của spec 100 (D). Nhóm A cần thống nhất với B, C, D trước khi chạy `/speckit-plan`.
- Đổi số điện thoại không nằm trong phạm vi phiên bản này (đăng nhập bằng số điện thoại chỉ dùng số thử nghiệm).
- Thời gian giữ dữ liệu ẩn danh sau khi xóa tài khoản: giữ cho tới khi dữ liệu đó bị xóa theo nghiệp vụ của tính năng sở hữu nó.
- Cách lưu ảnh, cách xóa dữ liệu phía máy chủ và cách kiểm tra quyền tuân theo constitution v1.1.0 và [đặc tả CSDL](../../docs/design/database/README.md) (mục 4.1, 4.2, 8, 8a); chi tiết để `/speckit-plan` quyết định.
