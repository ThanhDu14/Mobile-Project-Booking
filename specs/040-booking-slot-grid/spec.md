# Feature Specification: Lưới khung giờ đặt sân và giữ chỗ

**Feature Branch**: `feature/04-booking`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi đã đăng nhập đặt sân cầu lông theo khung giờ. Từ chi tiết sân, người chơi chọn ngày (từ hôm nay đến 30 ngày tới), xem lưới khung giờ của từng sân con với 3 trạng thái: Còn trống, Đã đặt/Đang được giữ, Bị khóa. Khung giờ đã qua trong ngày hôm nay không chọn được. Người chơi chọn nhiều sân con và nhiều khung giờ liên tiếp trong một đơn; thanh tóm tắt hiện số slot đã chọn và tổng tiền tạm tính theo bảng giá của sân (giờ thường, cao điểm, cuối tuần). Bấm "Tiếp tục" để sang màn hình xác nhận (chi tiết slot, tổng tiền) và bắt đầu giữ chỗ 5 phút có đồng hồ đếm ngược. Giữ chỗ theo nguyên tắc tất cả hoặc không: nếu một slot trong lựa chọn vừa bị người khác lấy thì không slot nào được giữ; app báo rõ slot nào đã mất, lưới cập nhật và các slot còn trống vẫn ở trạng thái đang chọn. Hai người cùng giữ một slot thì chỉ một người thành công. Bấm nhiều lần hoặc bấm "Thử lại" không tạo đơn trùng. Rời màn hình xác nhận hoặc hết 5 phút thì slot được nhả; hết giờ khi đang xem thì quay về lưới, giữ lựa chọn cũ nếu slot còn trống. Luồng từ chi tiết sân đến khi đặt xong không quá 5 bước (tính cả thanh toán của spec 050). Luồng lỗi và trạng thái rỗng: mất mạng (báo lỗi rõ, có nút thử lại), sân không còn giờ trống trong ngày, ngày sân không mở cửa, người chưa đăng nhập bấm "Tiếp tục" (yêu cầu đăng nhập, giữ nguyên lựa chọn sau khi đăng nhập). Tiêu chí đo được: lưới giờ tải dưới 3 giây trên 4G; khi nhiều người cùng giữ một slot thì đúng 1 người thành công; thao tác lặp lại không tạo quá 1 đơn. Ranh giới: spec này kết thúc khi đơn ở trạng thái HOLD và chuyển sang màn thanh toán (spec 050). Không bao gồm: đặt cố định theo tuần (spec 041), chọn phương thức thanh toán và voucher (spec 050), quản lý danh sách đơn (spec 060). Ràng buộc: tuân thủ constitution nguyên tắc II và III; cách triển khai để phần /speckit-plan. Dữ liệu: slots, bookings, venues/courts, venues/priceRules theo docs/design/database/README.md. Điểm giao cần làm rõ: cách tính giá khi khung giờ nằm giữa hai priceRules (với D); khóa giờ BLOCKED (với D); ai chuyển đơn sang COMPLETED (với C)."

**Nhóm tính năng**: [04 — Đặt sân](../../docs/features/04-booking/README.md) · **Phụ trách**: B · **Liên quan**: [spec 010](../010-account-auth/spec.md) (đăng nhập / điều hướng), [spec 030](../030-court-detail/spec.md) (chi tiết sân), [spec 041](../041-weekly-recurring-booking/spec.md) (đặt cố định), [spec 050](../050-payment-voucher/spec.md) (thanh toán và voucher), [spec 060](../060-my-bookings/spec.md) (quản lý lịch đặt), [spec 111](../111-owner-venue-setup/spec.md) (cơ sở, sân con, bảng giá)

## Clarifications

### Session 2026-10-06

- Q: Quy định độ dài mỗi khung giờ trên lưới là bao nhiêu? → A: Cố định 60 phút (ví dụ 07:00–08:00, 08:00–09:00).
- Q: Cách tính giá khi khung giờ nằm giữa hai bảng giá (điểm giao với Thành viên D)? → A: Giờ bắt đầu và kết thúc của `priceRules` bắt buộc phải tròn theo độ dài slot (ví dụ 17:00 hoặc 18:00, không được lẻ 17:30). Thành viên D thiết lập bảng giá theo các mốc giờ tròn.
- Q: Giới hạn tối đa số lượng slot mà một người chơi được chọn trong cùng một đơn đặt là bao nhiêu? → A: Tối đa 8 slot (tương đương 8 giờ chơi) trong một đơn đặt.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem lưới khung giờ và chọn slot đặt sân (Priority: P1)

