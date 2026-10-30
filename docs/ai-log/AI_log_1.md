# Nhật ký sử dụng AI – Thành viên 1 (Thành Dự)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 1**. Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-05 | Viết spec 010 (đăng ký, đăng nhập) bằng `/speckit-specify` + `/speckit-clarify` | 01 |
| 2 | 2026-10-05 | Viết spec 011 (hồ sơ, quản lý tài khoản) bằng `/speckit-specify` | 01 |
| 3 | 2026-10-05 | Đối chiếu spec 010 với proposal bằng `/speckit-analyze` | 01 |
| 4 | 2026-10-05 | Viết spec 020 (tìm kiếm sân) bằng `/speckit-specify` | 02 |
| 5 | 2026-10-05 | Viết spec 022 (tìm sân thông minh – AI Assistant) bằng `/speckit-specify` | 02 |
| 6 | 2026-10-06 | Viết spec 021 (bản đồ sân, sân gần bạn) bằng `/speckit-specify` | 02 |
| 7 | 2026-10-06 | Viết spec 030 (chi tiết sân) bằng `/speckit-specify` | 03 |
| 8 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 010 bằng `/speckit-plan` | 01 |
| 9 | 2026-10-15 | Chia task spec 010 bằng `/speckit-tasks` | 01 |
| 10 | 2026-10-15 | Kiểm tra spec/plan/tasks spec 010 bằng `/speckit-analyze` | 01 |
| 11 | 2026-10-15 | Lập kế hoạch kỹ thuật spec 011 bằng `/speckit-plan` | 01 |
| 12 | 2026-10-15 | Chia task spec 011 bằng `/speckit-tasks` | 01 |
| 13 | 2026-10-15 | Kiểm tra spec/plan/tasks spec 011 bằng `/speckit-analyze` (2 lần) | 01 |
| 14 | 2026-10-19 | Lập kế hoạch kỹ thuật spec 020 bằng `/speckit-plan` | 02 |
| 15 | 2026-10-19 | Chia task spec 020 bằng `/speckit-tasks` | 02 |
| 16 | 2026-10-19 | Kiểm tra spec/plan/tasks spec 020 bằng `/speckit-analyze` | 02 |
| 17 | 2026-10-19 | Cài đặt Phase 1, 2A, 2D (và 2C) spec 010 bằng `/speckit-implement` | 01 |
| 18 | 2026-10-19 | Cài đặt Phase 2B (server, Rules) spec 010 bằng `/speckit-implement` | 01 |
| 19 | 2026-10-19 | Cài đặt US2, US4 spec 010 bằng `/speckit-implement` | 01 |
| 20 | 2026-10-20 | Cài đặt US1, US3, US5 và Polish spec 010 bằng `/speckit-implement` | 01 |
| 21 | 2026-10-25 | Lập kế hoạch kỹ thuật spec 030 bằng `/speckit-plan` | 03 |
| 22 | 2026-10-25 | Chia task spec 030 bằng `/speckit-tasks` | 03 |
| 23 | 2026-10-25 | Phân tích chéo spec 030 bằng `/speckit-analyze` và sửa theo kết quả | 03 |
| 24 | 2026-10-25 | Cài đặt Phase 1, 2A spec 011 bằng `/speckit-implement` | 01 |
| 25 | 2026-10-25 | Cài đặt Phase 2C, US1, US2 spec 011 bằng `/speckit-implement` | 01 |

---

## Mục 1: Viết spec 010 (đăng ký và đăng nhập) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (spec `specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` và `/speckit-clarify` |
| Mục đích | Sinh bản đặc tả (spec) cho tính năng đăng ký/đăng nhập từ mô tả ngắn, sau đó làm rõ các điểm còn mơ hồ |
| Nhánh Git | `feature/01-account-specs` |

### Prompt đã dùng

**Lần 1: tạo spec**

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Người dùng tạo tài khoản và đăng nhập SmashNow. Đăng ký bằng email hoặc số điện thoại, chọn mục
đích "Tôi muốn đặt sân" hoặc "Tôi là chủ sân", bắt buộc đồng ý điều khoản và chính sách quyền
riêng tư. Đăng nhập bằng email/mật khẩu, số điện thoại (OTP, dùng số thử nghiệm của Firebase)
hoặc Google. Quên mật khẩu và đặt lại qua email. Sau khi đăng ký, Edge Function on-signup gán
claim role="authenticated", appRole="PLAYER" và tạo users/{uid}; app làm mới token. Tài khoản
bị khóa (accountStatus=LOCKED) không đăng nhập được. Có xử lý mất mạng và lỗi nhập liệu.
Không bao gồm: hồ sơ, đổi mật khẩu, chuyển chế độ (spec 011); nộp hồ sơ chủ sân (spec 110).
Dữ liệu: users theo docs/design/database/README.md mục 4.1.
```

**Lần 2: làm rõ yêu cầu**

```
/speckit-clarify tôi muốn input của tôi chỉ là đăng nhập vào thôi, sau đó chia theo roles (nếu
chủ sân thì đưa vào giao diện chủ sân), nếu là user thường thì đưa vào giao diện user thường,
không cần phải chọn mục là chủ sân hay đặt sân. Ban đầu tất cả tài khoản đều là user bình
thường, khi đăng nhập vào rồi sẽ có trang profile. người dùng sẽ đăng kí là chủ sân ở đó với
các thông tin mà người đó cung cấp, sẽ có admin duyệt sau
```

Câu trả lời cho câu hỏi làm rõ của AI về xác minh email: `B`.

### Tóm tắt phản hồi của AI

- Tạo thư mục `specs/010-account-auth/` gồm `spec.md` và `checklists/requirements.md`.
- `spec.md` gồm:
  - 5 user story có độ ưu tiên: đăng ký email (P1), đăng nhập email (P1), quên mật khẩu (P2), đăng nhập Google (P2), số điện thoại + OTP (P3). Mỗi story có kịch bản chấp nhận dạng Given/When/Then.
  - Trường hợp biên: mất mạng, bấm nhiều lần, khởi tạo tài khoản lỗi giữa chừng, OTP quá số lần thử, vai trò thay đổi khi đang đăng nhập...
  - 24 yêu cầu chức năng (FR-001 → FR-024), 8 tiêu chí thành công đo được (SC-001 → SC-008), các giả định.
- AI tự kiểm tra spec theo checklist chất lượng và tự sửa 2 lỗi: chi tiết kỹ thuật lọt vào spec (tên dịch vụ, claim) và đơn vị "600dp".
- AI để lại 1 câu hỏi cần quyết định: có bắt buộc xác minh email không.
- Ở bước `/speckit-clarify`, AI cập nhật spec theo yêu cầu mới:
  - bỏ bước chọn vai trò khi đăng ký; mọi tài khoản mới là người chơi;
  - điều hướng theo vai trò sau đăng nhập (chủ sân đã duyệt → giao diện chủ sân, người chơi → giao diện người chơi, admin → quản trị);
  - đăng ký chủ sân chuyển sang trang hồ sơ (spec 011/110), chờ admin duyệt.
- AI ghi lại 2 quyết định vào mục Clarifications và đồng bộ `docs/features/01-account/README.md`, `docs/guides/w1-w2-spec-assignment.md`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Cấu trúc 5 user story và độ ưu tiên | Dùng | Đúng phạm vi nhóm 01 trong proposal; email/mật khẩu làm MVP vì không phụ thuộc dịch vụ ngoài |
| Bước chọn "Tôi muốn đặt sân / Tôi là chủ sân" khi đăng ký | **Bỏ** | Tự quyết định lại: đăng ký chỉ tạo người chơi, đăng ký chủ sân làm trong trang hồ sơ cho luồng đơn giản hơn |
| Điều hướng theo vai trò sau đăng nhập (FR-015) | **Sửa** | Ban đầu AI cho chủ sân vào chế độ người chơi mặc định; đổi thành chủ sân vào thẳng giao diện chủ sân |
| Xác minh email (FR-009) | Dùng phương án B | Không làm chậm đăng ký (mục tiêu dưới 2 phút) nhưng vẫn có trạng thái đã xác minh để dùng sau |
| Mặc định do AI đề xuất: mật khẩu ≥ 8 ký tự có chữ và số, OTP 6 số hết hạn 5 phút, chờ 60 giây gửi lại, sai 5 lần phải lấy mã mới | Dùng | Theo thông lệ, có thể đổi khi chạy `/speckit-plan` |
| Không tiết lộ email có tồn tại hay không khi đăng nhập sai / quên mật khẩu | Dùng | Tránh lộ thông tin tài khoản |
| Tiêu chí thành công SC-008 (4/5 người thử tự đăng ký được) | Dùng | Kiểm tra được bằng cách nhờ thành viên khác thử |
| Dòng **Input** gốc vẫn ghi "chọn mục đích" | Giữ, thêm ghi chú | Spec Kit giữ mô tả gốc để lưu vết; ghi chú rõ phần này đã bị bỏ |

### Cách kiểm chứng

- Đọc lại toàn bộ `spec.md`, đối chiếu với:
  - phạm vi nhóm 01 trong `docs/features/01-account/README.md` và proposal mục 5;
  - constitution (nguyên tắc II: vai trò do máy chủ cấp, người dùng không tự sửa; nguyên tắc VI: đồng ý điều khoản khi đăng ký);
  - đặc tả CSDL mục 4.1 (các trường của `users`).
- Kiểm tra checklist `specs/010-account-auth/checklists/requirements.md`: đạt 16/16 mục, không còn chỗ `[NEEDS CLARIFICATION]`.
- Kiểm tra không còn câu nào mâu thuẫn với quyết định bỏ bước chọn vai trò (tìm từ "mục đích" trong spec, chỉ còn ở dòng Input gốc và mục Clarifications).
- Bước tiếp theo: nhờ một thành viên khác review spec qua PR theo bảng review chéo ở `docs/guides/w1-w2-spec-assignment.md`.

---

## Mục 2: Viết spec 011 (hồ sơ và quản lý tài khoản) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (spec `specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả cho trang hồ sơ, đăng xuất, đăng ký chủ sân, chuyển chế độ, đổi mật khẩu, xóa tài khoản |
| Nhánh Git | `feature/01-account-specs` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/011-account-profile
Người dùng đã đăng nhập quản lý tài khoản của mình. Xem và sửa hồ sơ: ảnh đại diện (bucket
avatars của Supabase), tên, trình độ, khu vực hay chơi. Đổi mật khẩu, đăng xuất, xóa tài khoản
và dữ liệu cá nhân (Edge Function delete-account, theo Nghị định 13/2023). Chủ sân đã được duyệt
(appRole=OWNER) chuyển qua lại giữa chế độ "Người chơi" và "Quản lý sân". Người đã nộp hồ sơ chủ
sân xem trạng thái: chờ duyệt, đã duyệt, bị từ chối kèm lý do, và nút nộp lại.
Không bao gồm: màn hình nộp hồ sơ chủ sân (spec 110), màn hình quản lý sân (spec 111).
Dữ liệu: users, ownerApplications theo docs/design/database/README.md.
```

Câu trả lời cho câu hỏi làm rõ của AI về xóa tài khoản khi còn đơn sắp tới hoặc cơ sở đang hoạt động: `B` – "ưu tiên cho người dùng trước".

### Tóm tắt phản hồi của AI

- Tạo `specs/011-account-profile/spec.md` và `checklists/requirements.md`.
- 7 user story: sửa hồ sơ (P1), đăng xuất (P1), đăng ký làm chủ sân và theo dõi trạng thái (P1), chuyển chế độ người chơi / quản lý sân (P2), đổi mật khẩu (P2), xóa tài khoản (P2), đổi email (P3).
- 25 yêu cầu chức năng, trong đó có bảng hiển thị mục chủ sân theo 4 trạng thái (chưa đăng ký, chờ duyệt, bị từ chối, đã duyệt); 9 tiêu chí thành công.
- AI tự giữ cho spec 011 khớp với spec 010: "Đăng ký làm chủ sân" là lối vào duy nhất để thành chủ sân; mỗi lần mở app chủ sân vào giao diện quản lý sân; thêm đổi email vì spec 010 có hẹn "sửa email ở spec 011".
- AI hỏi 1 câu về xóa tài khoản và đề xuất phương án A (chặn xóa). Mình chọn **B**; AI cập nhật spec: cho xóa, hệ thống tự hủy đơn sắp tới theo chính sách, chủ sân thì ẩn cơ sở, hủy đơn và buổi vãng lai của khách, hoàn tiền giả lập toàn bộ và thông báo cho khách, tất cả trong một lần xử lý.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 7 user story và độ ưu tiên | Dùng | Bao đủ phạm vi nhóm 01 còn lại sau spec 010 |
| Bảng trạng thái chủ sân (FR-008) | Dùng | Khớp quyết định ở spec 010: đăng ký chủ sân từ trang hồ sơ |
| Đề xuất A của AI (chặn xóa khi còn đơn/cơ sở) | **Bỏ**, chọn B | Ưu tiên quyền xóa dữ liệu của người dùng, không bắt người dùng tự hủy từng đơn |
| Hủy đơn, ẩn cơ sở, thông báo khách khi xóa (FR-022, FR-022a) | Dùng | Hệ quả của phương án B; cần thống nhất với B, C, D vì dùng chính sách hủy, ẩn cơ sở, thông báo của họ |
| Đổi email (User Story 7) | Dùng | Spec 010 đã hẹn phần này |
| Chế độ chủ sân chỉ có hiệu lực trong phiên (FR-011) | Dùng | Giữ đúng luật "chủ sân mở app vào giao diện quản lý sân" của spec 010 |

### Cách kiểm chứng

- Đối chiếu spec 011 với spec 010 (điều hướng theo vai trò, banner xác minh email, yêu cầu mật khẩu) để không mâu thuẫn.
- Đối chiếu với constitution nguyên tắc VI (người dùng xóa được tài khoản theo Nghị định 13/2023) và đặc tả CSDL mục 4.1, 4.2.
- Checklist `specs/011-account-profile/checklists/requirements.md` đạt 16/16, không còn `[NEEDS CLARIFICATION]`.
- Việc cần làm tiếp: thống nhất với B (chính sách hủy), C (hủy lượt vãng lai), D (ẩn cơ sở, thông báo) trước `/speckit-plan`; nhờ B review spec qua PR.

---

## Mục 3: Đối chiếu spec 010 với proposal bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` (chỉ đọc) |
| Mục đích | Kiểm tra spec 010 có khớp proposal, constitution và đặc tả CSDL không |
| Nhánh Git | `feature/01-account-specs` |

### Prompt đã dùng

```
/speckit-analyze hãy check lại mô tả spec với lại proposal phần 010-account-auth
```

### Tóm tắt phản hồi của AI

- Spec 010 mới có `spec.md` (chưa có plan, tasks) nên không chạy được phần độ phủ task; AI chỉ đối chiếu spec với `Proposal.html`, constitution 1.2.0 và đặc tả CSDL mục 4.1.
- 11 phát hiện, trong đó 4 mức HIGH:
  - F1: bỏ bước chọn "Tôi muốn đặt sân / Tôi là chủ sân" nhưng proposal vẫn còn ở 4 chỗ (mục 5, sơ đồ luồng, quy trình xác minh chủ sân, bảng luồng chụp ảnh);
  - F2: khóa tài khoản chỉ mô tả hành vi trên app, thiếu yêu cầu chặn phía server (nguyên tắc II);
  - F3: cho dùng app khi chưa xác minh email + liên kết Google cùng email → Firebase có thể gỡ phương thức mật khẩu;
  - F4: OTP 5 phút, 5 lần sai không cấu hình được trong Firebase Phone Auth, không test được với số thử nghiệm.
- Các mức MEDIUM/LOW: chính sách không tiết lộ email chưa thống nhất, điều hướng chủ sân lệch với "bật chuyển Quản lý sân" của proposal, thiếu luồng liên kết số điện thoại, chính sách mật khẩu ở trang đặt lại, thiếu Splash/Onboarding, phương án email OTP, cách viết `role/ownerStatus`.
- Bảng độ phủ: 9/12 mục proposal nhóm 1 được phủ đúng.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Báo cáo 11 phát hiện | Dùng làm danh sách việc cần sửa | AI chưa sửa file nào; mình sẽ tự quyết định sửa |
| F3, F4, F8 (giới hạn của Firebase) | Cần kiểm chứng thêm | Phụ thuộc cấu hình Firebase, sẽ xác minh ở bước `/speckit-plan` |

### Cách kiểm chứng

- Mở `Proposal.html` tại các dòng AI dẫn (342, 469, 475, 488, 611, 845, 901) để xác nhận.
- Đối chiếu tài liệu Firebase Auth về liên kết tài khoản cùng email và giới hạn Phone Auth trước khi sửa FR-012, FR-016.

---

