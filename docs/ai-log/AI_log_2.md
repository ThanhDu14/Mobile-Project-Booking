# Nhật ký sử dụng AI – Thành viên 2 (Lê Quốc Hưng)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 2** (mã **B**, phụ trách nhóm 04 Đặt sân · 05 Thanh toán và khuyến mãi · 06 Quản lý lịch đặt). Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-06 | Viết spec 040 (lưới khung giờ đặt sân và giữ chỗ) bằng `/speckit-specify` | 04 |
| 2 | 2026-10-06 | Làm rõ spec 040 bằng `/speckit-clarify` (3 câu hỏi) | 04 |
| 3 | 2026-10-06 | Viết spec 041 (đặt sân cố định theo tuần) bằng `/speckit-specify` | 04 |
| 4 | 2026-10-06 | Làm rõ spec 041 bằng `/speckit-clarify` (3 câu hỏi) | 04 |
| 5 | 2026-10-06 | Viết spec 050 (thanh toán và khuyến mãi giả lập) bằng `/speckit-specify` | 05 |
| 6 | 2026-10-06 | Làm rõ spec 050 bằng `/speckit-clarify` (3 câu hỏi) | 05 |
| 7 | 2026-10-06 | Viết spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-specify` | 06 |
| 8 | 2026-10-06 | Làm rõ spec 060 bằng `/speckit-clarify` (3 câu hỏi) | 06 |
| 9 | 2026-10-10 | Rà soát điểm giao thoa liên thành viên & Cập nhật slot 30 phút cho spec 040, 041 | 04, 05, 06 |

---

## Mục 1: Viết spec 040 (lưới khung giờ đặt sân và giữ chỗ) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-specify` |
| Mục đích | Soạn thảo và đánh giá bản mô tả tính năng đặt sân theo khung giờ, sinh bản đặc tả `spec.md` và checklist chất lượng |
| Nhánh Git | `Hung`|

### Prompt đã dùng

**Lần 1: Yêu cầu AI đánh giá bản nháp mô tả tính năng**

User đưa bản nháp prompt mô tả chi tiết: vai trò người chơi, chọn ngày (hôm nay đến 30 ngày), xem lưới khung giờ 3 trạng thái, chọn nhiều sân/slot liên tiếp, thanh tóm tắt tạm tính giá, bấm tiếp tục giữ chỗ 5 phút theo nguyên tắc all-or-nothing, chống trùng lịch và double-submit, hết hạn 5 phút nhả slot, luồng ≤ 5 bước, xử lý mất mạng, rỗng, auth guard với state restoration, tiêu chí đo lường và ranh giới với spec 050.

**Lần 2: Chạy lệnh `/speckit-specify`**

```text
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/040-booking-slot-grid
Người chơi đã đăng nhập đặt sân cầu lông theo khung giờ. Từ chi tiết sân, người chơi chọn ngày
(từ hôm nay đến 30 ngày tới), xem lưới khung giờ của từng sân con với 3 trạng thái: Còn trống,
Đã đặt/Đang được giữ, Bị khóa. Khung giờ đã qua trong ngày hôm nay không chọn được. Người chơi
chọn nhiều sân con và nhiều khung giờ liên tiếp trong một đơn; thanh tóm tắt hiện số slot đã chọn
và tổng tiền tạm tính theo bảng giá của sân (giờ thường, cao điểm, cuối tuần).

Bấm "Tiếp tục" để sang màn hình xác nhận (chi tiết slot, tổng tiền) và bắt đầu giữ chỗ 5 phút
có đồng hồ đếm ngược. Giữ chỗ theo nguyên tắc tất cả hoặc không: nếu một slot trong lựa chọn
vừa bị người khác lấy thì không slot nào được giữ; app báo rõ slot nào đã mất, lưới cập nhật
và các slot còn trống vẫn ở trạng thái đang chọn. Hai người cùng giữ một slot thì chỉ một người
thành công. Bấm nhiều lần hoặc bấm "Thử lại" không tạo đơn trùng. Rời màn hình xác nhận hoặc hết
5 phút thì slot được nhả; hết giờ khi đang xem thì quay về lưới, giữ lựa chọn cũ nếu slot còn trống.
Luồng từ chi tiết sân đến khi đặt xong không quá 5 bước (tính cả thanh toán của spec 050).

Luồng lỗi và trạng thái rỗng: mất mạng (báo lỗi rõ, có nút thử lại), sân không còn giờ trống
trong ngày, ngày sân không mở cửa, người chưa đăng nhập bấm "Tiếp tục" (yêu cầu đăng nhập,
giữ nguyên lựa chọn sau khi đăng nhập).

Tiêu chí đo được: lưới giờ tải dưới 3 giây trên 4G; khi nhiều người cùng giữ một slot thì đúng
1 người thành công; thao tác lặp lại không tạo quá 1 đơn.

Ranh giới: spec này kết thúc khi đơn ở trạng thái HOLD và chuyển sang màn thanh toán (spec 050).
Không bao gồm: đặt cố định theo tuần (spec 041), chọn phương thức thanh toán và voucher
(spec 050), quản lý danh sách đơn (spec 060).
Ràng buộc: tuân thủ constitution nguyên tắc II và III; cách triển khai để phần /speckit-plan.
Dữ liệu: slots, bookings, venues/courts, venues/priceRules theo docs/design/database/README.md.
Điểm giao cần làm rõ: cách tính giá khi khung giờ nằm giữa hai priceRules (với D); khóa giờ
BLOCKED (với D); ai chuyển đơn sang COMPLETED (với C).
```

