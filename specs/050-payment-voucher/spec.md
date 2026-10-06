# Feature Specification: Thanh toán và Khuyến mãi (Giả lập)

**Feature Branch**: `feature/05-payment-promotion`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi thanh toán cho đơn đặt sân đang được giữ chỗ (đơn lẻ từ spec 040 hoặc đơn cố định theo tuần từ spec 041) trong thời gian giữ chỗ còn lại. Toàn bộ thanh toán là GIẢ LẬP, phục vụ học tập, không thu tiền thật, và app hiển thị rõ điều đó. Luồng chính: 1. Nhận đơn đang giữ chỗ từ spec 040/041. Màn hình thanh toán tiếp tục đếm ngược thời gian còn lại của lần giữ chỗ đó (không bắt đầu lại 5 phút). 2. Hiển thị tóm tắt đơn: tên cơ sở, chi tiết slot/các buổi, tiền sân tạm tính. 3. Voucher: người chơi nhập mã hoặc chọn từ danh sách voucher khả dụng của cơ sở. Hệ thống kiểm tra hạn dùng, đơn tối thiểu, lượt dùng tối đa (chung và theo từng người). Voucher giảm theo % (có mức giảm tối đa) hoặc số tiền cố định; tổng phải trả không âm. Mỗi đơn tối đa 1 voucher, có thể gỡ để về giá gốc. Hiển thị lại tiền dưới 1 giây. 4. Chọn 1 trong 3 phương thức, luôn hiển thị cho mọi đơn: (1) Thanh toán tại sân: chưa trả tiền, trả khi đến sân; (2) Đặt cọc: trả trước một tỷ lệ tổng đơn bằng ví giả lập, phần còn lại trả tại sân; (3) Ví giả lập: trừ 100% từ ví của người chơi. 5. 'Xác nhận thanh toán': hệ thống kiểm tra lại toàn bộ (còn trong hạn giữ chỗ, voucher còn hợp lệ, ví đủ tiền) rồi xử lý giao dịch giả lập. Thành công thì đơn chuyển sang bước tiếp theo của máy trạng thái ở spec 040 và hiện hóa đơn điện tử (mã đơn, mã nhóm nếu là đơn cố định, tiền gốc, giảm giá, tiền cọc, tổng, phương thức, dòng 'Giao dịch giả lập phục vụ học tập'), kèm nút sang 'Lịch đặt của tôi' (spec 060). Đơn trả tại sân hiển thị là phiếu đặt sân, trạng thái chưa thanh toán. Giả định mặc định: Đơn cố định theo tuần: một lần thanh toán cho cả chuỗi; voucher, đơn tối thiểu và cọc tính trên tổng cả chuỗi. Lượt dùng voucher chỉ bị tính khi thanh toán thành công, không tính khi mới nhập mã. Voucher hết hiệu lực giữa lúc áp dụng và lúc xác nhận thì báo lỗi và tính lại giá. Tiền cọc là số nguyên đồng, làm tròn theo quy tắc ghi rõ trong spec. Nạp ví giả lập là chức năng học tập, không giới hạn, có nhãn 'giả lập'. Hết hạn giữ chỗ: Khi hết hạn giữ chỗ mà chưa thanh toán thành công, slot được coi là trống ngay tại thời điểm hết hạn đối với mọi người dùng khác. Hệ thống phía server tự chuyển đơn sang trạng thái hết hạn và dọn sạch toàn bộ slot của đơn trong vòng tối đa 2 phút sau hạn. App vô hiệu hóa nút thanh toán và hiển thị 'Đã hết thời gian giữ chỗ'. Xác nhận thanh toán gửi đến sau hạn bị từ chối. Luồng lỗi và trường hợp biên: Ví không đủ: báo số tiền thiếu, cho nạp ví giả lập hoặc đổi phương thức. Voucher sai: nêu rõ lý do (hết hạn, chưa đủ đơn tối thiểu, hết lượt, không áp dụng cho cơ sở). Mất mạng khi xác nhận: báo lỗi, có nút thử lại; thử lại hoặc bấm nhiều lần không trừ tiền ví hai lần và không tạo đơn trùng. Thoát app khi đang thanh toán: còn hạn thì mở lại đơn từ 'Lịch đặt của tôi' để thanh toán tiếp; quá hạn thì đơn đã hết hạn. Tổng sau giảm bằng 0: không cần chọn phương thức trả tiền. Chỉ chủ đơn mới thanh toán được; chưa đăng nhập hoặc tài khoản bị khóa thì không. Tiêu chí đo được: Áp voucher và cập nhật tiền dưới 1 giây; xác nhận thanh toán giả lập dưới 1,5 giây trên 4G. 100% đơn hết hạn giữ chỗ được chuyển trạng thái hết hạn và nhả sạch slot trong vòng tối đa 2 phút sau hạn. Gửi lặp cùng một yêu cầu xác nhận: ví chỉ bị trừ 1 lần. Hai người cùng dùng lượt voucher cuối: đúng 1 người thành công. Hai đơn cùng trừ một ví: số dư không bao giờ âm. Ranh giới: Bắt đầu khi nhận đơn đang giữ chỗ, kết thúc khi hiển thị hóa đơn/phiếu đặt sân. Không bao gồm: tạo đơn và giữ chỗ (040, 041); chủ sân duyệt đơn và check-in QR (112, 060); hủy và hoàn tiền (060); cổng thanh toán thật hoặc lưu thông tin thẻ (bị cấm theo constitution nguyên tắc VI: thanh toán chỉ giả lập). Ràng buộc: constitution nguyên tắc II (server kiểm tra tiền, voucher, trạng thái đơn), nguyên tắc III (tiền là số nguyên VND, giữ chỗ có hạn theo thời gian server, thao tác idempotent) và nguyên tắc VI (thanh toán giả lập, ghi rõ bản học tập); cách triển khai để phần /speckit-plan. Dữ liệu: bookings, vouchers (+ redemptions), users theo docs/design/database/README.md. Điểm giao cần làm rõ: Sau thanh toán đơn sang chờ duyệt hay tự xác nhận theo từng cơ sở; slot giữ bao lâu khi chủ sân chưa phản hồi; ai hoàn ví khi chủ sân từ chối (với D, spec 112). Tỷ lệ cọc: cố định hay chủ sân cấu hình trong bảng giá (với D). Số dư ví ban đầu và ai quản lý trường số dư trong users (với A). Danh sách 'Lịch đặt của tôi' có hiển thị đơn đang giữ chỗ chưa thanh toán (với spec 060)."