## Mục 4: Viết spec 020 (tìm kiếm sân) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (spec `specs/020-court-search`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả cho tìm kiếm sân (tên/quận, bộ lọc, sắp xếp, lịch sử) |
| Nhánh Git | `feature/02-court-search` (tạo mới từ `origin/main`) |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/020-court-search
Người chơi tìm cơ sở sân cầu lông để đặt. Màn hình tìm kiếm có ô tìm theo tên sân (gõ không dấu,
viết hoa hay thường đều khớp, khớp theo phần đầu của từ) và chọn quận/huyện từ danh mục. Bộ lọc
gồm: khoảng giá theo giờ; khung giờ còn trống (chọn ngày + giờ bắt đầu/kết thúc, chỉ hiện cơ sở có
ít nhất một sân con trống trọn khung đó); tiện ích (gửi xe, nước uống, phòng tắm, cho thuê vợt;
cơ sở phải có đủ mọi tiện ích đã chọn). Bộ lọc đang áp dụng hiện thành chip, bỏ từng chip hoặc
xóa hết được. Sắp xếp theo: gần nhất, giá thấp đến cao, đánh giá cao nhất. Thẻ kết quả gồm ảnh,
tên, quận, giá "từ X đ/giờ", điểm và số lượt đánh giá, khoảng cách (khi có vị trí); danh sách
tải thêm khi cuộn; bấm thẻ mở chi tiết sân. Chỉ hiện cơ sở đang hoạt động.
Lịch sử tìm kiếm: lưu các lần tìm gần đây của chính người dùng (từ khóa + bộ lọc), bấm để tìm
lại, xóa từng mục hoặc xóa hết; người khác không xem được.
Vị trí: xin quyền lúc người dùng chọn "gần nhất"; từ chối thì ẩn khoảng cách, không sắp xếp theo
khoảng cách được, các chức năng khác vẫn dùng bình thường.
Bộ lọc là đầu vào dùng chung: spec 022 (AI) điền vào đúng bộ lọc này rồi chạy cùng truy vấn.
Có trạng thái đang tải, rỗng (gợi ý bỏ bớt bộ lọc), lỗi kèm thử lại; mất mạng không crash.
Danh sách trang đầu tải dưới 3 giây trên 4G.
Không bao gồm: bản đồ, marker và gợi ý sân gần vị trí hiện tại (spec 021); tìm bằng câu tự do
hoặc giọng nói (spec 022); chi tiết sân (spec 030); lưới giờ và đặt sân (spec 040); tìm buổi
vãng lai (spec 080).
Dữ liệu: venues (nameKeywords, districtId, amenityIds, minPricePerHour, ratingAvg, geohash,
status), slots để xét giờ trống, districts, amenities, users/{uid}/searchHistory theo
docs/design/database/README.md.
```

Câu trả lời cho 2 câu hỏi làm rõ của AI: `Q1: B, Q2: B`.

### Tóm tắt phản hồi của AI

- Tạo `specs/020-court-search/spec.md` và `checklists/requirements.md`:
  - 4 user story: tìm theo tên/quận (P1), lọc giá + khung giờ trống + tiện ích (P1), sắp xếp và khoảng cách (P2), lịch sử tìm kiếm (P3);
  - 28 yêu cầu chức năng, 7 tiêu chí thành công, các trường hợp biên (mất mạng, gõ nhanh, khoảng giá ngược, cơ sở chưa có bảng giá...).
- AI hỏi 2 câu: giá dùng để lọc khi có khung giờ, và khách chưa đăng nhập có được tìm sân không.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Q1 – giá theo khung giờ | Chọn **B** (thay vì A: giá thấp nhất) | Giá trên thẻ phải khớp ý người dùng khi lọc "tối mai dưới 100k" |
| Q2 – khách tìm sân | Chọn **B** | Người mới xem được sân ngay, chỉ đặt sân và lịch sử mới cần đăng nhập |
| Mặc định: lịch sử 10 mục, trang 20, đặt trước 14 ngày, sắp xếp mặc định theo đánh giá | Dùng tạm | Sẽ rà lại ở `/speckit-clarify` |

### Cách kiểm chứng

- Checklist `specs/020-court-search/checklists/requirements.md` đạt 16/16, không còn `[NEEDS CLARIFICATION]`.
- Đối chiếu với constitution (nguyên tắc V: 4 trạng thái màn hình, dưới 3 giây; nguyên tắc VI: xin quyền vị trí lúc cần) và đặc tả CSDL mục 4.3, 4.4.
- Việc cần làm tiếp: thống nhất với B, D cách tính giá theo khung (để khớp spec 040); với D về quyền đọc công khai `venues`, `districts`, `amenities`, `slots` cho khách.

---

## Mục 5: Viết spec 022 (tìm sân thông minh – AI Assistant) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-05 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (spec `specs/022-ai-court-search`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh spec cho ô tìm sân bằng câu tự nhiên/giọng nói theo `docs/proposal/AI-feature.md` |
| Nhánh Git | `feature/02-court-search` |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/022-ai-court-search
Người chơi tìm sân bằng một câu tiếng Việt tự nhiên, gõ hoặc nói (giọng nói xin quyền micro lúc
bấm; từ chối thì vẫn gõ được). Ví dụ "tối mai 7h-9h sân gần Quận 10 dưới 100k có gửi xe", kể cả
viết tắt, không dấu ("q10 duoi 100k co gui xe"). AI phân loại ý định trong một lần gọi:
- search: trích bộ lọc gồm quận, ngày, giờ bắt đầu/kết thúc, giá tối đa, tiện ích, loại (sân
  thường hay buổi vãng lai). Hiểu thời gian đời thường theo ngày giờ hiện tại, múi giờ
  Asia/Ho_Chi_Minh ("mai", "cuối tuần này", "tối" = 18:00–22:00). Kết quả hiện thành chip bộ lọc
  để người dùng sửa trước khi tìm, rồi chạy truy vấn của spec 020.
- unclear: hỏi lại đúng 1 câu kèm chip chọn nhanh (ví dụ [Tối nay] [Tối mai] [Cuối tuần]); bấm
  chip thì app tự điền, không gọi AI lần hai; vẫn thiếu thì chuyển sang bộ lọc thủ công với phần
  đã hiểu được điền sẵn.
- faq: hiện nội dung chính sách có sẵn theo chủ đề (hủy sân, đặt cọc, voucher, thanh toán), không
  do AI viết.
- off_topic: hiện thông báo cố định "chỉ hỗ trợ tìm và đặt sân" kèm câu ví dụ. Câu pha trộn thì
  chỉ lấy phần tìm sân.
Kết quả AI luôn được kiểm tra trước khi dùng: quận và tiện ích phải có trong danh mục, giờ và
giá hợp lệ, trường lạ bị bỏ; sai thì xử lý như unclear. AI không trả lời tự do, không tự đặt sân,
không đọc hay ghi dữ liệu nghiệp vụ; câu cố bẻ prompt chỉ cho ra bộ lọc sai hoặc off_topic.
Giới hạn: câu rỗng hoặc quá 200 ký tự bị chặn ngay trên app; mỗi người tối đa N lượt mỗi ngày
và app hiện số lượt còn lại; 3 câu lạc đề liên tiếp thì tạm khóa ô AI vài phút, vẫn dùng bộ lọc
thủ công được; cùng một câu trong ngày dùng lại kết quả cũ. Số điện thoại và email trong câu bị
che trước khi gửi; không gửi tên hay thông tin tài khoản. AI lỗi, quá thời gian hoặc mất mạng
thì báo ngắn và quay về bộ lọc thủ công, giữ nguyên câu đã nhập. Có câu ví dụ gợi ý dưới ô nhập.
Không có kết quả thì gợi ý nới điều kiện (tăng giá, đổi giờ) bằng truy vấn lại, không gọi AI.
Ghi số liệu không kèm thông tin cá nhân (intent, kiểm tra hợp lệ hay không, độ trễ) để đo tỉ lệ
trích đúng trên bộ 20–30 câu mẫu và 10–15 câu ngoại lệ.
Không bao gồm: bộ lọc thủ công, danh sách kết quả, lịch sử tìm kiếm (spec 020); bản đồ (spec
021); danh sách buổi vãng lai (spec 080, AI chỉ điền bộ lọc rồi chuyển sang); nội dung chính
sách (lấy từ spec 050, 060); chọn nhà cung cấp LLM (họp 09/10).
Dữ liệu và quy tắc: docs/proposal/AI-feature.md; districts, amenities, venues,
users/{uid}/aiUsage, aiSearchCache theo docs/design/database/README.md; Edge Function
ai-search-parse theo constitution v1.2.0.
```

Câu trả lời cho 2 câu hỏi làm rõ của AI: `Q1: A, Q2: A`.

### Tóm tắt phản hồi của AI

- Tạo `specs/022-ai-court-search/spec.md` và `checklists/requirements.md`:
  - 6 user story: tìm bằng câu tự nhiên (P1), câu thiếu thông tin / không hiểu được (P1), hỏi chính sách và lạc đề (P2), nhập giọng nói (P2), giới hạn lượt và quay về bộ lọc thủ công (P2), gợi ý nới điều kiện khi không có kết quả (P3);
  - 25 yêu cầu chức năng, 8 tiêu chí thành công (trích đúng toàn bộ ≥ 85% câu mẫu, 100% câu ngoại lệ vào đúng nhánh, 0 số điện thoại/email lọt ra ngoài...).
- AI dùng lại các quyết định đã chốt ở spec 020 (bộ lọc dùng chung, giá theo khung giờ, bước 30 phút, đặt trước 14 ngày) và tự đặt mặc định cho các buổi trong ngày, "7h-9h" hiểu là buổi tối, tạm khóa 5 phút, chờ 10 giây.
- AI hỏi 2 câu: số lượt AI mỗi ngày (N), và khách chưa đăng nhập có được dùng ô AI không.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Q1 – số lượt mỗi ngày | Chọn **A: 10 lượt/ngày** | An toàn nhất cho gói miễn phí; kiểm thử dùng nhiều tài khoản thử nghiệm |
| Q2 – khách dùng ô AI | Chọn **A: phải đăng nhập** | Giới hạn lượt gắn chắc với tài khoản, khó lạm dụng; khách vẫn dùng bộ lọc thủ công |
| Mặc định thời gian ("sáng/trưa/chiều/tối", "7h-9h" = tối), tạm khóa 5 phút, chờ 10 giây, bậc nới giá 20.000 đ | Dùng tạm | Sẽ rà lại ở `/speckit-clarify` |
| Dòng **Input** gốc vẫn ghi "N lượt" | Giữ | Spec Kit giữ mô tả gốc để lưu vết; giá trị đã chốt nằm ở Clarifications và FR-016 |

### Cách kiểm chứng

- Checklist `specs/022-ai-court-search/checklists/requirements.md` đạt 16/16, không còn `[NEEDS CLARIFICATION]`.
- Đối chiếu với `docs/proposal/AI-feature.md` (workflow, 4 ý định, các lớp chặn, chống prompt injection) và dòng "AI Assistant (LLM)" trong constitution 1.2.0 (không gửi tên/email/SĐT, AI không ghi dữ liệu nghiệp vụ).
- Hai spec 020 và 022 được commit chung (`b08bd6a`) trên nhánh `feature/02-court-search`.
- Việc cần làm tiếp: thống nhất với C (bộ lọc buổi vãng lai), B (chủ đề chính sách), D (khoảng giá hợp lệ); spec 010 cần luồng "đăng nhập rồi quay lại màn hình trước".

---

## Mục 6: Viết spec 021 (bản đồ sân và sân gần bạn) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (spec `specs/021-court-map`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả cho chế độ bản đồ (marker quả cầu lông, cụm, thẻ tóm tắt), "Vị trí của tôi" và mục "Sân gần bạn" |
| Nhánh Git | `feature/02-court-map` (tạo mới từ `origin/main`) |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/021-court-map
Người chơi (kể cả khách chưa đăng nhập) xem các cơ sở sân cầu lông trên bản đồ và tìm sân gần mình.
Chế độ bản đồ: trên màn hình tìm kiếm của spec 020 có nút chuyển "Danh sách / Bản đồ"; hai chế độ
dùng chung một bộ lọc (từ khóa, quận/huyện, khoảng giá, khung giờ còn trống, tiện ích), đổi chế độ
không mất bộ lọc. Mỗi cơ sở đang hoạt động khớp bộ lọc hiện thành một marker hình quả cầu lông tại
vị trí của cơ sở; nhiều marker gần nhau thì gộp thành một cụm có số lượng, phóng to thì tách ra.
Chạm vào marker: marker được làm nổi bật và hiện thẻ tóm tắt ở cuối màn hình (ảnh, tên, quận, giá
"từ X đ/giờ" hoặc giá của khung đã chọn theo cách tính của spec 020, điểm và số lượt đánh giá,
khoảng cách nếu có vị trí); vuốt ngang thẻ để chuyển sang cơ sở kế bên và bản đồ di theo; chạm
thẻ mở chi tiết sân (spec 030), mang theo ngày và khung giờ đã chọn. Khi kéo hoặc phóng bản đồ sang
vùng khác, hiện nút "Tìm ở khu vực này" để tải cơ sở trong vùng đang xem, không tự tải lại liên
tục.
Vị trí của tôi: nút "Vị trí của tôi" đưa bản đồ về vị trí hiện tại và hiện chấm vị trí; xin quyền
vị trí lúc người dùng bấm nút hoặc mở mục "Sân gần bạn" lần đầu, có giải thích trước. Từ chối quyền
hoặc tắt định vị thì bản đồ mở ở khu vực mặc định, mọi chức năng khác vẫn dùng được.
Sân gần bạn: một mục gợi ý các cơ sở đang hoạt động gần vị trí hiện tại nhất, xếp theo khoảng cách,
hiện khoảng cách và nút "Xem tất cả trên bản đồ"; không có cơ sở nào trong bán kính gợi ý thì báo
và gợi ý mở rộng tìm kiếm. Vị trí chỉ dùng trên thiết bị, không lưu lên máy chủ.
Có trạng thái đang tải marker, không có cơ sở nào trong vùng hoặc khớp bộ lọc (gợi ý bỏ bớt bộ lọc
hoặc thu nhỏ bản đồ), lỗi kèm thử lại. Bản đồ không tải được (mất mạng, dịch vụ bản đồ lỗi) thì
báo và chuyển về chế độ danh sách của spec 020, không crash. Marker trong vùng đang xem hiện trong
dưới 3 giây trên 4G. Hiển thị đúng trên điện thoại và máy tính bảng (máy tính bảng có thể đặt danh
sách và bản đồ cạnh nhau), chế độ sáng và tối; marker và nút có mô tả cho trình đọc màn hình.
Không bao gồm: ô tìm kiếm, bộ lọc, sắp xếp và lịch sử (spec 020); tìm bằng câu tự do (spec 022);
bản đồ nhúng và chỉ đường trong chi tiết sân (spec 030); buổi vãng lai trên bản đồ (spec 080); chủ
sân ghim vị trí cơ sở (spec 110, 111).
Dữ liệu: venues (location, geohash, status, name, photoPaths, districtId, amenityIds,
minPricePerHour, ratingAvg, ratingCount), districts, cùng quy tắc giờ trống và giá theo khung của
spec 020 (slots, priceRules) theo docs/design/database/README.md.
```

Câu trả lời cho 2 câu hỏi làm rõ của AI: `Q1: A`, `Q2: Mình muốn nó ở bên dưới thanh tìm kiếm`.

### Tóm tắt phản hồi của AI

- Tạo `specs/021-court-map/spec.md` và `checklists/requirements.md`:
  - 4 user story: xem sân trên bản đồ và mở chi tiết (P1), duyệt nhiều sân bằng cụm marker, vuốt thẻ, "Tìm ở khu vực này" (P2), vị trí của tôi (P2), sân gần bạn (P2);
  - 24 yêu cầu chức năng, 7 tiêu chí thành công (marker hiện dưới 3 giây, tập cơ sở trên bản đồ trùng 100% với danh sách của spec 020, kéo/phóng 1 phút không tự tải lại lần nào...);
  - trường hợp biên: bản đồ lỗi thì về danh sách, cơ sở thiếu vị trí, nhiều cơ sở cùng tọa độ, AI điền bộ lọc khi đang ở bản đồ, người chơi đang di chuyển, máy tính bảng.
- AI tự thêm: giới hạn 100 cơ sở mỗi lần tải (giữ trong hạn mức miễn phí), bỏ bộ lọc quận khi "Tìm ở khu vực này" ở vùng khác, mặc định bán kính 5 km và tối đa 10 sân cho "Sân gần bạn".
- AI hỏi 2 câu: khu vực mặc định khi không có vị trí, và mục "Sân gần bạn" đặt ở đâu.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 4 user story và độ ưu tiên | Dùng | Bao đủ phạm vi spec 021 trong bảng phân công |
| Q1 – khu vực mặc định | Chọn **A: trung tâm TP.HCM** | Đơn giản, không cần thêm tọa độ tâm quận vào CSDL |
| Q2 – vị trí "Sân gần bạn" | **Tự trả lời: ngay dưới thanh tìm kiếm** (không chọn A/B/C của AI) | Người chơi thấy ngay khi vào màn hình tìm kiếm, không phụ thuộc trang chủ chưa có spec |
| Mục thu gọn khi bắt đầu tìm hoặc mở lịch sử (FR-015a) | Dùng | AI thêm để không tranh chỗ với kết quả và lịch sử của spec 020 |
| Mặc định 5 km, 10 sân, 100 cơ sở mỗi lần tải, chờ vị trí 10 giây | Dùng tạm | Rà lại ở `/speckit-clarify` |

### Cách kiểm chứng

- Checklist `specs/021-court-map/checklists/requirements.md` đạt 16/16, không còn `[NEEDS CLARIFICATION]`.
- Đối chiếu với spec 020 (bộ lọc dùng chung, giá theo khung, quy tắc khoảng cách, khách được tìm sân), spec 030 (mở chi tiết mang theo ngày và khung giờ), constitution nguyên tắc V và VI (4 trạng thái, xin quyền vị trí lúc cần, không lưu vị trí).
- Việc cần làm tiếp: spec 020 chừa chỗ cho "Sân gần bạn" dưới thanh tìm kiếm; chốt dịch vụ bản đồ (Google Maps hay osmdroid) ở buổi họp 09/10.

---

## Mục 7: Viết spec 030 (chi tiết sân) bằng `/speckit-specify`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-06 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 03 – Chi tiết sân (spec `specs/030-court-detail`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-specify` |
| Mục đích | Sinh bản đặc tả cho màn hình chi tiết cơ sở: ảnh, bảng giá, tiện ích, giờ mở cửa, liên hệ, bản đồ, đánh giá tổng quan, nút Đặt sân |
| Nhánh Git | `feature/03-court-detail` (tạo mới từ `origin/main`) |

### Prompt đã dùng

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/030-court-detail
Người chơi (kể cả khách chưa đăng nhập) xem chi tiết một cơ sở sân cầu lông trước khi đặt. Vào từ
danh sách kết quả (spec 020), thẻ trên bản đồ (spec 021) hoặc danh sách sân yêu thích (spec 070).
Màn hình gồm:
- Thư viện ảnh: vuốt ngang, bấm để xem toàn màn hình và phóng to; không có ảnh thì hiện ảnh mặc định.
- Thông tin chính: tên, địa chỉ, quận/huyện, điểm trung bình và số lượt đánh giá.
- Bảng giá theo khung giờ và thứ trong tuần (giờ thường, giờ cao điểm, cuối tuần), hiển thị theo
  đ/giờ; chưa có bảng giá thì hiện "Liên hệ". Nếu người chơi đến từ tìm kiếm có chọn ngày và khung
  giờ thì làm nổi bật giá của đúng khung đó, cách tính giống spec 020 và spec 040.
- Tiện ích (lấy tên và biểu tượng từ danh mục), số sân con đang hoạt động và loại mặt sân (gỗ, PU,
  thảm).