### Tóm tắt phản hồi của AI

- Đánh giá bản mô tả đạt chuẩn chất lượng cao, bám sát các nguyên tắc cốt lõi của Constitution (Nguyên tắc III về tính toàn vẹn đặt lịch, Nguyên tắc V về trải nghiệm XML).
- Khởi tạo thư mục `specs/040-booking-slot-grid/` và sinh file `spec.md`:
  - 5 User Stories có độ ưu tiên rõ ràng:
    - US1 (P1): Xem lưới khung giờ và chọn slot đặt sân.
    - US2 (P1): Giữ chỗ tạm thời 5 phút với nguyên tắc "Tất cả hoặc không" (All-or-Nothing).
    - US3 (P2): Xử lý hết hạn giữ chỗ và hủy giữ chỗ (đếm ngược 5 phút, timeout auto-release).
    - US4 (P2): Yêu cầu đăng nhập và phục hồi lựa chọn (Auth guard + state restoration).
    - US5 (P3): Chống tạo đơn trùng lặp và xử lý mất kết nối (Idempotency với UUID requestId).
  - 28 yêu cầu chức năng chuẩn hóa (FR-001 → FR-028).
  - 8 tiêu chí thành công đo lường được (SC-001 → SC-008): Tải lưới < 3s trên 4G, zero double-booking, luồng ≤ 3 bước đến màn xác nhận giữ chỗ...
  - 7 trường hợp biên (Edge cases) chi tiết (client time spoofing, giá giao thoa, kín sân...).
  - 4 thực thể chính: `VenueCourt`, `CourtSlot`, `PriceRule`, `Booking`.
- Tạo checklist chất lượng tại `specs/040-booking-slot-grid/checklists/requirements.md`.
- Cập nhật link trỏ đến `spec.md` trong `docs/features/04-booking/README.md`.
- Xác định 3 điểm cần làm rõ (`[NEEDS CLARIFICATION]`) cho bước `/speckit-clarify` tiếp theo:
  1. Độ dài slot (cố định 60 phút hay cho phép cấu hình 30 phút theo từng cơ sở).
  2. Cách tính giá khi slot nằm vắt qua 2 priceRules (thường/cao điểm) với Thành viên D.
  3. Giới hạn số lượng slot tối đa trong một đơn đặt sân.

---

## Mục 2: Làm rõ spec 040 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Giải quyết các điểm còn mơ hồ về độ dài slot, quy tắc tính giá khi giao thoa bảng giá và giới hạn số lượng slot tối đa |
| Nhánh Git | `Hung`  |

### Prompt đã dùng

AI đưa ra 3 câu hỏi clarification dựa trên các điểm [NEEDS CLARIFICATION] từ spec. Người dùng chọn phương án:

```text
Q1: A, Q2: A , Q3: B
```

Bảng đối chiếu lựa chọn:

| # | Câu hỏi của AI | Các phương án | Người dùng chọn | Ý nghĩa kỹ thuật & nghiệp vụ |
|---|---|---|---|---|
| 1 | Quy định độ dài mỗi khung giờ trên lưới | A: Cố định 60 phút<br/>B: 30 hoặc 60 phút theo cấu hình | **A** (Cố định 60 phút) | Giữ lưới khung giờ gọn gàng, tính toán đơn giản, phù hợp thói quen đặt sân |
| 2 | Cách tính giá khi khung giờ nằm giữa 2 bảng giá (với D) | A: Bắt buộc mốc giờ PriceRule tròn 60p<br/>B: Tính theo giờ bắt đầu<br/>C: Chia tỷ lệ phút | **A** (Bắt buộc PriceRule mốc giờ tròn) | Tránh hoàn toàn xung đột giá lẻ; thống nhất với Thành viên D khi cấu hình bảng giá |
| 3 | Giới hạn số lượng slot tối đa trong một đơn đặt | A: Tối đa 4 slot<br/>B: Tối đa 8 slot<br/>C: Không giới hạn | **B** (Tối đa 8 slot) | Cho phép linh hoạt cho nhóm đông người hoặc giải đấu nhỏ, đồng thời vẫn chặn spam giữ chỗ ảo |

### Tóm tắt phản hồi của AI

- Cập nhật mục `Clarifications` trong `specs/040-booking-slot-grid/spec.md`, xóa bỏ hoàn toàn 3 tag `[NEEDS CLARIFICATION]`.
- Cập nhật các yêu cầu chức năng liên quan:
  - `FR-002`: Khung giờ trên lưới cố định 60 phút.
  - `FR-008`: Quy tắc giá (PriceRule) bắt buộc có mốc giờ bắt đầu và kết thúc tròn theo khung 60 phút.
  - `FR-026`: Giới hạn tối đa 8 slot (tương đương 8 giờ chơi) trong một đơn đặt sân.
  - Edge Cases: Làm rõ việc loại bỏ trường hợp xung đột giá lẻ giữa 2 PriceRules nhờ ràng buộc giờ tròn.
