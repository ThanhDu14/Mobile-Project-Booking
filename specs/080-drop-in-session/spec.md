# Feature Specification: Đặt lịch chơi vãng lai theo lượt

**Feature Branch**: `feature/08-drop-in`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi tìm và đăng ký các buổi chơi vãng lai (drop-in) do sân/CLB mở. Danh sách buổi hiển thị ngày, giờ, sân, trình độ, số chỗ còn lại, giá theo lượt; lọc theo khu vực, ngày, khung giờ, trình độ, mức giá. Đăng ký: chọn buổi, số người (một mình hoặc kèm bạn), xác nhận; không vượt quá số chỗ còn lại, việc đăng ký chạy phía server trong transaction (Edge Function register-drop-in) để không bị vượt chỗ khi nhiều người đăng ký cùng lúc. Thanh toán theo lượt (giả lập) hoặc trả tại sân; sau khi đăng ký có vé lượt kèm mã QR check-in. Hủy đăng ký theo chính sách (cancel-drop-in), xem lịch sử các buổi đã tham gia, đánh giá buổi chơi sau khi tham gia (reviews với targetType = DROP_IN, mỗi lượt một lần). Có xử lý mất mạng, buổi bị hủy/đã đủ người, trạng thái rỗng. Không bao gồm: hàng chờ và tự đẩy người lên (spec 081), chủ sân mở buổi và quét QR check-in (spec 112), gửi thông báo (spec 100), thanh toán đặt sân thường (spec 050). Dữ liệu: dropInSessions, dropInSessions/{id}/registrations/{uid}, reviews theo docs/design/database/README.md mục 4.7, 4.9."

**Nhóm tính năng**: [08 — Đặt lịch vãng lai](../../docs/features/08-drop-in/README.md) · **Phụ trách**: C · **Liên quan**: spec 081 (hàng chờ), [spec 070](../070-review-favorite/spec.md) (đánh giá), [spec 011](../011-account-profile/spec.md) (trình độ, khu vực, xóa tài khoản), [spec 022](../022-ai-court-search/spec.md) (AI điền bộ lọc vãng lai), spec 100 (thông báo), spec 112 (chủ sân mở buổi, check-in)

## Clarifications

### Session 2026-10-06

- Q: Khi người chơi tự hủy đăng ký buổi vãng lai, áp dụng chính sách hoàn tiền nào? → A: Hủy trước giờ bắt đầu hơn 4 giờ được hoàn 100%; trong vòng 4 giờ vẫn hủy được (chỗ được trả lại) nhưng không hoàn tiền.
- Q: Điều kiện nào để người chơi được đánh giá một buổi vãng lai đã kết thúc? → A: Chỉ lượt đã check-in mới đánh giá được; lượt "Không đến", đã hủy hoặc buổi bị hủy thì không.
- Q: "Thanh toán ngay (giả lập)" khi đăng ký vãng lai hoạt động thế nào? → A: Màn hình thanh toán giả lập luôn thành công, không dùng ví có số dư và không phụ thuộc spec 050; hoàn tiền chỉ ghi trạng thái "Đã hoàn tiền".
- Q: Một lần đăng ký được kèm tối đa bao nhiêu người? → A: Tối đa 4 người (bản thân + 3 bạn), và không vượt số chỗ còn lại.
- Q: Người chơi khác có được xem danh sách những ai đã đăng ký một buổi vãng lai không? → A: Không. Người khác chỉ thấy số chỗ còn lại; danh sách người đăng ký chỉ chủ cơ sở mở buổi và admin xem được.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tìm buổi vãng lai phù hợp (Priority: P1)

Người chơi lẻ không thuê nguyên sân mở mục "Vãng lai" và thấy danh sách các buổi sắp diễn ra do sân/CLB mở. Mỗi buổi hiển thị ngày, giờ bắt đầu–kết thúc, tên cơ sở và quận, trình độ, số chỗ còn lại trên tổng số chỗ, giá mỗi lượt. Người chơi lọc theo khu vực, ngày, khung giờ, trình độ, mức giá, rồi bấm vào một buổi để xem chi tiết (địa chỉ, sân con, ghi chú của chủ sân, số chỗ còn lại cập nhật liên tục).

**Why this priority**: Không tìm được buổi thì không đăng ký được; đây là cửa vào của toàn bộ nhóm 08 và là điểm khác biệt so với đặt nguyên sân.

