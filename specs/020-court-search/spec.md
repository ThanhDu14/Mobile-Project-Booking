# Feature Specification: Tìm kiếm sân

**Feature Branch**: `feature/02-court-search`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Người chơi tìm cơ sở sân cầu lông để đặt. Màn hình tìm kiếm có ô tìm theo tên sân (gõ không dấu, viết hoa hay thường đều khớp, khớp theo phần đầu của từ) và chọn quận/huyện từ danh mục. Bộ lọc gồm: khoảng giá theo giờ; khung giờ còn trống (chọn ngày + giờ bắt đầu/kết thúc, chỉ hiện cơ sở có ít nhất một sân con trống trọn khung đó); tiện ích (gửi xe, nước uống, phòng tắm, cho thuê vợt; cơ sở phải có đủ mọi tiện ích đã chọn). Bộ lọc đang áp dụng hiện thành chip, bỏ từng chip hoặc xóa hết được. Sắp xếp theo: gần nhất, giá thấp đến cao, đánh giá cao nhất. Thẻ kết quả gồm ảnh, tên, quận, giá "từ X đ/giờ", điểm và số lượt đánh giá, khoảng cách (khi có vị trí); danh sách tải thêm khi cuộn; bấm thẻ mở chi tiết sân. Chỉ hiện cơ sở đang hoạt động. Lịch sử tìm kiếm: lưu các lần tìm gần đây của chính người dùng (từ khóa + bộ lọc), bấm để tìm lại, xóa từng mục hoặc xóa hết; người khác không xem được. Vị trí: xin quyền lúc người dùng chọn "gần nhất"; từ chối thì ẩn khoảng cách, không sắp xếp theo khoảng cách được, các chức năng khác vẫn dùng bình thường. Bộ lọc là đầu vào dùng chung: spec 022 (AI) điền vào đúng bộ lọc này rồi chạy cùng truy vấn. Có trạng thái đang tải, rỗng (gợi ý bỏ bớt bộ lọc), lỗi kèm thử lại; mất mạng không crash. Danh sách trang đầu tải dưới 3 giây trên 4G. Không bao gồm: bản đồ, marker và gợi ý sân gần vị trí hiện tại (spec 021); tìm bằng câu tự do hoặc giọng nói (spec 022); chi tiết sân (spec 030); lưới giờ và đặt sân (spec 040); tìm buổi vãng lai (spec 080). Dữ liệu: venues (nameKeywords, districtId, amenityIds, minPricePerHour, ratingAvg, geohash, status), slots để xét giờ trống, districts, amenities, users/{uid}/searchHistory theo docs/design/database/README.md."

**Nhóm tính năng**: [02 — Tìm kiếm sân](../../docs/features/02-court-search/README.md) · **Phụ trách**: A · **Liên quan**: spec 021 (bản đồ), spec 022 (AI Assistant), spec 030 (chi tiết sân), spec 040 (đặt sân), spec 111 (cơ sở, bảng giá, tiện ích của chủ sân), [spec 011](../011-account-profile/spec.md) (xóa tài khoản)

## Clarifications

### Session 2026-10-05

- Q: Khi người chơi lọc theo khung giờ, giá dùng để lọc là giá thấp nhất của cơ sở hay giá của đúng khung giờ đã chọn? → A: Khi có bộ lọc khung giờ, lọc, sắp xếp và hiển thị theo giá của đúng khung đó (quy ra đ/giờ). Khi không có khung giờ thì dùng giá thấp nhất mỗi giờ của cơ sở.
- Q: Khách chưa đăng nhập có được tìm và xem danh sách sân không? → A: Có. Khách tìm, lọc, sắp xếp và mở chi tiết sân được; chỉ đặt sân và lịch sử tìm kiếm mới cần đăng nhập.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tìm sân theo tên hoặc quận (Priority: P1)

Người chơi (kể cả khách chưa đăng nhập) mở màn hình tìm kiếm, gõ một phần tên sân (có thể không dấu, ví dụ "hoa binh") hoặc chọn một quận/huyện, và thấy danh sách cơ sở phù hợp. Mỗi thẻ có ảnh, tên, quận, giá "từ X đ/giờ", điểm và số lượt đánh giá. Bấm vào thẻ để mở chi tiết sân.

