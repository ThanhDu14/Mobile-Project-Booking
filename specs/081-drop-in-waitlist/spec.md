# Feature Specification: Hàng chờ buổi vãng lai

**Feature Branch**: `feature/08-drop-in`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi vào hàng chờ khi buổi vãng lai đã đủ người, và được tự động đẩy lên khi có chỗ trống. Ở buổi "Đã đủ người" (spec 080), người chơi bấm "Vào hàng chờ", chọn số người (tối đa 4 như spec 080); xem được vị trí của mình trong hàng chờ và rời hàng chờ bất cứ lúc nào. Khi có người hủy hoặc chủ sân tăng sức chứa, hệ thống tự đẩy người chờ lâu nhất có số người vừa với số chỗ trống lên thành đăng ký chính thức, chạy phía server trong transaction (Edge Function cancel-drop-in / register-drop-in) để không vượt chỗ và không đẩy trùng. Người được đẩy lên nhận vé QR và thông báo "Đã có chỗ" (DROPIN_SPOT_OPENED); thanh toán theo cách đã chọn khi vào hàng chờ (giả lập hoặc trả tại sân), áp dụng chính sách hủy của spec 080. Hàng chờ tự đóng khi buổi bắt đầu hoặc bị hủy; người còn trong hàng chờ được báo. Có xử lý mất mạng, bấm nhiều lần, trạng thái rỗng. Không bao gồm: danh sách, đăng ký, vé, hủy và đánh giá buổi (spec 080); chủ sân mở và sửa buổi (spec 112); gửi thông báo (spec 100). Dữ liệu: dropInSessions, dropInSessions/{id}/registrations/{uid} (status WAITLISTED, waitlistedAt) theo docs/design/database/README.md mục 4.9."

**Nhóm tính năng**: [08 — Đặt lịch vãng lai](../../docs/features/08-drop-in/README.md) · **Phụ trách**: C · **Liên quan**: [spec 080](../080-drop-in-session/spec.md) (buổi, đăng ký, vé, chính sách hủy), spec 100 (thông báo), spec 112 (chủ sân sửa sức chứa, hủy buổi)

## Clarifications

### Session 2026-10-06

- Q: Khi có chỗ trống, người chờ được đẩy lên trở thành đăng ký chính thức ngay hay được giữ chỗ để tự bấm xác nhận? → A: Thành đăng ký chính thức ngay và nhận vé QR luôn; không muốn đi thì hủy theo chính sách.
- Q: Khi người đứng đầu hàng chờ đi nhóm đông hơn số chỗ vừa trống thì xử lý thế nào? → A: Bỏ qua nhưng giữ nguyên vị trí của họ, xét người tiếp theo có số người vừa với số chỗ trống.
- Q: Người được đẩy lên sát giờ (trong vòng 4 giờ trước giờ bắt đầu) có quyền lợi gì khi muốn hủy? → A: Được hủy hoàn 100% trong 30 phút kể từ lúc được đẩy lên; sau đó áp dụng chính sách hủy của spec 080.
- Q: Hàng chờ có giới hạn số lượt không và ngừng đẩy người lên từ lúc nào? → A: Không giới hạn số lượt; hàng chờ đóng và ngừng đẩy lên 30 phút trước giờ bắt đầu, chỗ trống sau mốc đó mở cho đăng ký thường.
- Q: Nếu một người đang chờ nhiều buổi trùng giờ và được đẩy lên một buổi thì các lượt chờ còn lại xử lý thế nào? → A: Hệ thống tự cho người đó rời hàng chờ của các buổi trùng giờ khác (không mất phí) trong cùng lần đẩy lên.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vào hàng chờ khi buổi đã đủ người (Priority: P1)

Người chơi muốn tham gia một buổi đã "Đã đủ người". Ở chi tiết buổi, người chơi bấm "Vào hàng chờ", chọn số người và cách thanh toán (giống màn hình đăng ký của spec 080), rồi xác nhận. Không có tiền nào bị trừ lúc này. App hiện "Bạn đang ở vị trí thứ N trong hàng chờ" và lượt chờ xuất hiện trong "Lượt vãng lai của tôi".

