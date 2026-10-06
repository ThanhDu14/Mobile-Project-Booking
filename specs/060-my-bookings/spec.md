# Feature Specification: Quản lý lịch đặt của tôi

**Feature Branch**: `feature/06-my-bookings`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi đã đăng nhập quản lý toàn bộ đơn đặt sân của mình (đơn lẻ từ spec 040, đơn cố định theo tuần từ spec 041): xem danh sách theo trạng thái, xem chi tiết, hiển thị mã QR check-in tại sân, hủy đơn theo chính sách hoàn tiền ví giả lập, tiếp tục thanh toán đơn đang giữ chỗ (spec 050) và đặt lại nhanh từ lịch sử. Luồng chính: 1. Danh sách 'Lịch đặt của tôi' gồm 5 nhóm, mỗi nhóm một tab: (1) 'Chờ thanh toán': đơn HOLD còn hạn giữ chỗ, hiển thị đồng hồ đếm ngược theo giờ server và nút 'Thanh toán tiếp' mở lại màn hình thanh toán của spec 050 (không bắt đầu lại 5 phút). Đơn hết hạn giữ chỗ (EXPIRED) không hiển thị trong danh sách; (2) 'Chờ duyệt': đơn PENDING, hiển thị thời gian còn lại của hạn 60 phút chủ sân duyệt; (3) 'Sắp tới': đơn CONFIRMED chưa qua giờ chơi, sắp xếp theo giờ chơi gần nhất; (4) 'Đã hoàn thành': đơn COMPLETED; (5) 'Đã hủy': đơn CANCELLED hoặc REJECTED, hiển thị lý do và số tiền đã hoàn. Thẻ đơn hiển thị: tên cơ sở, sân con, ngày và khung giờ, tổng tiền, trạng thái thanh toán, nhãn 'Đặt cố định' nếu thuộc chuỗi của spec 041. Danh sách tải theo trang (phân trang), mới nhất trước. 2. Chi tiết đơn: mã đơn, mã nhóm (nếu là đơn cố định), tên và địa chỉ cơ sở, số điện thoại, nút mở bản đồ, danh sách slot/buổi, chi tiết tiền (tiền gốc, giảm giá voucher, tiền đã cọc hoặc đã trả bằng ví, số tiền còn lại phải trả tại sân), phương thức thanh toán, thời gian tạo đơn. 3. Mã QR check-in: đơn CONFIRMED hiển thị mã QR sinh từ mã bí mật do server cấp cho đơn. Người chơi xuất trình tại sân để chủ sân quét (việc quét thuộc spec 112). Mã QR vẫn hiển thị được khi mất mạng nếu đã từng tải chi tiết đơn. Chỉ chủ đơn mới thấy mã QR. 4. Hủy đơn: người chơi hủy được đơn PENDING hoặc CONFIRMED chưa đến giờ chơi. Hộp thoại xác nhận nêu rõ số tiền sẽ được hoàn hoặc mất, và bắt buộc chọn lý do hủy (danh sách lý do có sẵn, có mục 'Khác' cho nhập tự do). Chính sách hoàn tiền tính theo thời điểm hủy so với giờ bắt đầu của buổi: Đơn PENDING: luôn hoàn 100% số tiền đã trả; Đơn CONFIRMED hủy sớm (>= 240 phút trước giờ bắt đầu): hoàn 100% số tiền đã trả; Đơn CONFIRMED hủy trễ (< 240 phút): không hoàn số tiền đã trả. Theo phương thức thanh toán: đơn 'Thanh toán tại sân' hủy được nhưng không có gì để hoàn; đơn 'Đặt cọc' hoàn hoặc mất tiền cọc; đơn 'Ví giả lập' hoàn hoặc mất toàn bộ số đã trả. Tiền hoàn cộng vào ví giả lập và slot được nhả trong cùng một giao dịch hủy. 5. Tiếp tục thanh toán và đặt lại nhanh: từ tab 'Chờ thanh toán' mở lại thanh toán (spec 050). Từ tab 'Đã hoàn thành' hoặc 'Đã hủy', nút 'Đặt lại sân này' mở trực tiếp lưới khung giờ của cơ sở đó ở spec 040. Đơn cố định theo tuần (spec 041): xem danh sách các buổi cùng recurringGroupId; hủy riêng từng buổi hoặc tất cả các buổi chưa diễn ra theo chính sách 4 tiếng từng buổi; phân bổ tiền giảm giá và cọc theo tỷ lệ giá từng buổi làm tròn số nguyên đồng, dư dồn vào buổi cuối; hoàn tiền theo phần đã phân bổ. Luồng lỗi & biên: rỗng, mất mạng (thử lại), hủy lặp (idempotent), race condition trạng thái với chủ sân, lệch mốc 4 tiếng thì server quyết định, PENDING quá 60 phút tự chuyển đã hủy, CONFIRMED qua giờ chơi chưa check-in vào hoàn thành nhãn 'Chưa check-in', realtime cập nhật khi đang xem, auth guard, cơ sở bị ẩn/khóa. Tiêu chí đo được: tải trang đầu < 2s trên 4G; hủy và hoàn tiền < 1.5s; 100% đơn hủy hợp lệ hoàn đúng tiền và nhả slot trong cùng transaction; 2 yêu cầu đồng thời chỉ hoàn 1 lần; tổng tiền hoàn các buổi cố định khớp 100% không lệch số lẻ; ví giả lập không bao giờ âm. Ranh giới: bắt đầu mở Lịch đặt của tôi, kết thúc khi xem chi tiết, hiển thị QR, hủy hoặc đặt lại. Không gồm duyệt/quét QR (112), tạo đơn (040/041), thanh toán (050), đánh giá (070), đổi lịch. Ràng buộc: Constitution II, III, VI. Dữ liệu: bookings, slots, vouchers (+ redemptions), users. Cần làm rõ: mốc 4 tiếng cố định hay cấu hình theo cơ sở (với D); ai chuyển COMPLETED và xử lý no-show (với C và 112); mức mất tiền hủy trễ ví giả lập và hoàn lượt voucher (với 050)."