**Independent Test**: Với dữ liệu demo gồm khoảng 20 buổi ở 3 quận, 3 trình độ và nhiều mức giá, mở "Vãng lai", áp từng bộ lọc và kết hợp nhiều bộ lọc, kiểm tra chỉ còn đúng các buổi thỏa mãn; mở chi tiết một buổi thấy đủ thông tin.

**Acceptance Scenarios**:

1. **Given** có các buổi sắp diễn ra, **When** người chơi mở "Vãng lai", **Then** thấy danh sách buổi sắp xếp theo thời gian bắt đầu gần nhất, mỗi buổi có ngày, giờ, tên cơ sở, quận, trình độ, "còn X/Y chỗ" và giá mỗi lượt.
2. **Given** hồ sơ người chơi có khu vực hay chơi (spec 011), **When** mở "Vãng lai" lần đầu, **Then** bộ lọc khu vực được điền sẵn các khu vực đó và người chơi bỏ được.
3. **Given** người chơi chọn quận "Quận 10", ngày thứ Bảy, khung giờ "Tối", trình độ "Trung bình", giá tối đa 80.000 đ, **When** áp dụng, **Then** chỉ còn các buổi thỏa mãn đủ mọi điều kiện; mỗi bộ lọc đang áp dụng hiện thành chip, bỏ từng chip hoặc xóa hết được.
4. **Given** một buổi đã đủ người, **When** xuất hiện trong danh sách, **Then** buổi vẫn hiện với nhãn "Đã đủ người" (không có nút đăng ký; lối vào hàng chờ thuộc spec 081).
5. **Given** buổi đã bắt đầu, đã kết thúc, đã bị hủy, hoặc thuộc cơ sở đang bị ẩn/khóa, **When** người chơi xem danh sách, **Then** buổi đó không xuất hiện.
6. **Given** không có buổi nào khớp bộ lọc, **When** danh sách cập nhật, **Then** thấy màn hình trống gợi ý bỏ bớt bộ lọc.
7. **Given** người chơi đang xem chi tiết một buổi, **When** người khác đăng ký làm số chỗ còn lại thay đổi, **Then** con số trên màn hình cập nhật mà không cần tải lại.
8. **Given** AI Assistant (spec 022) chuyển người chơi sang danh sách vãng lai kèm điều kiện, **When** màn hình mở, **Then** các bộ lọc tương ứng đã được điền sẵn.

---

### User Story 2 - Đăng ký chơi theo lượt và nhận vé QR (Priority: P1)

Ở chi tiết buổi, người chơi bấm "Đăng ký", chọn số người (đi một mình hoặc kèm bạn), chọn cách thanh toán: "Thanh toán ngay (giả lập)" hoặc "Trả tại sân", xem tổng tiền rồi bấm "Xác nhận". Hệ thống giữ đúng số chỗ cho cả nhóm và cấp một vé lượt có mã QR để chủ sân quét khi đến.

**Why this priority**: Đây là giá trị cốt lõi của nhóm 08 và là bài toán chống vượt chỗ khi nhiều người đăng ký cùng lúc mà constitution (nguyên tắc III) coi là bắt buộc.

**Independent Test**: Đăng ký một buổi còn 3 chỗ với 2 người, thanh toán giả lập; kiểm tra vé hiện mã QR, số chỗ của buổi giảm còn 1. Cho 2 tài khoản cùng đăng ký chỗ cuối cùng gần như đồng thời: đúng một người thành công, người kia được báo hết chỗ.

**Acceptance Scenarios**:

1. **Given** buổi còn chỗ và người chơi chưa đăng ký buổi này, **When** bấm "Đăng ký", **Then** thấy màn hình xác nhận gồm thông tin buổi, ô chọn số người (mặc định 1), cách thanh toán và tổng tiền = giá mỗi lượt × số người.
2. **Given** buổi còn 2 chỗ, **When** người chơi chọn số người, **Then** không chọn được quá 2; khi buổi còn nhiều chỗ thì không chọn được quá 4.
3. **Given** người chơi chọn "Thanh toán ngay (giả lập)" và bấm "Xác nhận", **When** đăng ký thành công, **Then** vé hiện trạng thái "Đã thanh toán", có mã QR, số người, mã vé, và buổi giảm đúng số chỗ đã đăng ký.
4. **Given** người chơi chọn "Trả tại sân", **When** đăng ký thành công, **Then** vé hiện trạng thái "Thanh toán tại sân" kèm số tiền cần trả và mã QR.
5. **Given** hai người cùng đăng ký những chỗ cuối cùng của một buổi gần như đồng thời, **When** hệ thống xử lý, **Then** tổng số chỗ đã đăng ký không bao giờ vượt quá sức chứa; người không còn đủ chỗ nhận thông báo "Buổi không còn đủ chỗ" kèm số chỗ hiện còn.
6. **Given** người chơi bấm "Xác nhận" nhiều lần hoặc mất mạng rồi thử lại, **When** hệ thống xử lý, **Then** chỉ có một đăng ký và chỉ bị trừ tiền (giả lập) một lần.
7. **Given** người chơi đã có đăng ký còn hiệu lực ở buổi này, **When** mở chi tiết buổi, **Then** không còn nút "Đăng ký" mà là "Xem vé của bạn".
8. **Given** mất mạng khi đang ở màn hình xác nhận, **When** bấm "Xác nhận", **Then** app báo không có kết nối, giữ nguyên lựa chọn và cho thử lại.
9. **Given** buổi vừa bị chủ sân hủy hoặc vừa bắt đầu trong lúc người chơi đang xác nhận, **When** bấm "Xác nhận", **Then** hệ thống từ chối và app báo lý do cụ thể.

---

### User Story 3 - Xem vé và lịch sử lượt vãng lai (Priority: P2)

Trong mục "Lượt vãng lai của tôi", người chơi thấy các lượt sắp tới và đã qua. Mở một lượt sắp tới để xem vé: mã QR lớn, thông tin buổi, số người, trạng thái thanh toán, nút chỉ đường và nút hủy. Lượt đã qua hiện kết quả: đã tham gia, đã hủy, buổi bị hủy, hoặc không đến.

**Why this priority**: Người chơi cần mở vé khi đến sân và theo dõi các buổi mình đã đăng ký; nhưng vé đã hiện ngay sau khi đăng ký (User Story 2) nên danh sách đứng sau.

**Independent Test**: Với một tài khoản có 2 lượt sắp tới, 1 lượt đã tham gia, 1 lượt đã hủy, mở "Lượt vãng lai của tôi" và kiểm tra mỗi lượt nằm đúng tab với đúng nhãn; mở vé sắp tới thấy QR; bật chế độ máy bay vẫn mở được vé đã xem trước đó.

**Acceptance Scenarios**:

1. **Given** người chơi có lượt sắp tới, **When** mở "Lượt vãng lai của tôi", **Then** tab "Sắp tới" liệt kê các lượt theo thời gian bắt đầu gần nhất, mỗi lượt có ngày giờ, cơ sở, số người, trạng thái thanh toán.
2. **Given** người chơi mở vé của một lượt sắp tới, **When** màn hình hiện, **Then** thấy mã QR đủ lớn để quét, mã vé dạng chữ (dùng khi không quét được), thông tin buổi và nút "Chỉ đường".
3. **Given** người chơi đã mở vé ít nhất một lần, **When** mất mạng tại sân và mở lại vé, **Then** vé và mã QR vẫn hiện được.
4. **Given** các lượt đã qua, **When** mở tab "Đã qua", **Then** mỗi lượt có nhãn: "Đã tham gia" (đã check-in), "Đã hủy", "Buổi bị hủy" hoặc "Không đến" (buổi đã kết thúc mà chưa check-in).
5. **Given** chủ sân đổi giờ hoặc hủy buổi sau khi người chơi đã đăng ký, **When** người chơi mở vé, **Then** thấy thông tin mới kèm nhãn "Buổi đã thay đổi" hoặc "Buổi đã bị hủy"; vé của buổi bị hủy không còn mã QR.
6. **Given** người chơi chưa đăng ký lượt nào, **When** mở "Lượt vãng lai của tôi", **Then** thấy màn hình trống kèm nút "Tìm buổi vãng lai".

---

### User Story 4 - Hủy đăng ký theo chính sách (Priority: P2)

Người chơi không đi được thì mở vé và bấm "Hủy đăng ký". App hiện chính sách hủy và số tiền được hoàn (giả lập) trước khi người chơi xác nhận. Sau khi hủy, chỗ được trả lại cho buổi để người khác đăng ký (hoặc người trong hàng chờ, spec 081).

