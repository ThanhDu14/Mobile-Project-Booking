# Feature Specification: Đặt sân cố định theo tuần

**Feature Branch**: `feature/04-booking`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi đã đăng nhập đặt sân cầu lông cố định theo tuần (lịch định kỳ cho nhóm/CLB) thay vì đặt từng buổi lẻ. Từ màn hình đặt sân hoặc chi tiết sân, người chơi chọn chế độ 'Đặt cố định theo tuần'. Người chơi chọn: một hoặc nhiều thứ trong tuần (ví dụ Thứ Ba và Thứ Năm); khung giờ cố định cho mỗi buổi, độ dài và số slot liên tiếp theo quy tắc của spec 040 (slot 60 phút, mốc giờ tròn); sân con ưu tiên; chu kỳ từ 4 đến 12 tuần, bắt đầu từ tuần hiện tại hoặc tuần kế tiếp. Hệ thống kiểm tra khả dụng toàn bộ chu kỳ rồi hiển thị bảng tổng hợp: danh sách các buổi, số buổi, tổng tiền tạm tính. Bấm 'Tiếp tục' sang màn xác nhận: chi tiết từng buổi (ngày, sân, giờ, giá từng buổi), tổng tiền cả chu kỳ; đồng thời giữ chỗ tạm 5 phút có đồng hồ đếm ngược cho tất cả buổi trong chu kỳ. Các buổi thuộc cùng một đơn cố định được gắn chung một mã nhóm để quản lý. Xử lý xung đột: Nếu một hoặc vài tuần bị trùng (slot đã có người đặt hoặc bị sân khóa): hiển thị rõ danh sách ngày xung đột và cho người chơi chọn một trong hai cách: (1) tự động đổi sang sân khác còn trống cùng khung giờ của ngày đó; (2) bỏ qua buổi bị trùng, chỉ đặt các tuần còn trống, tổng tiền giảm tương ứng. Khi tự động đổi sân: ưu tiên sân cùng loại và cùng mức giá. Nếu sân được đổi có giá khác, màn hình xác nhận phải hiển thị chênh lệch giá của buổi đó để người chơi đồng ý trước khi tiếp tục. Nếu ngày đó không còn sân nào trống và người chơi không đồng ý bỏ qua: chặn luồng đặt cố định để người chơi chọn lại thứ/khung giờ khác. Sau khi bỏ các buổi bị trùng, nếu số buổi còn lại ít hơn 4 buổi thì không tạo được đơn cố định; hệ thống thông báo và đề nghị người chơi chọn lại thứ/khung giờ hoặc chuyển sang đặt lẻ. Giữ chỗ: Giữ chỗ theo nguyên tắc 'tất cả hoặc không': nếu trong 5 phút có bất kỳ buổi nào bị người khác chiếm, hủy giữ toàn bộ và thông báo rõ lý do. Người chơi bấm nhiều lần chỉ tạo một đơn, không tạo đơn trùng. Tổng số slot giữ chỗ trong một đơn cố định có giới hạn tối đa; con số cụ thể sẽ chốt ở bước lập kế hoạch kỹ thuật. Trường hợp lỗi và trạng thái rỗng: Mất mạng: báo lỗi và có nút thử lại. Cơ sở không mở đặt cố định: thông báo và không cho vào luồng. Toàn bộ chu kỳ không thể xếp lịch: thông báo và gợi ý chọn lại. Người chưa đăng nhập bấm 'Tiếp tục': yêu cầu đăng nhập, sau đó giữ nguyên cấu hình đã chọn. Tiêu chí thành công: Kiểm tra khả dụng toàn bộ chu kỳ 12 tuần trong vòng 2 giây trên mạng di động 4G. Giữ chỗ toàn bộ chu kỳ hoặc thành công hoàn toàn, hoặc hoàn tác sạch, không để lại trạng thái dở dang. Không có đơn trùng lặp. Ranh giới: Spec này kết thúc khi các đơn cố định ở trạng thái HOLD, có chung mã nhóm, và chuyển sang màn thanh toán/đặt cọc (spec 050). Không bao gồm: chọn phương thức thanh toán, chính sách đặt cọc theo chu kỳ, áp voucher (spec 050); dời lịch từng buổi lẻ, hủy nguyên chu kỳ, check-in từng buổi (spec 060). Ràng buộc: Tuân thủ constitution (nguyên tắc II và III); đồng bộ với spec 040. Cần làm rõ: Chính sách đặt cọc/trả trước cho đơn cố định (phối hợp spec 050). Chủ sân có được giới hạn số tuần tối đa cho đặt cố định không (phối hợp spec 111). Cách xử lý các thứ đã qua khi bắt đầu từ tuần hiện tại."

