# Log AI: Sửa hồi quy tìm sân, bản đồ và lưới giá

| Trường | Nội dung |
|---|---|
| Ngày | 2026-11-27 |
| Người thực hiện | Nhóm dự án với Codex |
| Nhóm tính năng liên quan | 02 – Tìm sân và bản đồ; 03 – Chi tiết sân; 04 – Đặt sân |
| Công cụ AI | OpenAI Codex |
| Mục đích | Kiểm thử trên điện thoại, sửa lỗi, đối chiếu Rules và chuẩn bị PR |

## Prompt đã dùng

Người dùng yêu cầu kiểm thử các feature 020, 021, 022, 040 và 060 trên điện thoại; cập nhật từ main, sửa các lỗi được phát hiện theo thứ tự ưu tiên, kiểm thử lại, ghi log và tạo PR. Sau khi đối chiếu bốn test 030 với chính sách hiện hành, người dùng chọn giữ quyền hiện tại và cập nhật test cùng tài liệu.

## Tóm tắt phản hồi của AI

- Cho khách đọc hai danh mục quận và tiện ích; không mở quyền ghi.
- Đợi tile bản đồ trong một khoảng ngắn trước khi kết luận không tải được; đặt nút chuyển chế độ trong vùng riêng để tránh che kết quả.
- Tính banner giá theo ngày đang chọn và ẩn banner cao điểm khi ngày đó chỉ có một mức giá.
- Đối chiếu test 030 với chính sách chỉ công khai cơ sở ACTIVE; tài liệu nêu rõ `PERMISSION_DENIED` không xác định được lý do cụ thể.

## Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Sửa Rules, giao diện và banner | Dùng và bổ sung test | Giải quyết các lỗi quan sát được, giữ quyền ghi và dữ liệu khác |
| Đề xuất cho người chơi đọc sân không hoạt động | Bỏ | Người dùng chọn giữ chính sách phân quyền hiện hành |
| Triển khai toàn bộ Rules từ nhánh | Bỏ | Rules trên server khác ở các tính năng khác; chỉ triển khai hai thay đổi đọc danh mục trên bản đang chạy |

## Cách kiểm chứng

- Build debug và chạy 147 unit test liên quan sau khi gộp main; sau thay đổi banner giá cố định, build và test ViewModel liên quan chạy lại thành công.
- Chạy 348/348 test Firestore Rules trên Emulator sau khi cập nhật test 030. Kiểm tra riêng bản Rules triển khai từ trạng thái server: 8/8 test danh mục; đối chiếu nội dung sau triển khai.
- Trên điện thoại: khách tải 11 sân, lọc Quận 12 và tiện ích; kiểm tra danh sách/bản đồ giữ ba điều kiện, marker mở thẻ, vuốt thẻ, mở chi tiết, cụm marker tách khi phóng. Đổi ngày trên lưới kiểm tra banner giá.
- Giới hạn: giao diện chi tiết sân hiện chưa phân biệt được sân ẩn/khóa/xóa với sân không tồn tại khi Firestore trả lỗi quyền; cần thiết kế riêng theo chính sách hiện hành.
