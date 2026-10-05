# Nhật ký sử dụng AI – Thành viên 1 (Thành Dự)

Mỗi thành viên ghi log vào một file riêng theo số của mình; file này là của **thành viên số 1**. Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| # | Ngày | Nội dung | Nhóm |
|---|---|---|---|
| 1 | 2026-10-05 | Viết spec 010 (đăng ký, đăng nhập) bằng `/speckit-specify` + `/speckit-clarify` | 01 |
| 2 | 2026-10-05 | Viết spec 011 (hồ sơ, quản lý tài khoản) bằng `/speckit-specify` | 01 |

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