**Nhóm tính năng**: [04 — Đặt sân](../../docs/features/04-booking/README.md) · **Phụ trách**: B · **Liên quan**: [spec 010](../010-account-auth/spec.md) (đăng nhập / điều hướng), [spec 030](../030-court-detail/spec.md) (chi tiết sân), [spec 040](../040-booking-slot-grid/spec.md) (lưới khung giờ đặt sân & giữ chỗ), [spec 050](../050-payment-voucher/spec.md) (thanh toán và đặt cọc), [spec 060](../060-my-bookings/spec.md) (quản lý lịch đặt định kỳ), [spec 111](../111-owner-venue-setup/spec.md) (cấu hình cơ sở, sân con, bảng giá)

## Clarifications

### Session 2026-10-06

- Q: Khi người chơi chọn bắt đầu từ "Tuần hiện tại" nhưng một số thứ được chọn trong tuần này đã trôi qua, hệ thống xử lý như thế nào? → A: Tự động bắt đầu từ buổi hợp lệ tiếp theo ngay trong tuần này; các buổi đã trôi qua trong tuần hiện tại không tính vào đơn và chu kỳ kéo dài đủ số tuần đã chọn tính từ ngày bắt đầu thực tế.
- Q: Đối với đơn cố định dài hạn, hệ thống áp dụng hình thức thanh toán/đặt cọc như thế nào (phối hợp spec 050)? → A: Cho phép đặt cọc theo tỷ lệ phần trăm (ví dụ: cọc 30% tổng giá trị chu kỳ) hoặc trả trước toàn bộ 100% tùy theo người chơi lựa chọn tại màn hình thanh toán của spec 050.
- Q: Giới hạn chu kỳ tối đa (4–12 tuần) là cố định hay chủ sân được tùy chỉnh (phối hợp spec 111)? → A: Khung chuẩn từ 4 đến 12 tuần được ấn định cố định trên toàn hệ thống; chủ sân chỉ có quyền BẬT hoặc TẮT tính năng nhận đặt cố định cho từng cơ sở.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Thiết lập cấu hình lịch đặt cố định theo tuần (Priority: P1)

Từ màn hình đặt sân của một cơ sở, người chơi chọn tab "Đặt cố định theo tuần". Người chơi chọn một hoặc nhiều thứ trong tuần (ví dụ Thứ Ba và Thứ Năm), chọn khung giờ cố định cho mỗi buổi (độ dài 60 phút hoặc nhiều slot liên tiếp, mốc giờ tròn theo quy tắc spec 040), chọn sân con ưu tiên (ví dụ Sân 1), và chọn chu kỳ đặt (từ 4 đến 12 tuần, bắt đầu từ tuần hiện tại hoặc tuần kế tiếp). Sau khi người chơi hoàn tất cấu hình, hệ thống tự động kiểm tra khả dụng của toàn bộ các buổi trong chu kỳ và hiển thị bảng tổng hợp lịch: danh sách ngày các buổi, số lượng buổi, và tổng tiền tạm tính theo bảng giá của từng ngày/giờ.

**Why this priority**: Đây là luồng nghiệp vụ cốt lõi mở đầu cho việc đặt lịch định kỳ. Người chơi cần thiết lập cấu hình mong muốn và biết trước tổng quan toàn bộ lịch chơi cùng chi phí dự tính.

**Independent Test**: Mở một cơ sở sân, chọn chế độ đặt cố định; chọn Thứ Ba từ 18:00–20:00 (2 slot), Sân 1, chu kỳ 8 tuần bắt đầu từ tuần kế tiếp; kiểm tra hệ thống hiển thị bảng tổng hợp đủ 8 buổi chơi (mỗi buổi 2 tiếng) và tổng tiền tạm tính chính xác.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình đặt sân của một cơ sở, **When** bấm chuyển sang chế độ "Đặt cố định theo tuần", **Then** hệ thống hiển thị giao diện cấu hình gồm: chọn thứ trong tuần, chọn khung giờ, chọn sân con ưu tiên, và chọn chu kỳ (4–12 tuần).
2. **Given** người chơi chọn các thứ trong tuần và khung giờ, **When** chọn khung giờ bắt đầu và kết thúc, **Then** hệ thống áp dụng quy tắc slot 60 phút và mốc giờ tròn theo spec 040, cho phép chọn tối đa 3 khung giờ liên tiếp trong một buổi (tối đa 3 giờ/buổi).
3. **Given** người chơi cấu hình đầy đủ thông tin chu kỳ (ví dụ: Thứ Ba và Thứ Năm, 19:00–20:00, Sân 1, 4 tuần), **When** hệ thống kiểm tra và tất cả các buổi đều còn trống, **Then** hiển thị bảng tổng hợp gồm danh sách 8 buổi với ngày cụ thể, sân con, khung giờ và tổng số tiền tạm tính; nút "Tiếp tục" được kích hoạt.
4. **Given** cơ sở chưa kích hoạt tính năng đặt cố định hoặc chủ sân tạm ngưng nhận lịch dài hạn, **When** người chơi vào màn hình đặt cố định, **Then** hệ thống hiển thị thông báo "Cơ sở hiện không áp dụng đặt sân cố định" và hướng dẫn chuyển sang đặt lẻ theo ngày (spec 040).
5. **Given** người chơi chưa chọn đủ thông tin bắt buộc (chưa chọn thứ hoặc chưa chọn giờ), **When** xem giao diện, **Then** nút "Tiếp tục" ở trạng thái vô hiệu hóa.