- Giờ mở cửa kèm trạng thái "Đang mở cửa" / "Đã đóng cửa" theo giờ Việt Nam; số điện thoại liên hệ,
  bấm vào thì mở trình gọi điện của máy.
- Bản đồ nhúng có marker quả cầu lông tại vị trí cơ sở; nút "Chỉ đường" mở ứng dụng bản đồ (không
  có thì mở trình duyệt). Không cần quyền vị trí để xem bản đồ.
- Đánh giá tổng quan: điểm trung bình, số lượt, điểm trung bình từng tiêu chí (chất lượng sân, vệ
  sinh, thái độ phục vụ), 3 đánh giá mới nhất đang hiển thị, nút "Xem tất cả" mở danh sách đầy đủ
  của spec 070; chưa có đánh giá thì hiện "Sân chưa có đánh giá".
- Nút trái tim yêu thích (hành vi theo spec 070), nút "Chat với chủ sân" (mở hội thoại của spec
  091) và nút "Đặt sân" luôn hiển thị ở cuối màn hình. "Đặt sân" mở lưới giờ của spec 040, mang
  theo ngày và khung giờ đã chọn ở tìm kiếm nếu có.
Khách chưa đăng nhập xem được mọi thông tin. Bấm "Đặt sân", trái tim hoặc "Chat với chủ sân" thì
mời đăng nhập; đăng nhập xong quay lại đúng màn hình này và tiếp tục thao tác vừa bấm.
Cơ sở bị chủ sân ẩn hoặc bị admin khóa: hiện thông tin kèm ghi chú "Sân tạm ngừng hoạt động", ẩn
nút "Đặt sân" và "Chat với chủ sân", vẫn bỏ yêu thích được. Cơ sở không còn tồn tại: hiện màn hình
"Sân không còn tồn tại" kèm nút quay lại.
Có trạng thái đang tải, lỗi kèm thử lại; mất mạng thì hiện dữ liệu đã tải trước đó (nếu có) kèm
thông báo, không crash. Phần đầu màn hình (ảnh, tên, giá, nút Đặt sân) hiện trong dưới 3 giây trên
4G; bản đồ và đánh giá được phép tải sau. Hiển thị đúng trên điện thoại và máy tính bảng, chế độ
sáng và tối.
Không bao gồm: lưới giờ và đặt sân (spec 040); danh sách đầy đủ, viết đánh giá và logic yêu thích
(spec 070); nội dung chat (spec 091); buổi vãng lai của cơ sở (spec 080); chủ sân sửa thông tin
cơ sở, bảng giá, sân con (spec 111); bản đồ tìm sân (spec 021).
Dữ liệu: venues (name, address, districtId, location, openTime, closeTime, phone, amenityIds,
photoPaths, ratingAvg, ratingCount, ratingSums, status), venues/{id}/courts (surfaceType,
isActive), venues/{id}/priceRules, reviews (status VISIBLE, mới nhất), amenities, districts theo
docs/design/database/README.md.
```

Câu trả lời cho câu hỏi làm rõ của AI: `Q1: A`.

### Tóm tắt phản hồi của AI

- Tạo `specs/030-court-detail/spec.md` và `checklists/requirements.md`:
  - 6 user story: xem thông tin và bấm Đặt sân (P1), bảng giá theo khung giờ (P1), vị trí, chỉ đường và liên hệ (P2), đánh giá tổng quan (P2), khách chưa đăng nhập và thao tác cần tài khoản (P2), cơ sở tạm ngừng hoặc không còn tồn tại (P3);
  - 27 yêu cầu chức năng, 7 tiêu chí thành công (phần đầu màn hình dưới 3 giây, giá "Khung bạn chọn" khớp 100% với spec 020 và 040, khách đăng nhập xong được tiếp tục thao tác...);
  - trường hợp biên: giờ mở qua nửa đêm, mở 24 giờ, không có sân con hoạt động, nhiều loại mặt sân, thiếu số điện thoại hoặc vị trí, khung giờ từ tìm kiếm đã qua, một mục tải lỗi.
- AI dùng lại các quyết định đã chốt ở spec 020 (khách được xem sân, giá theo đúng khung giờ) và spec 070 (trái tim, 3 đánh giá mới nhất, "Xem tất cả", cơ sở tạm ngừng); tự thêm quy tắc chủ sân không đặt sân hoặc chat với cơ sở của chính mình.
- AI hỏi 1 câu: số điện thoại của cơ sở có hiện cho khách chưa đăng nhập không.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 6 user story và độ ưu tiên | Dùng | Bao đủ phạm vi nhóm 03 trong proposal mục 5 |
| Q1 – số điện thoại cho khách | Chọn **A: công khai cho mọi người** | Là thông tin kinh doanh của cơ sở, giống các app đặt sân khác; spec 111 cần báo cho chủ sân biết |
| Chủ sân không đặt sân/chat với cơ sở của mình (FR-018) | Dùng | Khớp quy tắc chủ sân không tự đánh giá sân mình ở spec 070 |
| Mặc định: giờ mở cửa giống nhau mọi ngày, không có nút chia sẻ, không hiện buổi vãng lai của cơ sở | Dùng tạm | Theo đặc tả CSDL hiện tại; rà lại ở `/speckit-clarify` |

### Cách kiểm chứng

- Checklist `specs/030-court-detail/checklists/requirements.md` đạt 16/16, không còn `[NEEDS CLARIFICATION]`.
- Đối chiếu với phạm vi nhóm 03 (`docs/features/03-court-detail/README.md`, proposal mục 5), constitution nguyên tắc V (4 trạng thái, dưới 3 giây, điện thoại và `w600dp`, accessibility) và đặc tả CSDL mục 4.3 (`venues`, `courts`, `priceRules`).
- Đối chiếu điểm nối với spec 070 (`contracts/ui-screens.md`: trái tim, tổng quan, "Xem tất cả") và spec 020 (FR-017, giá theo khung giờ).
- Việc cần làm tiếp: D xác nhận quyền đọc công khai `venues`, `courts`, `priceRules`, `reviews` và đọc theo ID cơ sở tạm ngừng; B ↔ D chốt cách tính giá theo khung; C xác nhận lối vào chat riêng với chủ sân (spec 091).

---

## Mục 8: Lập kế hoạch kỹ thuật spec 010 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan`; tra tài liệu Firebase qua MCP `developerknowledge` |
| Mục đích | Lập kế hoạch kỹ thuật cho đăng ký/đăng nhập, chốt phần `core/` dùng chung cả nhóm, kiểm chứng các giới hạn Firebase còn treo từ lần analyze (F3, F4, F8) |
| Nhánh Git | `feature/01-account-plan` (tạo mới từ `main`) |

### Prompt đã dùng

```
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Lập kế hoạch kỹ thuật cho spec 010 theo constitution 1.2.0 (MVVM, một Activity + Navigation,
Hilt, StateFlow; Firebase gói Spark, Supabase gói Free) và đặc tả CSDL chung (mục 4.1 users,
mục 8a on-signup). Đây là spec nền nên phải chốt rõ phần core/ mà các tính năng khác dùng lại.
Lưu ý:
- Gói Spark không có trigger Auth (onCreate/blocking functions) nên on-signup không tự chạy.
  App phải gọi Edge Function on-signup sau khi tạo tài khoản, gửi kèm Firebase ID token. Function
  xác minh token, gán custom claim role="authenticated", appRole="PLAYER" (cần cho Third-party
  Auth Supabase ở PR #23), tạo users/{uid} theo mục 4.1 (role, ownerStatus=NONE,
  accountStatus=ACTIVE, walletBalance=0, termsAcceptedAt). Sau đó app gọi
  getIdToken(true). Function phải idempotent để xử lý edge case "tạo tài khoản xong nhưng khởi
  tạo lỗi" (màn hình chờ + thử lại, tự hoàn tất ở lần mở app sau).
- Google/OTP lần đầu chỉ tạo users/{uid} sau bước đồng ý điều khoản. Đóng app giữa chừng thì
  quay lại đúng bước đó. Nêu rõ app nhận biết "chưa khởi tạo" bằng claim hay document.
- Khóa tài khoản (FR-014): so sánh hai cách, chỉ dùng accountStatus=LOCKED hoặc kết hợp
  disabled của Firebase Auth do Edge Function của admin (spec 120, D) đặt. Chọn một cách và ghi
  rõ D cần làm gì.
- Điều hướng theo vai trò (FR-015) đọc từ custom claim appRole, không đọc từ document. Đặt
  AuthRepository/SessionManager ở core/ để các feature khác dùng qua interface.
- Kiểm chứng giới hạn Firebase còn treo từ analyze (F3, F4, F8): liên kết tài khoản cùng
  email Google và email/mật khẩu (FR-012, cài đặt "one account per email"), thời hạn và số lần
  sai OTP của Phone Auth có cấu hình được không (FR-016), số điện thoại thử nghiệm, rate limit
  gửi email đặt lại mật khẩu. Chỗ nào Firebase không cho chỉnh thì ghi rõ để sửa spec.
- Đăng nhập Google dùng Credential Manager. Email xác minh và đặt lại mật khẩu dùng luồng có
  sẵn của Firebase. Không lưu mật khẩu trên máy.
- Security Rules cho users/{uid}: chủ tài khoản không được tạo document và không được sửa các
  trường 🔒. Kèm test trên Emulator (dựa trên bộ test ở PR #22).
- UI theo các ảnh trong specs/010-account-auth/ui (7 bước, có chế độ tối).
Ghi rõ những thay đổi cần cập nhật vào docs/design/database/README.md và việc cần B, C, D
phối hợp (D: khóa tài khoản, duyệt chủ sân thì gán appRole=OWNER và bắt app làm mới token).
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:**
  - `plan.md`
  - `research.md` (14 quyết định R1–R14)
  - `data-model.md`
  - `contracts/edge-function-on-signup.md` (kèm `_shared/auth.ts`, `_shared/identity.ts`, test S1–S12)
  - `contracts/security-rules.md` (test U1–U18)
  - `contracts/ui-screens.md`
  - `quickstart.md` (kịch bản thủ công M1–M25)
- **`on-signup`:** app gọi sau khi xác thực và đồng ý điều khoản. Function ghi `users/{uid}` **trước**, gộp claim **sau**. Function idempotent: dùng điều kiện `exists=false` khi tạo, và không bao giờ hạ `appRole`. Function đặt `verify_jwt = false` và tự xác minh token bằng `jose`, vì lúc đó token chưa có claim `role`.
- **"Chưa khởi tạo"** = token chưa có claim `appRole`:
  - người đăng ký email (đã đồng ý trên form, lưu tạm `PendingSignup` trong DataStore) thấy màn hình chờ, app tự gọi lại;
  - người đăng nhập Google/OTP lần đầu vào màn hình Hoàn tất tài khoản.
- **Khóa tài khoản:** dùng `accountStatus = LOCKED` kèm cờ `disabled` của Firebase Auth. Cả hai do Edge Function mới `set-account-status` (D) ghi. Firebase tự chặn mọi phương thức đăng nhập và làm mới phiên. Admin không sửa `accountStatus` qua Rules nữa.
- **Điều hướng** đọc claim `appRole`. Listener `users/{uid}` phát hiện vai trò đổi hoặc tài khoản bị khóa khi đang mở app. Vì vậy D phải ghi claim trước rồi mới ghi document.
- **Phần `core/` dùng chung:** `SessionManager`, `IdTokenProvider`, `SupabaseClient` (Third-party Auth), `AppError` với timeout 15 giây, `UserProfileReader`, banner xác minh email. Phía server: `_shared/auth.ts`, `_shared/identity.ts`.
- **Kiểm chứng giới hạn Firebase** (tài liệu chính thức):
  - Gói Spark không gửi được SMS thật; chỉ dùng số thử nghiệm, tối đa 10 số.
  - Thời hạn OTP và số lần nhập sai không cấu hình được.
  - Email `@gmail.com` có mật khẩu nhưng chưa xác minh sẽ bị Google ghi đè (mất mật khẩu, giữ `uid`). Email ngoài gmail thì báo lỗi, nên cần thêm hộp thoại liên kết tài khoản.
  - Password policy không có lựa chọn "có chữ cái bất kỳ".
  - Hạn mức mỗi ngày: 150 email đặt lại mật khẩu, 1000 email xác minh.
- **Thay đổi ảnh hưởng cả nhóm:**
  - Rules nền: `appRole()` không còn mặc định `PLAYER`, thêm `isInitialized()`.
  - `users` chỉ chủ tài khoản và admin đọc được.
  - `users.level` đổi thành `String?`; `termsAcceptedAt` thành 🔒.
- **Constitution Check:** đạt cả 7 nguyên tắc, không có vi phạm.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| App gọi `on-signup`, ghi document trước, claim sau | Dùng | Spark không có trigger; thứ tự này đảm bảo có claim thì chắc chắn có document |
| Nhận biết "chưa khởi tạo" bằng claim `appRole` | Dùng | Không tốn lượt đọc Firestore; Rules và Edge Function cũng kiểm tra đúng claim này |
| Khóa bằng `accountStatus` + `disabled` qua `set-account-status` | Chờ xác nhận | Cần D (spec 120) đồng ý viết function và bỏ quyền sửa `accountStatus` qua Rules |
| Chặn người khác đọc `users/{uid}` | Chờ xác nhận | Đúng nguyên tắc VI (document chứa email, số điện thoại, số dư ví); cần cả nhóm xác nhận không ai đọc hồ sơ người khác |
| Sửa Rules nền `appRole()`, thêm `isInitialized()` | Chờ xác nhận | Ảnh hưởng Rules của mọi người; báo nhóm trước khi merge |
| 5 phút / 5 lần sai OTP do app thực thi | Dùng | Firebase không cho cấu hình; sẽ ghi rõ vào spec (R14) |
| Hộp thoại liên kết tài khoản (không có trong wireframe) | Dùng | Cần cho FR-012 với email Google ngoài gmail |
| Các sửa đổi spec trong R14 (FR-012, FR-016, FR-018, FR-019) | Chờ thực hiện | Sửa `spec.md` trước `/speckit-tasks` |

### Cách kiểm chứng

- Đối chiếu Constitution Check với từng nguyên tắc I–VII trong `.specify/memory/constitution.md`.
- Mở lại tài liệu Firebase mà AI dẫn (Auth Limits, Email enumeration protection, Verified email addresses, Phone Auth Android, Password policy) để xác nhận các giới hạn ở R7–R9.
- Chạy các kịch bản M9, M10 (liên kết Google), M17 (password policy), M20 (tài khoản bị khóa nhập sai mật khẩu) trong `quickstart.md` để kiểm tra những chỗ AI chỉ suy ra từ tài liệu.
- Đối chiếu mọi FR trong spec với ít nhất một quy tắc trong data-model/contract hoặc một kịch bản trong `quickstart.md`.
- Việc cần làm tiếp: chốt với D (`set-account-status`, thứ tự ghi claim), B (`_shared/firestore.ts`) và C (`_shared/auth.ts`, `level` nullable) ở họp 16/10; cập nhật đặc tả CSDL theo danh sách cuối `data-model.md`; sửa spec theo R14.

## Mục 9: Chia task spec 010 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 010 thành task theo user story, tách riêng PR `chore(core)` dùng chung cả nhóm, ghi rõ phụ thuộc với B, C, D |
| Nhánh Git | `feature/01-account-plan` |

### Prompt đã dùng

```text
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Chia task theo user story, viết test trước (phải fail) rồi mới cài đặt. Yêu cầu:
- Phase Foundational là PR `chore(core)` dùng chung cả nhóm: Hilt, core/session (SessionManager, IdTokenProvider,
  UserProfileReader), core/network (AppError, awaitWithTimeout 15s), core/di, DataStore, Credentials, Safe Args,
  nav_main + 5 nav graph, LauncherFragment. Tách riêng để nhóm review và merge sớm.
- Server: _shared/auth.ts, _shared/identity.ts, Edge Function on-signup với test S1–S12 trên Auth/Firestore Emulator,
  có ca gọi on-signup nhiều lần liên tiếp và đồng thời (idempotent).
- Rules users/{uid} và sửa Rules nền (appRole() bỏ mặc định PLAYER, thêm isInitialized()), có test U1–U18.
- Unit test cho các rule kiểm tra dữ liệu nhập, UseCase, ViewModel và SessionManager (điều hướng theo claim appRole,
  tài khoản bị khóa, vai trò đổi khi đang mở app).
- Ghi rõ task phụ thuộc B (_shared/firestore.ts), C (dùng _shared/auth.ts), D (set-account-status, mergeClaims trước
  khi ghi role), kèm phương án tạm nếu các bạn chưa xong.
- Có task cập nhật docs/design/database/README.md theo data-model.md và task chạy các kịch bản thủ công trong
  quickstart.md (M9, M10, M17, M20).
