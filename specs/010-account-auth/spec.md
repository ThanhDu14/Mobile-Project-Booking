# Feature Specification: Đăng ký và đăng nhập tài khoản

**Feature Branch**: `feature/01-account-specs`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Người dùng tạo tài khoản và đăng nhập SmashNow. Đăng ký bằng email hoặc số điện thoại, chọn mục đích "Tôi muốn đặt sân" hoặc "Tôi là chủ sân", bắt buộc đồng ý điều khoản và chính sách quyền riêng tư. Đăng nhập bằng email/mật khẩu, số điện thoại (OTP, dùng số thử nghiệm của Firebase) hoặc Google. Quên mật khẩu và đặt lại qua email. Sau khi đăng ký, Edge Function on-signup gán claim role="authenticated", appRole="PLAYER" và tạo users/{uid}; app làm mới token. Tài khoản bị khóa (accountStatus=LOCKED) không đăng nhập được. Có xử lý mất mạng và lỗi nhập liệu. Không bao gồm: hồ sơ, đổi mật khẩu, chuyển chế độ (spec 011); nộp hồ sơ chủ sân (spec 110). Dữ liệu: users theo docs/design/database/README.md mục 4.1."

> Mô tả gốc ở trên được giữ nguyên để lưu vết. Phần "chọn mục đích" **đã bị bỏ** theo mục Clarifications (Session 2026-10-05): không chọn vai trò khi đăng ký, điều hướng theo vai trò sau đăng nhập, đăng ký chủ sân trong trang hồ sơ.

**Nhóm tính năng**: [01 — Tài khoản](../../docs/features/01-account/README.md) · **Phụ trách**: A

## Clarifications

### Session 2026-10-05

- Q: Khi đăng ký có cho người dùng chọn "Tôi muốn đặt sân" / "Tôi là chủ sân" không, và sau đăng nhập đưa người dùng đi đâu? → A: Không có bước chọn mục đích. Mọi tài khoản mới đều là người chơi. Sau khi đăng nhập, app điều hướng theo vai trò: chủ sân đã được duyệt vào giao diện chủ sân, người chơi vào giao diện người chơi (admin vào giao diện quản trị). Muốn làm chủ sân thì đăng ký trong trang hồ sơ sau khi đăng nhập, bằng thông tin người dùng cung cấp, và chờ admin duyệt.
- Q: Người đăng ký bằng email có phải xác minh email không, và xác minh trước hay sau khi dùng app? → A: Gửi email xác minh ngay sau đăng ký, cho dùng app ngay, hiện banner nhắc cho tới khi xác minh xong.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Đăng ký bằng email và mật khẩu (Priority: P1)

Người mới mở app lần đầu, xem màn hình chào, nhập họ tên, email và mật khẩu, đánh dấu đồng ý điều khoản và chính sách quyền riêng tư, rồi tạo tài khoản. Sau khi tạo xong, người dùng vào thẳng màn hình chính của người chơi mà không phải đăng nhập lại.

**Why this priority**: Không có tài khoản thì không dùng được tính năng nào khác (đặt sân, đánh giá, chat). Email/mật khẩu là cách đăng ký không phụ thuộc dịch vụ ngoài, nên là lát cắt MVP nhỏ nhất.

**Independent Test**: Cài app mới, đăng ký bằng một email chưa dùng, kiểm tra người dùng vào được màn hình chính với vai trò người chơi và hồ sơ người dùng đã được tạo với đúng họ tên, email, thời điểm đồng ý điều khoản.

**Acceptance Scenarios**:

1. **Given** người dùng chưa có tài khoản và đang ở màn hình đăng ký, **When** nhập họ tên, email hợp lệ chưa dùng, mật khẩu đạt yêu cầu, đánh dấu đồng ý điều khoản và bấm "Đăng ký", **Then** tài khoản được tạo với vai trò người chơi, thời điểm đồng ý điều khoản được ghi lại, và người dùng được đưa tới màn hình chính ở trạng thái đã đăng nhập.
2. **Given** người dùng chưa đánh dấu đồng ý điều khoản, **When** xem màn hình đăng ký, **Then** nút "Đăng ký" bị vô hiệu hóa và có liên kết mở được nội dung điều khoản và chính sách quyền riêng tư.
3. **Given** email đã được dùng cho một tài khoản khác, **When** người dùng bấm "Đăng ký", **Then** app báo "Email đã được sử dụng" ngay dưới ô email, gợi ý đăng nhập hoặc quên mật khẩu, và không tạo tài khoản mới.
4. **Given** người dùng đang ở màn hình đăng ký, **When** xem các trường cần nhập, **Then** không có lựa chọn vai trò hay mục đích sử dụng; tài khoản tạo ra luôn là người chơi.
5. **Given** người dùng vừa đăng ký bằng email và chưa bấm link xác minh, **When** vào màn hình chính, **Then** thấy banner nhắc xác minh email có nút "Gửi lại email"; sau khi bấm link xác minh trong hộp thư và quay lại app, banner không còn.
6. **Given** người dùng nhập email sai định dạng, mật khẩu ngắn hơn yêu cầu hoặc bỏ trống họ tên, **When** rời ô nhập hoặc bấm "Đăng ký", **Then** lỗi hiển thị ngay dưới đúng ô đó bằng tiếng Việt và không có yêu cầu nào được gửi đi.

---

### User Story 2 - Đăng nhập bằng email và mật khẩu (Priority: P1)

Người đã có tài khoản mở app, nhập email và mật khẩu để vào app. Lần mở app sau, người dùng vẫn ở trạng thái đăng nhập cho tới khi tự đăng xuất.

**Why this priority**: Cùng mức với đăng ký: mọi lần dùng app sau lần đầu đều cần đăng nhập. Ghép User Story 1 và 2 là đủ một MVP tài khoản.

**Independent Test**: Với một tài khoản đã có, đăng nhập đúng thì vào màn hình chính; tắt hẳn app rồi mở lại thì vẫn ở màn hình chính; đăng nhập sai thì nhận thông báo lỗi.

**Acceptance Scenarios**:

1. **Given** tài khoản người chơi đang hoạt động (kể cả người đang chờ duyệt hoặc bị từ chối hồ sơ chủ sân), **When** người dùng đăng nhập đúng, **Then** người dùng vào giao diện người chơi.
2. **Given** tài khoản đã được admin duyệt làm chủ sân, **When** người dùng đăng nhập đúng, **Then** người dùng vào thẳng giao diện chủ sân.
3. **Given** tài khoản admin, **When** đăng nhập đúng, **Then** người dùng vào giao diện quản trị.
4. **Given** người dùng nhập sai email hoặc mật khẩu, **When** bấm "Đăng nhập", **Then** app báo "Email hoặc mật khẩu không đúng" mà không cho biết cụ thể sai phần nào.
5. **Given** người dùng đã đăng nhập trước đó và chưa đăng xuất, **When** mở lại app, **Then** app vào thẳng màn hình chính, không hiện màn hình đăng nhập.
6. **Given** tài khoản đã bị admin khóa, **When** người dùng đăng nhập bằng bất kỳ phương thức nào, **Then** app không cho vào, hiển thị "Tài khoản của bạn đã bị khóa" kèm cách liên hệ hỗ trợ, và người dùng ở lại màn hình đăng nhập.
7. **Given** tài khoản bị khóa trong lúc người dùng đang đăng nhập sẵn, **When** người dùng mở lại app, **Then** app đăng xuất người dùng và hiển thị thông báo tài khoản bị khóa.

---

### User Story 3 - Quên và đặt lại mật khẩu (Priority: P2)

Người dùng quên mật khẩu bấm "Quên mật khẩu", nhập email, nhận email chứa liên kết đặt lại mật khẩu, đặt mật khẩu mới rồi đăng nhập lại.

**Why this priority**: Không có chức năng này thì người quên mật khẩu mất luôn tài khoản, nhưng nó không chặn luồng chính lần đầu dùng app.

**Independent Test**: Gửi yêu cầu đặt lại cho một email đã đăng ký, mở liên kết trong email, đặt mật khẩu mới, đăng nhập được bằng mật khẩu mới và không đăng nhập được bằng mật khẩu cũ.

**Acceptance Scenarios**:

