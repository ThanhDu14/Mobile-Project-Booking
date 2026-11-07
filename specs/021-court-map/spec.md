# Feature Specification: Bản đồ sân và sân gần bạn

**Feature Branch**: `feature/02-court-map`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi (kể cả khách chưa đăng nhập) xem các cơ sở sân cầu lông trên bản đồ và tìm sân gần mình. Chế độ bản đồ: trên màn hình tìm kiếm của spec 020 có nút chuyển Danh sách / Bản đồ; hai chế độ dùng chung một bộ lọc, đổi chế độ không mất bộ lọc. Mỗi cơ sở đang hoạt động khớp bộ lọc hiện thành marker quả cầu lông; nhiều marker gần nhau gộp thành cụm có số lượng, phóng to thì tách ra. Chạm marker: làm nổi bật và hiện thẻ tóm tắt (ảnh, tên, quận, giá "từ X đ/giờ" hoặc giá của khung đã chọn theo spec 020, điểm và số lượt, khoảng cách nếu có vị trí); vuốt ngang thẻ để chuyển cơ sở kế bên và bản đồ di theo; chạm thẻ mở chi tiết sân (spec 030) mang theo ngày và khung giờ. Kéo hoặc phóng sang vùng khác thì hiện nút "Tìm ở khu vực này", không tự tải lại liên tục. Nút "Vị trí của tôi"; xin quyền vị trí lúc bấm nút hoặc mở "Sân gần bạn" lần đầu, có giải thích; từ chối thì mở ở khu vực mặc định. Sân gần bạn: gợi ý cơ sở gần nhất, xếp theo khoảng cách, nút "Xem tất cả trên bản đồ"; không có cơ sở nào trong bán kính thì báo và gợi ý mở rộng. Vị trí chỉ dùng trên thiết bị. Trạng thái đang tải, rỗng, lỗi; bản đồ lỗi thì chuyển về danh sách. Marker trong vùng hiện dưới 3 giây trên 4G. Điện thoại và máy tính bảng, sáng và tối, trình đọc màn hình. Không bao gồm: bộ lọc, sắp xếp, lịch sử (020); câu tự do (022); bản đồ nhúng trong chi tiết sân (030); buổi vãng lai trên bản đồ (080); chủ sân ghim vị trí (110, 111). Dữ liệu: venues, districts, quy tắc giờ trống và giá theo khung của spec 020."

**Nhóm tính năng**: [02 — Tìm kiếm sân](../../docs/features/02-court-search/README.md) · **Phụ trách**: A · **Liên quan**: [spec 020](../020-court-search/spec.md) (bộ lọc, danh sách), [spec 022](../022-ai-court-search/spec.md) (AI điền bộ lọc), [spec 030](../030-court-detail/spec.md) (chi tiết sân), spec 111 (vị trí cơ sở)

## Clarifications

### Session 2026-10-06

- Q: Khu vực mặc định khi không có vị trí là ở đâu? → A: Luôn mở ở trung tâm TP.HCM, mức phóng thấy được vài quận.
- Q: Mục "Sân gần bạn" đặt ở đâu? → A: Ngay bên dưới thanh tìm kiếm trên màn hình tìm kiếm của spec 020.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xem sân trên bản đồ và mở chi tiết (Priority: P1)

Trên màn hình tìm kiếm, người chơi chuyển sang chế độ "Bản đồ" và thấy các cơ sở khớp bộ lọc hiện thành marker quả cầu lông. Chạm vào một marker, thẻ tóm tắt hiện ở cuối màn hình; chạm thẻ để mở chi tiết sân.

**Why this priority**: Người chơi thường chọn sân theo vị trí (gần nhà, gần chỗ làm); nhìn trên bản đồ nhanh hơn đọc địa chỉ từng thẻ. Riêng story này đã là một MVP của spec bản đồ.

**Independent Test**: Với dữ liệu demo có 10 cơ sở ở 3 quận, chuyển sang bản đồ thấy đủ 10 marker đúng vị trí; chạm một marker thấy thẻ của đúng cơ sở đó; chạm thẻ mở đúng chi tiết sân.

**Acceptance Scenarios**:

1. **Given** người chơi đang ở chế độ danh sách của spec 020, **When** bấm "Bản đồ", **Then** bản đồ hiện các cơ sở đang hoạt động khớp bộ lọc hiện tại, mỗi cơ sở là một marker quả cầu lông tại vị trí của cơ sở.
2. **Given** đang có bộ lọc "Quận 10 · ≤100k · Gửi xe", **When** chuyển qua lại giữa "Danh sách" và "Bản đồ", **Then** bộ lọc và các chip giữ nguyên; tập cơ sở trên bản đồ trùng với tập cơ sở trong danh sách cho cùng vùng.
3. **Given** bản đồ đang hiện marker, **When** người chơi chạm một marker, **Then** marker đó được làm nổi bật và thẻ tóm tắt hiện ở cuối màn hình gồm ảnh, tên, quận, giá, điểm và số lượt đánh giá, và khoảng cách nếu có vị trí.
4. **Given** bộ lọc có khung giờ ngày mai 19:00–21:00, **When** xem thẻ tóm tắt, **Then** giá trên thẻ là giá của khung đó, giống giá trên thẻ ở chế độ danh sách của spec 020.
5. **Given** thẻ tóm tắt đang hiện, **When** người chơi chạm vào thẻ, **Then** chi tiết sân (spec 030) của đúng cơ sở đó mở ra, mang theo ngày và khung giờ của bộ lọc nếu có; quay lại thì bản đồ ở đúng vị trí, mức phóng và cơ sở đang chọn như trước.
6. **Given** thẻ tóm tắt đang hiện, **When** người chơi chạm vào vùng trống trên bản đồ, **Then** thẻ ẩn đi và marker hết nổi bật.
7. **Given** khách chưa đăng nhập, **When** mở chế độ bản đồ, **Then** dùng được đầy đủ như người đã đăng nhập.

---

### User Story 2 - Duyệt nhiều sân: cụm marker, vuốt thẻ, tìm ở khu vực này (Priority: P2)

Khi có nhiều cơ sở gần nhau, marker được gộp thành cụm có số lượng. Người chơi vuốt ngang thẻ tóm tắt để xem lần lượt các cơ sở kế bên, bản đồ di theo. Khi kéo bản đồ sang vùng khác, nút "Tìm ở khu vực này" hiện ra để tải các cơ sở trong vùng mới.

**Why this priority**: Giúp so sánh nhiều sân trong một khu vực mà không phải chạm từng marker; cần thiết khi có nhiều cơ sở, nhưng với dữ liệu ít thì US1 đã đủ dùng.

**Independent Test**: Với 8 cơ sở nằm sát nhau ở Quận 10, ở mức phóng xa thấy một cụm "8"; chạm cụm thì bản đồ phóng vào và tách thành marker riêng; vuốt thẻ qua lại thấy bản đồ di theo từng cơ sở; kéo bản đồ sang Quận 3 thấy nút "Tìm ở khu vực này", bấm thì hiện các cơ sở ở Quận 3.

**Acceptance Scenarios**:

1. **Given** nhiều cơ sở ở gần nhau tới mức marker chồng lên nhau, **When** xem bản đồ, **Then** các marker đó được gộp thành một cụm hiện số lượng cơ sở.
2. **Given** bản đồ đang có một cụm, **When** người chơi chạm vào cụm, **Then** bản đồ phóng vào vùng của cụm cho tới khi các marker tách ra (hoặc tới mức phóng tối đa thì hiện các cơ sở của cụm thành dãy thẻ để vuốt).
3. **Given** thẻ tóm tắt đang hiện, **When** người chơi vuốt ngang sang trái hoặc phải, **Then** thẻ chuyển sang cơ sở kế bên trong vùng đang xem (theo khoảng cách tới cơ sở đang chọn), marker tương ứng được làm nổi bật và bản đồ di tới cơ sở đó.
4. **Given** người chơi kéo hoặc phóng bản đồ sang vùng khác, **When** dừng thao tác, **Then** nút "Tìm ở khu vực này" hiện ra; các marker cũ vẫn giữ nguyên, app không tự tải lại.
5. **Given** nút "Tìm ở khu vực này" đang hiện, **When** người chơi bấm, **Then** app tải các cơ sở khớp bộ lọc trong vùng đang xem và nút ẩn đi.
6. **Given** bộ lọc đang có chọn quận/huyện, **When** người chơi bấm "Tìm ở khu vực này" ở một vùng thuộc quận khác, **Then** app bỏ bộ lọc quận (chip quận biến mất) và báo ngắn "Đã tìm theo khu vực trên bản đồ"; các bộ lọc khác giữ nguyên.
7. **Given** vùng đang xem quá rộng (phóng xa quá mức cho phép), **When** người chơi bấm "Tìm ở khu vực này", **Then** app đề nghị phóng gần hơn thay vì tải.

