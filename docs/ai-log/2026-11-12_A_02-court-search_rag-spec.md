# Log AI: Đặc tả đề xuất sân dựa trên đánh giá

| Trường | Nội dung |
|---|---|
| Ngày | 2026-11-12 |
| Người thực hiện | Lê Quốc Hưng (thành viên 2) |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (spec 020 và 022) |
| Công cụ AI | Codex |
| Mục đích | Cập nhật đặc tả nghiệp vụ và kịch bản nghiệm thu RAG |

## Prompt đã dùng

Tạo nhánh `feature/020-court-search-rag` và cập nhật các đặc tả liên quan để thêm RAG cho luồng tìm sân.

## Tóm tắt phản hồi của AI

Spec 020 kiểm tra điều kiện tìm kiếm. Spec 022 dùng đánh giá công khai để đề xuất sân và chỉ yêu cầu lời giải thích có nguồn khi người chơi bấm xem.

## Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Đề xuất theo đánh giá | Dùng | Giúp so sánh các sân cùng đáp ứng bộ lọc. |
| Giải thích theo yêu cầu | Dùng | Người chơi có thể kiểm tra nguồn và xem danh sách trước. |
| Giới hạn lượt | Sửa | Phân biệt lượt có kết quả và số lần thực tế gọi LLM, gồm cả lỗi và dự phòng. |

## Cách kiểm chứng

Đối chiếu yêu cầu 020 và 022 với quy tắc hiển thị đánh giá của 070. Rà soát kịch bản sân kín giờ, đánh giá bị ẩn, lỗi, hết lượt và phản hồi đến muộn. Kiểm thử hành vi thuộc giai đoạn triển khai.

## Triển khai

Theo yêu cầu tiếp theo, đã nối tab tìm sân 020, chuyển ngày/giờ tham khảo từ 030 vào lưới 040, và bổ sung Edge Function cho phân tích câu, truy xuất và giải thích đề xuất. Dùng truy vấn đánh giá trực tiếp có giới hạn để tránh nhân bản nguồn. Android dùng lại cách hiển thị thẻ sân, bộ tính giá theo slot và kiểm tra giao dịch giữ chỗ của dự án.

Đã chạy build APK debug, các unit test Android liên quan và test Edge Function trên Auth/Firestore Emulator. Nhà cung cấp mô hình trong test là bản giả. Chưa chạy bộ đánh giá với khóa Gemini/Groq thật.

## Điều chỉnh giao diện theo thiết kế cũ

Đối chiếu ảnh trong `specs/020-court-search/ui`, chỉnh ô tìm kiếm, thẻ AI và bảng lọc dạng bottom sheet; giữ cấu trúc điều hướng và chức năng tìm kiếm hiện tại. Kiểm tra bằng build APK debug và các unit test tìm kiếm. Chưa kiểm tra trực quan trên máy thật do chưa có thiết bị kết nối.

## Sửa lỗi mở màn hình tìm kiếm (2026-11-13)

Log trên máy thật cho thấy `RecyclerView has no LayoutManager` khi tạo phần điều khiển tìm kiếm. Đã khởi tạo `LinearLayoutManager` trước khi inflate view. Build APK debug và unit test tìm kiếm thành công; cài cập nhật lên điện thoại SM-A546E, xác nhận mở tab Tìm kiếm và bảng lọc không bị thoát. Chưa kiểm thử đầy đủ tìm kiếm với dữ liệu thật và backend AI.

## Cập nhật danh sách và giao diện tìm kiếm (2026-11-13)

Theo lựa chọn của người dùng, tab Tìm kiếm tải danh sách cơ sở hoạt động ngay khi chưa có bộ lọc, giữ thanh điều hướng năm tab và không thêm nút chưa có chức năng. Khi duyệt mặc định, app đọc các trang Firestore rồi sắp xếp và hiển thị từng đợt; tìm có điều kiện vẫn áp dụng giới hạn ứng viên hiện tại. Điều chỉnh vị trí thẻ AI, chip, bảng lọc và tỷ lệ ảnh thẻ sân theo wireframe cũ; cập nhật spec về trạng thái mặc định. Build APK và unit test tìm kiếm thành công. APK đã cài trên điện thoại, kiểm tra trực quan bản mới cần điện thoại mở khóa.

Sau khi điện thoại được mở khóa: xác nhận tab Tìm kiếm hiển thị “Tất cả sân · 10”, thẻ sân và mở được bảng lọc trên SM-A546E. Chưa kiểm thử trường hợp trên 100 cơ sở hoặc luồng AI với nhà cung cấp thật.

## Điều chỉnh khoảng cách giao diện tìm kiếm (2026-11-13)

Theo ảnh và phản hồi của người dùng, tăng khoảng cách giữa ô tìm, thẻ AI và hàng lọc; tăng khoảng cách giữa các nút lọc. Giữ nguyên thao tác và vùng chạm.