Từ màn hình chi tiết sân, người chơi bấm "Đặt sân" để vào màn hình chọn lịch và xem lưới khung giờ. Người chơi chọn một ngày (hôm nay hoặc trong vòng 30 ngày tới), lưới hiển thị danh sách các sân con cùng toàn bộ khung giờ hoạt động trong ngày với 3 trạng thái trực quan: Còn trống, Đã đặt/Đang được giữ, Bị khóa. Các khung giờ đã trôi qua trong ngày hôm nay bị làm mờ và không thể bấm chọn. Người chơi có thể chọn một hoặc nhiều sân con và các khung giờ liên tiếp; phía dưới màn hình có thanh tóm tắt hiển thị tổng số slot đã chọn và tổng tiền tạm tính tương ứng theo bảng giá (giờ thường, giờ cao điểm, ngày cuối tuần).

**Why this priority**: Đây là bước mở đầu bắt buộc của toàn bộ luồng đặt sân. Nếu không hiển thị được lịch, tình trạng slot và không cho chọn slot, toàn bộ nghiệp vụ cốt lõi của ứng dụng không thể hoạt động.

**Independent Test**: Mở một cơ sở sân có cấu hình 3 sân con, chọn ngày bất kỳ trong vòng 30 ngày; xác nhận lưới hiển thị đầy đủ các cột sân con và hàng giờ; bấm chọn 2 slot liên tiếp, kiểm tra thanh tóm tắt cập nhật đúng số lượng slot và tổng tiền. Thử chọn slot trong quá khứ hoặc slot đã đặt thì không chọn được.

**Acceptance Scenarios**:

1. **Given** người chơi mở màn hình đặt sân từ chi tiết một cơ sở, **When** màn hình tải xong, **Then** mặc định chọn ngày hôm nay và hiển thị lưới khung giờ tương ứng với giờ mở cửa - đóng cửa của cơ sở.
2. **Given** người chơi xem lưới khung giờ của ngày hôm nay, **When** có các khung giờ có giờ bắt đầu nhỏ hơn hoặc bằng giờ hiện tại, **Then** các slot này ở trạng thái "Đã qua", hiển thị mờ và không cho phép người chơi chọn.
3. **Given** một slot ở trạng thái "Đã đặt / Đang được giữ" hoặc "Bị khóa" (do chủ sân đóng sân), **When** người chơi bấm vào slot đó, **Then** hệ thống không chọn slot và hiển thị thông báo trạng thái tương ứng.
4. **Given** người chơi chọn một slot còn trống, **When** bấm vào slot, **Then** slot đổi sang trạng thái "Đang chọn", thanh tóm tắt dưới đáy màn hình hiện "1 khung giờ" và tổng tiền tạm tính chính xác theo bảng giá của cơ sở tại khung giờ đó.
5. **Given** người chơi đã chọn một slot trên Sân 1 (ví dụ 17:00–18:00), **When** chọn tiếp slot 18:00–19:00 trên cùng Sân 1, **Then** cả 2 slot đều ở trạng thái "Đang chọn", thanh tóm tắt cập nhật "2 khung giờ" và tổng tiền là tổng của 2 slot.
6. **Given** người chơi chọn 2 khung giờ không liên tiếp trên cùng một sân con hoặc chọn slot ngắt quãng, **When** bấm slot thứ hai không liền kề, **Then** hệ thống hiển thị thông báo nhắc nhở yêu cầu chọn các khung giờ liên tục trên cùng sân.
7. **Given** người chơi đã chọn nhiều slot, **When** bấm lại vào một slot đang chọn, **Then** slot đó được hủy chọn, thanh tóm tắt tự động trừ bớt số lượng và tiền; nếu không còn slot nào thì nút "Tiếp tục" bị vô hiệu hóa.
8. **Given** người chơi chuyển đổi giữa các ngày trên thanh lịch chọn ngày (từ hôm nay đến tối đa ngày thứ 30), **When** chọn ngày mới, **Then** lưới làm mới dữ liệu cho ngày mới và xóa các lựa chọn của ngày cũ (kèm cảnh báo xác nhận nếu đang có slot được chọn).

---

### User Story 2 - Giữ chỗ tạm thời 5 phút với nguyên tắc "Tất cả hoặc không" (Priority: P1)

