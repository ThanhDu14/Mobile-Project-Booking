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
| 10 | 2026-10-12 | Thống nhất liên thành viên (D: 1A, 2A, 3B; C: 4A, 5A; A: 6A) và hoàn thiện các spec | 04, 05, 06 |
| 11 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 040 (lưới ca 30 phút) bằng `/speckit-plan` | 04 |
| 12 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 041 (đặt cố định theo tuần) bằng `/speckit-plan` | 04 |
| 13 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 050 (thanh toán và voucher giả lập) bằng `/speckit-plan` | 05 |
| 14 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-plan` | 06 |
| 15 | 2026-10-19 | Lập danh sách công việc spec 040 (lưới khung giờ đặt sân) bằng `/speckit-tasks` | 04 |
| 16 | 2026-10-19 | Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 040 | 04 |
| 17 | 2026-10-19 | Lập danh sách công việc spec 041 (đặt cố định theo tuần) bằng `/speckit-tasks` | 04 |
| 18 | 2026-10-19 | Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 041 | 04 |
| 19 | 2026-10-19 | Lập danh sách công việc spec 050 (thanh toán và voucher giả lập) bằng `/speckit-tasks` | 05 |
| 20 | 2026-10-19 | Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 050 | 05 |
| 21 | 2026-10-19 | Lập danh sách công việc spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-tasks` | 06 |
| 22 | 2026-10-19 | Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 060 | 06 |
| 23 | 2026-10-26 | Triển khai mã nguồn Phase 1–3 spec 040 bằng `/speckit-implement` | 04 |
| 24 | 2026-10-26 | Triển khai giao diện Phase 4 (UI & ViewModel) spec 040 bằng `/speckit-implement` | 04 |
| 25 | 2026-10-27 | Thống nhất 3 chiều (Spec ↔ Tasks ↔ Sản phẩm) theo Q1–Q13 & Mockup Spec 040 | 04 |
| 26 | 2026-10-27 | Tinh chỉnh giao diện Spec 040 chuẩn Mockup & Sửa lỗi hiển thị UI | 04 |
| 27 | 2026-10-27 | Bổ sung mục Thanh toán (display-only) Màn 2 & Ràng buộc min-2 / max-8 theo từng sân | 04, 05 |




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

---

## Mục 10: Thống nhất liên thành viên (D: 1A, 2A, 3B; C: 4A, 5A; A: 6A) và hoàn thiện các spec

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-12 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040`, `specs/041`), 05 – Thanh toán (`specs/050`), 06 – Quản lý lịch đặt (`specs/060`) |
| Công cụ AI | Antigravity |
| Mục đích | Tiếp nhận kết quả thống nhất phương án kỹ thuật từ các thành viên D, C, A và hoàn thiện đồng bộ toàn bộ tài liệu spec, schema database và README |
| Nhánh Git | `Hung` |

### Các phương án đã được toàn đội chốt

| Mã | Nội dung thống nhất | Thành viên | Phương án chốt | Chi tiết kỹ thuật đã đồng bộ |
|---|---|---|---|---|
| **1** | Bảng giá ca 30 phút | B ↔ D | **1.A** | `priceRules` lưu `pricePerHour` (Long VND), mốc bắt đầu/kết thúc là bội số 30 phút; giá 1 slot = `pricePerHour / 2` |
| **2** | Timeout 60m duyệt đơn PENDING | B ↔ D | **2.A** | Server Cron Job / Supabase Edge Function quét định kỳ mỗi 1-2 phút, tự động chuyển `REJECTED`, hoàn 100% ví giả lập, push FCM |
| **3** | Schema Collection Vouchers | B ↔ D | **3.B** | Tách 2 collection: `venues/{venueId}/vouchers` (voucher cơ sở do chủ sân tạo) và `systemVouchers` (voucher toàn sàn do admin tạo) |
| **4** | Khóa slot ca Drop-in | B ↔ C ↔ D | **4.A** | Ghi vào `slots/{courtId}_{yyyyMMdd}_{HHmm}` với `status = 'BLOCKED'` và `blockReason = 'DROP_IN'` trong transaction; lưới của B hiển thị xám có nhãn khóa |
| **5** | Ràng buộc quyền review | B ↔ C | **5.A** | Chỉ đơn `COMPLETED` (đã quét QR check-in) mới được đánh giá trong 7 ngày; đơn `NO_SHOW` (quá giờ không check-in) bị khóa vĩnh viễn quyền đánh giá |
| **6** | Cơ chế FAQ cho AI Search | A ↔ B | **6.A** | Hardcode template đối sánh intent `faq` tĩnh trong chatbot AI (Spec 022) theo văn bản chuẩn hóa của B, đảm bảo 0% hallucination |

### Các cập nhật tài liệu đã thực hiện

1. **`specs/050-payment-voucher/spec.md`**:
   - Bổ sung phiên làm rõ ngày 2026-10-12 ghi nhận phương án 3.B (tách 2 nguồn voucher), 2.A (Server Cron Job quét timeout 60m hoàn tiền), 6.A (FAQ tĩnh cho AI).
   - Cập nhật FR-005, FR-018 và Key Entities: `VenueVoucher`, `SystemVoucher`, `VoucherRedemption`.
   - Cập nhật checklist `specs/050-payment-voucher/checklists/requirements.md`.
2. **`specs/040-booking-slot-grid/spec.md`**:
   - Ghi nhận phương án 1.A (bảng giá 30 phút) và phương án 4.A (khóa slot `DROP_IN` hiển thị màu xám khóa).
   - Cập nhật FR-003 mô tả chi tiết hiển thị slot `BLOCKED` với `blockReason = DROP_IN`.
3. **`specs/060-my-bookings/spec.md`**:
   - Ghi nhận phương án 2.A (Server cron job quét timeout 60m), 5.A (khóa quyền đánh giá đơn `NO_SHOW`), 6.A (FAQ tĩnh).
4. **`docs/design/database/README.md`**:
   - Cập nhật mục 4.6 thành 2 collection: `venues/{venueId}/vouchers/{code}` và `systemVouchers/{code}`.
   - Cập nhật sơ đồ máy trạng thái bổ sung nhánh `NO_SHOW`.
   - Cập nhật Mục 9: Đánh dấu đã giải quyết xong câu 2 (duyệt đơn 60m) và câu 3 (chính sách hủy & no-show).
5. **README nhóm 05 & 06**:
   - Cập nhật các ghi chú thiết kế về voucher 3.B, timeout 2.A, và no-show 5.A.

---

## Mục 11: Lập kế hoạch kỹ thuật spec 040 (lưới ca 30 phút) bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật chi tiết (Architecture, Data Models, Research, Contracts, Quickstart) cho tính năng lưới khung giờ 30 phút và giữ chỗ |
| Nhánh Git | `feature/040-booking-slot-grid` (hoặc `Hung`) |

### Bối cảnh và Quyết định kỹ thuật cốt lõi

1. **Tối ưu Quota Firestore gói Spark (Sparse Storage - Lưu trữ thưa)**:
   - Client tự động tính toán và sinh lưới ca 30 phút mặc định `AVAILABLE` trong bộ nhớ dựa trên `openTime` và `closeTime` của Venue.
   - Firestore chỉ lưu và phát realtime snapshot trên root collection `slots/{courtId}_{yyyyMMdd}_{HHmm}` đối với các ca bận (`HELD`, `BOOKED`, `BLOCKED`) trong ngày đang chọn với điều kiện `venueId` và `date`.
   - Giúp tiết kiệm hơn 85% lượng Document Reads, đảm bảo an toàn tuyệt đối cho hạn mức gói Spark miễn phí.
2. **Thuật toán chọn ca liên tiếp (`ValidateSlotSelectionUseCase`)**:
   - Chỉ cho phép chọn các ca 30 phút liên tiếp trên cùng 1 sân con (tối đa 8 slot = 4 tiếng).
   - Xử lý thông minh: click nối dài đầu mút, click cách quãng tự động reset chọn mốc mới, click ở giữa tự động cắt đuôi.
3. **Xử lý xung đột Realtime (Race Condition)**:
   - Dùng `callbackFlow` bọc `SnapshotListener`, tự động hủy listener trong `awaitClose` khi rời màn hình hoặc đổi ngày.
   - Khi ca đang chọn bị người khác giữ chỗ (`HELD`) hoặc đặt (`BOOKED`), ViewModel tự động loại bỏ slot đó, tính lại tiền và phát `UiEffect.ShowConflictWarning` hiển thị Snackbar thông báo.
4. **Quy đổi bảng giá 30 phút theo phương án 1.A**:
   - Mọi mốc trong `priceRules` là bội số của 30 phút. Đơn giá mỗi slot: `pricePerSlot = pricePerHour / 2`.
5. **Hợp đồng chuyển tiếp sang Spec 041**:
   - Đóng gói dữ liệu qua Navigation Safe Args: `venueId`, `courtId`, `courtName`, `date`, `selectedSlotTimes: Array<String>`, `subtotalAmount: Long`.

### Các tài liệu kỹ thuật đã sinh

- `specs/040-booking-slot-grid/plan.md`: Kế hoạch kỹ thuật tổng thể (Summary, Context, Constitution Check, Project Structure, Core Rules, Phases).
- `specs/040-booking-slot-grid/research.md`: 5 quyết định kỹ thuật sâu (Sparse Storage, chọn liên tiếp, Realtime conflict, tính giá 30p, Lifecycle listener).
- `specs/040-booking-slot-grid/data-model.md`: Chi tiết Schema Firestore, Kotlin DTOs, Domain Entities (`TimeSlot`, `SlotStatus`, `Court`, `PriceRule`, `SlotSelectionState`) và UI State.
- `specs/040-booking-slot-grid/contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args, Repository Interface, UseCases và bảng mã màu Material 3 cho 6 trạng thái ca.
- `specs/040-booking-slot-grid/quickstart.md`: Hướng dẫn kiểm thử Unit Test, UI Test và kịch bản xác minh thủ công trên Firebase Emulator.

---

## Mục 12: Lập kế hoạch kỹ thuật spec 041 (đặt cố định theo tuần) bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/041-weekly-recurring-booking`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật chi tiết (Architecture, Data Models, Research, Contracts, Quickstart) cho tính năng đặt sân cố định theo tuần (chu kỳ 4–12 tuần) |
| Nhánh Git | `feature/041-weekly-recurring-booking` |

### Bối cảnh và Quyết định kỹ thuật cốt lõi

1. **Vượt giới hạn Firestore `whereIn(30)` bằng Batching song song**:
   - Khi quét khả dụng chu kỳ 12 tuần (lên tới 36 ngày), áp dụng giải thuật `dates.chunked(30)` kết hợp Coroutines `async/awaitAll` để thực thi truy vấn song song.
   - Vừa tránh crash do giới hạn 30 phần tử của Firestore SDK, vừa đảm bảo tốc độ phản hồi cực nhanh dưới 1.2 giây trên mạng 4G (đạt tiêu chí SC-001 < 2s).
2. **Kiểm soát giới hạn 500 operations của Firestore Transaction (All-or-Nothing Hold)**:
   - Toàn bộ chu kỳ tối đa 36 buổi $\times$ 6 slot 30 phút $\approx$ 216 slot documents + 36 booking documents + 1 recurringGroup document = 253 writes, hoàn toàn nằm an toàn dưới trần 500 operations của Firestore Transaction.
   - Cơ chế All-or-Nothing: Nếu có bất kỳ slot nào bị chiếm dở dang, Transaction tự động ném `SlotUnavailableException` để rollback sạch sẽ 100%.
3. **Thuật toán xử lý xung đột lịch thông minh (`ResolveRecurringConflictUseCase`)**:
   - Một buổi chỉ hợp lệ khi toàn bộ $N$ slot 30 phút liên tiếp trên cùng 1 sân con đều trống.
   - Tự động quét tìm sân thay thế cùng `surfaceType` trong cùng khung giờ (giá không đổi theo Phương án 1.A).
   - Cho phép người chơi bỏ qua buổi bị trùng (giảm trừ tiền tương ứng), nhưng bắt buộc phải còn tối thiểu 4 buổi hợp lệ mới cho phép tạo đơn cố định.
4. **Cơ chế chủ động giải phóng chỗ `ReleaseRecurringHoldUseCase`**:
   - Khi người dùng bấm Quay lại (Back) hoặc Hủy từ màn hình xác nhận, app chủ động bắn lệnh giải phóng ngay các slot đang giữ, tránh hiện tượng chiếm dụng ảo (ghost holding) suốt 5 phút.
5. **Hợp đồng chuyển tiếp sang Spec 050**:
   - Đóng gói Bundle Safe Args `RecurringToPaymentArgs`: `recurringGroupId`, `venueId`, `venueName`, `totalSessions`, `subtotalAmount`, `depositAmount` (30%), `holdExpiresAtMillis`.

### Các tài liệu kỹ thuật đã sinh

