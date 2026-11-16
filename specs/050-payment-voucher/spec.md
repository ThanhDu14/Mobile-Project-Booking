# Feature Specification: Thanh toán và Khuyến mãi (Giả lập)

**Feature Branch**: `feature/05-payment-promotion`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi thanh toán cho đơn đặt sân đang được giữ chỗ (đơn lẻ từ spec 040 hoặc đơn cố định theo tuần từ spec 041) trong thời gian giữ chỗ còn lại. Toàn bộ thanh toán là GIẢ LẬP, phục vụ học tập, không thu tiền thật, và app hiển thị rõ điều đó. Luồng chính: 1. Nhận đơn đang giữ chỗ từ spec 040/041. Màn hình thanh toán tiếp tục đếm ngược thời gian còn lại của lần giữ chỗ đó (không bắt đầu lại 5 phút). 2. Hiển thị tóm tắt đơn: tên cơ sở, chi tiết slot/các buổi, tiền sân tạm tính. 3. Voucher: người chơi nhập mã hoặc chọn từ danh sách voucher khả dụng của cơ sở. Hệ thống kiểm tra hạn dùng, đơn tối thiểu, lượt dùng tối đa (chung và theo từng người). Voucher giảm theo % (có mức giảm tối đa) hoặc số tiền cố định; tổng phải trả không âm. Mỗi đơn tối đa 1 voucher, có thể gỡ để về giá gốc. Hiển thị lại tiền dưới 1 giây. 4. Chọn 1 trong 3 phương thức, luôn hiển thị cho mọi đơn: (1) Thanh toán tại sân: chưa trả tiền, trả khi đến sân; (2) Đặt cọc: trả trước một tỷ lệ tổng đơn bằng ví giả lập, phần còn lại trả tại sân; (3) Ví giả lập: trừ 100% từ ví của người chơi. 5. 'Xác nhận thanh toán': hệ thống kiểm tra lại toàn bộ (còn trong hạn giữ chỗ, voucher còn hợp lệ, ví đủ tiền) rồi xử lý giao dịch giả lập. Thành công thì đơn chuyển sang bước tiếp theo của máy trạng thái ở spec 040 và hiện hóa đơn điện tử (mã đơn, mã nhóm nếu là đơn cố định, tiền gốc, giảm giá, tiền cọc, tổng, phương thức, dòng 'Giao dịch giả lập phục vụ học tập'), kèm nút sang 'Lịch đặt của tôi' (spec 060). Đơn trả tại sân hiển thị là phiếu đặt sân, trạng thái chưa thanh toán. Giả định mặc định: Đơn cố định theo tuần: một lần thanh toán cho cả chuỗi; voucher, đơn tối thiểu và cọc tính trên tổng cả chuỗi. Lượt dùng voucher chỉ bị tính khi thanh toán thành công, không tính khi mới nhập mã. Voucher hết hiệu lực giữa lúc áp dụng và lúc xác nhận thì báo lỗi và tính lại giá. Tiền cọc là số nguyên đồng, làm tròn theo quy tắc ghi rõ trong spec. Nạp ví giả lập là chức năng học tập, không giới hạn, có nhãn 'giả lập'. Hết hạn giữ chỗ: Khi hết hạn giữ chỗ mà chưa thanh toán thành công, slot được coi là trống ngay tại thời điểm hết hạn đối với mọi người dùng khác. Hệ thống phía server tự chuyển đơn sang trạng thái hết hạn và dọn sạch toàn bộ slot của đơn trong vòng tối đa 2 phút sau hạn. App vô hiệu hóa nút thanh toán và hiển thị 'Đã hết thời gian giữ chỗ'. Xác nhận thanh toán gửi đến sau hạn bị từ chối. Luồng lỗi và trường hợp biên: Ví không đủ: báo số tiền thiếu, cho nạp ví giả lập hoặc đổi phương thức. Voucher sai: nêu rõ lý do (hết hạn, chưa đủ đơn tối thiểu, hết lượt, không áp dụng cho cơ sở). Mất mạng khi xác nhận: báo lỗi, có nút thử lại; thử lại hoặc bấm nhiều lần không trừ tiền ví hai lần và không tạo đơn trùng. Thoát app khi đang thanh toán: còn hạn thì mở lại đơn từ 'Lịch đặt của tôi' để thanh toán tiếp; quá hạn thì đơn đã hết hạn. Tổng sau giảm bằng 0: không cần chọn phương thức trả tiền. Chỉ chủ đơn mới thanh toán được; chưa đăng nhập hoặc tài khoản bị khóa thì không. Tiêu chí đo được: Áp voucher và cập nhật tiền dưới 1 giây; xác nhận thanh toán giả lập dưới 1,5 giây trên 4G. 100% đơn hết hạn giữ chỗ được chuyển trạng thái hết hạn và nhả sạch slot trong vòng tối đa 2 phút sau hạn. Gửi lặp cùng một yêu cầu xác nhận: ví chỉ bị trừ 1 lần. Hai người cùng dùng lượt voucher cuối: đúng 1 người thành công. Hai đơn cùng trừ một ví: số dư không bao giờ âm. Ranh giới: Bắt đầu khi nhận đơn đang giữ chỗ, kết thúc khi hiển thị hóa đơn/phiếu đặt sân. Không bao gồm: tạo đơn và giữ chỗ (040, 041); chủ sân duyệt đơn và check-in QR (112, 060); hủy và hoàn tiền (060); cổng thanh toán thật hoặc lưu thông tin thẻ (bị cấm theo constitution nguyên tắc VI: thanh toán chỉ giả lập). Ràng buộc: constitution nguyên tắc II (server kiểm tra tiền, voucher, trạng thái đơn), nguyên tắc III (tiền là số nguyên VND, giữ chỗ có hạn theo thời gian server, thao tác idempotent) và nguyên tắc VI (thanh toán giả lập, ghi rõ bản học tập); cách triển khai để phần /speckit-plan. Dữ liệu: bookings, vouchers (+ redemptions), users theo docs/design/database/README.md. Điểm giao cần làm rõ: Sau thanh toán đơn sang chờ duyệt hay tự xác nhận theo từng cơ sở; slot giữ bao lâu khi chủ sân chưa phản hồi; ai hoàn ví khi chủ sân từ chối (với D, spec 112). Tỷ lệ cọc: cố định hay chủ sân cấu hình trong bảng giá (với D). Số dư ví ban đầu và ai quản lý trường số dư trong users (với A). Danh sách 'Lịch đặt của tôi' có hiển thị đơn đang giữ chỗ chưa thanh toán (với spec 060)."