**Why this priority**: Đây là đường vào chính của luồng đặt sân; không tìm được sân thì không đặt được. Chỉ riêng story này đã đủ một MVP tìm kiếm.

**Independent Test**: Với dữ liệu demo có nhiều cơ sở ở các quận khác nhau, gõ "hoa" thấy các sân có từ bắt đầu bằng "Hòa"; chọn "Quận 10" chỉ thấy cơ sở ở Quận 10; bấm một thẻ mở đúng chi tiết sân.

**Acceptance Scenarios**:

1. **Given** có cơ sở tên "Sân cầu lông Hòa Bình", **When** người chơi gõ "hoa binh", "HÒA" hoặc "binh", **Then** cơ sở đó có trong kết quả.
2. **Given** có cơ sở tên "Sân cầu lông Hòa Bình", **When** người chơi gõ "oa bi" (không phải phần đầu của từ nào), **Then** cơ sở đó không có trong kết quả.
3. **Given** người chơi chọn "Quận 10" trong danh mục quận/huyện, **When** danh sách cập nhật, **Then** chỉ còn cơ sở ở Quận 10.
4. **Given** người chơi vừa gõ tên vừa chọn quận, **When** danh sách cập nhật, **Then** kết quả thỏa cả hai điều kiện.
5. **Given** một cơ sở đã bị chủ sân ẩn hoặc bị admin khóa, **When** người chơi tìm đúng tên cơ sở đó, **Then** cơ sở đó không xuất hiện.
6. **Given** có nhiều hơn 20 kết quả, **When** người chơi cuộn tới cuối danh sách, **Then** app tải thêm kết quả tiếp theo cho tới khi hết.
7. **Given** người chơi bấm một thẻ kết quả, **When** màn hình chuyển, **Then** chi tiết sân của đúng cơ sở đó được mở.

---

### User Story 2 - Lọc theo giá, khung giờ còn trống và tiện ích (Priority: P1)

Người chơi mở bảng bộ lọc, chọn khoảng giá theo giờ, chọn ngày và khung giờ muốn chơi, và đánh dấu các tiện ích cần có (gửi xe, nước uống, phòng tắm, cho thuê vợt...). Các bộ lọc đang áp dụng hiện thành chip phía trên danh sách; người chơi bỏ từng chip hoặc bấm "Xóa bộ lọc" để bỏ hết.

**Why this priority**: Người chơi thường đã biết mình muốn chơi lúc nào và trả bao nhiêu; lọc theo giờ còn trống giúp không phải mở từng sân để xem lịch. Đây là giá trị khác biệt chính so với danh bạ sân, và là đầu vào mà spec 022 dùng lại.

**Independent Test**: Với dữ liệu demo có cơ sở A còn trống 19:00–21:00 ngày mai và cơ sở B đã kín khung đó, lọc khung 19:00–21:00 ngày mai thì chỉ thấy A; thêm tiện ích "Gửi xe" thì chỉ thấy cơ sở có gửi xe; bỏ chip khung giờ thì B xuất hiện lại.

**Acceptance Scenarios**:

1. **Given** người chơi đặt khoảng giá 50.000–100.000 đ/giờ và chưa chọn khung giờ, **When** áp dụng, **Then** chỉ còn cơ sở có giá thấp nhất mỗi giờ nằm trong khoảng đó, và chip "50k–100k/giờ" xuất hiện.
2. **Given** một cơ sở có giá thường 80.000 đ/giờ và giá cao điểm 19:00–22:00 là 150.000 đ/giờ, **When** người chơi lọc "dưới 100k" cùng khung 19:00–21:00, **Then** cơ sở đó không có trong kết quả; **When** lọc "dưới 100k" cùng khung 15:00–17:00, **Then** cơ sở đó có trong kết quả và thẻ hiện "80.000 đ/giờ · 15:00–17:00".
3. **Given** người chơi chọn ngày mai, 19:00–21:00, **When** áp dụng, **Then** chỉ còn cơ sở có ít nhất một sân con trống liên tục suốt 19:00–21:00 ngày mai.
4. **Given** một cơ sở có sân con 1 trống 19:00–20:00 và sân con 2 trống 20:00–21:00 nhưng không sân con nào trống cả 19:00–21:00, **When** lọc 19:00–21:00, **Then** cơ sở đó không có trong kết quả.
5. **Given** một khung giờ đang được người khác giữ chỗ nhưng thời gian giữ đã hết, **When** lọc khung đó, **Then** khung đó được coi là trống.
6. **Given** khung giờ đã chọn nằm ngoài giờ mở cửa của một cơ sở, **When** áp dụng, **Then** cơ sở đó không có trong kết quả.
7. **Given** người chơi đánh dấu "Gửi xe" và "Phòng tắm", **When** áp dụng, **Then** chỉ còn cơ sở có đủ cả hai tiện ích.
8. **Given** đang có 3 chip bộ lọc, **When** người chơi bấm "x" trên một chip, **Then** chỉ bộ lọc đó bị bỏ và danh sách cập nhật; **When** bấm "Xóa bộ lọc", **Then** mọi bộ lọc bị bỏ (giữ nguyên từ khóa và quận đã chọn).
9. **Given** người chơi chọn ngày hôm nay, **When** mở danh sách giờ bắt đầu, **Then** không chọn được giờ đã qua; giờ kết thúc luôn phải sau giờ bắt đầu.
10. **Given** bộ lọc không còn kết quả nào, **When** danh sách cập nhật, **Then** app hiện trạng thái rỗng "Không tìm thấy sân phù hợp" kèm gợi ý bỏ bớt bộ lọc và nút "Xóa bộ lọc".

---

### User Story 3 - Sắp xếp kết quả và xem khoảng cách (Priority: P2)

Người chơi chọn cách sắp xếp: gần nhất, giá thấp đến cao, hoặc đánh giá cao nhất. Khi chọn "gần nhất" lần đầu, app xin quyền vị trí; nếu được cho phép, mỗi thẻ hiện thêm khoảng cách tới cơ sở.

**Why this priority**: Giúp chọn nhanh trong nhiều kết quả, nhưng danh sách đã lọc (P1) vẫn dùng được khi chưa sắp xếp.

**Independent Test**: Với 3 cơ sở có giá, điểm và vị trí khác nhau, đổi lần lượt ba cách sắp xếp và kiểm tra thứ tự; từ chối quyền vị trí thì "gần nhất" không áp dụng được còn hai cách kia vẫn chạy.

**Acceptance Scenarios**:

1. **Given** người chơi chọn "Giá thấp đến cao", **When** danh sách cập nhật, **Then** cơ sở được xếp theo giá đang hiển thị trên thẻ tăng dần (giá của khung đã chọn nếu có bộ lọc khung giờ, nếu không thì giá "từ X đ/giờ").
2. **Given** người chơi chọn "Đánh giá cao nhất", **When** danh sách cập nhật, **Then** cơ sở được xếp theo điểm trung bình giảm dần; cùng điểm thì nhiều lượt đánh giá hơn đứng trước; cơ sở chưa có đánh giá đứng cuối.
3. **Given** app chưa có quyền vị trí, **When** người chơi chọn "Gần nhất", **Then** app giải thích ngắn vì sao cần vị trí rồi mới hiện hộp xin quyền của hệ thống.
4. **Given** người chơi cho phép vị trí, **When** sắp xếp "Gần nhất", **Then** cơ sở được xếp theo khoảng cách tăng dần và mỗi thẻ hiện khoảng cách (ví dụ "1,2 km").
5. **Given** người chơi từ chối quyền vị trí, **When** quay lại danh sách, **Then** cách sắp xếp trở về lựa chọn trước đó, thẻ không hiện khoảng cách, có thông báo ngắn kèm lối vào cài đặt để bật lại, và mọi chức năng khác vẫn dùng bình thường.
6. **Given** đã cho phép nhưng vị trí thiết bị đang tắt hoặc không lấy được vị trí, **When** chọn "Gần nhất", **Then** app báo không lấy được vị trí và giữ cách sắp xếp trước đó.

---