---

### User Story 2 - Xử lý xung đột khi có tuần bị trùng lịch (Priority: P1)

Khi hệ thống kiểm tra khả dụng chu kỳ mà phát hiện có một hoặc vài tuần bị trùng slot (do đã có người chơi khác đặt trước, hoặc chủ sân đã khóa slot để bảo trì/tổ chức sự kiện), ứng dụng không hủy bỏ ngay lập tức mà hiển thị rõ ràng danh sách các ngày bị xung đột. Hệ thống cung cấp cho người chơi 2 phương án xử lý linh hoạt: (1) Tự động đổi sang sân con khác còn trống trong cùng khung giờ của ngày đó (ưu tiên cùng loại sân và đồng giá; nếu khác giá thì thông báo rõ chênh lệch); hoặc (2) Bỏ qua buổi bị trùng (chỉ đặt các tuần còn trống, tổng số buổi và tổng tiền được giảm trừ tương ứng). Nếu ngày đó kín tất cả các sân và người chơi không đồng ý bỏ qua, luồng đặt cố định bị chặn để người chơi chọn lại. Sau khi bỏ buổi trùng, nếu số buổi còn lại ít hơn 4 buổi, hệ thống chặn tạo đơn cố định và đề nghị người chơi chuyển sang đặt lẻ.

**Why this priority**: Trong thực tế đặt sân dài hạn 1–3 tháng, việc toàn bộ các tuần đều trống 100% trên cùng một sân con là rất khó. Cơ chế giải quyết xung đột linh hoạt này là mấu chốt quyết định tỷ lệ đặt lịch thành công của khách hàng.

**Independent Test**: Tạo kịch bản Sân 1 bị trùng ở tuần thứ 3 trong chu kỳ 8 tuần; thực hiện kiểm tra: (a) Chọn phương án đổi sang Sân 2 cùng giờ thành công; (b) Chọn phương án bỏ qua tuần thứ 3, đơn còn 7 buổi và tổng tiền tự động trừ đi 1 buổi; (c) Tạo kịch bản chu kỳ 4 tuần bị trùng 2 tuần, sau khi bỏ chỉ còn 2 buổi, app chặn tạo đơn cố định và thông báo rõ lý do.

**Acceptance Scenarios**:

1. **Given** người chơi chọn chu kỳ 8 tuần nhưng tuần thứ 3 slot Sân 1 đã có người đặt, **When** hệ thống kiểm tra khả dụng, **Then** hiển thị cảnh báo "Phát hiện 1 buổi bị xung đột lịch" kèm chi tiết ngày bị trùng và đưa ra 2 lựa chọn: "Đổi sang sân khác" hoặc "Bỏ qua buổi này".
2. **Given** người chơi chọn "Đổi sang sân khác" cho buổi bị trùng và Sân 2 còn trống cùng khung giờ có cùng mức giá, **When** hệ thống đổi sân, **Then** buổi đó được cập nhật thành Sân 2 trên danh sách tổng hợp mà không làm thay đổi đơn giá.
3. **Given** sân con thay thế có mức giá khác (ví dụ: Sân VIP chênh lệch +20.000đ/giờ), **When** người chơi xem màn hình giải quyết xung đột, **Then** hệ thống hiển thị rõ mức giá chênh lệch và chỉ áp dụng đổi sân khi người chơi bấm "Xác nhận chấp nhận chênh lệch giá".
4. **Given** người chơi chọn phương án "Bỏ qua buổi bị trùng", **When** bấm xác nhận, **Then** buổi bị trùng bị xóa khỏi danh sách, số lượng buổi giảm 1 và tổng tiền tự động trừ đi giá của buổi đó.
5. **Given** sau khi bỏ các buổi bị trùng, số buổi còn lại trong đơn cố định nhỏ hơn 4 buổi (ví dụ chu kỳ 4 tuần bị trùng 2 tuần, còn lại 2 buổi), **When** người chơi chuẩn bị tiếp tục, **Then** hệ thống hiển thị thông báo "Đơn đặt cố định yêu cầu tối thiểu 4 buổi chơi. Vui lòng chọn khung giờ khác hoặc chuyển sang đặt sân lẻ", không cho phép tiếp tục luồng cố định.
6. **Given** tại ngày bị trùng không còn bất kỳ sân con nào khác còn trống trong cùng khung giờ, **When** hiển thị thông báo xung đột, **Then** tùy chọn "Đổi sang sân khác" bị vô hiệu hóa, chỉ cho phép chọn "Bỏ qua buổi này" hoặc "Chọn lại khung giờ khác".

---

