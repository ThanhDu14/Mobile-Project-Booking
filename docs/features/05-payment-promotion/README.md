# Nhóm 5 — Thanh toán và khuyến mãi (giả lập)

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `05` |
| Vai trò sử dụng | Người chơi |
| Thành viên phụ trách | B |
| App tham khảo | Alobo |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/05-<ten-tinh-nang>` |
| Spec (Spec Kit) | [`specs/050-payment-voucher/spec.md`](../../../specs/050-payment-voucher/spec.md) (đã clarify) |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Chọn phương thức: thanh toán tại sân, đặt cọc, ví giả lập
- [ ] Nhập mã voucher, kiểm tra điều kiện áp dụng
- [ ] Xem hóa đơn điện tử
- [ ] Tự động hủy đơn nếu quá hạn thanh toán

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `05_payment-promotion_buocX_mo-ta.png`.

| Bước | Ảnh | Mô tả |
|---|---|---|
| 1 | | |

## Ghi chú thiết kế / quyết định

- **Nguồn voucher (Thống nhất 12/10 phương án 3.B)**: Phân tách 2 collection: `venues/{venueId}/vouchers` (voucher riêng của cơ sở) và `systemVouchers` (voucher toàn sàn). Redemptions lưu ở subcollection `redemptions/{bookingId}` dưới voucher tương ứng.
- **Duyệt đơn PENDING & Timeout 60 phút (Thống nhất 12/10 phương án 2.A)**: Server Cron Job / Supabase Edge Function quét tự động mỗi 1-2 phút, chuyển đơn sang `REJECTED`, kích hoạt hoàn 100% tiền vào ví giả lập và push thông báo FCM.
- **Tỷ lệ cọc**: Cố định 30% giá trị đơn hàng, làm tròn nghìn đồng chẵn gần nhất.
- **Nội dung FAQ cho AI (Thống nhất 12/10 phương án 6.A)**: Văn bản tĩnh chuẩn hóa về cọc 30%, ví giả lập, voucher được cung cấp cho Thành viên A để nhúng vào intent FAQ cố định.

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