### User Story 4 - Lịch sử tìm kiếm (Priority: P3)

Khi bấm vào ô tìm kiếm mà chưa gõ gì, người chơi thấy các lần tìm gần đây của mình (từ khóa và tóm tắt bộ lọc). Bấm một mục để chạy lại đúng lần tìm đó; xóa từng mục hoặc xóa toàn bộ lịch sử.

**Why this priority**: Tiết kiệm thao tác cho người hay tìm cùng một kiểu sân, nhưng không chặn luồng chính.

**Independent Test**: Tìm 3 lần với từ khóa/bộ lọc khác nhau, mở lại ô tìm kiếm thấy đủ 3 mục với lần mới nhất ở đầu; bấm một mục thì từ khóa và chip bộ lọc được khôi phục đúng; xóa một mục và xóa hết đều có hiệu lực sau khi mở lại app.

**Acceptance Scenarios**:

1. **Given** người chơi vừa thực hiện một lần tìm có từ khóa hoặc ít nhất một bộ lọc, **When** mở lại ô tìm kiếm, **Then** lần tìm đó đứng đầu lịch sử, hiện từ khóa và tóm tắt bộ lọc (ví dụ "Quận 10 · Mai 19:00–21:00 · ≤100k").
2. **Given** người chơi bấm một mục lịch sử, **When** màn hình cập nhật, **Then** từ khóa, quận và các bộ lọc của mục đó được điền lại thành chip và danh sách kết quả được chạy lại.
3. **Given** một mục lịch sử có ngày chơi đã qua, **When** người chơi bấm mục đó, **Then** các bộ lọc khác được khôi phục, còn bộ lọc khung giờ bị bỏ và app báo ngắn "Ngày đã qua, vui lòng chọn lại".
4. **Given** người chơi tìm lại đúng một tổ hợp đã có trong lịch sử, **When** mở lịch sử, **Then** tổ hợp đó chỉ xuất hiện một lần, ở vị trí đầu.
5. **Given** lịch sử đã có 10 mục, **When** người chơi tìm thêm một lần mới, **Then** mục cũ nhất bị bỏ.
6. **Given** người chơi bấm xóa một mục hoặc "Xóa tất cả" (có xác nhận), **When** mở lại app, **Then** các mục đã xóa không còn.
7. **Given** hai người dùng khác nhau, **When** mỗi người mở lịch sử, **Then** chỉ thấy lịch sử của chính mình.
8. **Given** khách chưa đăng nhập, **When** tìm kiếm rồi bấm vào ô tìm kiếm, **Then** không có lịch sử nào được lưu hay hiển thị (có thể hiện gợi ý ngắn "Đăng nhập để lưu lịch sử tìm kiếm").

---

### Edge Cases