- `specs/041-weekly-recurring-booking/plan.md`: Kế hoạch kỹ thuật tổng thể (Summary, Context, Constitution Check, Project Structure, Core Rules, Phases).
- `specs/041-weekly-recurring-booking/research.md`: 5 nghiên cứu kỹ thuật chuyên sâu (Chunked queries < 2s, Transaction 500 ops limit, tìm sân cùng surfaceType, giải phóng slot chủ động, phân bổ cọc 30%).
- `specs/041-weekly-recurring-booking/data-model.md`: Chi tiết Schema `recurringGroups`, `bookings`, DTOs, Domain Entities (`RecurringConfig`, `RecurringSession`, `RecurringConflict`, `RecurringSummary`) và UI State.
- `specs/041-weekly-recurring-booking/contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args sang `[050]`, Domain `RecurringBookingRepository` interface và hợp đồng 6 UseCases.
- `specs/041-weekly-recurring-booking/quickstart.md`: Hướng dẫn kiểm thử Unit Test (sinh lịch, bỏ ngày quá khứ, đổi sân, rollback All-or-Nothing), UI Test và kịch bản xác minh thủ công.

---

## Mục 13: Lập kế hoạch kỹ thuật spec 050 (thanh toán và voucher giả lập) bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 05 – Thanh toán và khuyến mãi (`specs/050-payment-voucher`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật chi tiết (Architecture, Data Models, Research, Contracts, Quickstart) cho tính năng thanh toán giả lập, áp dụng voucher đa nguồn và xuất hóa đơn điện tử E-Receipt |
| Nhánh Git | `feature/050-payment-voucher` |

### Bối cảnh và Quyết định kỹ thuật cốt lõi

1. **Giao dịch nguyên tử trong Firestore Transaction (Atomic Transaction)**:
   - Tuân thủ quy tắc 2 pha (Đọc-trước-Ghi-sau): Kiểm tra hạn giữ chỗ (`holdExpiresAt > now`), voucher còn lượt (`usedCount < usageLimit` & `perUserLimit`), và số dư ví ảo ($\ge$ tiền cọc hoặc 100% tiền đơn) trước khi thực hiện ghi.
   - Trừ số dư ví tại `users/{userId}.walletBalance`, ghi log giao dịch `walletTransactions/{txId}`, tăng lượt voucher và ghi `redemptions/{bookingId}` trong cùng Transaction.
   - Chuyển trạng thái đơn sang `PENDING` (chờ chủ sân duyệt 60 phút theo Phương án 2.A) và đồng thời chuyển các `slots` liên quan từ `HOLD` sang `BOOKED` (xóa `holdExpiresAt` 5 phút).
2. **Quản lý đa nguồn Voucher (Phương án 3.B)**:
   - Hỗ trợ song song: `venues/{venueId}/vouchers/{code}` (cơ sở) và `systemVouchers/{code}` (toàn sàn). Document ID chính là mã code viết hoa không dấu để chống tạo trùng mã.
   - Thiết kế document `.../vouchers/{code}/userUsage/{userId}` để đọc và tăng lượt dùng cá nhân (`perUserLimit`) nguyên tử trong Transaction.
3. **Công thức làm tròn đặt cọc 30% an toàn**:
   - `rawDeposit = (payableAmount * 30) / 100`, làm tròn nghìn đồng: `((rawDeposit + 500) / 1000) * 1000`.
   - Xử lý biên: Nếu cọc = 0đ thì lấy `payableAmount`; nếu voucher giảm 100% thì `depositAmount = 0đ` và phương thức tự chuyển thành `FREE`.
4. **Phân tách phương thức thanh toán theo loại đơn**:
   - `AT_VENUE` (trả tại sân): Chỉ áp dụng cho đơn đặt lẻ (`[040]`); tự động ẩn đối với đơn cố định theo tuần (`[041]`) để tránh rủi ro bùng sân dài hạn.
5. **Tuân thủ cam kết học tập phi thương mại (Constitution VI)**:
   - Hiển thị nhãn nổi bật *"Giao dịch giả lập phục vụ học tập - Không thu tiền thật"* trên màn hình thanh toán và E-Receipt.
   - Cung cấp nút nạp ví ảo thử nghiệm (100k, 500k, 1M) tránh tắc nghẽn luồng chấm điểm.
6. **Hợp đồng chuyển tiếp sang Spec 060**:
   - Đóng gói Bundle Safe Args `ReceiptToMyBookingsArgs`: `bookingId`, `isRecurring`, `initialTab = "PENDING"` để mở ngay tab "Chờ duyệt" tại Spec 060.

### Các tài liệu kỹ thuật đã sinh

- `specs/050-payment-voucher/plan.md`: Kế hoạch kỹ thuật tổng thể (Summary, Context, Constitution Check, Project Structure, Core Rules, Phases).
- `specs/050-payment-voucher/research.md`: 5 nghiên cứu kỹ thuật chuyên sâu (Atomic transaction trừ ví/voucher/slot, 2 nguồn voucher 3.B, làm tròn cọc 30%, timeout 60m duyệt đơn PENDING 2.A, cam kết học tập Constitution VI).
- `specs/050-payment-voucher/data-model.md`: Chi tiết Schema Firestore (vouchers, userUsage, redemptions, walletTransactions), DTOs, Domain Entities (`Voucher`, `PaymentMethod`, `PaymentBreakdown`, `PaymentReceipt`) và UI State.
- `specs/050-payment-voucher/contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args từ 040/041 sang 050 và tới 060, Domain `PaymentRepository` interface và hợp đồng các UseCases.
- `specs/050-payment-voucher/quickstart.md`: Hướng dẫn kiểm thử Unit Test (cọc 30%, voucher biên, rollback Transaction thiếu tiền/hết hạn), UI Test và kịch bản xác minh thủ công.

---

## Mục 14: Lập kế hoạch kỹ thuật spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 06 – Quản lý lịch đặt (`specs/060-my-bookings`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật chi tiết (Architecture, Data Models, Research, Contracts, Quickstart) cho tính năng quản lý lịch đặt 5 tab, hiển thị mã QR check-in realtime/offline, chính sách hoàn tiền hủy đơn mốc 240 phút và hủy chuỗi đơn cố định tuần |
| Nhánh Git | `feature/060-my-bookings` |

### Bối cảnh và Quyết định kỹ thuật cốt lõi

1. **Độc lập kiến trúc sạch (Clean Architecture - Constitution I)**:
   - Tầng `domain` chỉ chứa pure Kotlin, hoàn toàn không phụ thuộc vào `android.graphics.Bitmap` hay Firebase SDK. Model `QrBitMatrix(width, height, bits: BooleanArray)` được sinh bởi `GenerateCheckInQrUseCase` (sử dụng thư viện thuần ZXing Core) và chuyển đổi thành `Bitmap` tại tầng UI qua `QrBitmapRenderer.kt` với khung viền trắng `#FFFFFF` và kích thước tối thiểu 240dp để máy quét chủ sân nhận diện tốt kể cả khi thiết bị đang ở chế độ Dark Mode.
2. **Chống gian lận giờ điện thoại bằng `firestore.rules` (Constitution II)**:
   - Sử dụng `request.time` (Server Timestamp của Google Cloud trong Security Rules) để kiểm tra mốc 240 phút khi hủy đơn. Ngăn chặn triệt để hành vi người dùng cố tình chỉnh lùi đồng hồ trên thiết bị Android để nhận hoàn tiền 100% khi hủy trễ.
   - Giới hạn quyền đọc chuỗi nhạy cảm `qrToken` chỉ cho chủ đơn (`request.auth.uid == resource.data.userId`) hoặc chủ sân (`role == "VENUE_OWNER"`).
3. **Thuật toán tính tiền hoàn mốc 240 phút & kẹp biên an toàn khi có Voucher**:
   - Hủy đơn `PENDING` hoặc đơn `CONFIRMED` hủy sớm ($\ge 240$ phút trước giờ chơi): Hoàn 100% tiền đã trả (`refundAmount = paidAmount`).
   - Đơn `CONFIRMED` hủy trễ ($< 240$ phút trước giờ chơi):
     + Thanh toán ví giả lập (`MOCK_WALLET`): Phạt 30% tiền cọc chuẩn hóa, hoàn 70% còn lại.
     + Kẹp biên an toàn chống số tiền âm khi có áp dụng voucher: `penaltyAmount = minOf(booking.paidAmount, calculatedDepositPenalty)`, `refundAmount = maxOf(0L, booking.paidAmount - penaltyAmount)`.
     + Đặt cọc 30% (`DEPOSIT`): Mất 100% tiền cọc (`penaltyAmount = paidAmount`, `refundAmount = 0L`).
     + Trả tại sân (`AT_VENUE`): Hoàn 0đ, phạt 0đ.
4. **Firestore Transaction hủy đơn & nhả Slot nguyên tử (Constitution III)**:
   - Tuân thủ quy tắc 2 pha (All Reads Before All Writes).
   - Đảm bảo tính Idempotent qua document `users/{userId}/walletTransactions/refund_{bookingId}`.
   - Khi hủy đơn, xóa toàn bộ document `slots/{slotId}` thuộc đơn trong Transaction để nhả sân ngay lập tức trên lưới khung giờ của Spec 040.
5. **Khắc phục giới hạn Transaction đối với chuỗi đơn cố định tuần (`recurringGroupId`)**:
   - Do Android Client SDK không hỗ trợ truy vấn (query) bên trong `runTransaction`, tầng Repository thực hiện truy vấn danh sách `DocumentReference` của các buổi còn lại trước, sau đó truyền danh sách tham chiếu này vào Transaction để xử lý hủy nguyên tử.
6. **Cập nhật realtime khi Check-in & Hỗ trợ QR Offline**:
   - Lắng nghe realtime `bookings/{bookingId}` qua Flow để tự động cập nhật UI sang trạng thái *"Đã check-in lúc HH:mm"* và ẩn nút hủy đơn ngay khi chủ sân quét mã QR.
   - Tận dụng Firestore Offline Persistence để người chơi có thể xuất trình mã QR check-in ngay cả khi mất sóng 4G/Wi-Fi tại cụm sân thể thao.
7. **Tuân thủ thỏa thuận liên thành viên**:
   - Phương án 2.A: Tab 2 (Chờ duyệt) đếm ngược 60 phút hạn chủ sân duyệt. Nếu quá 60 phút, đơn tự động hủy (`REJECTED`) và hoàn 100% tiền ví giả lập.
   - Phương án 5.A: Khóa tuyệt đối quyền đánh giá đối với các đơn mang trạng thái `NO_SHOW` (vắng mặt).

### Các tài liệu kỹ thuật đã sinh

- `specs/060-my-bookings/plan.md`: Kế hoạch kỹ thuật tổng thể (Summary, Context, Constitution Check, Project Structure, Core Rules, Phases).
- `specs/060-my-bookings/research.md`: 5 nghiên cứu kỹ thuật chuyên sâu (Composite Index 5 tab, xử lý biên Voucher khi hủy trễ, chống chỉnh giờ bằng Security Rules, Transaction 2 pha, QR Offline).
- `specs/060-my-bookings/data-model.md`: Chi tiết Schema Firestore (bookings, recurringGroups, walletTransactions), DTOs, Domain Entities (`BookingSummaryItem`, `BookingDetail`, `RefundPolicyQuote`, `QrBitMatrix`) và UI States.
- `specs/060-my-bookings/contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args từ 050 sang 060, tới 040/070, Domain `BookingManagementRepository` interface, hợp đồng các UseCases và Firestore Security Rules.
- `specs/060-my-bookings/quickstart.md`: Hướng dẫn kiểm thử Unit Test (mốc 240m, kẹp biên voucher, tính Idempotent), UI Test và 5 kịch bản xác minh thủ công.

---

## Mục 15: Lập danh sách công việc spec 040 (lưới khung giờ đặt sân) bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Phân rã kế hoạch kỹ thuật spec 040 thành danh sách 42 task triển khai chi tiết (`tasks.md`), tuân thủ Clean Architecture, TDD, và giao dịch giữ chỗ 5 phút nguyên tử |
| Nhánh Git | `feature/040-booking-slot-grid` |

### Bối cảnh và Quyết định cốt lõi khi lập Tasks

1. **Tuân thủ quy trình Clean Architecture & Dependency Inversion**:
   - Phân chia Phase 2 (Domain Layer & Core Business Logic) trước Phase 3 (Data Layer & Infrastructure). Tầng domain hoàn toàn thuần Kotlin, không import Android SDK hay Firebase SDK.
   - Viết Unit Tests trước cho các UseCases (`ValidateSlotSelectionUseCaseTest`, `CalculateTotalPriceUseCaseTest`, `GenerateDailySlotsUseCaseTest`, `HoldSlotsTransactionUseCaseTest`, `ReleaseHoldUseCaseTest`) theo tôn chỉ TDD.
2. **Quy chuẩn tiền tệ & Giao dịch nguyên tử (Constitution II & III)**:
   - Toàn bộ số tiền (`pricePerHour`, `subtotalAmount`, `depositAmount`) sử dụng kiểu `Long` (VND, cấm âm). Công thức làm tròn cọc 30% an toàn: `((rawDeposit + 500) / 1000) * 1000`.
   - Firestore Transaction giữ chỗ 5 phút tuân thủ triệt để nguyên tắc **All Reads Before All Writes** và **All-or-Nothing** (chống double-booking khi 2 người cùng giữ 1 ca).
3. **Thống nhất ca 30 phút & 4 trạng thái slot**:
   - Mỗi slot biểu diễn 30 phút (`HHmm`). Người chơi có thể chọn từ 2 đến 6 slot liên tiếp (1h đến 3h).
   - Hiển thị trực quan 4 trạng thái: `AVAILABLE`, `HELD`, `BOOKED`, và `BLOCKED` (kèm tag `DROP_IN` theo Phương án 4.A đã thống nhất liên thành viên).
4. **Phân rã 42 Tasks chi tiết**:
   - **Phase 1 (Setup & Index)**: T001–T003 (Dependencies, Composite Index cho `slots`, Fixtures).
   - **Phase 2 (Domain Layer & TDD)**: T004–T015 (Domain Models, Repository Interface, Unit Tests trước cho UseCases, cài đặt UseCases).
   - **Phase 3 (Data Layer & Firestore Transaction)**: T016–T022 (DTOs, Mappers, Data Source, Transaction All-or-Nothing, Hilt DI Module, Firestore Security Rules).
   - **Phase 4 (UI Layer & ViewModel)**: T023–T034 (XML Layouts Material 3, ListAdapters với DiffUtil, ViewModel với StateFlow ticker 5 phút, BookingSlotGridFragment).
   - **Phase 5 (Navigation, Integration & Edge Cases)**: T035–T038 (Safe Args sang 050, tích hợp đăng nhập Spec 010, xử lý mất mạng `requestId`, dọn dẹp listener trong `onStop`).
   - **Phase 6 (System Verification & Polish)**: T039–T042 (ViewModelTest, FirestoreSlotTransactionTest trên Firebase Emulator, Espresso UI Test, rà soát Checklist).

### Tài liệu đã sinh

- `specs/040-booking-slot-grid/tasks.md`: 42 tasks chi tiết có checkbox `- [ ]`, ID `T001`–`T042`, gắn tag User Story (`[US1]`–`[US5]`), nhãn song song `[P]`, đường dẫn file chính xác và lệnh xác minh `Verify:`.

---

## Mục 16: Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 040

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Đối soát chéo 3 bên giữa `spec.md`, `plan.md`, `tasks.md` và `constitution.md`. Phát hiện các điểm lệch hoặc mơ hồ và tiến hành tinh chỉnh chuẩn hóa trước khi bước vào cài đặt mã nguồn (`/speckit-implement`) |
| Nhánh Git | `feature/040-booking-slot-grid` |

### Kết quả phân tích đối soát chéo (Cross-Artifact Analysis)

- **Tỷ lệ bao phủ (Coverage)**: **100%** (28/28 Functional Requirements và 8/8 Success Criteria đều có ít nhất một task tương ứng trong tổng số 42 tasks).
- **Tuân thủ Constitution**: Không có bất kỳ vi phạm nào (0 Critical, 0 High). Tầng domain độc lập thuần Kotlin, số tiền dùng `Long` VND, giao dịch Firestore Transaction đảm bảo All-or-Nothing và All Reads Before All Writes.
- **Các điểm cần tinh chỉnh**: Phát hiện 2 điểm Low (A1, C1) và 1 điểm Medium (U1) về tính đồng nhất câu chữ và ranh giới dọn dẹp dữ liệu.

### Chi tiết các tinh chỉnh đã thực thi

1. **A1 (Low – Chuẩn hóa giới hạn slot tối đa)**:
   - Cập nhật task `T006` trong `tasks.md`: Xác định rõ ràng đơn ngày lẻ của Spec 040 cho phép chọn tối đa **8 slot 30 phút (= 4 giờ chơi liên tục)** theo đúng FR-026 của `spec.md`, tách biệt rõ với mốc 6 slot (3 giờ) của đơn cố định theo tuần ở Spec 041.
2. **C1 (Low – Đồng nhất tên Bundle Safe Args)**:
   - Cập nhật `plan.md` (§5.3), `contracts/ui-contracts.md` (§1.2) và `tasks.md` (`T035`): Thống nhất sử dụng tên class **`HoldToPaymentArgs`** khi điều hướng từ lưới chọn sân sang thanh toán, khớp hoàn toàn với hợp đồng tiếp nhận của Spec 050.
3. **U1 (Medium – Làm rõ ranh giới dọn dẹp slot `HELD` quá hạn 5 phút)**:
   - Cập nhật task `T038` trong `tasks.md`: Làm rõ cơ chế phía Client là *Lazy Expiration* (tự động coi slot có `holdExpiresAt < serverNow` là `AVAILABLE` để người dùng khác có thể đặt ngay mà không bị khóa nhầm); việc quét xóa vật lý các document slot rác thuộc trách nhiệm của Supabase Edge Function theo lịch của hệ thống (Constitution III).

### Trạng thái tài liệu sau tinh chỉnh

- `specs/040-booking-slot-grid/tasks.md`: Đã cập nhật T006, T035, T038.
- `specs/040-booking-slot-grid/plan.md`: Đã đồng nhất `HoldToPaymentArgs`.
- `specs/040-booking-slot-grid/contracts/ui-contracts.md`: Đã cập nhật §1.2 sang `HoldToPaymentArgs`.
- Toàn bộ bộ tài liệu đạt chất lượng 10/10, sẵn sàng cho việc bắt đầu cài đặt mã nguồn (`/speckit-implement`).

---

## Mục 17: Lập danh sách công việc spec 041 (đặt cố định theo tuần) bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/041-weekly-recurring-booking`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Phân rã kế hoạch kỹ thuật spec 041 thành danh sách 44 task triển khai chi tiết (`tasks.md`), tuân thủ Clean Architecture, TDD, xử lý xung đột đổi sân cùng `surfaceType` (Phương án 1.A) và kiểm soát giới hạn 500 ops Firestore Transaction |
| Nhánh Git | `feature/041-weekly-recurring-booking` |