**Nhóm tính năng**: [05 — Thanh toán và khuyến mãi](../../docs/features/05-payment-promotion/README.md) · **Phụ trách**: B · **Liên quan**: [spec 010](../010-account-auth/spec.md) (xác thực người dùng), [spec 040](../040-booking-slot-grid/spec.md) (đơn giữ chỗ ngày lẻ), [spec 041](../041-weekly-recurring-booking/spec.md) (đơn giữ chỗ định kỳ), [spec 060](../060-my-bookings/spec.md) (lịch đặt của tôi / hủy đơn), [spec 112](../112-owner-operations/spec.md) (chủ sân duyệt đơn)

## Clarifications

### Session 2026-10-06

- Q: Sau khi thanh toán thành công, đơn chuyển sang trạng thái nào và xử lý hoàn tiền ra sao nếu chủ sân từ chối? → A: Mọi đơn sau khi thanh toán thành công chuyển sang trạng thái chờ duyệt (`PENDING`); chủ sân có tối đa 60 phút để duyệt đơn (phối hợp spec 112). Nếu chủ sân từ chối hoặc quá hạn 60 phút mà chưa duyệt, hệ thống tự động hủy đơn và hoàn trả 100% số tiền đã trừ (cọc hoặc ví) vào số dư ví giả lập của người chơi. *(Phần hoàn tiền đã được thay bởi Session 2026-11-16.)*
- Q: Tỷ lệ phần trăm đặt cọc khi người chơi chọn phương thức 'Đặt cọc' là bao nhiêu? → A: Tỷ lệ đặt cọc khi chọn `DEPOSIT` được áp dụng cố định là **30%** trên tổng giá trị đơn hàng cho toàn hệ thống, làm tròn đến hàng nghìn đồng chẵn gần nhất (ví dụ: 30% của 175.000đ = 52.500đ làm tròn thành 53.000đ). *(Từ Session 2026-11-16 phương thức này là `TRANSFER_DEPOSIT`, tên cũ là `DEPOSIT`.)*
- Q: Số dư ví giả lập ban đầu của tài khoản mới là bao nhiêu và tính năng 'Nạp ví giả lập' được đặt ở đâu? → A: Số dư ví giả lập ban đầu của tài khoản mới được khởi tạo là **0đ**. Người dùng có thể bấm nút "Nạp ví giả lập" (cộng số dư ảo để test luồng với các mệnh giá gợi ý như 100.000đ, 500.000đ, 1.000.000đ có nhãn "Tính năng thử nghiệm / giả lập") ngay tại màn hình thanh toán hoặc trong trang cá nhân (phối hợp spec 011). *(Đã bỏ theo Session 2026-11-16: không còn ví giả lập.)*

### Session 2026-10-12 (Thống nhất liên thành viên)

- Q: Thống nhất nguồn lưu trữ và cấu trúc dữ liệu Voucher (Thống nhất với Thành viên D theo Phương án 3.B)? → A: Phân tách rõ ràng thành 2 collection độc lập: (1) Voucher riêng của cơ sở lưu tại subcollection `venues/{venueId}/vouchers/{voucherId}` (do chủ sân tạo ở spec 111); (2) Voucher toàn sàn lưu tại collection cấp root `systemVouchers/{voucherId}` (do Admin tạo ở spec 120). Khi người chơi nhập mã hoặc chọn từ danh sách, app kiểm tra đồng thời cả 2 nguồn (ưu tiên voucher cơ sở của venue hiện tại, sau đó kiểm tra voucher toàn hệ thống). Lượt dùng được ghi nhận tại subcollection `redemptions/{bookingId}` bên dưới voucher tương ứng.
- Q: Cơ chế xử lý quá hạn 60 phút duyệt đơn PENDING (Thống nhất với Thành viên D theo Phương án 2.A)? → A: Server Cron Job / Supabase Edge Function (`booking-timeout`) do Thành viên D phụ trách quét định kỳ mỗi 1-2 phút; tự động chuyển các đơn `PENDING` đã quá `approvalDeadline` sang `REJECTED` (`cancelledBy = SYSTEM`, lý do "Quá hạn duyệt"), hoàn 100% tiền cọc/ví vào ví giả lập và gửi một thông báo `BOOKING_REJECTED` có ghi số tiền đã hoàn *(phần hoàn tiền đã được thay bởi Session 2026-11-16: không tự hoàn, chuyển sang chờ chủ sân hoàn)*. `approvalDeadline = min(thời điểm đơn sang PENDING + 60 phút, giờ bắt đầu buổi chơi)` (đặc tả CSDL mục 4.5); hạn không tính từ `createdAt` vì `createdAt` là lúc bắt đầu giữ chỗ.
- Q: Cơ chế cung cấp nội dung FAQ cho Chatbot/AI search (Thống nhất với Thành viên A theo Phương án 6.A)? → A: Cung cấp bộ văn bản tĩnh quy chuẩn về chính sách đặt cọc 30%, ví giả lập phi thương mại *(nay là chuyển khoản sandbox cho chủ sân, Session 2026-11-16)*, và quy tắc áp voucher cho Thành viên A để nhúng vào template đối sánh FAQ cố định trong spec 022, đảm bảo 0% sai lệch thông tin chính sách.

### Session 2026-11-16 (Thiết kế lại thanh toán: chuyển khoản trực tiếp cho chủ sân)

