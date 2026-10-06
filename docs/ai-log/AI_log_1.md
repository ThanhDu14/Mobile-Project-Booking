# Nhật ký sử dụng AI – Thành viên 1 (Thành Dự)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 1**. Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-05 | Viết spec 010 (đăng ký, đăng nhập) bằng `/speckit-specify` + `/speckit-clarify` | 01 |
| 2 | 2026-10-05 | Viết spec 011 (hồ sơ, quản lý tài khoản) bằng `/speckit-specify` | 01 |
| 3 | 2026-10-05 | Đối chiếu spec 010 với proposal bằng `/speckit-analyze` | 01 |
| 4 | 2026-10-05 | Viết spec 020 (tìm kiếm sân) bằng `/speckit-specify` | 02 |
| 5 | 2026-10-05 | Viết spec 022 (tìm sân thông minh – AI Assistant) bằng `/speckit-specify` | 02 |
| 6 | 2026-10-06 | Viết spec 030 (chi tiết sân) bằng `/speckit-specify` | 03 |

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

## Mục 6: Viết spec 030 (chi tiết sân) bằng `/speckit-specify`

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