- **Mất mạng** khi đang tìm: app không crash; nếu có kết quả đã tải trước đó thì vẫn hiển thị kèm thông báo "Không có kết nối, kết quả có thể chưa cập nhật"; nếu chưa có gì thì hiện trạng thái lỗi kèm nút "Thử lại".
- **Kết quả thay đổi sau khi tìm**: khung giờ đang trống có thể bị người khác đặt trước khi người chơi mở chi tiết sân; spec này chỉ cam kết trạng thái tại lúc tìm, việc kiểm tra cuối cùng do luồng đặt sân (spec 040) đảm bảo.
- **Gõ nhanh liên tục**: danh sách chỉ cập nhật theo nội dung cuối cùng người chơi gõ, không nhấp nháy kết quả của từ khóa cũ; từ khóa dưới 2 ký tự không kích hoạt tìm theo tên.
- **Từ khóa chỉ có khoảng trắng hoặc ký tự đặc biệt**: bị bỏ qua như ô trống.
- **Khoảng giá ngược** (giá tối thiểu lớn hơn tối đa): app không cho áp dụng và báo lỗi ngay tại bộ lọc.
- **Khung giờ không thẳng bước 30 phút**: giờ chọn được chỉ theo bước 30 phút, khớp với độ dài khung giờ đặt sân.
- **Ngày ngoài phạm vi đặt trước**: chỉ chọn được từ hôm nay tới hết số ngày cho phép đặt trước của luồng đặt sân (mặc định 14 ngày).
- **Cơ sở không có ảnh hoặc chưa có đánh giá**: thẻ hiện ảnh mặc định và nhãn "Chưa có đánh giá".
- **Cơ sở chưa có bảng giá**: vẫn xuất hiện khi không lọc theo giá, thẻ hiện "Liên hệ" thay cho giá; không khớp với bộ lọc giá; đứng cuối khi sắp xếp theo giá.
- **Bảng giá không phủ hết khung đã chọn** (một phần khung không có mức giá nào): giá của khung không xác định, cơ sở được xử lý như "chưa có bảng giá" cho khung đó.
- **Khung giờ đổi mức giá giữa chừng** (ví dụ 17:00–19:00 với giờ cao điểm từ 18:00): giá hiển thị là giá trung bình mỗi giờ của cả khung, tính từ từng khung 30 phút.
- **Khách chưa đăng nhập bấm đặt sân từ chi tiết sân**: thuộc spec 030/040; khi quay lại sau khi đăng nhập, từ khóa và bộ lọc ở màn hình tìm kiếm được giữ nguyên.
- **Một tiện ích bị admin xóa khỏi danh mục** trong khi đang nằm trong lịch sử: bộ lọc đó bị bỏ khi khôi phục mục lịch sử.
- **Cơ sở bị ẩn ngay sau khi xuất hiện trong kết quả**: thẻ có thể còn trên màn hình tới lần tải lại kế tiếp; khi mở chi tiết sân thì spec 030 xử lý trạng thái không còn hoạt động.
- **Bộ lọc do spec 022 (AI) điền vào** có giá trị không hợp lệ với spec này (ví dụ ngày đã qua, quận không có trong danh mục): giá trị đó bị bỏ và người chơi thấy các chip còn lại để tự chỉnh.

## Requirements *(mandatory)*

### Functional Requirements

**Tìm theo tên và quận**

- **FR-001**: Người chơi PHẢI tìm được cơ sở theo tên, không phân biệt chữ hoa/thường và có dấu/không dấu; một cơ sở khớp khi mọi từ người chơi gõ đều là phần đầu của một từ trong tên cơ sở. Từ khóa có ít hơn 2 ký tự (sau khi bỏ khoảng trắng) không kích hoạt tìm theo tên.
- **FR-002**: Người chơi PHẢI chọn được một quận/huyện từ danh mục chung của hệ thống để giới hạn kết quả.
- **FR-003**: Từ khóa, quận/huyện, các bộ lọc và cách sắp xếp PHẢI kết hợp được với nhau; kết quả thỏa đồng thời mọi điều kiện đang áp dụng.
- **FR-004**: Kết quả PHẢI chỉ gồm cơ sở đang hoạt động; cơ sở bị chủ sân ẩn hoặc bị admin khóa KHÔNG ĐƯỢC xuất hiện.

**Bộ lọc**

- **FR-005**: Người chơi PHẢI lọc được theo khoảng giá mỗi giờ (giá tối thiểu và/hoặc tối đa, bước 10.000 đ). Giá dùng để so sánh là **giá tham chiếu** của cơ sở:
  - Khi **không** có bộ lọc khung giờ: giá thấp nhất mỗi giờ của cơ sở.
  - Khi **có** bộ lọc khung giờ: giá thuê một sân con cho đúng khung đó theo bảng giá của cơ sở vào ngày đã chọn, quy ra đ/giờ (tổng tiền của khung chia cho số giờ). Khung nằm giữa hai mức giá (ví dụ một phần giờ thường, một phần giờ cao điểm) được tính theo từng khung 30 phút rồi cộng lại.