- Q: Có giữ ví giả lập và cách người chơi trả tiền cho chủ sân thay đổi thế nào? → A: Bỏ hẳn ví giả lập và nạp ví (không còn `walletBalance`, `walletTransactions`, phương thức `MOCK_WALLET`). Còn 2 cách: Trả tại sân (`AT_VENUE`) và Chuyển khoản trực tiếp cho chủ sân qua VietQR tới tài khoản ngân hàng của cơ sở, người chơi chọn cọc 30% (`TRANSFER_DEPOSIT`) hoặc trả đủ (`TRANSFER_FULL`); nội dung chuyển khoản là mã thanh toán của đơn. Webhook SePay **Test mode** (sandbox, không tiền thật) khớp mã + số tiền thì đơn sang `PENDING`; mọi tài khoản chủ sân gắn chung một tài khoản SePay test của nhóm, webhook định tuyến theo mã đơn, chống trùng theo `id` giao dịch SePay. Chọn chuyển khoản thì giữ chỗ được gia hạn 15 phút; hết hạn chưa nhận tiền thì đơn `EXPIRED`, nhả slot. Chủ sân từ chối, quá hạn duyệt hoặc người chơi hủy đơn đã trả: không tự hoàn; `paymentStatus = REFUND_PENDING`, chủ sân chuyển trả thủ công rồi bấm "Đã hoàn tiền" (`REFUNDED`). Thông tin nhận tiền (ngân hàng, số tài khoản, tên chủ tài khoản) do chủ sân nhập ở spec 110/111. Constitution VI giữ nguyên: chỉ sandbox, app ghi rõ bản học tập.
- Q: Tiền chuyển về sau khi đơn hết hạn giữ chỗ hoặc sai số tiền thì xử lý thế nào? → A: Không ghi nhận cho đơn; hệ thống lưu khoản tiền đó thành một khoản "cần hoàn" gắn với đơn và cơ sở, hiện trong danh sách cần hoàn tiền của chủ sân; chủ sân chuyển trả rồi bấm "Đã hoàn tiền". Người chơi được thông báo "Đơn không thành công, chủ sân sẽ hoàn tiền". Không tự khôi phục đơn.
- Q: Khi áp voucher rồi chọn chuyển khoản, lượt voucher có được giữ riêng trong lúc chờ tiền không? → A: Có. Lượt voucher được giữ tạm ngay khi người chơi bắt đầu chuyển khoản; tiền về thì tính chính thức, đơn hết hạn thì nhả lượt. Nếu lúc bắt đầu chuyển khoản voucher đã hết lượt thì báo lỗi và tính lại giá trước khi hiện QR.
- Q: Cơ sở chưa nhập tài khoản nhận tiền thì màn thanh toán hiện gì? → A: Ẩn lựa chọn chuyển khoản, chỉ còn "Thanh toán tại sân", kèm ghi chú "Cơ sở chưa hỗ trợ chuyển khoản".
- Q: Chủ sân không hoàn tiền hoặc bấm "Đã hoàn tiền" mà người chơi chưa nhận thì người chơi làm gì? → A: Chủ sân được nhắc lại sau 24 giờ nếu khoản cần hoàn vẫn chờ. Người chơi có nút "Báo cáo chưa nhận được tiền hoàn" (mở khi khoản chờ quá 3 ngày hoặc khi chủ sân đã bấm hoàn), tạo báo cáo gửi Admin qua luồng `reports` có sẵn của spec 120 (`targetType = VENUE`).

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

### User Story 2 - Lựa chọn phương thức thanh toán và xác nhận đơn (Priority: P1)

Người chơi chọn 1 trong 2 cách trả tiền: (1) Thanh toán tại sân (`AT_VENUE`), hoặc (2) Chuyển khoản cho chủ sân, kèm lựa chọn cọc 30% (`TRANSFER_DEPOSIT`) hoặc trả đủ (`TRANSFER_FULL`). Chọn tại sân thì bấm "Xác nhận đặt sân" là đơn sang chờ duyệt (`PENDING`) ngay. Chọn chuyển khoản thì hệ thống kiểm tra lại (còn hạn giữ chỗ, voucher còn hiệu lực), gia hạn giữ chỗ 15 phút và chuyển sang màn hình chuyển khoản (User Story 4); đơn chỉ sang `PENDING` khi hệ thống nhận được tiền. Chủ sân có tối đa 60 phút để duyệt đơn (spec 112). Khi đơn sang `PENDING`, app hiển thị Hóa đơn điện tử (đã cọc/đã trả) hoặc Phiếu đặt sân (trả tại sân) với dòng "Giao dịch giả lập phục vụ học tập" và nút "Lịch đặt của tôi" (spec 060).

**Why this priority**: Đây là bước hoàn tất luồng đặt sân, chốt đơn giữ chỗ thành đơn đặt chính thức và cung cấp chứng từ điện tử cho người chơi. Tuân thủ cam kết môi trường giả lập phi thương mại của Constitution (Nguyên tắc VI).

**Independent Test**: Đặt một đơn chọn trả tại sân, kiểm tra đơn sang `PENDING` và phiếu đặt sân hiển thị "Chưa thanh toán". Đặt đơn khác chọn chuyển khoản trả đủ, mô phỏng giao dịch trên SePay sandbox đúng mã và số tiền, kiểm tra đơn sang `PENDING`, `paymentStatus = PAID` và hóa đơn có nhãn "Giao dịch giả lập phục vụ học tập".

**Acceptance Scenarios**:

1. **Given** người chơi chọn phương thức "Thanh toán tại sân" cho đơn 200.000đ, **When** bấm "Xác nhận đặt sân", **Then** hệ thống cập nhật trạng thái thanh toán `paymentStatus = UNPAID`, chuyển trạng thái đơn sang `PENDING` (chờ chủ sân duyệt trong tối đa 60 phút), và hiển thị Phiếu đặt sân với tổng tiền phải trả tại sân là 200.000đ.
2. **Given** người chơi chọn "Chuyển khoản - cọc 30%" cho đơn 500.000đ, **When** hệ thống nhận được chuyển khoản 150.000đ đúng mã thanh toán, **Then** `depositAmount = 150000`, `paymentStatus = DEPOSITED`, đơn sang `PENDING`, và Hóa đơn điện tử ghi rõ: đã cọc 150.000đ, còn lại trả tại sân 350.000đ.
3. **Given** người chơi chọn "Chuyển khoản - trả đủ" cho đơn 300.000đ, **When** hệ thống nhận được chuyển khoản 300.000đ đúng mã thanh toán, **Then** `paymentStatus = PAID`, đơn sang `PENDING`, và Hóa đơn điện tử ghi đã thanh toán 100%.
4. **Given** đơn đặt cố định theo tuần (từ spec 041) gồm 8 buổi với tổng tiền 1.600.000đ, **When** thanh toán, **Then** hệ thống áp dụng một lần thanh toán cho cả chuỗi đơn, hóa đơn hiển thị mã nhóm `recurringGroupId`, danh sách các buổi và tổng tiền cả chu kỳ.
5. **Given** giao dịch thanh toán thành công, **When** hiển thị màn hình Hóa đơn điện tử, **Then** màn hình có dòng thông báo nổi bật: "Giao dịch giả lập phục vụ học tập - Không thu tiền thật" và cung cấp nút bấm "Xem lịch đặt của tôi" để điều hướng sang spec 060.
6. **Given** người chơi chưa đăng nhập hoặc tài khoản đang bị khóa (`accountStatus == LOCKED`), **When** cố gắng thực hiện thanh toán, **Then** hệ thống từ chối xử lý và thông báo lỗi tương ứng.