**Why this priority**: Cần cho sự công bằng và để buổi không bị trống chỗ ảo, nhưng ít dùng hơn tìm và đăng ký.

**Independent Test**: Hủy một lượt 2 người đã thanh toán, trước giờ bắt đầu hơn 4 giờ: được hoàn 100% và buổi tăng lại 2 chỗ. Hủy một lượt khác trong vòng 4 giờ: app báo không hoàn tiền, xác nhận thì vẫn hủy được và chỗ được trả lại.

**Acceptance Scenarios**:

1. **Given** lượt sắp tới còn hơn 4 giờ nữa mới bắt đầu, **When** người chơi bấm "Hủy đăng ký", **Then** app hiện "Bạn được hoàn 100% (X đ)" (hoặc "Không mất phí" nếu trả tại sân) và yêu cầu xác nhận.
2. **Given** lượt sắp tới còn 4 giờ hoặc ít hơn, **When** người chơi bấm "Hủy đăng ký", **Then** app báo rõ không được hoàn tiền; xác nhận thì vẫn hủy được.
3. **Given** người chơi xác nhận hủy, **When** hệ thống xử lý xong, **Then** lượt chuyển sang "Đã hủy", mã QR không còn hiệu lực, số chỗ của buổi tăng đúng số người của lượt, và trạng thái thanh toán ghi nhận hoàn tiền nếu có.
4. **Given** buổi đã bắt đầu, **When** người chơi mở vé, **Then** không còn nút hủy.
5. **Given** chủ sân đã đổi giờ buổi sau khi người chơi đăng ký, **When** người chơi hủy (bất kể còn bao lâu), **Then** được hoàn 100%.
6. **Given** người khác (không phải người đăng ký), **When** cố hủy lượt đó bằng bất kỳ cách nào, **Then** hệ thống từ chối.
7. **Given** người chơi bấm hủy nhiều lần hoặc thử lại khi mất mạng, **When** hệ thống xử lý, **Then** chỉ hủy một lần, số chỗ chỉ được trả lại một lần.

---

### User Story 5 - Đánh giá buổi chơi sau khi tham gia (Priority: P3)

Sau khi buổi kết thúc, lượt đã check-in hiện nút "Đánh giá buổi chơi". Người chơi chấm sao theo các tiêu chí, viết bình luận, đính kèm ảnh như khi đánh giá sân (spec 070). Đánh giá hiện trong danh sách đánh giá của cơ sở kèm nhãn "Buổi vãng lai".

**Why this priority**: Giúp người chơi khác chọn buổi tốt, nhưng không chặn luồng chính tìm – đăng ký – chơi.

**Independent Test**: Với một lượt đã check-in của buổi đã kết thúc, gửi đánh giá 3 tiêu chí; kiểm tra đánh giá hiện ở danh sách đánh giá của cơ sở với nhãn "Buổi vãng lai". Thử với lượt "Không đến" và lượt đã hủy thì không đánh giá được.

**Acceptance Scenarios**:

1. **Given** lượt đã check-in và buổi đã kết thúc, chưa quá hạn đánh giá, **When** người chơi mở lượt đó trong tab "Đã qua", **Then** thấy nút "Đánh giá buổi chơi".
2. **Given** người chơi chấm đủ 3 tiêu chí và gửi, **When** đánh giá được lưu, **Then** đánh giá hiện trong danh sách đánh giá của cơ sở kèm nhãn "Buổi vãng lai" và nút trên lượt đổi thành "Xem đánh giá của bạn".
3. **Given** lượt "Không đến", "Đã hủy" hoặc "Buổi bị hủy", **When** người chơi xem lượt đó, **Then** không có nút đánh giá; nếu cố gửi bằng cách khác thì hệ thống từ chối.
4. **Given** lượt đã có đánh giá, **When** người chơi (hoặc yêu cầu không qua app) cố tạo đánh giá thứ hai, **Then** hệ thống từ chối; mỗi lượt có tối đa một đánh giá.
5. **Given** người chơi đã đánh giá, **When** muốn sửa, xóa, hoặc chủ sân muốn phản hồi, **Then** áp dụng đúng quy tắc của spec 070 (hạn sửa, xóa bất cứ lúc nào, một phản hồi của chủ sân).

---

### Edge Cases

