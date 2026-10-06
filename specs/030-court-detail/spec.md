# Feature Specification: Chi tiết sân

**Feature Branch**: `feature/03-court-detail`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi (kể cả khách chưa đăng nhập) xem chi tiết một cơ sở sân cầu lông trước khi đặt. Vào từ danh sách kết quả (spec 020), thẻ trên bản đồ (spec 021) hoặc danh sách sân yêu thích (spec 070). Màn hình gồm: thư viện ảnh (vuốt ngang, xem toàn màn hình, phóng to; không có ảnh thì ảnh mặc định); thông tin chính (tên, địa chỉ, quận/huyện, điểm và số lượt đánh giá); bảng giá theo khung giờ và thứ trong tuần (giờ thường, cao điểm, cuối tuần) theo đ/giờ, chưa có thì "Liên hệ", đến từ tìm kiếm có khung giờ thì làm nổi bật giá của khung đó, cách tính giống spec 020 và 040; tiện ích, số sân con đang hoạt động và loại mặt sân; giờ mở cửa kèm trạng thái đang mở/đã đóng theo giờ Việt Nam, số điện thoại bấm để gọi; bản đồ nhúng có marker quả cầu lông, nút "Chỉ đường" mở ứng dụng bản đồ (không có thì trình duyệt), không cần quyền vị trí; đánh giá tổng quan (điểm trung bình, số lượt, điểm từng tiêu chí, 3 đánh giá mới nhất, "Xem tất cả" mở spec 070, chưa có thì "Sân chưa có đánh giá"); nút trái tim (spec 070), "Chat với chủ sân" (spec 091) và "Đặt sân" luôn hiển thị ở cuối màn hình, "Đặt sân" mở lưới giờ spec 040 mang theo ngày và khung giờ đã chọn. Khách xem được mọi thông tin; bấm Đặt sân, trái tim hoặc Chat thì mời đăng nhập, đăng nhập xong quay lại và tiếp tục thao tác. Cơ sở bị ẩn/khóa: ghi chú "Sân tạm ngừng hoạt động", ẩn Đặt sân và Chat, vẫn bỏ yêu thích được; không còn tồn tại: màn hình "Sân không còn tồn tại". Trạng thái đang tải, lỗi kèm thử lại; mất mạng hiện dữ liệu đã tải kèm thông báo. Phần đầu màn hình hiện dưới 3 giây trên 4G; bản đồ và đánh giá tải sau. Điện thoại và máy tính bảng, sáng và tối. Không bao gồm: lưới giờ và đặt sân (040); danh sách đầy đủ, viết đánh giá, logic yêu thích (070); nội dung chat (091); buổi vãng lai (080); chủ sân sửa thông tin (111); bản đồ tìm sân (021). Dữ liệu: venues, courts, priceRules, reviews (VISIBLE, mới nhất), amenities, districts theo docs/design/database/README.md."

**Nhóm tính năng**: [03 — Chi tiết sân](../../docs/features/03-court-detail/README.md) · **Phụ trách**: A · **Liên quan**: [spec 020](../020-court-search/spec.md) (tìm kiếm), spec 021 (bản đồ), [spec 070](../070-review-favorite/spec.md) (đánh giá, yêu thích), spec 040 (đặt sân), spec 091 (chat), spec 111 (thông tin cơ sở), [spec 010](../010-account-auth/spec.md) (đăng nhập)

## Clarifications

### Session 2026-10-06

- Q: Số điện thoại của cơ sở có hiển thị cho khách chưa đăng nhập không? → A: Có. Số điện thoại của cơ sở là thông tin kinh doanh công khai, ai cũng thấy và bấm gọi được, kể cả khách chưa đăng nhập.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem thông tin cơ sở và bấm "Đặt sân" (Priority: P1)

Từ danh sách kết quả tìm kiếm, người chơi bấm vào một cơ sở và thấy ngay ảnh, tên, địa chỉ, điểm đánh giá, giá, tiện ích, số sân và loại mặt sân, giờ mở cửa. Nút "Đặt sân" luôn nằm ở cuối màn hình; bấm vào thì mở lưới giờ của cơ sở đó.

**Why this priority**: Đây là bước bắt buộc giữa tìm sân và đặt sân; người chơi cần đủ thông tin để quyết định và một lối vào đặt sân rõ ràng. Riêng story này đã là một MVP của nhóm 03.

