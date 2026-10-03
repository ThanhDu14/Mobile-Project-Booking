# Nhóm 11 — Quản lý sân (chủ sân)

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `11` |
| Vai trò sử dụng | Chủ sân |
| Thành viên phụ trách | D |
| App tham khảo | Tự thiết kế (app tham khảo không có / không cho dùng thử) |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/11-<ten-tinh-nang>` |
| Spec (Spec Kit) | _Chưa tạo — chạy `/speckit-specify` rồi dán link `specs/NNN-.../spec.md` vào đây_ |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Đăng ký chủ sân: hồ sơ cơ sở (tên, địa chỉ ghim bản đồ, số sân, giờ mở cửa, người đại diện, SĐT), ảnh cơ sở và giấy tờ
- [ ] Theo dõi trạng thái duyệt; khi chờ duyệt chỉ dùng như người chơi
- [ ] Sau khi được duyệt: nhận thông báo và mở khóa chức năng quản lý
- [ ] Thêm, sửa, ẩn cơ sở; quản lý từng sân con
- [ ] Ghim vị trí cơ sở trên bản đồ hoặc nhập địa chỉ để lấy tọa độ
- [ ] Thiết lập bảng giá theo khung giờ và ngày trong tuần
- [ ] Khóa giờ (bảo trì, sự kiện, khách đặt trực tiếp)
- [ ] Duyệt hoặc từ chối đơn đặt
- [ ] Xem lịch tổng hợp theo ngày, tuần
- [ ] Mở buổi vãng lai; xem và duyệt danh sách đăng ký, check-in bằng QR
- [ ] Tạo voucher khuyến mãi
- [ ] Thống kê doanh thu, tỉ lệ lấp đầy, khung giờ đông nhất (biểu đồ)

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `11_court-owner_buocX_mo-ta.png`.

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
