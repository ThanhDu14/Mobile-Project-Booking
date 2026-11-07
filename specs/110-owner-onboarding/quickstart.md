# Hướng dẫn kiểm chứng: Feature 110

Tài liệu này mô tả các tình huống cần kiểm chứng sau khi triển khai. Repo hiện chưa có Feature 110, Edge Function hay migration Supabase nên chưa thể chạy các tình huống này. Cần chốt hợp đồng tích hợp trong [ranh giới tích hợp](contracts/owner-onboarding-boundaries.md) trước khi triển khai. Không chạy kiểm thử với dự án Firebase/Supabase production.

## Điều kiện chuẩn bị

- Cấu hình Android dùng Firebase Emulator Suite và Supabase local.
- Tài khoản kiểm thử: player đã đăng nhập có `ownerStatus=NONE`; player `PENDING`; player `REJECTED` kèm lý do; owner đã duyệt; người dùng không liên quan; tài khoản admin phục vụ kiểm tra policy.
- Policy Storage/hàm cục bộ và fixture Rules Emulator khớp [mô hình dữ liệu](data-model.md).
- Tệp ảnh hợp lệ JPEG, PNG, WebP; tệp MIME không hợp lệ, hỏng, quá lớn (>2 MB) và upload bị ngắt.
- Khi bổ sung phụ thuộc/lệnh kiểm thử, ghi lại lệnh đã xác nhận trong hướng dẫn này; tài liệu Plan không tự đặt tên tác vụ Gradle chưa kiểm chứng.

## Các tình huống kiểm chứng

1. **Nộp hồ sơ mới**: Dùng `PLAYER` có `NONE`, nhập đủ trường, chọn vị trí, thêm một ảnh venue và một giấy tờ, xem lại rồi gửi. Kỳ vọng: hồ sơ được máy chủ xác nhận đúng một lần, `users.ownerStatus=PENDING`, `role=PLAYER`, giao diện hiện đang chờ và không có quyền quản lý venue.
2. **Lỗi xác thực và thử tải lại**: Bỏ trống trường bắt buộc hoặc chọn giấy tờ sai định dạng/quá lớn/bị hỏng. Kỳ vọng: lỗi đúng trường/tệp, không tạo hồ sơ đã gửi. Ngắt một lượt tải, tải lại riêng tệp đó và xác nhận dữ liệu biểu mẫu được giữ.
3. **Gửi trùng/mất phản hồi**: Bấm gửi nhanh hai lần, sau đó giả lập timeout sau khi máy chủ có thể đã ghi. Mở lại/làm mới rồi thử tiếp. Kỳ vọng: tối đa một hồ sơ đang hiệu lực và không báo thành công `PENDING` trước xác nhận của máy chủ.
4. **Không sửa hồ sơ đang chờ**: Mở hồ sơ `PENDING`, thử sửa/nộp lại qua UI và request trực tiếp trái phép. Kỳ vọng: dữ liệu không đổi; chức năng player vẫn dùng được và máy chủ từ chối quyền quản lý owner.
5. **Đồng bộ trạng thái**: Tải lần lượt `NONE`, `PENDING`, `APPROVED`, `REJECTED`. Kiểm tra nhãn Việt hóa, vai trò tương ứng và khả năng thử lại. Với `REJECTED`, hiển thị lý do máy chủ cùng dữ liệu cũ; với `APPROVED`, điều hướng tới lối vào owner hiện có nhưng không triển khai màn hình quản lý ở đây.
6. **Nộp lại**: Sửa hồ sơ bị từ chối và gửi lại. Kỳ vọng: trạng thái về `PENDING`, role vẫn là `PLAYER`, lý do cũ không còn được hiển thị như quyết định hiện hành. Kiểm tra cách lưu document/lịch sử sau khi đã chốt định danh hồ sơ.
7. **Trạng thái thay đổi đồng thời**: Khi biểu mẫu mở, thay trạng thái trong emulator thành `PENDING`/`APPROVED` hoặc giả lập thiết bị khác vừa nộp lại. Gửi từ biểu mẫu cũ. Kỳ vọng: máy chủ trả xung đột/từ chối quyền, không ghi dở dang, tải trạng thái mới và giữ dữ liệu bản nháp.
8. **Quyền truy cập giấy tờ**: Người nộp mở giấy tờ của mình bằng signed URL còn hạn; player khác thử đọc bằng cách đoán object path. Kỳ vọng: chủ hồ sơ được phép, người khác bị từ chối. URL hết hạn phải thất bại; muốn tải lại cần xin URL mới có xác thực. Kiểm tra policy trên Supabase local.
9. **Mất mạng**: Tắt mạng khi đọc trạng thái, tải tệp và gửi hồ sơ. Kỳ vọng: dữ liệu cũ được đánh dấu, có thể tải lại tệp mà không nhập lại biểu mẫu, không báo gửi thành công sai và không cấp quyền owner từ cache.
10. **Dọn dữ liệu khi xóa tài khoản**: Dùng hợp đồng xóa đã thống nhất để xóa hồ sơ và các tệp riêng tư liên quan; giả lập xóa dở và xác minh cơ chế thử lại/kết quả được định nghĩa trước khi phát hành.

## Các tầng kiểm thử

- **Unit**: kiểm tra dữ liệu biểu mẫu, ánh xạ trạng thái sang UI, điều kiện gửi, xử lý phản hồi cũ và thử lại bằng repository giả.
- **Firebase Emulator**: kiểm tra được phép/bị chặn khi đọc/ghi hồ sơ của mình/người khác và trường được bảo vệ; kiểm tra gửi đồng thời/trạng thái cũ.
- **Supabase local**: kiểm tra `owner-docs` private RLS, quyền của người nộp/admin/người khác, MIME/kích thước và hạn signed URL; kiểm tra policy upload ảnh venue cho người nộp sau khi được xác định.
- **UI**: luồng gửi mới và nộp lại hồ sơ bị từ chối, trạng thái tải/rỗng/lỗi, tiến trình upload, thử lại và accessibility.
