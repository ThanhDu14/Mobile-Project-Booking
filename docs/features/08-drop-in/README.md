# Nhóm 8 — Đặt lịch vãng lai

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `08` |
| Vai trò sử dụng | Người chơi |
| Thành viên phụ trách | C |
| App tham khảo | Alobo |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/08-<ten-tinh-nang>` |
| Spec (Spec Kit) | _Chưa tạo — chạy `/speckit-specify` rồi dán link `specs/NNN-.../spec.md` vào đây_ |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Danh sách buổi vãng lai do sân/CLB mở (ngày, giờ, sân, trình độ, số chỗ còn lại, giá theo lượt)
- [ ] Lọc theo khu vực, ngày, khung giờ, trình độ, mức giá
- [ ] Đăng ký chơi theo lượt: chọn buổi, số người (đi một mình hoặc kèm bạn), xác nhận
- [ ] Thanh toán theo lượt (giả lập) hoặc trả tại sân, xem vé lượt và mã QR check-in
- [ ] Xếp hàng chờ khi buổi đã đủ người, tự động thông báo khi có người hủy
- [ ] Hủy đăng ký theo chính sách, xem lịch sử các buổi đã tham gia
- [ ] Đánh giá buổi chơi sau khi tham gia

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `08_drop-in_buocX_mo-ta.png`.

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