### Bối cảnh và Quyết định cốt lõi khi lập Tasks

1. **Tuân thủ quy trình Clean Architecture & TDD**:
   - Tầng domain hoàn toàn thuần Kotlin, độc lập với Android và Firebase SDK.
   - Viết Unit Tests trước cho 6 UseCases: `GenerateRecurringSessionsUseCaseTest` (kiểm tra bắt đầu tuần này/tuần sau, tự động bỏ qua các thứ đã qua, 2–6 slot 30 phút, chu kỳ 4–12 tuần), `CheckRecurringAvailabilityUseCaseTest` (quét sân ưu tiên và các sân thay thế), `ResolveRecurringConflictUseCaseTest` (đổi sân cùng `surfaceType` theo Phương án 1.A, bỏ buổi trùng lịch, chặn nếu còn dưới 4 buổi hợp lệ), `CalculateRecurringPriceUseCaseTest` (tính cọc 30% bằng `Long` VND), `CreateRecurringHoldTransactionUseCaseTest` (All-or-Nothing toàn chu kỳ), `ReleaseRecurringHoldUseCaseTest`.
2. **Kiểm soát tải và vượt qua giới hạn của Firestore**:
   - Áp dụng kỹ thuật chia batch `dates.chunked(30)` chạy song song qua Coroutines `async/awaitAll` để vượt qua giới hạn 30 phần tử của toán tử `whereIn` và đảm bảo thời gian phản hồi kiểm tra khả dụng dưới 2 giây.
   - Giao dịch Firestore Transaction tạo giữ chỗ chu kỳ tuân thủ triệt để nguyên tắc **All Reads Before All Writes**, **All-or-Nothing** và đảm bảo tổng số thao tác đọc/ghi luôn dưới trần **500 operations** cho toàn bộ chuỗi lên tới 12 tuần (khoảng ~290 ops).
3. **Giải phóng giữ chỗ chủ động & Hợp đồng liên module**:
   - Xây dựng `OnBackPressedCallback` tự động kích hoạt `ReleaseRecurringHoldUseCase` để giải phóng ngay các slot đang giữ nếu người chơi bấm Hủy hoặc Quay lại (Back) từ màn hình xác nhận, tránh tình trạng chiếm dụng sân ảo (ghost holding) suốt 5 phút.
   - Thống nhất hợp đồng chuyển tiếp sang Spec 050 bằng Bundle Safe Args `HoldToPaymentArgs` với cờ `isRecurring = true`, `totalSessions` ($\ge 4$), `subtotalAmount` và `depositAmount` (30%).
4. **Phân rã 44 Tasks chi tiết**:
   - **Phase 1 (Setup & Index)**: T001–T003 (Dependencies, Composite Index cho `slots`, Fixtures chu kỳ).
   - **Phase 2 (Domain Layer & TDD)**: T004–T017 (Domain Models, Repository Interface, Unit Tests Red, cài đặt 6 UseCases Green).
   - **Phase 3 (Data Layer & Infrastructure)**: T018–T023 (DTOs `RecurringGroupDto`, Mapper, Data Source batching `dates.chunked(30)`, Transaction All-or-Nothing an toàn dưới 500 ops, DI Module).
   - **Phase 4 (UI Layer & ViewModel)**: T024–T037 (XML Layouts Material 3, ListAdapters với DiffUtil, BottomSheet giải quyết xung đột, ViewModel StateFlow + Ticker 5 phút, Fragments).
   - **Phase 5 (Navigation, Integration & Edge Cases)**: T038–T040 (Safe Args `HoldToPaymentArgs`, cơ chế chủ động giải phóng giữ chỗ, Idempotency với `requestId`).
   - **Phase 6 (System Verification & Polish)**: T041–T044 (ViewModelTest, RecurringHoldTransactionTest trên Emulator, Espresso UI Test, rà soát Checklist).

### Tài liệu đã sinh

- `specs/041-weekly-recurring-booking/tasks.md`: 44 tasks chi tiết có checkbox `- [ ]`, ID `T001`–`T044`, gắn tag User Story (`[US1]`–`[US5]`), nhãn song song `[P]`, đường dẫn file chính xác và lệnh xác minh `Verify:`.

---

## Mục 18: Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 041

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/041-weekly-recurring-booking`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Đối soát chéo 3 bên giữa `spec.md`, `plan.md`, `tasks.md`, `contracts/ui-contracts.md` và `constitution.md`. Phát hiện các điểm lệch hoặc mơ hồ và tiến hành tinh chỉnh chuẩn hóa trước khi bước vào cài đặt mã nguồn (`/speckit-implement`) |
| Nhánh Git | `feature/041-weekly-recurring-booking` |

### Kết quả phân tích đối soát chéo (Cross-Artifact Analysis)

- **Tỷ lệ bao phủ (Coverage)**: **100%** (35/35 Functional Requirements và 5/5 Success Criteria đều có ít nhất một task tương ứng trong tổng số 44 tasks).
- **Tuân thủ Constitution**: Không có bất kỳ vi phạm nào (0 Critical, 0 High). Tầng domain thuần Kotlin độc lập, tiền tệ dùng `Long` VND, thuật toán đổi sân cùng `surfaceType` (Phương án 1.A) chặt chẽ, Firestore Transaction chia batch đọc-ghi an toàn dưới trần 500 operations.
- **Các điểm cần tinh chỉnh**: Phát hiện 2 điểm Low (A1, C1) về sự đồng nhất trạng thái entity và tên gọi hợp đồng Safe Args chuyển tiếp giữa các spec.

### Chi tiết các tinh chỉnh đã thực thi

1. **A1 (Low – Đồng bộ thuộc tính trạng thái vòng đời của nhóm đơn `RecurringBookingGroup`)**:
   - Cập nhật `specs/041-weekly-recurring-booking/spec.md`: Bổ sung thuộc tính `status` mang các giá trị (`HOLD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`) cho entity `RecurringBookingGroup`, phản ánh trạng thái vòng đời tổng thể của toàn bộ chu kỳ và khớp hoàn toàn với schema Firestore mục 4.5a trong `data-model.md`.
2. **C1 (Low – Chuẩn hóa tên lớp và thuộc tính Bundle Safe Args `HoldToPaymentArgs`)**:
   - Cập nhật `specs/041-weekly-recurring-booking/plan.md` (§5.2) và `contracts/ui-contracts.md` (§1.2): Chuẩn hóa tên lớp dữ liệu chuyển tiếp từ `RecurringToPaymentArgs` sang **`HoldToPaymentArgs`** (với `bookingId` là ID của nhóm `recurringGroupId`, cờ `isRecurring = true`, `slotIds: Array<String>`), đảm bảo tính nhất quán tuyệt đối với hợp đồng tiếp nhận của màn hình thanh toán tại Spec 050 (`specs/050-payment-voucher/contracts/ui-contracts.md`) và luồng đặt đơn lẻ của Spec 040.

### Trạng thái tài liệu sau tinh chỉnh

- `specs/041-weekly-recurring-booking/spec.md`: Đã bổ sung `status` cho `RecurringBookingGroup`.
- `specs/041-weekly-recurring-booking/plan.md`: Đã chuẩn hóa Safe Args sang `HoldToPaymentArgs`.
- `specs/041-weekly-recurring-booking/contracts/ui-contracts.md`: Đã chuẩn hóa Safe Args sang `HoldToPaymentArgs`.
- `specs/041-weekly-recurring-booking/tasks.md`: Đã bảo đảm tương thích 100% với các hợp đồng đã chuẩn hóa.
- Toàn bộ bộ tài liệu của Feature 041 đạt độ hoàn thiện 10/10, sẵn sàng bước vào giai đoạn thực thi mã nguồn (`/speckit-implement`).

---

## Mục 19: Lập danh sách công việc spec 050 (thanh toán và voucher giả lập) bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 05 – Thanh toán và khuyến mãi (`specs/050-payment-voucher`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Phân rã kế hoạch kỹ thuật spec 050 thành danh sách 42 task triển khai chi tiết (`tasks.md`), tuân thủ Clean Architecture, TDD, giao dịch Firestore Transaction 2 pha nguyên tử (All Reads Before All Writes), kiểm soát Idempotency với `requestId` UUID, voucher 2 nguồn (Phương án 3.B) và cam kết học tập phi thương mại (Constitution VI) |
| Nhánh Git | `feature/050-payment-voucher` |

### Bối cảnh và Quyết định cốt lõi khi lập Tasks

1. **Tuân thủ quy trình Clean Architecture & TDD**:
   - Tầng `domain` hoàn toàn thuần Kotlin, độc lập với Android SDK và Firebase SDK.
   - Viết Unit Tests trước (Red) cho 6 UseCases: `ValidateAndApplyVoucherUseCaseTest` (kiểm tra %, trần `maxDiscount`, minOrder, perUserLimit, collision 2 nguồn), `GetAvailableVouchersUseCaseTest` (đọc song song 2 nguồn), `CalculatePaymentBreakdownUseCaseTest` (cọc 30% làm tròn nghìn đồng an toàn, giảm 100% ra `FREE`), `TopUpMockWalletUseCaseTest` (nạp tiền ảo +100k, +500k, +1M), `CancelPaymentHoldUseCaseTest` (nhả slot khi hết giờ/hủy đơn), `ProcessMockPaymentUseCaseTest` (kiểm thử 3 phương thức, rollback khi thiếu tiền ví, hết hạn giữ chỗ hoặc mất lượt voucher phút chót).
2. **Giao dịch Firestore Transaction nguyên tử 2 pha (All Reads Before All Writes)**:
   - Pha Đọc (All Reads): Kiểm tra tính duy nhất (`walletTransactions/{requestId}`), kiểm tra hạn giữ chỗ (`holdExpiresAt > now`), đọc trạng thái các `slots` đang `HOLD`, kiểm tra voucher (`usedCount < usageLimit` & `userUsage.count < perUserLimit`), kiểm tra số dư ví (`walletBalance >= requiredAmount`).
   - Pha Ghi (All Writes): Trừ số dư ví, ghi log `walletTransactions/{requestId}`, tăng lượt voucher & ghi `redemptions/{bookingId}`, chuyển đơn sang `PENDING` (chờ duyệt 60 phút theo Phương án 2.A), chuyển toàn bộ `slots` sang `BOOKED` (xóa `holdExpiresAt`). Khống chế tổng số thao tác đọc-ghi an toàn dưới trần **500 operations** cho cả đơn lẻ 040 và đơn chu kỳ 041.
3. **Idempotency & Ticker đếm ngược kế thừa**:
   - Client sinh mã UUID `requestId` gắn kèm mỗi phiên thanh toán, vô hiệu hóa nút thanh toán ngay khi click đầu tiên để chống spam và chống trừ tiền kép khi mất mạng.
   - Ticker đếm ngược tính toán theo thời gian máy chủ thực tế: `remainingMillis = holdExpiresAtMillis - System.currentTimeMillis()`, tuyệt đối không tạo timer 5 phút mới. Chạm 00:00 tự động khóa nút và kích hoạt `CancelPaymentHoldUseCase`.
4. **Cam kết học tập phi thương mại & Thỏa thuận liên thành viên**:
   - Phương án 3.B: Đọc song song 2 nguồn voucher (`venues/{venueId}/vouchers` và `systemVouchers`) qua `async/awaitAll`.
   - Ràng buộc loại đơn: Ẩn hoàn toàn lựa chọn `AT_VENUE` khi đối số `isRecurring = true` (đơn chu kỳ từ 041), chỉ áp dụng cho đơn lẻ 040.
   - Constitution VI: Luồng thanh toán hoàn toàn GIẢ LẬP, hiển thị nhãn học tập nổi bật trên cả màn hình thanh toán và E-Receipt; cung cấp BottomSheet "Nạp ví giả lập".
5. **Phân rã 42 Tasks chi tiết**:
   - **Phase 1 (Setup & Test Infrastructure)**: T001–T002 (Composite Index, Test Fixtures).
   - **Phase 2 (Domain Layer & TDD)**: T003–T016 (Domain Models, Repository Interface, Unit Tests Red, cài đặt 6 UseCases Green).
   - **Phase 3 (Data Layer & Infrastructure)**: T017–T023 (DTOs, Mappers, `PaymentRepositoryImpl` với Transaction 2 pha, Hilt Module, Security Rules).
   - **Phase 4 (UI Layer & ViewModel)**: T024–T035 (XML Layouts Material 3, ListAdapter với DiffUtil, BottomSheets nạp ví/chọn voucher, ViewModel StateFlow + Ticker thực tế, Fragments).
   - **Phase 5 (Navigation, Integration & Edge Cases)**: T036–T038 (Safe Args `HoldToPaymentArgs`, `ReceiptToMyBookingsArgs`, cơ chế hủy giữ chỗ chủ động, Idempotency).
   - **Phase 6 (System Verification & Polish)**: T039–T042 (ViewModelTest, Transaction test trên Emulator, Espresso UI Test, rà soát Checklist).

### Tài liệu đã sinh

- `specs/050-payment-voucher/tasks.md`: 42 tasks chi tiết có checkbox `- [ ]`, ID `T001`–`T042`, gắn tag User Story (`[US1]`–`[US5]`), nhãn song song `[P]`, đường dẫn file chính xác và lệnh xác minh `Verify:`.

---

## Mục 20: Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 050

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 05 – Thanh toán và khuyến mãi (`specs/050-payment-voucher`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Đối soát chéo 3 bên giữa `spec.md`, `plan.md`, `tasks.md`, `data-model.md`, `contracts/ui-contracts.md` và `constitution.md`. Phát hiện các điểm lệch hoặc mơ hồ và tiến hành tinh chỉnh chuẩn hóa trước khi bước vào cài đặt mã nguồn (`/speckit-implement`) |
| Nhánh Git | `feature/050-payment-voucher` |

### Kết quả phân tích đối soát chéo (Cross-Artifact Analysis)

- **Tỷ lệ bao phủ (Coverage)**: **100%** (28/28 Functional Requirements và 7/7 Success Criteria đều có ít nhất một task tương ứng trong tổng số 42 tasks).
- **Tuân thủ Constitution**: Không có bất kỳ vi phạm nào (0 Critical, 0 High). Tầng domain thuần Kotlin độc lập, tiền tệ kiểu `Long` VND, kiểm tra giao dịch Firestore Transaction 2 pha an toàn dưới 500 ops, tuân thủ cam kết thanh toán giả lập phi thương mại (Constitution VI).
- **Các điểm cần tinh chỉnh**: Phát hiện 1 điểm Medium (C1) và 2 điểm Low (I1, I2) về tính nhất quán của mô hình dữ liệu và Safe Args điều hướng.

### Chi tiết các tinh chỉnh đã thực thi

1. **C1 (Medium – Chuẩn hóa tên Bundle Safe Args giữa Spec 050 và 060)**:
   - Cập nhật `specs/050-payment-voucher/contracts/ui-contracts.md` (§1.1) và `specs/060-my-bookings/contracts/ui-contracts.md` (§1.2): Thống nhất sử dụng chung một lớp Parcelable **`HoldToPaymentArgs`** cho toàn bộ các luồng chuyển tiếp tới màn hình thanh toán của Spec 050 (bao gồm đơn lẻ từ Spec 040, đơn chu kỳ từ Spec 041, và thao tác bấm "Thanh toán tiếp" từ Tab 1 "Chờ thanh toán" của Spec 060 theo FR-027). Xóa bỏ sự phân mảnh với tên gọi `ContinuePaymentArgs`.
2. **I1 (Low – Đồng nhất tên trường thời gian hết hạn voucher `validUntil`)**:
   - Cập nhật `specs/050-payment-voucher/spec.md` (FR-006 và mục Key Entities): Thay thế tên trường `validTo` bằng **`validUntil`**, đồng bộ 100% với schema Firestore, DTOs trong `data-model.md` (§1.1, §2) và các use case tầng domain.
3. **I2 (Low – Chuẩn hóa thuộc tính entity voucher `discountType` và `name`)**:
   - Cập nhật `specs/050-payment-voucher/spec.md`: Đồng bộ entity `VenueVoucher` và `SystemVoucher` sử dụng `discountType` mang các giá trị Enum (`PERCENT`, `FIXED_AMOUNT`), giá trị giảm `discountValue: Long`, và trường tên hiển thị `name: String`, khớp hoàn toàn với `tasks.md` (`T003`) và `data-model.md`.

### Trạng thái tài liệu sau tinh chỉnh

- `specs/050-payment-voucher/spec.md`: Đã cập nhật FR-006, `validUntil`, `discountType`, `name`.
- `specs/050-payment-voucher/contracts/ui-contracts.md`: Đã làm rõ `HoldToPaymentArgs` tiếp nhận từ cả 040, 041 và 060.
- `specs/060-my-bookings/contracts/ui-contracts.md`: Đã chuẩn hóa §1.2 sang `HoldToPaymentArgs`.
- `specs/050-payment-voucher/tasks.md`: Đã đảm bảo tính tương thích và bao phủ 100%.
- Toàn bộ bộ tài liệu của Feature 050 đạt độ hoàn thiện 10/10, sẵn sàng cho giai đoạn cài đặt mã nguồn (`/speckit-implement`).

---

## Mục 21: Lập danh sách công việc spec 060 (quản lý lịch đặt của tôi) bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 06 – Quản lý lịch đặt (`specs/060-my-bookings`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Phân rã kế hoạch kỹ thuật spec 060 thành danh sách 44 task triển khai chi tiết (`tasks.md`), tuân thủ Clean Architecture, TDD, giao dịch Firestore Transaction hủy đơn & nhả slot nguyên tử (All Reads Before All Writes), thuật toán hoàn tiền mốc 240 phút kẹp biên voucher an toàn, xuất trình mã QR check-in realtime/offline và bảo mật Security Rules với `request.time` |
| Nhánh Git | `060-my-bookings` |

### Bối cảnh và Quyết định cốt lõi khi lập Tasks

1. **Độc lập kiến trúc thuần Kotlin (Clean Architecture - Constitution I)**:
   - Tầng `domain` hoàn toàn thuần Kotlin, độc lập với Android SDK và Firebase SDK. Model `QrBitMatrix` được sinh bằng ZXing Core thuần JVM; tầng UI render `Bitmap` qua `QrBitmapRenderer.kt` với khung viền trắng `#FFFFFF`, kích thước tối thiểu 240dp để máy quét chủ sân nhận diện tốt kể cả khi thiết bị bật Dark Mode.