---

### User Story 3 - Xử lý hết hạn giữ chỗ trong lúc thanh toán (Priority: P1)

Màn hình thanh toán tiếp tục kế thừa đồng hồ đếm ngược thời gian giữ chỗ còn lại từ spec 040 hoặc spec 041 căn cứ theo mốc thời gian máy chủ `holdExpiresAt`. Nếu đơn chọn chuyển khoản, đồng hồ dùng hạn đã gia hạn 15 phút. Nếu đồng hồ đếm ngược chạm mốc 00:00 mà hệ thống chưa ghi nhận đơn thành công (chưa xác nhận đặt tại sân hoặc chưa nhận được chuyển khoản), hệ thống tự động coi các slot là trống ngay lập tức đối với những người dùng khác. Hệ thống phía server chuyển trạng thái đơn sang `EXPIRED` và dọn sạch dữ liệu slot trong vòng tối đa 2 phút sau hạn. Ứng dụng lập tức vô hiệu hóa nút thanh toán, hiển thị thông báo "Đã hết thời gian giữ chỗ" và không cho phép xác nhận đơn.

**Why this priority**: Bảo vệ tính toàn vẹn dữ liệu đặt lịch theo Nguyên tắc III Constitution. Không cho phép người dùng thanh toán cho một đơn giữ chỗ đã hết hạn vì các slot đó có thể đã được người khác đặt.

**Independent Test**: Giữ chỗ một đơn, mở màn hình thanh toán và chờ đồng hồ đếm ngược hết 5 phút; kiểm tra tại mốc 00:00 nút thanh toán bị khóa, bấm thử thì hệ thống từ chối; kiểm tra trên thiết bị khác slot đó đã chuyển về "Còn trống" và có thể đặt được.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở màn hình thanh toán với đồng hồ giữ chỗ còn 00:05, **When** thời gian chạm mốc 00:00, **Then** nút "Xác nhận thanh toán" lập tức bị vô hiệu hóa (disabled), hiển thị hộp thoại thông báo: "Đã hết thời gian giữ chỗ. Đơn đặt sân đã tự động hủy".
2. **Given** thời gian giữ chỗ đã hết, **When** người chơi cố tình gửi yêu cầu thanh toán (ví dụ do độ trễ mạng hoặc client gửi muộn), **Then** hệ thống phía server từ chối yêu cầu và báo lỗi đơn đã hết hạn.
3. **Given** đơn giữ chỗ hết hạn lúc 18:05:00, **When** đến 18:05:01, **Then** bất kỳ người chơi nào khác xem lưới giờ đều thấy các slot này ở trạng thái "Còn trống" và có thể giữ chỗ bình thường mà không cần chờ tác vụ dọn dẹp chạy xong.
4. **Given** đơn đã hết hạn giữ chỗ, **When** tác vụ dọn dẹp của hệ thống chạy theo lịch, **Then** đơn được chuyển trạng thái sang `EXPIRED` và các bản ghi slot liên quan được dọn dẹp sạch sẽ trong vòng tối đa 2 phút sau hạn.

---

### User Story 4 - Chuyển khoản cho chủ sân qua VietQR và xác nhận tự động (Priority: P1)

Sau khi chọn chuyển khoản, người chơi thấy mã VietQR trỏ tới tài khoản ngân hàng của cơ sở, kèm số tiền (cọc 30% hoặc trả đủ), tên ngân hàng, số tài khoản, tên chủ tài khoản, mã thanh toán của đơn (nội dung chuyển khoản) và đồng hồ đếm ngược 15 phút. Người chơi quét mã bằng app ngân hàng (trong bản học tập: mô phỏng giao dịch trên SePay sandbox). Khi SePay báo có giao dịch tiền vào khớp mã thanh toán và đúng số tiền, hệ thống ghi nhận thanh toán, đơn sang `PENDING` và màn hình tự chuyển sang Hóa đơn điện tử mà người chơi không phải bấm gì thêm.

**Why this priority**: Đây là cách duy nhất để trả trước cho chủ sân sau khi bỏ ví giả lập; thiếu nó đơn chuyển khoản không bao giờ sang được chờ duyệt.

**Independent Test**: Chọn chuyển khoản trả đủ cho đơn 200.000đ, kiểm tra màn hình hiển thị QR đúng tài khoản cơ sở, số tiền 200.000đ và mã thanh toán; mô phỏng giao dịch 200.000đ có mã đó trên SePay sandbox; kiểm tra trong vòng 10 giây màn hình chuyển sang hóa đơn đã thanh toán.

**Acceptance Scenarios**:

1. **Given** người chơi chọn chuyển khoản trả đủ cho đơn 200.000đ, **When** màn hình chuyển khoản mở, **Then** hiển thị VietQR, ngân hàng, số tài khoản, tên chủ tài khoản của cơ sở, số tiền 200.000đ, mã thanh toán và đồng hồ đếm ngược theo hạn giữ chỗ đã gia hạn 15 phút.
2. **Given** đơn đang chờ chuyển khoản, **When** SePay gửi giao dịch tiền vào có mã thanh toán của đơn và đúng số tiền, **Then** hệ thống ghi nhận thanh toán (`DEPOSITED` hoặc `PAID`), đơn sang `PENDING`, chủ sân nhận thông báo `BOOKING_NEW` và màn hình người chơi tự chuyển sang Hóa đơn điện tử.
3. **Given** SePay gửi lại cùng một giao dịch (cùng `id`) nhiều lần, **When** hệ thống xử lý, **Then** thanh toán chỉ được ghi nhận một lần.
4. **Given** đơn đang chờ chuyển khoản, **When** người chơi muốn đổi ý, **Then** người chơi có thể quay lại chọn "Thanh toán tại sân" khi vẫn còn hạn giữ chỗ.

---

### User Story 5 - Đảm bảo tính toàn vẹn giao dịch và chống ghi nhận trùng lặp (Priority: P3)