**Nhóm tính năng**: [05 — Thanh toán và khuyến mãi](../../docs/features/05-payment-promotion/README.md) · **Phụ trách**: B · **Liên quan**: [spec 010](../010-account-auth/spec.md) (xác thực người dùng), [spec 040](../040-booking-slot-grid/spec.md) (đơn giữ chỗ ngày lẻ), [spec 041](../041-weekly-recurring-booking/spec.md) (đơn giữ chỗ định kỳ), [spec 060](../060-my-bookings/spec.md) (lịch đặt của tôi / hủy đơn), [spec 112](../112-owner-operations/spec.md) (chủ sân duyệt đơn)

## Clarifications

### Session 2026-10-06

- Q: Sau khi thanh toán thành công, đơn chuyển sang trạng thái nào và xử lý hoàn tiền ra sao nếu chủ sân từ chối? → A: Mọi đơn sau khi thanh toán thành công chuyển sang trạng thái chờ duyệt (`PENDING`); chủ sân có tối đa 60 phút để duyệt đơn (phối hợp spec 112). Nếu chủ sân từ chối hoặc quá hạn 60 phút mà chưa duyệt, hệ thống tự động hủy đơn và hoàn trả 100% số tiền đã trừ (cọc hoặc ví) vào số dư ví giả lập của người chơi.
- Q: Tỷ lệ phần trăm đặt cọc khi người chơi chọn phương thức 'Đặt cọc' là bao nhiêu? → A: Tỷ lệ đặt cọc khi chọn `DEPOSIT` được áp dụng cố định là **30%** trên tổng giá trị đơn hàng cho toàn hệ thống, làm tròn đến hàng nghìn đồng chẵn gần nhất (ví dụ: 30% của 175.000đ = 52.500đ làm tròn thành 53.000đ).
- Q: Số dư ví giả lập ban đầu của tài khoản mới là bao nhiêu và tính năng 'Nạp ví giả lập' được đặt ở đâu? → A: Số dư ví giả lập ban đầu của tài khoản mới được khởi tạo là **0đ**. Người dùng có thể bấm nút "Nạp ví giả lập" (cộng số dư ảo để test luồng với các mệnh giá gợi ý như 100.000đ, 500.000đ, 1.000.000đ có nhãn "Tính năng thử nghiệm / giả lập") ngay tại màn hình thanh toán hoặc trong trang cá nhân (phối hợp spec 011).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Áp dụng mã khuyến mãi (Voucher) cho đơn đặt sân (Priority: P1)

Từ màn hình thanh toán sau khi nhận đơn giữ chỗ (spec 040 hoặc spec 041), người chơi xem tóm tắt thông tin đơn và tiền tạm tính. Người chơi có thể nhập mã voucher hoặc chọn từ danh sách voucher khả dụng của cơ sở/hệ thống. Hệ thống kiểm tra tính hợp lệ: thời hạn hiệu lực, giá trị đơn tối thiểu (`minOrderTotal`), số lượt dùng còn lại (`usedCount` < `usageLimit`), và giới hạn mỗi người dùng (`perUserLimit`). Nếu hợp lệ, hệ thống tính mức giảm giá (theo phần trăm có mức giảm tối đa `maxDiscount`, hoặc số tiền cố định), cập nhật lại tổng tiền thanh toán trong vòng dưới 1 giây. Mỗi đơn chỉ áp dụng tối đa 1 voucher, người chơi có thể bấm gỡ voucher để hoàn về giá gốc. Nếu tổng tiền sau giảm giá bằng 0đ, hệ thống cho phép hoàn tất đơn ngay mà không cần chọn phương thức thanh toán.

**Why this priority**: Khuyến mãi là công cụ thúc đẩy đặt sân và gắn liền với số tiền cuối cùng người chơi phải trả. Việc tính toán giảm giá chính xác là điều kiện tiên quyết trước khi thực hiện giao dịch thanh toán.