---

### User Story 3 - Vị trí của tôi (Priority: P2)

Người chơi bấm nút "Vị trí của tôi" để đưa bản đồ về chỗ mình đang đứng, thấy chấm vị trí của mình và khoảng cách tới từng cơ sở.

**Why this priority**: Phần lớn người chơi tìm sân gần chỗ hiện tại; nhưng bản đồ (US1) vẫn dùng được khi không có vị trí.

**Independent Test**: Cho phép vị trí trên máy thử đặt tại Quận 1, bấm "Vị trí của tôi" thấy bản đồ về Quận 1, có chấm vị trí và thẻ tóm tắt có khoảng cách; từ chối quyền thì bản đồ ở khu vực mặc định và mọi thứ khác vẫn chạy.

**Acceptance Scenarios**:

1. **Given** app chưa có quyền vị trí, **When** người chơi bấm "Vị trí của tôi", **Then** app giải thích ngắn vì sao cần vị trí rồi mới hiện hộp xin quyền của hệ thống.
2. **Given** người chơi cho phép, **When** lấy được vị trí, **Then** bản đồ di về vị trí hiện tại, hiện chấm vị trí, và app tải các cơ sở khớp bộ lọc quanh đó.
3. **Given** có vị trí, **When** xem thẻ tóm tắt, **Then** thẻ hiện khoảng cách đường chim bay tới cơ sở (dưới 1 km theo mét, từ 1 km 1 chữ số thập phân), giống quy tắc của spec 020.
4. **Given** người chơi từ chối quyền vị trí, **When** quay lại bản đồ, **Then** bản đồ ở khu vực mặc định, không có chấm vị trí, thẻ không hiện khoảng cách, có thông báo ngắn kèm lối vào cài đặt; mọi chức năng khác vẫn dùng được.
5. **Given** đã cho phép nhưng định vị của máy đang tắt hoặc không lấy được vị trí trong 10 giây, **When** bấm "Vị trí của tôi", **Then** app báo không lấy được vị trí, gợi ý bật định vị và giữ nguyên vùng đang xem.
6. **Given** người chơi mở bản đồ lần đầu, **When** chưa từng cho phép vị trí, **Then** bản đồ mở ở khu vực mặc định và app không tự xin quyền.

---

### User Story 4 - Sân gần bạn (Priority: P2)

Ngay bên dưới thanh tìm kiếm của màn hình tìm kiếm (spec 020), người chơi thấy mục "Sân gần bạn" để xem các cơ sở đang hoạt động gần vị trí hiện tại nhất, xếp theo khoảng cách; bấm một cơ sở để mở chi tiết, hoặc "Xem tất cả trên bản đồ" để mở chế độ bản đồ quanh vị trí của mình.

**Why this priority**: Là lối tắt nhanh nhất khi người chơi chỉ cần "sân nào gần đây", nằm trong phạm vi proposal; nhưng tìm kiếm (spec 020) và bản đồ (US1) đã đáp ứng được nhu cầu này với vài thao tác hơn.

**Independent Test**: Đặt vị trí máy thử ở giữa 3 cơ sở cách 0,8 km, 2,5 km và 12 km; mở "Sân gần bạn" thấy 2 cơ sở đầu theo đúng thứ tự khoảng cách, cơ sở cách 12 km không có; bấm "Xem tất cả trên bản đồ" mở bản đồ quanh vị trí đó.

**Acceptance Scenarios**:

1. **Given** người chơi chưa cho quyền vị trí, **When** mở "Sân gần bạn" lần đầu, **Then** app giải thích ngắn rồi xin quyền vị trí.
2. **Given** có vị trí, **When** mục "Sân gần bạn" hiện ra, **Then** thấy tối đa 10 cơ sở đang hoạt động trong bán kính 5 km, xếp theo khoảng cách tăng dần, mỗi cơ sở có ảnh, tên, khoảng cách, giá "từ X đ/giờ" và điểm đánh giá.
3. **Given** người chơi bấm một cơ sở trong mục, **When** màn hình chuyển, **Then** chi tiết sân (spec 030) của cơ sở đó mở ra.
4. **Given** người chơi bấm "Xem tất cả trên bản đồ", **When** màn hình chuyển, **Then** chế độ bản đồ mở quanh vị trí hiện tại với chấm vị trí và các cơ sở xung quanh, không mang theo bộ lọc nào.
5. **Given** không có cơ sở nào trong bán kính 5 km, **When** mục hiện ra, **Then** thấy "Chưa có sân trong bán kính 5 km quanh bạn" kèm nút "Tìm rộng hơn" (mở chế độ bản đồ ở mức phóng xa hơn quanh vị trí) và nút "Tìm theo quận" (mở tìm kiếm của spec 020).
6. **Given** người chơi từ chối quyền vị trí hoặc không lấy được vị trí, **When** xem mục "Sân gần bạn", **Then** mục hiện lời giải thích kèm nút "Bật vị trí" và nút "Tìm theo quận"; không hiện danh sách.
7. **Given** người chơi mở màn hình tìm kiếm, chưa nhập từ khóa và chưa có bộ lọc, **When** màn hình hiện ra, **Then** mục "Sân gần bạn" nằm ngay bên dưới thanh tìm kiếm, dạng dải thẻ vuốt ngang.
8. **Given** mục "Sân gần bạn" đang hiện, **When** người chơi bắt đầu tìm (gõ từ khóa, chọn bộ lọc) hoặc chạm vào thanh tìm kiếm để xem lịch sử (spec 020), **Then** mục thu gọn để nhường chỗ cho kết quả hoặc lịch sử; xóa hết từ khóa và bộ lọc thì mục hiện lại.

---

### Edge Cases

- **Mất mạng khi đang xem bản đồ**: các marker và thẻ đã tải vẫn hiện kèm thông báo "Không có kết nối, kết quả có thể chưa cập nhật"; nút "Tìm ở khu vực này" báo lỗi kèm "Thử lại". Không crash.
- **Bản đồ không tải được** (dịch vụ bản đồ lỗi, mất mạng ngay từ đầu): app báo ngắn và chuyển về chế độ danh sách của spec 020 với bộ lọc giữ nguyên; nút "Bản đồ" vẫn còn để thử lại.
- **Không có cơ sở nào trong vùng hoặc khớp bộ lọc**: hiện thông báo trên bản đồ "Không có sân phù hợp trong khu vực này" kèm gợi ý "Thu nhỏ bản đồ" và "Xóa bộ lọc".
- **Cơ sở thiếu vị trí hợp lệ**: không hiện trên bản đồ và không có trong "Sân gần bạn", nhưng vẫn có trong danh sách của spec 020.
- **Nhiều cơ sở cùng một địa chỉ (cùng tọa độ)**: luôn ở trong một cụm; chạm cụm ở mức phóng tối đa thì hiện dãy thẻ để vuốt qua từng cơ sở.
- **Bộ lọc do spec 022 (AI) điền vào trong lúc đang ở chế độ bản đồ**: bản đồ cập nhật marker theo bộ lọc mới, giữ vùng đang xem.
- **Người chơi đổi bộ lọc khi đang ở bản đồ**: tải lại marker theo bộ lọc mới trong vùng đang xem ngay lập tức (đây là thay đổi do người chơi chủ động, khác với kéo bản đồ).
- **Cơ sở bị ẩn hoặc khóa sau khi marker đã hiện**: marker có thể còn tới lần tải kế tiếp; mở chi tiết thì spec 030 hiện ghi chú tạm ngừng.
- **Vị trí của người chơi nằm ngoài vùng có dữ liệu** (ví dụ ở tỉnh khác): "Sân gần bạn" hiện trạng thái không có sân trong bán kính; bản đồ vẫn về vị trí đó khi bấm "Vị trí của tôi".
- **Vị trí thay đổi khi đang mở bản đồ** (người chơi đang di chuyển): chấm vị trí cập nhật, nhưng bản đồ không tự di theo và danh sách không tự tải lại.
- **Xoay màn hình hoặc chuyển sáng/tối**: giữ vùng đang xem, mức phóng, cơ sở đang chọn và bộ lọc.
- **Máy tính bảng**: danh sách và bản đồ hiện cạnh nhau; chọn một cơ sở ở bên này thì bên kia làm nổi bật cùng cơ sở.