- **Đăng ký chỗ cuối cùng cùng lúc:** nhiều người cùng đăng ký các chỗ cuối thì tổng số người đã đăng ký không bao giờ vượt sức chứa; buổi tự chuyển sang "Đã đủ người" khi hết chỗ và trở lại "Còn chỗ" khi có người hủy.
- **Nhóm lớn hơn số chỗ còn lại:** không đăng ký một phần; app báo số chỗ còn lại để người chơi giảm số người.
- **Đăng ký lại sau khi hủy:** người chơi được đăng ký lại cùng buổi nếu buổi vẫn còn chỗ và chưa bắt đầu.
- **Đổi số người sau khi đăng ký:** không sửa trực tiếp; người chơi hủy rồi đăng ký lại (áp dụng chính sách hủy).
- **Trùng giờ với lượt hoặc đơn đặt sân khác của chính mình:** app cảnh báo trước khi xác nhận nhưng vẫn cho đăng ký.
- **Trình độ không khớp hồ sơ:** app hiện ghi chú "Buổi dành cho trình độ X" nhưng không chặn đăng ký.
- **Chủ sân đăng ký buổi do chính mình mở:** không được phép.
- **Chủ sân hủy buổi:** mọi lượt của buổi chuyển sang "Buổi bị hủy", hoàn 100% (giả lập) cho lượt đã thanh toán, mã QR hết hiệu lực; việc hủy buổi thuộc spec 112, spec này hiển thị kết quả.
- **Chủ sân đổi giờ/sân/giá:** lượt đã đăng ký giữ nguyên số tiền đã chốt lúc đăng ký; người chơi được hủy và hoàn 100% bất kể còn bao lâu.
- **Cơ sở bị ẩn/khóa sau khi đã đăng ký:** vé vẫn hiện, kèm ghi chú "Cơ sở tạm ngừng hoạt động"; xử lý buổi thuộc chủ sân/admin.
- **Buổi kết thúc mà chưa check-in:** lượt hiện "Không đến"; không tự hoàn tiền, không đánh giá được.
- **Người chơi xóa tài khoản:** lượt sắp tới bị hủy theo chính sách hủy, chỗ được trả lại (theo spec 011 FR-022a).
- **Đồng hồ điện thoại sai:** mọi mốc thời gian (đóng đăng ký, mốc 4 giờ, đã bắt đầu hay chưa) tính theo giờ server.
- **Hết hạn đăng ký:** đăng ký đóng đúng lúc buổi bắt đầu; người chơi đang ở màn hình xác nhận lúc đó nhận thông báo "Buổi đã bắt đầu".

## Requirements *(mandatory)*

### Functional Requirements

**Danh sách và bộ lọc**

- **FR-001**: Hệ thống PHẢI hiển thị danh sách buổi vãng lai còn nhận đăng ký hoặc đã đủ người, chưa bắt đầu, thuộc cơ sở đang hoạt động; sắp xếp theo thời gian bắt đầu gần nhất, tải theo trang (mỗi lần 20 buổi).
- **FR-002**: Mỗi buổi trong danh sách PHẢI hiển thị: ngày, giờ bắt đầu–kết thúc, tên cơ sở, quận, trình độ, số chỗ còn lại trên sức chứa, giá mỗi lượt; buổi đã đủ người có nhãn "Đã đủ người".
- **FR-003**: Người chơi PHẢI lọc được theo: khu vực (chọn nhiều quận từ danh mục), ngày (hôm nay đến 14 ngày tới), khung giờ (Sáng 05:00–12:00, Chiều 12:00–17:00, Tối 17:00–23:00, theo giờ bắt đầu), trình độ (Mới chơi, Trung bình, Khá/Nâng cao), mức giá tối đa mỗi lượt. Các bộ lọc kết hợp với nhau (thỏa mãn tất cả); mỗi bộ lọc đang áp dụng hiện thành chip có thể bỏ.
- **FR-004**: Bộ lọc khu vực PHẢI được điền sẵn theo khu vực hay chơi trong hồ sơ (nếu có) và PHẢI nhận được điều kiện điền sẵn từ AI Assistant (spec 022).
- **FR-005**: Chi tiết buổi PHẢI hiển thị thêm địa chỉ cơ sở, sân con dùng cho buổi, ghi chú của chủ sân, và số chỗ còn lại cập nhật theo thời gian thực khi màn hình đang mở.

**Đăng ký**