**Independent Test**: Mở màn hình thanh toán của một đơn có giá 200.000đ; nhập mã voucher giảm 20% (tối đa 30.000đ), kiểm tra tổng tiền hiển thị giảm xuống còn 170.000đ; bấm gỡ voucher, kiểm tra giá trở lại 200.000đ. Nhập mã hết hạn hoặc mã có điều kiện đơn tối thiểu 300.000đ, kiểm tra hệ thống báo lỗi rõ ràng và giữ nguyên giá gốc.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình thanh toán với đơn có tiền tạm tính 300.000đ, **When** nhập mã voucher giảm 10% (không giới hạn tối đa) và bấm "Áp dụng", **Then** hệ thống tính mức giảm 30.000đ, hiển thị tổng tiền phải trả là 270.000đ trong thời gian dưới 1 giây.
2. **Given** voucher có quy định giảm 50% nhưng tối đa 40.000đ, **When** áp dụng cho đơn 150.000đ, **Then** mức giảm giá hiển thị chính xác là 40.000đ (chạm trần `maxDiscount`), tổng tiền phải trả là 110.000đ.
3. **Given** voucher có yêu cầu giá trị đơn tối thiểu 200.000đ, **When** người chơi áp dụng cho đơn có tiền tạm tính 160.000đ, **Then** hệ thống từ chối áp dụng và thông báo rõ: "Đơn hàng chưa đạt giá trị tối thiểu 200.000đ".
4. **Given** voucher đã hết hạn sử dụng hoặc đã hết tổng lượt sử dụng trên hệ thống, **When** người chơi nhập mã, **Then** hệ thống từ chối và hiển thị thông báo "Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng".
5. **Given** người chơi đã từng sử dụng voucher này đạt giới hạn số lần cho phép (`perUserLimit`), **When** nhập mã cho đơn mới, **Then** hệ thống từ chối và báo "Bạn đã sử dụng hết lượt của mã khuyến mãi này".
6. **Given** người chơi đã áp dụng thành công một voucher, **When** bấm nút "Gỡ bỏ", **Then** voucher bị hủy khỏi đơn, mức giảm giá trở về 0đ và tổng tiền hiển thị khôi phục về tiền tạm tính ban đầu.
7. **Given** voucher giảm giá số tiền cố định lớn hơn hoặc bằng tổng tiền sân (ví dụ voucher giảm 100.000đ cho đơn 80.000đ), **When** áp dụng, **Then** tổng tiền phải trả hiển thị là 0đ (không bị âm tiền), các tùy chọn phương thức thanh toán chuyển sang trạng thái "Miễn phí" và người chơi có thể bấm xác nhận hoàn tất ngay.

---

### User Story 2 - Lựa chọn phương thức thanh toán và xác nhận giao dịch giả lập (Priority: P1)

Người chơi lựa chọn 1 trong 3 phương thức thanh toán luôn hiển thị cho mọi đơn: (1) Thanh toán tại sân (`AT_VENUE`), (2) Đặt cọc (`DEPOSIT`), (3) Ví giả lập (`MOCK_WALLET`). Khi người chơi bấm "Xác nhận thanh toán", hệ thống kiểm tra toàn diện (đơn còn trong thời gian giữ chỗ, voucher còn hiệu lực, số dư ví đủ nếu chọn cọc hoặc ví giả lập) và xử lý giao dịch giả lập. Khi thành công, trạng thái thanh toán được cập nhật tương ứng, đơn chuyển sang trạng thái chờ duyệt (`PENDING` - chủ sân có tối đa 60 phút để duyệt đơn theo spec 112; nếu bị từ chối hoặc quá 60 phút chưa duyệt, hệ thống tự động hoàn 100% tiền cọc/ví cho người chơi), và màn hình hiển thị Hóa đơn điện tử (E-receipt) hoặc Phiếu đặt sân với đầy đủ chi tiết, có ghi chú rõ ràng "Giao dịch giả lập phục vụ học tập" cùng nút điều hướng sang "Lịch đặt của tôi" (spec 060).

**Why this priority**: Đây là bước hoàn tất luồng đặt sân, chốt đơn giữ chỗ thành đơn đặt chính thức và cung cấp chứng từ điện tử cho người chơi. Tuân thủ cam kết môi trường giả lập phi thương mại của Constitution (Nguyên tắc VI).

**Independent Test**: Thực hiện thanh toán một đơn bằng ví giả lập đủ tiền; kiểm tra đơn chuyển trạng thái thành công sang PENDING, số dư ví bị trừ chính xác số tiền, hiển thị màn hình hóa đơn điện tử với nhãn "Giao dịch giả lập phục vụ học tập" và nút xem lịch đặt. Thử với phương thức tại sân, kiểm tra phiếu đặt sân hiển thị trạng thái "Chưa thanh toán".

**Acceptance Scenarios**:

1. **Given** người chơi chọn phương thức "Thanh toán tại sân" cho đơn 200.000đ, **When** bấm "Xác nhận đặt sân", **Then** hệ thống không trừ tiền ví, cập nhật trạng thái thanh toán `paymentStatus = UNPAID`, chuyển trạng thái đơn sang `PENDING` (chờ chủ sân duyệt trong tối đa 60 phút), và hiển thị Phiếu đặt sân với tổng tiền phải trả tại sân là 200.000đ.
2. **Given** người chơi chọn phương thức "Đặt cọc" với tỷ lệ 30% cho đơn 500.000đ, **When** bấm "Xác nhận đặt cọc", **Then** hệ thống trừ 150.000đ từ ví giả lập (30% làm tròn nghìn đồng), cập nhật `depositAmount = 150000`, `paymentStatus = DEPOSITED`, chuyển trạng thái đơn sang `PENDING`, và hiển thị Hóa đơn điện tử ghi rõ: đã cọc 150.000đ, số tiền còn lại trả tại sân là 350.000đ.
3. **Given** người chơi chọn phương thức "Ví giả lập" cho đơn 300.000đ và ví có số dư 500.000đ, **When** bấm "Xác nhận thanh toán", **Then** hệ thống trừ 300.000đ từ ví giả lập, số dư ví còn lại 200.000đ, `paymentStatus = PAID`, chuyển trạng thái đơn sang `PENDING`, và hiển thị Hóa đơn điện tử đã thanh toán 100%.
4. **Given** đơn đặt cố định theo tuần (từ spec 041) gồm 8 buổi với tổng tiền 1.600.000đ, **When** thanh toán, **Then** hệ thống áp dụng một lần thanh toán cho cả chuỗi đơn, hóa đơn hiển thị mã nhóm `recurringGroupId`, danh sách các buổi và tổng tiền cả chu kỳ.
5. **Given** giao dịch thanh toán thành công, **When** hiển thị màn hình Hóa đơn điện tử, **Then** màn hình có dòng thông báo nổi bật: "Giao dịch giả lập phục vụ học tập - Không thu tiền thật" và cung cấp nút bấm "Xem lịch đặt của tôi" để điều hướng sang spec 060.
6. **Given** người chơi chưa đăng nhập hoặc tài khoản đang bị khóa (`accountStatus == LOCKED`), **When** cố gắng thực hiện thanh toán, **Then** hệ thống từ chối xử lý và thông báo lỗi tương ứng.

---

### User Story 3 - Xử lý hết hạn giữ chỗ trong lúc thanh toán (Priority: P1)

Màn hình thanh toán tiếp tục kế thừa đồng hồ đếm ngược thời gian giữ chỗ còn lại từ spec 040 hoặc spec 041 căn cứ theo mốc thời gian máy chủ `holdExpiresAt`. Nếu đồng hồ đếm ngược chạm mốc 00:00 mà người chơi chưa hoàn tất xác nhận thanh toán thành công, hệ thống tự động coi các slot là trống ngay lập tức đối với những người dùng khác. Hệ thống phía server chuyển trạng thái đơn sang `EXPIRED` và dọn sạch dữ liệu slot trong vòng tối đa 2 phút sau hạn. Ứng dụng lập tức vô hiệu hóa nút thanh toán, hiển thị thông báo "Đã hết thời gian giữ chỗ" và không cho phép thực hiện bất kỳ giao dịch trừ tiền nào.

**Why this priority**: Bảo vệ tính toàn vẹn dữ liệu đặt lịch theo Nguyên tắc III Constitution. Không cho phép người dùng thanh toán cho một đơn giữ chỗ đã hết hạn vì các slot đó có thể đã được người khác đặt.

**Independent Test**: Giữ chỗ một đơn, mở màn hình thanh toán và chờ đồng hồ đếm ngược hết 5 phút; kiểm tra tại mốc 00:00 nút thanh toán bị khóa, bấm thử thì hệ thống từ chối; kiểm tra trên thiết bị khác slot đó đã chuyển về "Còn trống" và có thể đặt được.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình thanh toán với đồng hồ giữ chỗ còn 00:05, **When** thời gian chạm mốc 00:00, **Then** nút "Xác nhận thanh toán" lập tức bị vô hiệu hóa (disabled), hiển thị hộp thoại thông báo: "Đã hết thời gian giữ chỗ. Đơn đặt sân đã tự động hủy".
2. **Given** thời gian giữ chỗ đã hết, **When** người chơi cố tình gửi yêu cầu thanh toán (ví dụ do độ trễ mạng hoặc client gửi muộn), **Then** hệ thống phía server từ chối giao dịch, không trừ tiền ví, và báo lỗi đơn đã hết hạn.
3. **Given** đơn giữ chỗ hết hạn lúc 18:05:00, **When** đến 18:05:01, **Then** bất kỳ người chơi nào khác xem lưới giờ đều thấy các slot này ở trạng thái "Còn trống" và có thể giữ chỗ bình thường mà không cần chờ tác vụ dọn dẹp chạy xong.
4. **Given** đơn đã hết hạn giữ chỗ, **When** tác vụ dọn dẹp của hệ thống chạy theo lịch, **Then** đơn được chuyển trạng thái sang `EXPIRED` và các bản ghi slot liên quan được dọn dẹp sạch sẽ trong vòng tối đa 2 phút sau hạn.

---

### User Story 4 - Nạp tiền vào ví giả lập và xử lý số dư không đủ (Priority: P2)

