# Nhật ký sử dụng AI – Thành viên 3 (Nguyễn Đức Duy)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 3** (mã **C**, phụ trách nhóm 07 Đánh giá và yêu thích · 08 Đặt lịch vãng lai · 09 Cộng đồng và chat). Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-05 | Viết spec 070 (đánh giá sân, sân yêu thích) bằng `/speckit-specify` | 07 |
| 2 | 2026-10-05 | Làm rõ spec 070 bằng `/speckit-clarify` (5 câu hỏi) | 07 |
| 3 | 2026-10-05 | Lập kế hoạch kỹ thuật spec 070 bằng `/speckit-plan` | 07 |

---

## Mục 1: Viết spec 070 (đánh giá sân và sân yêu thích) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec `specs/070-review-favorite`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Lên kế hoạch viết spec cho nhóm 07, sau đó sinh bản đặc tả từ mô tả tính năng |
| Nhánh Git | `feature/07-review-favorite` |

### Prompt đã dùng

**Lần 1: yêu cầu AI trình bày kế hoạch trước khi làm**

```
Mình bắt đầu với nhóm tính năng 07 (Đánh giá và yêu thích). Trước khi chạy Spec Kit, hãy đọc
docs/features/07-review-favorite/README.md, docs/guides/w1-w2-spec-assignment.md và đặc tả CSDL
(các collection reviews, favorites), rồi trình bày các bước bạn sẽ làm và bản nháp mô tả cho
/speckit-specify để mình duyệt. Chưa tạo hay sửa file nào.
```

**Lần 2: tạo spec (dùng bản mô tả đã duyệt)**

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/070-review-favorite
Người chơi đánh giá sân sau khi chơi và lưu sân yêu thích. Chỉ đánh giá được khi đơn đặt
sân đã hoàn thành (COMPLETED) và thuộc chính người đó; mỗi đơn đánh giá một lần. Chấm 1–5
sao cho 3 tiêu chí: chất lượng sân, vệ sinh, thái độ phục vụ; điểm tổng là trung bình. Viết
bình luận, đính kèm tối đa N ảnh (bucket review-photos của Supabase). Người viết sửa/xóa
đánh giá của mình. Chủ sân phản hồi một lần cho mỗi đánh giá trên sân của mình. Điểm trung
bình và số lượt đánh giá của sân được hệ thống cập nhật (Edge Function on-review-created),
người dùng không tự ghi. Đánh giá bị admin ẩn thì không hiển thị. Người dùng đã xóa tài
khoản hiện "Người dùng đã xóa". Lưu/bỏ yêu thích từ chi tiết sân, xem danh sách sân yêu
thích; sân bị ẩn/khóa vẫn trong danh sách nhưng có ghi chú. Có xử lý mất mạng, upload ảnh
lỗi, trạng thái rỗng.
Không bao gồm: đánh giá buổi vãng lai (spec 080), báo cáo đánh giá vi phạm và ẩn đánh giá
(spec 120), thông báo khuyến mãi cho người theo dõi (spec 100), hiển thị tổng quan đánh
giá ở chi tiết sân (spec 030).
Dữ liệu: reviews, users/{uid}/favorites, venues.ratingAvg theo docs/design/database/README.md.
```

### Tóm tắt phản hồi của AI

- Lần 1: AI đọc tài liệu nhóm 07, phân công tuần 1–2 và đặc tả CSDL (mục 4.7, 4.8, 7, 8), đề xuất kế hoạch 6 bước (tạo nhánh → specify → đối chiếu constitution/CSDL → clarify → cập nhật tài liệu → review) và bản nháp mô tả ở lần 2. AI liệt kê trước các điểm cần chốt với thành viên khác: "yêu thích = theo dõi" (D), ai chuyển đơn sang `COMPLETED` (B).
- Lần 2: tạo `specs/070-review-favorite/spec.md` và `checklists/requirements.md`:
  - 6 user story: đánh giá sau khi chơi (P1), đính kèm ảnh (P2), sân yêu thích (P2), chủ sân phản hồi (P2), sửa/xóa đánh giá (P3), xem tất cả đánh giá của sân (P3);
  - 27 yêu cầu chức năng (FR-001 → FR-027), 13 trường hợp biên, 8 tiêu chí thành công (SC-001 → SC-008);
  - giá trị mặc định do AI chọn: tối đa 5 ảnh (thay "N"), hạn đánh giá 30 ngày, bình luận ≤ 1000 ký tự, phản hồi ≤ 500 ký tự;
  - AI tự bổ sung: chủ sân không được đánh giá sân của mình; điểm trung bình tính lại cả khi sửa/xóa/ẩn đánh giá; sự kiện thông báo cho spec 100 (FR-026).
- AI đọc máy trạng thái đơn của B và xác định đơn chuyển sang `COMPLETED` ở phía server, nên không cần hỏi lại.
- Checklist chất lượng đạt 16/16, không còn `[NEEDS CLARIFICATION]`. Cập nhật link spec và nhánh Git trong `docs/features/07-review-favorite/README.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Kế hoạch 6 bước và bản mô tả AI đề xuất | Dùng | Đã đối chiếu với phạm vi nhóm 07 và phân công tuần 1–2 |
| 6 user story và độ ưu tiên | Dùng | Bao đủ 5 chức năng của nhóm 07 trong proposal |
| Mặc định 5 ảnh, hạn 30 ngày | Dùng tạm | Để xem lại ở bước clarify (sau đó đã sửa, xem Mục 2) |
| User Story 6 và màn hình "Đánh giá của khách" cho chủ sân | Dùng, cần thống nhất | Spec 030 (A) chỉ có phần tổng quan; menu chủ sân thuộc D |