2. **Bảo mật chống gian lận giờ điện thoại & Transaction hủy đơn nguyên tử**:
   - `firestore.rules` sử dụng `request.time` (Server Timestamp của Google Cloud) đối chiếu với `resource.data.startAt` để kiểm tra mốc 240 phút khi hủy đơn, chặn lùi đồng hồ thiết bị. Giới hạn chỉ chủ đơn (`request.auth.uid == resource.data.userId`) được đọc `qrToken`.
   - Transaction hủy đơn tuân thủ All Reads Before All Writes: đọc đơn, đọc nhóm chu kỳ (nếu có), kiểm tra tính Idempotent qua document `users/{userId}/walletTransactions/refund_{bookingId}`, đọc số dư ví $\rightarrow$ ghi cập nhật đơn `CANCELLED`, cộng ví và **xóa trực tiếp toàn bộ các document `slots/{slotId}`** thuộc đơn để giải phóng sân ngay lập tức trên lưới của Spec 040.
3. **Thuật toán Hoàn tiền mốc 240 phút & Kẹp biên an toàn khi có Voucher**:
   - Đơn `PENDING` và đơn `CONFIRMED` hủy sớm ($\ge 240$ phút): Hoàn 100% tiền đã trả.
   - Đơn `CONFIRMED` hủy trễ ($< 240$ phút): ví giả lập phạt 30% tiền cọc chuẩn hóa, hoàn 70% còn lại (`penaltyAmount = minOf(booking.paidAmount, calculatedDepositPenalty)`, `refundAmount = maxOf(0L, booking.paidAmount - penaltyAmount)`, chống âm tiền); đặt cọc 30% mất 100% cọc; trả tại sân hoàn 0đ.
4. **Realtime Check-in, Hỗ trợ QR Offline & Thỏa thuận liên thành viên**:
   - Lắng nghe realtime `bookings/{bookingId}` qua Flow; khi chủ sân quét QR thành công (`checkedInAt != null`), UI chuyển sang "Đã check-in lúc HH:mm" và ẩn nút "Hủy lịch đặt".
   - Tận dụng Firestore Offline Persistence để người chơi vẫn mở được mã QR check-in từ cache khi mất mạng tại sân.
   - Phương án 2.A: Tab 2 (Chờ duyệt) đếm ngược 60 phút hạn chủ sân duyệt. Nếu quá 60 phút, hiển thị đơn `REJECTED` và đã hoàn 100% tiền ví giả lập.
   - Phương án 5.A: Khóa tuyệt đối quyền viết đánh giá ở Spec 070 đối với các đơn mang trạng thái `NO_SHOW`.
   - Hỗ trợ hủy riêng lẻ từng buổi trong chuỗi cố định `recurringGroupId` [041], tiền cọc/voucher phân bổ đều, phần lẻ dồn buổi cuối.
5. **Đồng bộ hóa Ticker Flow trên RecyclerView**:
   - `MyBookingsViewModel` phát luồng `serverNowMillis` Flow tập trung mỗi giây để `BookingListAdapter` đồng bộ hiển thị đếm ngược cho Tab 1 và Tab 2, triệt tiêu lỗi rò rỉ coroutine/bộ nhớ trên từng item.
6. **Phân rã 44 Tasks chi tiết**:
   - **Phase 1 (Setup & Test Infrastructure)**: T001–T002 (Composite Index, Test Fixtures).
   - **Phase 2 (Domain Layer & TDD)**: T003–T016 (Domain Models, Repository Interface, Unit Tests Red, cài đặt 6 UseCases Green).
   - **Phase 3 (Data Layer & Infrastructure)**: T017–T023 (DTOs, Mappers, `BookingManagementRepositoryImpl` với Transaction hủy đơn & xóa slot, Hilt Module, Security Rules với `request.time`).
   - **Phase 4 (UI Layer & ViewModel)**: T024–T037 (XML Layouts Material 3, ListAdapters với DiffUtil, `QrBitmapRenderer`, ViewModels, Fragments).
   - **Phase 5 (Navigation, Integration & Edge Cases)**: T038–T040 (Safe Args `HoldToPaymentArgs`, `ReceiptToMyBookingsArgs`, QR offline banner, Idempotency).
   - **Phase 6 (System Verification & Polish)**: T041–T044 (ViewModelTest với Turbine, Emulator Integration Test, Espresso UI Test, rà soát Checklist).

### Tài liệu đã sinh

- `specs/060-my-bookings/tasks.md`: 44 tasks chi tiết có checkbox `- [ ]`, ID `T001`–`T044`, gắn tag User Story (`[US1]`–`[US5]`), nhãn song song `[P]`, đường dẫn file chính xác và lệnh xác minh `Verify:`.

---

## Mục 22: Phân tích đối soát chất lượng bằng `/speckit-analyze` & tinh chỉnh spec 060

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 06 – Quản lý lịch đặt (`specs/060-my-bookings`) |
| Công cụ AI | Antigravity, lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Đối soát toàn diện tính nhất quán, độ phủ yêu cầu và tính khả thi kỹ thuật giữa `spec.md`, `plan.md`, `data-model.md`, `contracts/ui-contracts.md` và `tasks.md`; xử lý triệt để 1 điểm Medium và 2 điểm Low phát hiện từ báo cáo phân tích |
| Nhánh Git | `060-my-bookings` |

### Kết quả Phân tích Đối soát (`/speckit-analyze`)

- **Độ phủ yêu cầu (Requirements Coverage)**: Đạt **100% (36/36 yêu cầu)** gồm đầy đủ 29 Functional Requirements (FR-001 đến FR-029) và 7 Success Criteria (SC-001 đến SC-007) từ `spec.md`. Toàn bộ 4 User Stories chính và 1 User Story phụ đều được phân rã thành các task khả thi, có tiêu chí kiểm thử rõ ràng.
- **Rà soát rủi ro & lỗi kiến trúc**:
  - **Critical**: 0
  - **High**: 0
  - **Medium**: 1 (C1: Sự sai lệch giữa kiểu mảng `slotIds: List<String>` ở Firestore DTO và `items: List<BookingSlotItem>` ở Domain/UI)
  - **Low**: 2 (I1: Tên trường hạn chót duyệt đơn chưa đồng bộ giữa `pendingExpiresAt` và `approvalDeadline`; I2: Tên phương thức Repository giữa `getBookingDetail` và `observeBookingDetail` chưa đồng bộ)

### Chi tiết các điểm tinh chỉnh và khắc phục

1. **Khắc phục điểm Medium (C1 - Làm rõ ánh xạ `slotIds` sang `items: List<BookingSlotItem>`)**:
   - **Vấn đề**: Trong Firestore, đơn hàng lưu `slotIds: List<String>` dạng ID rút gọn (`{courtId}_{yyyyMMdd}_{HHmm}`) để tiết kiệm dung lượng và phục vụ thao tác xóa slot trong transaction. Tuy nhiên, màn hình chi tiết đơn ở tầng UI và Domain model `BookingDetail` lại yêu cầu danh sách các slot cụ thể (`items: List<BookingSlotItem>`) gồm giờ bắt đầu, kết thúc, tên sân và đơn giá.
   - **Giải pháp**:
     - Bổ sung định nghĩa `BookingSlotItem` chuẩn Clean Architecture thuần Kotlin vào Section 3 của `specs/060-my-bookings/data-model.md`.
     - Cập nhật entity `BookingDetail` trong `data-model.md` bổ sung trường `items: List<BookingSlotItem> = emptyList()` song song với `slotIds: List<String>`.
     - Bổ sung Section 5 trong `data-model.md` quy định rõ hợp đồng ánh xạ và giải mã (hydration) của `BookingManagementMapper`: bóc tách chuỗi `{courtId}_{yyyyMMdd}_{HHmm}` thành `startAt`, tính `endAt = startAt + 30 phút`, kế thừa `courtName`, `date` và phân bổ đơn giá `price`.

2. **Khắc phục điểm Low (I1 - Đồng bộ hóa tên trường `approvalDeadline`)**:
   - **Vấn đề**: Trong `data-model.md` dòng 44 và 125 từng sử dụng `pendingExpiresAt`, trong khi `spec.md`, `plan.md` và `tasks.md` đã chuẩn hóa sang `approvalDeadline: Timestamp?` theo Phương án 2.A (hạn chót chủ sân duyệt đơn 60 phút).
   - **Giải pháp**:
     - Thay thế `pendingExpiresAt` bằng `approvalDeadline: Timestamp?` tại Section 1.1 và Section 2 (`BookingDto`) trong `specs/060-my-bookings/data-model.md`.
     - Chuẩn hóa trường thời gian ở Domain/UI thành `approvalDeadlineMillis: Long?` tại Section 3 và Section 5.