**Nhóm tính năng**: [06 — Quản lý lịch đặt](../../docs/features/06-booking-management/README.md) · **Phụ trách**: B · **Liên quan**: [spec 010](../010-account-auth/spec.md) (xác thực tài khoản), [spec 020](../020-court-search/spec.md) (khám phá tìm sân), [spec 040](../040-booking-slot-grid/spec.md) (đặt sân lẻ theo lưới giờ), [spec 041](../041-weekly-recurring-booking/spec.md) (đặt sân cố định theo tuần), [spec 050](../050-payment-voucher/spec.md) (thanh toán & ví giả lập), [spec 070](../070-review-favorite/spec.md) (đánh giá sau khi hoàn thành), [spec 111](../111-owner-venue-setup/spec.md) (cấu hình cơ sở & bảng giá), [spec 112](../112-owner-operations/spec.md) (chủ sân duyệt đơn & quét QR check-in)

## Clarifications

### Session 2026-10-06

- Q: Mốc thời gian hủy đơn để được hoàn 100% tiền cọc/ví là áp dụng cố định hay cấu hình theo cơ sở? → A: Áp dụng cố định **4 tiếng (240 phút)** trên toàn hệ thống trước giờ bắt đầu của buổi chơi cho mọi cơ sở sân (đồng nhất, dễ nhớ cho người chơi).
- Q: Xử lý đơn quá giờ chơi mà không đến check-in (no-show) như thế nào và ai chuyển trạng thái? → A: Hệ thống phía máy chủ tự động quét theo giờ kết thúc slot chơi và chuyển trạng thái đơn sang **`NO_SHOW`** (gom hiển thị trong tab "Đã hoàn thành" với nhãn cảnh báo *"Vắng mặt / Chưa check-in"*), đồng thời **khóa quyền viết đánh giá** ở spec 070 đối với đơn này (do người chơi không đến trải nghiệm thực tế).
- Q: Mức phạt tiền khi hủy đơn trễ đối với phương thức 'Ví giả lập' là bao nhiêu? → A: Đảm bảo tính công bằng giữa các phương thức: Đơn trả 100% qua ví giả lập khi hủy trễ (< 4 tiếng) **chỉ bị phạt 30%** (tương đương mức cọc), hệ thống tự động **hoàn lại 70% còn lại** vào số dư ví giả lập của người chơi. Đơn cọc mất 100% tiền cọc (hoàn 0đ), đơn trả tại sân hoàn 0đ.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem danh sách lịch đặt theo các tab trạng thái và xem chi tiết đơn (Priority: P1)

Người chơi đã đăng nhập mở màn hình "Lịch đặt của tôi" từ thanh điều hướng chính. Giao diện chia thành 5 tab trạng thái rõ ràng: (1) "Chờ thanh toán" (đơn `HOLD` còn hạn giữ chỗ, kèm đồng hồ đếm ngược và nút tiếp tục thanh toán), (2) "Chờ duyệt" (đơn `PENDING` kèm thời gian còn lại của hạn 60 phút chủ sân duyệt), (3) "Sắp tới" (đơn `CONFIRMED` sắp diễn ra, sắp xếp theo giờ chơi gần nhất), (4) "Đã hoàn thành" (đơn `COMPLETED` đã check-in hoặc đơn `NO_SHOW` quá giờ không đến sân kèm nhãn cảnh báo), và (5) "Đã hủy" (đơn `CANCELLED` hoặc `REJECTED`, hiển thị lý do và số tiền đã hoàn). Mỗi thẻ đơn trong danh sách hiển thị tóm tắt: tên cơ sở, sân con, ngày, khung giờ chơi, tổng tiền, trạng thái thanh toán và nhãn "Đặt cố định" nếu thuộc chuỗi định kỳ. Danh sách được tải phân trang và sắp xếp đơn mới nhất lên đầu. Khi người chơi bấm vào thẻ đơn, ứng dụng mở màn hình "Chi tiết đơn đặt" với đầy đủ thông tin: mã đơn, mã nhóm (nếu có), tên và địa chỉ cơ sở kèm nút mở bản đồ chỉ đường, số điện thoại hotline, chi tiết các slot/buổi chơi, chi tiết tài chính (tiền gốc, giảm giá voucher, tiền đã cọc hoặc thanh toán ví, số tiền còn lại phải trả tại sân), phương thức thanh toán và thời gian tạo đơn.

**Why this priority**: Đây là điểm truy cập trung tâm của toàn bộ nhóm chức năng quản lý lịch đặt. Nếu người chơi không xem được danh sách và chi tiết các đơn của mình, họ không thể quản lý, theo dõi hay thực hiện các thao tác tiếp theo như check-in hay hủy đơn.

**Independent Test**: Tạo một tài khoản có sẵn các đơn ở các trạng thái khác nhau (`HOLD`, `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`); mở màn hình "Lịch đặt của tôi", kiểm tra từng tab hiển thị chính xác các đơn tương ứng; bấm vào một đơn `CONFIRMED`, kiểm tra màn hình chi tiết hiển thị đầy đủ thông tin sân, giờ, tiền và nút liên quan.

**Acceptance Scenarios**:

1. **Given** người chơi có một đơn đang được giữ chỗ trong thời gian 5 phút (spec 040/041), **When** mở tab "Chờ thanh toán", **Then** thẻ đơn hiển thị đồng hồ đếm ngược thời gian giữ chỗ còn lại chính xác theo giờ máy chủ và hiển thị nút "Thanh toán tiếp".
2. **Given** một đơn giữ chỗ đã hết hạn 5 phút (`status == EXPIRED`), **When** người chơi xem danh sách các tab, **Then** đơn này không xuất hiện trong tab "Chờ thanh toán" và cũng không xuất hiện trong các tab khác (tự động ẩn khỏi giao diện để tránh rác màn hình).
3. **Given** người chơi có đơn ở trạng thái `PENDING` đã thanh toán cọc/ví giả lập, **When** mở tab "Chờ duyệt", **Then** thẻ đơn hiển thị rõ mốc thời gian còn lại trong hạn 60 phút chờ chủ sân duyệt (theo spec 112).
4. **Given** người chơi có nhiều đơn ở tab "Sắp tới" (`CONFIRMED`), **When** xem danh sách, **Then** các đơn được sắp xếp theo thời gian bắt đầu chơi tăng dần (đơn sắp diễn ra nhất được xếp lên đầu tiên).
5. **Given** danh sách đơn có số lượng lớn (> 10 đơn), **When** người chơi cuộn xuống cuối màn hình, **Then** ứng dụng tự động tải trang dữ liệu tiếp theo mượt mà dưới 1.5 giây mà không làm giật lag giao diện.
6. **Given** người chơi bấm vào một thẻ đơn bất kỳ, **When** màn hình chi tiết mở ra, **Then** ứng dụng hiển thị đầy đủ thông tin cơ sở (tên, địa chỉ, hotline, nút mở ứng dụng bản đồ ngoại vi), chi tiết từng slot/buổi chơi, và chi tiết tài chính minh bạch (tiền gốc, giảm giá, số tiền đã trả, số tiền còn thiếu phải thanh toán tại sân).