**Independent Test**: Với một cơ sở demo có đủ ảnh, bảng giá, tiện ích và 3 sân con, mở chi tiết từ danh sách tìm kiếm; kiểm tra từng thông tin khớp dữ liệu và bấm "Đặt sân" mở đúng lưới giờ của cơ sở đó.

**Acceptance Scenarios**:

1. **Given** người chơi bấm một thẻ trong danh sách tìm kiếm, **When** màn hình chi tiết mở, **Then** thấy tên, địa chỉ, quận/huyện, điểm trung bình kèm số lượt đánh giá, và nút "Đặt sân" ở cuối màn hình.
2. **Given** cơ sở có 5 ảnh, **When** người chơi vuốt ngang thư viện ảnh, **Then** xem lần lượt cả 5 ảnh kèm chỉ báo vị trí (ví dụ "2/5"); **When** bấm vào một ảnh, **Then** ảnh mở toàn màn hình, phóng to bằng hai ngón và vuốt sang ảnh khác được.
3. **Given** cơ sở có tiện ích gửi xe và phòng tắm, 3 sân con đang hoạt động mặt thảm và 1 sân con tạm ngưng, **When** xem chi tiết, **Then** thấy biểu tượng và tên hai tiện ích, "3 sân · Thảm" (không tính sân tạm ngưng).
4. **Given** cơ sở mở 05:00–23:00 và bây giờ là 20:00 giờ Việt Nam, **When** xem chi tiết, **Then** thấy "05:00–23:00 · Đang mở cửa"; lúc 23:30 thì thấy "Đã đóng cửa · Mở lại 05:00".
5. **Given** người chơi đã đăng nhập, **When** bấm "Đặt sân", **Then** lưới giờ của đúng cơ sở này (spec 040) được mở.
6. **Given** người chơi đến từ tìm kiếm có chọn ngày mai 19:00–21:00, **When** bấm "Đặt sân", **Then** lưới giờ mở sẵn ở ngày mai và khung 19:00–21:00 được làm nổi bật.
7. **Given** người chơi đã cuộn xuống giữa màn hình, **When** nhìn cuối màn hình, **Then** nút "Đặt sân" vẫn hiển thị.

---

### User Story 2 - Xem bảng giá theo khung giờ (Priority: P1)

Người chơi xem bảng giá của cơ sở theo thứ trong tuần và khung giờ (giờ thường, giờ cao điểm, cuối tuần), giá tính theo đ/giờ. Nếu đến từ tìm kiếm có chọn khung giờ, giá của đúng khung đó được làm nổi bật.

**Why this priority**: Giá là yếu tố quyết định hàng đầu khi chọn sân; đặt sân mà không biết giá trước thì dễ bỏ ngang ở bước xác nhận.

**Independent Test**: Với cơ sở có 3 mức giá (thường 80.000 đ/giờ, cao điểm 17:00–22:00 thứ Hai–Sáu 120.000 đ/giờ, cuối tuần 130.000 đ/giờ), kiểm tra bảng giá hiển thị đúng ba mức và đúng khung; đến từ tìm kiếm "thứ Ba 19:00–21:00" thì mức 120.000 đ/giờ được làm nổi bật.

**Acceptance Scenarios**:

1. **Given** cơ sở có bảng giá theo thứ và giờ, **When** xem mục "Bảng giá", **Then** các mức được nhóm theo nhóm ngày (ví dụ "Thứ 2 – Thứ 6", "Thứ 7, Chủ nhật"), mỗi dòng có khung giờ, nhãn (Giờ thường / Giờ cao điểm / Cuối tuần) và giá đ/giờ.
2. **Given** người chơi đến từ tìm kiếm có ngày và khung giờ, **When** xem bảng giá, **Then** dòng giá áp dụng cho khung đó được làm nổi bật, kèm dòng "Khung bạn chọn: 19:00–21:00 · 120.000 đ/giờ" có giá trùng với giá trên thẻ kết quả của spec 020.
3. **Given** khung đã chọn nằm giữa hai mức giá (ví dụ 16:00–18:00 với giờ cao điểm từ 17:00), **When** xem bảng giá, **Then** cả hai dòng được làm nổi bật và dòng "Khung bạn chọn" hiện giá trung bình mỗi giờ của cả khung.
4. **Given** cơ sở chưa có bảng giá, **When** xem chi tiết, **Then** mục giá hiện "Liên hệ" kèm số điện thoại của cơ sở.
5. **Given** người chơi mở chi tiết không qua tìm kiếm (từ yêu thích, bản đồ), **When** xem bảng giá, **Then** không có dòng nào được làm nổi bật.