```

### Tóm tắt phản hồi của AI

- Trước khi chạy, AI nhắc `spec.md` chưa sửa theo R14 của plan. Mình đồng ý cho sửa FR-012, FR-016, FR-019 (giới hạn OTP và password policy do app thực thi) và thêm hạn mức email vào Assumptions.
- Sinh `tasks.md` với **79 task** (T001–T079), 41 task `[P]`:

| Phase | Nội dung | Số task |
|---|---|---|
| 1 | Setup: thư viện, Hilt, cấu hình Firebase Console | 5 |
| 2A | PR `chore(core)`: `SessionManager`, `IdTokenProvider`, `UserProfileReader`, `AppError`, DataStore, nav graph, `LauncherFragment` | 13 |
| 2B | Server: `_shared/auth.ts`, `_shared/identity.ts`, `on-signup` (S1–S12), Rules U1–U18, `grant-role.ts` | 10 |
| 2C | Domain auth: kiểm tra dữ liệu nhập, ánh xạ lỗi, `CompleteSignupUseCase`, `strings_auth.xml` | 7 |
| 3–7 | US1 (12), US2 (6), US3 (4), US4 (9), US5 (7) | 38 |
| 8 | Polish | 6 |

- Test idempotent T024: gọi `on-signup` 5 lần liên tiếp và 10 lần song song, chỉ tạo đúng 1 document.
- Có bảng phụ thuộc B, C, D kèm phương án tạm (A viết bản tối thiểu `_shared/firestore.ts` ghi `TODO(B)`, khóa tài khoản bằng tay khi D chưa có `set-account-status`, dùng `grant-role.ts` giả lập cấp quyền chủ sân).
- Đề xuất MVP: Phase 1, 2A, 2B, 2C cùng US1 và US2; mở PR `chore(core)` ngay sau 2A.
- `ConsentPrefill` đặt ở `core/session` chứ không ở `feature/auth/domain/model` như data-model, vì `core` không được import `feature`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Chia 2A / 2B / 2C, PR `chore(core)` riêng | Dùng | Cả nhóm đang chờ phần dùng chung |
| Bảng phụ thuộc kèm phương án tạm | Dùng | Không bị chặn khi B, C, D chưa xong |
| Sửa `spec.md` theo R14 trước khi chia task | Dùng | Tránh sinh task theo câu chữ Firebase không làm được |
| Đặt `ConsentPrefill` ở `core/session` | Dùng | Đúng ranh giới module của constitution I |

### Cách kiểm chứng

- Kiểm tra định dạng checkbox, ID T001–T079 liên tục, mỗi task có nhãn story và đường dẫn file.
- Đối chiếu mỗi yêu cầu trong prompt với task tương ứng (T024 idempotent, T074 đặc tả CSDL, T076 kịch bản thủ công).
- Chạy `/speckit-analyze` ngay sau đó (mục 10).

## Mục 10: Kiểm tra độ khớp spec/plan/tasks spec 010 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Soát `spec.md`, `plan.md`, `tasks.md` với constitution trước khi code |
| Nhánh Git | `feature/01-account-plan` |

### Prompt đã dùng

```text
/speckit-analyze
```

### Tóm tắt phản hồi của AI

- Kết quả: **1 CRITICAL, 3 HIGH, 8 MEDIUM, 6 LOW**. Độ phủ 30/31 yêu cầu (97%), thiếu SC-003 (đăng nhập ≤ 3 giây).
- **C1 (CRITICAL):** constitution II bắt buộc đọc khóa qua Secrets Gradle Plugin, nhưng T002 đọc tay `local.properties` vào `BuildConfig`.
- **H1:** kịch bản thủ công M1–M25 chạy trên project thật, trái nguyên tắc IV.
- **H2:** `verifyPhoneNumber` cần `Activity` nhưng chưa có thiết kế đưa `Activity` xuống data source (đề xuất `ActivityProvider`).
- **H3:** chưa có test khóa tài khoản cho luồng Google và OTP.
- MEDIUM tiêu biểu: banner xác minh email ở `core/` phụ thuộc ngược vào `feature/` (M1), chưa test Repository (M2), tên khóa Supabase khác với spec 070 (M4), chưa có cách thay JWKS giả cho test S8 (M5).
- Theo yêu cầu "Sửa C1 trước", AI sửa T001, T002, T003, T015, `plan.md` và `quickstart.md` để dùng Secrets Gradle Plugin cùng file `local.defaults.properties` (giá trị giả, được commit).

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| C1: dùng Secrets Gradle Plugin | Dùng, đã sửa | Bắt buộc theo constitution II |
| H1, H2, H3 và các mục MEDIUM | Chưa sửa lúc đó | Chuyển sang làm plan 011; H2 sau này giải quyết bằng `CurrentActivityProvider` ở 011 |
| M4 (tên khóa Supabase) | Chờ xác nhận | Chốt với C ở họp 16/10 |

### Cách kiểm chứng

- Đọc lại nguyên tắc II, IV trong `.specify/memory/constitution.md` để xác nhận C1, H1.
- Mở `tasks.md` T001–T003, T015 sau khi sửa C1, kiểm tra không còn đọc `Properties()` trong Gradle.
- Việc kiểm tra plugin chạy với AGP 9.4 để lại cho lúc implement (T002).

## Mục 11: Lập kế hoạch kỹ thuật spec 011 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan`; tra tài liệu Firebase, Supabase |
| Mục đích | Lập kế hoạch cho hồ sơ, ảnh đại diện, đăng xuất, chuyển chế độ chủ sân, đổi mật khẩu/email, xóa tài khoản; dùng lại phần `core/` của plan 010 |
| Nhánh Git | `feature/01-account-plan` |

### Prompt đã dùng

```text
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/011-account-profile
Lập kế hoạch kỹ thuật cho spec 011 theo constitution và đặc tả CSDL chung (mục 4.1 users,
mục 7 quyền, mục 8a Edge Functions). Spec này xây trên plan 010: dùng lại core/session
(SessionManager, IdTokenProvider, UserProfileReader), Rules users/{uid} (selfEditableFields,
isInitialized()) và _shared/auth.ts, _shared/identity.ts. Không làm lại phần đã có ở 010, chỉ
mở rộng. Lưu ý:
- Sửa hồ sơ: bổ sung Rules kiểm tra từng trường mà 010 để lại cho 011 (level thuộc 3 giá trị
  hoặc null, favoriteDistrictIds 0–5 phần tử có trong danh mục, avatarPath đúng tiền tố uid),
  kèm test Emulator. UserProfileReader mở rộng cho trang tài khoản. Hỏi xác nhận khi thoát
  mà còn thay đổi chưa lưu (FR-006).
- Ảnh đại diện: Supabase Storage (bucket, đường dẫn theo uid, RLS: người đã đăng nhập đọc,
  chỉ chủ tài khoản ghi/xóa) dùng Third-party Auth của PR #23. Cắt vuông, nén ≤ 2 MB, chỉ xin
  quyền camera/ảnh khi bấm đổi ảnh (FR-024). Chính sách Storage phải có test trên supabase local.
- Đăng xuất: SessionManager.signOut() phải xóa FCM token của máy khỏi users.fcmTokens (spec 100),
  xóa DataStore, xóa back stack về nav_auth.
- Mục chủ sân theo 4 trạng thái ownerStatus và trường hợp bị thu hồi (ownerRevokedReason),
  lối vào spec 110. Chuyển chế độ Người chơi ↔ Quản lý sân chỉ trong phiên (FR-011), trước khi
  vào nav_owner phải làm mới token và kiểm tra claim appRole (FR-012).
- Đổi mật khẩu và đổi email: xác nhận lại danh tính khi đăng nhập đã lâu (reauthenticate,
  FR-017), ẩn với tài khoản chỉ có Google/số điện thoại (FR-015). Đổi email dùng
  verifyBeforeUpdateEmail; users.email không nằm trong selfEditableFields nên ghi rõ ai đồng bộ
  email mới vào document sau khi xác minh. Kiểm chứng hành vi với Email enumeration protection.
- Xóa tài khoản: Edge Function delete-account ở server. Chốt cách đạt "toàn bộ hoặc không"
  (FR-021) khi phải đụng Firestore, Storage và Firebase Auth (thứ tự, idempotent, thử lại được,
  xóa Auth sau cùng). Có chế độ xem trước tác động cho FR-022 (số đơn, lượt vãng lai, tiền hoàn,
  cơ sở bị ẩn). Ẩn danh dữ liệu liên quan người khác (FR-020) và hủy đơn theo FR-022a. Ghi rõ
  giới hạn gói Spark/Free (batch 500 ghi, thời gian chạy function).
- Số dư ví giả lập hiển thị kèm nhãn, nút nạp dùng chức năng của spec 050 (FR-001a).
- UI theo các ảnh trong specs/011-account-profile/ui (có chế độ tối, w600dp).
Ghi rõ những thay đổi cần cập nhật vào docs/design/database/README.md và việc cần phối hợp:
B (hủy đơn theo chính sách hủy, nạp ví), C (ẩn danh đánh giá, tin nhắn, bài đăng; hủy lượt
vãng lai; xóa favorites), D (ẩn cơ sở, hủy đơn của khách, gửi thông báo, xóa hồ sơ và ảnh
giấy tờ chủ sân, thu hồi quyền).
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:** `plan.md`, `research.md` (R1–R16), `data-model.md`, `quickstart.md`, `contracts/security-rules.md`, `contracts/storage-avatars.md`, `contracts/edge-functions-account.md`, `contracts/ui-screens.md`.
- **Xóa tài khoản (`delete-account`)** chạy 3 giai đoạn: xem trước (chỉ đọc), chốt (một Firestore transaction: hủy đơn, nhả slot, hoàn tiền khách, ẩn cơ sở), dọn dẹp (ẩn danh, xóa ảnh, xóa tài khoản Firebase sau cùng). Phần dọn dẹp chưa xong thì `delete-account-sweeper` chạy lại 5 phút một lần.
- **Ảnh đại diện:** bucket `avatars`, tên file `{uid}/avatar_{ms}.jpg` để tránh bộ nhớ đệm, RLS test bằng pgTAP.
- **Đổi email:** thêm Edge Function `sync-account-email` để server ghi `users.email`.
- **Đăng xuất:** thêm cơ chế `SignOutHook` vào `SessionManager` của 010 để gỡ token FCM.
- **Kiểm chứng tài liệu:** Firestore không còn giới hạn 500 document mỗi transaction (giới hạn thật là 500 field transform, 270 giây, 10 MiB); Edge Functions gói Free 150 giây, 2 giây CPU. Với Email enumeration protection, `verifyBeforeUpdateEmail` không báo "email đã được dùng", nên US7-2 không làm được.
- Liệt kê 7 chỗ cần sửa spec (R16) và 2 chỗ cần sửa plan 010 (`MainActivity` điều hướng theo vai trò và chế độ, `SESSION_REVOKED`).

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Xóa tài khoản 3 giai đoạn, xóa Auth sau cùng | Dùng | Đạt "toàn bộ hoặc không" ở bước chốt, phần còn lại thử lại được |
| `sync-account-email` | Dùng | Client không được ghi `users.email` |
| Sửa spec theo R16 (US7-2, FR-004, FR-017, FR-018, FR-021, FR-022, FR-022a) | Dùng, đã sửa trước khi chia task | Tránh sinh task theo yêu cầu Firebase không làm được |
| Thư viện cắt ảnh `android-image-cropper` | Dùng | Ghi ở Complexity Tracking |
| Màn hình "Hồ sơ chủ sân" (wireframe `buoc4`) thuộc spec 110 | Chờ xác nhận | D xác nhận ở họp 16/10 |

### Cách kiểm chứng

- Mở lại tài liệu Firestore quotas, Supabase Edge Functions limits và Email enumeration protection mà AI dẫn.
- Đối chiếu Constitution Check với nguyên tắc I–VII.
- Kiểm tra mọi FR có quy tắc trong contract hoặc kịch bản trong `quickstart.md`.

## Mục 12: Chia task spec 011 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 011 thành task theo 7 user story, tách phần mở rộng `core/` thành PR riêng |
| Nhánh Git | `feature/01-account-plan` |

### Prompt đã dùng

```text
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/011-account-profile
Chia task theo user story (US1–US7), viết test trước (phải fail) rồi mới cài đặt. Yêu cầu:
- Phase Foundational: phần mở rộng core dùng chung (research R1) gồm thêm trường MyProfile,
  AppModeController, SignOutHook (Hilt @IntoSet), SignOutReason.SESSION_REVOKED/ACCOUNT_DELETED,
  core/media/StorageUrls, MainActivity điều hướng theo (appRole, mode). Ghi rõ đây là sửa trên
  core/session của 010, không đổi chữ ký cũ, tách PR riêng để nhóm review sớm.
- Server: Rules users/{uid} cho level, favoriteDistrictIds, avatarPath và collection districts,
  accountDeletions, có test P1–P18, D1–D4, A1 (U1–U18 của 010 vẫn phải xanh). Migration bucket
  avatars và RLS với test pgTAP ST1–ST8 trên Supabase local.
- Edge Functions: sync-account-email, delete-account (preview, confirm một transaction,
  cleanup 10 bước, xóa Auth sau cùng), delete-account-sweeper (pg_cron, CRON_SECRET),
  requireUser trả 403 ACCOUNT_DELETING, 403 ADMIN_SELF_DELETE; test X1–X16, E1–E3 trên
  Auth/Firestore Emulator, X7 và X10 chạy 10 lần liên tiếp.
- Ghi rõ task phụ thuộc helper của B (cancelBookingInTx, anonymizeBookings), C
  (cancelRegistrationInTx, leaveWaitlistInTx, anonymizeReviews, anonymizeCommunity), D
  (hideVenueForOwnerDeletionInTx, cancelSessionInTx, deleteOwnerApplications, send-notification
  có khóa idempotent), kèm phương án tạm: helper chưa có thì bước đó ghi SKIPPED (research R12).
- App: unit test cho DistrictSelectionRule, DifferentPasswordRule, DeleteConfirmRule,
  AvatarImageRule, bảng OwnerSection 6 dòng, AppModeController, SignOutHook trong signOut(),
  ERROR_USER_TOKEN_EXPIRED → SESSION_REVOKED, và các ViewModel (Turbine) theo quickstart §4.
- UI theo contracts/ui-screens.md và wireframe trong specs/011-account-profile/ui (chế độ tối, w600dp).
- Có task cập nhật docs/design/database/README.md theo 8 thay đổi trong data-model.md, task sửa
  plan 010 (MainActivity, SESSION_REVOKED), và task chạy kịch bản thủ công Q1–Q27 trong quickstart.md.
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` với **101 task** (T001–T101), 58 task `[P]`:

| Phase | Số task | Nội dung chính |
|---|---|---|
| 1 Setup | 4 | Thư viện cắt ảnh, `storage-kt`, FileProvider, `strings_account.xml`, seed `districts` |
| 2A PR `chore(core)` | 14 | `MyProfile`, `SignOutReason`, `SignOutHook`, `AppModeController`, `StorageUrls`, `MainActivity` theo `(appRole, mode)` |
| 2B, 2C | 10 | Model, `AccountError`, xác nhận lại danh tính; `nav_account`, khung `AccountFragment` |
| US1–US3 (P1) | 30 | Rules P1–P18, Storage ST1–ST8, sửa hồ sơ, đổi ảnh; đăng xuất; mục chủ sân |
| US4–US7 (P2, P3) | 37 | Chuyển chế độ; đổi mật khẩu; `delete-account`, sweeper, X1–X16; `sync-account-email` |
| Polish | 6 | README CSDL, rà giao diện, Q1–Q27, chạy toàn bộ test |

- **Làm khác research R12:** chỉ bước dọn dẹp được ghi `SKIPPED` khi thiếu helper. Helper của bước chốt (hủy đơn, hủy lượt vãng lai, ẩn cơ sở) chưa có thì `confirm` trả `500`, vì bỏ qua hủy đơn sẽ làm slot bị chiếm mãi (sai FR-022a).
- Tách hàm thuần `rootGraphFor(appRole, mode)` và `accountEntries(...)` để test được; thêm `deno task test:flaky` chạy X7, X10 10 lần.
- MVP đề xuất: US1 + US2.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Bước chốt thiếu helper thì trả `500` thay vì `SKIPPED` | Dùng | Đúng FR-022a, không để slot bị chiếm |
| Hàm thuần `rootGraphFor`, `accountEntries` | Dùng | Logic điều hướng test được bằng unit test |
| Không deploy, chỉ khai báo function trong `config.toml` (T080) | Dùng | Đúng quy ước của PR #20/#21 |

### Cách kiểm chứng

- Kiểm tra ID T001–T101 liên tục, đúng định dạng checkbox và nhãn story.
- Đối chiếu bảng phụ thuộc B, C, D với bảng phối hợp trong `plan.md`.
- Chạy `/speckit-analyze` ngay sau đó (mục 13).

## Mục 13: Kiểm tra độ khớp spec/plan/tasks spec 011 bằng `/speckit-analyze` (2 lần)

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-15 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Soát spec 011 trước khi code; sửa theo báo cáo rồi chạy lại để xác nhận |
| Nhánh Git | `feature/01-account-plan` (commit `e511e0b`, `e9118ab`) |

### Prompt đã dùng

```text
/speckit-analyze
```

(Chạy 2 lần: lần 1 trên tasks vừa sinh; lần 2 sau khi AI sửa theo báo cáo lần 1.)

### Tóm tắt phản hồi của AI

- **Lần 1:** 1 CRITICAL, 4 HIGH, 8 MEDIUM, 6 LOW; độ phủ 34/35 (thiếu SC-009).
  - **C1:** `AccountFragment` mở `LegalDocumentFragment` của `feature/auth/ui`, trái nguyên tắc I. Đề xuất mở bằng deep link `smashnow://legal/{doc}`.
  - **H1:** lần gọi `confirm` thứ hai bị `requireUser` chặn bằng `403 ACCOUNT_DELETING`, nên X10 không đạt. Đề xuất `requireUser(req, { allowDeleting: true })`.
  - **H2:** khóa idempotent thông báo dùng `sessionId`, hai khách cùng buổi vãng lai sẽ trùng khóa. Đổi thành `{kind}_{id}_{recipientUid}_OWNER_DELETED`.
  - **H3, H4:** kịch bản thủ công chạy trên project thật; OTP cần `Activity` (cùng lỗi với 010).
- Theo yêu cầu "Bạn cứ chỉnh cho phù hợp nhất", AI sửa cả 19 mục: `tasks.md` lên **105 task**, thêm T005 (môi trường chạy tay cục bộ), T025 (`CurrentActivityProvider`), T038 (test Repository), T104 (thử SC-009 với 5 người); sửa contract, research, spec ("5 trạng thái" chủ sân), quickstart (Q28).
- **Lần 2:** 0 CRITICAL, 0 HIGH, 2 MEDIUM, 6 LOW; độ phủ 35/35 (100%). Còn M1: token cũ còn hạn tới 1 giờ sau khi xóa tài khoản vẫn tạo được đánh giá/bài đăng, nên lượt quét cuối cần chạy lại các bước ẩn danh.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| 19 mục sửa của lần 1 | Dùng | Đều là lỗi thiết kế hoặc lệch tài liệu thật |
| M1, M2, L1–L6 của lần 2 | Chưa sửa | Không chặn việc code; M1 cần sửa trước khi viết `delete-account` |
| Third-party Auth của Supabase local với token Emulator | Chờ xác nhận | Có thể bị từ chối; chốt ở họp 16/10 (T005 thử trước) |

### Cách kiểm chứng

- Chạy lại `/speckit-analyze` lần 2 để xác nhận các mục CRITICAL/HIGH đã hết.
- Kiểm tra mọi chỗ "Cho Txxx xanh" trỏ đúng task test sau khi đánh số lại; số task của 010 ghi dạng `010-T017` để không bị đổi nhầm.
- Commit `e511e0b` (010) và `e9118ab` (011), push nhánh `feature/01-account-plan`.