1. **Given** người dùng ở màn hình quên mật khẩu, **When** nhập email và bấm "Gửi", **Then** app luôn hiển thị "Nếu email đã đăng ký, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu", dù email có tồn tại hay không.
2. **Given** người dùng đã nhận email đặt lại, **When** mở liên kết và đặt mật khẩu mới đạt yêu cầu, **Then** mật khẩu mới có hiệu lực và mật khẩu cũ không dùng được nữa.
3. **Given** người dùng vừa gửi yêu cầu, **When** muốn gửi lại, **Then** nút "Gửi lại" chỉ bấm được sau 60 giây.

---

### User Story 4 - Đăng nhập bằng Google (Priority: P2)

Người dùng bấm "Tiếp tục với Google", chọn tài khoản Google trên máy và vào app ngay. Nếu đây là lần đầu, tài khoản SmashNow (vai trò người chơi) được tạo tự động sau khi người dùng đồng ý điều khoản.

**Why this priority**: Giảm số bước đăng ký đáng kể và là phương thức proposal ưu tiên, nhưng app vẫn dùng được hoàn toàn bằng email.

**Independent Test**: Trên máy có tài khoản Google, đăng nhập lần đầu tạo được tài khoản người chơi mới; đăng nhập lần hai vào thẳng màn hình chính.

**Acceptance Scenarios**:

1. **Given** tài khoản Google chưa từng dùng với SmashNow, **When** người dùng chọn tài khoản đó, **Then** app hiển thị bước đồng ý điều khoản; sau khi xác nhận, tài khoản người chơi được tạo với họ tên và email lấy từ Google.
2. **Given** tài khoản Google đã dùng với SmashNow trước đó, **When** người dùng chọn tài khoản đó, **Then** người dùng vào thẳng giao diện theo vai trò của mình.
3. **Given** email của tài khoản Google trùng với một tài khoản đã đăng ký bằng email/mật khẩu, **When** người dùng đăng nhập bằng Google, **Then** hai cách đăng nhập dẫn tới cùng một tài khoản SmashNow, không tạo tài khoản thứ hai.
4. **Given** người dùng hủy ở bước chọn tài khoản Google, **When** quay lại app, **Then** người dùng ở lại màn hình đăng nhập và không có thông báo lỗi.

---

### User Story 5 - Đăng ký và đăng nhập bằng số điện thoại với mã OTP (Priority: P3)

Người dùng chọn "Dùng số điện thoại", nhập số điện thoại Việt Nam, nhận mã OTP 6 chữ số, nhập mã để đăng nhập. Nếu số chưa có tài khoản, người dùng điền họ tên và đồng ý điều khoản để tạo tài khoản người chơi mới.

**Why this priority**: Có trong proposal nhưng trong phiên bản học tập chỉ chạy được với các số điện thoại thử nghiệm đã khai báo trước, nên là phương thức phụ.

**Independent Test**: Với một số điện thoại thử nghiệm và mã OTP tương ứng, tạo được tài khoản mới, đăng xuất rồi đăng nhập lại được bằng cùng số.

**Acceptance Scenarios**:

1. **Given** người dùng nhập số điện thoại hợp lệ (10 số, đầu 0, hoặc dạng +84), **When** bấm "Gửi mã", **Then** app chuyển sang màn hình nhập mã OTP, hiển thị số đã nhập (che bớt) và bộ đếm 60 giây cho nút "Gửi lại mã".
2. **Given** người dùng nhập đúng mã OTP còn hiệu lực, **When** số đó đã có tài khoản, **Then** người dùng vào giao diện theo vai trò; **When** số đó chưa có tài khoản, **Then** app hiển thị bước điền họ tên, đồng ý điều khoản rồi mới tạo tài khoản người chơi.
3. **Given** người dùng nhập sai mã OTP, **When** bấm "Xác nhận", **Then** app báo "Mã không đúng" và cho nhập lại; sau 5 lần sai liên tiếp, phải yêu cầu mã mới.
4. **Given** mã OTP đã hết hạn, **When** người dùng nhập mã, **Then** app báo mã hết hạn và gợi ý gửi lại mã.

---

### Edge Cases