**Why this priority**: Buổi vãng lai phổ biến thường kín chỗ; không có hàng chờ thì người chơi phải tự canh xem có ai hủy. Đây là cửa vào của toàn bộ spec 081.

**Independent Test**: Với một buổi đã đủ người, 3 tài khoản lần lượt vào hàng chờ; mỗi tài khoản thấy đúng vị trí 1, 2, 3; buổi vẫn "Đã đủ người" và số chỗ không đổi.

**Acceptance Scenarios**:

1. **Given** buổi đã đủ người, chưa bắt đầu và người chơi chưa có đăng ký hay lượt chờ ở buổi này, **When** mở chi tiết buổi, **Then** thấy nút "Vào hàng chờ" kèm số lượt đang chờ.
2. **Given** người chơi ở màn hình vào hàng chờ, **When** chọn 2 người, "Trả tại sân" và xác nhận, **Then** lượt chờ được tạo với vị trí theo thứ tự thời gian vào hàng chờ, app ghi rõ "Chưa giữ chỗ, chưa thanh toán; bạn sẽ được tự động đăng ký khi có đủ chỗ".
3. **Given** người chơi chọn "Thanh toán ngay (giả lập)" khi vào hàng chờ, **When** xác nhận, **Then** không có khoản nào được ghi nhận cho đến khi được đẩy lên; app báo trước số tiền sẽ ghi nhận (giá mỗi lượt × số người).
4. **Given** người chơi đang có lượt chờ, **When** mở chi tiết buổi hoặc lượt chờ, **Then** thấy vị trí hiện tại và tổng số người đang chờ phía trước; vị trí cập nhật khi người phía trước rời hàng chờ hoặc được đẩy lên.
5. **Given** buổi vẫn còn chỗ, **When** người chơi (hoặc yêu cầu không qua app) cố vào hàng chờ, **Then** hệ thống từ chối và app đưa người chơi về luồng đăng ký thường của spec 080.
6. **Given** người chơi bấm "Xác nhận" nhiều lần hoặc thử lại sau khi mất mạng, **When** hệ thống xử lý, **Then** chỉ có một lượt chờ, vị trí giữ theo lần ghi thành công đầu tiên.

---

### User Story 2 - Tự động được đẩy lên khi có chỗ (Priority: P1)

Khi một người đã đăng ký hủy lượt, hoặc chủ sân tăng sức chứa, hệ thống tự lấy người chờ lâu nhất có số người vừa với số chỗ vừa trống và chuyển lượt chờ đó thành đăng ký chính thức. Người được đẩy lên nhận ngay vé QR (như spec 080) và thông báo "Đã có chỗ cho bạn".

**Why this priority**: Đây là lý do tồn tại của hàng chờ và là bài toán toàn vẹn dữ liệu (không vượt chỗ, không đẩy trùng) mà constitution nguyên tắc III bắt buộc.

**Independent Test**: Buổi 10 chỗ đã đủ, hàng chờ gồm A (2 người), B (1 người). Một lượt 1 người hủy: B được đẩy lên (A không vừa), A giữ vị trí 1. Một lượt 2 người hủy tiếp: A được đẩy lên. Số người đã đăng ký luôn bằng 10.

**Acceptance Scenarios**:

1. **Given** buổi đã đủ người và hàng chờ có người, **When** một lượt 2 người hủy, **Then** trong cùng một lần xử lý hệ thống đẩy lên những người chờ theo thứ tự vào hàng chờ cho đến khi hết chỗ trống hoặc không còn ai vừa; số người đã đăng ký không bao giờ vượt sức chứa.
2. **Given** người chờ đầu tiên có số người lớn hơn số chỗ vừa trống, **When** hệ thống đẩy lên, **Then** người đó giữ nguyên vị trí và hệ thống xét người tiếp theo có số người vừa với số chỗ trống.
3. **Given** một người được đẩy lên với "Thanh toán ngay (giả lập)", **When** đẩy lên thành công, **Then** lượt chuyển thành đăng ký chính thức, ghi nhận đã thanh toán, có vé QR như một đăng ký của spec 080; với "Trả tại sân" thì vé ghi "Thanh toán tại sân".
4. **Given** người chơi vừa được đẩy lên, **When** mở app, **Then** lượt trong "Lượt vãng lai của tôi" đã chuyển từ "Đang chờ" sang "Sắp tới" kèm nhãn "Được nhận từ hàng chờ".
5. **Given** chủ sân tăng sức chứa thêm 3 chỗ (spec 112), **When** thay đổi được lưu, **Then** hệ thống đẩy lên người chờ theo cùng quy tắc cho tối đa 3 chỗ đó.
6. **Given** hai lượt hủy xảy ra gần như cùng lúc, **When** hệ thống xử lý, **Then** mỗi người chờ được đẩy lên tối đa một lần và tổng số người đã đăng ký vẫn không vượt sức chứa.
7. **Given** người chơi đang chờ hai buổi trùng giờ, **When** được đẩy lên một buổi, **Then** lượt chờ ở buổi trùng giờ còn lại tự chuyển sang "Đã rời hàng chờ" (không mất phí) và người chơi được báo.
8. **Given** hàng chờ có người nhưng không ai vừa với số chỗ trống, **When** hủy xong, **Then** chỗ trống được mở cho đăng ký thường của spec 080 và buổi hiện lại "Còn X chỗ".