- **FR-005a**: Một cơ sở khớp bộ lọc giá khi giá tham chiếu nằm trong khoảng đã chọn. Sắp xếp theo giá và giá hiển thị trên thẻ PHẢI dùng cùng giá tham chiếu đó, để giá trên thẻ luôn khớp với điều kiện lọc.
- **FR-006**: Người chơi PHẢI lọc được theo khung giờ còn trống bằng cách chọn một ngày (từ hôm nay tới hết số ngày cho phép đặt trước) và giờ bắt đầu, giờ kết thúc theo bước 30 phút. Một cơ sở khớp khi có **ít nhất một sân con** trống liên tục suốt khung đó và khung đó nằm trong giờ mở cửa. Khung giờ đang bị giữ chỗ đã hết hạn được coi là trống; khung đã đặt, đang giữ còn hạn, hoặc bị chủ sân khóa được coi là bận.
- **FR-007**: Khi chọn ngày hôm nay, app KHÔNG ĐƯỢC cho chọn giờ bắt đầu đã qua; giờ kết thúc PHẢI sau giờ bắt đầu.
- **FR-008**: Người chơi PHẢI lọc được theo một hoặc nhiều tiện ích lấy từ danh mục chung (ví dụ gửi xe, nước uống, phòng tắm, cho thuê vợt). Một cơ sở khớp khi có **đủ tất cả** tiện ích đã chọn.
- **FR-009**: Mỗi bộ lọc đang áp dụng (quận, khoảng giá, khung giờ, từng tiện ích) PHẢI hiện thành một chip có nhãn ngắn; bỏ một chip chỉ bỏ bộ lọc đó; "Xóa bộ lọc" bỏ mọi bộ lọc nhưng giữ từ khóa.
- **FR-010**: Bộ lọc tìm kiếm PHẢI là một tập điều kiện dùng chung (từ khóa, quận/huyện, khoảng giá, ngày và khung giờ, tiện ích, cách sắp xếp) mà spec 022 có thể điền sẵn rồi chạy cùng một cách tìm như khi người chơi tự chọn. Giá trị không hợp lệ (ngày đã qua, quận/tiện ích không có trong danh mục, khoảng giá ngược) PHẢI bị bỏ và không làm hỏng các điều kiện còn lại.

**Sắp xếp và vị trí**

- **FR-011**: Người chơi PHẢI sắp xếp được theo: gần nhất, giá thấp đến cao, đánh giá cao nhất. Mặc định là "Đánh giá cao nhất". Khi sắp xếp theo đánh giá, cùng điểm thì nhiều lượt hơn đứng trước, chưa có đánh giá đứng cuối. Khi sắp xếp theo giá, cơ sở chưa có giá đứng cuối.
- **FR-012**: App PHẢI chỉ xin quyền vị trí khi người chơi chọn "Gần nhất" (kèm giải thích ngắn trước hộp xin quyền); chấp nhận cả vị trí gần đúng.
- **FR-013**: Khi có vị trí, mỗi thẻ PHẢI hiện khoảng cách đường chim bay tới cơ sở (dưới 1 km hiện theo mét, từ 1 km hiện 1 chữ số thập phân). Khi không có vị trí, thẻ KHÔNG hiện khoảng cách, "Gần nhất" không áp dụng được, và mọi chức năng khác vẫn hoạt động.
- **FR-014**: App KHÔNG ĐƯỢC lưu vị trí của người chơi lên máy chủ hay vào lịch sử tìm kiếm; vị trí chỉ dùng trên thiết bị để tính khoảng cách và sắp xếp.

**Danh sách kết quả**

- **FR-015**: Mỗi thẻ kết quả PHẢI hiện ảnh đại diện (hoặc ảnh mặc định), tên cơ sở, quận/huyện, giá (khi không có bộ lọc khung giờ: "từ X đ/giờ"; khi có: "X đ/giờ · HH:mm–HH:mm" của khung đã chọn; "Liên hệ" khi chưa có giá), điểm trung bình kèm số lượt đánh giá (hoặc "Chưa có đánh giá"), và khoảng cách khi có vị trí.
- **FR-016**: Danh sách PHẢI tải theo trang, mỗi trang 20 cơ sở, tải thêm khi cuộn tới cuối.
- **FR-017**: Bấm một thẻ PHẢI mở chi tiết sân (spec 030) của đúng cơ sở đó; quay lại PHẢI giữ nguyên từ khóa, bộ lọc, cách sắp xếp và vị trí cuộn.
- **FR-018**: Danh sách PHẢI cập nhật theo nội dung cuối cùng người chơi gõ hoặc chọn; kết quả của điều kiện cũ KHÔNG ĐƯỢC ghi đè kết quả của điều kiện mới.
- **FR-019**: Màn hình PHẢI có đủ 4 trạng thái: đang tải, có kết quả, rỗng (thông báo "Không tìm thấy sân phù hợp", gợi ý bỏ bớt bộ lọc, nút "Xóa bộ lọc"), lỗi (kèm nút "Thử lại").
- **FR-020**: Khi mất mạng, app KHÔNG ĐƯỢC crash hay treo; nếu đã có kết quả trước đó thì vẫn hiển thị kèm thông báo có thể chưa cập nhật, nếu chưa có thì hiện trạng thái lỗi.

