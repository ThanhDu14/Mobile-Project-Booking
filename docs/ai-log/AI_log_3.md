# Nhật ký sử dụng AI – Thành viên 3 (Nguyễn Đức Duy)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 3** (mã **C**, phụ trách nhóm 07 Đánh giá và yêu thích · 08 Đặt lịch vãng lai · 09 Cộng đồng và chat). Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-05 | Viết spec 070 (đánh giá sân, sân yêu thích) bằng `/speckit-specify` | 07 |
| 2 | 2026-10-05 | Làm rõ spec 070 bằng `/speckit-clarify` (5 câu hỏi) | 07 |
| 3 | 2026-10-05 | Lập kế hoạch kỹ thuật spec 070 bằng `/speckit-plan` | 07 |
| 4 | 2026-10-05 | Chia task triển khai spec 070 bằng `/speckit-tasks` | 07 |
| 5 | 2026-10-06 | Viết spec 080 (đặt lịch chơi vãng lai theo lượt) bằng `/speckit-specify` | 08 |
| 6 | 2026-10-06 | Làm rõ spec 080 bằng `/speckit-clarify` (5 câu hỏi) | 08 |
| 7 | 2026-10-06 | Viết spec 081 (hàng chờ buổi vãng lai) bằng `/speckit-specify` | 08 |
| 8 | 2026-10-06 | Làm rõ spec 081 bằng `/speckit-clarify` (5 câu hỏi) | 08 |

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

---

## Mục 4: Chia task triển khai spec 070 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec `specs/070-review-favorite`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia kế hoạch kỹ thuật thành danh sách task có thứ tự, theo từng user story, kèm test bắt buộc |
| Nhánh Git | `feature/07-review-favorite` |

### Prompt đã dùng

```
/speckit-tasks
Chia plan của spec 070 thành các task cụ thể theo từng user story, có đường dẫn file rõ ràng.
Theo constitution nguyên tắc IV, thêm task viết test trước (Rules trên Emulator, Edge Function,
ViewModel) và đánh dấu những task làm song song được.
```

### Tóm tắt phản hồi của AI

- Sinh `specs/070-review-favorite/tasks.md` gồm **78 task** chia 9 phase:
  - Phase 1 Setup (9 task): thư viện, Hilt, Firebase/Supabase module, Emulator, Supabase local — ghi chú dùng lại PR `chore(core)` chung của nhóm nếu đã có;
  - Phase 2 Foundational (16 task): model domain, interface repository, DTO, chuỗi, nav graph, `ReviewAdapter`, hàm tính tổng điểm, xác thực token, Rules + index, dữ liệu demo;
  - Phase 3–8 theo user story: US1 12 task (MVP), US2 9, US3 8, US4 7, US5 5, US6 5;
  - Phase 9 Polish (7 task): `applyReviewVisibility()` cho D, cập nhật đặc tả CSDL, Espresso, kiểm tra chế độ tối/`w600dp`/TalkBack, chạy quickstart.
- Mỗi story có test viết trước (ví dụ test 10 người gửi đánh giá cùng lúc cho SC-005), 45 task đánh dấu `[P]` làm song song được.
- Bảng phụ thuộc giữa các story và với thành viên khác (B: `completedAt`; D: `ratingSums`, Rules `venues`, `send-notification`).
- Đề xuất thứ tự giao: US1 → US3 → US2 → US6 → US4 → US5, khớp lịch tuần 5 trong team contract.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Có task viết test trước | Dùng | Constitution nguyên tắc IV bắt buộc test Rules, đồng thời, ViewModel |
| Phase 1 dùng lại PR `chore(core)` của nhóm | Dùng | Tránh 4 người cấu hình Hilt/Firebase/Supabase trùng nhau |
| MVP chỉ gồm US1 | Dùng | Demo được sớm luồng chính "đánh giá sau khi chơi" |
| Thứ tự US3 trước US2 | Dùng | Yêu thích độc lập, nhanh xong, dễ demo |

### Cách kiểm chứng

- Kiểm tra định dạng: cả 78 task có ô đánh dấu, mã T001–T078, đường dẫn file; mọi task ở Phase 3–8 có nhãn `[US1]`…`[US6]`.
- Đối chiếu: mỗi user story trong spec có ít nhất một task test và các task cài đặt đủ từ server tới giao diện; mỗi kịch bản trong `quickstart.md` được một checkpoint nhắc tới.
- Việc tiếp theo: chạy `/speckit-analyze` để kiểm tra độ khớp giữa spec, plan và tasks trước khi code.