Sau khi chọn các slot mong muốn trên lưới, người chơi bấm "Tiếp tục" để sang màn hình xác nhận đơn đặt. Tại thời điểm này, hệ thống thực hiện giữ chỗ tạm thời (trạng thái `HOLD`) cho tất cả các slot đã chọn trong vòng 5 phút (có đồng hồ đếm ngược hiển thị thời gian giữ chỗ). Quá trình giữ chỗ tuân thủ nghiêm ngặt nguyên tắc "Tất cả hoặc không" (All-or-Nothing): nếu có ít nhất một slot trong đơn vừa bị người khác giữ trước hoặc bị chủ sân khóa, toàn bộ các slot khác trong đơn KHÔNG được giữ; hệ thống báo rõ slot nào đã bị mất, cập nhật lại trạng thái trên lưới và giữ nguyên lựa chọn các slot còn trống để người chơi bổ sung. Khi hai người chơi cùng bấm giữ cùng một slot tại cùng thời điểm, chỉ duy nhất một người thành công.

**Why this priority**: Đây là giải pháp kỹ thuật cốt lõi giải quyết bài toán chống trùng lịch (double-booking) được xác định trong Constitution (Nguyên tắc III). Đảm bảo tính toàn vẹn dữ liệu đặt chỗ và trải nghiệm minh bạch cho người chơi.

**Independent Test**: Giả lập 2 tài khoản cùng chọn chung slot 18:00–19:00 trên Sân 1 và bấm "Tiếp tục" gần như đồng thời; kiểm tra một tài khoản vào màn hình xác nhận với đồng hồ đếm ngược 5 phút, tài khoản còn lại nhận thông báo slot đã bị giữ và quay lại lưới cập nhật.

**Acceptance Scenarios**:

1. **Given** người chơi đã chọn 2 slot hợp lệ còn trống trên lưới, **When** bấm "Tiếp tục", **Then** hệ thống thực hiện giữ chỗ tạm thời, chuyển sang màn hình xác nhận hiển thị chi tiết tên cơ sở, danh sách sân con, khung giờ, tổng tiền và đồng hồ đếm ngược bắt đầu từ 05:00.
2. **Given** người chơi chọn 3 slot, trong đó có 1 slot vừa bị một người khác giữ thành công trước 1 giây, **When** người chơi bấm "Tiếp tục", **Then** hệ thống không giữ bất kỳ slot nào trong 3 slot đó, hiển thị thông báo "Slot [Giờ - Sân] vừa có người đặt trước", quay về màn hình lưới với slot bị mất chuyển sang màu đã đặt và 2 slot còn lại vẫn được giữ trạng thái đang chọn.
3. **Given** hai người chơi cùng thao tác trên cùng một slot còn trống, **When** cả hai cùng bấm "Tiếp tục" tại cùng một thời điểm, **Then** hệ thống chỉ cho phép đúng 1 người chơi nhận kết quả giữ chỗ thành công; người chơi còn lại nhận thông báo lỗi slot không còn khả dụng.
4. **Given** người chơi bấm nút "Tiếp tục" liên tiếp nhiều lần thật nhanh (do sốt ruột hoặc giật lag), **When** hệ thống nhận nhiều yêu cầu gửi tới, **Then** hệ thống đảm bảo tính duy nhất (idempotency), chỉ xử lý một yêu cầu giữ chỗ duy nhất và không tạo ra nhiều đơn trùng lặp.
5. **Given** người chơi ở màn hình xác nhận đơn với đồng hồ đang đếm ngược, **When** người khác xem lưới khung giờ của sân đó trên thiết bị khác, **Then** các slot này hiển thị ở trạng thái "Đang được giữ" và người khác không thể bấm chọn.

---

### User Story 3 - Xử lý hết hạn giữ chỗ và hủy giữ chỗ (Priority: P2)

Trong thời gian 5 phút giữ chỗ ở màn hình xác nhận, người chơi có thể chủ động bấm "Hủy" hoặc "Quay lại" để từ bỏ việc giữ chỗ. Nếu quá 5 phút mà người chơi chưa hoàn tất chuyển sang bước thanh toán (spec 050), thời gian giữ chỗ kết thúc: hệ thống tự động giải phóng toàn bộ các slot đã giữ về trạng thái Còn trống. Nếu người chơi vẫn đang mở app tại màn hình xác nhận khi hết giờ, app tự động thông báo "Đã hết thời gian giữ chỗ", điều hướng quay lại lưới khung giờ và khôi phục các slot đang chọn nếu các slot đó chưa bị người khác lấy.

**Why this priority**: Cơ chế tự động giải phóng slot giúp tài nguyên sân không bị chiếm dụng ảo (ghost holding) khi người dùng đổi ý hoặc thoát app, đảm bảo công bằng cho những người chơi khác.