Trong điều kiện mạng di động 4G chập chờn, khi người chơi bấm xác nhận nhưng yêu cầu bị timeout hoặc bấm nhiều lần liên tiếp, hệ thống dùng mã định danh yêu cầu duy nhất (`requestId` UUID) để thao tác idempotent. Webhook SePay có thể gửi lại cùng một giao dịch; hệ thống chống trùng theo `id` giao dịch SePay. Hệ thống cam kết: không tạo đơn trùng, không ghi nhận một khoản chuyển khoản hai lần, và xử lý an toàn khi hai người cùng tranh lượt voucher cuối cùng.

**Why this priority**: Đảm bảo an toàn tài chính (dù là môi trường giả lập) và tính toàn vẹn dữ liệu máy chủ theo Nguyên tắc II & III Constitution, tránh ghi nhận một khoản chuyển khoản hai lần hay vượt hạn mức khuyến mãi.

**Independent Test**: Giả lập kịch bản người dùng bấm "Xác nhận thanh toán" 5 lần liên tiếp trong 500ms; kiểm tra chỉ có 1 yêu cầu được xử lý và cùng một kết quả trả về. Gửi lại cùng một webhook SePay 3 lần; kiểm tra thanh toán chỉ được ghi nhận 1 lần. Giả lập voucher chỉ còn 1 lượt dùng cho 2 tài khoản bấm thanh toán cùng lúc; kiểm tra chỉ 1 tài khoản được trừ giá khuyến mãi.

**Acceptance Scenarios**:

1. **Given** người chơi bấm nút xác nhận thanh toán nhiều lần liên tiếp thật nhanh, **When** các yêu cầu đến máy chủ, **Then** hệ thống nhận diện cùng một `requestId`, chỉ xử lý 1 lần và trả về cùng một kết quả.
2. **Given** người chơi gặp sự cố mất mạng khi đang gửi yêu cầu xác nhận, **When** bấm nút "Thử lại" sau khi có mạng lại, **Then** hệ thống xử lý idempotent, không tạo đơn hay yêu cầu chuyển khoản trùng.
3. **Given** một voucher chỉ còn đúng 1 lượt sử dụng cuối cùng trên hệ thống, **When** hai người chơi cùng bấm xác nhận thanh toán áp dụng mã này tại cùng thời điểm, **Then** hệ thống chỉ cho phép đúng 1 người chơi áp dụng (hoặc giữ tạm, nếu chọn chuyển khoản) voucher thành công; người chơi còn lại nhận thông báo voucher đã hết lượt và tổng tiền được tính lại theo giá gốc trước khi hiện QR hoặc xác nhận.
4. **Given** SePay gửi cùng một giao dịch tiền vào (cùng `id`) 3 lần do thử lại, **When** hệ thống nhận cả 3, **Then** thanh toán của đơn chỉ được ghi nhận 1 lần và cả 3 lần đều được trả lời thành công để SePay ngừng gửi lại.

---

### Edge Cases