Khi người chơi chọn phương thức "Ví giả lập" hoặc "Đặt cọc" nhưng số dư ví giả lập hiện tại nhỏ hơn số tiền cần thanh toán, hệ thống thông báo rõ số tiền còn thiếu. Để phục vụ mục đích học tập và thử nghiệm luồng ứng dụng thuận tiện, hệ thống cung cấp tính năng "Nạp ví giả lập" (cộng số tiền ảo tùy chọn với nhãn rõ ràng "Tính năng thử nghiệm / giả lập") hoặc cho phép người chơi dễ dàng chuyển đổi sang phương thức khác.

**Why this priority**: Giúp người dùng và người chấm điểm/giảng viên trải nghiệm trọn vẹn luồng thanh toán ví ảo mà không bị tắc nghẽn do thiếu số dư, đồng thời minh bạch đây là tính năng học tập phi thương mại.

**Independent Test**: Dùng tài khoản có số dư ví 50.000đ thanh toán đơn 200.000đ; kiểm tra app báo "Số dư ví không đủ (thiếu 150.000đ)" và hiển thị nút "Nạp ví giả lập"; bấm nạp thêm 200.000đ ảo, kiểm tra số dư cập nhật lên 250.000đ và thanh toán thành công ngay sau đó.

**Acceptance Scenarios**:

1. **Given** người chơi chọn phương thức ví giả lập cho đơn 200.000đ nhưng số dư ví chỉ có 50.000đ, **When** bấm xác nhận, **Then** hệ thống không trừ tiền, hiển thị thông báo: "Số dư ví giả lập không đủ. Bạn còn thiếu 150.000đ" kèm nút "Nạp ví giả lập".
2. **Given** người chơi bấm nút "Nạp ví giả lập", **When** chọn một mệnh giá nạp ảo (ví dụ: 100.000đ, 500.000đ, 1.000.000đ) và bấm xác nhận nạp, **Then** số dư ví giả lập được cộng thêm ngay lập tức và người chơi có thể tiếp tục thanh toán đơn đang giữ.
3. **Given** người chơi không muốn nạp ví, **When** bấm đổi phương thức, **Then** người chơi có thể chọn sang "Thanh toán tại sân" để hoàn tất đơn mà không bị hủy phiên giữ chỗ.

---

### User Story 5 - Đảm bảo tính toàn vẹn giao dịch và chống trừ tiền trùng lặp (Priority: P3)

Trong điều kiện mạng di động 4G chập chờn, khi người chơi bấm xác nhận thanh toán nhưng yêu cầu bị timeout hoặc người chơi bấm nút nhiều lần liên tiếp, hệ thống sử dụng mã định danh yêu cầu duy nhất (`requestId` UUID) để đảm bảo tính duy nhất (idempotency). Hệ thống cam kết: ví chỉ bị trừ đúng một lần duy nhất, không tạo ra đơn trùng lặp, xử lý an toàn khi hai người cùng tranh chấp lượt voucher cuối cùng, và số dư ví giả lập không bao giờ bị âm dưới mọi tình huống tranh chấp đồng thời.

**Why this priority**: Đảm bảo an toàn tài chính (dù là môi trường giả lập) và tính toàn vẹn dữ liệu máy chủ theo Nguyên tắc II & III Constitution, tránh lỗi trừ trùng số dư hay vượt hạn mức khuyến mãi.

**Independent Test**: Giả lập kịch bản người dùng bấm "Xác nhận thanh toán" 5 lần liên tiếp trong 500ms; kiểm tra ví chỉ bị trừ tiền đúng 1 lần và chỉ có 1 bản ghi giao dịch thành công. Giả lập voucher chỉ còn 1 lượt dùng cho 2 tài khoản bấm thanh toán cùng lúc; kiểm tra chỉ 1 tài khoản được trừ giá khuyến mãi.

**Acceptance Scenarios**:

1. **Given** người chơi bấm nút xác nhận thanh toán nhiều lần liên tiếp thật nhanh, **When** các yêu cầu đến máy chủ, **Then** hệ thống nhận diện cùng một `requestId`, chỉ thực hiện trừ ví 1 lần duy nhất và trả về cùng một kết quả hóa đơn thành công.
2. **Given** người chơi gặp sự cố mất mạng khi đang gửi yêu cầu xác nhận, **When** bấm nút "Thử lại" sau khi có mạng lại, **Then** hệ thống xử lý idempotent, không trừ tiền hai lần cho cùng một phiên thanh toán.
3. **Given** một voucher chỉ còn đúng 1 lượt sử dụng cuối cùng trên hệ thống, **When** hai người chơi cùng bấm xác nhận thanh toán áp dụng mã này tại cùng thời điểm, **Then** hệ thống chỉ cho phép đúng 1 người chơi áp dụng voucher thành công; người chơi còn lại nhận thông báo voucher đã hết lượt và được yêu cầu xác nhận thanh toán theo giá gốc.
4. **Given** người chơi mở 2 thiết bị khác nhau cùng thanh toán 2 đơn hàng bằng cùng một ví giả lập có số dư 300.000đ (mỗi đơn 200.000đ), **When** cả hai đơn cùng bấm thanh toán đồng thời, **Then** giao dịch được kiểm soát chặt chẽ ở máy chủ: một đơn thành công (ví còn 100.000đ) và đơn còn lại bị từ chối do không đủ số dư, số dư ví không bao giờ bị âm.