### Cách kiểm chứng

- Đối chiếu với đặc tả CSDL mục 4.5 (máy trạng thái đơn), 4.7 (`reviews`), 4.8 (`favorites`), 7 (phân quyền), 8 (bucket `review-photos`, ảnh ≤ 2 MB).
- Đối chiếu constitution: nguyên tắc II (kiểm tra quyền phía server – FR-001, FR-010, FR-018), V (4 trạng thái màn hình – FR-017), VI (nội dung người dùng báo cáo được – FR-027).
- Đối chiếu spec 011 FR-020: đánh giá của người đã xóa tài khoản hiện "Người dùng đã xóa" (FR-016).
- Checklist 16/16; tìm "Supabase", "Edge Function", "bucket" trong spec chỉ còn ở dòng Input gốc.

---

## Mục 2: Làm rõ spec 070 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec `specs/070-review-favorite`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Chốt các điểm còn mơ hồ và các giá trị mặc định AI tự chọn, trước khi lập kế hoạch kỹ thuật |
| Nhánh Git | `feature/07-review-favorite` |

### Prompt đã dùng

```
/speckit-clarify
Rà soát spec 070 và hỏi mình lần lượt từng câu về những điểm còn mơ hồ có ảnh hưởng lớn tới
dữ liệu, phân quyền và trải nghiệm người dùng; mỗi câu kèm phương án bạn đề xuất và lý do.
```

AI hỏi lần lượt 5 câu. Câu trả lời của mình:

| # | Câu hỏi của AI | AI đề xuất | Mình chọn |
|---|---|---|---|
| 1 | "Sân yêu thích" có đồng thời là "sân đang theo dõi" (nhận khuyến mãi) không? | A – là một | A – đồng ý |
| 2 | Được viết đánh giá trong bao lâu sau khi đơn hoàn thành? | B – 7 ngày | B – 7 ngày |
| 3 | Được sửa đánh giá đến khi nào? | B – sửa trong hạn 7 ngày, xóa bất cứ lúc nào | Theo đề xuất |
| 4 | Chủ sân có được sửa/xóa phản hồi không? | B – được sửa, không được xóa | Theo đề xuất |
| 5 | Tối đa bao nhiêu ảnh mỗi đánh giá? | B – 3 ảnh | Theo đề xuất |

### Tóm tắt phản hồi của AI

- Thêm mục **Clarifications / Session 2026-10-05** với 5 câu hỏi – trả lời.
- Thêm FR-025a: yêu thích = theo dõi, là nguồn duy nhất để spec 100 biết ai nhận thông báo khuyến mãi; không có nút "Theo dõi" riêng.
- Đổi hạn đánh giá từ 30 ngày thành 7 ngày ở FR-001, SC-002, Edge Cases, Assumptions.
- FR-007: chỉ sửa đánh giá trong hạn 7 ngày; FR-008: xóa bất cứ lúc nào; thêm kịch bản 5 cho User Story 5 (quá hạn chỉ còn nút "Xóa").
- FR-018, FR-019 và User Story 4: chủ sân sửa được phản hồi (nhãn "Đã chỉnh sửa"), không ai xóa được phản hồi.
- Đổi số ảnh tối đa từ 5 thành 3 ở User Story 2, FR-005, Assumptions.
- Checklist chất lượng vẫn 16/16.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Yêu thích = theo dõi | Dùng | Khớp đặc tả CSDL mục 4.8; một khái niệm, một danh sách. Đã báo D |
| Hạn 30 ngày ở bản đầu | **Sửa** thành 7 ngày | Đánh giá khi trải nghiệm còn mới; dễ kiểm thử trong thời gian demo |
| Sửa/xóa đánh giá không giới hạn ở bản đầu | **Sửa** | Tránh đổi nội dung sau khi chủ sân đã phản hồi; vẫn giữ quyền xóa của người dùng |
| Chủ sân xóa phản hồi ở bản đầu | **Bỏ** | Minh bạch: chủ sân không xóa được điều đã nói |
| 5 ảnh ở bản đầu | **Sửa** thành 3 ảnh | Tiết kiệm dung lượng gói miễn phí Supabase |