---

### User Story 3 - Xem vị trí, chỉ đường và liên hệ (Priority: P2)

Người chơi xem cơ sở trên bản đồ nhúng với marker quả cầu lông, bấm "Chỉ đường" để mở ứng dụng bản đồ, và bấm số điện thoại để gọi cho cơ sở.

**Why this priority**: Giúp người chơi biết sân có tiện đường không và tới được sân; quan trọng nhưng không chặn quyết định đặt sân như giá và giờ trống.

**Independent Test**: Mở chi tiết một cơ sở, thấy bản đồ có marker đúng vị trí; bấm "Chỉ đường" mở ứng dụng bản đồ với điểm đến là cơ sở; bấm số điện thoại mở trình gọi điện với số đã điền sẵn.

**Acceptance Scenarios**:

1. **Given** cơ sở có vị trí, **When** cuộn tới mục "Vị trí", **Then** thấy bản đồ nhỏ có marker quả cầu lông tại cơ sở và địa chỉ đầy đủ; app không xin quyền vị trí.
2. **Given** máy có ứng dụng bản đồ, **When** bấm "Chỉ đường", **Then** ứng dụng bản đồ mở với điểm đến là vị trí cơ sở.
3. **Given** máy không có ứng dụng bản đồ, **When** bấm "Chỉ đường", **Then** trang chỉ đường mở trong trình duyệt.
4. **Given** cơ sở có số điện thoại, **When** bấm vào số, **Then** trình gọi điện của máy mở với số đã điền sẵn, chưa tự gọi.
5. **Given** bản đồ không tải được (mất mạng, dịch vụ bản đồ lỗi), **When** xem mục "Vị trí", **Then** hiện địa chỉ dạng chữ và nút "Chỉ đường" vẫn dùng được; các phần khác của màn hình không bị ảnh hưởng.
6. **Given** thiết bị không có chức năng gọi điện (máy tính bảng chỉ có wifi), **When** bấm số điện thoại, **Then** app cho sao chép số thay vì mở trình gọi.

---

### User Story 4 - Xem đánh giá tổng quan (Priority: P2)

Người chơi xem điểm trung bình, số lượt đánh giá, điểm trung bình từng tiêu chí và 3 đánh giá mới nhất; bấm "Xem tất cả" để mở danh sách đầy đủ.

**Why this priority**: Đánh giá thật từ người đã chơi giúp tin tưởng sân, nhưng thông tin giá, giờ, vị trí (P1) đã đủ để đặt.

**Independent Test**: Với cơ sở có 12 đánh giá (1 đánh giá bị admin ẩn), kiểm tra điểm và số lượt khớp với các đánh giá đang hiển thị, 3 đánh giá mới nhất không gồm đánh giá bị ẩn, "Xem tất cả" mở danh sách của spec 070.

**Acceptance Scenarios**:

1. **Given** cơ sở có đánh giá, **When** cuộn tới mục "Đánh giá", **Then** thấy điểm trung bình (1 chữ số thập phân), số lượt, và điểm trung bình của "Chất lượng sân", "Vệ sinh", "Thái độ phục vụ".
2. **Given** cơ sở có hơn 3 đánh giá đang hiển thị, **When** xem mục "Đánh giá", **Then** thấy 3 đánh giá mới nhất (tên và ảnh người viết, số sao, bình luận rút gọn, ảnh thu nhỏ nếu có, ngày, phản hồi của chủ sân nếu có) và nút "Xem tất cả (N)".
3. **Given** người chơi bấm "Xem tất cả", **When** màn hình chuyển, **Then** danh sách đầy đủ đánh giá của đúng cơ sở này (spec 070) được mở.
4. **Given** một đánh giá đã bị admin ẩn, **When** bất kỳ ai xem chi tiết sân, **Then** đánh giá đó không nằm trong 3 đánh giá mới nhất và không được tính vào điểm.
5. **Given** người viết một đánh giá đã xóa tài khoản, **When** đánh giá đó nằm trong 3 đánh giá mới nhất, **Then** hiện tên "Người dùng đã xóa" và ảnh mặc định.
6. **Given** cơ sở chưa có đánh giá nào, **When** xem mục "Đánh giá", **Then** thấy "Sân chưa có đánh giá" và không có nút "Xem tất cả".