---

### User Story 3 - Rời hàng chờ (Priority: P2)

Người chơi không còn muốn chờ thì mở lượt chờ và bấm "Rời hàng chờ". Không mất phí vì chưa có khoản nào được ghi nhận.

**Why this priority**: Cần để hàng chờ không đầy người không còn muốn đi, nhưng ít dùng hơn vào hàng chờ và được đẩy lên.

**Independent Test**: Hàng chờ có A, B, C; B rời hàng chờ; C thấy vị trí đổi từ 3 thành 2; B không còn lượt chờ và không bị ghi khoản nào.

**Acceptance Scenarios**:

1. **Given** người chơi đang có lượt chờ, **When** bấm "Rời hàng chờ" và xác nhận, **Then** lượt chờ chuyển sang "Đã rời hàng chờ", người chơi không bị ghi khoản phí nào, và vị trí của những người phía sau tăng lên.
2. **Given** người chơi vừa được đẩy lên trong lúc đang bấm "Rời hàng chờ", **When** hệ thống xử lý, **Then** hệ thống từ chối thao tác rời hàng chờ, báo "Bạn đã được nhận vào buổi" và hướng người chơi sang hủy đăng ký theo chính sách của spec 080.
3. **Given** người chơi đã rời hàng chờ, **When** buổi vẫn đủ người và còn hơn 30 phút nữa mới bắt đầu, **Then** người chơi được vào hàng chờ lại nhưng xếp ở cuối hàng.
4. **Given** người khác (không phải chủ lượt chờ), **When** cố rời hàng chờ thay người đó bằng bất kỳ cách nào, **Then** hệ thống từ chối.

---

### User Story 4 - Hàng chờ đóng trước giờ bắt đầu hoặc khi buổi bị hủy (Priority: P2)

Khi còn 30 phút nữa buổi bắt đầu mà người chơi vẫn chưa được đẩy lên, hoặc khi chủ sân hủy buổi, lượt chờ kết thúc và người chơi được báo để tìm buổi khác.

**Why this priority**: Tránh người chơi chờ một buổi đã qua; cần cho trạng thái rõ ràng trong lịch sử nhưng không chặn luồng chính.

**Independent Test**: Với một buổi có 2 lượt chờ, cho thời điểm đến mốc 30 phút trước giờ bắt đầu: cả 2 lượt chuyển sang "Không được nhận chỗ" và không bị ghi khoản nào. Với một buổi khác bị chủ sân hủy: lượt chờ chuyển sang "Buổi bị hủy".

**Acceptance Scenarios**:

1. **Given** còn 30 phút nữa buổi bắt đầu, **When** người chơi vẫn còn trong hàng chờ, **Then** lượt chờ chuyển sang "Không được nhận chỗ", không bị ghi khoản phí nào, và hệ thống phát sự kiện để báo người chơi.
2. **Given** chủ sân hủy buổi (spec 112), **When** hủy xong, **Then** mọi lượt chờ của buổi chuyển sang "Buổi bị hủy" và người chơi được báo.
3. **Given** còn 30 phút hoặc ít hơn nữa buổi bắt đầu, hoặc buổi đã bị hủy, **When** ai đó cố vào hàng chờ, **Then** hệ thống từ chối; nếu sau mốc này có chỗ trống thì chỗ đó mở cho đăng ký thường của spec 080.
4. **Given** các lượt chờ đã kết thúc, **When** người chơi mở tab "Đã qua" trong "Lượt vãng lai của tôi", **Then** thấy chúng với nhãn "Đã rời hàng chờ", "Không được nhận chỗ" hoặc "Buổi bị hủy".

