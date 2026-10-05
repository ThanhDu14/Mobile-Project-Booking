# Nhật ký sử dụng AI – Thành viên 2 (Lê Quốc Hưng)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 2** (mã **B**, phụ trách nhóm 04 Đặt sân · 05 Thanh toán và khuyến mãi · 06 Quản lý lịch đặt). Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-06 | Viết spec 040 (lưới khung giờ đặt sân và giữ chỗ) bằng `/speckit-specify` | 04 |
| 2 | 2026-10-06 | Làm rõ spec 040 bằng `/speckit-clarify` (3 câu hỏi) | 04 |

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