---

### User Story 5 - Khách chưa đăng nhập và các thao tác cần tài khoản (Priority: P2)

Khách chưa đăng nhập xem được toàn bộ thông tin. Khi bấm "Đặt sân", trái tim yêu thích hoặc "Chat với chủ sân", app mời đăng nhập; đăng nhập xong app quay lại đúng màn hình chi tiết và tiếp tục thao tác vừa bấm.

**Why this priority**: Spec 020 đã cho khách tìm sân; nếu chi tiết sân chặn khách thì luồng xem sân bị đứt. Nhưng người đã đăng nhập (P1) vẫn dùng được khi chưa có phần này.

**Independent Test**: Chưa đăng nhập, mở chi tiết một cơ sở, xem được mọi mục; bấm "Đặt sân" thì được mời đăng nhập; đăng nhập xong quay về đúng cơ sở và lưới giờ tự mở. Lặp lại với trái tim (sân được lưu sau khi đăng nhập) và "Chat với chủ sân".

**Acceptance Scenarios**:

1. **Given** khách chưa đăng nhập, **When** mở chi tiết sân, **Then** thấy đầy đủ ảnh, giá, tiện ích, giờ mở cửa, số điện thoại (bấm gọi được), bản đồ và đánh giá như người đã đăng nhập.
2. **Given** khách bấm "Đặt sân", **When** app phản hồi, **Then** hiện lời mời "Đăng nhập để đặt sân" với nút "Đăng nhập" và "Để sau"; chọn "Để sau" thì ở lại màn hình chi tiết.
3. **Given** khách chọn "Đăng nhập" sau khi bấm "Đặt sân", **When** đăng nhập hoặc đăng ký thành công, **Then** app quay lại đúng màn hình chi tiết của cơ sở đó và mở lưới giờ, giữ ngày và khung giờ đã chọn ở tìm kiếm nếu có.
4. **Given** khách bấm trái tim rồi đăng nhập thành công, **When** quay lại màn hình chi tiết, **Then** cơ sở đã được lưu vào yêu thích (trừ khi đã có sẵn trong danh sách của tài khoản đó).
5. **Given** khách bấm "Chat với chủ sân" rồi đăng nhập thành công, **When** quay lại, **Then** hội thoại với chủ sân của cơ sở này (spec 091) được mở.
6. **Given** khách hủy hoặc thoát giữa chừng màn hình đăng nhập, **When** quay lại, **Then** ở lại màn hình chi tiết, không có thao tác nào được thực hiện.
7. **Given** tài khoản vừa đăng nhập là chủ sở hữu cơ sở đang xem, **When** app tiếp tục thao tác "Đặt sân" hoặc "Chat với chủ sân", **Then** app báo "Đây là cơ sở của bạn" và không thực hiện thao tác đó.

---

### User Story 6 - Cơ sở tạm ngừng hoạt động hoặc không còn tồn tại (Priority: P3)

Khi người chơi mở một cơ sở đã bị chủ sân ẩn hoặc bị admin khóa (thường từ danh sách yêu thích hoặc liên kết cũ), app vẫn hiện thông tin kèm ghi chú "Sân tạm ngừng hoạt động" và không cho đặt. Cơ sở đã bị xóa thì hiện màn hình "Sân không còn tồn tại".

**Why this priority**: Ít gặp, nhưng thiếu thì người chơi có thể đặt nhầm sân không hoạt động hoặc gặp màn hình lỗi khó hiểu.

**Independent Test**: Lưu một cơ sở vào yêu thích, cho chủ sân ẩn cơ sở đó, mở lại từ danh sách yêu thích thấy ghi chú và không có nút "Đặt sân"; xóa hẳn một cơ sở khác rồi mở lại thấy màn hình "Sân không còn tồn tại".

**Acceptance Scenarios**:

1. **Given** cơ sở đang bị chủ sân ẩn hoặc bị admin khóa, **When** người chơi mở chi tiết, **Then** thấy thông tin của cơ sở kèm ghi chú nổi bật "Sân tạm ngừng hoạt động", không có nút "Đặt sân" và "Chat với chủ sân".
2. **Given** cơ sở đang tạm ngừng và nằm trong yêu thích của người chơi, **When** bấm trái tim, **Then** bỏ yêu thích được; cơ sở chưa nằm trong yêu thích thì không thêm được.
3. **Given** cơ sở đang tạm ngừng, **When** xem bảng giá và mục đánh giá, **Then** vẫn thấy để tham khảo; các nút dẫn tới thao tác mới (đặt, chat) không có.
4. **Given** cơ sở đã bị xóa khỏi hệ thống, **When** người chơi mở chi tiết, **Then** thấy màn hình "Sân không còn tồn tại" kèm nút quay lại; nếu cơ sở nằm trong yêu thích thì có thêm nút "Bỏ khỏi yêu thích".
5. **Given** người chơi đang xem chi tiết thì cơ sở bị ẩn hoặc khóa, **When** bấm "Đặt sân", **Then** app báo "Sân tạm ngừng hoạt động", cập nhật màn hình theo trạng thái mới và không mở lưới giờ.

---

### Edge Cases

- **Mất mạng khi mở chi tiết**: nếu đã từng tải cơ sở này thì hiện dữ liệu đã tải kèm thông báo "Không có kết nối, thông tin có thể chưa cập nhật"; nếu chưa thì hiện trạng thái lỗi kèm nút "Thử lại". Không crash, không treo.
- **Mất mạng sau khi đã mở**: phần đã hiển thị giữ nguyên; bản đồ và ảnh chưa tải hiện chỗ trống có biểu tượng thay thế; bấm "Đặt sân" thì luồng đặt sân (spec 040) tự xử lý lỗi mạng.
- **Một phần dữ liệu tải lỗi** (ví dụ đánh giá lỗi nhưng thông tin chính tải được): chỉ mục lỗi hiện thông báo kèm "Thử lại", các mục khác vẫn hiển thị.
- **Ảnh lỗi hoặc không tải được**: ô ảnh đó hiện ảnh mặc định, không làm hỏng thư viện.
- **Giờ mở cửa qua nửa đêm** (ví dụ 05:00–01:00): trạng thái "Đang mở cửa" tính đúng cho khoảng 00:00–01:00.
- **Cơ sở mở 24 giờ**: hiện "Mở cả ngày", trạng thái luôn "Đang mở cửa".
- **Cơ sở không có sân con nào đang hoạt động**: hiện "Tạm thời chưa có sân hoạt động"; nút "Đặt sân" vẫn hiện nhưng lưới giờ của spec 040 sẽ trống.
- **Nhiều loại mặt sân** (2 sân gỗ, 1 sân thảm): hiện "3 sân · 2 Gỗ, 1 Thảm".
- **Cơ sở không có số điện thoại**: ẩn mục số điện thoại; giá "Liên hệ" khi đó chỉ hiện chữ "Liên hệ chủ sân" kèm nút chat.
- **Cơ sở không có vị trí hợp lệ**: ẩn bản đồ và nút "Chỉ đường", vẫn hiện địa chỉ.
- **Khung giờ từ tìm kiếm đã qua** (người chơi để màn hình mở qua giờ): không làm nổi bật khung đó nữa và "Đặt sân" mở lưới giờ ở ngày hiện tại.
- **Bảng giá không phủ hết khung đã chọn**: dòng "Khung bạn chọn" hiện "Liên hệ" cho khung đó, cùng quy tắc với spec 020.
- **Người chơi xoay màn hình hoặc chuyển sáng/tối**: vị trí cuộn, ảnh đang xem và trạng thái các mục được giữ nguyên.
- **Chủ sân xem cơ sở của chính mình ở chế độ người chơi**: xem được như mọi người, không đặt sân và không chat với chính mình được (US5 kịch bản 7).

## Requirements *(mandatory)*

### Functional Requirements

**Vào màn hình**

- **FR-001**: Màn hình chi tiết PHẢI mở được cho một cơ sở xác định từ danh sách tìm kiếm (spec 020), thẻ trên bản đồ (spec 021), danh sách yêu thích (spec 070) và các thông báo có liên kết tới cơ sở. Khi đến từ tìm kiếm, màn hình PHẢI nhận kèm ngày và khung giờ đã chọn (nếu có).
- **FR-002**: Quay lại từ màn hình chi tiết PHẢI trở về đúng màn hình trước đó với trạng thái cũ (theo yêu cầu của màn hình đó, ví dụ spec 020 FR-017).