- **Mất mạng** khi bấm "Đăng ký", "Đăng nhập", "Gửi mã" hoặc "Gửi": app không treo, không thoát, hiển thị "Không có kết nối mạng" kèm nút "Thử lại", giữ nguyên dữ liệu người dùng đã nhập (trừ mật khẩu và OTP).
- **Bấm nhiều lần** nút "Đăng ký" hoặc "Đăng nhập": chỉ một yêu cầu được xử lý; nút bị khóa và hiển thị trạng thái đang xử lý cho tới khi có kết quả.
- **Tạo tài khoản thành công nhưng bước khởi tạo hồ sơ và vai trò thất bại** (mất mạng giữa chừng, lỗi máy chủ): người dùng không bị kẹt; lần mở app hoặc đăng nhập tiếp theo, hệ thống tự hoàn tất bước khởi tạo. Trong lúc chưa hoàn tất, người dùng thấy màn hình chờ có nút thử lại, không vào được các tính năng cần quyền.
- **Đóng app giữa chừng** ở bước đồng ý điều khoản sau khi xác thực Google hoặc OTP: lần mở sau, app đưa người dùng quay lại đúng bước đó, không vào màn hình chính khi chưa đồng ý điều khoản.
- **Email có chữ hoa hoặc khoảng trắng thừa**: được chuẩn hóa (bỏ khoảng trắng đầu/cuối, chữ thường) trước khi kiểm tra trùng và đăng nhập.
- **Số điện thoại không phải số thử nghiệm** trong phiên bản học tập: app báo không gửi được mã và gợi ý dùng email hoặc Google.
- **Gửi OTP hoặc yêu cầu đặt lại mật khẩu quá nhiều lần**: khi bị giới hạn, app báo "Bạn đã thử quá nhiều lần, vui lòng thử lại sau".
- **Không nhận được email xác minh** (vào thư rác, gõ nhầm email): người dùng vẫn dùng app bình thường và có thể gửi lại email từ banner; nếu gõ nhầm email, sửa email thuộc spec 011.
- **Tài khoản admin**: đăng nhập như người dùng khác ở màn hình này, không có lựa chọn đăng ký làm admin.
- **Vai trò thay đổi khi đang đăng nhập** (admin vừa duyệt hoặc vừa thu hồi quyền chủ sân): app áp dụng vai trò mới ở lần mở app hoặc lần làm mới phiên kế tiếp và điều hướng lại cho đúng giao diện; người bị thu hồi quyền không còn vào được giao diện chủ sân.
- **Người dùng đã đăng nhập mở màn hình đăng nhập** (qua nút quay lại): không thể quay về màn hình đăng nhập/đăng ký khi đang đăng nhập.

## Requirements *(mandatory)*

### Functional Requirements

**Đăng ký**

- **FR-001**: Hệ thống PHẢI cho phép tạo tài khoản bằng một trong ba cách: email kèm mật khẩu, số điện thoại kèm mã OTP, hoặc tài khoản Google.
- **FR-002**: Màn hình đăng ký KHÔNG ĐƯỢC hỏi vai trò hay mục đích sử dụng. Mọi tài khoản mới đều bắt đầu là người chơi.
- **FR-003**: Người dùng PHẢI đánh dấu đồng ý điều khoản sử dụng và chính sách quyền riêng tư trước khi tài khoản được tạo; hệ thống PHẢI lưu thời điểm đồng ý. Nội dung điều khoản PHẢI mở xem được từ màn hình đăng ký và có ghi rõ đây là phiên bản học tập.
- **FR-004**: Khi đăng ký bằng email, hệ thống PHẢI yêu cầu họ tên (2–50 ký tự), email đúng định dạng, mật khẩu tối thiểu 8 ký tự gồm cả chữ và số, và nhập lại mật khẩu khớp.
- **FR-005**: Mọi tài khoản mới PHẢI được tạo với vai trò **người chơi**, trạng thái chủ sân **chưa đăng ký** và trạng thái tài khoản **đang hoạt động**. Người dùng KHÔNG ĐƯỢC tự chọn hay tự sửa vai trò, trạng thái chủ sân hoặc trạng thái tài khoản.
- **FR-006**: Việc gán vai trò và tạo hồ sơ người dùng PHẢI được thực hiện ở phía máy chủ ngay sau khi tài khoản được tạo; app PHẢI chỉ coi người dùng là đã đăng nhập đầy đủ khi vai trò đã có hiệu lực trong phiên đăng nhập hiện tại.
- **FR-007**: Spec này KHÔNG có lối vào đăng ký chủ sân. Người chơi muốn trở thành chủ sân đăng ký từ trang hồ sơ sau khi đăng nhập (spec 011 dẫn tới spec 110), và chỉ có quyền chủ sân sau khi admin duyệt (spec 120).
- **FR-008**: Hệ thống PHẢI ngăn tạo hai tài khoản với cùng một email hoặc cùng một số điện thoại.
- **FR-009**: Khi đăng ký bằng email, hệ thống PHẢI gửi email xác minh ngay sau khi tạo tài khoản. Người dùng được dùng app ngay mà không cần xác minh trước; trong lúc chưa xác minh, app PHẢI hiển thị banner nhắc kèm nút "Gửi lại email" (bấm lại được sau 60 giây). Banner biến mất sau khi xác minh xong (kiểm tra lại khi người dùng quay về app). Tài khoản đăng nhập bằng Google được coi là đã xác minh email; tài khoản chỉ dùng số điện thoại không có banner này.