## Requirements *(mandatory)*

### Functional Requirements

**Chế độ bản đồ**

- **FR-001**: Màn hình tìm kiếm của spec 020 PHẢI có nút chuyển "Danh sách / Bản đồ". Hai chế độ PHẢI dùng chung một bộ lọc tìm kiếm (từ khóa, quận/huyện, khoảng giá, ngày và khung giờ, tiện ích) và chuyển qua lại không làm mất bộ lọc.
- **FR-002**: Ở chế độ bản đồ, mỗi cơ sở đang hoạt động, có vị trí hợp lệ, khớp bộ lọc và nằm trong vùng đang xem PHẢI hiện thành một marker hình quả cầu lông tại vị trí của cơ sở. Quy tắc khớp bộ lọc (kể cả khung giờ còn trống và giá theo khung) PHẢI giống spec 020.
- **FR-003**: Các marker chồng lên nhau ở mức phóng hiện tại PHẢI được gộp thành một cụm hiện số lượng; chạm cụm thì phóng vào vùng của cụm; ở mức phóng tối đa mà vẫn chồng thì hiện các cơ sở của cụm thành dãy thẻ để vuốt.
- **FR-004**: Khi bộ lọc thay đổi (do người chơi hoặc do spec 022), bản đồ PHẢI tải lại marker trong vùng đang xem; khi người chơi kéo hoặc phóng bản đồ, app KHÔNG ĐƯỢC tự tải lại mà PHẢI hiện nút "Tìm ở khu vực này".
- **FR-005**: Bấm "Tìm ở khu vực này" PHẢI tải các cơ sở khớp bộ lọc trong vùng đang xem. Nếu đang có bộ lọc quận/huyện, bộ lọc đó PHẢI được bỏ (kèm thông báo) để tìm theo vùng bản đồ. Nếu vùng quá rộng, app PHẢI đề nghị phóng gần hơn thay vì tải.
- **FR-006**: Mỗi lần tải PHẢI giới hạn tối đa 100 cơ sở; nếu vùng có nhiều hơn, app hiện các cơ sở gần tâm vùng nhất và báo "Phóng gần hơn để xem thêm sân".

**Marker và thẻ tóm tắt**

- **FR-007**: Chạm một marker PHẢI làm nổi bật marker đó và hiện thẻ tóm tắt ở cuối màn hình gồm: ảnh đại diện (hoặc ảnh mặc định), tên, quận/huyện, giá (theo đúng quy tắc hiển thị của spec 020: "từ X đ/giờ" khi không có khung giờ, giá của khung khi có, "Liên hệ" khi chưa có giá), điểm trung bình kèm số lượt (hoặc "Chưa có đánh giá"), và khoảng cách khi có vị trí.
- **FR-008**: Vuốt ngang thẻ tóm tắt PHẢI chuyển sang cơ sở kế bên trong vùng đang xem (sắp theo khoảng cách tới cơ sở đang chọn), đồng thời làm nổi bật marker tương ứng và di bản đồ tới cơ sở đó.
- **FR-009**: Chạm thẻ tóm tắt PHẢI mở chi tiết sân (spec 030) của đúng cơ sở, mang theo ngày và khung giờ của bộ lọc nếu có. Quay lại PHẢI giữ nguyên vùng đang xem, mức phóng, cơ sở đang chọn và bộ lọc.
- **FR-010**: Chạm vùng trống trên bản đồ PHẢI ẩn thẻ tóm tắt và bỏ làm nổi bật marker.

**Vị trí**

