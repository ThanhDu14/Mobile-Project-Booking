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
| 9 | 2026-10-06 | Viết spec 090 (nhóm/CLB và bảng tin cộng đồng) bằng `/speckit-specify` | 09 |
| 10 | 2026-10-06 | Làm rõ spec 090 bằng `/speckit-clarify` (5 câu hỏi) | 09 |
| 11 | 2026-10-06 | Viết spec 091 (chat nhóm và chat với chủ sân) bằng `/speckit-specify` | 09 |
| 12 | 2026-10-06 | Làm rõ spec 091 bằng `/speckit-clarify` (5 câu hỏi) | 09 |
| 13 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 080 bằng `/speckit-plan` | 08 |
| 14 | 2026-10-15 | Chia task triển khai spec 080 bằng `/speckit-tasks` | 08 |
| 15 | 2026-10-15 | Kiểm tra độ khớp spec/plan/tasks của spec 080 bằng `/speckit-analyze` | 08 |
| 16 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 081 bằng `/speckit-plan` | 08 |
| 17 | 2026-10-16 | Chia task triển khai spec 081 bằng `/speckit-tasks` | 08 |
| 18 | 2026-10-16 | Kiểm tra độ khớp spec/plan/tasks của spec 081 bằng `/speckit-analyze` và sửa theo kết quả | 08 |
| 19 | 2026-10-16 | Đồng bộ tasks 070/080 với spec 010 và cấu hình Firebase, Supabase trên main | 07, 08 |
| 20 | 2026-10-16 | Lập kế hoạch kỹ thuật spec 090 bằng `/speckit-plan` | 09 |
| 21 | 2026-10-16 | Chia task triển khai spec 090 bằng `/speckit-tasks` | 09 |
| 22 | 2026-10-16 | Kiểm tra độ khớp spec/plan/tasks của spec 090 bằng `/speckit-analyze` và sửa theo kết quả | 09 |
| 23 | 2026-10-16 | Lập kế hoạch kỹ thuật spec 091 bằng `/speckit-plan` | 09 |
| 24 | 2026-10-16 | Chia task triển khai spec 091 bằng `/speckit-tasks` | 09 |
| 25 | 2026-10-16 | Kiểm tra độ khớp spec/plan/tasks của spec 091 bằng `/speckit-analyze` và sửa theo kết quả | 09 |
| 26 | 2026-10-30 | Triển khai US3 sân yêu thích (Phase 1–2 phần chung + Phase 5) của spec 070 bằng `/speckit-implement` | 07 |
| 27 | 2026-10-30 | Kiểm tra tiến độ tuần 4, soạn tài liệu PDF hướng dẫn test trên emulator và giải thích code | 07 |
| 28 | 2026-10-30 | Làm trước phần nền US1 của spec 070: Rules `reviews`, index, mã lỗi và DTO Edge Function | 07 |

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

---

## Mục 9: Viết spec 090 (nhóm/CLB và bảng tin cộng đồng) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/090-community-group`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả đầu tiên cho nhóm/CLB, bảng tin, sự kiện giao lưu và báo cáo bài đăng |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/090-community-group
Người chơi tạo và tham gia nhóm/CLB cầu lông, đăng bài và sự kiện giao lưu trên bảng tin nhóm.
- Tạo nhóm: tên, mô tả, ảnh đại diện, khu vực, chế độ công khai hoặc riêng tư; người tạo là
  chủ nhóm. Tìm nhóm theo tên và khu vực; tham gia nhóm công khai ngay, nhóm riêng tư phải được
  chủ nhóm/quản trị viên duyệt. Rời nhóm; chủ nhóm phân quyền quản trị viên, mời ra khỏi nhóm.
- Bảng tin nhóm: thành viên đăng bài (chữ, tối đa vài ảnh) hoặc sự kiện giao lưu (thời gian,
  địa điểm, số người dự kiến); thành viên bấm "Tham gia sự kiện". Người đăng sửa/xóa bài của mình,
  quản trị viên ẩn bài vi phạm trong nhóm.
- Báo cáo bài đăng vi phạm (gửi vào hàng đợi kiểm duyệt của admin, spec 120).
- Số thành viên do hệ thống cập nhật, người dùng không tự ghi. Ảnh lưu ở bucket group-media
  của Supabase. Có xử lý mất mạng, upload ảnh lỗi, trạng thái rỗng.
Không bao gồm: chat nhóm và chat riêng (spec 091), xử lý báo cáo của admin (spec 120), gửi
thông báo (spec 100).
Dữ liệu: groups/{groupId} (+ members, posts), reports theo docs/design/database/README.md
mục 4.10, 4.12, 8.
```

### Tóm tắt phản hồi của AI

- Đọc đặc tả CSDL mục 4.10 (`groups`, `members`, `posts`), 4.12 (`reports`), 7 (phân quyền), 8 (bucket `group-media`) và spec 011 (khu vực, xóa tài khoản).
- Tạo `specs/090-community-group/spec.md` và `checklists/requirements.md`:
  - 6 user story: tạo nhóm (P1), tìm và tham gia (P1), đăng bài (P2), sự kiện giao lưu (P2), quản lý thành viên (P2), báo cáo và ẩn bài (P3);
  - 22 yêu cầu chức năng (FR-001 → FR-022), 10 trường hợp biên, 8 tiêu chí thành công (SC-001 → SC-008);
  - giá trị mặc định do AI chọn: tên nhóm 3–50 ký tự, bài ≤ 2000 ký tự, tối đa 4 ảnh mỗi bài, mỗi người làm chủ tối đa 5 nhóm, số người dự kiến của sự kiện chỉ để tham khảo, không có bình luận/lượt thích;
  - AI tự bổ sung: phân quyền 3 vai trò (chủ nhóm, quản trị viên, thành viên), chủ nhóm phải chuyển quyền trước khi rời, người bị mời ra phải xin vào lại, danh sách thành viên chỉ thành viên xem được, chuyển quyền chủ nhóm tự động khi chủ nhóm xóa tài khoản.
- Phát hiện mâu thuẫn trong đặc tả CSDL: mục 7 cho mọi người đọc nhóm công khai, nhưng mục 8 để ảnh nhóm ở bucket private chỉ thành viên xem → ghi vào Assumptions để chốt ở clarify.
- Checklist chất lượng đạt 16/16, không còn `[NEEDS CLARIFICATION]`. Cập nhật link spec và nhánh Git trong `docs/features/09-community-chat/README.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 6 user story và độ ưu tiên | Dùng | Bao đủ phần nhóm và bảng tin của nhóm 09; chat để riêng spec 091 |
| Phân quyền 3 vai trò | Dùng | Khớp `members.role` (`OWNER`, `ADMIN`, `MEMBER`) trong đặc tả CSDL mục 4.10 |
| Danh sách thành viên là nguồn của chat nhóm (FR-022) | Dùng | Tránh hai nơi quản lý thành viên lệch nhau giữa 090 và 091 |
| Xem ảnh nhóm công khai, bình luận/lượt thích, giới hạn sự kiện | Dùng tạm | Để hỏi lại ở bước clarify |

### Cách kiểm chứng

- Đối chiếu đặc tả CSDL mục 4.10: các trường của nhóm, thành viên và bài đăng (`type`: `POST`, `EVENT`) đều có trong Key Entities.
- Đối chiếu constitution: nguyên tắc II (FR-002, FR-003, FR-009 – kiểm tra quyền phía server), V (FR-020 – 4 trạng thái màn hình), VI (FR-018 – nội dung báo cáo được).
- Checklist 16/16; tìm "Supabase", "group-media" trong spec chỉ còn ở dòng Input gốc.

---

## Mục 10: Làm rõ spec 090 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/090-community-group`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Chốt quyền xem bảng tin, phạm vi tương tác trên bài đăng và cách hoạt động của sự kiện giao lưu |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-clarify
Rà soát spec 090 và nêu tối đa 5 điểm còn mơ hồ ảnh hưởng tới phân quyền, quyền riêng tư và
trải nghiệm trên bảng tin nhóm, ưu tiên điểm mâu thuẫn trong đặc tả CSDL về việc người ngoài
xem bài và ảnh của nhóm công khai. Mỗi điểm kèm phương án đề xuất và lý do; mình đồng ý dùng
phương án đề xuất cho cả 5 câu, bạn ghi vào spec.
```

| # | Câu hỏi của AI | AI đề xuất | Mình chọn |
|---|---|---|---|
| 1 | Người ngoài có xem được bảng tin (bài, sự kiện, ảnh) của nhóm công khai không? | Không; "công khai" chỉ là tham gia ngay không cần duyệt | Theo đề xuất |
| 2 | Bài đăng có bình luận và lượt thích không? | Không ở phiên bản này; thảo luận qua chat nhóm | Theo đề xuất |
| 3 | Sự kiện đủ số người dự kiến thì có chặn tham gia không? | Không chặn, chỉ hiện "Vượt số dự kiến" | Theo đề xuất |
| 4 | Nhóm riêng tư có hiện khi tìm kiếm không? | Có, hiện trang giới thiệu để xin tham gia | Theo đề xuất |
| 5 | Bài đăng có cần chủ nhóm/quản trị viên duyệt trước không? | Không; hiện ngay, ẩn sau nếu vi phạm | Theo đề xuất |

### Tóm tắt phản hồi của AI

- Thêm mục **Clarifications / Session 2026-10-06** với 5 câu hỏi – trả lời.
- FR-014 và User Story 3 kịch bản 7: bảng tin của mọi nhóm chỉ thành viên đọc được, người ngoài chỉ thấy trang giới thiệu (bản đầu cho người ngoài đọc bài nhóm công khai). Thêm kịch bản 8: bài hiện ngay không qua duyệt.
- FR-005: kết quả tìm kiếm gồm cả nhóm riêng tư (chỉ phần giới thiệu). FR-013: ghi rõ không duyệt bài, không bình luận/lượt thích.
- Edge Cases "Đổi chế độ nhóm", "Người chưa đăng nhập", User Story 6 kịch bản 3 và SC-004 sửa theo quyền xem mới.
- Assumptions: ghi việc sửa dòng `groups` ở mục 7 đặc tả CSDL cho khớp khi viết plan.
- Checklist chất lượng vẫn 16/16, không còn `[NEEDS CLARIFICATION]`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Người ngoài đọc bảng tin nhóm công khai (bản đầu) | **Sửa** thành chỉ thành viên | Khớp bucket `group-media` private (mục 8), ít lộ dữ liệu cá nhân (constitution nguyên tắc VI), Rules đơn giản hơn |
| Bình luận, lượt thích | **Bỏ** khỏi phiên bản này | YAGNI (nguyên tắc VII); chat nhóm spec 091 đã đáp ứng việc thảo luận |
| Số người dự kiến chỉ để tham khảo | Dùng | Không cần transaction giữ chỗ như buổi vãng lai |
| Bài hiện ngay, ẩn sau khi vi phạm | Dùng | Nhóm nhỏ, kiểm duyệt sau qua báo cáo (spec 120) là đủ |

### Cách kiểm chứng

- Mục Clarifications có đúng 5 dòng; mỗi câu trả lời khớp với FR-005, FR-013, FR-014, FR-016 và User Story 3.
- Tìm trong spec: không còn câu cho người ngoài đọc bài, không còn "hỏi lại ở `/speckit-clarify`" hay `[NEEDS CLARIFICATION]`.
- Việc tiếp theo: khi viết plan, sửa dòng `groups` ở mục 7 đặc tả CSDL; chạy `/speckit-specify` cho spec 091 (chat).

---

## Mục 11: Viết spec 091 (chat nhóm và chat với chủ sân) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/091-chat`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả đầu tiên cho chat nhóm, chat riêng với chủ sân, gửi ảnh và báo cáo tin nhắn |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/091-chat
Người dùng trò chuyện theo thời gian thực.
- Mỗi nhóm/CLB (spec 090) có một cuộc trò chuyện chung, thành viên trong đó đúng bằng danh sách
  thành viên nhóm.