**Đăng nhập và phiên**

- **FR-010**: Hệ thống PHẢI cho phép đăng nhập bằng email/mật khẩu, số điện thoại/OTP và Google.
- **FR-011**: Khi đăng nhập thất bại vì sai thông tin, thông báo lỗi KHÔNG ĐƯỢC tiết lộ email hoặc số điện thoại có tồn tại trong hệ thống hay không.
- **FR-012**: Nếu email Google trùng với email của tài khoản đã có, hai cách đăng nhập PHẢI dẫn tới cùng một tài khoản.
- **FR-013**: Hệ thống PHẢI giữ trạng thái đăng nhập giữa các lần mở app cho tới khi người dùng đăng xuất (thuộc spec 011) hoặc tài khoản bị khóa.
- **FR-014**: Tài khoản có trạng thái **bị khóa** KHÔNG ĐƯỢC đăng nhập bằng bất kỳ phương thức nào; nếu đang đăng nhập sẵn, app PHẢI đăng xuất người dùng ở lần mở app hoặc lần làm mới phiên kế tiếp và hiển thị thông báo tài khoản bị khóa.
- **FR-015**: Sau khi đăng nhập (và mỗi lần mở app khi còn phiên), app PHẢI điều hướng theo vai trò hiện tại: **chủ sân đã được duyệt** → giao diện chủ sân; **người chơi** (gồm cả người có hồ sơ chủ sân đang chờ duyệt hoặc bị từ chối) → giao diện người chơi; **admin** → giao diện quản trị. Vai trò dùng để điều hướng PHẢI là vai trò do máy chủ cấp, không phải giá trị lưu trên thiết bị.

**OTP**

- **FR-016**: Mã OTP PHẢI gồm 6 chữ số, có hiệu lực tối đa 5 phút; nút gửi lại mã chỉ bật sau 60 giây; sau 5 lần nhập sai liên tiếp, mã hiện tại bị vô hiệu.
- **FR-017**: Ô số điện thoại PHẢI chấp nhận số di động Việt Nam dạng `0xxxxxxxxx` hoặc `+84xxxxxxxxx` và chuẩn hóa về một định dạng duy nhất trước khi gửi mã.

**Quên mật khẩu**

- **FR-018**: Người dùng PHẢI yêu cầu được email đặt lại mật khẩu từ màn hình đăng nhập; thông báo sau khi gửi PHẢI giống nhau dù email có tồn tại hay không.
- **FR-019**: Mật khẩu mới PHẢI đạt cùng yêu cầu như FR-004; sau khi đặt lại, mật khẩu cũ PHẢI hết hiệu lực.

**Xử lý lỗi và trải nghiệm**

- **FR-020**: Lỗi nhập liệu PHẢI hiển thị bằng tiếng Việt ngay dưới ô tương ứng, trước khi gửi yêu cầu lên máy chủ.
- **FR-021**: Khi mất mạng hoặc máy chủ không phản hồi trong 15 giây, app PHẢI hiển thị thông báo lỗi kèm nút thử lại, không crash, không treo, và giữ lại dữ liệu đã nhập (trừ mật khẩu, OTP).
- **FR-022**: Trong lúc một yêu cầu đăng ký/đăng nhập đang xử lý, nút gửi PHẢI bị khóa để không phát sinh yêu cầu trùng.
- **FR-023**: Các màn hình PHẢI hiển thị đúng ở chế độ sáng, chế độ tối, trên điện thoại và máy tính bảng; ô nhập và nút có nhãn cho trình đọc màn hình.
- **FR-024**: App KHÔNG ĐƯỢC lưu mật khẩu ở dạng đọc được trên thiết bị.