---

### User Story 2 - Hiển thị mã QR check-in tại sân cho đơn đã xác nhận (Priority: P1)

Với những đơn đặt sân ở trạng thái "Sắp tới" (`CONFIRMED`), màn hình chi tiết đơn hiển thị một thẻ Mã QR check-in trực quan được sinh động từ chuỗi mã bí mật (`qrToken`) do máy chủ cấp phát an toàn khi đơn được duyệt. Người chơi xuất trình mã QR này tại quầy tiếp đón của cơ sở sân để chủ sân dùng ứng dụng quản lý quét xác nhận check-in (phối hợp spec 112). Ứng dụng hỗ trợ lưu bộ nhớ đệm (caching) cục bộ để mã QR vẫn có thể hiển thị rõ ràng ngay cả khi thiết bị mất kết nối mạng Internet tại thời điểm đến sân, miễn là người chơi đã từng tải chi tiết đơn đó trước đó. Hệ thống đảm bảo chỉ có tài khoản chính chủ tạo đơn mới được quyền xem và hiển thị mã QR này.

**Why this priority**: Mã QR là cầu nối quan trọng giữa ứng dụng đặt sân của người chơi và quy trình vận hành đón khách tại sân của chủ sân (spec 112), thay thế cho giấy tờ truyền thống và phòng chống gian lận.

**Independent Test**: Mở chi tiết một đơn `CONFIRMED`; kiểm tra mã QR hiển thị rõ nét trên màn hình kèm hướng dẫn "Đưa mã này cho nhân viên lễ tân khi đến sân". Bật chế độ máy bay (ngắt kết nối mạng), thoát app rồi mở lại chi tiết đơn; kiểm tra mã QR vẫn hiển thị bình thường từ bộ nhớ đệm. Đăng nhập bằng tài khoản khác thử truy cập đơn, kiểm tra hệ thống chặn không cho xem.

**Acceptance Scenarios**:

1. **Given** đơn đặt sân ở trạng thái `CONFIRMED`, **When** người chơi xem màn hình chi tiết đơn, **Then** ứng dụng hiển thị khối Mã QR check-in nổi bật kèm mã đặt sân dạng chữ bên dưới và hướng dẫn xuất trình tại sân.
2. **Given** đơn đặt sân ở trạng thái khác (`HOLD`, `PENDING`, `COMPLETED`, `CANCELLED`, `NO_SHOW`), **When** người chơi xem màn hình chi tiết đơn, **Then** khối Mã QR check-in hoàn toàn không hiển thị.
3. **Given** người chơi đã mở chi tiết đơn `CONFIRMED` khi có mạng, **When** ngắt kết nối mạng di động/Wi-Fi và mở lại màn hình chi tiết đơn, **Then** mã QR vẫn được hiển thị nguyên vẹn từ bộ nhớ tạm cục bộ của thiết bị.
4. **Given** tài khoản người dùng B cố tình mở chi tiết đơn đặt của tài khoản A, **When** gửi yêu cầu lấy thông tin đơn hoặc mã QR, **Then** hệ thống phía server từ chối truy cập và báo lỗi không có quyền sở hữu đơn.

---

### User Story 3 - Hủy đơn đặt sân và hoàn tiền ví giả lập theo chính sách (Priority: P1)

Người chơi có thể thực hiện thao tác "Hủy đơn đặt sân" trực tiếp trên ứng dụng đối với các đơn ở trạng thái `PENDING` (đang chờ duyệt) hoặc `CONFIRMED` (đã xác nhận) trước thời điểm buổi chơi bắt đầu. Khi người chơi bấm nút "Hủy đơn", ứng dụng hiển thị hộp thoại xác nhận hủy nêu rõ chính sách hoàn tiền tương ứng với đơn và bắt buộc người chơi chọn một lý do hủy (từ danh sách lý do định sẵn hoặc chọn "Khác" để nhập lý do tự do). Chính sách hoàn tiền được hệ thống áp dụng căn cứ theo thời điểm hủy so với giờ bắt đầu chơi:
- Đối với đơn `PENDING` (chưa được chủ sân duyệt): Người chơi hủy bất kỳ lúc nào cũng được hoàn trả **100% số tiền đã thanh toán** (cả tiền cọc hoặc tiền ví).
- Đối với đơn `CONFIRMED` hủy sớm, từ **4 tiếng trở lên (≥ 240 phút)** trước giờ bắt đầu của buổi chơi đầu tiên: Hoàn trả **100% số tiền đã thanh toán** vào số dư ví giả lập.
- Đối với đơn `CONFIRMED` hủy trễ, **dưới 4 tiếng (< 240 phút)** trước giờ bắt đầu: Áp dụng chính sách phạt hủy trễ nhằm bảo vệ quyền lợi chủ sân và đảm bảo công bằng giữa các phương thức:
  + Đơn chọn "Đặt cọc": Mất toàn bộ 100% tiền cọc (hoàn 0đ).
  + Đơn chọn "Ví giả lập" (đã trả 100%): Phạt 30% giá trị đơn hàng (tương đương mức cọc), hệ thống tự động hoàn trả 70% còn lại vào số dư ví giả lập của người chơi.
  + Đơn chọn "Thanh toán tại sân": Hủy đơn thành công, giải phóng slot và không phát sinh giao dịch hoàn tiền vì người chơi chưa trả trước.