3. **Khắc phục điểm Low (I2 - Chuẩn hóa tên hàm `observeBookingDetail` trong `tasks.md`)**:
   - **Vấn đề**: Hợp đồng `contracts/ui-contracts.md` định nghĩa hàm lắng nghe chi tiết đơn là `observeBookingDetail(bookingId: String): Flow<Result<BookingDetail>>` (thể hiện tính chất Flow realtime callbackFlow), trong khi `tasks.md` tại `T004`, `T010`, `T016`, `T019` dùng tên `getBookingDetail`.
   - **Giải pháp**:
     - Đồng bộ hóa toàn bộ tên phương thức trong `specs/060-my-bookings/tasks.md` thành `observeBookingDetail` tại `T004` (Repository interface), `T010` (Unit Test phát dữ liệu qua Flow), `T016` (UseCase kết nối Flow) và `T019` (cài đặt `callbackFlow` với `addSnapshotListener`).

### Tình trạng bộ Artifact sau tinh chỉnh

- Toàn bộ 5 file tài liệu kỹ thuật cốt lõi của tính năng 060 (`spec.md`, `plan.md`, `data-model.md`, `contracts/ui-contracts.md`, `tasks.md`) đạt độ nhất quán 100%, sẵn sàng chuyển sang bước triển khai mã nguồn (`/speckit-implement`).

---

## Mục 23: Triển khai mã nguồn Phase 1–3 spec 040 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-26 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (`specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, quy trình `/speckit-implement` (TDD Red-Green) |
| Mục đích | Triển khai toàn bộ mã nguồn Phase 1–3 (Task T001–T022) của spec 040 gồm: Data Infrastructure, Domain Layer (Domain Models, Repository Interface, UseCases thuần Kotlin theo quy chuẩn TDD Red-Green), Data Layer (DTOs, Mappers, Firestore Transaction giữ chỗ nguyên tử 5 phút All Reads Before All Writes, Repository bọc AppError, DI Module), và Firestore Security Rules kèm bộ test emulator. |
| Nhánh Git | `feature/040-booking-slot-grid` |

### Bối cảnh và phạm vi thực hiện

- **Phạm vi nghiêm ngặt**: Triển khai chính xác Phase 1–3 (T001–T022) theo `specs/040-booking-slot-grid/tasks.md`. Dừng lại trước Phase 4 (T023+ UI Layer) theo kế hoạch Tuần 43 (Week 4) của dự án. Demo ở mức Domain & Data layer.
- **Chính sách Git Zero-Touch**: Toàn bộ thao tác sửa đổi file do AI thực hiện trên working directory hiện tại; tuyệt đối không chạy bất kỳ lệnh can thiệp Git (`add`, `commit`, `push`, `branch`, `checkout`).
- **Tuân thủ Hiến pháp kỹ thuật (`constitution.md v1.2.0`)**:
  - *Constitution I*: Tầng `domain` 100% Kotlin thuần túy, tuyệt đối không phụ thuộc Android SDK hay Firebase SDK.
  - *Constitution II & III*: Toàn bộ tiền tệ dùng kiểu `Long` (VND, không dùng Float/Double, cấm âm); slot ID tất định `{courtId}_{yyyyMMdd}_{HHmm}`; giữ chỗ trong Firestore Transaction nguyên tử (*All Reads Before All Writes*), hạn 5 phút theo server timestamp (`request.time`), chống double-submit qua UUID `requestId`.
  - *Constitution IV*: Thực thi quy trình TDD Red $\rightarrow$ Green cho toàn bộ UseCases và Data Mappers/Repositories; kiểm thử Security Rules trên Firebase Emulator local.

### Chi tiết các công việc đã triển khai (T001 – T022)

#### Phase 1: Setup & Data Infrastructure (T001 – T003)
- **T001**: Rà soát xác nhận các thư viện cấu hình trong `app/build.gradle.kts`: Navigation Safe Args, Hilt DI, Coroutines/Flow, Material 3, ViewBinding, Firebase Firestore, Turbine, MockK.
- **T002**: Bổ sung Composite Index cho collection `slots` trong `firestore.indexes.json` gồm các trường `venueId` (ASC), `date` (ASC), `startTime` (ASC) phục vụ truy vấn tối ưu sparse storage theo cơ sở và ngày.
- **T003**: Xây dựng bộ test fixture `BookingSlotFixtures.kt` tại `app/src/test/java/.../feature/booking/fixture/` với bảng giá chuẩn (giờ thường 80k/h $\rightarrow$ 40k/slot, cao điểm 150k/h $\rightarrow$ 75k/slot) và các DTOs/Entities mẫu.

#### Phase 2: Domain Layer & Core Business Logic qua TDD (T004 – T015)
- **T004**: Khởi tạo đầy đủ các Domain Models thuần Kotlin tại `feature/booking/domain/model/`:
  - `Court.kt`: Đại diện sân thi đấu con.
  - `PriceRule.kt`: Khung giá theo ngày trong tuần (`daysOfWeek: List<Int>` chuẩn ISO 1=Thứ 2..7=Chủ nhật) và khung giờ.
  - `TimeSlot.kt`: Đại diện ca 30 phút (`slotId`, `courtId`, `date`, `startTime`, `endTime`, `price`, `status`, `blockReason`, `holdExpiresAtMillis`).
  - `SlotStatus.kt`: Enum 6 trạng thái (`AVAILABLE`, `SELECTED`, `BOOKED`, `HELD`, `BLOCKED`, `PAST`).
  - `SlotSelectionState.kt`: Trạng thái chọn slot (`selectedSlots`, `totalPrice`, `depositAmount`, `isValid`, `errorMessage`).
  - `HoldResult.kt`: Sealed class kết quả giữ chỗ nguyên tử (`Success`, `Conflict`).
- **T005**: Khởi tạo Interface `BookingRepository.kt` tại `feature/booking/domain/repository/` định nghĩa đầy đủ 6 phương thức thao tác dữ liệu cơ sở, khung giá, slot realtime và giữ/hủy slot.
- **T006 – T010 (TDD RED)**: Viết 5 bộ Unit Test suite (tổng cộng 22 test cases) tại `app/src/test/.../feature/booking/domain/` và xác nhận toàn bộ báo đỏ (`NotImplementedError`):
  - `ValidateSlotSelectionUseCaseTest.kt` (9 tests): Chọn slot đầu tiên, nối chuỗi liền kề, chọn cách quãng tự reset chuỗi, bỏ chọn slot đầu/cuối, cắt đuôi khi bỏ chọn slot ở giữa, chặn chọn slot bận/quá khứ, chặn vượt quá 8 slots 30p (= 4 giờ theo FR-026), kiểm tra điều kiện tối thiểu 2 slots (1 giờ).
  - `CalculateTotalPriceUseCaseTest.kt` (4 tests): Giá slot = nửa đơn giá giờ, tính tổng tiền khung thường, cao điểm, giao thoa khung giờ, tính cọc 30% làm tròn `((rawDeposit + 500) / 1000) * 1000`.
  - `GenerateDailySlotsUseCaseTest.kt` (4 tests): Sinh đủ 32 slots từ 06:00 đến 22:00, gộp slot bận từ Firestore, gắn nhãn `PAST` theo timezone `Asia/Ho_Chi_Minh`, gắn nhãn `BLOCKED` kèm lý do `DROP_IN`.
  - `HoldSlotsTransactionUseCaseTest.kt` (3 tests): Giữ slot thành công, phát hiện conflict All-or-Nothing, kiểm tra idempotent.
  - `ReleaseHoldUseCaseTest.kt` (2 tests): Hủy giữ slot thành công và xử lý ngoại lệ.
- **T011 – T015 (TDD GREEN)**: Lần lượt hoàn thiện logic cho 5 UseCases tại `feature/booking/domain/usecase/`:
  - `ValidateSlotSelectionUseCase.kt`
  - `CalculateTotalPriceUseCase.kt`
  - `GenerateDailySlotsUseCase.kt`
  - `HoldSlotsTransactionUseCase.kt`
  - `ReleaseHoldUseCase.kt`
  - Chạy lại kiểm thử: 22/22 domain tests chuyển sang xanh (100% PASS).

#### Phase 3: Data Layer & Infrastructure (T016 – T022)
- **T016**: Khởi tạo Data Transfer Objects tại `feature/booking/data/model/`: `CourtDto`, `PriceRuleDto`, `SlotDto` (có `@DocumentId`), `HoldBookingRequestDto`.
- **T017**: Cài đặt `BookingSlotMapper.kt` ánh xạ hai chiều an toàn DTO $\leftrightarrow$ Domain Model; viết `BookingSlotMapperTest.kt` (4 tests PASS).
- **T018 & T019**: Cài đặt `BookingRemoteDataSource` và `FirestoreBookingRemoteDataSource`:
  - Đọc `venues/{venueId}`, subcollection `courts` và `priceRules`.
  - Lắng nghe realtime `slots` bằng `callbackFlow` với `awaitClose { listener.remove() }` bảo vệ Quota Spark.
  - Firestore Transaction giữ chỗ 2 pha nghiêm ngặt:
    - *Pha 1 (All Reads)*: Đọc toàn bộ các document slot `{courtId}_{yyyyMMdd}_{HHmm}`. Nếu phát hiện slot đã có người giữ (chưa hết hạn) hoặc `BOOKED`/`BLOCKED` $\rightarrow$ Rollback và trả về `HoldResult.Conflict`.
    - *Pha 2 (All Writes)*: Ghi đồng thời toàn bộ documents với `status = "HELD"`, `holdBy = userId`, `holdExpiresAt = serverNow + 5 phút`.
- **T020**: Cài đặt `BookingRepositoryImpl.kt` triển khai `BookingRepository`, bọc dữ liệu trong `Result<T>` và ánh xạ ngoại lệ Firestore/mạng sang `AppError.Network`, `Timeout`, `RateLimited`, `Conflict`, `NotSignedIn`, `Unknown(cause)`. Thêm `AppError.Conflict` vào `core/network/AppError.kt` chuyên xử lý tranh chấp tài nguyên (Firestore transaction abort do nhiều client giữ cùng slot), tách biệt hoàn toàn khỏi `AppError.Locked` (vốn chỉ dành cho tài khoản bị khóa FR-014 để tránh đăng xuất nhầm người dùng). Viết `BookingRepositoryImplTest.kt` (10 tests PASS bao gồm case `DEADLINE_EXCEEDED` $\rightarrow$ `Timeout` và `ABORTED` $\rightarrow$ `Conflict`).
- **T021**: Cài đặt DI Module `BookingModule.kt` tại `feature/booking/di/` với `@Binds` kết nối `BookingRepository` sang `BookingRepositoryImpl`.
- **T022**: Cập nhật `firestore.rules` khai báo quy tắc bảo mật cho `venues`, `courts`, `priceRules`, và `slots` (ràng buộc `request.time` và `holdExpiresAt <= request.time + 5m`, quyền delete cho chủ slot hoặc admin). Xây dựng bộ test `firestore-tests/test/slots.test.ts` (7 tests gồm create, update, expiry guard, read và delete/release slot). Chạy toàn bộ 34 tests trên Firebase Emulator đều đạt 100% PASS.

### Kết quả Kiểm thử & Quality Gate (Sau tinh chỉnh, fix lint & bổ sung AppError.Conflict)

1. **Android Lint**:
   - Chạy `./gradlew lintDebug`: **0 ERRORS, 44 WARNINGS** (đã fix triệt để 2 warning `DefaultLocale` với `String.format(Locale.US, "%02d%02d", ...)` tại `FirestoreBookingRemoteDataSource.kt:142` và `GenerateDailySlotsUseCase.kt:92`; 44 warnings còn lại là các cảnh báo nền tiền khả dụng ở XML Auth).
2. **Android Unit Tests**:
   - Chạy `./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.booking.*"`: **36/36 PASS (0 failure, 0 error, 0 skipped)** gồm 22 Domain UseCases + 4 Mapper + 10 Repository.
3. **Build Debug APK**:
   - `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
4. **Firebase Emulator Security Rules**:
   - `vitest run` trong `firestore-tests`: **34/34 PASS** (3 test files: 24 users + 7 slots + 3 base).

### Điểm lưu ý cho nhóm thảo luận
- **Quy ước `daysOfWeek`**: Trong `PriceRule.kt`, quy ước ngày trong tuần được định nghĩa theo chuẩn ISO-8601 (1 = Thứ Hai, ..., 7 = Chủ Nhật) phù hợp với script seed dữ liệu Firestore (PR #41). Cần thống nhất chính thức giữa các thành viên nếu có sự khác biệt với lịch chuẩn của Java/Calendar (Chủ Nhật = 1).
- **Phân định `Conflict` vs `Locked`**: `AppError.Conflict` đã được thêm chính thức vào core để đại diện cho tranh chấp tài nguyên (ví dụ transaction abort), bảo đảm không kích hoạt `signOutLocked` của `SessionManager`. Toàn bộ các khối `when` exhaustive trên `AppError` trong project (`CompleteSignupUseCase`, `AuthErrorMapper`) đã được cập nhật đồng bộ để đảm bảo an toàn biên dịch.

---

## Mục 24: Triển khai giao diện Phase 4 (UI & ViewModel) spec 040 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-26 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity, kỹ thuật Speckit Implement |
| Mục đích | Triển khai trọn vẹn Phase 4 (T023 – T034) bao gồm Layouts XML Material 3, RecyclerView Adapters, StateFlow ViewModel, và Fragment tương tác đặt sân |
| Nhánh Git | `feature/040-booking-slot-grid` |

### Prompt đã dùng

```text
LÀM PHASE 4 (UI) BẰNG SPECKIT-IMPLEMENT — spec 040, nhánh feature/040-booking-slot-grid (repo ThanhDu14/Mobile-Project-Booking), nối tiếp commit 42e7dd2.