---

## Mục 5: Viết spec 080 (đặt lịch chơi vãng lai theo lượt) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/080-drop-in-session`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả đầu tiên cho phần tìm, đăng ký, vé QR, hủy, lịch sử và đánh giá buổi vãng lai |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/080-drop-in-session
Người chơi tìm và đăng ký các buổi chơi vãng lai do sân/CLB mở.
- Danh sách buổi: ngày, giờ, sân, trình độ, số chỗ còn lại, giá theo lượt; lọc theo khu vực,
  ngày, khung giờ, trình độ, mức giá.
- Đăng ký: chọn buổi, số người (đi một mình hoặc kèm bạn), xác nhận. Không được vượt số chỗ còn
  lại; việc đăng ký chạy phía server trong transaction (Edge Function register-drop-in) để không
  vượt chỗ khi nhiều người đăng ký cùng lúc.
- Thanh toán theo lượt (giả lập) hoặc trả tại sân; sau khi đăng ký có vé lượt kèm mã QR check-in.
- Hủy đăng ký theo chính sách (cancel-drop-in), xem lịch sử các buổi đã tham gia, đánh giá buổi
  chơi sau khi tham gia (reviews với targetType = DROP_IN, mỗi lượt một lần).
- Xử lý mất mạng, buổi bị hủy hoặc đã đủ người, trạng thái rỗng.
Không bao gồm: hàng chờ và tự đẩy người lên (spec 081), chủ sân mở buổi và quét QR check-in
(spec 112), gửi thông báo (spec 100), thanh toán đặt sân thường (spec 050).
Dữ liệu: dropInSessions, dropInSessions/{id}/registrations/{uid}, reviews theo
docs/design/database/README.md mục 4.7, 4.9.
```

### Tóm tắt phản hồi của AI

- Đọc constitution, đặc tả CSDL (mục 4.5, 4.7, 4.9, 4.13, 7, 8a), spec 070, 011, 022 để thống nhất trình độ, khu vực, cách đánh giá và lối vào từ AI Assistant.
- Tạo `specs/080-drop-in-session/spec.md` và `checklists/requirements.md`:
  - 5 user story: tìm buổi (P1), đăng ký và nhận vé QR (P1), xem vé và lịch sử (P2), hủy theo chính sách (P2), đánh giá buổi chơi (P3);
  - 27 yêu cầu chức năng (FR-001 → FR-027), 14 trường hợp biên, 8 tiêu chí thành công (SC-001 → SC-008);
  - giá trị mặc định do AI chọn: nhóm tối đa 4 người (bản thân + 3 bạn), hủy trước hơn 4 giờ hoàn 100% / trong 4 giờ không hoàn, chỉ đánh giá khi đã check-in, khung giờ Sáng/Chiều/Tối, danh sách 14 ngày tới;
  - AI tự bổ sung: chủ sân không đăng ký buổi của chính mình; buổi bị chủ sân đổi giờ/giá thì người chơi được hủy hoàn 100%; vé xem được khi mất mạng; lượt không check-in hiện "Không đến"; đánh giá vãng lai dùng chung quy tắc spec 070 và tính vào điểm cơ sở.
- Đề xuất SC-002: 20 người cùng đăng ký 5 chỗ cuối thì đúng 5 người thành công, để kiểm thử nguyên tắc III của constitution.
- Checklist chất lượng đạt 16/16, không còn `[NEEDS CLARIFICATION]`. Cập nhật link spec và nhánh Git trong `docs/features/08-drop-in/README.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 5 user story và độ ưu tiên | Dùng | Bao đủ các chức năng nhóm 08 trong proposal, trừ hàng chờ (để riêng spec 081) |
| Đăng ký xác nhận ngay, không giữ chỗ tạm, không cần chủ sân duyệt | Dùng | Buổi vãng lai đã do chủ sân mở sẵn; giữ luồng ≤ 4 bước |
| Nhóm tối đa 4 người, mốc hủy 4 giờ, đánh giá cần check-in | Dùng tạm | Để hỏi lại ở bước clarify |
| Đánh giá vãng lai tính vào điểm trung bình cơ sở | Dùng | Dùng lại cơ chế của spec 070, không thêm luồng tính điểm mới |

### Cách kiểm chứng