Toàn bộ thao tác hủy đơn, hoàn tiền vào ví giả lập và giải phóng các slot sân con tương ứng được thực thi trong cùng một giao dịch (transaction) an toàn ở phía máy chủ, đảm bảo tính duy nhất (idempotent), không trừ/hoàn tiền trùng lặp và slot được nhả ngay lập tức cho những người chơi khác trên toàn hệ thống.

**Why this priority**: Quyền hủy đơn là tính năng thiết yếu trong trải nghiệm người dùng, đồng thời cơ chế hoàn cọc tự động qua ví giả lập giúp người chơi an tâm đặt sân và tuân thủ các quy tắc văn minh trong cộng đồng thể thao.

**Independent Test**: Hủy một đơn `CONFIRMED` trước giờ chơi 5 tiếng; kiểm tra đơn chuyển sang trạng thái `CANCELLED`, số dư ví giả lập được cộng lại đúng 100% số tiền đã trả, và các slot trên lưới giờ lập tức chuyển về trạng thái "Còn trống". Thử hủy một đơn `CONFIRMED` trả qua ví giả lập 200.000đ trước giờ chơi 2 tiếng; kiểm tra đơn hủy thành công, ví được hoàn lại đúng 140.000đ (70%) và ghi nhận phạt 60.000đ (30%).

**Acceptance Scenarios**:

1. **Given** đơn ở trạng thái `PENDING` đã đặt cọc 60.000đ bằng ví giả lập, **When** người chơi bấm "Hủy đơn" và xác nhận lý do, **Then** hệ thống chuyển trạng thái đơn sang `CANCELLED`, cộng 60.000đ vào ví giả lập của người chơi, giải phóng các slot đang giữ, và hiển thị thông báo "Hủy đơn thành công, đã hoàn 60.000đ vào ví giả lập".
2. **Given** đơn `CONFIRMED` bắt đầu lúc 18:00 hôm nay và người chơi bấm hủy lúc 13:00 cùng ngày (còn 5 tiếng, ≥ 240 phút), **When** xác nhận hủy đơn, **Then** hệ thống tính toán hủy sớm hợp lệ, hoàn 100% số tiền đã thanh toán vào ví giả lập, chuyển đơn sang tab "Đã hủy" và nhả các slot ngay lập tức.
3. **Given** đơn `CONFIRMED` trả qua ví giả lập 200.000đ bắt đầu lúc 18:00 hôm nay và người chơi bấm hủy lúc 15:30 cùng ngày (còn 2.5 tiếng, < 240 phút), **When** mở hộp thoại hủy, **Then** hộp thoại hiển thị cảnh báo: "Đơn hủy trong vòng 4 tiếng trước giờ chơi sẽ bị phạt 30% phí hủy trễ (60.000đ) và hoàn lại 70% (140.000đ) vào ví giả lập"; khi người chơi bấm xác nhận hủy, hệ thống chuyển đơn sang `CANCELLED`, hoàn đúng 140.000đ vào ví giả lập và nhả slot ngay lập tức.
4. **Given** đơn chọn phương thức "Thanh toán tại sân" (`AT_VENUE`, chưa trả tiền), **When** người chơi bấm hủy đơn trước giờ chơi, **Then** hệ thống hủy đơn thành công, giải phóng slot và không phát sinh giao dịch hoàn tiền ví nào.
5. **Given** người chơi mở hộp thoại hủy đơn, **When** chưa chọn hoặc chưa nhập lý do hủy, **Then** nút "Xác nhận hủy đơn" bị vô hiệu hóa (disabled).
6. **Given** người chơi bấm nút "Xác nhận hủy đơn" 3 lần liên tiếp do mạng chập chờn, **When** yêu cầu gửi đến máy chủ, **Then** hệ thống xử lý idempotent qua `requestId`: chỉ hủy đơn đúng 1 lần, cộng tiền hoàn vào ví đúng 1 lần duy nhất, không tạo ra giao dịch hoàn tiền trùng lặp.

---

### User Story 4 - Quản lý và hủy riêng lẻ buổi trong chuỗi đặt cố định theo tuần (Priority: P2)

Với các đơn đặt cố định theo tuần (từ spec 041) thuộc cùng một nhóm `recurringGroupId`, người chơi có thể xem danh sách toàn bộ các buổi trong chu kỳ (ví dụ 8 buổi qua 8 tuần) kèm trạng thái độc lập của từng buổi (buổi đã chơi `COMPLETED`, buổi sắp tới `CONFIRMED`, buổi đã hủy `CANCELLED`). Người chơi có thể lựa chọn hủy riêng lẻ một hoặc vài buổi cụ thể trong chuỗi (ví dụ: bận đột xuất vào tuần thứ 3) hoặc bấm "Hủy tất cả các buổi còn lại". Chính sách thời hạn 4 tiếng và quy tắc hoàn tiền được áp dụng độc lập cho từng buổi được hủy:
- Số tiền giảm giá voucher và số tiền đặt cọc ban đầu của cả chuỗi được phân bổ đều cho từng buổi theo tỷ lệ giá trị buổi đó trên tổng giá trị chuỗi, làm tròn đến số nguyên đồng VND; phần tiền dư do làm tròn số lẻ được cộng dồn vào buổi cuối cùng của chuỗi.
- Khi người chơi hủy một buổi lẻ, số tiền hoàn trả vào ví giả lập được tính chính xác dựa trên phần tiền đã phân bổ cho buổi đó. Giới hạn tối thiểu 4 buổi của spec 041 chỉ áp dụng tại thời điểm tạo đơn đặt ban đầu, không áp dụng ràng buộc khi người chơi thực hiện thao tác hủy buổi trong quá trình sử dụng.

**Why this priority**: Người chơi cố định dài hạn thường xuyên phát sinh tình huống bận đột xuất một buổi trong chu kỳ nhiều tuần. Tính năng này mang lại sự linh hoạt tối đa mà vẫn đảm bảo tính toán tài chính minh bạch, chính xác.

**Independent Test**: Mở đơn cố định gồm 4 buổi (mỗi buổi 200.000đ, tổng 800.000đ, đã cọc 30% = 240.000đ, mỗi buổi phân bổ 60.000đ cọc); chọn hủy riêng buổi tuần thứ 2 trước giờ chơi 1 ngày; kiểm tra buổi đó chuyển sang trạng thái hủy, ví giả lập nhận hoàn đúng 60.000đ, 3 buổi còn lại vẫn giữ nguyên lịch và slot của buổi tuần thứ 2 được nhả ra trên lưới giờ.