BỐI CẢNH
- Nhiệm vụ: triển khai Phase 4 (UI) cho spec 040 (Lưới khung giờ đặt sân và giữ chỗ), CHỈ T023–T034 trong specs/040-booking-slot-grid/tasks.md (Phase 4: UI Layer & ViewModels — Phase 4A–4D theo kế hoạch tuần 4).
- Phase 1–3 (T001–T022) đã xong trên nhánh (domain + data + test + rules + index, 2 commit); T001–T022 đã tick trong tasks.md, không làm lại.
- Tài liệu nguồn, đọc trước khi code theo thứ tự: tasks.md, spec.md, plan.md, data-model.md, contracts/ trong specs/040-booking-slot-grid/. tasks.md là danh sách việc chính thức.
- Git: Hưng là người duy nhất quản lý và thao tác trên git. Agent KHÔNG chạy bất kỳ lệnh git nào.
- Hạn: code phải xong sớm để Hưng review và tự commit trước 18:00 thứ Sáu 30/10/2026, demo 20:00 cùng ngày.
```

### Nguyên tắc và chuẩn mực tuân thủ
- **Clean Architecture & Android Architecture Components**:
  - `BookingSlotGridFragment` chỉ tương tác với `BookingSlotGridViewModel`, tuyệt đối không gọi trực tiếp Firebase SDK hay Repository.
  - Sử dụng `StateFlow<BookingSlotUiState>` cho UI state liên tục và `SharedFlow<BookingSlotUiEffect>` cho one-shot event (Navigation, Conflict dialog/snackbar).
  - Thu thập state an toàn vòng đời với `viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`.
- **Material 3 Token Design System**:
  - Giao diện tuân thủ chuẩn DayNight với color tokens từ `res/values/colors.xml` và `colors_booking.xml`.
  - Hỗ trợ đầy đủ 6 trạng thái màu sắc slot: Xanh lá (`AVAILABLE`), Xanh dương (`SELECTED`), Vàng cam (`HELD`), Đỏ (`BOOKED`), Xám có biểu tượng khóa (`BLOCKED` kèm tag `Giao lưu Drop-in`), Mờ gạch ngang (`PAST`).
- **Nguyên tắc YAGNI (Constitution VII)**:
  - Tránh thêm tùy tiện các thư viện Gradle bên ngoài khi không thật sự cần thiết.
  - `HoldToPaymentArgs` tự cài đặt chuẩn `android.os.Parcelable` thuần thay vì bắt buộc thêm plugin `kotlin-parcelize`.
  - Thay thế `SwipeRefreshLayout` bằng `NestedScrollView` tiêu chuẩn để tránh thêm dependency `androidx.swiperefreshlayout`.

### Chi tiết các công việc đã triển khai (T023 – T034)

#### 4A. Layout XML & Resources (T023 – T028)
- **T028**: Khởi tạo hệ thống màu sắc `res/values/colors_booking.xml` và chuỗi giao diện đa ngôn ngữ `res/values/strings_booking.xml`:
  - Mã màu trực quan: `booking_slot_available_bg` (#E8F5E9), `booking_slot_selected_bg` (#E3F2FD), `booking_slot_held_bg` (#FFF3E0), `booking_slot_booked_bg` (#FFEBEE), `booking_slot_blocked_bg` (#F5F5F5), `booking_slot_past_bg` (#FAFAFA).
  - Chuỗi tóm tắt, legend trạng thái, thông báo lỗi xung đột realtime, xác nhận đổi ngày/sân, v.v.
- **T023**: Tạo `res/layout/item_date_selector.xml`: Thẻ chọn ngày cuộn ngang hiển thị thứ viết tắt (T2, T3.. CN), số ngày, chấm tròn chỉ thị "Hôm nay", và hiệu ứng đổi màu stroke/background khi active.
- **T024**: Tạo `res/layout/item_court_tab.xml`: Chip chọn sân con hiển thị tên sân (Sân 1, Sân 2...) và huy hiệu loại mặt sân (Gỗ, Thảm, PU) với trạng thái active/inactive rõ ràng.
- **T025**: Tạo `res/layout/item_slot_grid.xml`: Thẻ hiển thị ca 30 phút với thời gian bắt đầu - kết thúc, đơn giá hiển thị (VND rút gọn), nhãn trạng thái trực quan, icon khóa cho slot bị chặn và tag `Giao lưu Drop-in`.
- **T026**: Tạo `res/layout/view_booking_bottom_summary.xml`: Thanh tóm tắt cố định đáy màn hình hiển thị số khung giờ/tổng giờ đã chọn, tạm tính tổng tiền, tiền cọc 30%, và nút "Tiếp tục" (`MaterialButton`) tự động disable khi chọn chưa đủ 2 slot hoặc vi phạm quy tắc.
- **T027**: Tạo `res/layout/fragment_booking_slot_grid.xml`: Kết hợp thanh tiêu đề/back, danh sách ngày ngang, tab sân con, bảng chú thích trạng thái (Legend), lưới ca 30 phút (`GridLayoutManager`), trạng thái rỗng khi sân đóng cửa kèm nút Thử lại, và thanh tóm tắt đáy.
- **Bổ sung Asset**: Tạo vector drawable `res/drawable/ic_lock_16.xml` hiển thị trên các ca bận hoặc bị chủ sân chặn.

#### 4B. Adapters & Custom UI (T029 – T031)
- **T029**: Cài đặt `DateSelectorAdapter.kt` kế thừa `ListAdapter` + `DiffUtil.ItemCallback`, hỗ trợ hiển thị 30 ngày từ hôm nay, phát sự kiện khi chọn ngày mới.
- **T030**: Cài đặt `CourtTabAdapter.kt` kế thừa `ListAdapter`, hiển thị danh sách sân con, xử lý click chuyển sân và highlight tab đang chọn.
- **T031**: Cài đặt `SlotGridAdapter.kt` kế thừa `ListAdapter`, hiển thị lưới slot 30 phút, điều phối màu sắc nền/chữ theo 6 trạng thái, chặn click đối với các ca `BOOKED`, `BLOCKED`, `PAST`, và kích hoạt callback chọn/bỏ chọn với ca khả dụng.

#### 4C. ViewModel & State Machine (T032 – T033)
- **T032**: Định nghĩa `BookingSlotUiState.kt` và `BookingSlotUiEffect.kt` tại `feature/booking/ui/`:
  - `BookingSlotUiState`: Lưu trữ toàn bộ dữ liệu phản ứng của màn hình (`isLoading`, `venueName`, `operatingHours`, `courts`, `selectedCourtId`, `dates`, `selectedDate`, `slots`, `selectedSlotIds`, `totalAmount`, `depositAmount`, `canProceed`, `isHolding`, `errorMessage`).
  - `BookingSlotUiEffect`: One-shot events gồm `ShowConflictWarning(message)`, `NavigateToBookingSummary(args)`, `NavigateToLogin`, `ShowToast(message)`, `ShowErrorSnackbar(message)`.
  - Cài đặt `HoldToPaymentArgs.kt` làm cầu nối Parcelable an toàn gửi thông tin giữ chỗ sang màn hình thanh toán/tóm tắt (Phase 5).
- **T033**: Cài đặt `BookingSlotGridViewModel.kt`:
  - Khởi tạo nhận `venueId` từ `SavedStateHandle`.
  - Tải danh sách sân con, bảng giá và giờ hoạt động của cơ sở qua repository.
  - Tự động tạo danh sách 30 ngày tiếp theo bắt đầu từ ngày hiện tại.
  - Lắng nghe realtime `getBusySlotsRealtime` từ Firestore theo sân và ngày đang chọn.
  - Sinh đầy đủ danh sách slot trong ngày thông qua `GenerateDailySlotsUseCase`.
  - **Xử lý xung đột Realtime**: Nếu slot người dùng đang chọn bị người khác giữ (`HELD`/`BOOKED`), tự động loại bỏ khỏi danh sách đã chọn, tính lại tiền và phát ra `UiEffect.ShowConflictWarning`.
  - Tích hợp `ValidateSlotSelectionUseCase` khi người dùng click vào ô giờ.
  - Thực thi giữ chỗ 5 phút nguyên tử qua `HoldSlotsTransactionUseCase`. Khi thành công, phát ra `UiEffect.NavigateToBookingSummary`.

#### 4D. Fragment & User Interactions (T034)
- **T034**: Cài đặt `BookingSlotGridFragment.kt`:
  - Tích hợp ViewBinding và cấu hình `GridLayoutManager(context, 3)` cho lưới slot.
  - Thu thập `UiState` và `UiEffect` an toàn trong `viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`.
  - Hiển thị hộp thoại cảnh báo: *"Bạn có chắc muốn đổi ngày/sân? Lựa chọn khung giờ hiện tại sẽ bị xóa"* khi người dùng bấm chuyển ngày hoặc sân con trong lúc đang có slot được chọn.
  - Xử lý hiệu ứng Snackbar/Toast khi xảy ra xung đột hoặc lỗi mạng, tự động kích hoạt reload dữ liệu.

### Kết quả Kiểm thử & Quality Gate

1. **Android Lint**:
   - Chạy `./gradlew lintDebug`: **0 ERRORS, BUILD SUCCESSFUL**.
2. **Android Unit Tests**:
   - Chạy `./gradlew testDebugUnitTest`: **37/37 PASS (100%)**.
3. **Build Debug APK**:
   - Chạy `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
4. **Firebase Emulator Tests**:
   - Chạy `npm test` trong `firestore-tests`: **34/34 PASS** (100% security rules verification).

### Demo & Cách mở màn hình
Đã bổ sung đích đến `bookingSlotGridFragment` và action mẫu `action_playerHome_to_bookingSlotGrid` trong `app/src/main/res/navigation/nav_player.xml`. Khi mở app với vai trò Người chơi, màn hình Lưới khung giờ đặt sân có thể được kích hoạt trực tiếp từ `PlayerHomePlaceholderFragment` hoặc truyền tham số `venueId` qua Safe Args.

### Kiểm thử thực tế trên thiết bị thật & Tinh chỉnh trải nghiệm người dùng (UX)

#### 1. Quá trình kiểm thử thực tế (Hưng thực hiện trên thiết bị Samsung thật):
- Cấu hình bypass điều hướng và mock data tạm thời để nạp app trực tiếp vào thiết bị thật qua Android Studio (`Shift + F10`) nhằm kiểm tra UI spec 040 độc lập.
- **Kết quả kiểm tra trực quan & thao tác**:
  - Giao diện thanh chọn ngày ngang 30 ngày, tabs sân con, lưới slot 30 phút và thanh tóm tắt hiển thị mượt mà, đúng chuẩn Material 3.
  - Thao tác chọn / bỏ chọn slot trống (`AVAILABLE`) hoạt động chính xác, thanh đáy cập nhật tổng tiền và tiền cọc tức thì.
- **Vấn đề phát hiện khi test tay**:
  - Khi chạm vào các ô không khả dụng (`BOOKED`, `HELD`, `BLOCKED`, `PAST`), hệ thống chặn chọn thành công nhưng hoàn toàn không có phản hồi nào, khiến người dùng không biết vì sao thao tác không có tác dụng.
  - Độ mờ (`alpha`) của các ô không khả dụng chưa đủ phân biệt rõ ràng so với các ô trống trước khi người dùng chạm vào.

#### 2. Yêu cầu điều chỉnh từ Hưng & Giải pháp AI thực hiện:
- **Nguyên tắc**: Giữ nguyên tuyệt đối quy tắc chặn và logic chọn slot (không đổi selection, không đổi thanh tóm tắt).
- **Phân biệt trực quan**:
  - Cập nhật `SlotGridAdapter.kt`: Thiết lập mức độ mờ (`alpha`) tương ứng theo từng trạng thái: `AVAILABLE`/`SELECTED` (1.0f), `HELD` (0.80f), `BOOKED` (0.55f), `BLOCKED` (0.50f kèm icon ổ khóa), `PAST` (0.35f).
- **Phản hồi tức thì qua UiEffect**:
  - Bổ sung chuỗi thông báo chuẩn trong `strings_booking.xml`.
  - Trong `BookingSlotGridViewModel.kt`: Bắt các trạng thái không trống trong `onSlotClicked()` và phát ra `BookingSlotUiEffect.ShowErrorSnackbar` với thông điệp tương ứng:
    - `BOOKED` $\rightarrow$ *"Slot này đã được đặt"*
    - `HELD` $\rightarrow$ *"Slot này đang được giữ"*
    - `BLOCKED` $\rightarrow$ *"Slot này không khả dụng"*
    - `PAST` $\rightarrow$ *"Slot này đã qua giờ"*
  - Trong `BookingSlotGridFragment.kt`: Thiết lập `setAnchorView(binding.bottomSummary.root)` để Snackbar hiển thị nổi phía trên thanh tóm tắt đáy màn hình, không che khuất giá tiền hay nút bấm.

#### 3. Nghiệm thu & Hoàn nguyên mã nguồn sạch (Revert):
- Hưng kiểm tra lại trên thiết bị thật, xác nhận các ô đã mờ rõ rệt và bấm vào hiện đúng thông điệp Snackbar tương ứng.
- Do các tính năng liên quan đến giữ chỗ thực tế cần qua bước đăng nhập (thuộc phạm vi tích hợp Phase 5 & Spec 010), Hưng quyết định sẽ test tiếp ở Phase sau và yêu cầu hoàn nguyên lại các file bypass tạm thời để giữ mã nguồn Phase 4 ở trạng thái chuẩn sản xuất.
- AI đã hoàn nguyên (revert) chính xác 3 file:
  1. `app/src/main/res/navigation/nav_main.xml`: Khôi phục `startDestination` về `@id/launcherFragment` và gỡ bỏ fragment đích tạm thời.
  2. `app/src/main/java/com/example/booking_app/MainActivity.kt`: Khôi phục khối `viewModel.sessionState.collect { route(it) }`.
  3. `app/src/main/java/com/example/booking_app/feature/booking/ui/BookingSlotGridViewModel.kt`: Gỡ bỏ toàn bộ mock data fallback trong `loadInitialData()` và `observeRealtimeSlots()`, giữ lại logic phản hồi Snackbar khi chạm slot không trống.
- **Quality Gate sau khi revert**: Full pass 100% (`lintDebug` 0 errors, `testDebugUnitTest` 37/37 pass, `assembleDebug` thành công).

---

## Mục 25: Thống nhất 3 chiều (Spec ↔ Tasks ↔ Sản phẩm) theo Quyết định Q1–Q13 & Mockup chuẩn Spec 040

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-27 |
| Người thực hiện | Lê Quốc Hưng |
| Nhóm tính năng liên quan | 04 – Đặt sân (spec `specs/040-booking-slot-grid`) |
| Công cụ AI | Antigravity CLI |
| Mục đích | Thống nhất tuyệt đối 3 chiều giữa Tài liệu (spec.md, tasks.md, plan.md, ui-contracts.md) ↔ Thiết kế (Mockup 1 & 2) ↔ Mã nguồn (Domain, Data, UI, ViewModel, Tests) theo 13 quyết định chốt Q1–Q13 |
| Nhánh Git | `feature/040-booking-slot-grid` (nối tiếp commit `ec41041`) |

### Bối cảnh & 13 Quyết định đã chốt (Q1–Q13)

Sau đợt kiểm thử trên thiết bị thật và đối chiếu kỹ lưỡng giữa 2 ảnh wireframe mockup (`specs/040-booking-slot-grid/ui/`) với tài liệu spec và mã nguồn hiện tại, Hưng đã chốt 13 quyết định cốt lõi để chuẩn hóa toàn bộ tính năng đặt sân theo giờ:

1. **Q1 (Đa sân M1)**: Cho phép một đơn chứa nhiều chuỗi slot trên nhiều sân con khác nhau (cùng ngày, cùng cơ sở); mỗi sân là một chuỗi liên tục độc lập; tổng số slot tối đa là 8 slot (4 tiếng).
2. **Q2 (Tối thiểu 2 slot M2)**: Giữ nguyên `MIN_SLOTS = 2` (1 tiếng) để bảo đảm hiệu quả vận hành sân. Nút "Giữ chỗ" chỉ kích hoạt khi có đủ ít nhất 2 slot hợp lệ liên tiếp.
3. **Q3 (Lưới ma trận M3)**: Chuyển hoàn toàn sang bố cục ma trận 2 chiều (Cột = Sân con cuộn ngang tối đa 10 sân; Hàng = Mốc giờ 30 phút cuộn dọc). Bỏ `CourtTabAdapter` và dialog đổi sân con; giữ dialog cảnh báo đổi ngày (US1-AS8).
4. **Q4 (Màn hình xác nhận M4 - FR-018)**: Xây dựng `BookingConfirmFragment` thuộc phạm vi Spec 040: hiển thị tên + địa chỉ cơ sở thật, danh sách slot gom theo từng sân, tổng tiền, đồng hồ đếm ngược 5 phút theo `holdExpiresAtMillis` thật từ Firestore. Nút "Hủy / Quay lại" nhả slot ngay lập tức; nút "Thanh toán" chuyển tiếp dữ liệu `HoldToPaymentArgs` sang Spec 050.
5. **Q5 (Định giá theo dữ liệu M5)**: Toàn bộ đơn giá, banner giờ cao điểm và tổng tiền tính động từ `priceRules` của cơ sở, không hardcode bất kỳ con số nào.
6. **Q6 (Đồng bộ nhãn nút M6)**: Đổi tên nút hành động chính ở đáy màn hình từ *"Tiếp tục"* thành *"Giữ chỗ"* trên toàn bộ tài liệu và chuỗi giao diện.
7. **Q7 (Chuẩn thị giác Mockup M13/M14/M15)**: Header chuẩn: *"Chọn khung giờ"* + `{tên cơ sở} · {quận}`; dải ngày ngang có nhãn *"Hôm nay"*, *"Mai"*, *"T2–CN"* + chấm highlight hôm nay; ô slot ma trận chỉ ghi giờ bắt đầu (vd: `17:00`), kích thước 80dp × 44dp; 6 trạng thái màu sắc chuẩn Semantic Tokens SmashNow (`AVAILABLE`, `SELECTED`, `HELD`, `BOOKED`, `BLOCKED` kèm icon/badge, `PAST` với alpha 0.35f); bảng chú thích (Legend) đầy đủ 6 mục.
8. **Q8 (Xử lý xung đột giữ chỗ M7)**: Khi giữ chỗ thất bại vì có slot bị tranh chấp (`HoldResult.Conflict`), chỉ loại bỏ các slot bị mất, thông báo rõ tên slot bị tranh chấp, giữ nguyên các slot còn trống đang chọn (không clear toàn bộ lựa chọn).
9. **Q9 (Bấm slot không liền kề M8)**: Khi bấm slot không liền kề trên cùng một sân, reset chuỗi cũ trên sân đó, lấy slot mới làm mốc và phát Snackbar: *"Vui lòng chọn các khung giờ liên tiếp trên cùng sân"*.
10. **Q10 (Bảo toàn lựa chọn khi đăng nhập M9 - US4)**: Khi người dùng chưa đăng nhập bấm "Giữ chỗ", lưu danh sách `slotIds` và ngày chọn vào `SavedStateHandle` trước khi sang màn hình đăng nhập; khi đăng nhập xong quay lại, tự động khôi phục các slot còn trống và cảnh báo nếu có slot đã bị người khác lấy. Nút Hủy/Bỏ qua ở đăng nhập quay lại lưới giữ nguyên lựa chọn.
11. **Q11 (Lọc giá & kiểm soát thời gian M10)**: Lọc `priceRule` theo `daysOfWeek` (chuẩn ISO 1..7) và khung giờ. Slot không khớp rule nào sẽ bị khóa (`BLOCKED`, `NO_PRICE_RULE`, tránh giá 0đ). Chặn giữ slot quá khứ theo giờ máy chủ Firestore (`Timestamp.now()`).
12. **Q12 (Dữ liệu cơ sở thật M11/M12)**: Bổ sung API `getVenueInfo` đọc tên, địa chỉ, quận từ Firestore; gỡ bỏ hoàn toàn fallback `demo-venue-01` im lặng; sử dụng mã cơ sở seed thật `demo-truong-chinh-909`.
13. **Q13 (Nút Đặt cố định theo tuần M16)**: Bổ sung nút pill *"Đặt cố định theo tuần"* ngay dưới dải ngày để điều hướng sang Feature 041 theo hợp đồng điều hướng liên spec.

### Chi tiết các công việc đã thực hiện

#### 1. Tài liệu (Spec Kit & Contracts)
- `specs/040-booking-slot-grid/spec.md`: Cập nhật ghi chú đa sân tại FR-005, FR-006; chuẩn hóa điều kiện kích hoạt nút tại FR-010, US1-AS4, US1-AS7; đồng bộ nhãn nút "Giữ chỗ" (Input, US2, US4, US5, FR-010, FR-011, FR-013).
- `specs/040-booking-slot-grid/plan.md`: Cập nhật kiến trúc ma trận thay thế tab sân, bổ sung `BookingConfirmFragment` và `SlotMatrixRowAdapter`.
- `specs/040-booking-slot-grid/contracts/ui-contracts.md`: Cập nhật §1.2 luồng điều hướng lưới $\rightarrow$ xác nhận 040 $\rightarrow$ thanh toán 050; cập nhật §4 bảng token màu sắc và kích thước ô slot chuẩn wireframe.
- `specs/040-booking-slot-grid/tasks.md`: Cập nhật checklist T006, T025, T026, T027, T030, T031, T032, T033, T034, T035 theo ma trận đa sân và màn hình xác nhận.

#### 2. Domain & Data Architecture
- Tạo `VenueInfo.kt` chứa thông tin cơ sở (tên, địa chỉ, quận, giờ mở/đóng cửa).
- Thêm `getVenueInfo(venueId)` vào `BookingRepository`, `BookingRemoteDataSource`, `FirestoreBookingRemoteDataSource`, `BookingRepositoryImpl`.
- Cập nhật `FirestoreBookingRemoteDataSource.kt`:
  - Đọc `venues/{id}` và map chuẩn 22 quận/huyện TP.HCM.
  - Cập nhật transaction `holdSlotsAtomic`: Đánh giá mốc thời gian slot với `Timestamp.now()` server time để từ chối slot quá khứ; trích xuất `courtId` từ từng slot ID cho phép giữ chỗ đa sân nguyên tử trong 1 transaction duy nhất.
- Cập nhật `GenerateDailySlotsUseCase.kt`: Lọc giá theo ngày trong tuần chuẩn ISO (`1 = T2 .. 7 = CN`), gán `BLOCKED` với `NO_PRICE_RULE` nếu không tìm thấy bảng giá tương ứng.
- Cập nhật `ValidateSlotSelectionUseCase.kt`: Hỗ trợ đa sân độc lập, duy trì chuỗi liên tục trên từng sân, tổng tối đa 8 slot, phát lỗi rõ ràng khi bấm slot không liền kề.
- Cập nhật `HoldToPaymentArgs.kt`: Bổ sung `venueAddress` và `districtName` hỗ trợ hiển thị màn hình xác nhận và thanh toán.

#### 3. Giao diện người dùng (UI & Adapters)
- Gỡ bỏ `CourtTabAdapter.kt` và `item_court_tab.xml`.
- Tạo `item_slot_matrix_cell.xml` (ô 80dp × 44dp, chỉ hiển thị giờ bắt đầu, bo góc 8dp, hiệu ứng icon và màu nền theo 6 trạng thái).
- Tạo `item_slot_matrix_row.xml` và `item_court_column_header.xml`.
- Cài đặt `SlotMatrixRowAdapter.kt`: Quản lý hiển thị các hàng ma trận giờ 30 phút, đồng bộ cuộn ngang với danh sách sân con.
- Cập nhật `DateSelectorAdapter.kt`: Hiển thị nhãn *"Hôm nay"*, *"Mai"*, *"T2–CN"* kèm chấm highlight hôm nay theo Q7.
- Cập nhật `fragment_booking_slot_grid.xml`: Bố cục ma trận cuộn ngang, banner giờ cao điểm động, banner kín sân kèm nút xem ngày mai, nút pill *"Đặt cố định theo tuần"*, bảng chú thích 6 mục.
- Xây dựng `fragment_booking_confirm.xml` và `BookingConfirmFragment.kt` (Q4):
  - Đồng hồ đếm ngược 5 phút từ `holdExpiresAtMillis`, tự động hiển thị hộp thoại timeout và hủy giữ chỗ khi hết giờ.
  - Hiển thị thông tin cơ sở thật và danh sách slot gom theo từng sân.
  - Nút *"Hủy / Quay lại"* nhả slot qua `ReleaseHoldUseCase` và quay về lưới.
  - Nút *"Thanh toán"* chuẩn bị chuyển tiếp dữ liệu sang Spec 050.
- Cập nhật `BookingSlotGridViewModel.kt` và `BookingSlotGridFragment.kt`: Tích hợp toàn diện ma trận đa sân, khôi phục pending selection sau đăng nhập, xử lý xung đột hold chỉ loại bỏ slot mất.
- Cập nhật `nav_player.xml`: Đăng ký `bookingConfirmFragment`, thêm action điều hướng, thiết lập venue mặc định `demo-truong-chinh-909`.

#### 4. Kết quả Kiểm thử & Quality Gates (Full Pass)
1. **Unit Tests (`./gradlew testDebugUnitTest`)**:
   - **218/218 tests PASS (100%)**.
   - Bổ sung các ca kiểm thử chuyên biệt: chọn đa sân độc lập, bấm không liền kề reset từng sân, lọc giá theo ISO ngày, không khớp giá bị chặn, giữ chỗ xung đột giữ lại slot trống, và luồng ViewModel lưu trạng thái đăng nhập.
2. **Build Debug APK (`./gradlew assembleDebug`)**:
   - **BUILD SUCCESSFUL**.
3. **Android Lint (`./gradlew lintDebug`)**:
   - **0 ERRORS, BUILD SUCCESSFUL**.
4. **Firestore Security & Emulator Tests (`npm test` trong `firestore-tests`)**:
   - **34/34 tests PASS (100%)**.

---

## Mục 26: Tinh chỉnh giao diện Spec 040 chuẩn Mockup & Sửa lỗi hiển thị UI

| Trường | Nội dung |
|---|---|
| **Người thực hiện** | Thành viên số 2 (Lê Quốc Hưng - B) |
| **Thời điểm** | 27/10/2026 |
| **Công cụ AI** | Google Antigravity Assistant |
| **Mục tiêu** | Tinh chỉnh giao diện 2 màn hình của Spec 040 (`SlotGrid` & `BookingConfirm`) sát chuẩn 2 ảnh mockup thiết kế trong `specs/040-booking-slot-grid/ui/`; khắc phục 2 lỗi hiển thị trực quan (badge Giao hữu bị tràn/cắt chữ và chuỗi `30%%`); đảm bảo hoạt động hoàn hảo trên cả Light Theme và Dark Theme. |

### Các hạng mục tinh chỉnh và sửa lỗi đã hoàn thành

#### Màn hình 1 — Chọn khung giờ (SlotGrid)
1. **1a. Màu đang chọn**: Đổi token màu slot đang chọn và chip ngày đang chọn thành `#1B5E45` + chữ trắng + dấu checkmark ✓ (`ic_check_20`) cho cả Light & Dark theme (`colors_booking.xml`), loại bỏ hoàn toàn hiện tượng xanh mint lệch mockup.
2. **1b. Header sân**: Loại bỏ dạng pill tối màu có nền sunken; chuyển về dạng văn bản phẳng chuẩn mockup: tên sân in đậm (`tvCourtName`, bold 13sp), loại mặt sân chữ nhạt bên dưới (`tvCourtSurface`, 11sp muted) lấy trực tiếp từ dữ liệu sân thật (`item_court_column_header.xml`).
3. **1c. Banner giờ cao điểm**: Nền sáng nhạt (`@color/sn_surface_sunken`), biểu tượng tag (`ic_tag_16`), giá tiền in đậm (`<b>$priceFormatted đ/giờ</b>`) từ `priceRules` động của cơ sở, phân cách bằng dấu chấm giữa `·`.
4. **1d. Bảng chú thích (Legend)**: Bố trí lại thành 2 hàng cân đối (3 cột × 2 hàng), loại bỏ thanh cuộn ngang `HorizontalScrollView` để không một mục nào bị cắt lề phải trên mọi kích thước màn hình. Mục "Trống" dùng hộp bo góc 3dp có viền stroke (`bg_legend_box.xml`).
5. **1e. Sửa lỗi Badge "Giao hữu" (Thay thế mục 1e cũ)**:
   - Trong các ô `BLOCKED` dạng giao lưu/giao hữu (`DROP_IN`), tổ chức bố cục 2 dòng căn giữa: Dòng 1 gồm icon khóa 12dp + giờ bắt đầu; Dòng 2 là chip "Giao hữu" nhỏ nằm gọn hoàn toàn bên trong ô slot (nền amber nhạt `#FCE8BF`, chữ tối `#3D2A00`, bo góc 3dp, kích thước 9sp). Đảm bảo không tràn viền, không ngắt chữ, dễ đọc trên cả 2 theme.
   - Các ô `BLOCKED` thông thường chỉ hiển thị 1 dòng icon khóa + giờ.
6. **1f. Ghi chú dưới Legend**: Bổ sung văn bản hướng dẫn chuẩn wireframe: *"Mỗi ô là 30 phút. Chọn nhiều ô liền nhau trên cùng sân hoặc ở nhiều sân. Khung giờ được giữ 5 phút khi bạn bấm 'Giữ chỗ'."*
7. **1g. Nút Pill "Đặt cố định theo tuần"**: Thu gọn về kích thước nội dung (`wrap_content`), căn trái (`layout_gravity="start"`), không kéo giãn toàn màn hình.
8. **1h. Thanh tóm tắt đáy (Bottom Summary)**: Khi đã chọn slot, hiển thị tổng tiền in đậm (`20sp`) và dòng chi tiết định dạng chuẩn `Sân 1 · T3, 06/10 · 19:00 – 21:00 · 2 giờ`; hỗ trợ liệt kê theo từng sân nếu chọn đa sân; chưa chọn thì giữ thông báo mặc định.
9. **1i. Phân biệt PAST và BOOKED**:
   - `PAST`: Làm mờ alpha 0.35f kèm gạch ngang chữ (`STRIKE_THRU_TEXT_FLAG`).
   - `BOOKED`: Nền xám nhạt (`sn_slot_booked`), chữ xám, alpha 1.0f, **không** gạch ngang.

