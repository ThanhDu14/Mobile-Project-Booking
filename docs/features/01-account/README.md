# Nhóm 1 — Tài khoản

| Thuộc tính | Giá trị                                                                  |
|---|--------------------------------------------------------------------------|
| Mã nhóm | `01`                                                                     |
| Vai trò sử dụng | Người chơi                                                               |
| Thành viên phụ trách | Nguyễn Thành Dự                                                          |
| App tham khảo | Alobo                                                                    |
| Trạng thái | ⬜ Chưa bắt đầu                                                           |
| Nhánh Git | `feature/01-<ten-tinh-nang>`                                             |
| Spec (Spec Kit) | Đã  chạy `/speckit-specify` tại `specs/010-account-auth/spec.md`  |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Đăng ký bằng email hoặc số điện thoại (mọi tài khoản mới là người chơi, không chọn vai trò khi đăng ký)
- [ ] Sau đăng nhập, điều hướng theo vai trò: chủ sân đã duyệt → giao diện chủ sân, người chơi → giao diện người chơi, admin → quản trị
- [ ] Đăng nhập bằng email/mật khẩu, số điện thoại, Google
- [ ] Xác thực OTP
- [ ] Quên mật khẩu, đặt lại mật khẩu
- [ ] Xem và chỉnh sửa hồ sơ (ảnh đại diện, tên, trình độ, khu vực hay chơi)
- [ ] Đổi mật khẩu, đăng xuất
- [ ] Chuyển chế độ "Người chơi / Quản lý sân" với tài khoản chủ sân đã được duyệt
- [ ] Xem trạng thái hồ sơ chủ sân (chờ duyệt, đã duyệt, bị từ chối kèm lý do) và nộp lại hồ sơ

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `01_account_buocX_mo-ta.png`.

| Bước | Ảnh | Mô tả |
|---|---|---|
| 1 | | |

## Ghi chú thiết kế / quyết định

- 

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