- Người chơi nhắn riêng cho chủ sân từ chi tiết sân để hỏi lịch, giá; chủ sân trả lời trong chế
  độ quản lý sân.
- Gửi tin nhắn chữ và ảnh (bucket chat-media của Supabase, chỉ người trong cuộc trò chuyện xem
  được). Danh sách cuộc trò chuyện sắp xếp theo tin nhắn mới nhất, có số tin chưa đọc.
- Báo cáo tin nhắn vi phạm (spec 120).
- Có xử lý mất mạng (tin chờ gửi, gửi lại), gửi lỗi, trạng thái rỗng.
Không bao gồm: tạo/quản lý nhóm và thành viên (spec 090), xử lý báo cáo của admin (spec 120),
gửi thông báo đẩy (spec 100).
Dữ liệu: chats/{chatId} (+ messages), reports theo docs/design/database/README.md mục 4.11,
4.12, 8.
```

### Tóm tắt phản hồi của AI

- Đọc đặc tả CSDL mục 4.11 (`chats`, `messages`), 4.12 (`reports`), 4.13 (`CHAT_MESSAGE`), 5 (ID tất định chat riêng), 8 (bucket `chat-media` private) và spec 090 (FR-022 – thành viên nhóm là nguồn của chat nhóm).
- Tạo `specs/091-chat/spec.md` và `checklists/requirements.md`:
  - 5 user story: nhắn riêng với chủ sân (P1), chat nhóm (P1), danh sách trò chuyện và tin chưa đọc (P2), gửi ảnh (P2), thu hồi/báo cáo/chặn (P3);
  - 18 yêu cầu chức năng (FR-001 → FR-018), 9 trường hợp biên, 7 tiêu chí thành công (SC-001 → SC-007);
  - giá trị mặc định do AI chọn: chỉ chat riêng người chơi – chủ sân, thành viên mới đọc toàn bộ lịch sử nhóm, thu hồi tin trong 10 phút (không sửa tin), chặn trong chat riêng, tin chữ ≤ 1000 ký tự, một ảnh mỗi tin, tải 30 tin mỗi lần;
  - AI tự bổ sung: tin chờ gửi khi mất mạng và chống gửi trùng, tắt thông báo từng cuộc trò chuyện, mục "Tin nhắn khách hàng" riêng cho chủ sân, nhóm bị khóa thì chat chỉ đọc.
- Checklist chất lượng đạt 16/16, không còn `[NEEDS CLARIFICATION]`. Cập nhật link spec 091 trong `docs/features/09-community-chat/README.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 5 user story và độ ưu tiên | Dùng | Bao đủ 4 chức năng chat còn lại của nhóm 09 trong proposal |
| Một cuộc trò chuyện cho mỗi cặp người chơi – chủ sân | Dùng | Khớp ID tất định `{uidNhỏ}_{uidLớn}` ở đặc tả CSDL mục 5 |
| Tin chờ gửi và chống trùng khi mất mạng | Dùng | Constitution nguyên tắc V: mất mạng không được treo hay mất dữ liệu |
| Phạm vi chat riêng, thu hồi, chặn, lịch sử nhóm | Dùng tạm | Để hỏi lại ở bước clarify |

### Cách kiểm chứng

- Đối chiếu đặc tả CSDL mục 4.11: loại `GROUP`/`DIRECT`, `groupId`, `venueId`, `lastMessage`, trạng thái tin nhắn đều có trong Key Entities.
- Đối chiếu constitution: nguyên tắc II (FR-004, FR-016 – kiểm tra phía server), V (FR-017 – 4 trạng thái màn hình), VI (FR-014 – báo cáo tin nhắn).
- Checklist 16/16; tìm "Supabase", "chat-media" trong spec chỉ còn ở dòng Input gốc.

---

## Mục 12: Làm rõ spec 091 bằng `/speckit-clarify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/091-chat`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-clarify` |
| Mục đích | Chốt phạm vi chat riêng, quyền đọc lịch sử chat nhóm và các thao tác an toàn (thu hồi, chặn) |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-clarify
Rà soát spec 091 và nêu tối đa 5 điểm còn mơ hồ ảnh hưởng tới phân quyền, quyền riêng tư và an
toàn khi nhắn tin (ai được nhắn riêng cho ai, ai đọc được lịch sử chat nhóm, thu hồi tin, chặn
người dùng). Mỗi điểm kèm phương án đề xuất và lý do; mình đồng ý dùng phương án đề xuất cho cả
5 câu, bạn ghi vào spec.
```

| # | Câu hỏi của AI | AI đề xuất | Mình chọn |
|---|---|---|---|
| 1 | Người chơi có nhắn riêng cho người chơi khác không? | Không; chỉ người chơi – chủ sân, người chơi với nhau qua chat nhóm | Theo đề xuất |
| 2 | Thành viên mới có đọc được tin cũ của chat nhóm không? | Có, toàn bộ lịch sử | Theo đề xuất |
| 3 | Có thu hồi hoặc sửa tin nhắn không? | Thu hồi trong 10 phút, không sửa | Theo đề xuất |
| 4 | Có chặn người dùng trong chat riêng không? | Có, cả hai bên chặn/bỏ chặn được | Theo đề xuất |
| 5 | Chủ sân nhiều cơ sở: mỗi cơ sở một cuộc trò chuyện hay một cuộc chung? | Một cuộc chung mỗi cặp, hiện cơ sở hỏi gần nhất | Theo đề xuất |

### Tóm tắt phản hồi của AI

- Thêm mục **Clarifications / Session 2026-10-06** với 5 câu hỏi – trả lời. Cả 5 câu trùng với giá trị mặc định ở bản đầu nên FR-001, FR-002, FR-003, FR-010, FR-016 giữ nguyên nội dung, chỉ chuyển từ "giả định" sang "đã chốt".
- Assumptions: ghi rõ trạng thái chặn và thời điểm đọc gần nhất là dữ liệu mới so với đặc tả CSDL mục 4.11, cần bổ sung khi viết plan.
- Checklist chất lượng vẫn 16/16, không còn `[NEEDS CLARIFICATION]`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Chỉ chat riêng người chơi – chủ sân | Dùng | Đúng phạm vi proposal; giảm tin nhắn rác giữa người lạ |
| Thành viên mới đọc toàn bộ lịch sử | Dùng | Rules chỉ cần kiểm tra đang là thành viên; người mới nắm được lịch hẹn của CLB |
| Sửa tin nhắn | **Bỏ** | Tránh đổi nội dung sau khi đã bị báo cáo; thu hồi 10 phút đủ cho trường hợp gửi nhầm |
| Chặn trong chat riêng | Dùng | Constitution nguyên tắc VI: người dùng tự bảo vệ trước tin làm phiền |

### Cách kiểm chứng

- Mục Clarifications có đúng 5 dòng; mỗi câu trả lời khớp với FR-001, FR-002, FR-003, FR-010, FR-016 và User Story 1, 2, 5.
- Tìm trong spec: không còn "hỏi lại ở `/speckit-clarify`" hay `[NEEDS CLARIFICATION]`.
- Việc tiếp theo: báo A về nút "Nhắn tin cho chủ sân" ở spec 030, báo D về mục "Tin nhắn khách hàng" trong menu chủ sân; khi viết plan bổ sung trạng thái đọc/chặn vào đặc tả CSDL mục 4.11.

---

## Mục 13: Lập kế hoạch kỹ thuật spec 080 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/080-drop-in-session`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Sinh thiết kế kỹ thuật: research, data model, contract, hướng dẫn kiểm chứng |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/080-drop-in-session
Lập kế hoạch kỹ thuật cho spec 080 theo constitution (MVVM, Firebase gói Spark, Supabase gói Free)
và đặc tả CSDL chung (mục 4.9 dropInSessions + registrations). Lưu ý: slot đã chốt 30 phút; buổi
vãng lai giữ slot bằng slots status=BLOCKED, blockReason=DROP_IN trong cùng transaction (spec 111
FR-010, 112 FR-009); gói Spark không có trigger Firestore nên đăng ký/hủy lượt phải qua Edge
Function có transaction để không vượt sức chứa. Ghi rõ thay đổi cần B, D cập nhật trên collection
của họ và các type thông báo cần gửi D.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:**
  - `plan.md`
  - `research.md` (15 quyết định R1–R15)
  - `data-model.md`
  - `contracts/edge-functions-dropin.md`, `contracts/security-rules.md`, `contracts/ui-screens.md`
  - `quickstart.md`