- Cập nhật `specs/040-booking-slot-grid/checklists/requirements.md`: Đánh dấu `[x] No [NEEDS CLARIFICATION] markers remain`, toàn bộ 16/16 tiêu chí đạt chuẩn chất lượng.
- Cập nhật `docs/features/04-booking/README.md` sang trạng thái `(đã clarify)`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Độ dài slot 60 phút | Dùng | Đơn giản hóa cấu trúc dữ liệu và đồng nhất với thiết kế CSDL chung |
| Ràng buộc PriceRule tròn giờ | Dùng | Thống nhất với D (người phụ trách nhóm 11/bảng giá), không cần xử lý chia nhỏ phút |
| Giới hạn 8 slot | Dùng | Cân bằng giữa nhu cầu đặt theo nhóm đông và chống chiếm dụng slot ảo |

### Cách kiểm chứng

- Kiểm tra file `specs/040-booking-slot-grid/spec.md` không còn chứa chuỗi `NEEDS CLARIFICATION`.
- Kiểm tra checklist `specs/040-booking-slot-grid/checklists/requirements.md` đạt 100% checkmark.
- Đối chiếu với Constitution Nguyên tắc III (toàn vẹn dữ liệu) và phân công Tuần 1-2.

---

## Mục 3: Viết spec 041 (đặt sân cố định theo tuần) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/041-weekly-recurring-booking`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-specify` |
| Mục đích | Soạn thảo, đánh giá và sinh bản đặc tả kỹ thuật đặt sân cố định theo chu kỳ tuần, xử lý xung đột slot giữa các tuần |
| Nhánh Git | `Hung` |

### Prompt đã dùng

**Lần 1: Yêu cầu AI đánh giá khách quan bản mô tả đề xuất**

User đưa bản thảo phân mục chi tiết: Mục tiêu, Luồng chính (chọn thứ, giờ 60p, sân ưu tiên, chu kỳ 4-12 tuần), Xử lý xung đột (2 cách: đổi sân cùng giờ hoặc bỏ buổi trùng giảm tiền; chênh lệch giá; chặn nếu < 4 buổi), Giữ chỗ (All-or-Nothing, 5 phút, idempotent, recurringGroupId), Lỗi/rỗng, Tiêu chí thành công, Ranh giới, Ràng buộc, Cần làm rõ.
AI đánh giá 9.5/10 và đề xuất 3 tinh chỉnh: quy định chênh lệch giá khi đổi sân, sàn tối thiểu 4 buổi sau khi bỏ trùng, và chuyển giao giới hạn transaction sang plan.

**Lần 2: Chạy lệnh `/speckit-specify` với bản prompt hoàn thiện**

```text
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/041-weekly-recurring-booking
[Toàn bộ nội dung prompt đã được tối ưu theo đề xuất]
```

### Tóm tắt phản hồi của AI

- Khởi tạo thư mục `specs/041-weekly-recurring-booking/` và sinh file `spec.md`:
  - 5 User Stories có độ ưu tiên rõ ràng:
    - US1 (P1): Thiết lập cấu hình lịch đặt cố định theo tuần (quét chu kỳ 4-12 tuần, bảng tổng hợp buổi và giá).
    - US2 (P1): Xử lý xung đột khi có tuần bị trùng lịch (2 tùy chọn đổi sân có duyệt chênh lệch giá hoặc bỏ buổi trùng; chặn nếu < 4 buổi).
    - US3 (P1): Giữ chỗ tạm thời 5 phút toàn chu kỳ với nguyên tắc "Tất cả hoặc không" (All-or-Nothing qua transaction, liên kết chung mã `recurringGroupId`).
    - US4 (P2): Hết hạn giữ chỗ hoặc người chơi hủy giữ chỗ chu kỳ (tự động giải phóng toàn bộ slot chu kỳ, đồng hồ đếm ngược theo server timestamp).
    - US5 (P2): Yêu cầu đăng nhập và khôi phục cấu hình lịch cố định (Auth guard + state restoration).
  - 28 yêu cầu chức năng chuẩn hóa (FR-001 → FR-028).
  - 7 tiêu chí thành công đo lường được (SC-001 → SC-007): Quét 12 tuần < 2s trên 4G, zero double-booking, luồng ≤ 3 bước đến màn xác nhận...
  - 5 trường hợp biên (Edge cases) chi tiết.
  - 4 thực thể chính: `RecurringBookingGroup`, `Booking`, `CourtSlot`, `VenueCourt`.
- Tạo checklist chất lượng tại `specs/041-weekly-recurring-booking/checklists/requirements.md`.
- Cập nhật link trỏ đến spec 041 trong `docs/features/04-booking/README.md`.
- Xác định 3 điểm cần làm rõ (`[NEEDS CLARIFICATION]`):
  1. Cách xử lý các thứ đã qua khi bắt đầu từ tuần hiện tại (bỏ qua buổi đã qua hay dời toàn bộ chu kỳ).
  2. Chính sách đặt cọc/trả trước cho đơn cố định (đối chiếu spec 050: cọc % hay trả 100%).
  3. Quyền hạn của chủ sân về giới hạn số tuần tối đa tại từng cơ sở (đối chiếu spec 111).

---

## Mục 4: Làm rõ spec 041 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/041-weekly-recurring-booking`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Giải quyết các điểm còn mơ hồ về thứ đã qua trong tuần hiện tại, chính sách đặt cọc theo chu kỳ và quyền hạn cấu hình chu kỳ của chủ sân |
| Nhánh Git | `Hung` |

### Prompt đã dùng

AI đưa ra 3 câu hỏi clarification dựa trên các điểm [NEEDS CLARIFICATION] từ spec 041. Người dùng chọn phương án:

```text
Q1: A, Q2: A , Q3: A
```

Bảng đối chiếu lựa chọn:

| # | Câu hỏi của AI | Các phương án | Người dùng chọn | Ý nghĩa kỹ thuật & nghiệp vụ |
|---|---|---|---|---|
| 1 | Xử lý các thứ đã qua khi bắt đầu từ "Tuần hiện tại" | A: Bắt đầu từ buổi hợp lệ tiếp theo ngay trong tuần<br/>B: Dời toàn bộ chu kỳ sang tuần kế tiếp<br/>C: Chặn tùy chọn nếu có thứ đã qua | **A** (Tính từ buổi hợp lệ tiếp theo) | Cho phép người chơi bắt đầu sớm nhất có thể; buổi đã trôi qua không tính vào đơn, chu kỳ kéo dài đủ số tuần kể từ ngày bắt đầu thực tế |
| 2 | Chính sách đặt cọc cho đơn cố định (với spec 050) | A: Cọc theo % (ví dụ 30%) hoặc trả 100% tùy chọn<br/>B: Bắt buộc trả trước 100%<br/>C: Cọc cố định theo số buổi | **A** (Cọc theo % hoặc trả 100%) | Giảm áp lực tài chính cho người chơi đặt dài hạn, đảm bảo cam kết giữ sân cho chủ sân |
| 3 | Quyền hạn cấu hình chu kỳ của chủ sân (với spec 111) | A: Khung chuẩn 4-12 tuần do hệ thống ấn định, chủ sân Bật/Tắt<br/>B: Chủ sân tùy chỉnh số tuần tối đa | **A** (Khung chuẩn 4-12 tuần hệ thống) | Đảm bảo tính nhất quán trên toàn hệ thống app, chủ sân quyết định có nhận đặt cố định hay không |

### Tóm tắt phản hồi của AI

- Cập nhật mục `Clarifications` trong `specs/041-weekly-recurring-booking/spec.md`, xóa bỏ hoàn toàn 3 tag `[NEEDS CLARIFICATION]`.
- Cập nhật Edge Cases: Làm rõ quy tắc xử lý khi bắt đầu từ tuần hiện tại mà có thứ đã trôi qua.
- Cập nhật `specs/041-weekly-recurring-booking/checklists/requirements.md`: Đánh dấu `[x] No [NEEDS CLARIFICATION] markers remain`, hoàn thành 16/16 tiêu chí đạt chuẩn chất lượng.
- Cập nhật `docs/features/04-booking/README.md`: Cả 2 spec 040 và 041 đều ở trạng thái `(đã clarify)`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Bắt đầu từ buổi hợp lệ tiếp theo | Dùng | Tối ưu trải nghiệm cho khách hàng muốn chơi ngay trong tuần |
| Đặt cọc theo tỷ lệ % | Dùng | Phù hợp thực tế thị trường đặt sân cầu lông dài hạn tại Việt Nam |
| Khung 4-12 tuần do hệ thống ấn định | Dùng | Giữ giao diện và logic đồng nhất giữa các cơ sở |

### Cách kiểm chứng

- Kiểm tra file `specs/041-weekly-recurring-booking/spec.md` không còn chứa chuỗi `NEEDS CLARIFICATION`.
- Kiểm tra checklist `specs/041-weekly-recurring-booking/checklists/requirements.md` đạt 100% checkmark.
- Đối chiếu với Constitution và tài liệu phân công CSDL (liên kết `recurringGroupId`).

---

## Mục 5: Viết spec 050 (thanh toán và khuyến mãi giả lập) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 05 – Thanh toán và khuyến mãi (spec `specs/050-payment-voucher`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-specify` |
| Mục đích | Soạn thảo, đánh giá và sinh bản đặc tả kỹ thuật thanh toán giả lập, áp voucher khuyến mãi và tự hủy đơn quá hạn |
| Nhánh Git | `Hung` |

### Prompt đã dùng

**Lần 1: Yêu cầu AI rà soát và hoàn thiện bản nháp mô tả tính năng**

User đưa bản thảo chi tiết gồm: Mục tiêu (thanh toán giả lập học tập, không thu tiền thật), Luồng chính (kế thừa đếm ngược từ 040/041, voucher, 3 phương thức thanh toán: tại sân, cọc, ví giả lập; xác nhận tạo hóa đơn điện tử E-receipt), Giả định mặc định (tính voucher lúc commit, tổng = 0đ miễn chọn phương thức, đơn cố định tính cả chuỗi), Tự hủy đơn quá hạn (slot trống ngay lập tức, server dọn trong 2 phút), Lỗi & biên (ví thiếu cho nạp ảo, race condition trừ ví và voucher), Tiêu chí đo được, Ranh giới (tuân thủ Nguyên tắc II, III, VI Constitution), Cần làm rõ.
AI đánh giá 9.8/10, đề xuất chốt mốc dọn dẹp slot trong tối đa 2 phút và chỉ rõ Nguyên tắc VI trong Constitution.

**Lần 2: Chạy lệnh `/speckit-specify` với bản prompt hoàn chỉnh**

```text
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/050-payment-voucher
[Nội dung prompt hoàn chỉnh theo đề xuất]
```

### Tóm tắt phản hồi của AI