**Independent Test**: Đặt giữ chỗ 1 slot, để đồng hồ đếm ngược hết 5 phút; kiểm tra slot đó trên một thiết bị khác chuyển từ "Đang được giữ" về lại "Còn trống" và có thể chọn lại được.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình xác nhận đơn với đồng hồ đếm ngược còn thời gian, **When** bấm nút "Quay lại" hoặc nút "Hủy giữ chỗ", **Then** hệ thống giải phóng các slot đang giữ về trạng thái Còn trống, hủy đơn giữ chỗ và quay lại màn hình lưới giờ.
2. **Given** đồng hồ đếm ngược chạm mốc 00:00 ở màn hình xác nhận, **When** thời gian kết thúc, **Then** app hiển thị hộp thoại thông báo "Thời gian giữ chỗ 5 phút đã hết", khi người chơi bấm "Đồng ý", app điều hướng về màn hình lưới giờ; nếu các slot đó vẫn còn trống thì giữ lại trạng thái đang chọn.
3. **Given** người chơi đang ở màn hình xác nhận nhưng tắt app hoặc mất kết nối mạng đột ngột, **When** thời gian 5 phút trôi qua theo thời gian máy chủ, **Then** hệ thống tự động coi các slot này đã hết hạn và cho phép người chơi khác vào đặt bình thường.
4. **Given** người chơi chuyển app xuống background trong lúc đồng hồ 5 phút đang chạy rồi mở lại sau 2 phút, **When** quay lại app, **Then** đồng hồ hiển thị chính xác thời gian còn lại (khoảng 3 phút) dựa theo mốc hết hạn của máy chủ chứ không bị đóng băng theo thời gian giao diện.

---

### User Story 4 - Yêu cầu đăng nhập và phục hồi lựa chọn (Priority: P2)

Khách vãng lai (chưa đăng nhập tài khoản) vẫn được phép duyệt thông tin sân, chọn ngày và xem lưới khung giờ trực quan. Khách cũng có thể chọn thử các slot trên lưới để xem tạm tính tiền. Tuy nhiên, khi bấm "Tiếp tục" để giữ chỗ, hệ thống phát hiện chưa đăng nhập và yêu cầu người chơi đăng nhập (hoặc đăng ký). Sau khi đăng nhập thành công, hệ thống tự động đưa người chơi trở lại màn hình đặt sân và phục hồi nguyên vẹn các slot đã chọn trước đó (nếu vẫn còn trống) mà không bắt người chơi phải chọn lại từ đầu.

**Why this priority**: Tối ưu phễu chuyển đổi người dùng (conversion funnel). Cho phép người dùng trải nghiệm trước khi bị rào cản đăng nhập, đồng thời không làm mất công sức thao tác của người dùng sau khi đăng nhập xong.

**Independent Test**: Mở app ở chế độ khách chưa đăng nhập, chọn 2 slot trên lưới và bấm "Tiếp tục"; kiểm tra app chuyển sang màn hình đăng nhập; sau khi đăng nhập tài khoản thành công, app tự động quay lại màn hình đặt sân với đúng 2 slot đó đang được chọn.

**Acceptance Scenarios**:

1. **Given** người dùng chưa đăng nhập tài khoản đang xem lưới khung giờ, **When** bấm chọn các slot còn trống, **Then** lưới vẫn cho phép chọn và thanh tóm tắt vẫn tính tiền bình thường.
2. **Given** người dùng chưa đăng nhập bấm nút "Tiếp tục", **When** hệ thống kiểm tra trạng thái xác thực, **Then** hiển thị thông báo yêu cầu đăng nhập và điều hướng sang luồng đăng nhập (spec 010), kèm lưu tạm danh sách slot đang chọn vào bộ nhớ phiên làm việc.
3. **Given** người dùng đăng nhập thành công từ luồng chuyển tiếp, **When** quay lại màn hình đặt sân, **Then** hệ thống tự động kiểm tra lại tính khả dụng của các slot đã lưu tạm: nếu tất cả slot vẫn còn trống thì tự động khôi phục trạng thái chọn và cho phép người dùng bấm "Tiếp tục" để giữ chỗ.
4. **Given** người dùng quay lại sau khi đăng nhập nhưng 1 trong các slot đã bị người khác đặt mất trong lúc đăng nhập, **When** app tải lại lưới, **Then** hệ thống thông báo "Một số slot bạn chọn trước đó đã không còn trống" và chỉ giữ lại những slot còn khả dụng.
5. **Given** người dùng bấm nút "Bỏ qua / Hủy" ở màn hình đăng nhập, **When** quay lại app, **Then** người dùng vẫn ở lại màn hình lưới giờ với các slot đang chọn nhưng không được chuyển sang màn hình xác nhận giữ chỗ.