- **FR-006**: Người chơi đã đăng nhập PHẢI đăng ký được một buổi còn chỗ và chưa bắt đầu, chọn số người từ 1 đến tối đa 4 (bản thân và tối đa 3 bạn đi kèm) và không vượt số chỗ còn lại. Bạn đi kèm không cần có tài khoản.
- **FR-007**: Việc kiểm tra số chỗ còn lại và ghi đăng ký PHẢI xảy ra trong cùng một bước nguyên tử ở phía server; tổng số người đã đăng ký của một buổi KHÔNG ĐƯỢC vượt sức chứa trong mọi trường hợp, kể cả khi nhiều người đăng ký cùng lúc.
- **FR-008**: Mỗi người chơi có tối đa một đăng ký còn hiệu lực cho mỗi buổi; bấm nhiều lần, thử lại khi mất mạng hay gửi từ hai thiết bị KHÔNG ĐƯỢC tạo đăng ký trùng hay trừ tiền hai lần.
- **FR-009**: Số tiền của lượt PHẢI do server tính bằng giá mỗi lượt tại thời điểm đăng ký × số người, lưu bằng số nguyên VND; người dùng KHÔNG ĐƯỢC tự ghi số tiền, trạng thái thanh toán hay trạng thái đăng ký.
- **FR-010**: Người chơi PHẢI chọn được một trong hai cách thanh toán: "Thanh toán ngay (giả lập)" (một màn hình thanh toán mẫu luôn thành công, không có số dư ví; lượt được ghi nhận đã thanh toán ngay khi đăng ký thành công, khi được hoàn tiền thì trạng thái thanh toán chuyển thành "Đã hoàn tiền") hoặc "Trả tại sân" (lượt ghi nhận chưa thanh toán; chủ sân thu khi check-in, spec 112). App PHẢI ghi rõ đây là thanh toán giả lập, không thu tiền thật.
- **FR-011**: Hệ thống PHẢI từ chối đăng ký khi buổi không còn đủ chỗ, đã bắt đầu, đã bị hủy, thuộc cơ sở bị ẩn/khóa, hoặc người đăng ký là chủ của cơ sở mở buổi; app PHẢI báo lý do cụ thể.
- **FR-012**: App PHẢI cảnh báo (không chặn) khi buổi trùng giờ với một lượt vãng lai hoặc đơn đặt sân còn hiệu lực khác của người chơi, và khi trình độ của buổi khác trình độ trong hồ sơ.

**Vé lượt và QR**

- **FR-013**: Mỗi đăng ký thành công PHẢI có một vé gồm: mã QR check-in, mã vé dạng chữ, thông tin buổi, số người, số tiền và trạng thái thanh toán. Một mã QR dùng cho cả nhóm của lượt đó.
- **FR-014**: Mã QR PHẢI là chuỗi ngẫu nhiên do server sinh, không đoán được và không chứa thông tin cá nhân; chỉ người đăng ký (và chủ sân khi check-in, spec 112) đọc được. Mã QR của lượt đã hủy hoặc của buổi bị hủy PHẢI hết hiệu lực.
- **FR-015**: Vé đã mở ít nhất một lần PHẢI xem được khi mất mạng.

**Hủy đăng ký**

- **FR-016**: Người đăng ký PHẢI hủy được lượt của mình trước khi buổi bắt đầu; chỉ người đăng ký được hủy lượt đó (kiểm tra ở phía server). Không hủy một phần số người.
- **FR-017**: Chính sách hủy: hủy trước giờ bắt đầu hơn 4 giờ được hoàn 100% số tiền đã thanh toán (giả lập); hủy trong vòng 4 giờ vẫn được hủy nhưng không hoàn tiền; nếu chủ sân đã đổi giờ, sân hoặc giá sau khi người chơi đăng ký thì luôn được hoàn 100%. Mốc thời gian tính theo giờ server. App PHẢI hiển thị số tiền được hoàn trước khi người chơi xác nhận.
- **FR-018**: Hủy lượt và trả lại chỗ cho buổi PHẢI xảy ra trong cùng một bước nguyên tử ở phía server; hủy nhiều lần chỉ có tác dụng một lần. Buổi đã đủ người PHẢI trở lại trạng thái còn chỗ khi có chỗ được trả lại (việc đẩy người từ hàng chờ lên thuộc spec 081).

**Lịch sử**

