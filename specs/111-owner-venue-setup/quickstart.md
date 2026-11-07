# Hướng dẫn nhanh: Kiểm tra thiết lập cơ sở của chủ sân

Tài liệu này mô tả các tình huống kiểm tra sau khi triển khai. Chỉ chạy với fake cục bộ và Firebase Emulator Suite, không chạy trên dự án thật. Repo hiện mới có mã khởi tạo; tên task build/kiểm thử còn tùy cấu hình Gradle và Emulator khi được triển khai, vì vậy cần xác nhận từ cấu hình thực tế thay vì tự suy đoán.

## Điều kiện chuẩn bị

- Có dependency Android cần thiết và emulator/thiết bị để kiểm tra UI.
- Cấu hình Firebase Emulator Suite để chạy Rules/kiểm thử Firestore.
- Fixture kiểm thử: owner A đã duyệt, owner B đã duyệt, player chưa được duyệt, một venue với hai court, một price rule khớp; slot trống và các slot có trạng thái `HOLD`, `BOOKED`, `BLOCKED`.
- Không dùng dữ liệu người thật, dự án Firebase production hoặc dự án Supabase production.

## Các tình huống

1. **Cô lập quyền owner**: owner A đọc/quản lý được venue, court, bảng giá của mình; owner B không truy cập được dù đoán đúng ID; player/chưa duyệt không ghi được. Xác nhận yêu cầu ghi Firestore trực tiếp cũng bị từ chối, không chỉ thao tác trên UI.
2. **Vòng đời venue**: tạo/sửa venue hợp lệ, ẩn venue và xác nhận booking mới không còn khả dụng trong khi booking đã xác nhận giữ nguyên; owner không đổi được venue admin `LOCKED` hoặc sửa trường được bảo vệ.
3. **Vòng đời court**: thêm/sửa/sắp xếp và ngừng hoạt động court; xác nhận booking mới bị chặn và booking cũ được bảo toàn.
4. **Bảng giá**: chấp nhận thứ/giờ/giá hợp lệ; từ chối thứ sai, mốc không phải 30 phút, giờ qua đêm, giá không dương/không chia hết, label sai và rule chồng giờ; cho phép xóa; xác nhận giá booking item cũ không đổi và slot tương lai thiếu rule không đặt được.
5. **Cạnh tranh booking và khóa nguyên tử**: khóa thành công khoảng nhiều slot thì mọi slot ID tương ứng đều có `BLOCKED` cùng lý do được chọn. Nếu có slot đang giữ/đặt/khóa, không slot nào trong khoảng bị đổi. Cho giao dịch giữ booking cạnh tranh với giao dịch khóa owner nhiều lần; chỉ một bên thành công và bên thua nhận trạng thái mới.
6. **An toàn mở khóa**: mở khóa block còn khớp; xác nhận slot `HOLD`/`BOOKED`, slot đã đổi trạng thái và block `DROP_IN` không bị thao tác mở khóa của owner xóa.
7. **Mạng/lỗi**: mô phỏng dữ liệu cũ, từ chối quyền, timeout sau khi gửi yêu cầu và offline. Xác nhận biểu mẫu được giữ lại, cache không được coi là quyền hiện tại, thao tác chưa rõ kết quả phải tải lại server trước khi thử lại.
8. **UI/accessibility**: kiểm tra loading/content/empty/error/thử lại, chuỗi tiếng Việt từ resource, theme sáng/tối, điện thoại và `w600dp`, contentDescription có ý nghĩa và vùng chạm 48dp.

## Kết quả mong đợi

Đạt khi các tình huống trên đúng và đạt tiêu chí đo lường trong Feature 111: danh sách/lịch dưới 3 giây trên 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường. Chỉ bổ sung câu lệnh kiểm thử cụ thể sau khi xác minh task Gradle và cấu hình Emulator; không tự đặt lệnh chưa được hỗ trợ.

Xem [data-model.md](data-model.md) và [contracts/firestore-boundary.md](contracts/firestore-boundary.md) về trường dữ liệu và ranh giới phân quyền.