### User Story 3 - Giữ chỗ tạm thời 5 phút toàn chu kỳ với nguyên tắc "Tất cả hoặc không" (Priority: P1)

Sau khi giải quyết xong các xung đột (nếu có), người chơi bấm "Tiếp tục" để sang màn hình xác nhận đơn cố định. Màn hình hiển thị chi tiết từng buổi (ngày, sân, khung giờ, đơn giá từng buổi), tổng số buổi, tổng tiền toàn bộ chu kỳ, và tiến hành giữ chỗ tạm thời trong vòng 5 phút (có đồng hồ đếm ngược từ 05:00) cho toàn bộ các slot trong chu kỳ. Quá trình giữ chỗ tuân thủ nguyên tắc "Tất cả hoặc không" (All-or-Nothing): nếu có bất kỳ slot nào trong toàn bộ chu kỳ vừa bị người khác chiếm mất trong quá trình xác nhận, toàn bộ các slot còn lại KHÔNG được giữ, hệ thống báo lỗi rõ ràng và hoàn tác sạch sẽ trạng thái. Toàn bộ các buổi trong đơn cố định được gắn chung một mã nhóm (`recurringGroupId`) để liên kết quản lý. Người chơi bấm nhiều lần hoặc thử lại khi mất mạng không tạo đơn trùng lặp.

**Why this priority**: Đảm bảo tính toàn vẹn dữ liệu đặt lịch cho giao dịch lớn gồm nhiều slot liên tuần theo Nguyên tắc III của Constitution, ngăn chặn tình trạng đặt thành công dở dang gây thiệt hại quyền lợi cho khách hàng.

**Independent Test**: Xác nhận một đơn cố định 8 buổi; kiểm tra trên cơ sở dữ liệu toàn bộ các slot của 8 buổi đều chuyển sang trạng thái đang giữ (`HOLD`), có chung mã nhóm `recurringGroupId` và cùng mốc thời gian hết hạn 5 phút. Giả lập một slot bị tranh chấp trước khi bấm tiếp tục, kiểm tra không có slot nào bị giữ dở dang.

**Acceptance Scenarios**:

1. **Given** người chơi có danh sách buổi hợp lệ sau khi giải quyết xung đột, **When** bấm "Tiếp tục", **Then** hệ thống chuyển sang màn hình xác nhận đơn, hiển thị đồng hồ đếm ngược 05:00 và thực hiện giữ chỗ toàn bộ các slot của các buổi trong chu kỳ.
2. **Given** hệ thống đang thực hiện giữ chỗ cho chu kỳ, **When** có ít nhất 1 slot trong bất kỳ tuần nào vừa bị người khác đặt hoặc khóa trước đó, **Then** hệ thống hủy bỏ hoàn toàn việc giữ chỗ của tất cả các slot khác, thông báo "Slot [Ngày - Giờ - Sân] vừa có người đặt. Đơn cố định không thể giữ chỗ" và đưa người chơi quay lại màn hình giải quyết xung đột.
3. **Given** giữ chỗ thành công cho toàn bộ chu kỳ, **When** kiểm tra dữ liệu đơn đặt, **Then** các đơn con tương ứng với từng buổi đều mang trạng thái `HOLD` và được gán chung một mã định danh nhóm `recurringGroupId`.
4. **Given** người chơi bấm nút "Tiếp tục" nhiều lần liên tiếp khi mạng chập chờn, **When** các yêu cầu gửi đến máy chủ, **Then** hệ thống sử dụng mã định danh duy nhất (`requestId` UUID) để chỉ tạo đúng 1 nhóm đơn cố định duy nhất, không tạo trùng lặp.
5. **Given** các slot trong chu kỳ đang ở trạng thái giữ chỗ 5 phút, **When** người dùng khác xem lưới khung giờ của các ngày tương ứng, **Then** các slot này đều hiển thị trạng thái "Đang được giữ" và không thể bấm chọn.

---

### User Story 4 - Hết hạn giữ chỗ hoặc người chơi hủy giữ chỗ chu kỳ (Priority: P2)

Trong thời gian 5 phút giữ chỗ ở màn hình xác nhận đơn cố định, người chơi có thể chủ động bấm "Hủy" hoặc nút "Quay lại" để từ bỏ việc đặt lịch. Nếu quá 5 phút mà người chơi chưa hoàn tất chuyển sang bước thanh toán/đặt cọc (spec 050), thời gian giữ chỗ kết thúc: hệ thống tự động giải phóng toàn bộ các slot trong chu kỳ về trạng thái Còn trống một cách đồng bộ. Nếu người chơi vẫn đang mở app tại màn hình xác nhận khi đồng hồ chạm 00:00, app tự động thông báo "Thời gian giữ chỗ 5 phút đã hết", điều hướng quay lại màn hình cấu hình chu kỳ và giữ nguyên các lựa chọn ban đầu nếu các slot vẫn còn trống.