**Thông tin cơ sở**

- **FR-003**: Màn hình PHẢI hiển thị tên, địa chỉ, quận/huyện, điểm trung bình (1 chữ số thập phân) kèm số lượt đánh giá; cơ sở chưa có đánh giá hiện "Chưa có đánh giá".
- **FR-004**: Thư viện ảnh PHẢI cho vuốt ngang qua các ảnh theo thứ tự chủ sân sắp xếp, hiện chỉ báo vị trí, bấm để xem toàn màn hình, phóng to/thu nhỏ và vuốt sang ảnh khác ở chế độ toàn màn hình. Cơ sở không có ảnh hoặc ảnh tải lỗi hiện ảnh mặc định.
- **FR-005**: Màn hình PHẢI hiển thị các tiện ích của cơ sở với tên và biểu tượng lấy từ danh mục chung; tiện ích không còn trong danh mục thì không hiển thị.
- **FR-006**: Màn hình PHẢI hiển thị số sân con đang hoạt động và loại mặt sân (Gỗ, PU, Thảm) kèm số lượng mỗi loại khi có nhiều loại; sân con tạm ngưng không được tính.
- **FR-007**: Màn hình PHẢI hiển thị giờ mở cửa và trạng thái "Đang mở cửa" / "Đã đóng cửa · Mở lại HH:mm" tính theo giờ Việt Nam tại thời điểm xem, xử lý đúng giờ mở qua nửa đêm và mở cả ngày.

**Bảng giá**

- **FR-008**: Màn hình PHẢI hiển thị bảng giá của cơ sở theo nhóm ngày trong tuần và khung giờ, mỗi dòng có khung giờ, nhãn (Giờ thường / Giờ cao điểm / Cuối tuần) và giá quy ra đ/giờ. Cơ sở chưa có bảng giá hiện "Liên hệ".
- **FR-009**: Khi màn hình được mở kèm ngày và khung giờ từ tìm kiếm, các dòng giá áp dụng cho khung đó PHẢI được làm nổi bật và PHẢI có dòng "Khung bạn chọn" hiện giá đ/giờ của cả khung. Giá này PHẢI được tính cùng một quy tắc với spec 020 (thẻ kết quả) và spec 040 (màn hình đặt sân): tính từng khung 30 phút theo bảng giá rồi quy ra giá trung bình mỗi giờ.

**Vị trí và liên hệ**

- **FR-010**: Màn hình PHẢI hiển thị bản đồ nhúng có marker quả cầu lông tại vị trí cơ sở, kèm địa chỉ đầy đủ. Xem bản đồ KHÔNG ĐƯỢC yêu cầu quyền vị trí.
- **FR-011**: Nút "Chỉ đường" PHẢI mở ứng dụng bản đồ của máy với điểm đến là vị trí cơ sở; không có ứng dụng bản đồ thì mở trang chỉ đường trong trình duyệt.
- **FR-012**: Bấm số điện thoại của cơ sở PHẢI mở trình gọi điện với số điền sẵn (không tự gọi); thiết bị không gọi điện được thì cho sao chép số. Số điện thoại là thông tin công khai, hiển thị cho mọi người kể cả khách chưa đăng nhập.
- **FR-013**: Bản đồ tải lỗi KHÔNG ĐƯỢC làm hỏng các phần khác; khi đó vẫn hiện địa chỉ và nút "Chỉ đường".

**Đánh giá tổng quan**

- **FR-014**: Màn hình PHẢI hiển thị điểm trung bình, số lượt đánh giá và điểm trung bình của từng tiêu chí (chất lượng sân, vệ sinh, thái độ phục vụ), chỉ tính các đánh giá đang hiển thị.
- **FR-015**: Màn hình PHẢI hiển thị tối đa 3 đánh giá mới nhất đang hiển thị (tên và ảnh người viết hoặc "Người dùng đã xóa", số sao, bình luận rút gọn, ảnh thu nhỏ nếu có, ngày, phản hồi của chủ sân nếu có) và nút "Xem tất cả (N)" mở danh sách đầy đủ của spec 070. Chưa có đánh giá thì hiện "Sân chưa có đánh giá" và ẩn nút.

**Thao tác**