- **Đăng ký và hủy:** qua Edge Function `register-drop-in` và `cancel-drop-in`. Mỗi function chạy một Firestore transaction trên document buổi (`registeredCount`), thử lại tối đa 8 lần khi xung đột. Chống trùng bằng `registrations/{uid}` cộng `requestId`.
- **Slot:** spec 080 không ghi `slots`. Slot `DROP_IN` do Edge Function tạo/sửa/hủy buổi của D ghi (spec 112).
- **Thanh toán:** `MOCK_PAY` hoặc `AT_VENUE`. Thêm `paymentStatus` (`PAID`, `UNPAID`, `REFUNDED`).
- **Hoàn tiền:** hủy trước giờ bắt đầu từ 240 phút trở lên, hoặc buổi đã bị chủ sân đổi, thì hoàn 100%. Mốc tính theo giờ server. App gọi `dryRun` để hiện số tiền được hoàn trước khi người chơi xác nhận.
- **QR và mã vé:** `qrToken` 32 byte ngẫu nhiên và `ticketCode` dạng `DI-XXXXXXXX`. Cả hai bị xóa khi hủy.
- **Nhãn "Đã tham gia / Đã hủy / Buổi bị hủy / Không đến":** suy ra từ `status`, `cancelledBy` và giờ kết thúc, không thêm enum.
- **Trường mới:**
  - Trong `registrations`: `userId`, `sessionStartAt`, `paymentStatus`, `ticketCode`, `cancelledBy`, `sessionChanged`…
  - Trong `dropInSessions`: `timeSlot`, `venueSnapshot`.
- **Đánh giá buổi:** mở rộng Edge Function `review` (spec 070) với `sessionId`. Điều kiện là đăng ký đã `CHECKED_IN` và buổi đã kết thúc.
- **Hàm dùng chung** trong `_shared/dropin.ts` (`deriveSessionFields`, `computeRefund`, `cancelRegistrationInTx`, `afterSpotsReleased`) để spec 081, spec 112 và `delete-account` gọi lại.
- **Constitution Check:** đạt cả 7 nguyên tắc.
- **Bảng phối hợp:**
  - D: trường suy ra, sửa/hủy buổi, check-in, thông báo `DROPIN_REGISTERED` và `DROPIN_CHANGED`.
  - B: hàm transaction có thử lại, `UpcomingBookingsReader`, mốc 4 giờ.
  - A: `delete-account`, bộ lọc AI 022.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Transaction trên document buổi + `requestId` | Dùng | Đúng constitution III, chống vượt chỗ (SC-002) và chống trùng (SC-003) |
| Không ghi `slots` trong spec 080 | Dùng | Spec 112 FR-009 đã giao việc giữ slot `DROP_IN` cho luồng mở buổi của D |
| Mở rộng `review` thay vì tạo function mới | Dùng | Dùng lại quy tắc sửa/xóa/phản hồi của spec 070 |
| Không có cron riêng cho spec 080 | Dùng | Suy ra "đã bắt đầu/kết thúc" từ thời gian; cron đóng hàng chờ thuộc spec 081 |
| Trường `timeSlot`, `venueSnapshot` trên `dropInSessions` | Chờ D đồng ý | Spec 112 FR-008 ghi "không thêm field" |
| `UpcomingBookingsReader` (cảnh báo trùng giờ) | Chờ B xác nhận | Interface thuộc tính năng của B |

### Cách kiểm chứng

- Đối chiếu Constitution Check với từng nguyên tắc I–VII trong `.specify/memory/constitution.md`.
- Mỗi FR trong spec đều có ít nhất một quy tắc trong data-model/contract hoặc một ca test (D1–D13, E1–E15) hay kịch bản Q1–Q15 trong `quickstart.md`.
- Đối chiếu với spec 111 FR-010, 112 FR-008–FR-012 và đặc tả CSDL mục 4.4, 4.9, 4.13. Tìm trong các file vừa sinh: không còn `NEEDS CLARIFICATION`.
- Việc tiếp theo: chạy `/speckit-tasks`; mang bảng "Phối hợp với nhóm" trong `plan.md` ra họp 16/10.

---

## Mục 14: Chia task triển khai spec 080 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/080-drop-in-session`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia kế hoạch kỹ thuật thành danh sách task có thứ tự theo từng user story, kèm test bắt buộc |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/080-drop-in-session
Chia task theo user story, có test Emulator cho trường hợp 2 người đăng ký lượt cuối cùng lúc.
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` gồm **66 task** chia thành 8 phase:

  | Phase | Nội dung | Task |
  |---|---|---|
  | 1 | Setup | 3 |
  | 2 | Foundational | 13 |
  | 3 | US1 – Tìm buổi | 13 |
  | 4 | US2 – Đăng ký và vé QR | 12 |
  | 5 | US3 – Lịch sử và vé offline | 8 |
  | 6 | US4 – Hủy theo chính sách | 6 |
  | 7 | US5 – Đánh giá buổi | 5 |
  | 8 | Polish | 6 |

  Có 32 task đánh dấu `[P]` (làm song song được).
- **Test đồng thời trên Firebase Emulator (T030):**
  - E1: 20 người tranh 5 chỗ cuối, đúng 5 thành công, `registeredCount` không vượt `capacity`.
  - E2: 2 người cùng đăng ký nhóm 2 người vào 3 chỗ còn lại, đúng 1 người thành công.
  - Mỗi ca chạy 10 lần liên tiếp.
- **Test hủy và đăng ký đồng thời (T051).**
- **Test idempotent:** E3 (bấm "Xác nhận" 5 lần) và E4 (bấm "Hủy" 5 lần).
- **Test mốc hoàn tiền:** hủy khi còn 239, 240 và 241 phút.
- Viết test trước (phải fail) rồi mới cài đặt.
- Phase 1 dùng lại hạ tầng của spec 070 / PR `chore(core)`, chỉ thêm ZXing và `strings_dropin.xml`.
- **MVP:** US1 + US2.
- **Bảng phụ thuộc ngoài nhóm:** B (`_shared/firestore.ts`, `UpcomingBookingsReader`), A (`UserProfileReader`), D (trường `timeSlot`/`venueSnapshot`, `DROPIN_REGISTERED`), kèm phương án tạm nếu chưa có.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Test E1, E2 chạy 10 lần liên tiếp | Dùng | Lỗi tranh chấp chỉ lộ ra khi chạy nhiều lần; đây là bài toán cốt lõi theo constitution III |
| Dùng lại Phase 1 của spec 070 thay vì viết lại | Dùng | Tránh làm trùng hạ tầng chung của nhóm |
| Interface tạm khi A/B chưa có | Dùng | Không bị chặn khi các bạn chưa xong phần của mình; báo lại để thay bằng bản thật |
| Lịch tuần (US1–US2 tuần 6, US3–US4 tuần 7) | Dùng | Khớp kế hoạch triển khai trong `docs/team-contract.md` mục 4 |

### Cách kiểm chứng

- Mỗi task có đúng định dạng `- [ ] T### [P?] [US?] mô tả + đường dẫn file`. Dùng `grep` đếm được 66 task, task nào cũng có ID và đường dẫn.
- Mỗi FR-001 → FR-027 và mỗi ca test D1–D13, E1–E15 trong `contracts/security-rules.md` đều có task tương ứng.
- Thứ tự trong mỗi story: test → Edge Function → repository → UseCase → ViewModel → giao diện.
- Việc tiếp theo: chạy `/speckit-analyze` cho spec 080 để kiểm tra độ khớp giữa spec, plan và tasks.

---

## Mục 15: Kiểm tra độ khớp spec 080 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/080-drop-in-session`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` (chỉ đọc, không sửa file) |
| Mục đích | Tìm chỗ thiếu, mâu thuẫn giữa spec, plan và tasks trước khi nhờ D, B review và trước khi code |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-analyze SPECIFY_FEATURE_DIRECTORY=specs/080-drop-in-session
Kiểm tra độ khớp giữa spec.md, plan.md và tasks.md của spec 080: mỗi FR và SC phải có task
tương ứng, không mâu thuẫn với constitution (nhất là nguyên tắc II và III), và khớp với đặc tả
CSDL chung mục 4.9 cùng các spec liên quan 081, 112, 100.
```

### Tóm tắt phản hồi của AI

- **Độ phủ 100%:** cả 27 FR và 7 SC cần build đều có task. SC-008 là chỉ số thử với người dùng nên không cần task.
- **Không có lỗi CRITICAL.** Nguyên tắc II, III và test 2 người đặt cùng lúc (nguyên tắc IV) đều được đáp ứng.
- **1 lỗi HIGH (F1):** plan thêm `timeSlot` và `venueSnapshot` vào `dropInSessions`, trong khi spec 112 FR-008 ghi "không thêm field".
- **6 lỗi MEDIUM:**
  - **F2:** đặc tả CSDL 4.13 nói "tự làm thì không thông báo", mâu thuẫn với việc gửi `DROPIN_REGISTERED`.
  - **U1:** đăng ký lại sau khi hủy chưa xóa các trường hủy còn sót.
  - **C1:** thiếu test cho `cancelRegistrationInTx` khi gọi với `OWNER`/`SYSTEM`.
  - **K1:** T036 đặt interface tạm trong package của B.
  - **D1:** T022 phụ thuộc Rules `venues` của D nhưng chưa ghi vào bảng phụ thuộc.
  - **D2:** các trường AI được phép điền chưa được A xác nhận.
- **3 lỗi LOW:**
  - **X1:** `SESSION_FULL` chưa có trường `waitlistOpen` mà spec 081 cần.
  - **T1:** tên `MOCK_PAY` khác `MOCK_WALLET`.
  - **T2:** câu cũ ở đặc tả CSDL 4.7.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| F1, F2, D1, D2 | Đưa vào danh sách bàn với nhóm ở họp 16/10 | Liên quan collection hoặc quyết định của A, D |
| U1, C1, K1 | Sửa trong `tasks.md` | Sửa nhỏ, thuộc phần của C |
| X1 | Xử lý khi chia task spec 081 | Phát sinh từ 081 |
| T1, T2 | Ghi chú khi cập nhật đặc tả CSDL | Không ảnh hưởng thứ tự triển khai |

### Cách kiểm chứng

- Dùng `grep` đếm các FR/SC xuất hiện trong `tasks.md`, kết hợp đối chiếu theo nội dung (AC, mã ca test E/D) cho các FR không ghi số.
- Đọc lại spec 112 FR-008, FR-010a, FR-011, spec 100 mục thông báo `DROPIN_*`, đặc tả CSDL 4.7, 4.9, 4.13 để xác nhận từng phát hiện liên spec.
- Đối chiếu nguyên tắc I–IV của constitution với Rules (D1–D13), Edge Function (E1–E15) và vị trí file trong `tasks.md`.

---

## Mục 16: Lập kế hoạch kỹ thuật spec 081 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/081-drop-in-waitlist`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Thiết kế hàng chờ dựa trên dữ liệu và Edge Function đã có của spec 080 |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/081-drop-in-waitlist
Lập kế hoạch kỹ thuật cho hàng chờ (spec 081) theo constitution và đặc tả CSDL chung, xây trên
thiết kế đã có của spec 080:
- Dùng lại document registrations (status WAITLISTED) và hai Edge Function register-drop-in,
  cancel-drop-in; chỉ tạo collection hoặc function mới khi thật sự cần, kèm lý do.
