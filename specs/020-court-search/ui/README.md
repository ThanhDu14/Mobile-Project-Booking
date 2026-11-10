# Wireframe

> **Lỗi thời từ 2026-11-06.** Bốn ảnh `02_court-search_buoc1..4` bên dưới vẽ luồng 4 bước cũ (Trang chủ là bước 1 của tìm kiếm, tab "Tài khoản" ở thanh dưới). Thiết kế điều hướng mới ([spec Clarifications 2026-11-06](../spec.md#session-2026-11-06-thay-đổi-thiết-kế-điều-hướng), FR-028 → FR-033) thay thế chúng. Giữ ảnh cũ để tham khảo thẻ sân và bảng lọc (hai phần này không đổi); bố cục màn hình theo mục "Thiết kế 2026-11-06" dưới đây. Ảnh mới sẽ xuất sau khi vẽ lại trên canvas.

## Thiết kế 2026-11-06 (cần vẽ lại trên canvas)

Thanh dưới chung: Trang chủ · Tìm kiếm · Lịch đặt · Vãng lai · Cộng đồng. Header chung: logo bên trái, avatar tròn góc phải trên (spec 011; khách thấy biểu tượng người → đăng nhập).

| Artboard mới (đề xuất) | Nội dung |
|---|---|
| `Home` | Header; tiêu đề "Tất cả sân · N" + nút sắp xếp (mặc định "Đánh giá cao nhất"); danh sách thẻ sân (giống thẻ trong ảnh bước 2 cũ). Không có ô tìm, chip lọc, ô AI, "Sân gần bạn". Kèm trạng thái rỗng "Chưa có sân nào đang hoạt động" |
| `Search` (chưa có điều kiện) | Ô tìm có con trỏ + bàn phím; hàng chip "Quận", "Giờ trống", "Giá", "+"; vùng chừa ô AI (022, để trống); "Tìm gần đây" + "Xóa tất cả" + tối đa 10 mục. Bản khách: "Đăng nhập để lưu lịch sử tìm kiếm" |
| `SearchTyping` | Như `Search`, đang gõ: hàng gợi ý quận/huyện khớp từ khóa phía trên |
| `SearchResults` | Ô tìm có từ khóa + nút xóa; chip lọc nhanh; chip đang áp dụng có "x"; "N sân phù hợp" (hoặc "Kết quả phù hợp") + sắp xếp; vùng chừa nút Danh sách/Bản đồ (021); danh sách kết quả; trạng thái rỗng có "Xóa bộ lọc" |
| `SearchFilter` | Giữ như ảnh bước 3 cũ, chỉ đổi nền phía sau thành `SearchResults` |

Mỗi artboard cần bản sáng và tối như trước.

## Ảnh cũ (luồng 4 bước, lỗi thời)

Ảnh xuất từ canvas thiết kế SmashNow ngày 13/10/2026, khớp spec sau commit `0c56d5c`. Bản gốc (có prototype bấm được, chế độ sáng/tối): [SmashNow Screens](https://claude.ai/artifact/9v89i3NEiUfw7mACk3zgq3).

| Ảnh | Artboard trên canvas |
|---|---|
| [`02_court-search_buoc1_trang-chu.png`](02_court-search_buoc1_trang-chu.png) | `Search` |
| [`02_court-search_buoc2_ket-qua-co-bo-loc.png`](02_court-search_buoc2_ket-qua-co-bo-loc.png) | `SearchResults` |
| [`02_court-search_buoc3_bo-loc.png`](02_court-search_buoc3_bo-loc.png) | `SearchFilter` |
| [`02_court-search_buoc4_lich-su-tim-kiem.png`](02_court-search_buoc4_lich-su-tim-kiem.png) | `SearchHistory` |

Mã QR, marker bản đồ và ảnh sân trong ảnh là hình minh họa thay cho ảnh tải lên trên canvas.

**Khác biệt đã chốt khi lập plan (research R3, R15), vẫn áp dụng cho thiết kế mới:**

- Bảng lọc (bước 3 cũ): nút chính hiện "Xem N sân" khi biết số đếm, "Xem kết quả" khi chưa biết (lọc trên máy hoặc có khung giờ). Tiêu đề kết quả tương ứng là "Kết quả phù hợp" không kèm số.
- Huy hiệu "Đang mở cửa" trên thẻ (nếu có trong ảnh) nằm ngoài phạm vi spec 020, không làm ở phiên bản này.
- Nút trái tim trên thẻ thuộc spec 070 (C).