- Đối chiếu đặc tả CSDL mục 4.9: trạng thái buổi (`OPEN`, `FULL`, `CANCELLED`, `COMPLETED`) và đăng ký (`REGISTERED`, `CANCELLED`, `CHECKED_IN`; `WAITLISTED` để spec 081) khớp với Key Entities.
- Đối chiếu constitution: nguyên tắc III (FR-007, FR-008, FR-018 – bước nguyên tử, chống trùng), II (FR-009, FR-014, FR-021 – kiểm tra phía server), V (FR-024 – 4 trạng thái màn hình), VI (FR-010 – ghi rõ thanh toán giả lập).
- Đối chiếu spec 011 FR-022a (xóa tài khoản hủy lượt vãng lai) và spec 022 FR-012 (AI điền bộ lọc vãng lai).
- Checklist 16/16; tìm "Edge Function", "transaction", "Firestore" trong spec chỉ còn ở dòng Input gốc.

---

## Mục 6: Làm rõ spec 080 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/080-drop-in-session`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Chốt các giá trị mặc định AI tự chọn ở bước specify và các điểm ảnh hưởng tới tiền, phân quyền, quyền riêng tư |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-clarify
Rà soát spec 080 và hỏi mình lần lượt từng câu về những điểm còn mơ hồ, ưu tiên các điểm ảnh
hưởng tới tiền hoàn khi hủy, điều kiện đánh giá và quyền riêng tư của người đăng ký; mỗi câu
kèm phương án bạn đề xuất và lý do.
```

AI hỏi lần lượt 5 câu. Câu trả lời của mình:

| # | Câu hỏi của AI | AI đề xuất | Mình chọn |
|---|---|---|---|
| 1 | Chính sách hoàn tiền khi người chơi tự hủy? | A – trước hơn 4 giờ hoàn 100%, trong 4 giờ vẫn hủy được nhưng không hoàn | A – đồng ý |
| 2 | Điều kiện để được đánh giá buổi đã kết thúc? | A – chỉ lượt đã check-in | Theo đề xuất |
| 3 | "Thanh toán ngay (giả lập)" hoạt động thế nào? | A – màn hình giả lập luôn thành công, không dùng ví có số dư | Theo đề xuất |
| 4 | Một lần đăng ký kèm tối đa bao nhiêu người? | B – 4 người (bản thân + 3 bạn) | B – đồng ý |
| 5 | Người khác có xem được danh sách người đăng ký không? | A – không, chỉ thấy số chỗ còn lại | A – đồng ý |

### Tóm tắt phản hồi của AI

- Thêm mục **Clarifications / Session 2026-10-06** với 5 câu hỏi – trả lời.
- FR-010: mô tả rõ thanh toán giả lập là màn hình mẫu luôn thành công, hoàn tiền chỉ đổi trạng thái thành "Đã hoàn tiền"; spec 080 không phụ thuộc ví của spec 050.
- FR-021: người chơi khác không thấy danh sách, tên hay ảnh người đăng ký, kể cả người cùng buổi; chi tiết buổi không có phần "Người tham gia".
- User Story 2 kịch bản 2: ghi rõ giới hạn 4 người.
- Assumptions: chuyển 4 giá trị mặc định sang "đã chốt", ghi việc cần báo B (mốc hủy 4 giờ) và D (spec 112 nhắc chủ sân check-in đủ người).
- Checklist chất lượng vẫn 16/16, không còn `[NEEDS CLARIFICATION]`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Mốc hủy 4 giờ, hoàn 100% / 0% | Dùng | Đơn giản, dễ kiểm thử; vẫn cho hủy sát giờ để trả chỗ cho người khác |
| Chỉ đánh giá khi đã check-in | Dùng | Bảo đảm người đánh giá thật sự đã chơi, giống quy tắc spec 070 |
| Ví giả lập có số dư (phương án B câu 3) | **Bỏ** | Phụ thuộc tiến độ spec 050 của B và thêm nhiều trường hợp lỗi không cần thiết |
| Cho người cùng buổi xem nhau (phương án B câu 5) | **Bỏ** | Hạn chế lộ dữ liệu cá nhân (constitution nguyên tắc VI); giao lưu thuộc nhóm 09 |

### Cách kiểm chứng

- Mục Clarifications có đúng 5 dòng; mỗi câu trả lời khớp với FR-006, FR-010, FR-017, FR-021, FR-022 và phần Assumptions.
- Tìm trong spec: không còn "cần xác nhận ở `/speckit-clarify`" hay `[NEEDS CLARIFICATION]`.
- Việc tiếp theo: báo B về mốc hủy 4 giờ, báo D về việc nhắc check-in ở spec 112; sau đó chạy `/speckit-specify` cho spec 081 (hàng chờ).

---

## Mục 7: Viết spec 081 (hàng chờ buổi vãng lai) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/081-drop-in-waitlist`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả đầu tiên cho hàng chờ khi buổi đủ người và việc tự đẩy người lên khi có chỗ |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/081-drop-in-waitlist
Người chơi vào hàng chờ khi buổi vãng lai đã đủ người, và được tự động đẩy lên khi có chỗ trống.
- Ở buổi "Đã đủ người" (spec 080), người chơi bấm "Vào hàng chờ", chọn số người (tối đa 4 như
  spec 080); xem được vị trí của mình trong hàng chờ và rời hàng chờ bất cứ lúc nào.