**Why this priority**: Đơn cố định chiếm dụng một lượng lớn slot của cơ sở (từ 4 đến 36 slot). Việc tự động giải phóng sạch sẽ khi hết hạn giúp cơ sở không bị "đóng băng" slot ảo khi người dùng bỏ dở phiên đặt.

**Independent Test**: Tạo một đơn giữ chỗ chu kỳ 4 tuần (8 slot); để đồng hồ đếm ngược hết 5 phút; kiểm tra toàn bộ 8 slot trên hệ thống đều tự động trở về trạng thái "Còn trống" và có thể đặt lại bình thường.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình xác nhận đơn cố định với đồng hồ còn thời gian, **When** bấm nút "Hủy giữ chỗ" hoặc nút "Quay lại", **Then** hệ thống hủy nhóm đơn giữ chỗ, giải phóng toàn bộ các slot của chu kỳ về trạng thái Còn trống và đưa người chơi về màn hình cấu hình.
2. **Given** đồng hồ đếm ngược tại màn hình xác nhận chạm mốc 00:00, **When** hết hạn 5 phút, **Then** app hiển thị hộp thoại thông báo "Đã hết thời gian giữ chỗ đơn cố định", khi người chơi bấm xác nhận, app điều hướng về màn hình cấu hình lịch.
3. **Given** người chơi tắt app hoặc mất kết nối mạng khi đang giữ chỗ, **When** thời gian 5 phút trôi qua theo thời gian máy chủ, **Then** hệ thống tự động coi toàn bộ các slot trong nhóm đơn cố định đã hết hạn (`EXPIRED`) và mở lại cho người dùng khác đặt.
4. **Given** người chơi chuyển app xuống background và mở lại sau 3 phút, **When** quay lại màn hình xác nhận, **Then** đồng hồ hiển thị chính xác thời gian còn lại (khoảng 2 phút) căn cứ theo mốc thời gian máy chủ.

---

### User Story 5 - Yêu cầu đăng nhập và khôi phục cấu hình lịch cố định (Priority: P2)

Khách vãng lai (chưa đăng nhập tài khoản) vẫn được phép vào màn hình đặt cố định, chọn thứ, khung giờ, chu kỳ và xem kết quả kiểm tra khả dụng cũng như phương án xử lý xung đột. Khi bấm "Tiếp tục" để giữ chỗ đơn cố định, hệ thống phát hiện chưa đăng nhập và yêu cầu người chơi đăng nhập (hoặc đăng ký). Sau khi đăng nhập thành công, hệ thống tự động đưa người chơi trở lại màn hình đặt cố định và phục hồi nguyên vẹn cấu hình cùng danh sách các buổi đã xử lý xung đột trước đó để người chơi tiếp tục bấm giữ chỗ mà không phải cấu hình lại từ đầu.

**Why this priority**: Tránh làm mất công sức thiết lập lịch phức tạp của người chơi, tối ưu trải nghiệm người dùng và tỷ lệ chuyển đổi đơn hàng.

**Independent Test**: Mở app khi chưa đăng nhập, thiết lập lịch cố định 4 tuần và chọn phương án bỏ 1 buổi trùng; bấm "Tiếp tục"; sau khi hoàn tất đăng nhập tài khoản, kiểm tra app quay lại đúng màn hình đặt cố định với đầy đủ danh sách các buổi đã được xử lý.

**Acceptance Scenarios**:

1. **Given** người dùng chưa đăng nhập tài khoản, **When** thao tác chọn thứ, giờ, chu kỳ và xử lý xung đột, **Then** hệ thống cho phép thao tác và hiển thị đầy đủ bảng tổng hợp lịch cùng số tiền tạm tính.
2. **Given** người dùng chưa đăng nhập bấm nút "Tiếp tục" tại bảng tổng hợp, **When** hệ thống kiểm tra trạng thái xác thực, **Then** hiển thị thông báo yêu cầu đăng nhập và chuyển hướng sang luồng đăng nhập (spec 010), lưu tạm toàn bộ cấu hình lịch cố định vào phiên làm việc.
3. **Given** người dùng đăng nhập thành công từ luồng chuyển tiếp, **When** quay lại app, **Then** hệ thống tự động đưa người dùng trở lại màn hình đặt cố định, khôi phục lại cấu hình và thực hiện quét nhanh lại tính khả dụng của chu kỳ.
4. **Given** người dùng quay lại sau khi đăng nhập nhưng một số slot trong chu kỳ đã bị người khác đặt trong thời gian đăng nhập, **When** app kiểm tra lại, **Then** hệ thống hiển thị thông báo cập nhật lại danh sách xung đột để người dùng xác nhận lại trước khi giữ chỗ.
5. **Given** người dùng bấm "Hủy / Quay lại" tại màn hình đăng nhập, **When** trở lại app, **Then** người dùng vẫn ở lại màn hình cấu hình lịch cố định với dữ liệu đã nhập.

---

### Edge Cases

