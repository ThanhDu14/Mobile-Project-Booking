# Nhóm 4 — Đặt sân

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `04` |
| Vai trò sử dụng | Người chơi |
| Thành viên phụ trách | B |
| App tham khảo | Alobo |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/04-<ten-tinh-nang>` |
| Spec (Spec Kit) | - [`040: Lưới khung giờ & giữ chỗ`](../../../specs/040-booking-slot-grid/spec.md) (đã clarify)<br/>- [`041: Đặt cố định theo tuần`](../../../specs/041-weekly-recurring-booking/spec.md) (đã clarify) |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Chọn ngày trên lịch
- [ ] Xem lưới khung giờ theo từng sân (trống / đã đặt / khóa)
- [ ] Chọn nhiều sân, nhiều khung giờ liên tiếp trong một đơn
- [ ] Đặt cố định theo tuần (ví dụ mỗi tối thứ Ba)
- [ ] Màn hình xác nhận đơn, tổng tiền
- [ ] Giữ chỗ tạm thời trong 5 phút trong lúc thanh toán
- [ ] Chống trùng lịch khi nhiều người đặt cùng lúc

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `04_booking_buocX_mo-ta.png`.

| Bước | Ảnh | Mô tả |
|---|---|---|
| 1 | | |

## Ghi chú thiết kế / quyết định

- **Độ dài khung giờ (Slot Duration)**: Cố định **30 phút** toàn hệ thống. Thống nhất đồng bộ với Thành viên D (spec 111, 112) và Thành viên C (spec 080). Bảng giá `pricePerHour` có bước nhảy 30 phút, giá slot = `pricePerHour / 2`.
- **Giới hạn đặt**: Đặt lẻ tối đa 8 slot 30 phút (4 giờ liên tục); đặt cố định theo tuần từ 2 đến 6 slot 30 phút/buổi (1 đến 3 giờ/buổi), chu kỳ 4–12 tuần.

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