- **FR-016**: Nút "Đặt sân" PHẢI luôn hiển thị ở cuối màn hình khi cơ sở đang hoạt động; bấm vào mở lưới giờ của cơ sở (spec 040), mang theo ngày và khung giờ từ tìm kiếm nếu còn hợp lệ (chưa qua).
- **FR-017**: Màn hình PHẢI có nút trái tim yêu thích và nút "Chat với chủ sân"; hành vi lưu/bỏ yêu thích theo spec 070, hội thoại theo spec 091.
- **FR-018**: Chủ sở hữu cơ sở KHÔNG ĐƯỢC đặt sân hoặc chat với chính mình từ màn hình chi tiết cơ sở của mình; app báo "Đây là cơ sở của bạn".

**Khách chưa đăng nhập**

- **FR-019**: Khách chưa đăng nhập PHẢI xem được toàn bộ thông tin công khai của cơ sở đang hoạt động (ảnh, giá, tiện ích, sân con, giờ mở cửa, số điện thoại, vị trí, đánh giá) mà không bị yêu cầu đăng nhập.
- **FR-020**: Khi khách bấm "Đặt sân", trái tim hoặc "Chat với chủ sân", app PHẢI mời đăng nhập (cho chọn "Để sau"). Đăng nhập hoặc đăng ký thành công thì app PHẢI quay lại đúng màn hình chi tiết của cơ sở đó và tự tiếp tục thao tác vừa bấm (mở lưới giờ giữ ngày và khung giờ, lưu yêu thích, mở hội thoại). Hủy đăng nhập thì ở lại màn hình chi tiết và không thực hiện gì.

**Trạng thái cơ sở**

- **FR-021**: Cơ sở đang bị chủ sân ẩn hoặc bị admin khóa PHẢI hiện ghi chú "Sân tạm ngừng hoạt động", KHÔNG ĐƯỢC có nút "Đặt sân" và "Chat với chủ sân"; người chơi vẫn xem được thông tin và bỏ yêu thích được nhưng không thêm yêu thích mới được.
- **FR-022**: Cơ sở không còn tồn tại PHẢI hiện màn hình "Sân không còn tồn tại" kèm nút quay lại, và nút "Bỏ khỏi yêu thích" nếu cơ sở nằm trong yêu thích của người chơi.
- **FR-023**: Nếu cơ sở đổi sang tạm ngừng trong lúc người chơi đang xem, thao tác "Đặt sân" PHẢI bị từ chối kèm thông báo và màn hình cập nhật theo trạng thái mới.

**Hiển thị, hiệu năng, lỗi**

- **FR-024**: Phần đầu màn hình (ảnh đầu tiên, tên, điểm, giá, nút "Đặt sân") PHẢI hiển thị trước; bản đồ và đánh giá được tải sau và mỗi phần có trạng thái đang tải riêng.
- **FR-025**: Màn hình PHẢI có trạng thái đang tải, có dữ liệu, lỗi kèm "Thử lại"; từng mục (đánh giá, bản đồ, bảng giá) tải lỗi thì chỉ mục đó báo lỗi kèm "Thử lại".
- **FR-026**: Khi mất mạng, app KHÔNG ĐƯỢC crash hay treo; nếu đã từng tải cơ sở này thì hiện dữ liệu đã tải kèm thông báo có thể chưa cập nhật.
- **FR-027**: Màn hình PHẢI hiển thị đúng ở chế độ sáng và tối, trên điện thoại và máy tính bảng (máy tính bảng tận dụng chiều ngang, ví dụ thư viện ảnh và thông tin đặt cạnh nhau); ảnh, biểu tượng tiện ích, nút có mô tả cho trình đọc màn hình và vùng chạm đủ lớn.

### Key Entities *(include if feature involves data)*