---

### User Story 5 - Chống tạo đơn trùng lặp và xử lý mất kết nối (Priority: P3)

Trong điều kiện mạng di động chập chờn, khi người chơi bấm "Tiếp tục" để giữ chỗ nhưng yêu cầu bị timeout hoặc mất mạng giữa chừng, app hiển thị thông báo mất kết nối rõ ràng và cung cấp nút "Thử lại". Khi người chơi bấm "Thử lại", hệ thống tái sử dụng định danh yêu cầu duy nhất (`requestId` dạng UUID) đã sinh ra từ lần bấm đầu tiên để đảm bảo máy chủ không tạo thêm đơn đặt chỗ mới nếu yêu cầu trước đó thực tế đã đến được máy chủ.

**Why this priority**: Đảm bảo tính ổn định và chống sai lệch dữ liệu trong môi trường mạng di động 4G không ổn định tại Việt Nam (tuân thủ Nguyên tắc III & IV của Constitution).

**Independent Test**: Sử dụng chế độ mô phỏng ngắt mạng khi bấm "Tiếp tục"; kiểm tra app báo lỗi kết nối và hiện nút "Thử lại"; bấm "Thử lại" khi có mạng, kiểm tra chỉ có đúng một đơn giữ chỗ được tạo với duy nhất một mã phiên.

**Acceptance Scenarios**:

1. **Given** người chơi bấm "Tiếp tục" nhưng thiết bị mất kết nối mạng internet hoàn toàn, **When** yêu cầu không thể gửi đi, **Then** app hiển thị thông báo "Không có kết nối mạng. Vui lòng kiểm tra lại đường truyền" kèm nút "Thử lại", không làm mất các slot đang chọn.
2. **Given** người chơi gặp lỗi mạng và bấm nút "Thử lại" sau khi có mạng lại, **When** yêu cầu được gửi đi với cùng `requestId`, **Then** hệ thống chỉ thực hiện giữ chỗ đúng 1 lần duy nhất cho phiên đặt đó.
3. **Given** người chơi đang ở màn hình lưới giờ và bị mất mạng, **When** cố bấm chọn slot mới, **Then** app báo lỗi mất kết nối và không cho phép thực hiện thao tác tiếp theo cho đến khi có mạng lại.
4. **Given** người chơi bấm "Tiếp tục", hệ thống đang xử lý trong vòng 1–2 giây, **When** nút "Tiếp tục" đang ở trạng thái loading, **Then** nút bị vô hiệu hóa (disabled) để ngăn chặn người dùng bấm đúp (double-tap).

---

### Edge Cases