- **FR-011**: Nút "Vị trí của tôi" PHẢI đưa bản đồ về vị trí hiện tại, hiện chấm vị trí và tải các cơ sở khớp bộ lọc quanh đó.
- **FR-012**: App PHẢI chỉ xin quyền vị trí khi người chơi bấm "Vị trí của tôi" hoặc mở "Sân gần bạn" lần đầu, kèm giải thích ngắn trước hộp xin quyền; chấp nhận cả vị trí gần đúng. Mở bản đồ KHÔNG ĐƯỢC tự xin quyền.
- **FR-013**: Khi không có vị trí (từ chối quyền, định vị tắt, không lấy được trong 10 giây), bản đồ PHẢI mở ở khu vực mặc định, không hiện khoảng cách, có thông báo kèm lối vào cài đặt, và mọi chức năng khác vẫn hoạt động. Khu vực mặc định là trung tâm TP.HCM với mức phóng thấy được vài quận, giống nhau cho mọi người chơi.
- **FR-014**: Khoảng cách PHẢI là khoảng cách đường chim bay, hiển thị theo cùng quy tắc với spec 020.
- **FR-015**: Vị trí của người chơi KHÔNG ĐƯỢC lưu lên máy chủ hay vào lịch sử tìm kiếm; chỉ dùng trên thiết bị để hiển thị, tính khoảng cách và chọn vùng tải.

**Sân gần bạn**

- **FR-015a**: Mục "Sân gần bạn" PHẢI nằm ngay bên dưới thanh tìm kiếm trên màn hình tìm kiếm của spec 020, dạng dải thẻ vuốt ngang; mục hiện khi chưa có từ khóa và bộ lọc, thu gọn khi người chơi bắt đầu tìm hoặc mở lịch sử tìm kiếm, và hiện lại khi xóa hết từ khóa và bộ lọc.
- **FR-016**: Mục "Sân gần bạn" PHẢI hiện tối đa 10 cơ sở đang hoạt động trong bán kính 5 km quanh vị trí hiện tại, xếp theo khoảng cách tăng dần; mỗi cơ sở có ảnh, tên, khoảng cách, giá "từ X đ/giờ" và điểm đánh giá; bấm một cơ sở mở chi tiết sân (spec 030).
- **FR-017**: Mục "Sân gần bạn" PHẢI có nút "Xem tất cả trên bản đồ" mở chế độ bản đồ quanh vị trí hiện tại (không mang bộ lọc).
- **FR-018**: Không có cơ sở nào trong bán kính thì mục PHẢI báo rõ kèm nút "Tìm rộng hơn" và "Tìm theo quận"; không có vị trí thì mục PHẢI hiện lời giải thích kèm nút "Bật vị trí" và "Tìm theo quận".

**Trạng thái, hiển thị, hiệu năng**

- **FR-019**: Chế độ bản đồ PHẢI có trạng thái đang tải marker (không che bản đồ), không có kết quả (gợi ý thu nhỏ bản đồ hoặc xóa bộ lọc), và lỗi kèm "Thử lại".
- **FR-020**: Khi bản đồ không tải được, app PHẢI báo ngắn và chuyển về chế độ danh sách của spec 020 với bộ lọc giữ nguyên; không crash, không treo.
- **FR-021**: Khi mất mạng sau khi đã tải, marker và thẻ đã có PHẢI vẫn hiển thị kèm thông báo có thể chưa cập nhật.
- **FR-022**: Khách chưa đăng nhập PHẢI dùng được toàn bộ chế độ bản đồ, "Vị trí của tôi" và "Sân gần bạn".
- **FR-023**: Màn hình PHẢI hiển thị đúng ở chế độ sáng và tối, trên điện thoại và máy tính bảng; trên máy tính bảng, danh sách và bản đồ PHẢI có thể hiện cạnh nhau và đồng bộ cơ sở đang chọn. Marker, cụm và các nút PHẢI có mô tả cho trình đọc màn hình (ví dụ "Sân Hòa Bình, 1,2 km, 4,5 sao"; "Cụm 8 sân") và vùng chạm đủ lớn.

### Key Entities *(include if feature involves data)*