- Việc tự đẩy người chờ lên khi có người hủy hoặc chủ sân tăng sức chứa phải chạy trong cùng
  transaction tạo ra chỗ trống, không vượt sức chứa, không đẩy trùng; người đầu hàng không vừa
  chỗ thì bỏ qua nhưng giữ vị trí.
- Người chơi thấy vị trí của mình theo thời gian thực nhưng không lộ danh tính người chờ khác.
- Hàng chờ đóng 30 phút trước giờ bắt đầu theo giờ server.
- Thông báo dùng đúng các type đã chốt với D trong spec 100: DROPIN_SPOT_OPENED,
  DROPIN_WAITLIST_CLOSED.
- Ghi rõ những gì D (spec 112), A (delete-account) và B (_shared/firestore.ts) cần làm.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:**
  - `plan.md`
  - `research.md` (13 quyết định R1–R13)
  - `data-model.md` (chỉ phần mở rộng so với spec 080)
  - `contracts/edge-functions-waitlist.md`, `contracts/ui-screens.md`
  - `quickstart.md`
- **Không có collection mới:**
  - Vào hàng chờ dùng `register-drop-in` với `joinWaitlist: true`.
  - Rời hàng chờ dùng `cancel-drop-in` (có `expectWaitlisted` để không hủy nhầm khi vừa được đẩy lên).
  - Function mới duy nhất là `dropin-close`, chạy bằng pg_cron mỗi phút: đóng hàng chờ lúc T−30 phút và chuyển buổi đã qua sang `COMPLETED`.
- **Đẩy lên:** `promoteFromWaitlist` gắn vào hook `afterSpotsReleased` của spec 080, chạy trong cùng transaction hủy hoặc tăng sức chứa. Mọi thao tác hàng chờ đều ghi vào document buổi, nên các thao tác đồng thời được xếp tuần tự.
- **Vị trí công khai:** document buổi có mảng `waitlistTickets` (chỉ là số vé, không có `uid`), nên app tính vị trí theo realtime mà không lộ ai đang chờ.
- **Trường mới:**
  - Trên buổi: `waitlistNextTicket`, `waitlistTickets`, `waitlistCount`.
  - Trên đăng ký: `waitlistTicket`, `promotedAt`, `waitlistEndReason` (dùng thay cho enum mới).
- **Các xử lý khác:**
  - Tự rời hàng chờ của các buổi trùng giờ khi được đẩy lên.
  - Hoàn 100% nếu hủy trong 30 phút sau khi được đẩy lên sát giờ (`PROMOTED_GRACE`).
  - Mốc 30 phút được kiểm tra lại trong transaction.
- **14 ca test Edge Function**, trong đó có Q6 (5 lượt hủy với 10 người chờ chạy đồng thời, lặp 20 lần) và Q7 (rời hàng chờ cùng lúc bị đẩy lên).
- Constitution Check đạt cả 7 nguyên tắc.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Dùng lại `registrations` và 2 Edge Function của 080 | Dùng | Đúng yêu cầu prompt; một người một document mỗi buổi tự chống vừa đăng ký vừa chờ |
| Đẩy lên trong cùng transaction hủy | Dùng | Constitution III, FR-007, FR-008 |
| Mảng số vé công khai trên buổi | Dùng | Vị trí realtime mà vẫn giữ FR-006 (không lộ danh tính) |
| Function `dropin-close` (pg_cron) | Dùng | Cần để gửi `DROPIN_WAITLIST_CLOSED` đúng mốc; mốc vẫn được kiểm tra lại trong transaction |
| 3 trường `waitlist*` trên `dropInSessions` | Chờ D đồng ý | Liên quan spec 112 FR-008 (giống phát hiện F1 ở Mục 15) |
| `runQuery` trong transaction | Chờ B xác nhận | Cần hỗ trợ trong `_shared/firestore.ts` |
| Giới hạn R11 (nhóm lớn hơn số chỗ còn lại không vào được hàng chờ) | Đưa ra họp nhóm | Đúng chữ FR-001 nhưng có thể gây khó cho người dùng |

### Cách kiểm chứng

- Đối chiếu Constitution Check với nguyên tắc I–VII; kiểm tra mọi thao tác hàng chờ đều ghi vào document buổi (điều kiện để transaction xếp tuần tự).
- Mỗi FR-001 → FR-017 và SC-001 → SC-005 đều có quy tắc trong data-model hoặc ca test Q1–Q14, W1–W2.
- Đối chiếu với spec 080 (hook `afterSpotsReleased`, `computeRefund`), spec 112 FR-010a (tăng sức chứa, hủy buổi) và spec 100 (type `DROPIN_SPOT_OPENED`, `DROPIN_WAITLIST_CLOSED`).
- Việc tiếp theo: chạy `/speckit-tasks` cho spec 081.

---

## Mục 17: Chia task triển khai spec 081 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/081-drop-in-waitlist`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia kế hoạch kỹ thuật hàng chờ thành task theo user story, xây tiếp trên tasks 080, kèm test bắt buộc |
| Nhánh Git | `feature/08-drop-in` (đã pull main `cfe2312`, gồm cấu hình Firebase, `firestore-tests/` và Supabase local) |

### Prompt đã dùng

```
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/081-drop-in-waitlist
Chia task theo user story, xây tiếp trên tasks của spec 080: không làm lại Setup/Foundational đã có, chỉ tham chiếu ID task 080 làm phụ thuộc.
Bắt buộc viết test trước (phải fail) rồi mới cài đặt:
- Edge Function: các ca test trong quickstart, đặc biệt Q6 (5 lượt hủy với 10 người chờ chạy đồng thời, lặp 20 lần) và Q7 (rời hàng chờ cùng lúc bị đẩy lên).
- Firestore Rules: viết vào firestore.rules và firestore-tests/test/<collection>.test.ts theo cấu trúc Vitest vừa merge trên main; mỗi dòng phân quyền có ít nhất một assertSucceeds và một assertFails.
Ghi rõ task nào phụ thuộc nhóm khác (D: trường waitlist* trên dropInSessions, spec 112, type thông báo spec 100; B: runQuery trong _shared/firestore.ts), kèm phương án tạm nếu chưa có.
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` gồm **54 task** chia thành 7 phase:

  | Phase | Nội dung | Task |
  |---|---|---|
  | 1 | Setup (kiểm tra nền 080, chuỗi, migration pg_cron, seed) | 4 |
  | 2 | Foundational (`runQueryInTx`, khung `waitlist.ts`, fixture, Rules, index, model) | 10 |
  | 3 | US1 – Vào hàng chờ | 11 |
  | 4 | US2 – Tự động đẩy lên | 10 |
  | 5 | US3 – Rời hàng chờ | 6 |
  | 6 | US4 – Đóng hàng chờ | 7 |
  | 7 | Polish | 6 |

  Có 25 task đánh dấu `[P]`.
- **Không làm lại hạ tầng:** bảng "Phụ thuộc vào tasks 080" ghi task 081 nào cần task 080 nào (ví dụ T030–T034 cần 080-T006, 080-T053).
- **Test đồng thời:**
  - Q6 (T027): 5 lượt hủy song song, 10 người chờ `partySize` 1–3, lặp 20 lần, in `runs=20 overCapacity=0 doublePromoted=0 orderViolations=0`.
  - Q7 (T036): rời hàng chờ cùng lúc bị đẩy lên, 20 lần, chỉ một kết quả thắng.
  - Fixture chung `waitlist_fixtures.ts` có `assertWaitlistInvariant` gọi sau mỗi ca.
- **Test Rules (T008):** bảng cặp `assertSucceeds`/`assertFails` cho từng dòng Rules, trong file `firestore-tests/test/dropInSessions.test.ts`. Dòng `write: if false` chỉ có ca bị chặn vì ca được phép đi qua Edge Function.
- **Tách file test** (`register-drop-in/waitlist.test.ts`, `cancel-drop-in/waitlist.test.ts`) để không đụng `index.test.ts` của 080.
- **Phát hiện:** tasks 080 (T008) và quickstart 081 còn dùng đường dẫn cũ `firestore-tests/dropin.test.ts`; tasks 081 dùng đường dẫn mới và thêm T052 để sửa quickstart.
- **Bảng phụ thuộc ngoài nhóm:** B (`runQueryInTx`), D (3 trường `waitlist*`, spec 112 gọi `promoteFromWaitlist`/`closeWaitlistInTx`, spec 100 hai type thông báo), A (`delete-account`), cả nhóm (pg_cron, pg_net), mỗi dòng kèm phương án tạm.
- **MVP:** US1 + US2, Q6 phải xanh 20/20 lần trước khi demo.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Tham chiếu `080-T###` thay vì viết lại Setup/Foundational | Dùng | Đúng yêu cầu prompt; tránh làm trùng hạ tầng |
| Khung `waitlist.ts` công bố chữ ký sớm (T006) | Dùng | D và A import được ngay, không phải chờ C cài xong |
| Q6, Q7 lặp 20 lần | Dùng | Lỗi tranh chấp chỉ lộ ra khi chạy nhiều lần (constitution III) |
| Dùng `firestore-tests/test/dropInSessions.test.ts` | Dùng | Khớp cấu trúc Vitest vừa merge trên main |
| Sửa đường dẫn test Rules trong tasks 080 | Đã làm (commit riêng) | Theo yêu cầu thêm của mình: sửa T002, T008, checkpoint Phase 2 của tasks 080 và plan/quickstart/contract của 080, 081; đổi `npm test -- dropin` (không chạy được vì script bọc vitest trong `firebase emulators:exec`) thành `npm test` |
| Nếu D không đồng ý 3 trường `waitlist*` | Đưa ra họp nhóm | Ảnh hưởng thiết kế vị trí hàng chờ (research R2) |

### Cách kiểm chứng

- Dùng `grep` đếm được 54 task đúng định dạng `- [ ] T### [P?] [US?] mô tả + đường dẫn`, 0 dòng sai định dạng.
- Mỗi ca Q1–Q14, W1–W2 trong `contracts/edge-functions-waitlist.md` và M1–M8 trong quickstart đều có task tương ứng.
- Mỗi FR-001 → FR-017 đều được ít nhất một task nhắc tới.
- Kiểm tra `git merge-tree` giữa main và `origin/feature/09-community-chat`: không có conflict, nhánh 09 không có commit riêng.
- Việc tiếp theo: chạy `/speckit-analyze` cho spec 081.