- Khởi tạo thư mục `specs/050-payment-voucher/` và sinh file `spec.md`:
  - 5 User Stories có độ ưu tiên:
    - US1 (P1): Áp dụng mã khuyến mãi (Voucher) cho đơn đặt sân (kiểm tra điều kiện, % có trần hoặc tiền cố định, cập nhật < 1s, tổng = 0đ miễn chọn phương thức).
    - US2 (P1): Lựa chọn phương thức thanh toán và xác nhận giao dịch giả lập (3 phương thức: `AT_VENUE`, `DEPOSIT`, `MOCK_WALLET`, hóa đơn E-receipt có nhãn giao dịch học tập).
    - US3 (P1): Xử lý hết hạn giữ chỗ trong lúc thanh toán (đếm ngược kế thừa, chạm 00:00 khóa nút, slot trống ngay lập tức, dọn trong tối đa 2 phút).
    - US4 (P2): Nạp tiền vào ví giả lập và xử lý số dư không đủ (nạp tiền ảo học tập, chuyển đổi phương thức).
    - US5 (P3): Đảm bảo tính toàn vẹn giao dịch và chống trừ tiền trùng lặp (Idempotent với UUID `requestId`, race condition voucher & ví không âm).
  - 28 yêu cầu chức năng chuẩn hóa (FR-001 → FR-028).
  - 7 tiêu chí thành công đo lường được (SC-001 → SC-007): Áp voucher < 1s, xác nhận < 1.5s trên 4G, zero duplicate charge, 100% hóa đơn gắn nhãn học tập...
  - 5 trường hợp biên (Edge cases) chi tiết.
  - 4 thực thể chính: `Booking`, `Voucher`, `VoucherRedemption`, `UserWallet`.
- Tạo checklist chất lượng tại `specs/050-payment-voucher/checklists/requirements.md`.
- Cập nhật link trỏ đến spec 050 trong `docs/features/05-payment-promotion/README.md`.
- Xác định 3 điểm cần làm rõ (`[NEEDS CLARIFICATION]`):
  1. Trạng thái đơn sau thanh toán (PENDING hay CONFIRMED) và luồng hoàn ví khi chủ sân từ chối (với D, spec 112).
  2. Tỷ lệ cọc mặc định khi chọn DEPOSIT (với D, spec 111).
  3. Số dư ban đầu của ví giả lập và vị trí tính năng nạp tiền ví giả lập (với A, spec 011).

---

## Mục 6: Làm rõ spec 050 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 05 – Thanh toán và khuyến mãi (spec `specs/050-payment-voucher`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Giải quyết các điểm còn mơ hồ về trạng thái đơn sau thanh toán (PENDING/CONFIRMED), tỷ lệ đặt cọc DEPOSIT và khởi tạo số dư ví giả lập |
| Nhánh Git | `Hung` |

### Prompt đã dùng

AI đưa ra 3 câu hỏi clarification dựa trên các điểm [NEEDS CLARIFICATION] từ spec 050. Người dùng chọn phương án:

```text
Q1: A, Q2: A , Q3: B
```

Bảng đối chiếu lựa chọn:

| # | Câu hỏi của AI | Các phương án | Người dùng chọn | Ý nghĩa kỹ thuật & nghiệp vụ |
|---|---|---|---|---|
| 1 | Trạng thái đơn sau khi thanh toán thành công và luồng hoàn tiền | A: Chuyển sang `PENDING` (chờ chủ sân duyệt trong 60 phút; nếu bị từ chối hoặc quá hạn, hoàn 100% ví)<br/>B: Tự động `CONFIRMED` ngay | **A** (Chuyển sang `PENDING`, duyệt trong 60 phút) | Tôn trọng quyền tiếp nhận đơn của chủ sân (spec 112); bảo vệ quyền lợi người chơi bằng cơ chế tự động hoàn ví 100% nếu bị từ chối hoặc quá hạn |
| 2 | Tỷ lệ đặt cọc khi chọn phương thức `DEPOSIT` | A: Cố định 30% trên tổng đơn (toàn hệ thống), làm tròn đến hàng nghìn đồng<br/>B: Do chủ sân cấu hình trong bảng giá | **A** (Cố định 30% toàn hệ thống) | Đồng nhất trải nghiệm người dùng, đơn giản hóa công thức tính toán và giao diện thanh toán |
| 3 | Số dư ví giả lập ban đầu và vị trí nút nạp ví ảo | A: Khởi tạo sẵn 1.000.000đ<br/>B: Khởi tạo 0đ, người dùng tự bấm "Nạp ví giả lập" khi thanh toán hoặc trong trang cá nhân | **B** (Khởi tạo 0đ, cho phép nạp ảo) | Giúp kiểm thử được cả 2 luồng: luồng ví không đủ tiền (báo thiếu, hướng dẫn nạp) và luồng nạp tiền ảo học tập |

### Tóm tắt phản hồi của AI

- Cập nhật mục `Clarifications` trong `specs/050-payment-voucher/spec.md`, xóa bỏ hoàn toàn 3 tag `[NEEDS CLARIFICATION]`.
- Cập nhật các yêu cầu chức năng và trường hợp biên liên quan:
  - `US2 & Scenarios`: Chuyển trạng thái đơn sang `PENDING` chờ chủ sân duyệt trong 60 phút cho cả 3 phương thức.
  - `FR-013`: Cố định tỷ lệ đặt cọc 30% trên tổng giá trị đơn hàng, làm tròn đến hàng nghìn đồng chẵn gần nhất.
  - `FR-017`: Khởi tạo số dư ví tài khoản mới là 0đ, cung cấp nút "Nạp ví giả lập" ngay tại màn hình thanh toán hoặc trang cá nhân.
  - `FR-018`: Quy định rõ đơn chuyển sang `PENDING`, tự động hoàn 100% tiền cọc/ví vào số dư ví nếu chủ sân từ chối hoặc quá 60 phút chưa duyệt.
  - `Edge Cases`: Bổ sung cơ chế hoàn tiền tự động 100% khi đơn quá hạn 60 phút chờ duyệt.
  - `Key Entities & Assumptions`: Ghi nhận `walletBalance` khởi tạo 0đ, tỷ lệ cọc cố định 30%.