- **FR-019**: Người chơi PHẢI xem được "Lượt vãng lai của tôi" gồm tab "Sắp tới" (theo thời gian bắt đầu gần nhất) và "Đã qua" (mới nhất trước, tải theo trang); mỗi lượt đã qua có một nhãn kết quả: "Đã tham gia", "Đã hủy", "Buổi bị hủy" hoặc "Không đến".
- **FR-020**: Khi chủ sân thay đổi hoặc hủy buổi, lượt của người chơi PHẢI hiện thông tin mới nhất kèm nhãn "Buổi đã thay đổi" hoặc "Buổi đã bị hủy"; lượt của buổi bị hủy được hoàn 100% (giả lập).
- **FR-021**: Đăng ký vãng lai của một người chỉ người đó, chủ cơ sở mở buổi và admin đọc được; người chơi khác chỉ thấy số chỗ còn lại, không thấy danh sách hay tên, ảnh của người đăng ký (kể cả khi cùng đăng ký buổi đó). Chi tiết buổi không có phần "Người tham gia"; giao lưu giữa người chơi thuộc nhóm 09.

**Đánh giá buổi chơi**

- **FR-022**: Người chơi PHẢI đánh giá được một lượt khi lượt đó thuộc chính mình, đã check-in, buổi đã kết thúc và chưa quá hạn đánh giá; điều kiện kiểm tra ở phía server. Mỗi lượt có tối đa một đánh giá.
- **FR-023**: Đánh giá buổi vãng lai PHẢI dùng cùng tiêu chí, giới hạn (ảnh, độ dài bình luận, hạn đánh giá và hạn sửa) và quy tắc sửa/xóa/phản hồi của chủ sân như đánh giá sân trong spec 070; hiển thị trong danh sách đánh giá của cơ sở kèm nhãn "Buổi vãng lai" và được tính vào điểm trung bình của cơ sở.

**Trải nghiệm chung**

- **FR-024**: Mỗi màn hình có dữ liệu (danh sách buổi, chi tiết buổi, lượt của tôi, vé) PHẢI có đủ 4 trạng thái: đang tải, có dữ liệu, rỗng, lỗi kèm nút thử lại. Mất mạng PHẢI hiện thông báo, giữ nguyên lựa chọn đang nhập, không crash.
- **FR-025**: Luồng từ danh sách đến khi nhận vé PHẢI không quá 4 bước: danh sách → chi tiết buổi → xác nhận (số người, thanh toán) → vé.
- **FR-026**: Hệ thống PHẢI phát sinh sự kiện để nhóm thông báo (spec 100) gửi: đăng ký thành công, hủy đăng ký thành công, buổi bị đổi hoặc hủy (đến mọi người đã đăng ký). Nội dung và cách gửi thuộc spec 100.
- **FR-027**: Thời gian lưu theo UTC và hiển thị theo múi giờ Việt Nam; tiền hiển thị dạng "80.000 đ".

### Key Entities