---

### Edge Cases

- **Người chờ đầu không vừa chỗ trống:** giữ nguyên vị trí, người phía sau vừa chỗ được đẩy lên trước; chỗ trống không ai vừa thì mở cho đăng ký thường.
- **Chủ sân giảm sức chứa:** không ảnh hưởng hàng chờ; buổi vẫn đủ người (việc xử lý người đã đăng ký vượt sức chứa mới thuộc spec 112).
- **Được đẩy lên sát giờ:** lượt được đẩy lên trong vòng 4 giờ trước giờ bắt đầu vẫn được hủy hoàn 100% trong vòng 30 phút kể từ lúc được đẩy lên; sau đó áp dụng chính sách hủy của spec 080.
- **Trùng giờ:** người chơi được vào hàng chờ của nhiều buổi trùng giờ (app cảnh báo giống spec 080); khi được đẩy lên một buổi thì tự rời hàng chờ các buổi trùng giờ còn lại. Người đã có đăng ký chính thức ở một buổi vẫn vào được hàng chờ buổi trùng giờ khác (kèm cảnh báo).
- **Chỗ trống sát giờ:** từ mốc 30 phút trước giờ bắt đầu, hàng chờ đã đóng nên chỗ trống do người hủy mở cho đăng ký thường đến lúc buổi bắt đầu.
- **Một người, một buổi:** mỗi người có tối đa một đăng ký hoặc một lượt chờ còn hiệu lực ở mỗi buổi, không thể vừa đăng ký vừa chờ.
- **Chủ sân vào hàng chờ buổi của chính mình:** không được phép (giống spec 080).
- **Giá thay đổi khi đang chờ:** số tiền khi được đẩy lên tính theo giá tại lúc vào hàng chờ (đã hiện cho người chơi).
- **Người chờ xóa tài khoản:** lượt chờ bị xóa khỏi hàng chờ, người phía sau lên vị trí.
- **Mất mạng khi được đẩy lên:** việc đẩy lên xảy ra ở phía server, không phụ thuộc thiết bị của người chơi; lần mở app tiếp theo hiện vé.
- **Thứ tự hàng chờ:** tính theo thời điểm vào hàng chờ do server ghi, không theo giờ điện thoại.

## Requirements *(mandatory)*

### Functional Requirements

**Vào hàng chờ**

- **FR-001**: Người chơi đã đăng nhập PHẢI vào được hàng chờ của một buổi khi buổi đã đủ người, còn hơn 30 phút nữa mới bắt đầu, chưa bị hủy và thuộc cơ sở đang hoạt động; hệ thống PHẢI từ chối vào hàng chờ khi buổi còn chỗ (người chơi đăng ký thường theo spec 080).
- **FR-002**: Khi vào hàng chờ, người chơi PHẢI chọn số người (1–4, giới hạn như spec 080) và cách thanh toán ("Thanh toán ngay (giả lập)" hoặc "Trả tại sân"); app PHẢI hiển thị số tiền sẽ ghi nhận khi được đẩy lên và ghi rõ chưa có chỗ, chưa trừ tiền.
- **FR-003**: Mỗi người chơi có tối đa một đăng ký hoặc một lượt chờ còn hiệu lực ở mỗi buổi; bấm nhiều lần hoặc thử lại khi mất mạng KHÔNG ĐƯỢC tạo lượt chờ trùng.
- **FR-004**: Thứ tự hàng chờ PHẢI theo thời điểm vào hàng chờ do server ghi (ai vào trước đứng trước); người dùng KHÔNG ĐƯỢC tự ghi thời điểm, vị trí hay trạng thái lượt chờ.
- **FR-005**: Người chơi PHẢI thấy vị trí hiện tại của mình (thứ N) và số lượt đang chờ của buổi; vị trí PHẢI cập nhật khi người phía trước rời hàng chờ hoặc được đẩy lên trong lúc màn hình đang mở.
- **FR-006**: Hàng chờ không giới hạn số lượt chờ; số lượt chờ hiển thị công khai nhưng danh tính người chờ chỉ chủ cơ sở và admin xem được (giống quy tắc danh sách người đăng ký của spec 080).