## Mục 14: Lập kế hoạch kỹ thuật spec 020 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (`specs/020-court-search`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan`; tra tài liệu Firestore qua MCP `developerknowledge` |
| Mục đích | Chọn cách tìm kiếm trong giới hạn truy vấn Firestore, chốt cách sinh `nameKeywords`, lọc khung giờ trống, sắp xếp gần nhất, lịch sử tìm kiếm, quyền đọc cho khách |
| Nhánh Git | `feature/02-court-search-plan` (tạo mới từ `main`) |

### Prompt đã dùng

```text
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/020-court-search
Lập kế hoạch kỹ thuật cho spec 020 theo constitution 1.2.0 (MVVM, một Activity + Navigation,
Hilt, StateFlow; Firebase gói Spark, Supabase gói Free) và đặc tả CSDL chung (mục 4.3 venues
kèm courts/priceRules, mục 4.4 slots, users/{uid}/searchHistory, mục 6 index, mục 9 điểm 6).
Dùng lại phần core/ đã chốt ở plan 010 (SessionManager, AppError, timeout 15 giây).
Lưu ý:
- Giới hạn truy vấn Firestore: mỗi query chỉ có một array-contains/array-contains-any, nên
  nameKeywords (tên) và amenityIds (tiện ích) không lọc chung được. Ngoài ra array-contains-any
  là "có một trong", còn FR-008 cần "có đủ tất cả". Tên có nhiều từ (FR-001) cũng cần mọi từ
  đều khớp. So sánh các cách:
  (a) query theo một điều kiện chính, phần còn lại lọc trên máy;
  (b) đổi amenityIds thành map amenities.{id}=true để lọc bằng nhiều điều kiện bằng (==);
  (c) Edge Function search-venues.
  Chọn một cách. Nêu rõ cách giữ trang 20 kết quả (FR-016) khi có lọc trên máy, và đánh giá
  số lượt đọc so với hạn mức 50.000 lượt/ngày của gói Spark.
- Chốt mục 9 điểm 6: dùng nameKeywords hay dịch vụ tìm kiếm ngoài. Nếu dùng nameKeywords
  thì định nghĩa cách sinh: chữ thường, bỏ dấu, đ→d, tiền tố của từng từ, có giới hạn độ dài.
  Đặt hàm này ở core/ để D (spec 111) gọi khi lưu cơ sở. Dùng cùng một hàm chuẩn hóa cho từ
  khóa người chơi gõ.
- Khoảng giá (FR-005, FR-005a) kết hợp với sắp xếp theo đánh giá hoặc giá: kiểm tra giới hạn
  bất đẳng thức và orderBy của Firestore, liệt kê composite index cần tạo.
- Lọc theo khung giờ còn trống (FR-006): slots chỉ có document khi khung bị chiếm, nên phải
  đọc slots theo venueId + date cho từng cơ sở ứng viên. Coi HOLD đã quá holdExpiresAt là trống.
  Giá tham chiếu theo khung (FR-005) tính từ priceRules theo từng khung 30 phút
  (pricePerHour / 2). Dùng chung logic tính giá và sinh lưới giờ với spec 040 (B), đưa phần
  này về core/ thay vì viết lại. Ước lượng số lượt đọc và thời gian để đạt mục tiêu trang
  đầu dưới 3 giây trên 4G.
- Sắp xếp "Gần nhất" (FR-011 đến FR-014): xin quyền vị trí đúng lúc chọn (chấp nhận vị trí
  gần đúng), tính khoảng cách đường chim bay trên máy, không gửi vị trí lên server. So sánh
  hai cách: query geohash (GeoFire utils) hoặc sắp xếp trên tập kết quả đã lọc. Khi bị từ chối
  quyền thì có hướng xử lý rõ.
- SearchFilter là model dùng chung với spec 022 (FR-010): đặt ở đâu, có hàm validate bỏ giá
  trị sai (ngày đã qua, quận/tiện ích không có trong danh mục, khoảng giá ngược). Ghi thành
  contract để người làm 022 dùng.
- FR-018: debounce ô gõ tên và dùng flatMapLatest để kết quả cũ không ghi đè kết quả mới.
  Quay lại từ màn chi tiết sân phải giữ bộ lọc và vị trí cuộn (FR-017). Khi mất mạng (FR-020)
  dùng cache offline của Firestore và hiện thông báo dữ liệu có thể chưa mới.
- Lịch sử tìm kiếm (FR-021 đến FR-025): tối đa 10 mục, trùng thì đưa lên đầu (cân nhắc dùng
  ID tất định từ bộ lọc đã chuẩn hóa). Dọn mục cũ ở đâu. Security Rules chỉ cho chủ tài khoản
  đọc/ghi. Bảo đảm Edge Function delete-account (spec 011) xóa cả subcollection này.
- Khách chưa đăng nhập (FR-026, FR-027): Rules cho đọc công khai venues có status=ACTIVE,
  danh mục districts/amenities, priceRules và slots ở mức đủ để tìm kiếm. Khách không có lịch
  sử. Kèm test Rules trên Emulator.
- UI theo các ảnh trong specs/020-court-search/ui (4 bước, có chế độ tối), có đủ 4 trạng thái
  đang tải, có kết quả, rỗng, lỗi (FR-019).
Ghi rõ những thay đổi cần cập nhật vào docs/design/database/README.md (trường, index, Rules)
và việc cần B, C, D phối hợp. B: dùng chung logic giá/slot, đọc slots công khai. C: ratingAvg,
ratingCount. D: sinh nameKeywords, minPricePerHour, status khi lưu cơ sở; danh mục districts,
amenities.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:** `plan.md`, `research.md` (R1–R15), `data-model.md`, `quickstart.md`, `contracts/search-criteria.md`, `contracts/keyword-normalization.md`, `contracts/security-rules-and-indexes.md`, `contracts/ui-screens.md`.
- **Cách tìm:** chọn phương án (a). Firestore lọc trạng thái, quận, một token tên và khoảng giá; máy lọc các token còn lại, tiện ích "đủ tất cả" và giờ trống. Loại (b) vì số index tăng theo tổ hợp và phải đổi schema của D; loại (c) vì tốn bấy nhiêu lượt đọc mà chậm hơn.
- **Phân trang hai cách:** sắp xếp theo đánh giá (hoặc giá khi không lọc giờ) lấy theo lô 25 tới khi đủ 20; "Gần nhất" (hoặc giá có khung giờ) lấy tối đa 150 cơ sở rồi sắp xếp trên máy.
- **Chi phí ước lượng:** một lần tìm có khung giờ và đã chọn quận tốn khoảng 35–105 lượt đọc, 1,2–1,8 giây trên 4G (chưa đo).
- **`nameKeywords`:** hàm sinh có bản Kotlin và TypeScript, dùng chung một file vector test.
- **Lỗi ngầm:** Firestore bỏ document thiếu trường dùng để `orderBy`, nên D phải ghi sẵn `ratingAvg = 0`, `ratingCount = 0`, `minPricePerHour = null`.
- Cần 13 composite index. Việc phối hợp: D cập nhật `minPricePerHour`; B và D chốt quy ước `daysOfWeek` (đề xuất ISO); B đồng ý đưa logic giá lên `core/pricing` và mở quyền đọc `slots` cho khách.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Phương án (a) lọc một phần trên Firestore | Dùng | Không đổi schema của D, ít index, nằm trong hạn mức Spark |
| `nameKeywords` thay cho dịch vụ tìm kiếm ngoài | Dùng | Không tốn phí, đủ cho tìm theo tiền tố |
| Giới hạn 150 cơ sở cho chiến lược sắp xếp trên máy | Dùng | Giữ số lượt đọc trong hạn mức; sửa câu chữ FR-016, SC-003 theo R15 |
| Đưa logic giá 30 phút lên `core/pricing` | Chờ xác nhận | Cần B đồng ý vì 040 dùng chung |
| Quy ước `daysOfWeek` theo ISO | Chờ xác nhận | Spec 040 đang coi 1 là Chủ nhật |

### Cách kiểm chứng

- Mở lại tài liệu Firestore về giới hạn `array-contains`, range filter nhiều trường và `orderBy` loại document thiếu trường.
- Đối chiếu Constitution Check trước và sau Phase 1.
- Việc đo thời gian thật để lại cho quickstart §4 (T096–T097).

## Mục 15: Chia task spec 020 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (`specs/020-court-search`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 020 thành task theo 4 user story, gồm PR `chore(core)` phần 2, Rules/index/seed và task theo dõi phối hợp |
| Nhánh Git | `feature/02-court-search-plan` |

### Prompt đã dùng

```text
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/020-court-search
Sinh tasks.md cho spec 020 từ plan.md, research.md (R1–R15), data-model.md, contracts/ và
quickstart.md. Chia theo user story của spec (US1 P1, US2 P1, US3 P2, US4 P3) để làm và kiểm
thử độc lập từng story.
Lưu ý:
- Phase Setup/Foundational làm trước mọi story, gồm PR chore(core) phần 2 của A:
  core/text (VietnameseNormalizer, KeywordGenerator, KeywordQuery), core/pricing (PriceRule,
  SlotPricing), core/schedule (TimeOfDay, SlotMath, BookingWindow, SlotId), core/location,
  core/catalog (CatalogRepository), SearchCoreModule. Dùng lại core/ của plan 010
  (SessionManager, AppError, awaitWithTimeout 15s). Nếu PR chore(core) của 010 chưa merge
  thì ghi rõ là phụ thuộc chặn.
- Constitution IV bắt buộc có test. Với logic thuần (normalizer, keyword, SlotPricing,
  sanitizer, VenueMatcher, AvailabilityEvaluator, ResultSorter, DistanceFormatter), viết task
  test trước task cài đặt. Test keyword đọc chung file
  supabase/functions/_shared/search-keywords.vectors.json, có task riêng cho bản TypeScript
  _shared/search-keywords.ts + deno test.
- Rules và test Emulator: task cho match districts, amenities, searchHistory (C-1…C-3,
  H-1…H-15 trong contracts/security-rules-and-indexes.md). Test V-1…V-5, S-1, S-2 phụ thuộc
  Rules của D (venues, courts, priceRules) và B (slots): để it.todo và đánh dấu [BLOCKED: D]
  / [BLOCKED: B], không tự viết Rules thay họ.
- firestore.indexes.json với đủ 13 index (V1–V12, S1) là một task riêng; báo B vì index slots
  thay index của 040.
- Dữ liệu demo có đáp án: scripts/seed/catalog.json, search-demo.json,
  search-demo.expected.json (12 bộ điều kiện), seed.mjs nhận --emulator/--project và --today.
  Đặt trong Foundational vì mọi story dùng để kiểm thử (SC-003, SC-004).
- US1: VenueQueryBuilder, VenueDataSource, FirestoreVenueSearchEngine chiến lược S1 (lô 25,
  tối đa 4 lô, đủ 20), SearchFragment hai trạng thái (Trang chủ / có điều kiện), thẻ kết quả,
  4 trạng thái màn hình, mất mạng (isFromCache), debounce + flatMapLatest (FR-018), giữ trạng
  thái khi quay lại từ chi tiết sân (FR-017, nav graph nav_search, SavedStateHandle). Vùng cho
  ô AI (022) và "Sân gần bạn" (021) chỉ là FragmentContainerView trống.
- US2: bottom sheet bộ lọc, chip (FR-009), AvailabilityEvaluator + VenueStaticCache, chiến
  lược S2 cho sắp xếp giá có khung giờ, giới hạn 150 ứng viên, count() cho "Xem N sân".
- US3: sắp xếp, sắp xếp giá hai pha (minPricePerHour null đứng cuối), luồng quyền vị trí có
  hộp giải thích, LocationManagerCompat, khoảng cách trên thẻ, từ chối thì quay về sắp xếp cũ.
- US4: SearchInputFragment, lịch sử với ID tất định, transaction ghi, dọn còn 10, xóa từng mục
  và xóa tất cả, khách thấy lời mời đăng nhập; sanitizer khi khôi phục mục lịch sử.
- Cửa vào cho 021/022 (SearchViewModel.applyExternal, SearchCriteria @Serializable với
  serializer LocalDate/TimeOfDay) đặt ở story nào cần trước, ghi rõ trong mô tả task.
- Phase cuối (Polish): w600dp lưới 2 cột, chế độ tối, TalkBack/contentDescription, đo
  SearchTiming/SearchReads theo quickstart §4, chạy lại 12 bộ điều kiện trên project thật để
  bắt lỗi thiếu index, Espresso luồng chính (nên có).
- Có task tài liệu: cập nhật docs/design/database/README.md theo danh sách cuối data-model.md,
  sửa spec 020 theo bảng R15, và cập nhật docs/ai-log/AI_log_1.md.
- Các việc phối hợp trong bảng "Phối hợp với nhóm" của plan (D: nameKeywords, giá trị mặc định
  rating/giá, ai cập nhật minPricePerHour; B và D: quy ước daysOfWeek; B: dùng chung
  core/pricing; C: nút trái tim) ghi thành task theo dõi có tên người phụ trách, không phải
  task code của A.