---

## Mục 18: Kiểm tra độ khớp spec/plan/tasks của spec 081 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 08 – Đặt lịch vãng lai (spec `specs/081-drop-in-waitlist`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Tìm chỗ thiếu, mâu thuẫn giữa spec, plan, tasks của spec 081 trước khi triển khai, rồi sửa tasks theo kết quả |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
/speckit-analyze SPECIFY_FEATURE_DIRECTORY=specs/081-drop-in-waitlist
Kiểm tra độ khớp spec/plan/tasks của spec 081, chú ý:
- mỗi FR-001 → FR-017 và SC-001 → SC-006 có task; mỗi ca Q1–Q14, W1–W2, M1–M8 có task;
- tham chiếu sang tasks 080 (080-T###) đúng ID và đúng nội dung;
- phụ thuộc nhóm khác (B runQuery trong transaction, D trường waitlist* và spec 112/100, A delete-account) nhất quán giữa plan và tasks;
- mâu thuẫn với constitution (đặc biệt nguyên tắc II, III, IV).
```

Sau khi xem báo cáo, mình nhờ AI sửa luôn cả 9 mục trong `tasks.md` và contract.

### Tóm tắt phản hồi của AI

- **0 lỗi CRITICAL**, không vi phạm constitution. Độ phủ 100% (23 yêu cầu: 17 FR + 6 SC); đủ 16/16 ca Q1–Q14, W1–W2 và M1–M8.
- 22 tham chiếu `080-T###` đều đúng ID và nội dung.
- **5 lỗi MEDIUM:**
  - C1: không có test FR-009 (người đăng ký thường không được lấy chỗ mà người chờ vừa đủ).
  - C2: test Rules thiếu ca admin đọc lượt chờ (FR-006).
  - C3: `leaveWaitlistInTx(..., "SYSTEM")`, đường mà `delete-account` của A gọi, chưa có test.
  - F1: `leaveWaitlistInTx` cài ở US3 nhưng US2 đã cần.
  - F2: plan ghi cập nhật đặc tả CSDL trước họp 16/10, tasks lại để ở Polish.
- **4 lỗi LOW:** bảng phụ thuộc thiếu việc màn hình chủ sân của D (F3); contract không ghi tên header của `CRON_SECRET` (U1); kiểu `WaitlistJoinResult` chưa định nghĩa (U2); thiếu test chủ sân giảm sức chứa (G1).
- **Đã sửa:**
  - Thêm ca FR-009 vào T027 (chạy 20 lần).
  - Thêm dòng admin vào bảng Rules T008.
  - Thêm ca `SYSTEM` và ca giảm sức chứa vào T026.
  - Chuyển cài `leaveWaitlistInTx` lên T031; T038 chỉ còn kiểm tra `ALREADY_PROMOTED`.
  - Gộp việc gửi đề xuất CSDL cho D vào T001.
  - Thêm dòng D vào bảng phụ thuộc.
  - Ghi header `x-cron-secret` vào contract.
  - Định nghĩa `WaitlistJoinResult` ở T014.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Sửa cả MEDIUM và LOW | Dùng | Sửa nhỏ, không đổi thiết kế; tránh để sót khi triển khai |
| AI đề xuất thêm task `T004a` | Bỏ, gộp vào T001 | Giữ đánh số task liên tiếp, không phải đánh số lại cả file |
| Ca FR-009 chạy 20 lần | Dùng | Tranh chấp giữa hủy và đăng ký thường chỉ lộ ra khi chạy nhiều lần (constitution III) |

### Cách kiểm chứng

- Đọc lại 22 task 080 được tham chiếu để xác nhận đúng nội dung.
- Sau khi sửa, `grep` vẫn đếm được 54 task đúng định dạng, 0 dòng sai.
- Đối chiếu constitution nguyên tắc II, III, IV: mọi thao tác hàng chờ chạy trong transaction trên document buổi, trường 🔒 do Edge Function ghi, có test đồng thời trên Emulator.
- Việc tiếp theo: lập kế hoạch kỹ thuật spec 090.

---

## Mục 19: Đồng bộ tasks 070/080 với spec 010 và cấu hình trên main

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec 070), 08 – Đặt lịch vãng lai (spec 080) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), chat thường (không dùng lệnh Spec Kit) |
| Mục đích | Sau khi pull main, sửa các task Setup của 070/080 đang tạo lại file mà spec 010 (A) hoặc PR #22, #23 đã làm, tránh conflict khi merge |
| Nhánh Git | `feature/08-drop-in` |

### Prompt đã dùng

```
Main vừa merge cấu hình Firebase (PR #22: firebase.json, firestore.rules nền, firestore-tests/ dùng Vitest với test/<collection>.test.ts)
và Supabase local (PR #23), cùng tasks spec 010 của A (010-T001..T004 thư viện/plugin/App, 010-T015 FirebaseModule/SupabaseModule,
010-T021 _shared/auth.ts). Rà tasks 070 và 080 của mình:
- task nào tạo lại file đã có thì đổi thành "kiểm tra và dùng lại", chỉ thêm phần riêng (không đổi chữ ký của 010);
- đổi đường dẫn test Rules sang firestore-tests/test/<collection>.test.ts, mỗi dòng phân quyền có assertSucceeds và assertFails;
- đổi tên khóa cho khớp 010 (SUPABASE_PUBLISHABLE_KEY, USE_EMULATOR);
- giữ nguyên ID task để không phải đánh số lại.
```

### Tóm tắt phản hồi của AI

- **Phát hiện trùng việc với spec 010:**
  - 070-T004, T005 tạo lại `core/di/FirebaseModule.kt`, `SupabaseModule.kt` mà 010-T015 đã nhận.
  - 070-T001 → T003 trùng 010-T001, T002, T004.
  - 070-T020 và 080-T007 cùng tạo `_shared/auth.ts`, mà 010-T021 đã nhận.
  - 070 dùng `SUPABASE_ANON_KEY`, còn 010 và hướng dẫn Supabase dùng `SUPABASE_PUBLISHABLE_KEY`.
- **Đã sửa tasks 070:**
  - T001 → T005, T008, T009, T020 thành "kiểm tra và dùng lại", chỉ thêm phần riêng: `storage-kt`, Coil, plugin `Storage` vào `SupabaseClient`, offline cache.
  - T022 dùng `firestore-tests/test/reviews.test.ts`, `test/favorites.test.ts`.
  - Sửa đường dẫn trong plan và contract, tên khóa trong quickstart.
- **Đã sửa tasks 080:** phần mở đầu Phase 1, T002 và T007 trỏ tới đúng task 010 / 070.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Giữ ID task, chỉ đổi nội dung | Dùng | Các tham chiếu chéo (`070-T006`, `080-T###`) vẫn đúng |
| "Chỉ thêm, không đổi chữ ký" khi đụng file của 010 | Dùng | Giống quy ước 011 đang làm với 010; tránh phá code của A |
| Commit sửa 070 trên nhánh `feature/08-drop-in` | Dùng | Chỉ sửa tài liệu, đi cùng thay đổi của 080 để review một lần |

### Cách kiểm chứng

- Đối chiếu từng task với 010-T001 → T004, T015, T021 và các file có trên main (`firebase.json`, `firestore-tests/test/setup.ts`, `supabase/config.toml`).
- `grep` không còn `SUPABASE_ANON_KEY`, `firestore-tests/reviews.test.ts`, `firestore-tests/dropin.test.ts` trong spec 070/080/081.
- Việc tiếp theo: lập kế hoạch kỹ thuật spec 090.

---

## Mục 20: Lập kế hoạch kỹ thuật spec 090 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/090-community-group`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật cho nhóm/CLB và bảng tin: dữ liệu, Edge Function, Rules, Storage, test |
| Nhánh Git | `feature/09-community-chat` (tạo từ `feature/08-drop-in` để AI log không bị conflict) |

### Prompt đã dùng