---

### Edge Cases

- **Voucher hết hiệu lực giữa lúc áp dụng và lúc bấm xác nhận**: Người chơi nhập voucher lúc voucher còn hạn, nhưng để màn hình thanh toán chờ 3 phút khiến voucher hết hạn hoặc hết lượt trước khi bấm xác nhận. Khi bấm xác nhận, hệ thống kiểm tra lại điều kiện tại thời điểm commit, từ chối voucher, thông báo cho người chơi và tính lại tổng tiền theo giá gốc.
- **Thoát ứng dụng khi đang ở màn hình thanh toán**: Người chơi vô tình tắt app hoặc chuyển sang ứng dụng khác khi đang ở màn hình thanh toán. Nếu quay lại app khi thời gian giữ chỗ vẫn còn, người chơi có thể mở lại đơn từ mục "Đơn chờ thanh toán" trong "Lịch đặt của tôi" (spec 060) để tiếp tục thanh toán; nếu đã quá hạn, đơn đã tự động chuyển sang `EXPIRED`.
- **Làm tròn số tiền khi tính tiền cọc**: Khi tính tỷ lệ đặt cọc cố định 30% (ví dụ 30% của đơn 175.000đ = 52.500đ), hệ thống làm tròn số tiền cọc đến hàng nghìn đồng gần nhất (làm tròn thành 53.000đ) để đảm bảo số tiền cọc và số tiền còn lại đều là số nguyên đồng chẵn, không phát sinh số lẻ.
- **Chỉ chủ sở hữu đơn mới được thanh toán**: Hệ thống phía server kiểm tra người gửi yêu cầu thanh toán phải có `uid` trùng khớp với `userId` tạo đơn giữ chỗ; không cho phép tài khoản khác thanh toán thay trừ khi có chức năng thanh toán hộ được đặc tả riêng.
- **Ràng buộc hoàn tiền khi đơn bị chủ sân từ chối hoặc quá hạn duyệt**: Nếu đơn ở trạng thái `DEPOSITED` hoặc `PAID` bị chủ sân từ chối tiếp nhận (ở spec 112) hoặc sau 60 phút chủ sân không phản hồi duyệt đơn, hệ thống tự động hủy đơn và hoàn lại 100% số tiền đã trừ (cọc hoặc toàn bộ) vào số dư ví giả lập của người chơi.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI nhận đơn đặt sân ở trạng thái `HOLD` từ spec 040 (đơn lẻ) hoặc spec 041 (đơn cố định theo tuần) và hiển thị màn hình thanh toán tương ứng.
- **FR-002**: Hệ thống PHẢI tiếp tục đếm ngược thời gian giữ chỗ còn lại của đơn dựa trên mốc thời gian máy chủ `holdExpiresAt`, tuyệt đối không khởi tạo lại 5 phút mới.
- **FR-003**: Hệ thống PHẢI hiển thị bảng tóm tắt đơn hàng gồm: tên cơ sở, địa chỉ, danh sách chi tiết các slot/buổi chơi, đơn giá từng slot và tiền tạm tính (`subtotal`).
- **FR-004**: Hệ thống PHẢI hiển thị dòng thông báo nổi bật trên giao diện: "Hệ thống thanh toán giả lập phục vụ học tập - Không thu tiền thật" trên mọi màn hình liên quan đến thanh toán.
- **FR-005**: Hệ thống PHẢI cho phép người chơi nhập mã voucher hoặc chọn từ danh sách voucher hợp lệ của cơ sở và voucher toàn hệ thống.
- **FR-006**: Hệ thống PHẢI kiểm tra đầy đủ các điều kiện hiệu lực của voucher: khoảng thời gian hợp lệ (`validFrom` đến `validTo`), giá trị đơn tối thiểu (`minOrderTotal`), tổng số lượt dùng còn lại (`usedCount` < `usageLimit`), và số lượt dùng của người chơi (`perUserLimit`).
- **FR-007**: Hệ thống PHẢI tính toán chính xác mức giảm giá theo quy tắc của voucher: giảm theo tỷ lệ phần trăm (áp dụng trần `maxDiscount` nếu có) hoặc giảm theo số tiền cố định, và cập nhật lại tổng tiền thanh toán trong vòng dưới 1 giây.
- **FR-008**: Hệ thống PHẢI đảm bảo tổng số tiền thanh toán sau khi áp dụng voucher không bao giờ nhỏ hơn 0đ.
- **FR-009**: Hệ thống PHẢI giới hạn mỗi đơn đặt sân chỉ được áp dụng tối đa 1 voucher khuyến mãi tại một thời điểm, và cho phép người chơi gỡ voucher để khôi phục về giá gốc.
- **FR-010**: Lượt sử dụng voucher (`usedCount` và bản ghi `redemptions`) CHỈ ĐƯỢC ghi nhận khi giao dịch thanh toán thành công, không tính khi người chơi mới áp dụng mã thử.
- **FR-011**: Hệ thống PHẢI hiển thị 3 phương thức thanh toán cho mọi đơn đặt sân: (1) Thanh toán tại sân (`AT_VENUE`), (2) Đặt cọc (`DEPOSIT`), (3) Ví giả lập (`MOCK_WALLET`).
- **FR-012**: Khi người chơi chọn "Thanh toán tại sân", hệ thống PHẢI ghi nhận trạng thái thanh toán là `UNPAID` và số tiền cọc `depositAmount = 0`.
- **FR-013**: Khi người chơi chọn "Đặt cọc", hệ thống PHẢI tính toán số tiền cọc theo tỷ lệ cố định là 30% trên tổng giá trị đơn hàng, làm tròn đến hàng nghìn đồng chẵn gần nhất, trừ tiền cọc từ ví giả lập và ghi nhận trạng thái `DEPOSITED`.
- **FR-014**: Khi người chơi chọn "Ví giả lập", hệ thống PHẢI trừ 100% tổng tiền thanh toán từ số dư ví giả lập của người chơi và ghi nhận trạng thái `PAID`.
- **FR-015**: Nếu tổng tiền sau giảm giá bằng 0đ, hệ thống PHẢI tự động miễn chọn phương thức thanh toán và cho phép hoàn tất đơn ngay lập tức với trạng thái `PAID`.
- **FR-016**: Khi người chơi bấm "Xác nhận thanh toán", hệ thống PHẢI kiểm tra lại: đơn còn trong hạn giữ chỗ, voucher còn hợp lệ, và số dư ví giả lập đủ để thanh toán (nếu chọn ví hoặc cọc).
- **FR-017**: Nếu số dư ví giả lập không đủ, hệ thống PHẢI hiển thị thông báo số tiền còn thiếu và cung cấp chức năng "Nạp ví giả lập" (cộng số dư ảo để thử nghiệm, khởi tạo số dư ban đầu của tài khoản là 0đ) ngay tại màn hình thanh toán hoặc trang cá nhân, hoặc cho phép đổi phương thức khác.
- **FR-018**: Khi thanh toán thành công, hệ thống PHẢI chuyển trạng thái đơn sang `PENDING` (chờ chủ sân duyệt trong tối đa 60 phút); nếu bị từ chối hoặc sau 60 phút chưa duyệt, hệ thống PHẢI tự động hoàn 100% tiền cọc/ví vào số dư ví giả lập của người chơi.
- **FR-019**: Hệ thống PHẢI hiển thị màn hình Hóa đơn điện tử (E-receipt) đối với đơn đã trả tiền/đặt cọc, hoặc Phiếu đặt sân đối với đơn trả tại sân, bao gồm: mã đơn (`bookingId`), mã nhóm (`recurringGroupId` nếu là đơn cố định), chi tiết các slot/buổi, tiền tạm tính, giảm giá, tiền cọc, tổng tiền, phương thức thanh toán, và nút chuyển sang "Lịch đặt của tôi" (spec 060).
- **FR-020**: Khi thời gian giữ chỗ hết hạn (`holdExpiresAt` quá giờ máy chủ), slot PHẢI được hệ thống coi là trống ngay lập tức đối với những người dùng khác; hệ thống phía server PHẢI chuyển trạng thái đơn sang `EXPIRED` và dọn sạch các slot của đơn trong vòng tối đa 2 phút sau hạn.
- **FR-021**: Khi hết hạn giữ chỗ trên giao diện, ứng dụng PHẢI lập tức vô hiệu hóa nút thanh toán và hiển thị thông báo "Đã hết thời gian giữ chỗ".
- **FR-022**: Mọi yêu cầu xác nhận thanh toán gửi đến sau thời điểm `holdExpiresAt` PHẢI bị hệ thống phía server từ chối an toàn và không thực hiện trừ tiền.
- **FR-023**: Mọi thao tác xác nhận thanh toán PHẢI đi kèm mã định danh duy nhất (`requestId` UUID) để đảm bảo tính duy nhất (idempotency), ngăn chặn trừ tiền ví hai lần hoặc tạo đơn trùng lặp khi người dùng bấm nhiều lần hoặc thử lại do mất mạng.
- **FR-024**: Hệ thống PHẢI đảm bảo kiểm tra và trừ tiền ví giả lập trong giao dịch an toàn (transaction) ở phía máy chủ, đảm bảo số dư ví không bao giờ bị âm dưới mọi tình huống giao dịch đồng thời.
- **FR-025**: Toàn bộ dữ liệu tiền tệ trong đơn đặt và voucher PHẢI được lưu trữ và tính toán bằng số nguyên đơn vị VND (kiểu Long), tuyệt đối không dùng số thực.
- **FR-026**: Hệ thống PHẢI kiểm tra quyền sở hữu đơn hàng: chỉ tài khoản tạo đơn (`uid == userId`) mới có quyền thực hiện thanh toán cho đơn hàng đó.
- **FR-027**: Nếu người chơi thoát ứng dụng khi đơn vẫn còn trong thời gian giữ chỗ, hệ thống PHẢI cho phép người chơi mở lại đơn từ danh sách chờ thanh toán để tiếp tục thanh toán trước khi hết hạn.
- **FR-028**: Hệ thống PHẢI xử lý và hiển thị đầy đủ 4 trạng thái giao diện: Đang tải, Có dữ liệu, Rỗng (không tìm thấy đơn), và Lỗi (mất mạng, lỗi máy chủ kèm nút thử lại).