**Lịch sử tìm kiếm**

- **FR-021**: Hệ thống PHẢI lưu một mục lịch sử mỗi khi người chơi thực hiện một lần tìm có từ khóa hoặc ít nhất một bộ lọc (không lưu theo từng ký tự gõ). Mục gồm từ khóa, quận/huyện, khoảng giá, ngày và khung giờ, tiện ích, cách sắp xếp và thời điểm tìm; không gồm vị trí.
- **FR-022**: Lịch sử PHẢI giữ tối đa 10 mục gần nhất, mới nhất ở đầu; tổ hợp trùng với một mục đã có thì đưa mục đó lên đầu thay vì thêm mục mới.
- **FR-023**: Bấm một mục lịch sử PHẢI khôi phục từ khóa và bộ lọc rồi chạy lại tìm kiếm; bộ lọc khung giờ có ngày đã qua và tiện ích/quận không còn trong danh mục PHẢI bị bỏ kèm thông báo ngắn.
- **FR-024**: Người chơi PHẢI xóa được từng mục và xóa toàn bộ lịch sử (có xác nhận).
- **FR-025**: Lịch sử tìm kiếm chỉ người sở hữu đọc, ghi và xóa được; lịch sử bị xóa cùng tài khoản (spec 011).

**Truy cập**

- **FR-026**: Khách chưa đăng nhập PHẢI dùng được toàn bộ tìm kiếm của spec này (tìm theo tên/quận, bộ lọc, sắp xếp, khoảng cách, mở chi tiết sân) mà không bị yêu cầu đăng nhập. Chỉ thông tin công khai của cơ sở hoạt động và danh mục quận/huyện, tiện ích được hiển thị cho khách; trạng thái khung giờ chỉ được dùng để xét trống/bận, không lộ ai đã đặt.
- **FR-027**: Lịch sử tìm kiếm chỉ có với người đã đăng nhập; khách không có lịch sử và không có dữ liệu tìm kiếm nào được lưu lên máy chủ. Thao tác cần tài khoản (đặt sân, yêu thích...) do spec tương ứng yêu cầu đăng nhập.

### Key Entities *(include if feature involves data)*