- Cập nhật `specs/050-payment-voucher/checklists/requirements.md`: Đánh dấu `[x] No [NEEDS CLARIFICATION] markers remain`, hoàn thành 16/16 tiêu chí đạt chuẩn chất lượng đặc tả.
- Cập nhật `docs/features/05-payment-promotion/README.md` sang trạng thái `(đã clarify)`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Trạng thái `PENDING` + timeout 60p | Dùng | Khớp chặt chẽ với quy trình vận hành của chủ sân (spec 112) và đảm bảo an toàn tiền cọc cho người chơi |
| Cố định 30% đặt cọc | Dùng | Thống nhất toàn hệ thống, tránh phức tạp hóa cho cả người chơi lẫn chủ sân |
| Ví 0đ + nút nạp ảo | Dùng | Phục vụ học tập và demo giáo dục, kiểm thử được toàn bộ edge case ví thiếu tiền |

### Cách kiểm chứng

- Kiểm tra file `specs/050-payment-voucher/spec.md` không còn chứa chuỗi `NEEDS CLARIFICATION`.
- Kiểm tra checklist `specs/050-payment-voucher/checklists/requirements.md` đạt 100% checkmark (16/16).
- Đối chiếu với Constitution Nguyên tắc II (server validation), III (Long VND, deterministic hold), VI (thanh toán giả lập phi thương mại).

---

## Mục 7: Viết spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 06 – Quản lý lịch đặt (spec `specs/060-my-bookings`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-specify` |
| Mục đích | Soạn thảo, đánh giá và sinh bản đặc tả kỹ thuật quản lý lịch đặt, mã QR check-in, hủy đơn hoàn tiền ví giả lập và tiếp tục thanh toán/đặt lại nhanh |
| Nhánh Git | `Hung` |

### Prompt đã dùng

**Lần 1: Yêu cầu AI đánh giá khách quan bản nháp mô tả tính năng**

User đưa bản thảo phân mục chi tiết: Mục tiêu, Luồng chính (5 tab, chi tiết đơn, mã QR check-in, hủy đơn theo mốc 4 tiếng, đặt lại nhanh), Đơn cố định (hủy từng buổi lẻ, phân bổ cọc/giảm giá), Luồng lỗi & biên, Tiêu chí đo được, Ranh giới, Ràng buộc, Cần làm rõ.
AI đánh giá 8/10, chỉ ra các lỗ hổng cần hoàn thiện: bổ sung tab "Chờ thanh toán" cho đơn HOLD (kết nối spec 050 FR-027), ẩn đơn EXPIRED, quy định cụ thể mức phạt hủy trễ theo từng phương thức thanh toán, làm tròn phân bổ tiền cho đơn cố định, và xử lý race condition với chủ sân.

**Lần 2: Chạy lệnh `/speckit-specify` với bản prompt hoàn chỉnh**

```text
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/060-my-bookings
[Nội dung prompt hoàn chỉnh theo đề xuất]
```

### Tóm tắt phản hồi của AI

- Khởi tạo thư mục `specs/060-my-bookings/` và sinh file `spec.md`:
  - 5 User Stories có độ ưu tiên:
    - US1 (P1): Xem danh sách lịch đặt theo 5 tab trạng thái và xem chi tiết đơn (Chờ thanh toán `HOLD`, Chờ duyệt `PENDING`, Sắp tới `CONFIRMED`, Đã hoàn thành `COMPLETED`, Đã hủy `CANCELLED`/`REJECTED`; phân trang, mới nhất xếp trước).
    - US2 (P1): Hiển thị mã QR check-in tại sân cho đơn đã xác nhận (sinh từ `qrToken` bí mật, hỗ trợ cache xem offline khi mất mạng tại sân).
    - US3 (P1): Hủy đơn đặt sân và hoàn tiền ví giả lập theo chính sách (PENDING hoàn 100%; CONFIRMED hủy sớm ≥ 4h hoàn 100%, hủy trễ < 4h không hoàn tiền; nhả slot và hoàn tiền trong cùng transaction; idempotent).
    - US4 (P2): Quản lý và hủy riêng lẻ buổi trong chuỗi đặt cố định theo tuần (xem theo `recurringGroupId`, hủy buổi lẻ theo mốc 4h riêng, phân bổ tiền cọc/giảm giá theo tỷ lệ, dư dồn buổi cuối).
    - US5 (P2): Tiếp tục thanh toán đơn giữ chỗ và đặt lại nhanh từ lịch sử (mở lại spec 050 từ tab Chờ thanh toán; điều hướng 1 chạm sang spec 040 từ tab Hoàn thành/Hủy).
  - 28 yêu cầu chức năng chuẩn hóa (FR-001 → FR-028).
  - 7 tiêu chí thành công đo lường được (SC-001 → SC-007): Tải trang đầu < 2s, hủy đơn < 1.5s, zero duplicate refund, 100% QR offline ready...
  - 9 trường hợp biên (Edge cases) chi tiết.
  - 4 thực thể chính: `Booking`, `CourtSlot`, `UserWallet`, `VoucherRedemption`.