- **Các thứ đã qua trong tuần hiện tại**: Khi người chơi chọn bắt đầu chu kỳ từ "Tuần hiện tại", nhưng hôm nay đã là Thứ Năm và người chơi chọn Thứ Ba & Thứ Bảy. Hệ thống tự động bắt đầu tính từ Thứ Bảy tuần này; buổi Thứ Ba đã qua không đưa vào đơn và không tính tiền, chu kỳ kéo dài đủ số tuần đã chọn kể từ ngày bắt đầu thực tế.
- **Cơ sở có các sân con với mức giá khác nhau**: Khi tự động đổi sân con cho buổi bị trùng, nếu Sân 1 có giá 100.000đ/giờ nhưng chỉ còn Sân VIP giá 150.000đ/giờ, hệ thống tính toán chính xác tổng tiền của chu kỳ dựa trên đơn giá thực tế của từng buổi riêng biệt và hiển thị chi tiết chênh lệch để người chơi duyệt.
- **Trùng slot ở đúng tuần cuối cùng của chu kỳ (tuần 12)**: Hệ thống áp dụng nhất quán quy tắc xử lý xung đột: cho phép đổi sang sân con khác hoặc bỏ qua tuần 12 (giảm xuống 11 tuần, vẫn thỏa mãn điều kiện ≥ 4 buổi).
- **Mất mạng khi đang xử lý giữ chỗ hàng loạt (batch hold)**: Do đơn cố định bao gồm nhiều slot, nếu thiết bị mất mạng giữa chừng khi đang gửi yêu cầu giữ chỗ, hệ thống đảm bảo nguyên tắc transaction: hoặc toàn bộ các slot của chu kỳ đều được giữ thành công, hoặc không có slot nào bị giữ dở dang. App hiển thị thông báo lỗi mạng kèm nút "Thử lại" với cùng `requestId`.
- **Chuyển giao sang màn hình thanh toán/đặt cọc**: Khi người chơi bấm "Thanh toán" tại màn hình xác nhận, toàn bộ nhóm đơn mang trạng thái `HOLD` và mã `recurringGroupId` được chuyển giao nguyên vẹn sang spec 050 cùng với đồng hồ thời gian giữ chỗ còn lại (không được cấp lại 5 phút mới).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cung cấp tùy chọn "Đặt cố định theo tuần" trên màn hình đặt sân của các cơ sở có hỗ trợ tính năng này.
- **FR-002**: Hệ thống PHẢI cho phép người chơi chọn một hoặc nhiều thứ trong tuần (từ Thứ Hai đến Chủ Nhật) cho lịch chơi định kỳ.
- **FR-003**: Hệ thống PHẢI cho phép người chơi chọn khung giờ cố định cho các buổi chơi, tuân thủ quy tắc slot 60 phút cố định và các mốc giờ tròn theo spec 040.
- **FR-004**: Hệ thống PHẢI cho phép người chơi chọn từ 1 đến tối đa 3 khung giờ liên tiếp trong một buổi chơi (tương đương 1 đến 3 giờ/buổi).
- **FR-005**: Hệ thống PHẢI cho phép người chơi chọn sân con ưu tiên trong danh sách các sân con đang hoạt động của cơ sở.
- **FR-006**: Hệ thống PHẢI cho phép người chơi chọn chu kỳ đặt sân cố định từ 4 tuần đến tối đa 12 tuần (tương đương 1 đến 3 tháng).
- **FR-007**: Hệ thống PHẢI cho phép người chơi chọn thời điểm bắt đầu chu kỳ: từ "Tuần hiện tại" hoặc từ "Tuần kế tiếp".
- **FR-008**: Khi người chơi hoàn tất chọn cấu hình, hệ thống PHẢI tự động quét và kiểm tra khả dụng của toàn bộ các slot trong chu kỳ đã chọn.
- **FR-009**: Hệ thống PHẢI hiển thị bảng tổng hợp gồm: danh sách chi tiết các buổi chơi (ngày, thứ, khung giờ, sân con), tổng số lượng buổi, và tổng tiền tạm tính toàn chu kỳ.
- **FR-010**: Tiền tệ trong toàn bộ đơn đặt cố định PHẢI được lưu trữ và tính toán bằng số nguyên đơn vị VND (Việt Nam Đồng), không dùng số thực.
- **FR-011**: Khi phát hiện có slot trong chu kỳ bị trùng (đã có người đặt hoặc bị sân khóa), hệ thống PHẢI hiển thị rõ danh sách các ngày bị xung đột và cung cấp 2 phương án xử lý: (1) Tự động đổi sang sân khác còn trống cùng giờ, hoặc (2) Bỏ qua buổi bị trùng.
- **FR-012**: Khi thực hiện đổi sân con cho buổi bị trùng, hệ thống PHẢI ưu tiên đổi sang sân con cùng loại và cùng mức giá; nếu sân thay thế có mức giá khác, hệ thống PHẢI hiển thị rõ mức chênh lệch giá và yêu cầu người chơi bấm xác nhận đồng ý trước khi tiếp tục.
- **FR-013**: Khi người chơi chọn phương án bỏ qua buổi bị trùng, hệ thống PHẢI tự động loại bỏ buổi đó khỏi danh sách đặt, giảm số lượng buổi tương ứng và tự động trừ tiền của buổi bị bỏ qua vào tổng tiền chu kỳ.
- **FR-014**: Sau khi loại bỏ các buổi bị trùng, nếu tổng số buổi chơi còn lại trong đơn cố định ít hơn 4 buổi, hệ thống PHẢI chặn luồng tiếp tục, hiển thị thông báo yêu cầu chọn lại khung giờ/thứ khác hoặc gợi ý chuyển sang đặt lẻ theo ngày.
- **FR-015**: Nếu tại ngày bị trùng không còn bất kỳ sân con nào khác còn trống trong cùng khung giờ và người chơi không đồng ý bỏ qua buổi đó, hệ thống PHẢI chặn luồng đặt cố định và yêu cầu người chơi chọn cấu hình khác.
- **FR-016**: Khi người chơi bấm "Tiếp tục", hệ thống PHẢI kiểm tra trạng thái đăng nhập; nếu chưa đăng nhập, hệ thống PHẢI lưu trạng thái cấu hình lịch và chuyển hướng sang màn hình đăng nhập.
- **FR-017**: Sau khi đăng nhập thành công, hệ thống PHẢI khôi phục lại cấu hình lịch cố định đã chọn và đưa người chơi trở lại màn hình đặt cố định.
- **FR-018**: Khi người chơi đã đăng nhập bấm "Tiếp tục" từ bảng tổng hợp hợp lệ, hệ thống PHẢI thực hiện giữ chỗ tạm thời cho toàn bộ các slot trong chu kỳ với thời hạn 5 phút tính theo mốc thời gian máy chủ (`holdExpiresAt`).
- **FR-019**: Thao tác giữ chỗ đơn cố định PHẢI tuân thủ nguyên tắc "Tất cả hoặc không" (All-or-Nothing): nếu có bất kỳ slot nào trong chu kỳ không còn trống tại thời điểm xử lý, toàn bộ yêu cầu giữ chỗ bị hủy bỏ và không có slot nào trong chu kỳ được giữ dở dang.
- **FR-020**: Khi giữ chỗ thành công, hệ thống PHẢI gán chung một mã nhóm `recurringGroupId` duy nhất cho toàn bộ các đơn con thuộc chu kỳ đó để quản lý đồng bộ.
- **FR-021**: Mỗi lần người chơi xác nhận giữ chỗ chu kỳ, hệ thống PHẢI gắn kèm một mã định danh yêu cầu duy nhất (`requestId` UUID) để chống tạo đơn trùng lặp khi người dùng bấm nhiều lần hoặc thử lại khi mất mạng.
- **FR-022**: Màn hình xác nhận đơn cố định PHẢI hiển thị đầy đủ: tên cơ sở, danh sách chi tiết từng buổi chơi (ngày, sân, khung giờ, giá), tổng tiền cả chu kỳ, và đồng hồ đếm ngược hiển thị thời gian còn lại (bắt đầu từ 05:00).
- **FR-023**: Trong thời gian 5 phút giữ chỗ, toàn bộ các slot được giữ trong chu kỳ PHẢI hiển thị trạng thái "Đang được giữ" đối với tất cả những người dùng khác trong hệ thống.
- **FR-024**: Hệ thống PHẢI cho phép người chơi chủ động bấm "Hủy giữ chỗ" hoặc nút "Quay lại" tại màn hình xác nhận; khi đó, hệ thống PHẢI lập tức giải phóng toàn bộ các slot đã giữ của chu kỳ về trạng thái Còn trống.
- **FR-025**: Khi đồng hồ 5 phút đếm ngược về 00:00 mà người chơi chưa chuyển sang bước thanh toán, hệ thống PHẢI tự động chuyển trạng thái nhóm đơn sang `EXPIRED`, giải phóng toàn bộ các slot về trạng thái Còn trống và thông báo cho người chơi.
- **FR-026**: Khi ứng dụng bị đưa vào chế độ nền (background), thời gian đếm ngược khi người chơi mở lại app PHẢI được tính toán lại chính xác căn cứ theo mốc thời gian hết hạn của máy chủ.
- **FR-027**: Khi người chơi bấm "Thanh toán" tại màn hình xác nhận, hệ thống PHẢI chuyển giao nhóm đơn cố định đang ở trạng thái `HOLD` cùng thời gian giữ chỗ còn lại sang tính năng Thanh toán/Đặt cọc (spec 050).
- **FR-028**: Hệ thống PHẢI hỗ trợ hiển thị giao diện rõ ràng trên cả 2 chế độ Sáng (Light mode) và Tối (Dark mode), và xử lý đầy đủ 4 trạng thái giao diện: Đang tải, Có dữ liệu, Rỗng (cơ sở không mở đặt cố định), Lỗi (mất mạng, lỗi máy chủ kèm nút thử lại).