### Key Entities *(include if feature involves data)*

- **Người dùng (User)**: một tài khoản SmashNow. Thuộc tính liên quan tới spec này: họ tên, email (nếu có), số điện thoại (nếu có), trạng thái xác minh email, vai trò (người chơi / chủ sân / admin), trạng thái chủ sân (chưa đăng ký / chờ duyệt / đã duyệt / bị từ chối), trạng thái tài khoản (đang hoạt động / bị khóa), thời điểm đồng ý điều khoản, thời điểm tạo. Một người dùng có thể có nhiều phương thức đăng nhập (email, Google, số điện thoại) cùng trỏ về một tài khoản. Chi tiết trường theo [đặc tả CSDL, mục 4.1](../../docs/design/database/README.md#41-usersuid--a).
- **Phương thức đăng nhập (Sign-in method)**: email/mật khẩu, số điện thoại, Google. Do dịch vụ xác thực quản lý, không lưu riêng trong dữ liệu nghiệp vụ.
- **Bản đồng ý điều khoản (Consent)**: thời điểm người dùng đồng ý điều khoản và chính sách quyền riêng tư, gắn với người dùng.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người dùng mới hoàn tất đăng ký bằng email và tới màn hình chính trong dưới 2 phút, tính từ màn hình chào.
- **SC-002**: Người dùng đã có tài khoản đăng nhập bằng Google trong tối đa 3 lần chạm.
- **SC-003**: Trên mạng 4G thông thường, kết quả đăng nhập (thành công hoặc lỗi) hiển thị trong vòng 3 giây sau khi bấm nút ở ít nhất 95% số lần thử.
- **SC-004**: 100% tài khoản mới có vai trò người chơi và thời điểm đồng ý điều khoản; không có tài khoản nào tạo được mà bỏ qua bước đồng ý.
- **SC-005**: 0 trường hợp tài khoản bị khóa đăng nhập được vào màn hình chính trong các kịch bản kiểm thử.
- **SC-006**: 0 trường hợp người dùng tự nâng được vai trò hoặc đổi trạng thái tài khoản của mình trong các kịch bản kiểm thử phân quyền.
- **SC-007**: Trong kiểm thử mất mạng ở mọi bước, app không crash lần nào và luôn có cách thử lại.
- **SC-008**: Ít nhất 4/5 người thử (thành viên nhóm khác và bạn bè) tự hoàn tất đăng ký và đăng nhập lần đầu mà không cần hướng dẫn.

## Assumptions

- Đây là phiên bản học tập: đăng nhập bằng số điện thoại chỉ hoạt động với các số và mã thử nghiệm do nhóm khai báo trước; người dùng thật được hướng tới email hoặc Google.
- Đăng ký làm chủ sân nằm trong trang hồ sơ (spec 011, 110); quyền chủ sân chỉ có sau khi admin duyệt (spec 120). Giao diện chủ sân, người chơi và quản trị thuộc các spec khác; spec này chỉ quy định điều hướng tới đó.
- Tài khoản admin do nhóm cấp thủ công; spec này không có màn hình tạo admin.
- Màn hình hồ sơ, đổi mật khẩu, đăng xuất, xóa tài khoản và chuyển chế độ thuộc spec 011. 
- Yêu cầu mật khẩu (8 ký tự, có chữ và số), thời hạn OTP 5 phút, chờ 60 giây giữa hai lần gửi lại và giới hạn 5 lần sai là mặc định theo thông lệ, nhóm có thể chỉnh trong `/speckit-clarify`.
- Nội dung điều khoản và chính sách quyền riêng tư là văn bản tĩnh do nhóm soạn, theo tinh thần Nghị định 13/2023/NĐ-CP.
- Cách thực hiện bước khởi tạo vai trò và hồ sơ (FR-006), cách lưu trạng thái khóa (FR-014) tuân theo constitution v1.1.0 và [đặc tả CSDL](../../docs/design/database/README.md) (mục 4.1 và 8a); chi tiết để `/speckit-plan` quyết định.