**Acceptance Scenarios**:

1. **Given** người chơi mở chi tiết đơn đặt cố định theo tuần gồm 8 buổi, **When** màn hình hiển thị, **Then** giao diện liệt kê danh sách toàn bộ 8 buổi theo thứ tự ngày, hiển thị rõ ngày, giờ, sân con và trạng thái riêng của từng buổi.
2. **Given** người chơi chọn hủy một buổi cụ thể cách ngày chơi 3 ngày (≥ 240 phút), **When** xác nhận hủy buổi này, **Then** hệ thống chỉ chuyển buổi đó sang `CANCELLED`, hoàn đúng số tiền cọc/ví đã phân bổ của buổi đó vào ví giả lập, nhả slot buổi đó, trong khi tất cả các buổi còn lại trong chuỗi vẫn duy trì trạng thái `CONFIRMED`.
3. **Given** người chơi chọn hủy một buổi cụ thể cách giờ chơi 2 tiếng (< 240 phút), **When** xác nhận hủy, **Then** buổi đó bị hủy và nhả slot, nhưng không được hoàn tiền phân bổ của buổi đó.
4. **Given** một chuỗi cố định có tổng tiền cọc sau làm tròn là 250.000đ chia cho 3 buổi (buổi 1: 83.000đ, buổi 2: 83.000đ, buổi 3: 84.000đ), **When** người chơi lần lượt hủy từng buổi sớm, **Then** tổng số tiền hoàn vào ví giả lập qua 3 lần hủy luôn bằng chính xác 250.000đ, không phát sinh sai lệch dù chỉ 1 đồng.
5. **Given** người chơi bấm "Hủy toàn bộ chuỗi còn lại", **When** xác nhận, **Then** hệ thống duyệt qua tất cả các buổi chưa diễn ra, tính toán hoàn tiền cho từng buổi theo mốc 4 tiếng độc lập của buổi đó, và tổng hợp hoàn tiền 1 lần vào ví giả lập.

---

### User Story 5 - Tiếp tục thanh toán đơn giữ chỗ và đặt lại nhanh từ lịch sử (Priority: P2)

Từ danh sách lịch đặt, người chơi có các đường dẫn tắt tiện lợi để tiếp nối hành trình trải nghiệm:
1. Từ tab "Chờ thanh toán": Người chơi bấm nút "Thanh toán tiếp" trên thẻ đơn `HOLD` để mở lại màn hình thanh toán của spec 050. Đồng hồ đếm ngược tiếp tục chạy theo thời gian giữ chỗ còn lại của mốc `holdExpiresAt` phía server, tuyệt đối không tạo phiên giữ chỗ mới.
2. Từ tab "Đã hoàn thành" hoặc "Đã hủy": Người chơi bấm nút "Đặt lại sân này" trên thẻ đơn. Ứng dụng tự động điều hướng người chơi trực tiếp sang màn hình chi tiết cơ sở sân đó và mở lưới chọn khung giờ của spec 040 với các thông tin cơ sở đã được chọn sẵn, giúp người chơi không phải tìm kiếm lại từ đầu. Nếu cơ sở sân tại thời điểm đó đang bị đóng cửa tạm thời, ẩn (`HIDDEN`) hoặc khóa (`LOCKED`), ứng dụng thông báo rõ lý do và giữ người chơi ở lại màn hình hiện tại.

**Why this priority**: Rút ngắn thời gian thao tác cho người dùng trung thành muốn đặt lại sân quen thuộc hoặc người dùng vừa thoát app muốn quay lại hoàn tất đơn giữ chỗ dở dang.

**Independent Test**: Mở tab "Chờ thanh toán", bấm "Thanh toán tiếp", kiểm tra mở đúng màn hình thanh toán spec 050 và đồng hồ giữ chỗ khớp thời gian còn lại. Mở một đơn đã hoàn thành, bấm "Đặt lại sân này", kiểm tra mở đúng màn hình đặt sân của cơ sở đó ở spec 040.

**Acceptance Scenarios**:

1. **Given** người chơi có đơn giữ chỗ còn hiệu lực 02:45 trong tab "Chờ thanh toán", **When** bấm "Thanh toán tiếp", **Then** ứng dụng điều hướng sang màn hình thanh toán của spec 050 với đồng hồ hiển thị tiếp tục từ 02:45.
2. **Given** đơn giữ chỗ vừa hết hạn trong lúc người chơi đang xem tab "Chờ thanh toán", **When** đồng hồ chạm mốc 00:00, **Then** thẻ đơn tự động biến mất khỏi tab "Chờ thanh toán", nút thanh toán bị khóa và ứng dụng thông báo "Đơn giữ chỗ đã hết hạn".
3. **Given** người chơi bấm nút "Đặt lại sân này" từ một đơn đã hoàn thành tại cơ sở "Sân Cầu Lông ABC", **When** cơ sở đang hoạt động bình thường, **Then** ứng dụng điều hướng sang chi tiết cơ sở ABC và mở màn hình chọn ngày/khung giờ của spec 040.
4. **Given** cơ sở sân cũ đã bị chủ sân ẩn (`status == HIDDEN`) hoặc bị hệ thống khóa (`status == LOCKED`), **When** người chơi bấm "Đặt lại sân này", **Then** ứng dụng không chuyển trang, hiển thị thông báo lỗi rõ ràng: "Cơ sở sân này hiện đang tạm ngưng hoạt động hoặc không nhận đặt lịch".

---

### Edge Cases