- **Cơ sở (Venue)** (của spec 111): vị trí trên bản đồ, tên, quận/huyện, ảnh đại diện, giá thấp nhất mỗi giờ, điểm và số lượt đánh giá, trạng thái. Spec này chỉ đọc.
- **Bộ lọc tìm kiếm** (của spec 020): dùng chung giữa danh sách, bản đồ và spec 022.
- **Vùng bản đồ đang xem**: tâm, mức phóng và phạm vi đang hiển thị; dùng để chọn cơ sở cần tải. Không lưu lên máy chủ.
- **Cụm marker**: nhóm cơ sở nằm gần nhau ở mức phóng hiện tại, kèm số lượng.
- **Vị trí hiện tại của người chơi**: chỉ tồn tại trên thiết bị khi được cho phép.
- **Danh mục quận/huyện**: tên quận để hiển thị trên thẻ.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Trên mạng 4G thông thường, các marker trong vùng đang xem hiện đầy đủ trong vòng 3 giây sau khi chuyển sang bản đồ, đổi bộ lọc hoặc bấm "Tìm ở khu vực này" ở ít nhất 95% số lần thử.
- **SC-002**: Với cùng bộ lọc và cùng vùng, tập cơ sở trên bản đồ trùng 100% với tập cơ sở trong danh sách của spec 020 trong các kịch bản kiểm thử.
- **SC-003**: Giá và khoảng cách trên thẻ tóm tắt khớp 100% với thẻ ở chế độ danh sách của spec 020 cho cùng cơ sở và cùng bộ lọc.
- **SC-004**: Người chơi tìm được và mở chi tiết một sân gần mình nhất trong dưới 30 giây kể từ khi mở bản đồ hoặc "Sân gần bạn", ở ít nhất 4/5 người thử.
- **SC-005**: Khi từ chối quyền vị trí, mất mạng hoặc bản đồ không tải được, app không crash lần nào và người chơi vẫn tìm được sân (bằng bản đồ ở khu vực mặc định hoặc chế độ danh sách) ở 100% kịch bản kiểm thử.
- **SC-006**: 0 lần vị trí của người chơi xuất hiện trong dữ liệu lưu trên máy chủ hoặc lịch sử tìm kiếm trong kiểm thử.
- **SC-007**: Kéo và phóng bản đồ liên tục trong 1 phút không làm app tự tải lại cơ sở lần nào (chỉ tải khi bấm "Tìm ở khu vực này", đổi bộ lọc hoặc bấm "Vị trí của tôi").

## Assumptions

- Vị trí của cơ sở do chủ sân ghim khi nộp hồ sơ và khi sửa cơ sở (spec 110, 111); spec này chỉ đọc. Dữ liệu phục vụ tìm theo vùng và theo khoảng cách do A định nghĩa, D ghi khi tạo/sửa cơ sở (cùng điểm giao A ↔ D của spec 020).
- Dịch vụ bản đồ theo constitution (có phương án thay thế khi không có tài khoản thanh toán); marker quả cầu lông dùng chung với bản đồ nhúng của spec 030. Nhóm chốt dịch vụ cụ thể ở buổi họp 09/10.
- Bán kính 5 km, tối đa 10 cơ sở ở "Sân gần bạn", tối đa 100 cơ sở mỗi lần tải bản đồ, chờ vị trí 10 giây là giá trị mặc định theo thông lệ và hạn mức gói miễn phí; có thể chỉnh trong `/speckit-clarify`.
- Lọc theo khung giờ còn trống và giá theo khung trên bản đồ dùng đúng quy tắc của spec 020, kéo theo cùng chi phí đọc dữ liệu; giới hạn 100 cơ sở mỗi lần tải là để giữ chi phí này trong hạn mức.
- Khách chưa đăng nhập dùng được bản đồ vì đã được phép tìm sân ở spec 020; quyền đọc công khai dữ liệu cơ sở cần D xác nhận (đã nêu ở spec 020 và 030).
- Khu vực mặc định cố định ở trung tâm TP.HCM (đã chốt ở Clarifications), nên không cần thêm tọa độ tâm cho từng quận vào dữ liệu danh mục.
- Mục "Sân gần bạn" thuộc màn hình tìm kiếm của spec 020 (đã chốt ở Clarifications); spec 020 cần chừa vị trí ngay dưới thanh tìm kiếm cho mục này. Trang chủ không thuộc phạm vi spec này.
- Buổi vãng lai không hiện trên bản đồ ở phiên bản này (thuộc spec 080 nếu làm sau).