**Tự động đẩy lên**

- **FR-007**: Mỗi khi buổi có chỗ trống mới (người đăng ký hủy, chủ sân tăng sức chứa), hệ thống PHẢI, trong cùng bước nguyên tử với thao tác tạo ra chỗ trống, đẩy lên lần lượt các lượt chờ theo thứ tự hàng chờ, bỏ qua (nhưng giữ vị trí) những lượt có số người lớn hơn số chỗ còn trống, cho đến khi hết chỗ hoặc không còn lượt nào vừa. Việc đẩy lên chỉ diễn ra trước mốc 30 phút trước giờ bắt đầu.
- **FR-008**: Tổng số người đã đăng ký KHÔNG ĐƯỢC vượt sức chứa và mỗi lượt chờ được đẩy lên tối đa một lần, kể cả khi nhiều lượt hủy hoặc thay đổi sức chứa xảy ra cùng lúc.
- **FR-009**: Khi hàng chờ có lượt vừa với chỗ trống, chỗ đó PHẢI dành cho người chờ trước; người chưa vào hàng chờ chỉ đăng ký được những chỗ còn lại sau khi đã xét hết hàng chờ.
- **FR-010**: Lượt được đẩy lên PHẢI trở thành đăng ký chính thức giống hệt đăng ký của spec 080 (vé QR, mã vé, trạng thái thanh toán, chính sách hủy) với số tiền = giá mỗi lượt tại lúc vào hàng chờ × số người, kèm nhãn "Được nhận từ hàng chờ". Với "Thanh toán ngay (giả lập)" khoản thanh toán được ghi nhận tại lúc đẩy lên. Không có bước giữ chỗ chờ người chơi xác nhận.
- **FR-010a**: Khi một lượt chờ được đẩy lên, hệ thống PHẢI trong cùng lần xử lý chuyển các lượt chờ khác của cùng người chơi ở những buổi trùng giờ sang "Đã rời hàng chờ" (không mất phí), và phát sự kiện để báo người chơi.
- **FR-011**: Lượt được đẩy lên trong vòng 4 giờ trước giờ bắt đầu PHẢI được hủy với hoàn 100% trong 30 phút kể từ lúc được đẩy lên; ngoài khoảng đó áp dụng chính sách hủy của spec 080.

**Rời hàng chờ và đóng hàng chờ**

- **FR-012**: Chủ lượt chờ PHẢI rời hàng chờ được bất cứ lúc nào trước khi được đẩy lên, không mất phí; chỉ chủ lượt chờ được rời (kiểm tra ở phía server). Rời rồi vào lại thì xếp cuối hàng.
- **FR-013**: Thao tác rời hàng chờ và việc đẩy lên của cùng một lượt KHÔNG ĐƯỢC cùng thành công; nếu lượt đã được đẩy lên, thao tác rời bị từ chối và người chơi được hướng sang hủy đăng ký theo spec 080.
- **FR-014**: Hàng chờ PHẢI đóng 30 phút trước giờ bắt đầu: mọi lượt chờ còn lại chuyển sang "Không được nhận chỗ" và chỗ trống sau mốc đó mở cho đăng ký thường của spec 080; khi buổi bị hủy, chuyển sang "Buổi bị hủy". Không lượt chờ nào bị ghi khoản phí trong hai trường hợp này.

**Hiển thị và liên kết**

- **FR-015**: Lượt chờ PHẢI xuất hiện trong "Lượt vãng lai của tôi" (spec 080): tab "Sắp tới" với nhãn "Đang chờ – vị trí N"; tab "Đã qua" với nhãn "Đã rời hàng chờ", "Không được nhận chỗ" hoặc "Buổi bị hủy".
- **FR-016**: Mỗi màn hình liên quan (chi tiết buổi phần hàng chờ, lượt chờ) PHẢI có đủ 4 trạng thái: đang tải, có dữ liệu, rỗng, lỗi kèm nút thử lại; mất mạng không crash và giữ nguyên lựa chọn đang nhập.
- **FR-017**: Hệ thống PHẢI phát sinh sự kiện để nhóm thông báo (spec 100) gửi: vào hàng chờ thành công, được đẩy lên ("Đã có chỗ cho bạn"), hàng chờ đóng mà chưa được nhận chỗ, buổi bị hủy khi đang chờ. Nội dung và cách gửi thuộc spec 100.