- **Voucher hết hiệu lực giữa lúc áp dụng và lúc bấm xác nhận**: Người chơi nhập voucher lúc voucher còn hạn, nhưng để màn hình thanh toán chờ 3 phút khiến voucher hết hạn hoặc hết lượt trước khi bấm xác nhận. Khi bấm xác nhận, hệ thống kiểm tra lại điều kiện tại thời điểm commit, từ chối voucher, thông báo cho người chơi và tính lại tổng tiền theo giá gốc.
- **Thoát ứng dụng khi đang ở màn hình thanh toán**: Người chơi vô tình tắt app hoặc chuyển sang ứng dụng khác khi đang ở màn hình thanh toán. Nếu quay lại app khi thời gian giữ chỗ vẫn còn, người chơi có thể mở lại đơn từ mục "Đơn chờ thanh toán" trong "Lịch đặt của tôi" (spec 060) để tiếp tục thanh toán; nếu đã quá hạn, đơn đã tự động chuyển sang `EXPIRED`.
- **Làm tròn số tiền khi tính tiền cọc**: Khi tính tỷ lệ đặt cọc cố định 30% (ví dụ 30% của đơn 175.000đ = 52.500đ), hệ thống làm tròn số tiền cọc đến hàng nghìn đồng gần nhất (làm tròn thành 53.000đ) để đảm bảo số tiền cọc và số tiền còn lại đều là số nguyên đồng chẵn, không phát sinh số lẻ.
- **Chỉ chủ sở hữu đơn mới được thanh toán**: Hệ thống phía server kiểm tra người gửi yêu cầu thanh toán phải có `uid` trùng khớp với `userId` tạo đơn giữ chỗ; không cho phép tài khoản khác thanh toán thay trừ khi có chức năng thanh toán hộ được đặc tả riêng.
- **Hoàn tiền khi đơn đã trả bị từ chối, quá hạn duyệt hoặc bị hủy**: Tiền nằm ở tài khoản chủ sân nên hệ thống không tự hoàn. Đơn có `paymentStatus` `DEPOSITED` hoặc `PAID` khi bị chủ sân từ chối (spec 112), quá `approvalDeadline` hoặc người chơi hủy (spec 060) thì chuyển `paymentStatus = REFUND_PENDING`; chủ sân chuyển trả thủ công rồi bấm "Đã hoàn tiền" để chuyển `REFUNDED`. Người chơi thấy trạng thái này trong "Lịch đặt của tôi".
- **Cơ sở chưa có tài khoản nhận tiền**: Cơ sở tạo trước khi có tính năng hoặc chủ sân chưa nhập ngân hàng: chỉ hiện "Thanh toán tại sân" với ghi chú "Cơ sở chưa hỗ trợ chuyển khoản".
- **Tiền đến muộn hoặc sai số tiền**: Chuyển khoản có `paymentCode` của đơn nhưng đến sau `holdExpiresAt` (đơn đã `EXPIRED`), hoặc số tiền khác số phải trả (thiếu hay thừa): hệ thống không ghi nhận cho đơn và không khôi phục đơn; khoản tiền được lưu thành khoản cần hoàn (`RefundItem`) hiện trong danh sách cần hoàn tiền của chủ sân, người chơi nhận thông báo "Đơn không thành công, chủ sân sẽ hoàn tiền". Chủ sân chuyển trả rồi bấm "Đã hoàn tiền".
- **Chủ sân không hoàn hoặc người chơi chưa nhận được tiền hoàn**: Sau 24 giờ khoản vẫn chờ thì chủ sân bị nhắc; sau 3 ngày, hoặc khi chủ sân đã bấm hoàn mà người chơi chưa nhận, người chơi bấm "Báo cáo chưa nhận được tiền hoàn" để gửi báo cáo cho Admin (spec 120).
- **Đơn cố định theo tuần chọn chuyển khoản**: Một lần chuyển khoản cho cả chuỗi, số tiền cọc 30% hoặc trả đủ tính trên tổng cả chuỗi.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI nhận đơn đặt sân ở trạng thái `HOLD` từ spec 040 (đơn lẻ) hoặc spec 041 (đơn cố định theo tuần) và hiển thị màn hình thanh toán tương ứng.
- **FR-002**: Hệ thống PHẢI tiếp tục đếm ngược thời gian giữ chỗ còn lại của đơn dựa trên mốc thời gian máy chủ `holdExpiresAt`, tuyệt đối không khởi tạo lại 5 phút mới.
- **FR-003**: Hệ thống PHẢI hiển thị bảng tóm tắt đơn hàng gồm: tên cơ sở, địa chỉ, danh sách chi tiết các slot/buổi chơi, đơn giá từng slot và tiền tạm tính (`subtotal`).
- **FR-004**: Hệ thống PHẢI hiển thị dòng thông báo nổi bật trên giao diện: "Hệ thống thanh toán giả lập phục vụ học tập - Không thu tiền thật" trên mọi màn hình liên quan đến thanh toán.
- **FR-005**: Hệ thống PHẢI cho phép người chơi nhập mã voucher hoặc chọn từ danh sách voucher hợp lệ lấy từ cả 2 nguồn: voucher riêng của cơ sở (`venues/{venueId}/vouchers`) và voucher toàn hệ thống (`systemVouchers`).
- **FR-006**: Hệ thống PHẢI kiểm tra đầy đủ các điều kiện hiệu lực của voucher: khoảng thời gian hợp lệ (`validFrom` đến `validUntil`), giá trị đơn tối thiểu (`minOrderTotal`), tổng số lượt dùng còn lại (`usedCount` < `usageLimit`), và số lượt dùng của người chơi (`perUserLimit`).
- **FR-007**: Hệ thống PHẢI tính toán chính xác mức giảm giá theo quy tắc của voucher: giảm theo tỷ lệ phần trăm (áp dụng trần `maxDiscount` nếu có) hoặc giảm theo số tiền cố định, và cập nhật lại tổng tiền thanh toán trong vòng dưới 1 giây.
- **FR-008**: Hệ thống PHẢI đảm bảo tổng số tiền thanh toán sau khi áp dụng voucher không bao giờ nhỏ hơn 0đ.
- **FR-009**: Hệ thống PHẢI giới hạn mỗi đơn đặt sân chỉ được áp dụng tối đa 1 voucher khuyến mãi tại một thời điểm, và cho phép người chơi gỡ voucher để khôi phục về giá gốc.
- **FR-010**: Lượt sử dụng voucher (`usedCount` và bản ghi `redemptions`) CHỈ ĐƯỢC ghi nhận chính thức khi đơn thành công (đặt tại sân được xác nhận hoặc chuyển khoản được ghi nhận), không tính khi người chơi mới áp dụng mã thử. Khi người chơi bắt đầu chuyển khoản, hệ thống PHẢI giữ tạm một lượt (tính vào giới hạn `usageLimit` và `perUserLimit`) cho đơn đó; đơn hết hạn thì nhả lượt giữ tạm. Nếu lúc bắt đầu chuyển khoản voucher đã hết lượt thì báo lỗi và tính lại giá trước khi hiện QR.
- **FR-011**: Hệ thống PHẢI hiển thị 2 cách trả tiền: (1) Thanh toán tại sân (`AT_VENUE`), (2) Chuyển khoản cho chủ sân với lựa chọn cọc 30% (`TRANSFER_DEPOSIT`) hoặc trả đủ (`TRANSFER_FULL`). Nếu cơ sở chưa có đủ thông tin tài khoản nhận tiền (FR-030), hệ thống PHẢI ẩn lựa chọn chuyển khoản, chỉ hiện "Thanh toán tại sân" kèm ghi chú "Cơ sở chưa hỗ trợ chuyển khoản"; server cũng từ chối yêu cầu chuyển khoản cho cơ sở đó.
- **FR-012**: Khi người chơi chọn "Thanh toán tại sân", hệ thống PHẢI ghi nhận trạng thái thanh toán là `UNPAID` và số tiền cọc `depositAmount = 0`.
- **FR-013**: Khi người chơi chọn chuyển khoản cọc, hệ thống PHẢI tính tiền cọc 30% tổng đơn, làm tròn đến hàng nghìn đồng chẵn gần nhất; khi nhận được chuyển khoản đủ số tiền đó thì ghi `depositAmount` và `paymentStatus = DEPOSITED`.
- **FR-014**: Khi người chơi chọn chuyển khoản trả đủ, hệ thống PHẢI yêu cầu chuyển 100% tổng đơn; khi nhận được chuyển khoản đủ số tiền thì ghi `paymentStatus = PAID`.
- **FR-015**: Nếu tổng tiền sau giảm giá bằng 0đ, hệ thống PHẢI tự động miễn chọn phương thức thanh toán và cho phép hoàn tất đơn ngay lập tức với trạng thái `PAID`.
- **FR-016**: Khi người chơi bấm xác nhận (đặt tại sân hoặc bắt đầu chuyển khoản), hệ thống PHẢI kiểm tra lại: đơn còn trong hạn giữ chỗ và voucher còn hợp lệ.
- **FR-017**: Khi người chơi bắt đầu chuyển khoản, hệ thống PHẢI gia hạn `holdExpiresAt` thành thời điểm đó + 15 phút (đơn vẫn ở `HOLD`, `paymentStatus = AWAITING_TRANSFER`), cấp cho đơn một mã thanh toán ngắn, in hoa, duy nhất (`paymentCode`) và hiển thị VietQR tới tài khoản nhận tiền của cơ sở với đúng số tiền và nội dung là `paymentCode`.
- **FR-018**: Khi thanh toán thành công, hệ thống PHẢI chuyển trạng thái đơn sang `PENDING` (chờ chủ sân duyệt trước `approvalDeadline` = min(lúc sang `PENDING` + 60 phút, giờ bắt đầu buổi chơi)), slot của đơn chuyển `BOOKED` và chủ sân nhận thông báo `BOOKING_NEW`; hệ thống máy chủ (Edge Function `booking-timeout` theo thỏa thuận 2.A) PHẢI tự động quét định kỳ: nếu chủ sân từ chối hoặc quá `approvalDeadline` chưa duyệt, hệ thống tự động chuyển đơn sang `REJECTED` và gửi thông báo FCM cho người chơi; nếu đơn đã trả (`DEPOSITED`/`PAID`) thì `paymentStatus` chuyển `REFUND_PENDING` (xem FR-031).
- **FR-019**: Hệ thống PHẢI hiển thị màn hình Hóa đơn điện tử (E-receipt) đối với đơn đã trả tiền/đặt cọc, hoặc Phiếu đặt sân đối với đơn trả tại sân, bao gồm: mã đơn (`bookingId`), mã nhóm (`recurringGroupId` nếu là đơn cố định), chi tiết các slot/buổi, tiền tạm tính, giảm giá, tiền cọc, tổng tiền, phương thức thanh toán, và nút chuyển sang "Lịch đặt của tôi" (spec 060).
- **FR-020**: Khi thời gian giữ chỗ hết hạn (`holdExpiresAt` quá giờ máy chủ), slot PHẢI được hệ thống coi là trống ngay lập tức đối với những người dùng khác; hệ thống phía server PHẢI chuyển trạng thái đơn sang `EXPIRED` và dọn sạch các slot của đơn trong vòng tối đa 2 phút sau hạn.
- **FR-021**: Khi hết hạn giữ chỗ trên giao diện, ứng dụng PHẢI lập tức vô hiệu hóa nút thanh toán và hiển thị thông báo "Đã hết thời gian giữ chỗ".
- **FR-022**: Mọi yêu cầu xác nhận đơn gửi đến sau thời điểm `holdExpiresAt` PHẢI bị hệ thống phía server từ chối an toàn.
- **FR-023**: Mọi thao tác xác nhận thanh toán PHẢI đi kèm mã định danh duy nhất (`requestId` UUID) để đảm bảo tính duy nhất (idempotency), ngăn tạo đơn hoặc yêu cầu chuyển khoản trùng lặp khi người dùng bấm nhiều lần hoặc thử lại do mất mạng.
- **FR-024**: Hệ thống PHẢI xác nhận chuyển khoản qua webhook SePay **Test mode**: xác thực chữ ký của SePay, tìm đơn theo `paymentCode` trong nội dung chuyển khoản, chỉ ghi nhận khi số tiền đúng bằng số phải trả; mỗi `id` giao dịch SePay chỉ được xử lý một lần (sổ chống trùng ghi trong cùng giao dịch với việc cập nhật đơn). Khóa bí mật SePay chỉ nằm ở phía server.
- **FR-025**: Toàn bộ dữ liệu tiền tệ trong đơn đặt và voucher PHẢI được lưu trữ và tính toán bằng số nguyên đơn vị VND (kiểu Long), tuyệt đối không dùng số thực.
- **FR-026**: Hệ thống PHẢI kiểm tra quyền sở hữu đơn hàng: chỉ tài khoản tạo đơn (`uid == userId`) mới có quyền thực hiện thanh toán cho đơn hàng đó.
- **FR-027**: Nếu người chơi thoát ứng dụng khi đơn vẫn còn trong thời gian giữ chỗ, hệ thống PHẢI cho phép người chơi mở lại đơn từ danh sách chờ thanh toán để tiếp tục thanh toán trước khi hết hạn.
- **FR-028**: Hệ thống PHẢI xử lý và hiển thị đầy đủ 4 trạng thái giao diện: Đang tải, Có dữ liệu, Rỗng (không tìm thấy đơn), và Lỗi (mất mạng, lỗi máy chủ kèm nút thử lại).
- **FR-029**: Khi chuyển khoản được ghi nhận, màn hình chuyển khoản của người chơi PHẢI tự chuyển sang Hóa đơn điện tử mà không cần người chơi thao tác.
- **FR-030**: Tài khoản nhận tiền (ngân hàng, số tài khoản, tên chủ tài khoản) lấy từ thông tin cơ sở do chủ sân nhập ở spec 110/111; app chỉ hiển thị, không cho người chơi sửa.
- **FR-031**: Đơn đã trả (`DEPOSITED`/`PAID`) bị chủ sân từ chối, quá `approvalDeadline` hoặc bị hủy PHẢI chuyển `paymentStatus = REFUND_PENDING`; chủ sân chuyển trả thủ công ngoài app rồi bấm "Đã hoàn tiền" để chuyển `REFUNDED`. Hệ thống không tự hoàn tiền.
- **FR-034**: Nếu một `RefundItem` còn `PENDING` sau 24 giờ, hệ thống PHẢI gửi thông báo nhắc chủ sân. Người chơi PHẢI có nút "Báo cáo chưa nhận được tiền hoàn" khi khoản đã chờ quá 3 ngày hoặc khi chủ sân đã bấm "Đã hoàn tiền"; nút này tạo một báo cáo trong luồng `reports` của spec 120 (`targetType = VENUE`, kèm `bookingId` và số tiền) để Admin xử lý.
- **FR-033**: Chuyển khoản khớp `paymentCode` nhưng đến sau hạn giữ chỗ hoặc sai số tiền PHẢI không được ghi nhận cho đơn; hệ thống PHẢI tạo một khoản cần hoàn (`RefundItem`) hiện cho chủ sân, gửi thông báo cho người chơi, và cho chủ sân bấm "Đã hoàn tiền" như FR-031.
- **FR-032**: Hệ thống KHÔNG được kết nối SePay ở chế độ Live; mọi màn hình chuyển khoản PHẢI ghi rõ "Môi trường thử nghiệm (sandbox) - Không thu tiền thật".