- **Chênh lệch thời gian khi hủy sát nút mốc 4 tiếng**: Người chơi mở hộp thoại hủy lúc 13:59:58 (hiển thị còn 4 tiếng 2 giây, báo được hoàn tiền), nhưng bấm xác nhận gửi lên máy chủ lúc 14:00:02 (còn 3 tiếng 59 phút 58 giây). Phía server là nơi đưa ra quyết định thẩm quyền cuối cùng căn cứ theo timestamp máy chủ. Nếu kết quả thực tế rơi vào hủy trễ không được hoàn tiền, máy chủ trả về kết quả hủy thành công kèm cảnh báo rõ ràng rằng mốc thời gian nhận diện tại máy chủ đã quá hạn hoàn tiền, và giao diện làm mới cập nhật số tiền hoàn là 0đ.
- **Tranh chấp đồng thời giữa người chơi và chủ sân**: Trong lúc người chơi đang mở màn hình chi tiết và bấm "Hủy đơn", chủ sân ở spec 112 cùng lúc bấm "Duyệt đơn", "Từ chối" hoặc "Quét mã check-in". Máy chủ thực hiện kiểm tra trạng thái trong transaction: trạng thái nào được ghi nhận trước sẽ có hiệu lực; nếu đơn đã bị chủ sân từ chối trước đó, yêu cầu hủy của người chơi sẽ nhận thông báo "Đơn đã được chủ sân xử lý từ chối và tự động hoàn tiền", ứng dụng làm mới dữ liệu về tab "Đã hủy".
- **Hủy đơn đồng thời từ 2 thiết bị**: Người chơi đăng nhập cùng một tài khoản trên 2 thiết bị di động và bấm hủy cùng 1 đơn tại cùng một thời điểm. Giao dịch máy chủ sử dụng cơ chế khóa lạc quan (optimistic locking) hoặc Firestore Transaction đảm bảo chỉ có duy nhất 1 yêu cầu thành công, ví giả lập chỉ nhận tiền hoàn đúng 1 lần, yêu cầu thứ 2 nhận thông báo lỗi "Đơn đặt sân đã được hủy trước đó".
- **Mất kết nối mạng khi đang thực hiện hủy đơn**: Yêu cầu hủy gửi đi nhưng bị timeout kết nối hoặc mất mạng trước khi nhận phản hồi từ máy chủ. Người chơi bấm "Thử lại", hệ thống tái sử dụng cùng `requestId` (UUID) đảm bảo tính duy nhất (idempotency), không thực hiện hoàn tiền lần thứ hai.
- **Đơn PENDING bị chủ sân từ chối hoặc quá hạn 60 phút không duyệt**: Hệ thống phía server tự động chuyển trạng thái đơn sang `REJECTED` hoặc `CANCELLED`, hoàn trả 100% tiền cọc/ví (logic thuộc spec 050/112). Khi người chơi mở app hoặc xem danh sách, đơn tự động xuất hiện trong tab "Đã hủy" với ghi chú rõ ràng: "Đơn bị từ chối do chủ sân không tiếp nhận / quá hạn duyệt. Đã hoàn 100% tiền vào ví giả lập".
- **Đơn CONFIRMED đã qua giờ chơi mà người chơi không đến (No-show)**: Khi thời gian kết thúc của slot chơi đã trôi qua mà đơn chưa từng được check-in quét QR tại sân, hệ thống phía server tự động chuyển trạng thái đơn sang `NO_SHOW`. Đơn hiển thị trong tab "Đã hoàn thành" với nhãn cảnh báo rõ "Vắng mặt / Chưa check-in", không còn nút hủy đơn hay mã QR, và bị hệ thống khóa quyền viết đánh giá ở spec 070 do người chơi không đến trải nghiệm thực tế.
- **Cập nhật dữ liệu tức thì khi đang xem danh sách**: Khi người chơi đang mở tab "Chờ duyệt", nếu chủ sân duyệt đơn ở spec 112, ứng dụng tự động lắng nghe cập nhật dữ liệu (realtime snapshot listener) và chuyển thẻ đơn sang tab "Sắp tới" trong vòng dưới 3 giây mà người chơi không cần kéo vuốt tải lại trang thủ công.
- **Tài khoản người dùng bị khóa (`LOCKED`) hoặc chưa đăng nhập**: Người dùng chưa đăng nhập cố gắng truy cập màn hình lịch đặt sẽ được điều hướng đến màn hình đăng nhập (spec 010); tài khoản bị khóa bị từ chối xem lịch đặt và từ chối mọi thao tác hủy đơn.
- **Hoàn trả lượt dùng voucher khi đơn bị hủy**: Khi đơn đặt sân được hủy hợp lệ và hoàn tiền 100%, lượt sử dụng voucher của đơn đó (`usedCount` và bản ghi trong `redemptions`) được hệ thống hoàn trả lại để người chơi có thể sử dụng cho đơn tiếp theo; trường hợp hủy trễ không hoàn tiền thì lượt voucher không được hoàn trả.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI hiển thị màn hình "Lịch đặt của tôi" dành riêng cho người chơi đã đăng nhập, phân chia thành 5 tab trạng thái độc lập: "Chờ thanh toán", "Chờ duyệt", "Sắp tới", "Đã hoàn thành", và "Đã hủy".
- **FR-002**: Hệ thống PHẢI hiển thị trong tab "Chờ thanh toán" các đơn đặt ở trạng thái `HOLD` còn trong thời gian giữ chỗ, kèm đồng hồ đếm ngược thời gian thực theo mốc `holdExpiresAt` của máy chủ và nút "Thanh toán tiếp".
- **FR-003**: Khi bấm "Thanh toán tiếp", hệ thống PHẢI điều hướng người chơi quay lại màn hình thanh toán của spec 050 với phiên giữ chỗ hiện tại, tuyệt đối không tạo đơn giữ chỗ mới hoặc khởi tạo lại thời gian 5 phút.
- **FR-004**: Hệ thống PHẢI tự động ẩn hoàn toàn các đơn giữ chỗ đã hết hạn (`status == EXPIRED`) khỏi giao diện danh sách lịch đặt.
- **FR-005**: Hệ thống PHẢI hiển thị trong tab "Chờ duyệt" các đơn đặt ở trạng thái `PENDING`, hiển thị thời gian còn lại của hạn mức 60 phút chờ chủ sân duyệt.
- **FR-006**: Hệ thống PHẢI hiển thị trong tab "Sắp tới" các đơn đặt ở trạng thái `CONFIRMED` có giờ bắt đầu chơi chưa trôi qua, sắp xếp theo thứ tự thời gian bắt đầu chơi gần nhất lên trước.
- **FR-007**: Hệ thống PHẢI hiển thị trong tab "Đã hoàn thành" các đơn đặt ở trạng thái `COMPLETED` (đã quét QR check-in) và các đơn `NO_SHOW` (quá giờ chơi không check-in, kèm nhãn cảnh báo "Vắng mặt / Chưa check-in" và bị khóa quyền đánh giá ở spec 070).
- **FR-008**: Hệ thống PHẢI hiển thị trong tab "Đã hủy" các đơn đặt ở trạng thái `CANCELLED` (do người chơi hoặc chủ sân hủy) hoặc `REJECTED` (do chủ sân từ chối hoặc hết hạn 60 phút duyệt), hiển thị lý do hủy và số tiền đã hoàn trả.
- **FR-009**: Hệ thống PHẢI hỗ trợ tải danh sách theo cơ chế phân trang (pagination / infinite scroll), hiển thị tối thiểu 10 đơn mỗi trang và sắp xếp đơn mới tạo lên đầu.
- **FR-010**: Trên mỗi thẻ đơn tóm tắt, hệ thống PHẢI hiển thị: tên cơ sở sân, danh sách sân con, ngày chơi, khung giờ chơi, tổng tiền đơn, trạng thái thanh toán (`UNPAID`, `DEPOSITED`, `PAID`, `REFUNDED`), và nhãn "Đặt cố định" nếu đơn có `recurringGroupId`.
- **FR-011**: Khi người chơi bấm vào thẻ đơn, hệ thống PHẢI mở màn hình "Chi tiết đơn đặt" hiển thị: mã đơn (`bookingId`), mã nhóm cố định (nếu có), thông tin cơ sở (tên, địa chỉ, số hotline, nút mở bản đồ), danh sách các slot/buổi chơi, chi tiết tài chính (tiền gốc, giảm giá voucher, số tiền đã cọc/trả ví, số tiền còn lại phải trả tại sân), phương thức thanh toán, và thời gian tạo đơn.
- **FR-012**: Đối với đơn ở trạng thái `CONFIRMED`, hệ thống PHẢI hiển thị khối Mã QR check-in sinh từ mã bí mật `qrToken` do máy chủ cấp phát.
- **FR-013**: Hệ thống PHẢI lưu bộ nhớ tạm (cache) cục bộ mã QR check-in của các đơn `CONFIRMED` đã tải để đảm bảo người chơi vẫn hiển thị được mã QR khi thiết bị mất kết nối Internet tại sân.
- **FR-014**: Hệ thống PHẢI kiểm tra bảo mật: chỉ tài khoản sở hữu đơn (`uid == userId`) mới có quyền xem thông tin chi tiết và hiển thị mã QR check-in của đơn đó.
- **FR-015**: Hệ thống PHẢI cho phép người chơi bấm "Hủy đơn" đối với các đơn ở trạng thái `PENDING` hoặc `CONFIRMED` có thời gian bắt đầu chơi chưa trôi qua.
- **FR-016**: Khi bấm "Hủy đơn", hệ thống PHẢI hiển thị hộp thoại xác nhận hủy nêu rõ chính sách hoàn tiền tương ứng và bắt buộc người chơi chọn hoặc nhập lý do hủy trước khi cho phép xác nhận.
- **FR-017**: Đối với đơn `PENDING`, khi người chơi xác nhận hủy, hệ thống PHẢI hủy đơn và tự động hoàn trả 100% số tiền đã thanh toán (cọc hoặc ví) vào ví giả lập của người chơi.
- **FR-018**: Đối với đơn `CONFIRMED` hủy sớm từ 4 tiếng trở lên (≥ 240 phút tính từ thời điểm máy chủ tiếp nhận yêu cầu đến giờ bắt đầu chơi), hệ thống PHẢI hủy đơn và hoàn trả 100% số tiền đã thanh toán vào số dư ví giả lập.
- **FR-019**: Đối với đơn `CONFIRMED` hủy trễ dưới 4 tiếng (< 240 phút): đơn "Đặt cọc" mất 100% tiền cọc (hoàn 0đ); đơn "Ví giả lập" bị phạt 30% giá trị đơn hàng (tương đương mức cọc) và hệ thống PHẢI tự động hoàn trả 70% còn lại vào số dư ví giả lập của người chơi; đơn "Thanh toán tại sân" không phát sinh hoàn tiền.
- **FR-020**: Đối với đơn chọn phương thức "Thanh toán tại sân" (`AT_VENUE`), hệ thống PHẢI cho phép hủy đơn và giải phóng slot mà không phát sinh giao dịch hoàn tiền.
- **FR-021**: Thao tác hủy đơn, hoàn tiền ví giả lập và giải phóng các slot sân con (`slots`) PHẢI được thực hiện trong cùng một giao dịch an toàn (transaction) ở phía máy chủ.
- **FR-022**: Mọi yêu cầu hủy đơn PHẢI đi kèm mã định danh duy nhất (`requestId` UUID) để đảm bảo tính duy nhất (idempotency), ngăn chặn hoàn tiền lặp lại khi người dùng bấm liên tiếp hoặc thử lại mạng.
- **FR-023**: Đối với chuỗi đơn đặt cố định theo tuần (`recurringGroupId`), hệ thống PHẢI hiển thị danh sách từng buổi với trạng thái riêng biệt và cho phép người chơi chọn hủy riêng lẻ từng buổi hoặc hủy toàn bộ các buổi chưa diễn ra.
- **FR-024**: Khi hủy một buổi trong chuỗi cố định, hệ thống PHẢI tính toán số tiền hoàn dựa trên phần tiền cọc/ví đã được phân bổ cho buổi đó (chia theo tỷ lệ giá trị buổi trên tổng chuỗi, làm tròn số nguyên VND, phần dư dồn vào buổi cuối).
- **FR-025**: Từ tab "Đã hoàn thành" hoặc "Đã hủy", hệ thống PHẢI cung cấp nút "Đặt lại sân này" giúp điều hướng trực tiếp sang màn hình chi tiết cơ sở và lưới giờ của spec 040 với cơ sở đã chọn sẵn; nếu cơ sở bị ẩn hoặc khóa, hệ thống PHẢI thông báo lý do và giữ nguyên màn hình.
- **FR-026**: Hệ thống PHẢI tự động lắng nghe và cập nhật giao diện theo thời gian thực (realtime) khi trạng thái đơn thay đổi (chủ sân duyệt, từ chối hoặc hết hạn 60 phút) mà không yêu cầu người dùng vuốt tải lại thủ công.
- **FR-027**: Khi đơn đặt sân được hủy thành công và hoàn tiền 100%, hệ thống PHẢI tự động hoàn trả lại lượt sử dụng voucher (`usedCount` và bản ghi redemption) để người chơi có thể sử dụng lại mã đó.
- **FR-028**: Hệ thống PHẢI xử lý và hiển thị đầy đủ 4 trạng thái giao diện: Đang tải (shimmer/skeleton), Có dữ liệu, Rỗng (empty state thân thiện kèm nút khám phá sân), và Lỗi (kèm nút thử lại).
- **FR-029**: Khi quá thời gian kết thúc của slot chơi mà đơn `CONFIRMED` chưa từng được check-in tại sân, hệ thống phía máy chủ PHẢI tự động chuyển trạng thái đơn sang `NO_SHOW`, đồng thời chặn quyền tạo đánh giá đối với đơn này ở spec 070.