### Key Entities

- **Lượt chờ (Waitlist entry)**: Một đăng ký ở trạng thái chờ của spec 080 (dùng chung loại dữ liệu, mỗi người tối đa một mỗi buổi). Gồm: số người, cách thanh toán đã chọn, giá mỗi lượt tại lúc vào hàng chờ, thời điểm vào hàng chờ (server ghi, quyết định thứ tự), trạng thái (đang chờ, được đẩy lên → thành đăng ký chính thức, đã rời, không được nhận chỗ, buổi bị hủy), thời điểm được đẩy lên.
- **Buổi vãng lai** (spec 080): sức chứa, số người đã đăng ký, số lượt đang chờ; trạng thái "đã đủ người" là điều kiện để vào hàng chờ.
- **Đăng ký** (spec 080): kết quả của việc đẩy lên; mang nhãn "Được nhận từ hàng chờ".

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người chơi vào được hàng chờ trong dưới 30 giây kể từ khi mở chi tiết một buổi đã đủ người.
- **SC-002**: Khi một lượt hủy, người chờ đủ điều kiện có vé trong "Lượt vãng lai của tôi" trong vòng 10 giây.
- **SC-003**: Trong kịch bản 5 lượt hủy và 10 người chờ xảy ra đồng thời, số người đã đăng ký không bao giờ vượt sức chứa, không lượt chờ nào được đẩy lên hai lần, và thứ tự đẩy lên đúng thứ tự hàng chờ (bỏ qua lượt không vừa) trong 100% lần chạy.
- **SC-004**: 100% lần thử tự ghi vị trí/thời điểm/trạng thái lượt chờ, vào hàng chờ khi buổi còn chỗ, rời hàng chờ thay người khác, hoặc đọc danh tính người chờ khác đều bị từ chối, kể cả khi gửi không qua app.
- **SC-005**: 0 lượt chờ bị ghi khoản phí khi rời hàng chờ (kể cả tự rời do trùng giờ), khi hàng chờ đóng hoặc khi buổi bị hủy.
- **SC-006**: Ít nhất 4/5 người thử (thành viên nhóm khác) hiểu được mình đang ở vị trí nào và điều gì sẽ xảy ra khi có chỗ, mà không cần hướng dẫn.

## Assumptions

- **Đẩy lên thành đăng ký ngay**, bỏ qua nhưng giữ vị trí cho nhóm không vừa, cửa sổ hủy 30 phút khi được đẩy lên sát giờ, hàng chờ không giới hạn và đóng 30 phút trước giờ bắt đầu, tự rời hàng chờ trùng giờ: đều đã chốt ở Clarifications.
- Mốc đóng hàng chờ 30 phút cần báo D để màn hình chủ sân (spec 112) hiển thị đúng; tác vụ đóng hàng chờ đúng mốc chạy ở phía server.
- Lượt chờ dùng chung loại dữ liệu đăng ký của spec 080 (trạng thái "chờ"); thao tác hủy và mở rộng sức chứa của spec 080/112 phải gọi cùng quy tắc đẩy lên của spec này.
- Thanh toán giả lập luôn thành công và hoàn tiền chỉ đổi trạng thái (đã chốt ở Clarifications spec 080); vì vậy việc ghi nhận thanh toán lúc đẩy lên không thể thất bại.
- Tăng/giảm sức chứa và hủy buổi thuộc spec 112 (D); spec này định nghĩa phản ứng của hàng chờ.
- Loại thông báo ở FR-017 (`DROPIN_SPOT_OPENED` và các loại cho vào hàng chờ, hàng chờ đóng) phải gửi cho D trước 14/10 cùng danh sách của spec 080.
- Khách chưa đăng nhập phải đăng nhập (spec 010) khi bấm "Vào hàng chờ".