---

### Key Entities *(mandatory)*

- **Booking (Đơn đặt sân)**: Thực thể đơn đặt nhận từ spec 040/041 và hoàn thiện thanh toán. Các thuộc tính liên quan thanh toán: `bookingId`, `userId`, `venueId`, `recurringGroupId` (nếu có), `subtotal` (Long), `discount` (Long), `total` (Long), `depositAmount` (Long), `paymentMethod` (`AT_VENUE`, `TRANSFER_DEPOSIT`, `TRANSFER_FULL`), `paymentStatus` (`UNPAID`, `AWAITING_TRANSFER`, `DEPOSITED`, `PAID`, `REFUND_PENDING`, `REFUNDED`), `paymentCode` (mã nội dung chuyển khoản, duy nhất), `amountDue` (Long, số tiền phải chuyển), `paidAt`, `sepayTransactionId`, `refundedAt`, `voucherCode`, `status` (`HOLD`, `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`), `holdExpiresAt`, `requestId`, `updatedAt`.
- **VenueVoucher (Voucher cơ sở)**: Mã khuyến mãi áp dụng cho một cơ sở cụ thể (chủ sân quản lý theo spec 111). Đường dẫn: `venues/{venueId}/vouchers/{code}` (mã duy nhất toàn hệ thống, đặc tả CSDL mục 4.6). Thuộc tính: `code`, `name` (tên hiển thị), `venueId`, `discountType` (`PERCENT`, `FIXED_AMOUNT`), `discountValue` (Long), `maxDiscount` (Long), `minOrderTotal` (Long), `validFrom`, `validUntil`, `usageLimit`, `usedCount` 🔒, `perUserLimit`, `isActive`.
- **SystemVoucher (Voucher toàn hệ thống)**: Mã khuyến mãi áp dụng cho mọi cơ sở toàn sàn (Admin quản lý theo spec 120). Đường dẫn: `systemVouchers/{code}`. Thuộc tính: tương tự `VenueVoucher` nhưng không có `venueId`.
- **VoucherRedemption (Lượt sử dụng voucher)**: Bản ghi ghi nhận việc áp dụng voucher thành công. Đường dẫn: `venues/{venueId}/vouchers/{code}/redemptions/{bookingId}` hoặc `systemVouchers/{code}/redemptions/{bookingId}`. Thuộc tính: `bookingId`, `userId`, `status` (`RESERVED` khi giữ tạm lúc chờ chuyển khoản, `USED` khi đơn thành công; bản ghi `RESERVED` bị xóa khi đơn hết hạn), `usedAt` (Timestamp) 🔒.
- **VenuePayoutAccount (Tài khoản nhận tiền của cơ sở)**: Thuộc tính của cơ sở do chủ sân nhập (spec 110/111): `bankCode`, `bankName`, `accountNumber`, `accountHolderName`.
- **RefundItem (Khoản cần hoàn)**: Một khoản chủ sân phải chuyển trả cho người chơi: `bookingId`, `venueId`, `userId`, `amount` (Long), `reason` (`REJECTED`, `APPROVAL_TIMEOUT`, `CANCELLED`, `LATE_TRANSFER`, `AMOUNT_MISMATCH`), `sepayTransactionId` (nếu có), `status` (`PENDING`, `DONE`), `createdAt`, `doneAt`. Dùng chung cho FR-031 và FR-033.
- **SepayTransaction (Sổ chống trùng giao dịch SePay)**: Một bản ghi cho mỗi `id` giao dịch SePay đã nhận: `sepayId`, `transferAmount`, `content`, `paymentCode`, `bookingId` (nếu khớp), `outcome` (đã ghi nhận / không khớp mã / sai số tiền / trùng ...), `receivedAt`. Chỉ server đọc/ghi.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Thao tác nhập mã hoặc chọn voucher và cập nhật lại số tiền phải trả hoàn tất trong vòng **dưới 1 giây**.
- **SC-002**: Xác nhận đặt tại sân hoặc mở màn hình chuyển khoản phản hồi trong vòng **dưới 1.5 giây** trên 4G; sau khi SePay gửi webhook, màn hình người chơi chuyển sang hóa đơn trong vòng **dưới 10 giây**.
- **SC-003**: Độ tin cậy dọn dẹp đơn quá hạn: **100%** các đơn hết hạn giữ chỗ mà chưa thanh toán đều được hệ thống chuyển sang trạng thái `EXPIRED` và dọn sạch các slot trong vòng **tối đa 2 phút sau hạn**; slot được coi là trống ngay lập tức tại thời điểm hết hạn.
- **SC-004**: Toàn vẹn thanh toán: **0%** đơn hoặc yêu cầu chuyển khoản trùng khi người dùng bấm liên tiếp hoặc thử lại mạng; **0%** giao dịch SePay được ghi nhận hai lần khi webhook gửi lại.
- **SC-005**: Kiểm soát giới hạn khuyến mãi: **100%** các voucher không vượt quá giới hạn tổng lượt sử dụng (`usedCount` ≤ `usageLimit`) và giới hạn mỗi tài khoản khi có nhiều người cùng thanh toán đồng thời.
- **SC-006**: Luồng thao tác thanh toán tinh gọn: Người chơi có thể hoàn tất việc chọn voucher, chọn phương thức và bấm xác nhận thanh toán trong vòng **không quá 2 màn hình / 3 thao tác chạm**.
- **SC-007**: Tính minh bạch học tập: **100%** các hóa đơn và giao dịch phát sinh đều hiển thị rõ ràng nhãn "Giao dịch giả lập phục vụ học tập".