---

### Key Entities *(mandatory)*

- **Booking (Đơn đặt sân)**: Thực thể đơn đặt nhận từ spec 040/041 và hoàn thiện thanh toán. Các thuộc tính liên quan thanh toán: `bookingId`, `userId`, `venueId`, `recurringGroupId` (nếu có), `subtotal` (Long), `discount` (Long), `total` (Long), `depositAmount` (Long), `paymentMethod` (`AT_VENUE`, `DEPOSIT`, `MOCK_WALLET`), `paymentStatus` (`UNPAID`, `DEPOSITED`, `PAID`, `REFUNDED`), `voucherCode`, `status` (`HOLD`, `PENDING`, `CONFIRMED`, `EXPIRED`), `holdExpiresAt`, `requestId`, `updatedAt`.
- **Voucher (Mã khuyến mãi)**: Đại diện cho chính sách giảm giá. Thuộc tính: `code` (chuỗi ID), `venueId` (null = toàn hệ thống, hoặc ID cơ sở cụ thể), `type` (`PERCENT`, `FIXED`), `value` (Long), `maxDiscount` (Long), `minOrderTotal` (Long), `validFrom` (Timestamp), `validTo` (Timestamp), `usageLimit` (Int), `usedCount` (Int 🔒), `perUserLimit` (Int), `isActive` (Boolean).
- **VoucherRedemption (Lượt sử dụng voucher)**: Bản ghi ghi nhận việc áp dụng voucher thành công. Đường dẫn: `vouchers/{code}/redemptions/{bookingId}`. Thuộc tính: `bookingId`, `userId`, `usedAt` (Timestamp).
- **UserWallet (Ví giả lập người dùng)**: Quản lý số dư ảo phục vụ học tập. Thuộc tính nằm trong `users/{uid}`: `walletBalance` (Long VND, khởi tạo ban đầu là 0đ; nạp ảo tự do cho mục đích kiểm thử).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Thao tác nhập mã hoặc chọn voucher và cập nhật lại số tiền phải trả hoàn tất trong vòng **dưới 1 giây**.
- **SC-002**: Thao tác xác nhận thanh toán giả lập phản hồi kết quả và hiển thị hóa đơn điện tử trong vòng **dưới 1.5 giây** trên kết nối mạng di động 4G.
- **SC-003**: Độ tin cậy dọn dẹp đơn quá hạn: **100%** các đơn hết hạn giữ chỗ mà chưa thanh toán đều được hệ thống chuyển sang trạng thái `EXPIRED` và dọn sạch các slot trong vòng **tối đa 2 phút sau hạn**; slot được coi là trống ngay lập tức tại thời điểm hết hạn.
- **SC-004**: Tính toàn vẹn số dư ví: **0%** xảy ra trường hợp trừ tiền trùng lặp khi người dùng bấm liên tiếp hoặc thử lại mạng; **0%** xảy ra trường hợp số dư ví giả lập bị âm dưới mọi tải giao dịch đồng thời.
- **SC-005**: Kiểm soát giới hạn khuyến mãi: **100%** các voucher không vượt quá giới hạn tổng lượt sử dụng (`usedCount` ≤ `usageLimit`) và giới hạn mỗi tài khoản khi có nhiều người cùng thanh toán đồng thời.
- **SC-006**: Luồng thao tác thanh toán tinh gọn: Người chơi có thể hoàn tất việc chọn voucher, chọn phương thức và bấm xác nhận thanh toán trong vòng **không quá 2 màn hình / 3 thao tác chạm**.
- **SC-007**: Tính minh bạch học tập: **100%** các hóa đơn và giao dịch phát sinh đều hiển thị rõ ràng nhãn "Giao dịch giả lập phục vụ học tập".