---

### Key Entities *(mandatory)*

- **Booking (Đơn đặt sân)**: Thực thể trung tâm lưu trữ thông tin đơn đặt. Thuộc tính chính: `bookingId`, `userId`, `venueId`, `recurringGroupId` (nếu có), `venueSnapshot` (`name`, `address`, `phone`), `items` (danh sách slot: `courtId`, `courtName`, `slotId`, `startAt`, `endAt`, `price`), `status` (`HOLD`, `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`, `NO_SHOW`), `subtotal`, `discount`, `total`, `depositAmount`, `paymentMethod` (`AT_VENUE`, `DEPOSIT`, `MOCK_WALLET`), `paymentStatus` (`UNPAID`, `DEPOSITED`, `PAID`, `REFUNDED`), `qrToken` (chuỗi bí mật dùng sinh mã QR), `checkedInAt`, `cancelReason`, `cancelledBy` (`USER`, `OWNER`, `SYSTEM`), `cancelledAt`, `refundAmount`, `createdAt`, `updatedAt`.
- **CourtSlot (Khung giờ sân con)**: Bản ghi chiếm dụng giờ trong `slots/{courtId}_{yyyyMMdd}_{HHmm}`. Khi đơn bị hủy hoặc hết hạn, các document này bị xóa để giải phóng slot về trạng thái còn trống.
- **UserWallet (Ví giả lập người dùng)**: Quản lý số dư tiền ảo trong `users/{uid}/walletBalance` (Long VND). Nhận tiền hoàn khi người chơi hủy đơn hợp lệ hoặc khi chủ sân từ chối đơn.
- **VoucherRedemption (Lượt dùng voucher)**: Bản ghi tại `vouchers/{code}/redemptions/{bookingId}`. Được xóa bỏ để hoàn lượt khi đơn được hoàn tiền 100%.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Thời gian tải trang đầu tiên của danh sách lịch đặt hoàn tất trong vòng **dưới 2 giây** trên kết nối mạng di động 4G.
- **SC-002**: Thao tác hủy đơn, cập nhật số dư ví giả lập và giải phóng slot hoàn tất phản hồi trong vòng **dưới 1.5 giây**.
- **SC-003**: Độ chính xác giải phóng tài nguyên: **100%** các đơn hủy thành công đều giải phóng toàn bộ các slot liên quan ngay trong cùng giao dịch phía máy chủ, hiển thị ngay trạng thái còn trống cho những người chơi khác.
- **SC-004**: Tính toàn vẹn tài chính: **0%** xảy ra trường hợp hoàn tiền ví giả lập trùng lặp khi người dùng bấm liên tiếp hoặc thử lại do mất mạng; **0%** trường hợp số dư ví giả lập bị âm sau giao dịch hoàn tiền.
- **SC-005**: Độ chính xác phân bổ đơn cố định: Tổng số tiền hoàn của tất cả các buổi khi hủy trong chuỗi định kỳ luôn bằng chính xác **100%** số tiền đã thanh toán của các buổi đó, không phát sinh sai lệch làm tròn dù chỉ 1 đồng VND.
- **SC-006**: Khả năng sẵn sàng của mã QR: **100%** các mã QR check-in đã từng tải thành công vào bộ nhớ tạm đều có thể hiển thị rõ nét trên màn hình khi thiết bị ngắt hoàn toàn kết nối mạng.
- **SC-007**: Tính minh bạch giao dịch học tập: **100%** các thông báo hoàn tiền, lịch sử hủy đơn đều hiển thị kèm nhãn rõ ràng: "Giao dịch giả lập phục vụ học tập".

