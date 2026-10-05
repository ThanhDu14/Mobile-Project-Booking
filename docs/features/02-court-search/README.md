# Nhóm 2 — Tìm kiếm sân

| Thuộc tính | Giá trị |
|---|---|
| Mã nhóm | `02` |
| Vai trò sử dụng | Người chơi |
| Thành viên phụ trách | A |
| App tham khảo | Alobo |
| Trạng thái | ⬜ Chưa bắt đầu |
| Nhánh Git | `feature/02-<ten-tinh-nang>` |
| Spec (Spec Kit) | _Chưa tạo — chạy `/speckit-specify` rồi dán link `specs/NNN-.../spec.md` vào đây_ |

## Phạm vi chức năng

Lấy từ [Proposal](../../proposal/Proposal.html), mục 5.

- [ ] Tìm theo tên sân, quận/huyện
- [ ] Bộ lọc: khoảng giá, khung giờ còn trống, tiện ích (gửi xe, nước uống, phòng tắm, cho thuê vợt)
- [ ] Tìm sân thông minh bằng AI (AI Assistant): nhập câu tự do hoặc giọng nói, tự trích xuất bộ lọc (quận, ngày/giờ, giá, tiện ích)
- [ ] Sắp xếp: gần nhất, giá, đánh giá
- [ ] Gợi ý sân gần vị trí hiện tại
- [ ] Chế độ xem bản đồ: marker icon quả cầu lông, chạm vào marker để xem thẻ tóm tắt rồi vào chi tiết sân
- [ ] Lưu lịch sử tìm kiếm

## Ảnh chụp tham khảo

Lưu ở [`screenshots/`](screenshots/) và đặt tên theo dạng `02_court-search_buocX_mo-ta.png`.

| Bước | Ảnh | Mô tả |
|---|---|---|
| 1 | | |

## Ghi chú thiết kế / quyết định

- Chi tiết thiết kế tính năng AI Assistant (workflow, JSON schema, chống prompt injection, xử lý câu lạc đề): xem tại [`docs/proposal/AI-feature.md`](../../proposal/AI-feature.md).

## Kiểm thử và minh chứng (Definition of Done)

- [ ] Có đủ các chức năng nhỏ ở trên và xử lý lỗi cơ bản
- [ ] AI trích xuất đúng bộ lọc trên bộ câu test mẫu và xử lý đúng các ngoại lệ (lạc đề, không rõ ý)
- [ ] Giao diện đúng thiết kế, hiển thị tốt trên ít nhất 2 kích thước màn hình
- [ ] Đã được review và merge vào `develop`
- [ ] Có kịch bản kiểm thử và ảnh/video minh chứng
- [ ] Có log AI (nếu dùng AI) trong [`docs/ai-log/`](../../ai-log/)
