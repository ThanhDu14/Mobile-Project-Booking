# Nhóm 6 — Quản lý lịch đặt

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `06` |
| Vai trò sử dụng | Người chơi |
| Thành viên phụ trách | B |
| App tham khảo | Alobo |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/06-<ten-tinh-nang>` |
| Spec (Spec Kit) | [`specs/060-my-bookings/spec.md`](../../../specs/060-my-bookings/spec.md) (đã clarify) |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Danh sách lịch sắp tới, đang chờ duyệt, đã hoàn thành, đã hủy
- [ ] Xem chi tiết đơn
- [ ] Hủy đơn theo chính sách (ví dụ hủy trước 4 giờ)
- [ ] Đề nghị đổi lịch
- [ ] Mã QR check-in tại sân
- [ ] Đặt lại nhanh từ lịch sử

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `06_booking-management_buocX_mo-ta.png`.

| Bước | Ảnh | Mô tả |
|---|---|---|
| 1 | | |

## Ghi chú thiết kế / quyết định

- **Chính sách hủy đơn (Mốc 4 giờ)**: Đơn `PENDING` hoàn 100% số đã trả; đơn `CONFIRMED` hủy trước giờ chơi ≥ 4 tiếng hoàn 100%. Hủy trễ < 4 tiếng mất cọc hoặc phạt 30% số tiền ví thực trả sau voucher, hoàn phần còn lại; áp dụng độc lập cho phần phân bổ của mỗi buổi cố định. Xem quyết định và điểm cần làm rõ trong [spec 060](../../../specs/060-my-bookings/spec.md).
- **Thiết kế 060:** bám sát cả bốn wireframe trong [`specs/060-my-bookings/ui/`](../../../specs/060-my-bookings/ui/); hủy đơn bằng màn hình riêng theo ảnh.
- **Trạng thái NO_SHOW & Khóa đánh giá (Thống nhất 12/10 phương án 5.A)**: Khi quá giờ kết thúc ca chơi mà chưa quét QR check-in, đơn tự động chuyển `NO_SHOW`, khóa vĩnh viễn quyền viết review ở spec 070. Chỉ đơn `COMPLETED` mới được đánh giá trong 7 ngày.
- **Duyệt đơn PENDING & Timeout 60 phút (Thống nhất 12/10 phương án 2.A)**: Server Cron Job tự động quét quá hạn 60 phút, chuyển `REJECTED`, hoàn 100% ví. App tự cập nhật theo realtime listener.

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