---

## Assumptions

- Toàn bộ giao dịch hoàn tiền và số dư ví đều là giả lập phục vụ mục đích học tập nghiên cứu theo Nguyên tắc VI Constitution, không kết nối tài khoản ngân hàng thật.
- Đơn vị tiền tệ hiển thị và tính toán là Việt Nam Đồng (VND), lưu trữ dưới dạng số nguyên `Long`.
- Mốc thời hạn 4 tiếng (240 phút) áp dụng cố định trên toàn hệ thống để phân định hủy sớm (hoàn 100%) và hủy trễ, căn cứ theo thời gian máy chủ đồng bộ múi giờ `Asia/Ho_Chi_Minh`.
- Chính sách phạt hủy trễ: Đơn cọc mất 100% tiền cọc; đơn ví giả lập phạt 30% và hoàn 70% còn lại vào ví giả lập. Tiền phạt hủy trễ không chuyển cho bất kỳ tài khoản nào, chỉ được ghi nhận trong lịch sử đơn nhằm mô phỏng nghiệp vụ thực tế.
- Đơn quá giờ kết thúc chơi mà không check-in được hệ thống tự động chuyển sang trạng thái `NO_SHOW` và khóa quyền đánh giá ở spec 070.
- Thao tác quét mã QR check-in tại sân và duyệt đơn của chủ sân thuộc phạm vi của `specs/112-owner-operations`.
- Đánh giá chất lượng sân sau khi hoàn thành buổi chơi thuộc phạm vi của `specs/070-review-favorite`.
- Chức năng đề nghị đổi lịch (reschedule) tạm thời nằm ngoài phạm vi của phiên bản này (người chơi có thể hủy đơn cũ hợp lệ và đặt đơn mới).