Mỗi task có ID, đánh dấu [P] khi làm song song được, nhãn [US1]…[US4], đường dẫn file cụ thể
theo cây Source Code của plan. Cuối file có sơ đồ phụ thuộc giữa các phase, gợi ý MVP (US1 +
US2) và cách chia PR (chore(core) phần 2 → Rules/index/seed → từng story).
```

### Tóm tắt phản hồi của AI

- Sinh `tasks.md` với **105 task** (T001–T105), khoảng 66 task `[P]`:

| Phase | Task | Nội dung |
|---|---|---|
| 1 Setup | T001–T002 | Kiểm tra PR `chore(core)` của 010, package script seed |
| 2A `chore(core)` phần 2 | T003–T017 | `core/text`, `core/schedule`, `core/pricing`, `core/location`, `core/catalog`, bản TS `search-keywords` |
| 2B Rules, index, seed | T018–T024 | Rules `districts`, `amenities`, `searchHistory`; 13 index; dữ liệu demo 12 bộ đáp án |
| 2C Domain | T025–T030 | `SearchCriteria` `@Serializable`, DTO, `VenueDataSource`, `nav_search` |
| 2D Phối hợp | T031–T039 | Task theo dõi việc của B, C, D |
| US1–US4 | T040–T092 | 16 / 14 / 9 / 14 task |
| Polish | T093–T105 | `w600dp`, chế độ tối, TalkBack, đo SC-001, Espresso, tài liệu |

- T001 là phụ thuộc chặn: code plan 010 chưa có trên `main`.
- Cửa vào `applyExternal` cho 022 đặt ở US4 (T088) vì dùng chung sanitizer với khôi phục lịch sử.
- Test V-1…V-5, S-1, S-2 để `it.todo` với `[BLOCKED: D]` / `[BLOCKED: B]`; nút mở chi tiết sân trỏ tạm vào màn placeholder (T030).

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Thứ tự PR `chore(core)` phần 2 → Rules/index/seed → domain → story | Dùng | Đúng yêu cầu trong prompt, PR nhỏ dễ review |
| Task theo dõi phối hợp (2D) không phải task code | Dùng | Không tự viết Rules thay B, D |
| Placeholder cho màn chi tiết sân | Dùng | Spec 030 chưa có code |

### Cách kiểm chứng

- Kiểm tra ID T001–T105 liên tục, mỗi task có nhãn và đường dẫn file theo cây Source Code của plan.
- Đối chiếu từng gạch đầu dòng trong prompt với task tương ứng.
- Chạy `/speckit-analyze` ngay sau đó (mục 16).

## Mục 16: Kiểm tra độ khớp spec/plan/tasks spec 020 bằng `/speckit-analyze`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (`specs/020-court-search`), kéo theo sửa 010 và 011 |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Soát spec 020 trước khi code, kiểm tra chéo với spec 010 |
| Nhánh Git | `feature/02-court-search-plan` (PR #34), `feature/01-account-guest-mode` (PR #33) |

### Prompt đã dùng

```text
/speckit-analyze
```

### Tóm tắt phản hồi của AI

- Kết quả: 0 CRITICAL, 2 HIGH, 8 MEDIUM, 6 LOW; độ phủ 32/34 (94%), thiếu SC-002 và SC-007 (thử với người dùng).
- **G1 (HIGH):** theo 010, người chưa đăng nhập luôn bị đưa về `nav_auth`, nên khách không bao giờ tới được màn tìm kiếm; FR-026 không làm được. Sau khi đăng nhập, `popUpTo` còn xóa luôn bộ lọc.
- **I1 (HIGH):** FR-009 nói "Xóa bộ lọc" giữ từ khóa, còn US2-8 nói giữ từ khóa và quận.
- MEDIUM tiêu biểu: giới hạn 150 ứng viên trái câu chữ SC-003 (I2); chưa ước lượng trường hợp xấu nhất 150 ứng viên có khung giờ (A1); `SearchSession` ở domain giữ kiểu Firestore (U1); bảng lọc thiếu trạng thái tải/lỗi (D1).
- Mình chọn sửa G1 thành **chế độ khách**: AI sửa spec 010 (thêm FR-025, FR-026, R15, `AuthNavigator`, `LoginPromptView`, nhóm task 2D T080–T083, kịch bản M26–M30), sửa 011 (đăng xuất về chế độ khách) và 020 cho khớp.
- Theo "Theo như đề xuất đi", AI sửa tiếp các mục còn lại: FR-009 giữ từ khóa và quận; FR-016, FR-026 theo R15; dừng sớm khi đủ 20 kết quả, ước lượng trường hợp xấu nhất (~1.500 lượt đọc, 7–8 giây) kèm kịch bản đo thứ 6; con trỏ phân trang là kiểu mờ; thêm T106 thử với 5 người.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Chế độ khách thay cho bắt đăng nhập | Dùng | Người dùng phải xem được sân trước khi đăng nhập; sửa ở 010 vì đó là nơi chốt điều hướng |
| Không dùng đăng nhập ẩn danh của Firebase | Dùng | Ghi lý do ở R15 của 010 |
| Dừng sớm khi đủ 20 kết quả, bỏ con số tổng khi có khung giờ | Dùng | Giảm lượt đọc; chấp nhận hiện "Kết quả phù hợp" thay cho số |
| Mỗi bạn tự quyết màn nào khách được xem | Chờ xác nhận | Báo nhóm trong PR #33 |

### Cách kiểm chứng

- Đọc lại `specs/010-account-auth/contracts/ui-screens.md` để xác nhận G1 có thật trước khi sửa.
- Kiểm tra các chỗ "105 task" đã đổi thành 106, thứ tự task không bị lệch.
- Tách commit `4a751e7` (chế độ khách 010 + 011, PR #33) và `cfb8dbf` (spec 020, PR #34).

## Mục 17: Cài đặt Phase 1, 2A, 2D (và 2C) spec 010 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement`; Firebase MCP (chỉ đọc) |
| Mục đích | Làm PR `chore(core)` mà cả nhóm đang chờ: thư viện, Hilt, phiên đăng nhập, điều hướng, chế độ khách |
| Nhánh Git | `feature/01-account-core` (tách từ `feature/01-account-guest-mode` vì PR #33 chưa merge), sau đó `feature/01-account-auth-domain` cho 2C |

### Prompt đã dùng

```text
/speckit-implement SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Chỉ làm Phase 1 (T001–T004, T003) và Phase 2A + 2D (T006–T018, T080–T083) cho PR chore(core).
Dừng sau Checkpoint 2A, không làm 2B, 2C hay user story.
- Tạo nhánh feature/01-account-core từ main (sau khi merge #33).
- Bỏ qua T005 (cấu hình Firebase Console), mình tự làm.
- Test viết trước và phải fail rồi mới cài đặt (T006, T007, T080).
- T002: kiểm tra Hilt, KSP, Secrets Gradle Plugin chạy được với AGP 9.4 / Kotlin 2.2.
  Firebase BoM giữ 34.19.0. Nếu không tương thích thì dừng lại báo mình, không tự hạ phiên bản.
- Không commit google-services.json, local.properties.
- Xong mỗi nhóm task thì đánh dấu [x] trong tasks.md. Cuối cùng chạy
  ./gradlew testDebugUnitTest và ./gradlew assembleDebug rồi báo kết quả.
```

### Tóm tắt phản hồi của AI

- **T002:** Hilt 2.59.2, Secrets Gradle Plugin 2.0.1, Safe Args 2.10.2 chạy được với AGP 9.4.1 / Kotlin 2.2.10. Ba thư viện mới phải chọn bản cụ thể: KSP 2.3.12 (bản 2.2.10-2.0.2 lỗi với Kotlin tích hợp của AGP 9), `googleid` 1.2.0 và supabase-kt 3.6.0 (bản mới hơn cần Kotlin 2.4). Firebase BoM giữ 34.19.0.
- Test T006, T007, T080 viết trước, chạy thấy fail rồi mới cài đặt; thêm test `DataStoreSessionPrefs` (đăng xuất giữ `welcome_seen`). Tổng **38/38 test xanh**.
- Gỡ một lỗi KSP: chuỗi `feature/*` trong KDoc bị Kotlin hiểu là mở comment lồng.
- Chạy thử Checkpoint 2A trên máy ảo API 35: màn hình chào → "Xem sân trước" vào trang chủ khách → mở lại app vào thẳng trang chủ khách → "Đăng nhập" mở chồng `nav_auth`, Back quay về.
- Xóa code mẫu (FirstFragment, SecondFragment, FAB, `nav_graph`), đổi `app_name` thành "SmashNow".
- Theo góp ý "phải theo design mà tôi với bạn đã làm", AI đưa màu, kiểu chữ (Be Vietnam Pro, Barlow Semi Condensed) và kích thước của design system SmashNow vào theme trong cùng PR. Commit `2feffcc`, PR #35.
- Làm tiếp 2C (T029–T035) theo đề xuất: kiểm tra ô nhập, 12 loại `AuthError` và bộ ánh xạ lỗi Firebase, `CompleteSignupUseCase`, `strings_auth.xml` lấy câu chữ từ wireframe, `terms_vi.html`, `privacy_vi.html`. Commit `9cc0d36`, **70 test xanh**.
- Kiểm tra project `mobile-22e6c` qua Firebase MCP: Google đã bật, SHA-1 máy đã có; ghi đè `app/google-services.json` bằng bản mới có đủ Android client.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Chọn KSP 2.3.12, `googleid` 1.2.0, supabase-kt 3.6.0 | Dùng | Chỉ chọn bản cho thư viện mới thêm, không hạ bản đang có |
| Tách nhánh từ `feature/01-account-guest-mode` thay vì `main` | Dùng | PR #33 chưa merge mà 2D cần nó |
| Áp theme design system ngay trong PR `chore(core)` | Dùng | Cả nhóm dùng chung theme, làm sớm đỡ sửa lại |
| `CompleteSignupUseCase` không tự `signOut()` khi bị khóa | Dùng, đã sửa T033 | Để `SessionManager` giữ lý do `LOCKED` và hiện đúng thông báo |
| Nội dung trang điều khoản do AI viết | Chờ xác nhận | Cả nhóm cần đọc lại |
| `support_email` giả | Chưa làm | Thay bằng hộp thư thật trước khi demo |

### Cách kiểm chứng

- Chạy `./gradlew clean testDebugUnitTest` và `./gradlew assembleDebug` (38 test, sau 2C là 70 test, đều xanh).
- Chạy thử luồng chế độ khách trên máy ảo, xem Logcat không có crash.
- Kiểm tra `git status` không có `google-services.json`, `local.properties`.

## Mục 18: Cài đặt Phase 2B (server `on-signup`, Rules) spec 010 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Viết Edge Function `on-signup`, các file `_shared/` dùng chung, Rules `users/{uid}` kèm test Emulator |
| Nhánh Git | `feature/01-account-signup-server` (tách từ `feature/01-account-core`) |

### Prompt đã dùng

```text
/speckit-implement SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Làm Phase 2B (T019–T028) trên nhánh mới feature/01-account-signup-server tách từ
feature/01-account-core.
- Được cài Deno 2, Supabase CLI, Firebase CLI (bản ổn định, cài ở mức user).
- T019: nếu chưa có _shared/firestore.ts của B thì viết bản tối thiểu (get, commit với
  exists=false, lấy access token service account), đánh dấu TODO(B).
- Test viết trước và phải fail rồi mới cài đặt (T020, T022, T024, T026); chạy trên
  Auth/Firestore Emulator.
- Không deploy Edge Function, không deploy Rules, không chạy supabase config push,
  không đặt secret lên project thật; không commit service account, .env.
- Đánh dấu [x] trong tasks.md, commit không thêm Co-Authored-By. Chưa push, chưa mở PR.
- Cuối cùng chạy deno test và npm test trong firestore-tests/ rồi báo kết quả.
```

### Tóm tắt phản hồi của AI

- Cài Deno 2.9.7, Supabase CLI 2.121.0 (kiểm checksum) và Firebase CLI 15.34.0 ở mức user.
- **T019:** chưa có bản của B nên viết `_shared/firestore.ts` tối thiểu (`get`, `commit` phân biệt `ALREADY_EXISTS`, lấy access token service account), ghi `TODO(B)`.
- **`_shared/auth.ts`:** xác minh token Firebase, có `requireUser`, `requireRole`, `errorResponse`. **`_shared/identity.ts`:** đọc/ghi/gộp claim, khóa/mở khóa, từ chối claim ≥ 1000 byte.
- **`on-signup`:** tạo document trước rồi gán claim, không hạ vai trò, email và số điện thoại chỉ lấy từ token; `verify_jwt = false` trong `config.toml`.
- **`firestore.rules`:** `appRole()` không còn mặc định PLAYER, thêm `isInitialized()`, `isPlayer()` và rule `users/{uid}`. **`grant-role.ts`:** gộp claim trước rồi mới ghi `users.role`.
- **Làm khác contract:** test gọi đồng thời bắt được lỗi `ABORTED` của Firestore, nên `on-signup` đọc lại rồi thử tạo lại tối đa 3 lần.
- Kết quả: `deno test` **35/35** (chạy 4 lần đều xanh), `npm test` trong `firestore-tests/` **27/27**; gọi thử bằng `curl` lần đầu `CREATED`, lần hai `ALREADY_INITIALIZED`. Commit `ca7b1bd`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| `_shared/firestore.ts` tối thiểu, ghi `TODO(B)` | Dùng tạm | B thay bản đầy đủ thì giữ `get`, `commit`, `getAccessToken` và mã lỗi |
| Thử lại khi `ABORTED` | Dùng | Test đồng thời cho thấy lỗi thật; cần cập nhật contract |
| Chạy `index.ts` bằng `deno run` thay vì `supabase functions serve` | Dùng | Tránh tải vài GB image Docker |
| Deploy `on-signup`, đặt secret lên Supabase | Chưa làm | Đúng yêu cầu prompt; làm khi được cho phép |

### Cách kiểm chứng

- Chạy lại `deno test -A on-signup/ _shared/` và `npm test` trong `firestore-tests/` trên Emulator.
- Gọi `on-signup` bằng `curl`: lần đầu `CREATED`, lần hai `ALREADY_INITIALIZED`, `GET` bị 405, thiếu token bị 401.
- Việc cần báo nhóm: B (`_shared/firestore.ts`), C (dùng chung `_shared/auth.ts`), cả nhóm (`isInitialized()`, admin đọc được `users`).

## Mục 19: Cài đặt US2 (đăng nhập email) và US4 (Google) spec 010 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-19 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Làm màn hình đăng nhập email và đăng nhập Google theo wireframe, bước Hoàn tất tài khoản, hộp thoại liên kết tài khoản |
| Nhánh Git | `feature/01-account-auth-domain` |

### Prompt đã dùng

```text
/speckit-implement SPECIFY_FEATURE_DIRECTORY=specs/010-account-auth
Làm US2 (T048–T053) rồi US4 (T058–T066) trên nhánh feature/01-account-auth-domain.
- Test viết trước và phải fail rồi mới cài đặt.
- Giao diện theo wireframe trong specs/010-account-auth/ui và design system SmashNow.
- Mỗi user story một commit (không thêm Co-Authored-By), đánh dấu [x] trong tasks.md.
- Không deploy, không commit google-services.json, local.properties.
- Cuối cùng chạy ./gradlew testDebugUnitTest assembleDebug, cài lên emulator, thử bấm
  "Tiếp tục với Google" rồi báo kết quả. Chưa push, chưa mở PR.
```

(Mình bỏ phần "có chế độ tối" khỏi prompt AI đề xuất, nên lượt này không làm và không kiểm tra riêng chế độ tối.)

### Tóm tắt phản hồi của AI

- **US2** (commit `1a892c6`): bấm hai lần chỉ gửi một yêu cầu; sai email hay mật khẩu chỉ hiện một thông báo chung; tài khoản bị khóa hiện hộp thoại kèm email hỗ trợ; mất mạng giữ email, xóa mật khẩu; đăng nhập xong quay lại đúng màn hình trước (`returnTo`).
- **US4** (commit `2b3017f`): chọn tài khoản qua Credential Manager, hủy thì không báo lỗi; lần đầu vào bước Hoàn tất tài khoản, đóng app mở lại vẫn quay về đây; email Google trùng tài khoản mật khẩu thì hiện hộp thoại liên kết.
- Làm luôn `LegalDocumentFragment` (T044 của US1) vì bước Hoàn tất tài khoản cần link điều khoản.
- **Làm khác `tasks.md`:** không thêm nút Google vào màn hình chào (wireframe không có); hộp thoại liên kết mở từ `LoginFragment` để dùng chung thông tin Google đang chờ.
- Kết quả: **99 unit test xanh**, `assembleDebug` qua. Trên emulator: màn hình đăng nhập khớp wireframe; đăng nhập sai trên Firebase thật báo "Email hoặc mật khẩu không đúng"; bấm Google khi máy chưa có tài khoản báo "Không tìm thấy tài khoản Google trên máy".

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Code US2, US4 | Dùng | Test xanh, bám wireframe và contract |
| Không thêm nút Google ở màn hình chào | Dùng | Đúng wireframe |
| Chế độ tối cho màn hình mới | Chưa kiểm tra | Mình bỏ khỏi phạm vi lượt này; FR-023 vẫn yêu cầu nên kiểm lại ở Polish |
| Thử trọn luồng Google tới trang chủ | Chưa làm | Cần thêm tài khoản Google vào emulator và deploy `on-signup` |

### Cách kiểm chứng

- Chạy `./gradlew testDebugUnitTest assembleDebug` (99 test xanh).
- Cài lên emulator, chụp màn hình so với wireframe `buoc2_dang-nhap`, thử đăng nhập sai và bấm "Tiếp tục với Google".
- Đọc diff hai commit, đối chiếu với T048–T066 trong `tasks.md`.

## Mục 20: Cài đặt US1, US3, US5 và Polish spec 010 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-20 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/010-account-auth`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Làm nốt các task còn mở của spec 010: đăng ký email, màn hình chờ khởi tạo, banner xác minh email (US1), quên mật khẩu (US3), đăng nhập số điện thoại + OTP (US5), Polish (T074–T079) |
| Nhánh Git | `feature/01-account-auth-domain` (đã merge `main` mới nhất trước khi làm) |

### Prompt đã dùng

```text
/speckit-implement Làm tiếp spec 010-account-auth trên nhánh feature/01-account-auth-domain, chỉ các task còn `- [ ]`,
theo thứ tự: 1. US1 (T036–T043, T045–T047): viết test T036–T039 trước, chạy cho thấy fail, rồi mới cài đặt cho xanh;
WelcomeFragment đã có sẵn, chỉ nối nút "Đăng ký". 2. US3 (T054–T057). 3. US5 (T067–T073). 4. Polish: T074, T075, T078,
T079; T077 chỉ làm phần kiểm tra/sửa được bằng code, phần còn lại ghi chú để kiểm tra tay.
Bỏ qua T005 và T076 (không đánh dấu [x], ghi rõ còn chờ người làm).
Hết mỗi story: chạy ./gradlew testDebugUnitTest assembleDebug cho xanh, đánh dấu [x], commit riêng, không push.
Test fail hoặc build lỗi thì dừng lại sửa; không sửa test cho khớp code sai.
Cuối cùng báo lại: task nào xong, task nào còn lại và lý do, kết quả lệnh T079.
```

### Tóm tắt phản hồi của AI

- **US1** (commit `417b843`): `RegisterWithEmailUseCase` (đăng ký → lưu `PendingSignup` → gửi email xác minh → gọi `on-signup`), `RegisterFragment`, màn hình chờ khởi tạo `AccountSetupFragment` (Thử lại / Đăng xuất), banner xác minh email trong `core/ui` với đếm ngược 60 giây. Thêm `AppClock` để test được thời gian. Viết test trước, chạy thấy fail (lỗi biên dịch) rồi mới cài đặt.
- **US3** (commit `933f589`): `SendPasswordResetUseCase` coi "email không tồn tại" là thành công (FR-018, không lộ email), màn hình quên mật khẩu có đếm ngược gửi lại.
- **US5** (commit `aecd299`): `verifyPhoneNumber` bọc bằng `callbackFlow`, `OtpSession` tự giới hạn 5 phút / 5 lần sai (Firebase không cấu hình được, R8), màn hình số điện thoại và 6 ô OTP (tự nhảy ô, dán cả mã, xóa lùi).
- **Polish** (commit `7ca49ca`): cập nhật đặc tả CSDL, README nhóm 01, test Espresso đăng ký → đăng xuất → đăng nhập lại, sửa layout màn hình chờ (thuộc tính `maxWidth` không có tác dụng trên `LinearLayout`) và cảnh báo Lint ở ô OTP.
- Kết quả T079: Gradle `lint testDebugUnitTest assembleDebug` xanh (Lint 0 Error / 42 Warning, 171 unit test); `firestore-tests` 27/27; `deno test` Edge Function 35/35.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Code US1, US3, US5 | Dùng | Test xanh, bám `contracts/ui-screens.md` và wireframe |
| `VerificationEmailSender` trong `core/` | Dùng | Banner ở `core/ui` không được import `feature/auth` |
| Đang ở màn hình đăng ký thì `Initializing` không chuyển màn hình | Dùng | Tránh nhảy màn hình giữa lúc đăng ký đang chạy |
| Test Espresso T075 | Dùng, chưa chạy | Cần máy ảo, `USE_EMULATOR=true` và `on-signup` chạy local; test tự bỏ qua khi không bật Emulator |
| T005, T076, ảnh M1/M7/M12/M18 (T078) | Chưa làm | Phải làm tay trên Firebase Console và máy thật |

### Cách kiểm chứng

- Chạy lại toàn bộ lệnh T079 (kết quả ở trên).
- Đọc diff từng commit, đối chiếu với task trong `tasks.md` và `contracts/ui-screens.md`.
- Việc cần làm tiếp: chạy T076 (kịch bản M1–M25 trên máy thật), chụp ảnh cho README, chạy T075 trên máy ảo; D, C xem lại phần sửa đặc tả CSDL trong PR.

## Mục 21: Lập kế hoạch kỹ thuật spec 030 bằng `/speckit-plan`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-25 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 03 – Chi tiết sân (`specs/030-court-detail`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-plan` |
| Mục đích | Lập kế hoạch cho màn hình chi tiết sân: chỉ đọc dữ liệu, dùng lại `core/` của 010 và 020, xử lý dữ liệu seed 10 sân của nhóm, ghi rõ việc cần B, C, D phối hợp |
| Nhánh Git | `feature/03-court-detail-plan` (tạo mới từ `main` sau khi pull seed của Duy, PR #41) |

### Prompt đã dùng

```text
/speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/030-court-detail
Lập kế hoạch kỹ thuật cho spec 030 theo constitution (MVVM, một Activity + Navigation, Hilt,
StateFlow; Firebase gói Spark, Supabase gói Free) và đặc tả CSDL chung (mục 4.3 venues kèm
courts/priceRules, reviews, danh mục amenities/districts, mục 7 quyền). Spec này chỉ đọc dữ liệu.
Dùng lại phần đã có, không viết lại: core/session (SessionManager, isGuest), AppError và timeout
15 giây, AppClock, AuthNavigator/LoginPromptView và cơ chế returnTo của plan 010 (R15), model
ngữ cảnh tìm kiếm và core/pricing của plan 020. Lưu ý:
- Lối vào (FR-001, FR-002): một destination chi tiết sân trong nav_player kèm deep link
  (ví dụ smashnow://venue/{venueId}?date=&start=&end=) để 020, 021, 070 và thông báo cùng mở
  được. Ghi thành contract tham số vào màn hình và tham số chuyển sang lưới giờ 040 (B).
- Đọc dữ liệu và hiệu năng (FR-024, SC-001): liệt kê từng lượt đọc khi mở màn hình (venue,
  courts, priceRules, 3 review VISIBLE mới nhất, danh mục). Phần đầu tải trước, bản đồ và
  đánh giá tải sau. So sánh get() một lần với snapshot listener. Ước lượng số lượt đọc mỗi lần
  mở so với hạn mức 50.000 lượt/ngày, và thời gian hiện phần đầu so với mục tiêu 3 giây trên 4G.
- Điểm đánh giá (FR-003, FR-014): đọc ratingAvg, ratingCount và tổng điểm từng tiêu chí trên
  venue (C cập nhật ở 070), không tự tính từ reviews. Ghi rõ các trường cần có trên venue.
  Người viết đã xóa tài khoản hiện "Người dùng đã xóa": kiểm tra delete-account (011) đã ẩn
  danh snapshot tên/ảnh trong review chưa.
- Bảng giá (FR-008, FR-009, SC-002): nhóm priceRules theo nhóm ngày. Giá "Khung bạn chọn"
  phải gọi đúng hàm core/pricing đã dùng cho thẻ kết quả 020 và màn đặt sân 040 (từng khung
  30 phút, pricePerHour / 2). Có test vector dùng chung để ba màn ra cùng một giá. Xử lý khung
  nằm giữa hai mức, bảng giá không phủ hết khung ("Liên hệ") và khung đã qua giờ.
- Giờ mở cửa (FR-007): hàm thuần tính "Đang mở cửa" / "Đã đóng cửa · Mở lại HH:mm" theo
  Asia/Ho_Chi_Minh với AppClock, đúng cả trường hợp mở qua nửa đêm và mở cả ngày, có unit test.
  Sân con: chỉ đếm sân đang hoạt động, gộp theo loại mặt sân (FR-006).
- Thư viện ảnh (FR-004): ViewPager2 và màn xem toàn màn hình có phóng to. Chọn thư viện tải
  ảnh/phóng to (ghi lý do vào Complexity Tracking nếu thêm thư viện), có ảnh mặc định khi lỗi
  và cache.
- Bản đồ và chỉ đường (FR-010, FR-011, FR-013): chọn dịch vụ bản đồ theo constitution, có
  phương án thay thế khi không có tài khoản thanh toán. Marker quả cầu lông dùng chung với 021.
  Không xin quyền vị trí. "Chỉ đường" dùng Intent tới ứng dụng bản đồ, không có ứng dụng thì mở
  trình duyệt. Bản đồ lỗi không ảnh hưởng các mục khác.
- Số điện thoại (FR-012): ACTION_DIAL không cần quyền; máy không gọi được thì sao chép số.
- Khách chưa đăng nhập (FR-019, FR-020, SC-004): mở rộng returnTo của 010 thành "thao tác
  đang chờ" (đặt sân / yêu thích / chat) kèm venueId và ngữ cảnh tìm kiếm, sống qua xoay màn
  hình và process death. Đăng nhập xong tự tiếp tục; hủy thì không làm gì. Chủ cơ sở (ownerId
  trùng currentUid) bị chặn đặt sân và chat (FR-018).
- Trạng thái cơ sở (FR-021 đến FR-023, SC-005): ACTIVE / bị ẩn / bị khóa / không còn tồn tại.
  Rules cho khách đọc venue ACTIVE, courts, priceRules, review VISIBLE và danh mục. Người chơi
  mở venue đang tạm ngừng qua liên kết trực tiếp (giống 070 R6, chờ D xác nhận). Kiểm tra lại
  trạng thái ngay trước khi mở lưới giờ. Kèm test Rules trên Emulator.
- Lỗi và offline (FR-025, FR-026, SC-007): mỗi mục có trạng thái đang tải / có dữ liệu / lỗi
  kèm "Thử lại" riêng. Mất mạng thì dùng cache offline của Firestore và hiện thông báo dữ liệu
  có thể chưa mới. Giữ vị trí cuộn và ảnh đang xem khi xoay màn hình.
- Dữ liệu cào: dùng seed của Duy ở scripts/seed/ (README.md, venues.json, catalog.json,
  seed.mjs: 10 sân thật từ Alobo, venues/demo-* kèm courts, priceRules; 22 quận, 4 tiện ích)
  làm dữ liệu demo cho Emulator và quickstart. Plan phải xử lý các điểm trong bảng "Những chỗ
  khác" của README seed:
  photoPaths là URL đầy đủ trên m-files.alobo.vn (path bắt đầu bằng http thì dùng nguyên,
  ngược lại ghép URL Supabase bucket venue-photos);
  closeTime/endTime "2400" cho mở tới nửa đêm, hàm giờ mở cửa và core/pricing phải hiểu giá trị này;
  số điện thoại bị che (0944xxx486), nếu không hợp lệ thì vẫn hiện nhưng không mở trình gọi;
  tọa độ gần đúng (approx);
  amenityIds rỗng thì mục tiện ích có trạng thái rỗng;
  mọi sân là PU;
  ownerId = demo-owner-1;
  quy ước daysOfWeek (seed dùng ISO, 040/041 ghi 1 = CN) cần chốt, vì ảnh hưởng nhóm ngày trong bảng giá;
  recurringPricePerHour chưa lên Firestore nên không hiển thị.
  Seed chưa có reviews, nên nêu cách có dữ liệu cho US4 (dùng seed reviews của 070 T025 hay viết thêm).
  Chọn vài cơ sở cụ thể làm ca kiểm thử cho quickstart (ví dụ giờ cao điểm, mở tới 24h, nhiều mức giá).
- UI theo specs/030-court-detail/ui (03_court-detail_buoc1_chi-tiet-san.png và bản chế độ tối,
  README.md), w600dp có ảnh và thông tin đặt cạnh nhau (FR-027), TalkBack và vùng chạm 48dp.
Ghi rõ những thay đổi cần cập nhật vào docs/design/database/README.md (trường, index, Rules)
và việc cần B, C, D phối hợp. B: tham số vào lưới giờ 040, core/pricing. C: màn danh sách đánh
giá và API yêu thích (070), lối vào chat (091), các trường điểm theo tiêu chí. D: trường venue
(ảnh có thứ tự, ownerId, status, giờ mở cửa, số điện thoại công khai), quyền đọc venue tạm ngừng.
```

### Tóm tắt phản hồi của AI

- **Các file đã sinh:** `plan.md`, `research.md` (R1–R14 kèm bảng phối hợp), `data-model.md`, `quickstart.md`, `contracts/navigation.md`, `contracts/ui-screens.md`, `contracts/security-rules.md`.
- **Đọc dữ liệu:** snapshot listener chỉ cho `venues/{id}` (bắt kịp khi cơ sở bị khóa, FR-023); `priceRules`, `courts`, 3 đánh giá mới nhất đọc bằng `get()` một lần. Khoảng 19–26 lượt đọc mỗi lần mở, tức khoảng 1.900–2.600 lần mở mỗi ngày trong hạn mức Spark; phần đầu ước lượng 0,7–1,5 giây trên 4G.
- **Bảng giá:** gộp các ngày liền nhau có cùng lịch giá thay vì gộp theo tập ngày của từng dòng (với sân Quang Vinh, cách thứ hai ra bảng lặp). Giá "Khung bạn chọn" gọi `SlotPricing` của `core/pricing`, có file test vector dùng chung cho 020/030/040.
- **Bản đồ:** chọn osmdroid vì nhóm không có tài khoản thanh toán Google Maps; bản đồ nhỏ không tương tác, chỉ khởi tạo khi sắp cuộn tới. "Chỉ đường" dùng Intent `geo:`, không có ứng dụng bản đồ thì mở URL trong trình duyệt.
- **Ảnh:** Coil, `PhotoUrlResolver` ở `core/media` xử lý cả URL đầy đủ lẫn path Supabase; tự viết `ZoomableImageView` vì các thư viện phóng to cho View đã ngừng bảo trì.
- **Khách:** không cần mở rộng `returnTo`. Cơ chế `nav_auth` chồng lên của 010 R15 giữ nguyên ViewModel, nên chỉ cần lưu `pendingAction` vào `SavedStateHandle`; đăng nhập xong thì chạy, quay lại khi vẫn là khách thì hủy.
- **Rules đề nghị D, C** (kèm test VD-1 → VD-9): khách `get` được cơ sở `ACTIVE` và biết cơ sở không tồn tại; người đã đăng nhập `get` mọi trạng thái; khách đọc được đánh giá `VISIBLE`.
- **Phát hiện vênh giữa các spec:**
  - action 040 thiếu tham số ngày và khung giờ;
  - quy ước `daysOfWeek` chưa thống nhất (điểm chặn);
  - Rules `reviews` của 070 bắt đăng nhập, trái FR-019;
  - 070 coi `DELETED` là "tạm ngừng";
  - US5-7 không xảy ra đúng như spec viết vì chủ sân đăng nhập từ chế độ khách bị chuyển sang giao diện chủ sân.
- **Constitution VI:** seed lấy ảnh, tên, số điện thoại của sân thật từ Alobo (app tham khảo); AI đánh dấu ⚠️ và đề xuất nhóm quyết định trước buổi demo.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Listener cho venue, `get()` cho phần còn lại | Dùng | Đủ cho FR-023 mà ít lượt đọc nhất |
| osmdroid thay Google Maps SDK | Dùng | Đúng phương án thay thế trong constitution, không cần thẻ |
| `ZoomableImageView` tự viết | Dùng | Không thêm phụ thuộc đã ngừng bảo trì |
| `pendingAction` trong `SavedStateHandle` thay vì mở rộng `returnTo` như prompt yêu cầu | Dùng | Đơn giản hơn, không phải sửa `SessionRouter` của 010 |
| Nhận định ban đầu "tasks 011 chưa có bước ẩn danh đánh giá" | Bỏ, đã sửa | Mình yêu cầu kiểm tra lại: 011 research R12 đã có `anonymizeReviews` (C viết) |
| Rules đề nghị cho `venues`, `reviews` | Chờ xác nhận | D và C là người phụ trách, chốt ở họp nhóm |
| Quy ước `daysOfWeek` | Chờ xác nhận | B và D chốt, ISO được đề xuất |
| Thay ảnh/dữ liệu Alobo trong seed | Chờ cả nhóm | Liên quan constitution VI, không chặn code 030 |
| Sửa câu US5-7 trong spec | Chờ thực hiện | Sửa khi chạy `/speckit-analyze` |

### Cách kiểm chứng

- Đối chiếu Constitution Check với nguyên tắc I–VII trong `.specify/memory/constitution.md`.
- Tự tính lại các ca giá P1–P9 trong `quickstart.md` từ `scripts/seed/venues.json` (ví dụ P2: 90.000 + 130.000 chia 2 giờ = 110.000 đ/giờ).
- Kiểm tra các contract được dẫn: 020 `ui-screens.md` §1 (tham số vào), 040 `ui-contracts.md` §1.1 (action chỉ có `venueId`), 070 `security-rules.md` (đọc `reviews` cần đăng nhập), 091 tasks T030 (deep link chat).
- Việc cần làm tiếp: gửi bảng phối hợp trong `plan.md` cho B, C, D; chạy `/speckit-tasks`.

## Mục 22: Chia task spec 030 bằng `/speckit-tasks`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-25 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 03 – Chi tiết sân (`specs/030-court-detail`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-tasks` |
| Mục đích | Chia plan 030 thành task theo user story, viết test trước, ghi rõ task phụ thuộc B, C, D kèm phương án tạm |
| Nhánh Git | `feature/03-court-detail-plan` |

### Prompt đã dùng

```text
/speckit-tasks SPECIFY_FEATURE_DIRECTORY=specs/030-court-detail
Chia task theo user story (US1–US6), viết test trước (chạy thấy fail) rồi mới cài đặt. Trước khi chia task,
sửa spec.md theo plan: US5-7 thành "chủ sân ở chế độ người chơi xem cơ sở của mình" (research R11), thêm
trạng thái rỗng "Chưa có thông tin tiện ích" cho FR-005 (R13). Yêu cầu:
- Phase Setup: thêm osmdroid vào gradle/libs.versions.toml (ghi lý do theo Complexity Tracking), kiểm tra Coil
  và ViewPager2 đã có chưa (có rồi thì dùng lại, không thêm trùng), strings_court_detail.xml, drawable
  ic_marker_shuttlecock, img_venue_placeholder, ic_amenity_*.
- Phase Foundational, tách PR `chore(core)` riêng vì nhóm khác dùng:
  core/media/PhotoUrlResolver, core/model/VenueRatingSummary, core/ui/photo (PhotoViewerFragment,
  ZoomableImageView, ZoomMath có unit test), file test vector app/src/test/resources/pricing/
  window-price-vectors.json với 9 ca P1–P9 của quickstart. Nếu PR core phần 2 của 020 chưa có
  core/pricing, core/schedule, core/catalog, core/location thì viết đúng theo contract của 020, kèm ca
  "2400" cho TimeOfDay (research R5) và DayConvention cho daysOfWeek. Phần chưa có thì đánh dấu, phần đã
  có thì dùng lại, không làm trùng.
- Foundational của feature/courtdetail: VenueDetailRepository (interface) + FirestoreVenueDetailRepository
  (listener venue bằng callbackFlow, get() cho courts/priceRules, awaitWithTimeout), DTO, nav_court_detail.xml
  có deep link và action_global_photoViewer theo contracts/navigation.md, include vào nav_player.
- Hàm thuần có unit test trước: SearchWindowArgs, OpeningHours (dùng AppClock giả: 20:00/23:30, qua nửa
  đêm, 0000–2400), CourtSummary, PriceTableBuilder (ca sân Quang Vinh ra 3 nhóm), PhoneDisplay (số bị che
  thành DisplayOnly), PendingActionResolver (đủ các dòng trong bảng ở data-model §2.4).
- ViewModel test bằng Turbine với repository giả: từng section có Loading/Content/Empty/Error riêng, offline
  (isFromCache), SUSPENDED/GONE/SUSPENDED_GUEST, chủ cơ sở bị chặn, thao tác chờ chạy đúng một lần.
- Test Rules VD-1 → VD-9 trong firestore-tests/test/court-detail.test.ts. Ghi rõ test sẽ đỏ cho tới khi
  D (venues) và C (reviews) sửa Rules; không tự sửa firestore.rules của người khác.
- Seed đánh giá: task có điều kiện, tạo scripts/seed/seed-reviews-030.mjs (chỉ chạy với --emulator) khi
  seed đánh giá của 070 T025 chưa có; không sửa seed.mjs của Duy.
- Ghi rõ task phụ thuộc người khác kèm phương án tạm nếu chưa xong:
  B: tham số date/startTime/endTime cho lưới giờ 040 (chưa có thì chỉ truyền venueId, TODO(B)),
     create-booking từ chối cơ sở không ACTIVE, chốt daysOfWeek;
  C: ReviewRepository.latestVisible (chưa có thì truy vấn tạm, TODO(C)), Rules reviews cho khách đọc,
     deep link chat 091 (chưa có thì báo "Tính năng sắp có");
  D: Rules venues theo research R10, ratingSums trong đặc tả CSDL.
- Polish: layout-w600dp hai cột, chế độ tối, TalkBack, đo SC-001; chạy kịch bản Q1–Q24 trong quickstart
  trên Emulator với seed; chụp ảnh Q1, Q11, Q13, Q17 vào docs/features/03-court-detail/screenshots/;
  cập nhật docs/features/03-court-detail/README.md và cột Spec nhóm 03 trong docs/features/README.md;
  chạy ./gradlew lint testDebugUnitTest assembleDebug và npm test trong firestore-tests.
```

### Tóm tắt phản hồi của AI

- **Sửa `spec.md` trước khi chia task:** US5-7 viết lại thành chủ sân ở chế độ người chơi xem cơ sở của mình (kèm ghi chú 010 chuyển chủ sân sang `nav_owner`); FR-005 thêm "Chưa có thông tin tiện ích".
- **`tasks.md` 75 task (T001–T075), 45 task `[P]`:** Setup 5; Foundational 21 (2A PR `chore(core)` T006–T019, 2B repository + nav T020–T024, 2C Rules + seed đánh giá T025–T026); US1 13; US2 7; US3 6; US4 6; US5 6; US6 3; Polish 8.
- **Kiểm tra code hiện có:** Coil chưa có trong version catalog; ViewPager2 chỉ có gián tiếp qua Material 1.14.0; manifest chưa khai `<nav-graph>`; `core/pricing`, `schedule`, `catalog`, `location` chưa có (PR phần 2 của 020 chưa merge); `feature/review` và deep link chat 091 chưa có. Các task tương ứng ghi rõ phương án tạm và `TODO(B/C/D)`.
- **Bảng phụ thuộc:** liệt kê task chờ B (tham số 040, `daysOfWeek`), C (`latestVisible`, `FavoriteRepository`, chat, Rules `reviews`), D (Rules `venues`, `ratingSums`).
- **MVP đề xuất:** Setup + Foundational + US1.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Chia task theo US, test trước | Dùng | Đúng yêu cầu và constitution IV |
| Tách PR `chore(core)` (2A) | Dùng | Nhóm khác dùng `PhotoUrlResolver`, `VenueRatingSummary`, `PhotoViewerFragment` |
| Thêm Coil vào catalog, dùng ViewPager2 trực tiếp | Dùng | Đã kiểm tra catalog, không thêm trùng |
| Sửa US5-7 và FR-005 trong spec | Dùng | Khớp research R11, R13 |
| Thiếu test Repository, cách xử lý seed Alobo | Sửa ở Mục 23 | `/speckit-analyze` phát hiện |

### Cách kiểm chứng

- Đếm lại ID task liên tục T001–T075, mỗi task có checkbox, ID, nhãn `[USx]` đúng phase và đường dẫn file.
- Đối chiếu từng FR/SC của spec với task; đối chiếu ca P1–P9 trong T008 với `quickstart.md`.
- Kiểm tra `gradle/libs.versions.toml` và `AndroidManifest.xml` để xác nhận nhận định về Coil, ViewPager2, `<nav-graph>`.

## Mục 23: Phân tích chéo spec 030 bằng `/speckit-analyze` và sửa theo kết quả

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-25 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 03 – Chi tiết sân (`specs/030-court-detail`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-analyze` |
| Mục đích | Tìm chỗ mâu thuẫn, thiếu sót giữa `spec.md`, `plan.md`, `tasks.md` và constitution trước khi cài đặt, rồi sửa theo kết quả |
| Nhánh Git | `feature/03-court-detail-plan` |

### Prompt đã dùng

```text
/speckit-analyze
```

Sau khi xem báo cáo, mình trả lời để AI sửa:

```text
C1 tôi dùng vài dữ liệu để mà phục vụ đồ án để show lên, sau đó thì tôi sẽ vẫn phải tạo sân,
ở mục tài khoản chủ sân. C2 thì theo theo đề xuất và các cái kia cũng theo đề xuất
```

### Tóm tắt phản hồi của AI

- **Báo cáo (chỉ đọc), 17 phát hiện:**
  - CRITICAL: C1 seed Alobo trái constitution VI; C2 Repository không có test (constitution IV bắt buộc).
  - HIGH: I1 khách mở cơ sở `DELETED` bị chặn nên thấy "tạm ngừng" thay vì "không còn tồn tại"; I2 regex số điện thoại loại số bàn 11 chữ số; C3 thiếu ghi nguồn OpenStreetMap.
  - MEDIUM: U1 vòng đời listener; U2 chưa rõ tầng nào quyết định "khách"; U3 khung giờ hết hạn khi đang mở màn hình; G1 SC-003 không có task; G2 SC-001 chỉ đo 5 lần; T1 tên interface lệch giữa các file.
  - LOW: G3 FR-002 (Back), L1 deep link `auth` lộ ra ngoài khi thêm `<nav-graph>`, L2 màu trình xem ảnh hardcode, L3 rà `TODO` còn sót.
- **Sửa theo quyết định của mình:**
  - C1: ghi quyết định vào research R13 (seed chỉ dùng demo đồ án, cơ sở thật do chủ sân tạo qua spec 111); plan ghi ngoại lệ constitution VI ở Complexity Tracking; thêm T077 (tạo cơ sở qua giao diện 111 rồi chạy lại Q1, Q6, Q9); ảnh chụp ghi nguồn datlich.alobo.vn.
  - C2: thêm T076 test `FirestoreVenueDetailRepositoryTest` trên Emulator.
  - I1: Rules cho khách `get` cơ sở `DELETED`, thêm test VD-10.
  - I2: regex `^(0|\+84)\d{9,10}$`, thêm ca số bàn.
  - C3: "© OpenStreetMap contributors" ở R7, T003, T050.
  - U1, U2, U3, G1, G2, T1, G3, L1–L3: sửa task và tài liệu tương ứng; thêm T078 (thử SC-003 với 5 người), Q25, Q26 trong quickstart.
- `tasks.md` còn 78 task; T076–T078 đặt ID nối tiếp để không đánh số lại.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Đề xuất thay ảnh seed hoặc sửa constitution (C1) | Sửa | Mình giữ seed để demo đồ án, cơ sở thật tạo qua tài khoản chủ sân (111); ghi rõ là ngoại lệ |
| Test Repository trên Emulator (C2) | Dùng | Đúng constitution IV |
| Rules cho khách đọc `DELETED` (I1) | Dùng, chờ D xác nhận | D phụ trách Rules `venues` |
| Các đề xuất HIGH/MEDIUM/LOW còn lại | Dùng | Theo đề xuất của AI |

### Cách kiểm chứng

- Kiểm tra lại `tasks.md`: 78 dòng task, không trùng ID.
- `grep` để chắc không còn "VD-1 → VD-9", regex `\d{9}$` cũ hay dòng "Cả nhóm quyết định" chưa cập nhật.
- Tự kiểm tra số `028 3812 3456` bỏ khoảng trắng có 11 chữ số, khớp `0` + 10 chữ số.
- Việc cần làm tiếp: gửi D đề nghị Rules (VD-10) và việc T077; bắt đầu `/speckit-implement` từ Setup.

## Mục 24: Cài đặt Phase 1, 2A spec 011 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-25 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Cài phần nền của spec 011: thư viện, chuỗi, index (Phase 1) và phần mở rộng `core/session` cả nhóm dùng (Phase 2A), tách PR `chore(core)` |
| Nhánh Git | `feature/01-account-profile-core` (tạo từ `main` mới nhất) |

### Prompt đã dùng

```text
/speckit-implement SPECIFY_FEATURE_DIRECTORY=specs/011-account-profile
Cài đặt spec 011 theo tasks.md, phạm vi tuần 4: Phase 1 (T001–T005), Phase 2A (T006–T019),
Phase 2C (T027–T030), US1 sửa hồ sơ (T031–T051), US2 đăng xuất (T052–T056). Không làm 2B, US3–US7
và Polish. Làm đúng thứ tự: test viết trước, chạy thấy fail rồi mới cài đặt; đánh dấu [x] từng
task trong tasks.md khi xong.

Nhánh và PR:
- Từ main mới nhất tạo nhánh feature/01-account-profile-core cho Phase 1 + 2A. Xong 2A thì dừng,
  chạy ./gradlew testDebugUnitTest (test 010 vẫn phải xanh), báo mình để commit và mở PR
  chore(core). Commit theo Conventional Commits, nội dung tiếng Việt, scope account hoặc core.
- Sau đó tạo nhánh feature/01-account-profile từ nhánh core cho 2C + US1 + US2.

Lưu ý khi làm:
- 011 xây trên 010: chỉ THÊM vào core/session, MainActivity, firestore.rules; không đổi chữ ký đã
  công bố cho nhóm. Hàm mới trong interface SessionManager phải có thân mặc định (T014) để fake
  của B, C, D vẫn biên dịch.
- T001: storage-kt đã có trong gradle/libs.versions.toml (BoM supabase 3.6.0), không thêm trùng.
  Chỉ thêm com.vanniktech:android-image-cropper. Không build được với AGP 9.4 thì dừng và báo
  mình, không tự đổi thư viện khác.
- T004: KHÔNG tạo scripts/seed/districts.ts. Seed của Duy (scripts/seed/seed.mjs, catalog.json)
  đã nạp 22 quận cùng ID. Không sửa seed.mjs. Seed ghi cityName = "TP. Hồ Chí Minh", data-model
  011 ghi city = "HCM": đối chiếu xem app 011 có đọc trường này không. Nếu không đọc thì sửa
  data-model 011 cho khớp seed; nếu có đọc thì báo mình để hỏi Duy. Bản ghi q-cu (isActive =
  false) chỉ tạo trong dữ liệu test (firestore-tests, unit test), không thêm vào seed. Vẫn thêm
  3 index vào firestore.indexes.json như T004.
- T005: chỉ chạy trên Firebase Emulator và Supabase local, không chạy trên mobile-22e6c hay
  Supabase smashnow thật. Supabase local không nhận token của Auth Emulator thì ghi rõ kết quả,
  không tự đổi cách xác thực.
- T039 sửa firestore.rules: chỉ thêm phần match users/{uid} và districts theo
  contracts/security-rules.md; bộ test U1–U18 của 010 vẫn phải xanh.
- T040 migration bucket avatars: tạo file migration và chạy pgTAP trên Supabase local. Không
  apply lên project smashnow thật.
- Deep link của B, C, D chưa có (ví, đơn của tôi, yêu thích…) thì ẩn mục đó theo R14, không tạo
  destination giả.
- Giao diện theo wireframe trong specs/011-account-profile/ui, đủ 4 trạng thái (đang tải / có dữ
  liệu / rỗng / lỗi), chế độ tối, vùng chạm 48dp, chuỗi trong strings_account.xml.
- Task nào bị chặn vì phụ thuộc người khác thì ghi [BLOCKED: B/C/D] kèm lý do trong tasks.md và
  làm tiếp task khác.

Cuối mỗi checkpoint (Phase 1, 2A, 2C, US1, US2): chạy ./gradlew lint testDebugUnitTest
assembleDebug, cd firestore-tests && npm test, npx supabase test db; báo số test xanh/đỏ và kịch
bản quickstart Q1–Q12 đã chạy hay chưa. Không tự push
```

AI dừng sau Phase 2A như prompt yêu cầu; mình trả lời "tôi đồng ý" để commit và chuyển sang lần cài đặt tiếp theo (Mục 25).

### Tóm tắt phản hồi của AI

- **Phase 1:** thêm `com.vanniktech:android-image-cropper` 4.7.0 (bản cuối biên dịch bằng Kotlin ≤ 2.3), quyền `CAMERA`, `FileProvider` cho `cacheDir/avatar/`, bộ chuỗi `strings_account.xml`, 3 index trong `firestore.indexes.json`. Không tạo `districts.ts`; app 011 không đọc trường thành phố nên sửa data-model 011 thành `cityName` cho khớp seed của Duy.
- **T005:** Supabase local (CLI 2.121.0) từ chối token của Auth Emulator (`"alg" (Algorithm) Header Parameter value not allowed`). Ghi kết quả vào `tasks.md`, không tự đổi cách xác thực.
- **Phase 2A:** `MyProfile` thêm trường có giá trị mặc định; `SignOutReason` thêm `SESSION_REVOKED`, `ACCOUNT_DELETED`; `SignOutHook` (chạy trước khi đăng xuất, tối đa 5 giây mỗi hook, bỏ qua lỗi); `SessionManager.signOut(reason)` có thân mặc định; `AppModeController` (chế độ người chơi / quản lý sân, chỉ trong bộ nhớ); `StorageUrls.publicUrl`; `MainActivity` điều hướng theo `(appRole, mode)`; màn hình đăng nhập báo "Phiên đăng nhập đã hết hạn".
- Commit `67f678b` (`chore(core)`). Kết quả: 199/199 unit test, Lint 0 Error, `firestore-tests` 27/27.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Phần mở rộng `core/session` | Dùng | Chỉ thêm, không đổi chữ ký cũ; fake của nhóm vẫn biên dịch |
| `modeFor(state)` tính đồng bộ | Dùng | Tránh chủ sân vừa đăng nhập bị nháy qua `nav_player` |
| Sửa `when` trong `CompleteSignupUseCase` khi thêm lỗi mới | Dùng, cần báo nhóm | Ai dùng `when` đầy đủ trên `AppError` sẽ phải thêm nhánh |
| Push và mở PR | Mình tự làm | Prompt yêu cầu không tự push |

### Cách kiểm chứng

- Chạy `./gradlew lint testDebugUnitTest assembleDebug` và `firestore-tests` (kết quả ở trên).
- Đọc diff commit `67f678b`, đối chiếu `tasks.md` (T001–T019) và `contracts/ui-screens.md` §4.
- Việc cần làm tiếp: cả nhóm chốt cách thử Storage khi Supabase local không nhận token Emulator.

## Mục 25: Cài đặt Phase 2C, US1, US2 spec 011 bằng `/speckit-implement`

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-25 |
| Người thực hiện | Thành Dự |
| Nhóm tính năng liên quan | 01 – Tài khoản (`specs/011-account-profile`) |
| Công cụ AI | Claude Code (model Claude Opus 5.5), lệnh Spec Kit `/speckit-implement` |
| Mục đích | Trang Tài khoản và điều hướng (2C), xem và sửa hồ sơ kèm ảnh đại diện (US1), đăng xuất (US2) |
| Nhánh Git | `feature/01-account-profile` (tạo từ `feature/01-account-profile-core`) |

### Prompt đã dùng

```text
/speckit-implement SPECIFY_FEATURE_DIRECTORY=specs/011-account-profile
Tiếp tục spec 011 trên nhánh feature/01-account-profile (tạo từ feature/01-account-profile-core, đã
xong Phase 1 + 2A, T001–T019 đã [x]). Phạm vi lần này: Phase 2C (T027–T030), US1 sửa hồ sơ
(T031–T051), US2 đăng xuất (T052–T056). Không làm 2B, US3–US7 và Polish. Test viết trước, chạy thấy
fail rồi mới cài đặt; đánh dấu [x] từng task khi xong.

Dùng lại phần 2A, không viết lại: StorageUrls.publicUrl, SignOutHook (@Binds @IntoSet),
SessionManager.signOut(reason), AppModeController, MyProfile đã có email/phone/walletBalance/
ownerStatus. Đọc lại tasks.md (ghi chú implement ở T004, T005, T015, T018) trước khi làm.

Lưu ý khi làm:
- Coil chưa có trong version catalog (plan ghi nhầm là có sẵn). Thêm io.coil-kt.coil3:coil và
  coil-network-okhttp bản 3.4.0 ở T029 (bản cuối dùng Kotlin 2.3; 3.5+ cần Kotlin 2.4 nên không dùng
  được với AGP 9.4.1). Ghi lý do trong catalog và plan Complexity Tracking. Nếu nhánh 030 đã thêm
  Coil thì dùng chung một khai báo, không thêm trùng.
- T028: nav_account include vào nav_player (tab "Tài khoản") và nav_owner. Destination của US3–US7
  (đổi mật khẩu, đổi email, xóa tài khoản, reauth) chưa làm thì để Fragment khung, mục tương ứng ẩn
  trên trang Tài khoản. Thêm deep link smashnow://legal/{doc} vào destination legal của nav_auth.
- T029: deep link của B, C, D chưa có destination (ví, đơn của tôi, vãng lai, yêu thích, cài đặt
  thông báo, chủ sân) thì ẩn theo R14, kiểm bằng navController.graph.hasDeepLink; không tạo
  destination giả. Mục "Điều khoản" mở bằng deep link, không import LegalDocumentFragment.
- T031 Rules và T039 sửa firestore.rules: chỉ thêm phần users/{uid} và districts theo
  contracts/security-rules.md. Bộ U1–U18 của 010 vẫn phải xanh. Dữ liệu test có districts/q-cu
  (isActive = false) trong firestore-tests, không thêm vào seed của Duy.
- T032 pgTAP và T040 migration bucket avatars: chỉ chạy trên Supabase local (npx supabase test db,
  supabase db reset nếu cần). Không apply lên project smashnow thật.
- Supabase local không nhận token của Auth Emulator (T005), nên ảnh đại diện trên máy ảo (Q5, Q8,
  Q9) chưa chạy được; ghi [BLOCKED: cả nhóm] ở các kịch bản đó, phần code và unit test vẫn làm đủ.
- T043 ImageProcessor: test được phần thuần (tính kích thước thu nhỏ, chọn chất lượng nén) bằng
  unit test; phần ImageDecoder/Bitmap để kiểm tay.
- T054 FcmSignOutHook bind @IntoSet trong module của core; ghi Firestore lỗi thì vẫn deleteToken().
- Giao diện theo wireframe ui/01_account_buoc1_tai-khoan.png và buoc2_sua-ho-so.png: đủ trạng thái
  đang tải / có dữ liệu / lỗi + "Thử lại" (trang Tài khoản không có trạng thái rỗng), chế độ tối,
  w600dp giới hạn 480dp (auth_form_max_width), vùng chạm 48dp, contentDescription cho ảnh, chuỗi
  trong strings_account.xml (đã có ở T003, thiếu thì thêm vào đó).
- Firestore Emulator đang chạy nền thì npm test trong firestore-tests báo trùng cổng 8080: tắt
  emulator trước, hoặc chạy FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npx vitest run.
- Task bị chặn vì người khác thì ghi [BLOCKED: B/C/D] kèm lý do, làm tiếp task khác.

Cuối mỗi checkpoint (2C, US1, US2): chạy ./gradlew lint testDebugUnitTest assembleDebug, test Rules
trong firestore-tests, npx supabase test db; báo số test xanh/đỏ, lỗi Lint mức Error, và kịch bản
quickstart Q1–Q12 nào đã chạy, chưa chạy hay bị chặn. Không tự commit, không tự push.
```

### Tóm tắt phản hồi của AI

- **2C:** `nav_account.xml` include vào `nav_player`, `nav_owner`; nút "Tài khoản" tạm trên màn hình chủ của 010; deep link `smashnow://legal/{doc}`. `AccountEntry` + `accountEntries(...)`: mục ẩn khi không có provider `password`, khi là admin, hoặc khi deep link chưa có destination. `AccountFragment` có skeleton, lỗi + "Thử lại" (quá 15 giây chưa có hồ sơ). Thêm Coil 3.4.0.
- **US1:**
  - Rules: kiểm tra `level`, `favoriteDistrictIds` (0–5, không trùng, khu vực đang hoạt động), `avatarPath`; thêm `match /districts`, `/accountDeletions`. Test P1–P18, D1–D4, A1 kèm vài ca thêm.
  - Storage: migration bucket `avatars` và pgTAP ST1–ST8, chỉ chạy trên Supabase local.
  - App: `DistrictRepository`, `AccountRepository` (chỉ ghi trường đổi), `AvatarRepository` (Supabase), `ImageProcessor` (512 px, JPEG 85→50, ≤ 2 MB), đổi ảnh theo thứ tự R3. Màn hình sửa hồ sơ, chọn khu vực, chọn nguồn ảnh, cắt ảnh vuông. Trang Tài khoản có chip trình độ/khu vực, thẻ ví, banner xác minh email.
- **US2:** `FcmSignOutHook` (gỡ token khỏi `users/{uid}`, lỗi ghi vẫn xóa token trên máy), `GoogleSignOutHook`; nút "Đăng xuất" có hộp thoại xác nhận, bấm lặp chỉ đăng xuất một lần.
- Kết quả cuối: 264/264 unit test, Lint 0 Error, Rules 55/55, pgTAP 8/8. Chưa chạy kịch bản Q1–Q12 trên máy ảo; Q5, Q8, Q9 ghi `[BLOCKED: cả nhóm]`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Code 2C, US1, US2 | Dùng | Test viết trước, chạy thấy đỏ rồi mới xanh; bám wireframe buoc1, buoc2 |
| Sửa dữ liệu mẫu test U8 của 010 | Dùng | 011 siết kiểm tra `avatarPath` và khu vực; ý nghĩa U8 không đổi |
| Làm sớm model T022 và kiểu `AccountError` (T023) | Dùng | US1 không biên dịch được nếu thiếu; mapper lỗi để lần sau |
| Mục US5–US7 ẩn nhờ deep link `smashnow://account/...` chưa khai báo | Dùng | Story nào xong thì thêm deep link là mục hiện |
| T056 (Espresso đăng xuất) | Chưa làm | Cần máy ảo đang chạy |

### Cách kiểm chứng

- Chạy lại `./gradlew lint testDebugUnitTest assembleDebug`, `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npx vitest run`, `npx supabase test db` (kết quả ở trên).
- Đối chiếu `tasks.md` (T027–T055 đã `[x]`, có ghi chú implement) với `contracts/ui-screens.md`, `security-rules.md`, `storage-avatars.md`.
- Việc cần làm tiếp: chạy app trên máy ảo để kiểm giao diện (Q1–Q4, Q6, Q7, Q10–Q12), chạy T056.