- Khi có người hủy hoặc chủ sân tăng sức chứa, hệ thống tự đẩy người chờ lâu nhất có số người vừa
  với số chỗ trống lên thành đăng ký chính thức, chạy phía server trong transaction (Edge Function
  cancel-drop-in / register-drop-in) để không vượt chỗ và không đẩy trùng.
- Người được đẩy lên nhận vé QR và thông báo "Đã có chỗ" (DROPIN_SPOT_OPENED); thanh toán theo
  cách đã chọn khi vào hàng chờ (giả lập hoặc trả tại sân), áp dụng chính sách hủy của spec 080.
- Hàng chờ tự đóng khi buổi bắt đầu hoặc bị hủy; người còn trong hàng chờ được báo.
- Có xử lý mất mạng, bấm nhiều lần, trạng thái rỗng.
Không bao gồm: danh sách, đăng ký, vé, hủy và đánh giá buổi (spec 080); chủ sân mở và sửa buổi
(spec 112); gửi thông báo (spec 100).
Dữ liệu: dropInSessions, dropInSessions/{id}/registrations/{uid} (status WAITLISTED,
waitlistedAt) theo docs/design/database/README.md mục 4.9.
```

### Tóm tắt phản hồi của AI

- Dựa trên spec 080 đã clarify (giới hạn 4 người, thanh toán giả lập luôn thành công, mốc hủy 4 giờ, không lộ danh sách người đăng ký) để hàng chờ dùng chung quy tắc.
- Tạo `specs/081-drop-in-waitlist/spec.md` và `checklists/requirements.md`:
  - 4 user story: vào hàng chờ (P1), tự động được đẩy lên (P1), rời hàng chờ (P2), hàng chờ đóng khi buổi bắt đầu/bị hủy (P2);
  - 17 yêu cầu chức năng (FR-001 → FR-017), 10 trường hợp biên, 6 tiêu chí thành công (SC-001 → SC-006);
  - quy tắc đẩy lên: theo thứ tự vào hàng chờ, bỏ qua nhưng giữ vị trí cho nhóm lớn hơn số chỗ trống, chạy cùng bước với thao tác hủy để không vượt chỗ;
  - giá trị mặc định do AI chọn: đẩy lên thành đăng ký ngay (không giữ chỗ chờ xác nhận), hàng chờ không giới hạn, được hủy hoàn 100% trong 30 phút nếu được đẩy lên sát giờ, giá tính theo lúc vào hàng chờ.
- Đề xuất SC-003: 5 lượt hủy và 10 người chờ cùng lúc, không vượt chỗ, không đẩy trùng, đúng thứ tự.
- Checklist chất lượng đạt 16/16, không còn `[NEEDS CLARIFICATION]`. Cập nhật link spec 081 trong `docs/features/08-drop-in/README.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 4 user story và độ ưu tiên | Dùng | Bao đủ phần hàng chờ của nhóm 08, tách khỏi luồng đăng ký spec 080 |
| Lượt chờ dùng chung dữ liệu đăng ký của spec 080 | Dùng | Khớp đặc tả CSDL mục 4.9 (`WAITLISTED`, `waitlistedAt`), mỗi người một document mỗi buổi |
| Không trừ tiền khi đang chờ | Dùng | Người chơi chưa có chỗ; rời hàng chờ không mất phí |
| Đẩy lên ngay, cửa sổ hủy 30 phút, hàng chờ không giới hạn | Dùng tạm | Để hỏi lại ở bước clarify |

### Cách kiểm chứng

- Đối chiếu constitution nguyên tắc III: FR-007, FR-008, FR-013 yêu cầu đẩy lên trong cùng bước nguyên tử với thao tác hủy, không vượt chỗ, không vừa rời vừa được đẩy lên.
- Đối chiếu spec 080: giới hạn 4 người (FR-002), quy tắc không lộ danh tính (FR-006), vé và chính sách hủy dùng lại (FR-010, FR-011).
- Checklist 16/16; tìm "Edge Function", "transaction" trong spec chỉ còn ở dòng Input gốc.