- **Cơ sở (Venue)** (của spec 111): tên, địa chỉ, quận/huyện, vị trí, giờ mở và đóng cửa, số điện thoại, danh sách tiện ích, danh sách ảnh, điểm trung bình, số lượt và tổng điểm theo tiêu chí, chủ sở hữu, trạng thái (hoạt động / bị ẩn / bị khóa). Spec này chỉ đọc.
- **Sân con (Court)** (của spec 111): tên, loại mặt sân (Gỗ, PU, Thảm), đang hoạt động hay tạm ngưng.
- **Mức giá (Price rule)** (của spec 111): nhóm ngày trong tuần, giờ bắt đầu, giờ kết thúc, giá mỗi khung 30 phút, nhãn (Giờ thường / Giờ cao điểm / Cuối tuần). Các mức cùng ngày không chồng giờ.
- **Đánh giá (Review)** (của spec 070): người viết (hoặc đã xóa), điểm từng tiêu chí, điểm tổng, bình luận, ảnh, ngày, phản hồi của chủ sân, trạng thái hiển thị.
- **Danh mục quận/huyện và tiện ích**: tên và biểu tượng dùng để hiển thị.
- **Ngữ cảnh từ tìm kiếm**: ngày và khung giờ người chơi đã chọn ở spec 020/022, được mang sang để làm nổi bật giá và mở lưới giờ.
- **Thao tác đang chờ đăng nhập**: thao tác khách vừa bấm (đặt sân, yêu thích, chat) cùng cơ sở và ngữ cảnh, được tiếp tục sau khi đăng nhập.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Trên mạng 4G thông thường, phần đầu màn hình (ảnh đầu tiên, tên, giá, nút "Đặt sân") hiển thị trong vòng 3 giây sau khi bấm vào cơ sở ở ít nhất 95% số lần thử.
- **SC-002**: Trong 100% kịch bản kiểm thử có khung giờ từ tìm kiếm, giá "Khung bạn chọn" bằng đúng giá trên thẻ kết quả (spec 020) và giá ở màn hình đặt sân (spec 040) cho cùng khung.
- **SC-003**: Người chơi tìm được giá giờ cao điểm, giờ đóng cửa và số điện thoại của một cơ sở trong dưới 30 giây kể từ khi mở chi tiết, ở ít nhất 4/5 người thử.
- **SC-004**: 100% lần khách bấm "Đặt sân", trái tim hoặc "Chat với chủ sân" rồi đăng nhập thành công đều quay về đúng cơ sở và thao tác được tiếp tục mà không phải bấm lại.
- **SC-005**: 0 trường hợp đặt được hoặc chat được với cơ sở đang tạm ngừng hoạt động từ màn hình chi tiết trong các kịch bản kiểm thử.
- **SC-006**: Điểm trung bình, số lượt và 3 đánh giá mới nhất trên màn hình chi tiết khớp 100% với danh sách đánh giá đang hiển thị của spec 070 cho cùng cơ sở.
- **SC-007**: Khi mất mạng hoặc một mục tải lỗi, app không crash lần nào và luôn có cách thử lại ở 100% kịch bản kiểm thử.

## Assumptions

- Thông tin cơ sở, sân con, bảng giá và ảnh do chủ sân nhập ở spec 111; điểm và tổng điểm theo tiêu chí do spec 070 cập nhật; spec này chỉ đọc.
- Giờ mở cửa giống nhau cho mọi ngày trong tuần ở phiên bản này (theo đặc tả CSDL hiện tại); nếu spec 111 mở rộng theo từng thứ thì spec này hiển thị theo thứ.
- Quy tắc tính giá của một khung (từng khung 30 phút, quy ra đ/giờ) dùng chung với spec 020 và 040; điểm giao B ↔ D "Tính giá từ bảng giá" phải chốt trước `/speckit-plan`, và spec này theo kết quả đó.
- Khách chưa đăng nhập đọc được thông tin công khai của cơ sở đang hoạt động, sân con, bảng giá, đánh giá đang hiển thị và danh mục (cùng yêu cầu với spec 020). Người chơi mở được theo liên kết trực tiếp một cơ sở đang tạm ngừng để thấy ghi chú (cùng đề xuất với spec 070 research R6). Hai điểm này cần D xác nhận ở bảng phân quyền của đặc tả CSDL.
- Số điện thoại của cơ sở được công khai cho mọi người (đã chốt ở Clarifications); spec 111 cần ghi rõ cho chủ sân biết khi nhập số này.
- Luồng "đăng nhập rồi quay lại màn hình trước và tiếp tục thao tác" do spec 010 cung cấp; spec 020 và 022 cũng cần cùng cơ chế.
- Lối vào chat riêng với chủ sân và nội dung hội thoại thuộc spec 091 (C); spec này chỉ đặt nút và truyền cơ sở cần chat.
- Bản đồ dùng dịch vụ bản đồ theo constitution (có phương án thay thế khi không có tài khoản thanh toán); marker quả cầu lông dùng chung với spec 021.
- Nút chia sẻ cơ sở và danh sách buổi vãng lai của cơ sở không thuộc phiên bản này.