```
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/090-community-group
Lập kế hoạch kỹ thuật cho spec 090 (nhóm/CLB và bảng tin cộng đồng), theo cùng khuôn với plan 070 và 080 của mình:
- Dữ liệu đúng collection và trường trong docs/design/database/README.md; trường nào đề xuất thêm thì đánh dấu [MỚI] và đưa vào danh sách cần D/nhóm đồng ý.
- Thao tác nhạy cảm (tham gia/rời nhóm, đổi vai trò, xóa bài, báo cáo, đếm thành viên) đi qua Edge Function có transaction; Rules chỉ mở quyền đọc đúng phạm vi.
- Dùng lại phần đã có: core/ của spec 010 (SessionManager, IdTokenProvider, SupabaseModule, _shared/auth.ts), _shared/firestore.ts của B, ảnh qua Supabase Storage có RLS.
- Ranh giới với spec 091 (chat nhóm dùng chung nhóm), spec 100 (type thông báo), spec 120 (admin xử lý reports).
- Test: Rules trong firestore-tests/test/<collection>.test.ts (mỗi dòng phân quyền có assertSucceeds và assertFails), Edge Function trên Emulator gồm thao tác đồng thời (nhiều người cùng tham gia nhóm đầy chỗ nếu spec có giới hạn), pgTAP cho RLS Storage.
- Ghi bảng phối hợp với nhóm (A, B, D) và Constitution Check đủ 7 nguyên tắc.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:** `plan.md`, `research.md` (18 quyết định R1–R18), `data-model.md`, `contracts/edge-functions-community.md`, `contracts/security-rules.md`, `contracts/ui-screens.md`, `quickstart.md`.
- **Edge Function:** 3 function theo `action`: `group-manage` (11 action), `group-post` (8 action), `group-media-url` (cấp signed URL theo lô). Rules mọi collection `groups` đều `write: if false`.
- **Dữ liệu:**
  - Thành viên, yêu cầu tham gia, người bị mời ra là 3 collection con riêng (`members`, `joinRequests` [MỚI], `removals` [MỚI]) để Rules chỉ cần `exists(members/{uid})`.
  - Người tham gia sự kiện ở `posts/{postId}/attendees/{uid}` [MỚI].
  - `groupId`, `postId` là `requestId` để chống trùng.
- **Đếm đúng khi đồng thời:** `memberCount`, `attendeeCount` chỉ đổi trong transaction trên document cha. Test G5, P6: 20 người cùng lúc, lặp 10 lần.
- **Ảnh:** bảng tin private (`group-media`), signed URL theo lô mỗi trang. Ảnh đại diện nhóm ở bucket public mới `group-avatars` vì phải hiện cho người ngoài và khách.
- **Ranh giới:**
  - Hook `onGroupMembershipChanged` cho spec 091, chạy trong cùng transaction.
  - `anonymizeCommunityGroups` cho `delete-account` của A.
  - `hiddenBy = SYSTEM` và `groups.status = LOCKED` cho spec 120.
  - 7 giá trị `data.event` của `GROUP_ACTIVITY` cho spec 100.
- **Test:** Rules GR1–GR12, pgTAP S1–S5, Edge Function G1–G15, P1–P11, M1–M3, kịch bản thủ công Q1–Q15. Constitution Check đạt cả 7 nguyên tắc.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Mọi ghi qua Edge Function | Dùng | Thao tác nào cũng đổi nhiều document; Rules không kiểm tra được ảnh đã tải lên và không xóa được ảnh (constitution II, III) |
| Tách `joinRequests`, `removals` khỏi `members` | Dùng | Spec cho phép dùng trạng thái trong `members`, nhưng quên lọc `status` một chỗ là lộ bảng tin cho người chưa duyệt |
| Bucket public `group-avatars` [MỚI] | Chờ D đồng ý | Đặc tả CSDL chỉ có `group-media` private; ảnh đại diện hiện ở kết quả tìm kiếm cho người ngoài |
| Đổi `avatarUrl` → `avatarPath` | Chờ D đồng ý | Đặc tả CSDL quy ước Firestore lưu path, không lưu URL |
| `group-media-url` riêng thay vì `sign-upload` chung của D | Đưa ra họp nhóm | Tránh để D phụ thuộc schema nhóm của C |
| Giới hạn mềm 5 nhóm (có thể vượt 1 khi 2 yêu cầu song song) | Dùng | Chỉ ảnh hưởng chính người đó, quy mô đồ án |
| Không có job dọn ảnh mồ côi | Dùng | Constitution VII; app gọi `discard` khi bỏ bài |

### Cách kiểm chứng

- Đối chiếu từng FR-001 → FR-022 và SC-001 → SC-008 với quy tắc trong data-model hoặc ca test (G, P, M, GR, S, Q).
- Đối chiếu trường với đặc tả CSDL mục 4.10, 4.12, 8; mọi trường mới đều đánh dấu [MỚI] và có trong danh sách cuối data-model.
- Đối chiếu ranh giới với spec 011 (research R12 `anonymizeCommunity`), spec 100 (`GROUP_ACTIVITY`), spec 120 (`HIDE_CONTENT`, `targetPath`) và spec 091 (chat nhóm theo danh sách thành viên).
- Việc tiếp theo: chạy `/speckit-tasks` cho spec 090.

---

## Mục 21: Chia task triển khai spec 090 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/090-community-group`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 090 thành task theo 6 user story, có test bắt buộc và bảng phụ thuộc nhóm khác |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/090-community-group
Chia task theo 6 user story của spec 090, cùng khuôn với tasks 080/081:
- Phase 1 chỉ kiểm tra và dùng lại phần nền đã có (010-T001..T004, T015, T021; 070-T006 UiState; cấu hình Firebase/Supabase trên main), không tạo lại.
- Viết test trước (phải fail) rồi mới cài đặt: Rules GR1–GR12 trong firestore-tests/test/groups.test.ts (mỗi dòng phân quyền có assertSucceeds và assertFails), pgTAP S1–S5, Edge Function G1–G15, P1–P11, M1–M3; G5 và P6 (20 người cùng lúc) lặp 10 lần.
- Khung _shared/groups.ts (hook onGroupMembershipChanged, anonymizeCommunityGroups) làm sớm ở Foundational để spec 091 và A dùng được.
- Ghi rõ phụ thuộc nhóm khác (A, B, D theo bảng phối hợp trong plan) kèm phương án tạm nếu chưa có.
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` gồm **75 task** chia thành 9 phase:

  | Phase | Nội dung | Task |
  |---|---|---|
  | 1 | Setup (kiểm tra nền, chuỗi, seed) | 3 |
  | 2 | Foundational (`_shared/groups.ts`, fixture, Rules, Storage, index, model, nav, nén ảnh) | 17 |
  | 3 | US1 – Tạo nhóm | 9 |
  | 4 | US2 – Tìm và tham gia | 10 |
  | 5 | US3 – Bảng tin | 11 |
  | 6 | US4 – Sự kiện | 6 |
  | 7 | US5 – Quản lý thành viên | 7 |
  | 8 | US6 – Báo cáo, ẩn bài | 5 |
  | 9 | Polish | 7 |

  Có 31 task đánh dấu `[P]`.
- **Làm sớm cho người khác:** T006 tạo `_shared/groups.ts` với hook `onGroupMembershipChanged` (thân trống cho spec 091), `anonymizeCommunityGroups` (khung cho A), `getMembership`, `loadGroupForWrite`.
- **Test đồng thời:** G5 (T030) và P6 (T051): 20 người song song, lặp 10 lần, in `runs=10 expected=20 actual=20 duplicates=0`. Fixture T007 có `assertGroupInvariant` gọi sau mỗi ca.
- **Rules và Storage:** T009 GR1–GR12 (cặp `assertSucceeds`/`assertFails`), T011 pgTAP S1–S5, viết trước migration và Rules.
- **Phát hiện:** phần nén ảnh của spec 070 nằm trong `feature/review`; T020 đưa lên `core/image/ImageCompressor.kt` để 070 và 090 dùng chung (constitution I cấm import chéo feature).
- **Bảng phụ thuộc ngoài nhóm:** A (core, auth, keywords, `delete-account`), B (`runQueryInTx`, dùng chung bản của 081-T005), D (trường mới, `group-avatars`, `group-media-url`, spec 100, spec 120), C-091 (hook). Mỗi dòng kèm phương án tạm, kể cả phương án nếu D không đồng ý bucket `group-avatars`.
- **MVP:** US1 + US2, G5 phải xanh 10/10 lần; spec 091 bắt đầu được sau MVP.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Phase 1 chỉ kiểm tra phần nền | Dùng | Tránh trùng việc với 010 (A) và 070, đúng bài học ở Mục 19 |
| Hook và khung `anonymizeCommunityGroups` ở Foundational | Dùng | Spec 091 và A không phải chờ 090 làm xong |
| Đưa nén ảnh lên `core/image` | Dùng | Constitution I; 070 giữ nguyên chữ ký `ReviewPhotoUploader` |
| Dùng chung `runQueryInTx` với 081-T005 | Dùng | Không viết hai bản cùng một hàm khi chờ B |
| Phương án dự phòng khi D không đồng ý `group-avatars` | Dùng | Ghi sẵn để không bị chặn sau họp |

### Cách kiểm chứng

- `grep` đếm được 75 task đúng định dạng `- [ ] T### [P?] [US?] mô tả + đường dẫn`, đánh số liên tiếp, 0 dòng sai.
- Mỗi ca G1–G15, P1–P11, M1–M3, GR1–GR12, S1–S5 và Q1–Q15 đều có task tương ứng.
- Thứ tự trong mỗi story: test → Edge Function → repository → UseCase → ViewModel → giao diện.
- Việc tiếp theo: chạy `/speckit-analyze` cho spec 090.

---