---

## Mục 8: Làm rõ spec 081 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/081-drop-in-waitlist`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Chốt cách đẩy người từ hàng chờ lên, quyền lợi khi được đẩy lên sát giờ và giới hạn của hàng chờ |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-clarify
Rà soát spec 081 và hỏi lần lượt từng câu về những điểm còn mơ hồ, ưu tiên cách đẩy người lên
khi có chỗ, quyền lợi của người được đẩy lên sát giờ và giới hạn của hàng chờ; mỗi câu kèm
phương án đề xuất và lý do.
```

Sau câu 1, mình đồng ý dùng phương án AI đề xuất cho cả 5 câu (đã đọc lại lý do từng câu trước khi chốt):

| # | Câu hỏi của AI | AI đề xuất | Mình chọn |
|---|---|---|---|
| 1 | Được đẩy lên là thành đăng ký ngay hay giữ chỗ chờ xác nhận? | A – thành đăng ký ngay, có vé QR luôn | Theo đề xuất |
| 2 | Người đầu hàng chờ đi nhóm đông hơn số chỗ trống thì sao? | Bỏ qua nhưng giữ vị trí, xét người tiếp theo vừa chỗ | Theo đề xuất |
| 3 | Được đẩy lên sát giờ (trong 4 giờ) có quyền lợi gì khi hủy? | Hủy hoàn 100% trong 30 phút sau khi được đẩy lên | Theo đề xuất |
| 4 | Hàng chờ có giới hạn không, ngừng đẩy lên lúc nào? | Không giới hạn; đóng 30 phút trước giờ bắt đầu, chỗ trống sau đó mở cho đăng ký thường | Theo đề xuất |
| 5 | Chờ nhiều buổi trùng giờ, được đẩy lên một buổi thì sao? | Tự rời hàng chờ các buổi trùng giờ còn lại, không mất phí | Theo đề xuất |

### Tóm tắt phản hồi của AI

- Thêm mục **Clarifications / Session 2026-10-06** với 5 câu hỏi – trả lời.
- FR-001, FR-007, FR-014 và User Story 4: hàng chờ đóng và ngừng đẩy lên 30 phút trước giờ bắt đầu (bản đầu là lúc buổi bắt đầu); chỗ trống sau mốc này mở cho đăng ký thường.
- FR-010: ghi rõ không có bước giữ chỗ chờ xác nhận. Thêm FR-010a: tự rời hàng chờ các buổi trùng giờ khi được đẩy lên; thêm kịch bản 7 cho User Story 2.
- Edge Cases: bổ sung "chỗ trống sát giờ" và quy tắc trùng giờ mới; SC-005 tính cả trường hợp tự rời do trùng giờ.
- Assumptions: chuyển các giá trị mặc định sang "đã chốt", ghi việc báo D về mốc 30 phút.
- Checklist chất lượng vẫn 16/16, không còn `[NEEDS CLARIFICATION]`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Đẩy lên thành đăng ký ngay | Dùng | Đúng mô tả "tự đẩy lên"; không cần tác vụ định kỳ thu hồi chỗ giữ tạm |
| Giữ chỗ 30 phút chờ xác nhận (phương án B câu 1) | **Bỏ** | Thêm trạng thái và tác vụ định kỳ, chỗ trống bị treo lâu hơn |
| Đẩy lên đến tận lúc bắt đầu ở bản đầu | **Sửa** thành đóng trước 30 phút | Người được đẩy lên quá sát giờ có thể không kịp biết để đến |
| Tự rời hàng chờ trùng giờ | Dùng (bổ sung mới) | Tránh một người bị tự động đăng ký hai buổi cùng giờ |

### Cách kiểm chứng

- Mục Clarifications có đúng 5 dòng; mỗi câu trả lời khớp với FR-001, FR-007, FR-010, FR-010a, FR-011, FR-014 và User Story 2, 4.
- Tìm trong spec: không còn "đến tận lúc buổi bắt đầu", "hỏi lại ở `/speckit-clarify`" hay `[NEEDS CLARIFICATION]`.
- Việc tiếp theo: báo D về mốc đóng hàng chờ 30 phút và việc tăng sức chứa/hủy buổi ở spec 112 phải gọi quy tắc đẩy lên; gửi D danh sách loại thông báo của 080 và 081 trước 14/10.