- **Khung giờ chuyển giao ngày hôm nay**: Người chơi mở app lúc 16:59 xem slot 17:00–18:00 (vẫn còn hợp lệ), nhưng đến 17:01 mới bấm chọn. Hệ thống phải kiểm tra lại thời gian thực lúc bấm và báo slot đã qua giờ đặt, không cho phép chọn.
- **Ngày cơ sở đóng cửa hoặc bảo trì**: Người chơi chọn một ngày mà cơ sở tạm ngưng hoạt động hoặc nghỉ lễ (được chủ sân cấu hình từ trước). Lưới giờ hiển thị trạng thái rỗng đặc biệt kèm thông báo: "Cơ sở đóng cửa vào ngày này" và gợi ý chọn ngày khác.
- **Toàn bộ sân con trong ngày đã kín chỗ**: Khi tất cả khung giờ của tất cả sân con đều ở trạng thái "Đã đặt" hoặc "Khóa", app hiển thị banner thông báo "Hôm nay đã kín sân" và nút chuyển nhanh sang ngày kế tiếp còn giờ trống.
- **Giá giờ cao điểm / cuối tuần xen kẽ**: Người chơi chọn 2 slot liên tiếp: Slot 1 rơi vào giờ thường (16:00–17:00, 80.000đ), Slot 2 rơi vào giờ cao điểm (17:00–18:00, 120.000đ). Thanh tóm tắt và màn hình xác nhận phải hiển thị tách bạch đơn giá từng slot và tính đúng tổng tiền là 200.000đ.
- **Xung đột khung giờ giữa 2 priceRules**: Giờ bắt đầu và kết thúc của các quy tắc giá (PriceRule) bắt buộc phải tròn theo khung giờ 60 phút (ví dụ 17:00 hoặc 18:00, không được lẻ như 17:30). Ràng buộc này được thống nhất với Thành viên D khi cấu hình bảng giá, do đó mỗi slot 60 phút luôn có đơn giá xác định duy nhất.
- **Thiết bị đổi giờ hệ thống (Client time spoofing)**: Người dùng cố tình chỉnh lùi giờ trên điện thoại để đặt slot đã qua. Toàn bộ việc kiểm tra slot hợp lệ và thời hạn 5 phút giữ chỗ đều căn cứ theo thời gian máy chủ (server timestamp), hoàn toàn bỏ qua giờ client.
- **Chuyển giao sang bước thanh toán**: Khi người chơi ở màn hình xác nhận bấm "Thanh toán", đơn giữ chỗ ở trạng thái `HOLD` được chuyển giao nguyên vẹn sang spec 050 cùng với đồng hồ đếm ngược thời gian còn lại (tổng thời gian giữ chỗ không bị reset lại thành 5 phút mới).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cho phép người chơi xem danh sách các ngày đặt sân từ ngày hiện tại đến tối đa 30 ngày tiếp theo (múi giờ `Asia/Ho_Chi_Minh`).
- **FR-002**: Hệ thống PHẢI hiển thị lưới khung giờ gồm các cột đại diện cho từng sân con hoạt động của cơ sở và các hàng đại diện cho các khung giờ cố định 60 phút trong ngày theo thời gian mở/đóng cửa của cơ sở.
- **FR-003**: Hệ thống PHẢI thể hiện rõ ràng và trực quan 3 trạng thái khả dụng của từng slot trên lưới: Còn trống (Available), Đã đặt/Đang giữ chỗ (Booked/Held), Bị khóa (Blocked).
- **FR-004**: Hệ thống PHẢI tự động làm mờ và vô hiệu hóa các khung giờ có giờ bắt đầu nhỏ hơn hoặc bằng thời gian hiện tại của ngày hôm nay.
- **FR-005**: Hệ thống PHẢI cho phép người chơi chọn nhiều slot trên cùng một sân con hoặc trên các sân con khác nhau trong cùng một cơ sở và cùng một ngày.
- **FR-006**: Hệ thống PHẢI ràng buộc các slot được chọn trên cùng một sân con phải là các khung giờ liên tiếp nhau, không được chọn ngắt quãng.
- **FR-007**: Hệ thống PHẢI hiển thị thanh tóm tắt cố định ở đáy màn hình lưới giờ gồm: số lượng slot đang chọn, danh sách tóm tắt (sân con, khung giờ) và tổng số tiền tạm tính.
- **FR-008**: Hệ thống PHẢI tính toán chính xác tổng tiền tạm tính dựa trên bảng giá của cơ sở tại ngày và khung giờ được chọn (phân biệt ngày thường, ngày cuối tuần, giờ bình thường và giờ cao điểm). Các quy tắc giá (PriceRule) bắt buộc phải có mốc giờ bắt đầu và kết thúc tròn theo khung giờ 60 phút.
- **FR-009**: Tiền tệ trong hệ thống PHẢI được lưu trữ và tính toán bằng số nguyên đơn vị VND (Việt Nam Đồng), không dùng số thực để tránh sai số thập phân.
- **FR-010**: Hệ thống PHẢI cung cấp nút "Tiếp tục" trên thanh tóm tắt, chỉ được kích hoạt khi có ít nhất một slot hợp lệ đang được chọn.
- **FR-011**: Khi người chơi bấm "Tiếp tục", hệ thống PHẢI kiểm tra trạng thái đăng nhập; nếu chưa đăng nhập, hệ thống PHẢI lưu trạng thái các slot đang chọn và chuyển hướng người chơi đến màn hình đăng nhập.
- **FR-012**: Sau khi người chơi hoàn tất đăng nhập thành công, hệ thống PHẢI khôi phục lại các slot đã chọn trước đó và đưa người chơi trở lại màn hình đặt sân.
- **FR-013**: Khi người chơi đã đăng nhập bấm "Tiếp tục", hệ thống PHẢI thực hiện giữ chỗ tạm thời cho toàn bộ các slot đã chọn với thời hạn chính xác là 5 phút tính theo mốc thời gian máy chủ (`holdExpiresAt`).
- **FR-014**: Thao tác giữ chỗ PHẢI tuân thủ nguyên tắc "Tất cả hoặc không" (All-or-Nothing): nếu có bất kỳ slot nào trong danh sách chọn không còn trống tại thời điểm xử lý, toàn bộ yêu cầu giữ chỗ bị hủy bỏ và không có slot nào được chuyển sang trạng thái giữ chỗ.
- **FR-015**: Khi thao tác giữ chỗ thất bại do có slot bị người khác tranh chấp trước, hệ thống PHẢI thông báo đích danh slot không còn khả dụng, tự động tải lại trạng thái mới nhất của lưới và duy trì trạng thái chọn đối với các slot còn lại.
- **FR-016**: Khi nhiều yêu cầu cùng tranh chấp giữ một slot trống, hệ thống PHẢI bảo đảm tính toàn vẹn: chỉ có duy nhất một yêu cầu thành công, các yêu cầu còn lại bị từ chối an toàn.
- **FR-017**: Mỗi lần người chơi xác nhận giữ chỗ, hệ thống PHẢI gắn kèm một mã định danh yêu cầu duy nhất (`requestId` UUID) để chống xử lý trùng lặp (idempotency) khi người dùng bấm nhiều lần hoặc thử lại do mất mạng.
- **FR-018**: Khi giữ chỗ thành công, hệ thống PHẢI chuyển sang màn hình xác nhận đơn hiển thị: tên cơ sở, địa chỉ, danh sách chi tiết các slot (sân con, ngày, khung giờ, đơn giá từng slot), tổng tiền thanh toán và đồng hồ đếm ngược hiển thị thời gian còn lại (bắt đầu từ 05:00).
- **FR-019**: Trong thời gian giữ chỗ 5 phút, các slot được giữ PHẢI hiển thị ở trạng thái "Đang được giữ" đối với tất cả những người dùng khác trong hệ thống.
- **FR-020**: Hệ thống PHẢI cho phép người chơi chủ động bấm "Hủy giữ chỗ" hoặc nút "Quay lại" tại màn hình xác nhận; khi đó, hệ thống PHẢI lập tức giải phóng các slot đã giữ về trạng thái Còn trống.
- **FR-021**: Khi đồng hồ 5 phút đếm ngược về 00:00 mà người chơi chưa chuyển sang bước thanh toán, hệ thống PHẢI tự động hết hạn đơn giữ chỗ (`EXPIRED`), giải phóng các slot về trạng thái Còn trống và thông báo cho người chơi biết.
- **FR-022**: Nếu ứng dụng bị đưa vào chế độ nền (background) hoặc thiết bị tắt màn hình, khi người chơi mở lại ứng dụng, thời gian đếm ngược PHẢI được tính toán lại chính xác dựa trên mốc thời gian hết hạn của máy chủ.
- **FR-023**: Khi người chơi bấm "Thanh toán" tại màn hình xác nhận đơn, hệ thống PHẢI chuyển giao đơn đặt đang ở trạng thái `HOLD` cùng thời gian giữ chỗ còn lại sang tính năng Thanh toán (spec 050).
- **FR-024**: Hệ thống PHẢI xử lý và hiển thị đầy đủ 4 trạng thái giao diện: Đang tải (Loading skeleton), Có dữ liệu (Content), Rỗng (Empty: ngày đóng cửa, không có sân), Lỗi (Error: mất mạng, lỗi máy chủ kèm nút thử lại).
- **FR-025**: Toàn bộ luồng nghiệp vụ từ màn hình chi tiết sân đến khi chuyển giao sang bước thanh toán PHẢI hoàn tất trong không quá 3 bước màn hình (Chi tiết sân -> Lưới khung giờ -> Màn hình xác nhận giữ chỗ).
- **FR-026**: Hệ thống PHẢI giới hạn người chơi được chọn tối đa 8 slot (tương đương 8 giờ chơi) trong một đơn đặt sân.
- **FR-027**: Mọi trạng thái đơn đặt sân được quản lý trong spec này PHẢI nằm trong tập trạng thái ban đầu của máy trạng thái: khởi tạo và giữ chỗ thành công chuyển sang `HOLD`, hủy bởi người dùng chuyển sang `CANCELLED`, quá thời gian giữ chỗ chuyển sang `EXPIRED`.
- **FR-028**: Hệ thống PHẢI hỗ trợ hiển thị giao diện rõ ràng trên cả 2 chế độ Sáng (Light mode) và Tối (Dark mode) với độ tương phản màu đạt chuẩn nhận diện trạng thái slot.