---

## Assumptions

- Toàn bộ giao dịch tiền tệ là giả lập phục vụ học tập: chuyển khoản chỉ đi qua SePay **Test mode** (mô phỏng giao dịch), không kết nối cổng thanh toán thật (MoMo, ZaloPay, VNPay, thẻ tín dụng) và không dùng SePay Live.
- Đơn vị tiền tệ hiển thị và tính toán là Việt Nam Đồng (VND), lưu trữ dưới dạng số nguyên `Long`.
- Khách hàng đã hoàn thành bước giữ chỗ ở spec 040 hoặc spec 041 và chuyển sang màn hình thanh toán khi đơn vẫn còn thời gian hiệu lực (`holdExpiresAt`).
- Tỷ lệ cọc khi chọn phương thức `TRANSFER_DEPOSIT` là cố định 30% giá trị đơn hàng cho toàn hệ thống, làm tròn đến hàng nghìn đồng chẵn gần nhất.
- Trong sandbox, tài khoản nhận tiền của mọi cơ sở được gắn chung vào một tài khoản SePay test của nhóm; webhook định tuyến theo `paymentCode`, không theo số tài khoản.
- Việc duyệt đơn của chủ sân và quét mã QR check-in tại sân thuộc phạm vi của các spec tiếp theo (`specs/060-my-bookings` và `specs/112-owner-operations`).
- Không còn ví giả lập: bỏ `users.walletBalance` và `walletTransactions` (đặc tả CSDL mục 4.1); spec 011, 060, 112 và đặc tả CSDL cần cập nhật theo. Buổi vãng lai (spec 080, 081) dùng thanh toán giả lập riêng theo quyết định đã chốt ở spec 080.