---

### Key Entities *(mandatory)*

- **RecurringBookingGroup (Nhóm đơn đặt cố định)**: Đại diện cho hợp đồng đặt định kỳ theo tuần của người chơi. Thuộc tính: `recurringGroupId` (UUID), `userId`, `venueId`, `dayOfWeekList` (danh sách các thứ trong tuần), `startTime`, `endTime`, `startDate`, `endDate`, `totalSessions` (tổng số buổi), `totalAmount` (tổng tiền VND), `status` (`HOLD`, `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`), `createdAt`, `updatedAt`.
- **Booking (Đơn đặt từng buổi con)**: Đại diện cho từng buổi chơi cụ thể trong chu kỳ đặt cố định. Thuộc tính: `bookingId`, `recurringGroupId` (liên kết với nhóm đơn), `userId`, `venueId`, `courtId`, `courtName`, `date` (`yyyyMMdd`), `slotIds` (danh sách slot của buổi), `subtotal`, `status` (`HOLD`, `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `EXPIRED`), `requestId`, `holdExpiresAt`.
- **CourtSlot (Khung giờ sân)**: Đại diện cho trạng thái của từng slot 60 phút trên từng sân con. Thuộc tính: `slotId` (`{courtId}_{yyyyMMdd}_{HHmm}`), `courtId`, `venueId`, `date`, `startTime`, `endTime`, `status` (`AVAILABLE`, `HELD`, `BOOKED`, `BLOCKED`), `heldByUserId`, `holdExpiresAt`.
- **VenueCourt (Sân con)**: Thông tin sân con trong cơ sở. Thuộc tính: `courtId`, `venueId`, `name`, `type` (STANDARD, VIP), `status` (ACTIVE, MAINTENANCE).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Hệ thống quét và kiểm tra khả dụng của toàn bộ chu kỳ đặt cố định tối đa 12 tuần (lên đến 36 buổi chơi) hoàn tất trong vòng **dưới 2 giây** trên kết nối mạng di động 4G.
- **SC-002**: Tính toàn vẹn giữ chỗ chu kỳ (All-or-Nothing): **100%** các giao dịch giữ chỗ đơn cố định hoặc thành công hoàn toàn cho tất cả các slot trong chu kỳ, hoặc hoàn tác sạch sẽ **0 slot** bị giữ dở dang khi có bất kỳ xung đột nào xảy ra.
- **SC-003**: Chống trùng lặp tuyệt đối: Thao tác bấm nhiều lần liên tiếp hoặc thử lại khi mạng chập chờn chỉ tạo ra chính xác **1 nhóm đơn cố định** duy nhất với duy nhất một mã `recurringGroupId`, tỷ lệ trùng lặp là **0%**.
- **SC-004**: Độ chính xác giải quyết xung đột: **100%** các trường hợp đổi sân con có chênh lệch giá đều được hiển thị minh bạch cho người dùng duyệt; **100%** các đơn sau khi bỏ buổi trùng có số buổi < 4 đều bị chặn an toàn.
- **SC-005**: Tự động giải phóng tài nguyên: **100%** các nhóm đơn cố định hết hạn 5 phút giữ chỗ đều được hệ thống tự động giải phóng toàn bộ các slot về trạng thái Còn trống cho người khác đặt.
- **SC-006**: Luồng thao tác tinh gọn: Người chơi có thể hoàn thành việc chọn cấu hình lịch, giải quyết xung đột và chuyển sang màn hình xác nhận trong **không quá 3 bước màn hình**.
- **SC-007**: Phục hồi trạng thái sau đăng nhập: Đạt **100%** người dùng chưa đăng nhập sau khi hoàn thành đăng nhập quay lại đúng màn hình đặt cố định với toàn bộ cấu hình lịch đã chọn được bảo toàn.

---

## Assumptions

- Khung giờ hoạt động của cơ sở và bảng giá `priceRules` cho các thứ trong tuần đã được chủ sân thiết lập ổn định trước khi mở tính năng đặt cố định.
- Tất cả các sân con trong cùng cơ sở mặc định có thể thay thế cho nhau nếu cùng loại sân và cùng khung giờ.
- Múi giờ chuẩn của toàn bộ hệ thống là giờ Việt Nam (`Asia/Ho_Chi_Minh`, UTC+7).
- Đơn vị tiền tệ hiển thị và tính toán là Việt Nam Đồng (VND), lưu trữ dưới dạng số nguyên Long.
- Chính sách đặt cọc theo tỷ lệ %, thanh toán tiền mặt/chuyển khoản và áp dụng mã khuyến mãi cho đơn cố định thuộc phạm vi giải quyết của spec tiếp theo (`specs/050-payment-voucher`).
- Việc dời lịch, hủy riêng lẻ từng buổi chơi hoặc check-in theo từng buổi trong chu kỳ thuộc phạm vi của tính năng quản lý lịch đặt (`specs/060-my-bookings`).