### Cách kiểm chứng

- Tìm trong spec: không còn "30 ngày", "5 ảnh", "Xóa phản hồi" hay `[NEEDS CLARIFICATION]`.
- Mục Clarifications có đúng 5 dòng, mỗi dòng khớp với phần đã sửa trong FR/User Story/Assumptions.
- Đối chiếu đặc tả CSDL mục 7: chủ sân chỉ được ghi `ownerReply` → khớp quyết định "sửa, không xóa".
- Đã thông báo cả nhóm các quyết định ảnh hưởng tới B (thời điểm hoàn thành đơn) và D (yêu thích = theo dõi, nhắc đánh giá).

---

## Mục 3: Lập kế hoạch kỹ thuật spec 070 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec `specs/070-review-favorite`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Sinh thiết kế kỹ thuật: research, data model, contract, hướng dẫn kiểm chứng |
| Nhánh Git | `feature/07-review-favorite` |

### Prompt đã dùng

```
/speckit-plan
Lập kế hoạch kỹ thuật cho spec 070 theo constitution (MVVM, Firebase gói Spark, Supabase gói
Free) và đặc tả CSDL chung. Các quyết định ở bước clarify đã được thông báo cho cả nhóm. Ghi rõ
những thay đổi cần các thành viên khác cập nhật trên collection của họ.
```

### Tóm tắt phản hồi của AI

- Sinh `plan.md`, `research.md` (12 quyết định R1–R12), `data-model.md`, `contracts/edge-function-review.md`, `contracts/security-rules.md`, `contracts/ui-screens.md`, `quickstart.md`.
- Phát hiện quan trọng: Firebase gói Spark **không có trigger Firestore**, nên Edge Function `on-review-created` trong đặc tả CSDL không tự chạy được. AI đề xuất một Edge Function `review` (create/update/delete/reply/discardPhotos) ghi đánh giá và cập nhật điểm sân trong **cùng một transaction**; client chỉ đọc `reviews`.
- Thêm trường mới: `venues.ratingSums` (D), `bookings.completedAt` (B), và trong `reviews`: `starBucket`, `hasPhotos`, `hasOwnerReply`, `authorDeleted`, `completedAt`, `editedAt`; `favorites.venueId`.
- Đổi đường dẫn ảnh bucket `review-photos` thành `{uid}/{reviewId}/...` vì RLS của Supabase không đọc được Firestore.
- Đề xuất D tách Rules `venues`: `get` mọi trạng thái, `list` chỉ `ACTIVE`, để danh sách yêu thích phân biệt sân bị ẩn và sân không còn.
- Constitution Check: đạt cả 7 nguyên tắc, không có vi phạm.
- Liệt kê 9 thay đổi cần đưa vào `docs/design/database/README.md` và bảng việc phối hợp với A, B, D.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Edge Function `review` thay `on-review-created` | Dùng | Không có trigger trên gói Spark; transaction giữ điểm trung bình đúng khi nhiều người gửi cùng lúc (SC-005) |
| Lưu `ratingSums` thay vì tính lại từ đầu | Dùng | Cập nhật O(1), tiết kiệm lượt đọc gói miễn phí |
| Đổi đường dẫn ảnh thành `{uid}/{reviewId}/...` | Dùng | RLS tự kiểm tra được, không phụ thuộc Edge Function `sign-upload` của D |
| Photo Picker thay vì xin quyền đọc ảnh | Dùng | Không cần quyền bộ nhớ (nguyên tắc VI) |
| Các thay đổi trên collection của B, D, A | Chờ xác nhận | Các bạn cập nhật phần của mình trong đặc tả CSDL trước 14/10 |

### Cách kiểm chứng

- Đối chiếu Constitution Check với từng nguyên tắc I–VII trong `.specify/memory/constitution.md`.
- Đối chiếu mọi FR trong spec với ít nhất một quy tắc trong data-model/contract hoặc một kịch bản trong `quickstart.md`.
- Kiểm tra lại giới hạn của gói Spark (không có Cloud Functions) theo constitution phần Ràng buộc công nghệ.
- Việc tiếp theo: gửi bảng "Phối hợp với nhóm" trong `plan.md` cho A, B, D; chạy `/speckit-tasks`.