- **Cơ sở (Venue)** (của spec 111): cụm sân có tên, địa chỉ, quận/huyện, vị trí, giờ mở cửa, danh sách tiện ích, ảnh, bảng giá theo thứ và giờ, giá thấp nhất mỗi giờ, điểm trung bình và số lượt đánh giá, trạng thái (hoạt động / bị ẩn / bị khóa). Spec này chỉ đọc.
- **Sân con và khung giờ** (của spec 111 và 040): mỗi cơ sở có nhiều sân con; mỗi sân con có các khung giờ 30 phút ở trạng thái trống / đang giữ / đã đặt / bị khóa. Dùng để xét bộ lọc giờ trống.
- **Danh mục quận/huyện và tiện ích**: danh sách chung do admin quản lý (spec 120), dùng cho bộ chọn quận, bộ lọc tiện ích và kiểm tra giá trị do spec 022 điền vào.
- **Bộ lọc tìm kiếm (Search criteria)**: từ khóa, quận/huyện, khoảng giá, ngày và khung giờ, danh sách tiện ích, cách sắp xếp. Là giao điểm với spec 022 (điền sẵn) và có thể được spec 021 dùng lại.
- **Mục lịch sử tìm kiếm (Search history entry)**: một bộ lọc tìm kiếm đã chạy kèm thời điểm, thuộc riêng một người dùng, tối đa 10 mục mỗi người.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Trên mạng 4G thông thường, trang đầu kết quả (kể cả khi có bộ lọc khung giờ) hiển thị trong vòng 3 giây sau khi người chơi đổi điều kiện ở ít nhất 95% số lần thử.
- **SC-002**: Người chơi tìm được một sân phù hợp theo yêu cầu "Quận X, ngày mai 19:00–21:00, dưới 100k, có gửi xe" và mở chi tiết sân trong dưới 1 phút kể từ khi vào màn hình tìm kiếm.
- **SC-003**: Trên bộ dữ liệu kiểm thử có đáp án biết trước, 100% kết quả thỏa mọi điều kiện đang áp dụng và không bỏ sót cơ sở nào thỏa điều kiện.
- **SC-004**: 0 trường hợp cơ sở bị ẩn hoặc bị khóa xuất hiện trong kết quả trong các kịch bản kiểm thử.
- **SC-005**: Khi từ chối quyền vị trí hoặc mất mạng, app không crash lần nào và người chơi vẫn tìm được (hoặc thấy rõ cách thử lại) ở 100% kịch bản kiểm thử.
- **SC-006**: 0 trường hợp người dùng đọc hoặc xóa được lịch sử tìm kiếm của người khác trong kiểm thử phân quyền.
- **SC-007**: Ít nhất 4/5 người thử tự dùng được bộ lọc khung giờ và bỏ chip bộ lọc mà không cần hướng dẫn.

## Assumptions

- Danh mục quận/huyện và tiện ích đã có sẵn dữ liệu (nhóm A tạo dữ liệu ban đầu, admin quản lý ở spec 120); bốn tiện ích trong mô tả là ví dụ, bộ lọc hiển thị theo danh mục thực tế.
- Giá của một khung giờ được tính từ bảng giá theo thứ trong tuần và giờ của cơ sở (spec 111). Cách tính khi khung nằm giữa hai mức giá phải thống nhất với spec 040 (điểm giao B ↔ D "Tính giá từ bảng giá"), để giá trên thẻ kết quả bằng giá ở màn hình đặt sân; nếu B ↔ D chốt cách khác thì spec này theo cách đó.
- Khách chưa đăng nhập đọc được thông tin công khai của cơ sở, danh mục và trạng thái trống/bận của khung giờ. Điều này đòi hỏi quyền đọc công khai cho các dữ liệu đó (đặc tả CSDL mục 7 cần ghi rõ) và spec 010/030 cần có lối "đăng nhập để đặt sân"; A báo B, D trước `/speckit-plan`.
- Giá thấp nhất mỗi giờ, điểm trung bình và số lượt đánh giá của cơ sở do hệ thống tính sẵn khi chủ sân đổi bảng giá (spec 111) hoặc khi có đánh giá (spec 070); spec này chỉ đọc. Cách sinh dữ liệu phục vụ tìm theo tên và theo vị trí do A định nghĩa, D ghi khi tạo/sửa cơ sở — điểm giao A ↔ D cần chốt trước `/speckit-plan` (phân công tuần 1–2, mục 2).
- Độ dài khung giờ là 30 phút và phạm vi đặt trước mặc định 14 ngày theo đặc tả CSDL; nếu spec 040 chốt giá trị khác thì spec này dùng theo spec 040.
- Trạng thái khung giờ (trống, giữ chỗ, đã đặt, bị khóa) do spec 040 và 112 quy định; spec này chỉ đọc để lọc.
- Giờ mở cửa áp dụng giống nhau cho mọi ngày trong tuần ở phiên bản này.
- "Gần nhất" tính theo khoảng cách đường chim bay, không theo quãng đường di chuyển; chỉ đường thuộc spec 030.
- Lịch sử 10 mục và trang 20 kết quả là giá trị mặc định theo thông lệ, có thể chỉnh trong `/speckit-clarify`.
- Gợi ý sân gần vị trí hiện tại và chế độ bản đồ thuộc spec 021; tìm bằng câu tự do hoặc giọng nói thuộc spec 022; tìm buổi vãng lai thuộc spec 080.
