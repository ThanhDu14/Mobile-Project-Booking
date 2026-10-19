# Nhóm 1 — Tài khoản

| Thuộc tính | Giá trị                                                                  |
|---|--------------------------------------------------------------------------|
| Mã nhóm | `01`                                                                     |
| Vai trò sử dụng | Người chơi                                                               |
| Thành viên phụ trách | Nguyễn Thành Dự                                                          |
| App tham khảo | Alobo                                                                    |
| Trạng thái | 🟨 Đang làm (spec 010 đã cài đặt, chờ kiểm thử trên máy; spec 011 chưa làm) |
| Nhánh Git | `feature/01-<ten-tinh-nang>`                                             |
| Spec (Spec Kit) | [010 Đăng ký và đăng nhập](../../../specs/010-account-auth/spec.md) ([plan](../../../specs/010-account-auth/plan.md), [tasks](../../../specs/010-account-auth/tasks.md)) · [011 Hồ sơ tài khoản](../../../specs/011-account-profile/spec.md) |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [x] Đăng ký bằng email hoặc số điện thoại (mọi tài khoản mới là người chơi, không chọn vai trò khi đăng ký) — spec 010
- [x] Sau đăng nhập, điều hướng theo vai trò: chủ sân đã duyệt → giao diện chủ sân, người chơi → giao diện người chơi, admin → quản trị — spec 010
- [x] Đăng nhập bằng email/mật khẩu, số điện thoại, Google — spec 010
- [x] Xác thực OTP — spec 010 (chỉ số thử nghiệm trên gói Spark)
- [x] Quên mật khẩu, đặt lại mật khẩu — spec 010
- [ ] Xem và chỉnh sửa hồ sơ (ảnh đại diện, tên, trình độ, khu vực hay chơi)
- [ ] Đổi mật khẩu, đăng xuất
- [ ] Chuyển chế độ "Người chơi / Quản lý sân" với tài khoản chủ sân đã được duyệt
- [ ] Xem trạng thái hồ sơ chủ sân (chờ duyệt, đã duyệt, bị từ chối kèm lý do) và nộp lại hồ sơ

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `01_account_buocX_mo-ta.png`.

Wireframe của spec 010 nằm ở [`specs/010-account-auth/ui/`](../../../specs/010-account-auth/ui/). Ảnh chụp app thật cho kịch bản M1, M7, M12, M18 ([quickstart §4](../../../specs/010-account-auth/quickstart.md#4-kịch-bản-kiểm-thử-thủ-công-trên-máy-với-project-thật-tài-khoản-thử)) **chưa có**, chụp khi chạy kiểm thử thủ công (T076).

| Bước | Wireframe | Mô tả |
|---|---|---|
| 1 | [`01_account_buoc1_man-chao.png`](../../../specs/010-account-auth/ui/01_account_buoc1_man-chao.png) | Màn hình chào, "Xem sân trước" vào chế độ khách |
| 2 | [`01_account_buoc2_dang-nhap.png`](../../../specs/010-account-auth/ui/01_account_buoc2_dang-nhap.png) | Đăng nhập email, Google, số điện thoại (có bản chế độ tối) |
| 3 | [`01_account_buoc3_dang-ky-email.png`](../../../specs/010-account-auth/ui/01_account_buoc3_dang-ky-email.png) | Đăng ký email, lỗi "Email đã được sử dụng" |
| 4 | [`01_account_buoc4_dang-nhap-so-dien-thoai.png`](../../../specs/010-account-auth/ui/01_account_buoc4_dang-nhap-so-dien-thoai.png) | Nhập số điện thoại |
| 5 | [`01_account_buoc5_nhap-otp-sai-ma.png`](../../../specs/010-account-auth/ui/01_account_buoc5_nhap-otp-sai-ma.png) | Nhập mã OTP, báo số lần còn lại |
| 6 | [`01_account_buoc6_hoan-tat-tai-khoan.png`](../../../specs/010-account-auth/ui/01_account_buoc6_hoan-tat-tai-khoan.png) | Hoàn tất tài khoản sau Google/OTP lần đầu |
| 7 | [`01_account_buoc7_quen-mat-khau.png`](../../../specs/010-account-auth/ui/01_account_buoc7_quen-mat-khau.png) | Quên mật khẩu, đếm 60 giây trước khi gửi lại |

## Ghi chú thiết kế / quyết định

- Cấu hình Firebase Auth (Email/Password, Google, Phone, số thử nghiệm, Password policy, SHA-1): [quickstart §0](../../../specs/010-account-auth/quickstart.md#0-chuẩn-bị) và [hướng dẫn cấu hình Firebase](../../guides/firebase-setup.md).
- Khởi tạo tài khoản bằng Edge Function `on-signup` (gói Spark không có trigger Auth); app nhận biết "chưa khởi tạo" qua claim `appRole` ([research R1, R4](../../../specs/010-account-auth/research.md)).
- Gói Spark chỉ gửi OTP tới số thử nghiệm; app tự giới hạn 5 phút và 5 lần nhập sai ([research R8](../../../specs/010-account-auth/research.md#r8-otp-số-điện-thoại-fr-016-fr-017-analyze-f4)).
- Chế độ khách: chưa đăng nhập vẫn xem được trang chủ; tính năng cần tài khoản dùng `LoginPromptView` ([research R15](../../../specs/010-account-auth/research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026)).

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