## Mục 22: Kiểm tra độ khớp spec/plan/tasks của spec 090 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/090-community-group`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Tìm chỗ thiếu, mâu thuẫn giữa spec, plan, tasks và với constitution, đặc tả CSDL trước khi code; sửa theo kết quả |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-analyze SPECIFY_FEATURE_DIRECTORY=specs/090-community-group
Kiểm tra độ khớp spec/plan/tasks của spec 090, chú ý:
- mỗi FR-001 → FR-022 và SC-001 → SC-008, mỗi Edge Case có task; mỗi ca G1–G15, P1–P11, M1–M3, GR1–GR12, S1–S5, Q1–Q15 có task;
- tham chiếu task spec khác (010-T###, 070-T###, 081-T###) đúng ID và nội dung;
- phụ thuộc nhóm khác (A, B, D, spec 091) nhất quán giữa plan, research và tasks;
- mâu thuẫn với constitution (đặc biệt nguyên tắc I, II, III, IV) và với đặc tả CSDL.
```

Sau khi xem báo cáo, mình nhờ AI sửa luôn cả 6 mục.

### Tóm tắt phản hồi của AI

- **0 lỗi CRITICAL**, không vi phạm constitution. Độ phủ 100% (30 yêu cầu: 22 FR + 8 SC); đủ mọi ca G, P, M, GR, S, Q. 11 tham chiếu task spec khác đều đúng.
- **1 lỗi HIGH (F1):** "Nhóm của tôi" đọc nhóm theo lô bằng `whereIn(documentId)` mà không lọc `status`, nên Rules `groups` sẽ từ chối cả query khi chạy thật.
- **2 lỗi MEDIUM:**
  - F2: mở nhóm đã xóa trả `PERMISSION_DENIED`, app sẽ hiện lỗi chung thay vì "Nhóm không còn tồn tại".
  - U1: tìm theo tên nhiều từ chưa rõ dùng từ nào cho `array-contains`.
- **3 lỗi LOW:** gộp `array-contains` với `whereIn` nhiều quận chưa kiểm chứng (U2); thiếu test tên nhóm trùng (G1); chưa ghi việc đồng bộ danh sách `reason` báo cáo sang spec 070 (F3).
- **Đã sửa:**
  - T025 thêm `where status in [ACTIVE, LOCKED]`.
  - T029, T032 ánh xạ `PERMISSION_DENIED` thành "Nhóm không còn tồn tại".
  - R5, T031 dùng từ dài nhất cho `array-contains`, lọc các từ còn lại ở app.
  - T035 ghi phương án chỉ lọc một quận nếu Firestore từ chối.
  - G1 (T021) thêm ca tên trùng.
  - Plan thêm dòng C đồng bộ `reason` cho 070, 091.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Sửa F1 ngay | Dùng | Lỗi chỉ lộ ra khi chạy thật với Rules; test Rules GR5 kiểm tra `members` chứ không kiểm tra truy vấn `groups` theo lô |
| Từ dài nhất cho `array-contains` | Dùng | Từ dài lọc được nhiều hơn, ít phải lọc ở app; vẫn đúng FR-005 |
| Lọc một quận nếu Firestore từ chối | Dùng | Có phương án sẵn, không chặn khi code |

### Cách kiểm chứng

- Đối chiếu Rules bản phác (`allow list` cần ràng buộc `status`) với từng truy vấn `groups` trong tasks: chỉ T025 thiếu, đã sửa.
- Đọc lại 010-T001, T002, T004, T012, T015, T016, T021, 070-T005, T006, T043, 081-T005 để xác nhận đúng nội dung.
- Sau khi sửa, `grep` vẫn đếm được 75 task đúng định dạng.
- Việc tiếp theo: lập kế hoạch kỹ thuật spec 091.

---

## Mục 23: Lập kế hoạch kỹ thuật spec 091 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/091-chat`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch kỹ thuật cho chat nhóm và chat với chủ sân, nối với hook của spec 090 |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/091-chat
Lập kế hoạch kỹ thuật cho spec 091 (chat nhóm và chat với chủ sân), cùng khuôn với plan 090:
- Dữ liệu theo đặc tả CSDL mục 4.11 (chats, messages, ID tất định chat riêng); trường mới đánh dấu [MỚI].
- Chat nhóm đồng bộ với danh sách thành viên qua hook onGroupMembershipChanged của spec 090, trong cùng transaction.
- Chọn cách gửi tin đáp ứng được: hiện ngay "Đang gửi", tự gửi khi có mạng, "Gửi lại"/"Xóa" tin lỗi, không tạo tin trùng, số chưa đọc realtime, thông báo CHAT_MESSAGE (Supabase không có trigger Firestore).
- Thu hồi 10 phút theo giờ server, báo cáo (ID tất định như 090), chặn trong chat riêng, ảnh ở chat-media private.
- Test Rules (mỗi dòng có assertSucceeds và assertFails), Edge Function có ca đồng thời (nhiều người gửi cùng lúc, hai bên mở chat riêng cùng lúc), pgTAP, test outbox khi mất mạng.
- Bảng phối hợp A, B, D và Constitution Check đủ 7 nguyên tắc.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:** `plan.md`, `research.md` (16 quyết định R1–R16), `data-model.md`, `contracts/edge-functions-chat.md`, `contracts/security-rules.md`, `contracts/ui-screens.md`, `quickstart.md`.
- **Quyết định chính, gửi tin qua Edge Function `chat-send` + outbox ở app (R1):** hàng đợi offline của Firestore SDK không hủy được lần ghi đã vào hàng đợi, nên không làm được "Xóa" tin lỗi (FR-008); số chưa đọc và thông báo cần tính ở server vì Supabase không có trigger Firestore. `messageId` tất định nên gửi lại không trùng.
- **Dữ liệu:**
  - Collection con đặt tên `participants` [MỚI] thay vì `members`, để không lẫn với collection group `members` của spec 090.
  - `chats.unreadCounts` [MỚI] cho số chưa đọc realtime.
  - `status` (`ACTIVE`, `READ_ONLY`, `DELETED`), `ownerId`, `playerId`, `venueSnapshot` [MỚI].
- **Đồng bộ chat nhóm:** `syncGroupChat` (hook của 090) chạy trong cùng transaction; người mới có `lastReadAt = joinedAt` nên đọc được lịch sử mà không bị tính chưa đọc; `purgeChat` gọi sau khi 090 xóa nhóm.
- **Rules:** chỉ người trong `memberIds` đọc; client chỉ được đặt `unreadCounts.{mình} = 0` và sửa `participants/{mình}`. Huy hiệu qua `core/chat/UnreadChatsReader` để `MainActivity` không import `feature/chat`.
- **Test:** CR1–CR8, S1–S4, C1–C8 (C3: 20 người gửi cùng lúc × 10), D1–D4 (D1: hai bên mở chat cùng lúc × 20), R1–R2, H1–H2, M1–M2, A1, kịch bản Q1–Q16. Constitution Check đạt 7 nguyên tắc.
- **Sửa thêm spec 090:** T060 gọi `purgeChat` sau khi xóa nhóm.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Gửi tin qua `chat-send` thay vì app ghi thẳng | Dùng | Đáp ứng "Xóa" tin lỗi, số chưa đọc và thông báo; độ trễ thêm 0,3–0,8 giây vẫn trong mục tiêu 3 giây |
| Outbox bằng DataStore, không dùng WorkManager | Dùng | Constitution VII: không thêm thư viện; gửi lại khi mở app và khi có mạng là đủ |
| Đổi tên `participants` | Dùng | Tránh lỗi truy vấn collection group "Nhóm của tôi" của 090 |
| Tin bị admin ẩn vẫn còn chữ trong document | Đưa ra họp nhóm | Spec 120 chỉ đổi `status`; đề xuất D ghi thêm `text = ""` khi ẩn |
| `ownerId` cố định lúc tạo chat riêng | Dùng | Trường hợp hai chủ sân hỏi sân của nhau hiếm; giữ một cuộc mỗi cặp đúng đặc tả CSDL |

### Cách kiểm chứng

- Đối chiếu từng FR-001 → FR-018 và SC-001 → SC-007 với quy tắc trong data-model hoặc ca test.
- Kiểm tra mọi truy vấn trong contract đều có `memberIds array-contains <mình>` để Rules chứng minh được người đọc nằm trong cuộc; truy vấn nhóm theo lô có lọc `status` (bài học F1 của spec 090).
- Đối chiếu với spec 090 (hook, `ImageCompressor`, danh sách `reason`), spec 011 (`anonymizeCommunity`), spec 100 (`CHAT_MESSAGE`), spec 120 (`HIDE_CONTENT`).
- Việc tiếp theo: chạy `/speckit-tasks` cho spec 091.

---

## Mục 24: Chia task triển khai spec 091 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/091-chat`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 091 thành task theo 5 user story, có test bắt buộc và bảng phụ thuộc |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/091-chat
Chia task theo 5 user story của spec 091, cùng khuôn với tasks 090:
- Phase 1 chỉ kiểm tra và dùng lại phần nền (010, 070-T006, 090-T006 hook, 090-T020 ImageCompressor, cấu hình trên main), không tạo lại.
- Viết test trước (phải fail): Rules CR1–CR8 trong firestore-tests/test/chats.test.ts (mỗi dòng có assertSucceeds và assertFails), pgTAP S1–S4, Edge Function C1–C8, D1–D4, R1–R2, H1–H2, M1–M2, A1; C3 lặp 10 lần, D1 lặp 20 lần; test Android cho outbox khi mất mạng (SC-004).
- syncGroupChat làm sớm ở Foundational và nối vào hook của 090 để chat nhóm có ngay khi tạo nhóm.
- Ghi rõ phụ thuộc A (spec 030, MainActivity, delete-account), B (REQUEST_TIME, tăng số trong Map), D (100, 111/112, 120) và spec 090, kèm phương án tạm.
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` gồm **59 task** chia thành 8 phase:

  | Phase | Nội dung | Task |
  |---|---|---|
  | 1 | Setup | 3 |
  | 2 | Foundational (`_shared/media.ts`, `_shared/chats.ts` + nối hook 090, Rules, Storage, index, model, nav, outbox) | 17 |
  | 3 | US1 – Chat với chủ sân | 10 |
  | 4 | US2 – Chat nhóm | 5 |
  | 5 | US3 – Danh sách, chưa đọc | 5 |
  | 6 | US4 – Gửi ảnh | 6 |
  | 7 | US5 – Thu hồi, báo cáo, chặn | 6 |
  | 8 | Polish | 7 |

  Có 25 task đánh dấu `[P]`.
- **Làm sớm:** T008 cài `syncGroupChat`, `purgeChat` và điền thân hook `onGroupMembershipChanged` của 090, nên tạo nhóm là có chat nhóm ngay. Test G1, G5 của 090 phải vẫn xanh.
- **Outbox:** T019 (unit test) và T020 (DataStore). T024 là test Android trên Emulator cho SC-004 (tắt mạng, gửi, bật mạng → đúng một tin, lặp 5 lần).
- **Test đồng thời:** D1 (T021) lặp 20 lần, C3 (T031) lặp 10 lần. T031 có test tích hợp với `group-manage` của 090 (bị mời ra thì gửi bị từ chối).
- **Tránh import chéo:** `SignedUrlCache` của 090 chuyển lên `core/media` (T045); huy hiệu qua `core/chat/UnreadChatsReader` (T017, T039).
- **Bảng phụ thuộc ngoài nhóm:** A (spec 030, `MainActivity`, `delete-account`), B (`REQUEST_TIME`, `increment`), D (spec 100, 111/112, 120, đặc tả CSDL), C-090. Mỗi dòng kèm phương án tạm (ví dụ test bằng deep link khi chưa có nút).
- **MVP:** US1 + US2, với D1 20/20, C3 10/10 và test outbox mất mạng xanh.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Nối `syncGroupChat` vào hook ngay ở Foundational | Dùng | Chat nhóm phải có cùng lúc với nhóm (FR-002); 091 test được ngay khi 090 có `create` |
| Test Android cho outbox | Dùng | SC-004 liên quan mạng thật trên thiết bị, unit test không đủ |
| Chuyển `SignedUrlCache` lên `core/media` | Dùng | Constitution I; 090 chỉ đổi import |
| Chia bước 3 của `chat-send` (khóa nhóm, chặn) sang US2, US5 | Dùng | Mỗi story kiểm thử độc lập được; US1 không phụ thuộc phần chặn |

### Cách kiểm chứng

- `grep` đếm được 59 task đúng định dạng, đánh số liên tiếp, 0 dòng sai.
- Mỗi ca C1–C8, D1–D4, R1–R2, H1–H2, M1–M2, A1, CR1–CR8, S1–S4 và Q1–Q16 đều có task tương ứng.
- Thứ tự trong mỗi story: test → Edge Function → repository → UseCase → ViewModel → giao diện.
- Việc tiếp theo: chạy `/speckit-analyze` cho spec 091.

---

## Mục 25: Kiểm tra độ khớp spec/plan/tasks của spec 091 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-16 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec `specs/091-chat`, kèm sửa nhỏ spec 090) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Tìm chỗ thiếu, mâu thuẫn giữa spec, plan, tasks của 091, với spec 090 và Rules, trước khi code; sửa theo kết quả |
| Nhánh Git | `feature/09-community-chat` |

### Prompt đã dùng

```
/speckit-analyze SPECIFY_FEATURE_DIRECTORY=specs/091-chat
Kiểm tra độ khớp spec/plan/tasks của spec 091, chú ý:
- mỗi FR-001 → FR-018, SC-001 → SC-007 và mỗi Edge Case có task; mỗi ca C, D, R, H, M, A, CR, S, Q có task;
- tham chiếu task spec khác (010, 070, 090) đúng ID và nội dung; khớp với hook và Rules của spec 090;
- mọi truy vấn đọc khớp Rules (có memberIds array-contains, truy vấn nhóm theo lô có lọc status);
- mâu thuẫn với constitution (I, II, III, IV) và đặc tả CSDL.
```

Sau khi xem báo cáo, mình nhờ AI sửa luôn cả 5 mục.

### Tóm tắt phản hồi của AI

- **0 lỗi CRITICAL, 0 lỗi HIGH**. Độ phủ 100% (25 yêu cầu: 18 FR + 7 SC); đủ mọi ca kiểm thử. 17 tham chiếu task spec khác đều đúng; mọi truy vấn `chats` có `memberIds array-contains`.
- **2 lỗi MEDIUM:**
  - F1: app ghi `viewingUntil` theo giờ điện thoại, Rules giới hạn `request.time + 2 phút`, nên điện thoại chạy nhanh là bị từ chối (đúng Edge Case "Đồng hồ điện thoại sai").
  - U1: tin "Gửi lỗi" sau 60 giây có còn tự gửi khi có mạng không, chưa chốt.
- **3 lỗi LOW:** 090-T047 chưa đặt `SignedUrlCache` ở `core/media` (F2); 090-T045 chưa dùng `_shared/media.ts` (F3); thiếu ca chủ sân bị thu hồi quyền vẫn nhắn được (G1).
- **Đã sửa:**
  - Rules nới `viewingUntil` lên 10 phút, app tính theo giờ server ước lượng (research R7, data-model, T011, T023).
  - Outbox: lỗi do mạng vẫn tự gửi lại, lỗi không thử lại được thì chờ người dùng (R1, T019).
  - Sửa 090-T045, T047 cho khớp 091.
  - Thêm ca vào D4.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Nới Rules `viewingUntil` lên 10 phút | Dùng | Chỉ ảnh hưởng việc bỏ qua thông báo, không phải quyền truy cập; vẫn chặn giá trị quá xa |
| Tin lỗi do mạng vẫn tự gửi | Dùng | Đúng cả hai câu trong spec: tự gửi khi có mạng, và báo "Gửi lỗi" sau 1 phút |
| Sửa luôn tasks 090 | Dùng | Hai spec cùng của mình; để lệch sẽ tạo hai bản `SignedUrlCache` |

### Cách kiểm chứng

- Đối chiếu từng truy vấn trong tasks/contract với Rules bản phác (`memberIds array-contains`, `status` khi đọc nhóm theo lô, `lastReadAt == request.time`).
- Đọc lại 010, 070, 090 task được tham chiếu; 090-T060 đã gọi `purgeChat`.
- Sau khi sửa, `grep` vẫn đếm được 59 task (091) và 75 task (090) đúng định dạng.
- Việc tiếp theo: họp nhóm 16/10 để chốt các trường [MỚI] và quy ước với A, B, D; sau đó bắt đầu triển khai theo thứ tự 080 → 081 → 090 → 091 (070 song song).

---

## Mục 26: Triển khai US3 sân yêu thích của spec 070 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-30 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec `specs/070-review-favorite`, User Story 3) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Làm xong việc tuần 4 theo team contract: "07: yêu thích, danh sách sân yêu thích" |
| Nhánh Git | `feature/070-review-favorite` |

### Prompt đã dùng

```
Tuần 4 theo team contract, mình (C) phải làm nhóm 07 phần "yêu thích, danh sách sân yêu thích",
tức User Story 3 của specs/070-review-favorite. Hãy triển khai theo tasks.md:
- phần Setup/Foundational còn thiếu: Coil, UiState/StateViews, VenueSummary, model domain,
  FavoriteRepository, DTO/mapper, chuỗi, nav graph nav_review;
- rồi T047–T054.
Làm theo TDD: viết test ViewModel/UseCase và test Rules F1–F4, chạy thấy fail rồi mới cài đặt.
Ràng buộc:
- không tạo lại file nhóm khác đã có trên main;
- ghi trực tiếp Firestore users/{uid}/favorites/{venueId} với Rules chặt schema;
- trái tim đổi ngay kể cả offline, ghi lỗi thì trả lại (FR-021);
- sân bị ẩn/xóa hiện nhãn và không mở được đặt sân (FR-024).
Gắn tạm nút trái tim vào lưới đặt sân (spec 040) và nút ở trang chủ để demo.
Kết quả mong muốn: build qua, test qua, cập nhật tasks.md và README hướng dẫn nhóm khác gắn nút trái tim.
```

### Tóm tắt phản hồi của AI

- 3 commit: `chore(core)` thêm Coil, `UiState`/`StateViews`, `VenueSummary`; `feat(review)` Rules `users/{uid}/favorites`, `FirestoreFavoriteRepository`, `ToggleFavoriteUseCase`, màn "Sân yêu thích", `FavoriteToggleButton`; `feat(review)` gắn trái tim vào header lưới đặt sân và nút tạm ở trang chủ.
- Trái tim cập nhật lạc quan: đổi ngay, ghi lỗi thì trả lại và báo snackbar. Danh sách đọc thêm `venues/{id}` để biết sân `ACTIVE`/`SUSPENDED`/`GONE`.
- Test: 16 unit test (`FavoriteMapperTest`, `ToggleFavoriteUseCaseTest`, `FavoriteVenuesViewModelTest`) và 8 test Rules F1–F4.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Ghi favorite trực tiếp từ client, Rules kiểm schema | Dùng | Dữ liệu riêng của người dùng, không cần Edge Function; đúng research R7 |
| Tạm mở lưới đặt sân khi bấm một sân yêu thích | Dùng tạm | Chi tiết sân (spec 030) chưa có; ghi TODO và bảng "Việc còn chờ nhóm khác" trong README |
| T009 `supabase start` | Chưa làm | Máy chưa có WSL2/Docker; phần yêu thích không dùng Supabase |

### Cách kiểm chứng

- `./gradlew testDebugUnitTest assembleDebug`: build qua, 16/16 unit test qua.
- `cd firestore-tests && npm test`: test F1–F4 qua.
- Đọc lại code từng tầng và chạy thử trên emulator.

---

## Mục 27: Kiểm tra tiến độ tuần 4 và soạn tài liệu hướng dẫn test, giải thích code

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-30 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec 070, US3) |
| Công cụ AI | Claude Code (model Claude Opus 5.5) |
| Mục đích | Biết việc tuần 4 đã đủ chưa; có tài liệu để tự test trên emulator và đọc hiểu code |
| Nhánh Git | `feature/070-review-favorite` |

### Prompt đã dùng

```
Kiểm tra giúp mình việc tuần 4 theo team contract đã xong chưa: đối chiếu tasks.md spec 070,
lịch sử commit, chạy lại build và unit test.
Sau đó soạn vài file PDF đặt ở D:\HK1-26-27:
(1) báo cáo tiến độ và việc còn thiếu;
(2) hướng dẫn từng bước test tính năng Sân yêu thích trên emulator Android, gồm cách dùng
    Firebase Emulator hay Firebase thật và các kịch bản quickstart 9–11;
(3) giải thích code theo từng tầng, có trích code kèm chú thích tiếng Việt để mình đọc hiểu
    cách nó hoạt động.
```

### Tóm tắt phản hồi của AI

- Kết luận: code tuần 4 (T047–T054) đã xong, build và 16 unit test qua. Việc còn lại: deploy Rules lên project thật (báo B trước), mở PR, demo, ảnh minh chứng, AI log, đủ 5 commit.
- 3 file PDF: `01_Tien-do-tuan-4_Spec070.pdf`, `02_Huong-dan-test-tren-may-ao.pdf`, `03_Giai-thich-code-San-yeu-thich.pdf`.
- Lưu ý: app đang dùng Firebase thật (`USE_EMULATOR=false`), nếu project thật chưa có Rules `favorites` thì trái tim sẽ đổi rồi tự trở lại.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 3 file PDF | Dùng, không đưa vào repo | Tài liệu học cá nhân |
| Cách A (Firebase Emulator) để tự test | Dùng | Không đụng dữ liệu thật, dùng đúng Rules trên nhánh |

### Cách kiểm chứng

- Chạy lại `./gradlew testDebugUnitTest assembleDebug`, đọc kết quả từng file test.
- Đối chiếu nội dung PDF với code thật (tên lớp, hàm, chuỗi giao diện).

---

## Mục 28: Làm trước phần nền US1 của spec 070 (Rules `reviews`, index, mã lỗi, DTO)

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-30 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 07 – Đánh giá và yêu thích (spec 070, task T013, T022, T023, T024) |
| Công cụ AI | Claude Code (model Claude Opus 5.5) |
| Mục đích | Chuẩn bị nền cho US1 (đánh giá sân, tuần 5); đủ cam kết 5 commit có nội dung trong tuần |
| Nhánh Git | `feature/070-review-favorite` |

### Prompt đã dùng

```
Tuần này mình cần ít nhất 5 commit có nội dung. Hãy làm trước một số task nền của tuần 5
(US1 đánh giá sân) trong specs/070-review-favorite/tasks.md có thể làm độc lập, không cần Supabase local:
- T022/T023 phần reviews: viết test R1–R9 trước, chạy thấy fail, rồi mới thêm Rules;
- T024: index Firestore;
- T013: mã lỗi ReviewError và DTO cho Edge Function review, kèm unit test.
Mỗi phần một commit đúng quy ước Git, không chia nhỏ giả tạo; cập nhật tasks.md;
không thêm dòng attribution AI vào commit.
```

### Tóm tắt phản hồi của AI

- `feat(review): Rules đọc reviews và test R1–R9`: đánh giá `VISIBLE` ai đăng nhập cũng đọc; người viết đọc bài của mình kể cả bị ẩn; admin đọc tất cả; client không ghi. Test viết trước, 4 test fail đúng dự kiến, sau khi thêm Rules cả bộ 52/52 qua.
- `feat(review): thêm index Firestore`: 6 index `reviews` (danh sách, lọc sao, có ảnh, chưa phản hồi, của tôi) và index collection group `favorites.venueId`.
- `feat(review): mã lỗi ReviewError và DTO`: request/response 5 action, `Throwable.toReviewError()`, chuỗi `review_error_*`, 8 unit test.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Index "Chưa phản hồi" thêm trường `status` | Dùng, sửa `data-model.md` mục 5 | Truy vấn T058 có lọc `status == VISIBLE`, thiếu trường này Firestore sẽ báo thiếu index |
| Thêm chuỗi thông báo cho từng mã lỗi | Dùng | T015 yêu cầu nhưng bản trước còn thiếu |

### Cách kiểm chứng

- `cd firestore-tests && npm test`: 52/52 test qua.
- `./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.review.*" assembleDebug`: 24/24 test qua, build được.