- **Buổi vãng lai (Drop-in session)**: Một buổi chơi theo lượt do chủ sân mở tại một cơ sở (tạo ở spec 112). Gồm: cơ sở, chủ sân, quận, các sân con dùng, ngày, giờ bắt đầu–kết thúc, trình độ, sức chứa, số người đã đăng ký (chỉ hệ thống ghi), giá mỗi lượt, ghi chú, trạng thái (còn chỗ, đã đủ người, đã hủy, đã kết thúc).
- **Đăng ký (Registration)**: Lượt đăng ký của một người chơi cho một buổi; mỗi người tối đa một đăng ký mỗi buổi. Gồm: số người, trạng thái (đã đăng ký, đã hủy, đã check-in; trạng thái chờ thuộc spec 081), cách thanh toán, số tiền, trạng thái thanh toán, mã QR (chỉ hệ thống ghi), mã yêu cầu chống trùng, thời gian đăng ký/hủy, cờ "buổi đã thay đổi sau khi đăng ký".
- **Vé lượt**: Cách hiển thị của một đăng ký còn hiệu lực cho người chơi: mã QR, mã vé dạng chữ và thông tin buổi.
- **Đánh giá buổi chơi**: Loại đánh giá của spec 070 gắn với một lượt (thay vì một đơn đặt sân); mỗi lượt tối đa một đánh giá.
- **Cơ sở (Venue)** (của spec 111) và **Người dùng** (của spec 011): cung cấp tên, địa chỉ, quận, trạng thái hoạt động; trình độ và khu vực hay chơi của người chơi.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người chơi tìm được một buổi phù hợp và nhận vé QR trong dưới 2 phút kể từ khi mở mục "Vãng lai", qua không quá 4 màn hình.
- **SC-002**: Khi 20 người cùng lúc đăng ký 5 chỗ cuối cùng của một buổi, đúng 5 chỗ được lấp, số người đã đăng ký không bao giờ vượt sức chứa, và mỗi người không thành công đều nhận thông báo hết chỗ.
- **SC-003**: Bấm "Xác nhận" hoặc "Hủy" 5 lần liên tiếp (hoặc thử lại sau khi mất mạng) chỉ tạo đúng 1 đăng ký / 1 lần hủy, tiền giả lập chỉ bị trừ hoặc hoàn 1 lần.
- **SC-004**: 100% lần thử tự ghi số tiền, trạng thái đăng ký, số chỗ đã đăng ký, hủy lượt của người khác, đọc đăng ký của người khác hoặc đánh giá lượt chưa check-in đều bị từ chối, kể cả khi gửi không qua app.
- **SC-005**: Số tiền hoàn khi hủy đúng chính sách trong 100% kịch bản kiểm thử (trước hơn 4 giờ, trong vòng 4 giờ, đúng mốc 4 giờ, buổi đã thay đổi).
- **SC-006**: Trang đầu danh sách buổi vãng lai tải xong trong dưới 3 giây trên mạng 4G; số chỗ còn lại trên chi tiết buổi cập nhật trong vòng 5 giây sau khi người khác đăng ký hoặc hủy.
- **SC-007**: Vé QR đã xem trước đó mở được trong dưới 1 giây khi không có mạng.
- **SC-008**: Ít nhất 4/5 người thử (thành viên nhóm khác) tự tìm, đăng ký kèm 1 bạn và mở được vé mà không cần hướng dẫn.

## Assumptions

- **Giới hạn nhóm 4 người** (bản thân + tối đa 3 bạn): đã chốt ở Clarifications, vừa một sân đánh đôi và để một người không chiếm hết chỗ.
- **Chính sách hủy mốc 4 giờ, hoàn 100% / 0%**: đã chốt ở Clarifications; cần báo B để chính sách hủy đặt sân (câu hỏi #3 mục 9 đặc tả CSDL) dùng cùng mốc nếu được.
- **Điều kiện đánh giá là đã check-in**: đã chốt ở Clarifications. Nếu chủ sân quên quét QR thì người chơi không đánh giá được, nên cần báo D để spec 112 nhắc chủ sân check-in đủ người.
- **Đánh giá buổi vãng lai tính vào điểm trung bình của cơ sở**, dùng chung tiêu chí và quy tắc với spec 070; cách cập nhật điểm dùng lại cơ chế đã thiết kế trong plan 070.
- Không có bước giữ chỗ tạm và không cần chủ sân duyệt: đăng ký được xác nhận ngay khi server ghi thành công (thanh toán giả lập xảy ra trong cùng bước). Voucher không áp dụng cho buổi vãng lai ở phiên bản này.
- Đăng ký đóng lúc buổi bắt đầu. Danh sách chỉ hiện buổi trong 14 ngày tới.
- Trình độ dùng chung 3 mức với hồ sơ (spec 011): Mới chơi, Trung bình, Khá/Nâng cao.
- Mở/sửa/hủy buổi, quét QR check-in và thu tiền tại sân thuộc spec 112 (D); buổi chuyển sang "đã kết thúc" ở phía server khi qua giờ kết thúc. Spec này chỉ đọc các trạng thái đó.
- Hàng chờ khi buổi đã đủ người, tự đẩy người lên và thông báo có chỗ trống thuộc spec 081; spec này chỉ đảm bảo chỗ được trả lại đúng khi có người hủy.
- Lối vào mục "Vãng lai" nằm ở màn hình chính và từ AI Assistant (spec 022); các điều kiện AI được phép điền (khu vực, ngày, giờ, giá, trình độ) cần thống nhất A ↔ C trước `/speckit-plan`.
- Danh sách loại thông báo ở FR-026 (`DROPIN_REGISTERED`, `DROPIN_CHANGED` và loại cho hủy) phải gửi cho D trước 14/10.
- Khách chưa đăng nhập xem được danh sách và chi tiết buổi nhưng phải đăng nhập (spec 010) khi bấm "Đăng ký".