---

## Assumptions

- Toàn bộ giao dịch tiền tệ là giả lập phục vụ mục đích học tập nghiên cứu, không kết nối cổng thanh toán thật (MoMo, ZaloPay, VNPay, thẻ tín dụng).
- Đơn vị tiền tệ hiển thị và tính toán là Việt Nam Đồng (VND), lưu trữ dưới dạng số nguyên `Long`.
- Khách hàng đã hoàn thành bước giữ chỗ ở spec 040 hoặc spec 041 và chuyển sang màn hình thanh toán khi đơn vẫn còn thời gian hiệu lực (`holdExpiresAt`).
- Tỷ lệ cọc khi chọn phương thức `DEPOSIT` là cố định 30% giá trị đơn hàng cho toàn hệ thống, làm tròn đến hàng nghìn đồng chẵn gần nhất.
- Tài khoản người chơi mới có số dư ví giả lập ban đầu là 0đ; người chơi có thể nạp thêm tiền ảo vào ví giả lập bất kỳ lúc nào ngay tại màn hình thanh toán hoặc trang cá nhân để phục vụ thử nghiệm các ca kiểm thử.
- Việc duyệt đơn của chủ sân và quét mã QR check-in tại sân thuộc phạm vi của các spec tiếp theo (`specs/060-my-bookings` và `specs/112-owner-operations`).