- Tạo checklist chất lượng tại `specs/060-my-bookings/checklists/requirements.md`.
- Cập nhật link trỏ đến spec 060 trong `docs/features/06-booking-management/README.md`.
- Xác định 3 điểm cần làm rõ (`[NEEDS CLARIFICATION]`):
  1. Mốc thời gian hủy đơn 4 tiếng: Cố định toàn hệ thống hay do chủ sân tự cấu hình trong bảng giá/cơ sở (với D, spec 111).
  2. Xử lý no-show (quá giờ chơi không check-in): Chuyển COMPLETED nhãn "Chưa check-in" hay CANCELLED, do hệ thống quét hay chủ sân xác nhận (với C và spec 112).
  3. Mức phạt hủy trễ qua ví giả lập: Mất 100% hay chỉ phạt tiền cọc 30% và hoàn 70% còn lại (với spec 050).

---

## Mục 8: Làm rõ spec 060 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 06 – Quản lý lịch đặt (spec `specs/060-my-bookings`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Giải quyết các điểm còn mơ hồ về mốc thời gian hủy đơn, xử lý đơn vắng mặt (no-show) và mức phạt hủy trễ đối với ví giả lập |
| Nhánh Git | `Hung` |

### Prompt đã dùng

AI đưa ra 3 câu hỏi clarification dựa trên các điểm [NEEDS CLARIFICATION] từ spec 060. Người dùng chọn phương án:

```text
Q1: A , Q2: B, Q3: B
```

Bảng đối chiếu lựa chọn:

| # | Câu hỏi của AI | Các phương án | Người dùng chọn | Ý nghĩa kỹ thuật & nghiệp vụ |
|---|---|---|---|---|
| 1 | Quy định mốc thời gian hủy đơn để được hoàn 100% tiền | A: Cố định 4 tiếng (240 phút) trên toàn hệ thống<br/>B: Do chủ sân cấu hình theo cơ sở<br/>C: Phân tầng tỷ lệ hoàn tiền | **A** (Cố định 4 tiếng toàn hệ thống) | Đồng nhất trải nghiệm người chơi, quy định rõ ràng, dễ nhớ, tránh tranh chấp giữa khách và sân |
| 2 | Xử lý đơn quá giờ chơi mà không đến check-in (No-show) | A: Hệ thống chuyển `COMPLETED` nhãn Chưa check-in<br/>B: Chuyển sang trạng thái riêng `NO_SHOW`, khóa quyền đánh giá ở spec 070<br/>C: Chủ sân chủ động bấm vắng mặt | **B** (Chuyển `NO_SHOW`, khóa quyền đánh giá) | Phân định rạch ròi giữa khách hoàn thành buổi chơi và khách vắng mặt; ngăn chặn đánh giá ảo khi chưa trải nghiệm thực tế |
| 3 | Mức phạt tiền khi hủy đơn trễ đối với phương thức "Ví giả lập" | A: Mất 100% số tiền đã trả<br/>B: Chỉ phạt 30% (tương đương mức cọc), hoàn 70% còn lại vào ví giả lập<br/>C: Phạt 50%, hoàn 50% | **B** (Phạt 30%, hoàn 70% vào ví) | Đảm bảo tính công bằng giữa người thanh toán trước 100% qua ví và người chỉ đặt cọc 30% |

### Tóm tắt phản hồi của AI

- Cập nhật mục `Clarifications` trong `specs/060-my-bookings/spec.md`, xóa bỏ hoàn toàn 3 tag `[NEEDS CLARIFICATION]`.
- Cập nhật các yêu cầu chức năng, trường hợp biên và thực thể liên quan:
  - `US1 & US3`: Bổ sung trạng thái `NO_SHOW` trong tab "Đã hoàn thành" với nhãn cảnh báo; cập nhật chính sách hủy trễ: đơn cọc mất 100% cọc, đơn ví giả lập phạt 30% và tự động hoàn trả 70% vào ví giả lập.
  - `FR-007 & FR-029`: Quy định máy chủ tự động chuyển đơn quá giờ kết thúc slot sang `NO_SHOW` và chặn quyền tạo đánh giá đối với đơn này ở spec 070.
  - `FR-019`: Quy định chi tiết mức phạt hủy trễ theo phương thức thanh toán (cọc mất cọc, ví hoàn 70%, tại sân không phát sinh hoàn).
  - `Edge Cases`: Làm rõ xử lý no-show và cơ chế tự động chuyển `NO_SHOW`.
  - `Key Entities`: Bổ sung `NO_SHOW` vào tập giá trị hợp lệ của `status` trong `Booking`.
  - `Assumptions`: Ghi nhận mốc 4h cố định, phạt 30% ví giả lập và xử lý no-show.