---

### Key Entities *(mandatory)*

- **VenueCourt (Sân con)**: Đại diện cho một sân cầu lông cụ thể trong cơ sở (ví dụ: Sân 1, Sân 2). Thuộc tính: `courtId`, `venueId`, `name`, `status` (ACTIVE / MAINTENANCE).
- **CourtSlot (Khung giờ sân)**: Đại diện cho một đơn vị thời gian có thể đặt của một sân con cụ thể trong một ngày. Thuộc tính: `slotId` (ID tất định theo định dạng `{courtId}_{yyyyMMdd}_{HHmm}`), `courtId`, `venueId`, `date` (`yyyyMMdd`), `startTime` (`HHmm`), `endTime` (`HHmm`), `status` (`AVAILABLE`, `HELD`, `BOOKED`, `BLOCKED`), `heldByUserId`, `holdExpiresAt` (thời điểm hết hạn giữ chỗ UTC).
- **PriceRule (Quy tắc giá)**: Đại diện cho cấu hình giá của cơ sở theo khung giờ và ngày. Thuộc tính: `ruleId`, `venueId`, `dayOfWeek` (thứ trong tuần), `startTime`, `endTime`, `pricePerHour` (đơn vị VND, kiểu Long), `ruleType` (REGULAR, PEAK, WEEKEND).
- **Booking (Đơn đặt chỗ)**: Đại diện cho đơn đặt của người chơi đang trong quá trình giữ chỗ. Thuộc tính: `bookingId`, `userId`, `venueId`, `totalAmount`, `status` (`HOLD`, `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`), `slotIds` (danh sách ID slot được giữ), `requestId` (UUID chống trùng lặp), `holdExpiresAt`, `createdAt`, `updatedAt`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Lưới khung giờ của một cơ sở (với tối đa 10 sân con và 16 khung giờ/ngày) tải và hiển thị hoàn tất dưới **3 giây** trên kết nối mạng di động 4G.
- **SC-002**: Đảm bảo **100% không trùng lịch** (Zero double-booking): Khi giả lập 10 người dùng cùng bấm giữ một slot duy nhất tại cùng một mili-giây, chính xác **1 người** thành công và **9 người** còn lại nhận thông báo từ chối an toàn mà không làm sai lệch trạng thái hệ thống.
- **SC-003**: Độ tin cậy giữ chỗ toàn vẹn (All-or-Nothing): **100%** trường hợp chọn nhiều slot mà có ít nhất 1 slot không khả dụng đều bị rollback an toàn, không có tình trạng giữ chỗ cục bộ một phần.
- **SC-004**: Cơ chế chống trùng đơn: Thao tác bấm nhiều lần liên tiếp hoặc thử lại khi timeout mạng tạo ra chính xác **1 đơn đặt chỗ** duy nhất, tỷ lệ tạo đơn trùng là **0%**.
- **SC-005**: Tự động giải phóng tài nguyên: **100%** các slot hết hạn 5 phút mà không tiếp tục thanh toán đều được hệ thống tự động giải phóng về trạng thái Còn trống cho người khác đặt.
- **SC-006**: Luồng thao tác gọn gàng: Người chơi có thể hoàn thành việc chọn slot và sang màn hình xác nhận giữ chỗ với không quá **3 lần chạm/bước màn hình** từ màn hình chi tiết sân.
- **SC-007**: Phản hồi thao tác người dùng: Thao tác bấm giữ chỗ và chuyển sang màn hình xác nhận phản hồi trong vòng **dưới 1.5 giây** trong điều kiện mạng bình thường.
- **SC-008**: Tỷ lệ khôi phục trạng thái sau đăng nhập: Đạt **100%** người dùng chưa đăng nhập sau khi hoàn thành đăng nhập quay lại đúng màn hình đặt sân và giữ nguyên các slot hợp lệ đã chọn.

---

## Assumptions

- Khung giờ hoạt động chuẩn của các cơ sở cầu lông nằm trong khoảng từ 05:00 sáng đến 23:00 đêm.
- Múi giờ chuẩn của toàn bộ hệ thống là giờ Việt Nam (`Asia/Ho_Chi_Minh`, UTC+7).
- Đơn vị tiền tệ hiển thị và tính toán trên toàn bộ hệ thống là Việt Nam Đồng (VND), không hỗ trợ đa tiền tệ.
- Người chơi có kết nối internet để tải dữ liệu thời gian thực và đồng bộ trạng thái giữ chỗ với máy chủ.
- Dữ liệu cơ sở (danh sách sân con, giờ mở cửa, bảng giá `priceRules`) đã được chủ sân thiết lập hoàn chỉnh và kích hoạt trong hệ thống.
- Việc xử lý giao dịch thanh toán tiền thật, đặt cọc và áp dụng mã khuyến mãi giảm giá thuộc phạm vi của tính năng tiếp theo (`specs/050-payment-voucher`).