#### Màn hình 2 — Xác nhận đặt sân (BookingConfirm)
1. **2a. Tiêu đề**: Đổi tiêu đề màn hình thành *"Xác nhận đặt sân"* (`booking_confirm_title`).
2. **2b. Sửa lỗi "30%%"**: Khắc phục lỗi hiển thị chuỗi, chuẩn hóa thành *"Tiền cọc (30%)"* (`booking_summary_deposit_label`).
3. **2c. Thẻ slot đã chọn**: Mỗi thẻ hiển thị đầy đủ badge sân bo vuông (`S1`/`S2`), tên sân in đậm kèm mặt sân (`Sân 1 · Thảm`), thứ ngày tháng và khoảng giờ chơi (`T3, 06/10 · 19:00 – 21:00 (2.0 giờ chơi)`), tổng tiền theo sân căn phải (`tvCourtSubtotal`).
4. **2d. Link "Đổi khung giờ"**: Thêm liên kết văn bản màu xanh thương hiệu (`#1B5E45`) ngay dưới danh sách thẻ slot; khi bấm sẽ quay lại màn hình lưới mà **không** nhả giữ chỗ (duy trì hold trong hạn 5 phút theo US3).
5. **2e. Khung thông tin cơ sở**: Bổ sung ô ảnh placeholder minh họa vạch sân cầu lông (`illus_court_lines`) trên nền xanh `#1B5E45` bo góc 10dp bên trái thẻ cơ sở, giữ nguyên tên và địa chỉ cơ sở từ dữ liệu thật.
6. **2f. Ranh giới tính năng Spec 050**: Tuyệt đối không build phần chọn phương thức thanh toán, số dư ví hay mã voucher (thuộc 050). Màn hình kết thúc tại bảng tổng kết tiền và 2 nút: `[Hủy / Quay lại]` (nhả hold) và `[Thanh toán]` (bàn giao sang 050 qua `HoldToPaymentArgs`).
7. **2g. Banner đếm ngược**: Đồng hồ đếm ngược thời gian thực từ `holdExpiresAtMillis`, hiển thị thông điệp *"Đang giữ chỗ cho bạn. Hoàn tất trước khi hết giờ."* kèm đồng hồ số phút:giây in đậm nổi bật bên phải.

#### Kết quả Kiểm thử & Quality Gates
- **Unit Tests**: `./gradlew testDebugUnitTest` $\rightarrow$ **218/218 tests PASS (100%)**.
- **Build Package**: `./gradlew assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL**.
- **Android Lint**: `./gradlew lintDebug` $\rightarrow$ **0 ERRORS, BUILD SUCCESSFUL**.
- **Quy tắc an toàn Git**: Không thực hiện bất kỳ lệnh git nào; toàn bộ file chỉnh sửa được liệt kê minh bạch để thành viên Hưng tự kiểm tra và commit.

---

## Mục 27: Bổ sung mục Thanh toán (display-only) Màn 2 & Ràng buộc min-2 / max-8 theo từng sân

| Trường | Nội dung |
|---|---|
| **Người thực hiện** | Thành viên số 2 (Lê Quốc Hưng - B) |
| **Thời điểm** | 27/10/2026 |
| **Công cụ AI** | Google Antigravity Assistant |
| **Mục tiêu** | 1. Xây dựng phần hiển thị (display-only) của mục Thanh toán trên màn hình Xác nhận (`BookingConfirmFragment`) khớp từng chi tiết với mockup `04_booking_buoc2_xac-nhan-giu-cho.png` (không làm logic thật, đánh dấu `// TODO(050)`).<br>2. Sửa lỗi định dạng chuỗi ngày thô trên thẻ slot thành `T4, 28/10 · 06:30 – 07:00` (bỏ hậu tố `(0.5 giờ chơi)`).<br>3. Điều chỉnh quy tắc chọn khung giờ: Áp dụng ràng buộc tối thiểu 2 khung liên tiếp và tối đa 8 khung theo **TỪNG SÂN** (thay thế giới hạn tổng 8 khung trên toàn bộ lựa chọn theo FR-026). |

### Các nội dung đã hoàn thành

#### 1. Mục "Thanh toán" hiển thị trên Màn hình Xác nhận (BookingConfirm - Display-Only)
- **Thứ tự khối theo đúng mockup bước 2**: Banner đếm ngược $\rightarrow$ Thẻ thông tin cơ sở $\rightarrow$ "Khung giờ đã chọn" + Thẻ slot $\rightarrow$ Liên kết "Đổi khung giờ" $\rightarrow$ Khối "Thanh toán" (3 radio options) $\rightarrow$ Thanh số dư ví giả lập $\rightarrow$ Hàng mã giảm giá (viền nét đứt) $\rightarrow$ Thẻ breakdown (Tạm tính, Giảm giá 0 đ, gạch ngang, Tổng cộng) $\rightarrow$ Ghi chú chính sách hủy $\rightarrow$ Bottom bar cố định dưới cùng.
- **Thẻ phương thức thanh toán (3 radio options)**:
  - *"Trả tại sân"* (mặc định): *"Trả khi đến sân. Chủ sân duyệt trong tối đa 60 phút"*.
  - *"Đặt cọc"*: *"Cọc 30% (X đ) bằng ví giả lập, phần còn lại trả tại sân"* (X tính động bằng 30% từ tổng tiền thật).
  - *"Ví SmashNow (giả lập)"*: *"Trừ đủ [tổng thật] đ từ ví, không phải tiền thật"*.
  - Bấm chọn radio chỉ thay đổi trạng thái chọn giao diện (UI selection), không gọi API, không trừ tiền, không làm logic của 050.
- **Thanh số dư ví giả lập**: Nền sunken xám nhạt (`bg_wallet_bar.xml`), icon ví vector (`ic_account_balance_wallet_20.xml`), dòng chữ *"Số dư ví giả lập 0 đ"* (placeholder kèm `// TODO(050)`), bên phải là nút bấm chữ xanh *"Nạp ví giả lập"*. Bấm vào hiển thị Snackbar thông báo tính năng thuộc feature 050.
- **Hàng mã giảm giá**: Hộp viền nét đứt (`bg_voucher_row.xml`), icon vé (`ic_confirmation_number_20.xml`), chữ *"Thêm mã giảm giá"*, icon chevron `>` (`ic_chevron_right_20.xml`). Bấm vào hiển thị Snackbar tính năng thuộc feature 050.
- **Thẻ breakdown chi tiết tiền**:
  - Dòng 1: *"Tạm tính (N × 30 phút)"* bên trái, số tiền tạm tính bên phải.
  - Dòng 2: *"Giảm giá"* bên trái, *"0 đ"* bên phải (kèm `// TODO(050)`).
  - Đường kẻ mờ phân cách.
  - Dòng 3: *"Tổng cộng"* in đậm bên trái, tổng tiền in đậm bên phải (`20sp`).
- **Ghi chú chính sách hủy**: Văn bản ghi chú màu xám nhạt, cỡ chữ nhỏ, nguyên văn mockup: *"Bấm "Xác nhận đặt" là bạn đồng ý chính sách hủy: hủy trước giờ chơi từ 4 giờ trở lên được hoàn 100%; hủy trễ mất tiền cọc, hoặc phạt 30% nếu trả bằng ví. Giao dịch giả lập phục vụ học tập, không thu tiền thật."* (sử dụng thuộc tính `formatted="false"` trong XML để tránh lỗi phân tích cú pháp ký tự `%`).
- **Bottom bar**:
  - Bên trái: Chip đếm ngược màu amber bo góc (`Giữ chỗ: 04:58`) + Tổng tiền in đậm 20sp.
  - Bên phải: Nút xanh thương hiệu `#1B5E45`, bo góc 12dp, chữ trắng in đậm *"Xác nhận đặt"*. Bấm vào hiển thị Snackbar: *"Đã ghi nhận phương thức thanh toán. Thanh toán thật sẽ được tích hợp ở feature 050."*
- **Hành vi nhả slot**: Mũi tên back trên top bar kích hoạt `handleCancelAndRelease()` nhả giữ chỗ nguyên tử theo đúng US3.

#### 2. Sửa lỗi định dạng chuỗi ngày trên Thẻ slot đã chọn
- **Nguyên nhân gốc**: `TimeSlot.date` lưu trữ chuỗi 8 chữ số `yyyyMMdd` (vd: `"20261028"`). `LocalDate.parse(dateStr)` mặc định chỉ chấp nhận định dạng ISO-8601 (`yyyy-MM-dd`), dẫn đến ngoại lệ `DateTimeParseException` và fallback trả về chuỗi số thô dạng `20261028 · 06:30 – 07:00 (0.5 giờ chơi)`.
- **Giải pháp**: Xử lý bóc tách 8 chữ số thành `LocalDate.of(y, m, d)`, tính toán chính xác thứ trong tuần theo múi giờ `Asia/Ho_Chi_Minh` (`T2`–`CN`).
- **Kết quả hiển thị**:
  - Dòng 1: Tên sân + mặt sân (vd: `"Sân 3 · PU"`).
  - Dòng 2: `"T4, 28/10 · 06:30 – 07:00"`, loại bỏ hậu tố `(0.5 giờ chơi)`.
  - Bên phải: Giá tiền của chuỗi sân tương ứng.

#### 3. Ràng buộc tối thiểu 2 khung & tối đa 8 khung theo TỪNG SÂN (Thay thế Cap tổng 8 slots)
- **Quy tắc mới**:
  - Bỏ điều kiện giới hạn tổng số slot trên toàn bộ lựa chọn $\le 8$.
  - Áp dụng giới hạn: **Mỗi sân tham gia vào lựa chọn phải có từ 2 đến 8 khung giờ liên tiếp ($1\text{h} \le t \le 4\text{h}$)**.
  - Nút *"Giữ chỗ"* chỉ sáng (enable) khi **tất cả** các sân được chọn đều thỏa mãn điều kiện $2 \le \text{độ dài chuỗi} \le 8$.
- **Hành vi tương tác chi tiết**:
  - Khi một sân đã đủ 8 khung: Bấm tiếp khung thứ 9 liền kề trên sân đó sẽ bị chặn ngay lập tức và phát Snackbar: `"$courtName đã đủ 8 khung (4 giờ)"`. Người chơi vẫn có thể chọn thêm các khung giờ trên các sân khác bình thường.
  - Vùng tóm tắt UI lưới: Khi có sân chưa đủ 2 khung liên tiếp, nút *"Giữ chỗ"* disable và vùng tóm tắt hiển thị gợi ý thông minh: `"$courtName cần thêm 1 khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ)."`.
  - Khi người chơi bấm "Giữ chỗ": Kiểm tra toàn diện qua `ValidateSlotSelectionUseCase.validateForProceed(state, courtNameMap)`, nếu không thỏa mãn sẽ từ chối và hiển thị thông báo chi tiết rõ tên sân.
  - Xử lý xung đột Realtime / Hold: Sau khi loại bỏ các slot bị tranh chấp, hệ thống tự động tái thẩm định tính hợp lệ theo từng sân. Nếu sân còn lại rơi xuống dưới 2 khung, nút "Giữ chỗ" lập tức disable và vùng tóm tắt hiển thị gợi ý bổ sung khung giờ cho sân đó.

#### 4. Đề xuất cập nhật câu chữ Spec (Proposed Spec Wording)
- **FR-010**:
  > *"Người dùng có thể chọn tối đa 8 khung giờ liên tiếp (4 tiếng) trên mỗi sân con và tối thiểu 2 khung giờ liên tiếp (1 tiếng) trên mỗi sân con tham gia vào lượt đặt. Một đơn đặt có thể chứa nhiều sân con khác nhau trong cùng một ngày, mỗi sân con tuân thủ độc lập giới hạn từ 2 đến 8 khung giờ liên tiếp."*
- **US1-AS4**:
  > *"Khi người dùng chọn đủ tối thiểu 2 khung giờ liên tiếp cho tất cả các sân trong lựa chọn, nút 'Giữ chỗ' chuyển sang trạng thái kích hoạt (enabled). Nếu có bất kỳ sân nào mới chỉ chọn 1 khung giờ, nút 'Giữ chỗ' giữ trạng thái vô hiệu hóa (disabled) và thanh tóm tắt hiển thị gợi ý: '{Tên sân} cần thêm 1 khung giờ liên tiếp mới đặt được (mỗi sân tối thiểu 2 khung, tức 1 giờ).'"*
- **US1-AS7**:
  > *"Khi người dùng cố gắng chọn vượt quá 8 khung giờ liên tiếp trên cùng một sân con, hệ thống chặn hành động chọn và hiển thị thông báo: '{Tên sân} đã đủ 8 khung (4 giờ)'. Người dùng vẫn có thể tiếp tục chọn khung giờ trên các sân con khác."*
- **FR-026**:
  > *"Ràng buộc số lượng khung giờ đặt: Hệ thống áp dụng quy tắc theo từng sân con độc lập — tối thiểu 2 slot (1 giờ) và tối đa 8 slot (4 giờ) liên tục trên mỗi sân con. Không áp đặt trần tối đa 8 slot trên tổng số lượng slot đa sân của toàn đơn."*

#### 5. Kết quả Kiểm thử & Quality Gates (Full Pass)
1. **Unit Tests (`./gradlew testDebugUnitTest`)**:
   - **226/226 tests PASS (100%)**.
   - Bổ sung và cập nhật đầy đủ các ca kiểm thử:
     - `selectExceedingMax8SlotsPerCourt_isBlockedOnSameCourt_allowedOnOtherCourt` (chặn slot thứ 9 trên sân 1, cho phép chọn tiếp trên sân 2).
     - `validateForProceed_requiresMinimum2SlotsPerCourt` (kiểm tra 1 sân FAIL, 1+1 sân FAIL, 2+1 sân FAIL, 2+2 sân PASS).
     - `multiCourtValidation_8Plus8SlotsAllowed` (cho phép 8 slot sân 1 + 8 slot sân 2 = 16 slots).
     - `multiCourtValidation_total9SlotsAcrossCourtsAllowed` (cho phép 2+2+2+3 = 9 slots trên 4 sân).
     - `findIncompleteCourt_returnsHelpfulHintWhenUnder2Slots` (gợi ý chính xác tên sân thiếu khung).
     - `onSlotClicked_whenCourtHas8Slots_blocks9thSlotWithCourtSnackbar` (ViewModel phát Snackbar khi sân chạm trần 8 khung).
2. **Build Package Debug (`./gradlew assembleDebug`)**:
   - **BUILD SUCCESSFUL**.
3. **Android Lint (`./gradlew lintDebug`)**:
   - **0 ERRORS, BUILD SUCCESSFUL** (đã giải quyết cảnh báo `StringFormatMatches` bằng `30%%` và `formatted="false"`).
4. **Quy tắc an toàn Git**: Tuyệt đối không chạy bất kỳ lệnh git nào; toàn bộ file chỉnh sửa được báo cáo chi tiết để người dùng tự xem và commit.