- Cập nhật `specs/060-my-bookings/checklists/requirements.md`: Đánh dấu `[x] No [NEEDS CLARIFICATION] markers remain`, hoàn thành 16/16 tiêu chí đạt chuẩn chất lượng đặc tả.
- Cập nhật `docs/features/06-booking-management/README.md` sang trạng thái `(đã clarify)`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Mốc hủy 4h cố định | Dùng | Đơn giản hóa tính toán cho hệ thống và quy tắc minh bạch cho người chơi |
| Trạng thái `NO_SHOW` + khóa đánh giá | Dùng | Đảm bảo chất lượng dữ liệu đánh giá (spec 070), ngăn chặn review ảo |
| Phạt 30% hoàn 70% ví giả lập | Dùng | Công bằng tài chính giữa các phương thức thanh toán, không đối xử bất lợi với người trả trước 100% |

### Cách kiểm chứng

- Kiểm tra file `specs/060-my-bookings/spec.md` không còn chứa chuỗi `NEEDS CLARIFICATION`.
- Kiểm tra checklist `specs/060-my-bookings/checklists/requirements.md` đạt 100% checkmark (16/16).
- Đối chiếu với Constitution Nguyên tắc II (server validation), III (Long VND, deterministic hold/timestamp), VI (ví giả lập phi thương mại).

---

## Mục 9: Rà soát điểm giao thoa liên thành viên & Cập nhật slot 30 phút cho spec 040, 041

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-10 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040`, `specs/041`), 05 – Thanh toán (`specs/050`), 06 – Quản lý lịch đặt (`specs/060`) |
| Công cụ AI | Antigravity |
| Mục đích | Rà soát các điểm giao thoa kỹ thuật với Thành viên A, C, D; soạn thảo nội dung/câu hỏi trao đổi; đồng thời cập nhật độ dài slot sang 30 phút (thay vì 60 phút) để đồng bộ toàn hệ thống |
| Nhánh Git | `Hung` |

### Bối cảnh và Yêu cầu

- Sau khi kéo cập nhật từ nhánh `main` (tổng cộng 20 specs từ 4 thành viên hoàn tất giai đoạn đặc tả), xuất hiện điểm không nhất quán: Thành viên B trước đó chốt slot 60 phút ở spec 040, trong khi Thành viên D thiết kế quản lý cơ sở và vận hành (spec 111, 112) và Thành viên C mở ca giao lưu (spec 080) đều dựa trên đơn vị slot **30 phút**.
- Người dùng yêu cầu:
  1. Cập nhật đặc tả của Thành viên B (spec 040, 041, database README, requirements checklist, features README) chuyển toàn bộ sang **slot 30 phút**.
  2. Rà soát bảng điểm giao thoa kỹ thuật liên thành viên và soạn bộ câu hỏi/nội dung trao đổi chi tiết mà Thành viên B cần gửi tới các thành viên A, C, D.

### Các thay đổi kỹ thuật đã thực hiện

1. **Cập nhật spec 040 (`specs/040-booking-slot-grid/spec.md`)**:
   - `Clarifications`: Chốt độ dài mỗi slot cố định **30 phút** (ví dụ: `07:00–07:30`, `07:30–08:00`...).
   - Quy tắc tính giá: `priceRules` có mốc giờ tròn theo bước nhảy 30 phút (`HH:00` hoặc `HH:30`). Đơn giá mỗi slot 30 phút quy đổi theo công thức `pricePerSlot = pricePerHour / 2`.
   - Giới hạn chọn: Tối đa 8 slot 30 phút (tương đương tối đa 4 giờ chơi liên tục) trong 1 đơn đặt sân.
   - Cập nhật US1, US2, Edge Cases, FR-002, FR-008, FR-026, CourtSlot entity, SC-001 (lưới hỗ trợ tới 32 khung giờ 30 phút/ngày).
2. **Cập nhật spec 041 (`specs/041-weekly-recurring-booking/spec.md`)**:
   - US1 & FR-003: Áp dụng quy tắc slot 30 phút cố định.
   - FR-004: Cho phép chọn từ 2 đến tối đa 6 slot 30 phút liên tiếp/buổi (tương đương 1 đến 3 giờ/buổi).
   - CourtSlot entity: Chuẩn hóa slot 30 phút.
3. **Cập nhật `docs/design/database/README.md`**:
   - Dòng 153: Chuyển ghi chú độ dài khung giờ sang **30 phút** (thống nhất đồng bộ giữa B và D).
   - Dòng 325: Đánh dấu câu hỏi số 1 là đã giải quyết (`Đã chốt 10/10: Cố định 30 phút toàn hệ thống`).
4. **Cập nhật checklists & README nhóm 04**:
   - `specs/040-booking-slot-grid/checklists/requirements.md`: Cập nhật ghi chú slot 30 phút.
   - `docs/features/04-booking/README.md`: Bổ sung ghi chú thiết kế về độ dài slot 30 phút và quy tắc giá.
5. **Soạn thảo bộ câu hỏi trao đổi liên thành viên (Cross-Member Coordination)**:
   - Gửi Thành viên D: Thống nhất format `priceRules` (bước 30 phút, `pricePerHour / 2`), cơ chế quét timeout 60 phút cho đơn `PENDING`, schema `vouchers` phục vụ áp mã, và danh sách mã thông báo FCM từ module B.
   - Gửi Thành viên C: Khóa quyền đánh giá đối với đơn `NO_SHOW`, cơ chế ghi đè slot `BLOCKED` (`blockReason = DROP_IN`) khi mở ca giao lưu.
   - Gửi Thành viên A: Văn bản FAQ tĩnh chuẩn hóa (hủy đơn, cọc 30%, ví giả lập, voucher) phục vụ intent `faq` của chatbot/AI search.
