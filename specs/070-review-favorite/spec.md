# Feature Specification: Đánh giá sân và sân yêu thích

**Feature Branch**: `feature/07-review-favorite`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Người chơi đánh giá sân sau khi chơi và lưu sân yêu thích. Chỉ đánh giá được khi đơn đặt sân đã hoàn thành (COMPLETED) và thuộc chính người đó; mỗi đơn đánh giá một lần. Chấm 1–5 sao cho 3 tiêu chí: chất lượng sân, vệ sinh, thái độ phục vụ; điểm tổng là trung bình. Viết bình luận, đính kèm tối đa N ảnh (bucket review-photos của Supabase). Người viết sửa/xóa đánh giá của mình. Chủ sân phản hồi một lần cho mỗi đánh giá trên sân của mình. Điểm trung bình và số lượt đánh giá của sân được hệ thống cập nhật (Edge Function on-review-created), người dùng không tự ghi. Đánh giá bị admin ẩn thì không hiển thị. Người dùng đã xóa tài khoản hiện "Người dùng đã xóa". Lưu/bỏ yêu thích từ chi tiết sân, xem danh sách sân yêu thích; sân bị ẩn/khóa vẫn trong danh sách nhưng có ghi chú. Có xử lý mất mạng, upload ảnh lỗi, trạng thái rỗng. Không bao gồm: đánh giá buổi vãng lai (spec 080), báo cáo đánh giá vi phạm và ẩn đánh giá (spec 120), thông báo khuyến mãi cho người theo dõi (spec 100), hiển thị tổng quan đánh giá ở chi tiết sân (spec 030). Dữ liệu: reviews, users/{uid}/favorites, venues.ratingAvg theo docs/design/database/README.md."

**Nhóm tính năng**: [07 — Đánh giá và yêu thích](../../docs/features/07-review-favorite/README.md) · **Phụ trách**: C · **Liên quan**: [spec 011](../011-account-profile/spec.md) (ẩn danh khi xóa tài khoản), spec 030 (chi tiết sân), spec 060 (lịch đặt của tôi), spec 100 (thông báo), spec 120 (kiểm duyệt)

## Clarifications

### Session 2026-10-05

- Q: "Sân yêu thích" có đồng thời là "sân đang theo dõi" (nhận thông báo khuyến mãi, buổi vãng lai mới của sân) không? → A: Là một. Yêu thích = theo dõi, chỉ có một danh sách; muốn tắt thông báo khuyến mãi thì tắt trong cài đặt thông báo (spec 100).
- Q: Người chơi được viết đánh giá trong bao lâu sau khi đơn hoàn thành? → A: 7 ngày kể từ thời điểm đơn hoàn thành; quá hạn không tạo mới được.
- Q: Người chơi được sửa đánh giá đã gửi đến khi nào? → A: Sửa được đến hết hạn 7 ngày kể từ khi đơn hoàn thành; xóa được bất cứ lúc nào.
- Q: Sau khi gửi phản hồi, chủ sân có được sửa hoặc xóa phản hồi không? → A: Được sửa (hiện nhãn "Đã chỉnh sửa"), không được xóa.
- Q: Một đánh giá được đính kèm tối đa bao nhiêu ảnh? → A: 3 ảnh.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Đánh giá sân sau khi chơi (Priority: P1)

Sau khi một đơn đặt sân đã hoàn thành, người chơi mở đơn đó trong "Lịch đặt của tôi" và bấm "Đánh giá". Người chơi chấm từ 1 đến 5 sao cho từng tiêu chí: chất lượng sân, vệ sinh, thái độ phục vụ; có thể viết thêm bình luận rồi gửi. Đánh giá hiện công khai trên sân đó kèm tên và ảnh đại diện của người viết, và điểm trung bình của sân được hệ thống cập nhật.

**Why this priority**: Đây là giá trị cốt lõi của nhóm 07: người chơi khác dựa vào đánh giá thật (chỉ từ người đã chơi) để chọn sân. Các story còn lại đều dựa trên việc đã có đánh giá.

**Independent Test**: Dùng một tài khoản có đơn ở trạng thái hoàn thành, gửi đánh giá 3 tiêu chí kèm bình luận; kiểm tra đánh giá hiện trong danh sách đánh giá của sân và điểm trung bình, số lượt đánh giá của sân thay đổi đúng. Thử với đơn chưa hoàn thành và đơn của người khác thì không đánh giá được.

**Acceptance Scenarios**:

1. **Given** người chơi có đơn đã hoàn thành và chưa đánh giá, **When** mở chi tiết đơn, **Then** thấy nút "Đánh giá".
2. **Given** người chơi ở màn hình đánh giá, **When** chấm đủ 3 tiêu chí (ví dụ 5, 4, 3 sao) và bấm "Gửi", **Then** đánh giá được lưu với điểm tổng 4,0, hiện trong danh sách đánh giá của sân, và nút trên đơn đổi thành "Xem đánh giá của bạn".
3. **Given** người chơi chưa chấm đủ 3 tiêu chí, **When** bấm "Gửi", **Then** app chỉ rõ tiêu chí còn thiếu và không gửi.
4. **Given** đơn ở trạng thái chờ duyệt, đã xác nhận, đã hủy, bị từ chối hoặc hết hạn, **When** người chơi xem đơn, **Then** không có nút "Đánh giá"; nếu cố gửi đánh giá bằng cách khác (không qua app) thì hệ thống từ chối.
5. **Given** đơn đã có đánh giá, **When** người chơi (hoặc một yêu cầu không qua app) cố tạo đánh giá thứ hai cho cùng đơn, **Then** hệ thống từ chối; mỗi đơn chỉ có tối đa một đánh giá.
6. **Given** một đánh giá mới được gửi, **When** người khác mở danh sách đánh giá của sân sau đó, **Then** điểm trung bình và số lượt đánh giá của sân đã tính cả đánh giá mới.
7. **Given** người chơi đang soạn đánh giá, **When** mất mạng và bấm "Gửi", **Then** app báo không có kết nối, giữ nguyên nội dung đã nhập và cho thử lại; thử lại nhiều lần không tạo đánh giá trùng.

---

### User Story 2 - Đính kèm ảnh vào đánh giá (Priority: P2)

Khi viết đánh giá, người chơi chụp hoặc chọn tối đa 3 ảnh từ thư viện để minh họa (mặt sân, phòng thay đồ...). Ảnh hiện dưới đánh giá; người xem bấm vào để xem lớn.

**Why this priority**: Ảnh làm đánh giá đáng tin hơn nhưng đánh giá chữ và sao đã đủ dùng (P1), nên ảnh đứng sau.

**Independent Test**: Gửi một đánh giá kèm 2 ảnh; mở danh sách đánh giá của sân bằng tài khoản khác và thấy đủ 2 ảnh, bấm vào xem được ảnh lớn. Thử chọn ảnh thứ 4 thì bị chặn.

**Acceptance Scenarios**:

1. **Given** người chơi ở màn hình đánh giá, **When** chọn 2 ảnh từ thư viện và gửi, **Then** đánh giá hiển thị đủ 2 ảnh theo đúng thứ tự đã chọn.
2. **Given** người chơi đã chọn 3 ảnh, **When** cố thêm ảnh nữa, **Then** app báo đã đạt tối đa 3 ảnh.
3. **Given** người chơi chọn một tệp không phải ảnh hợp lệ hoặc ảnh quá lớn dù đã nén, **When** thêm vào đánh giá, **Then** app báo lỗi cho đúng ảnh đó và không thêm.
4. **Given** đang tải ảnh lên thì một ảnh lỗi (mất mạng giữa chừng), **When** quá trình gửi kết thúc, **Then** app đánh dấu ảnh lỗi, giữ nguyên nội dung đánh giá và cho người chơi chọn "Thử lại" hoặc bỏ ảnh đó rồi gửi; đánh giá không được hiển thị với ảnh hỏng.
5. **Given** người chơi từ chối quyền camera hoặc quyền truy cập ảnh, **When** bấm thêm ảnh, **Then** app giải thích vì sao cần quyền, vẫn cho dùng cách còn lại, và vẫn gửi được đánh giá không có ảnh.

---

### User Story 3 - Lưu và xem sân yêu thích (Priority: P2)

Người chơi bấm biểu tượng trái tim ở chi tiết sân để lưu sân vào danh sách yêu thích, bấm lần nữa để bỏ. Trong trang "Tài khoản", mục "Sân yêu thích" liệt kê các sân đã lưu (ảnh, tên, địa chỉ, điểm đánh giá); bấm vào một sân để mở chi tiết sân và đặt lại nhanh.

**Why this priority**: Giúp người chơi quay lại sân quen nhanh hơn, độc lập với đánh giá; nhưng không chặn luồng đặt sân nên đứng sau P1.

**Independent Test**: Lưu 2 sân vào yêu thích, mở mục "Sân yêu thích" thấy đủ 2 sân, bỏ yêu thích 1 sân và thấy danh sách còn 1; thoát hẳn app mở lại vẫn đúng.

**Acceptance Scenarios**:

1. **Given** người chơi xem chi tiết một sân chưa yêu thích, **When** bấm trái tim, **Then** trái tim đổi sang trạng thái đã lưu ngay lập tức và có thông báo ngắn "Đã thêm vào yêu thích".
2. **Given** sân đã nằm trong yêu thích, **When** bấm trái tim lần nữa, **Then** sân bị bỏ khỏi danh sách và trái tim trở lại trạng thái chưa lưu.
3. **Given** người chơi đã lưu nhiều sân, **When** mở "Sân yêu thích", **Then** thấy danh sách sân, sân lưu gần nhất ở đầu, mỗi sân có ảnh, tên, địa chỉ và điểm đánh giá trung bình.
4. **Given** một sân trong danh sách đã bị chủ sân ẩn hoặc bị admin khóa, **When** người chơi mở "Sân yêu thích", **Then** sân đó vẫn còn trong danh sách nhưng có ghi chú "Sân tạm ngừng hoạt động" và không đặt được; người chơi vẫn bỏ yêu thích được.
5. **Given** người chơi chưa lưu sân nào, **When** mở "Sân yêu thích", **Then** thấy màn hình trống có lời gợi ý và nút "Tìm sân".
6. **Given** người chơi đang mất mạng, **When** bấm trái tim, **Then** trạng thái trên giao diện đổi ngay, thay đổi được đồng bộ khi có mạng lại; nếu đồng bộ thất bại thì trái tim trở về trạng thái cũ và có thông báo.
7. **Given** người chơi lưu cùng một sân hai lần (bấm nhanh hoặc trên hai thiết bị), **When** mở "Sân yêu thích", **Then** sân chỉ xuất hiện một lần.

---

### User Story 4 - Chủ sân phản hồi đánh giá (Priority: P2)

Chủ sân đã được duyệt mở mục "Đánh giá của khách" trong chế độ quản lý sân, thấy các đánh giá trên các cơ sở của mình (đánh giá chưa phản hồi lên trước), và viết một phản hồi cho từng đánh giá. Phản hồi hiện công khai ngay dưới đánh giá.

**Why this priority**: Phản hồi giúp chủ sân giải thích và giữ uy tín, có trong phạm vi proposal; nhưng cần có đánh giá trước (P1).

**Independent Test**: Với một đánh giá trên sân của chủ sân A, đăng nhập chủ sân A viết phản hồi và kiểm tra phản hồi hiện dưới đánh giá khi xem bằng tài khoản người chơi. Đăng nhập chủ sân B (không sở hữu sân đó) thì không phản hồi được.

**Acceptance Scenarios**:

1. **Given** chủ sân ở chế độ quản lý sân, **When** mở "Đánh giá của khách", **Then** thấy đánh giá của mọi cơ sở mình sở hữu, lọc được theo cơ sở và theo "Chưa phản hồi".
2. **Given** một đánh giá chưa có phản hồi, **When** chủ sân viết nội dung và bấm "Gửi phản hồi", **Then** phản hồi hiện dưới đánh giá kèm nhãn "Phản hồi của chủ sân" và thời gian.
3. **Given** đánh giá đã có phản hồi, **When** chủ sân mở lại đánh giá đó, **Then** không có nút tạo phản hồi mới hay nút xóa, chỉ có "Sửa phản hồi"; phản hồi đã sửa hiện nhãn "Đã chỉnh sửa"; mỗi đánh giá có tối đa một phản hồi.
4. **Given** một chủ sân không sở hữu cơ sở được đánh giá (hoặc người chơi bình thường), **When** cố gửi phản hồi bằng bất kỳ cách nào, **Then** hệ thống từ chối.
5. **Given** chủ sân gửi phản hồi rỗng hoặc vượt giới hạn độ dài, **When** bấm "Gửi phản hồi", **Then** app báo lỗi và không gửi.

---

### User Story 5 - Sửa hoặc xóa đánh giá của mình (Priority: P3)

Người chơi mở đánh giá mình đã viết (từ đơn đặt sân hoặc danh sách đánh giá của sân) để sửa số sao, bình luận, ảnh (trong hạn 7 ngày kể từ khi đơn hoàn thành), hoặc xóa hẳn đánh giá (bất cứ lúc nào).

**Why this priority**: Cần cho tính công bằng (người chơi sửa khi chấm nhầm, hoặc khi chủ sân đã khắc phục) nhưng ít dùng hơn việc viết đánh giá.

**Independent Test**: Sửa một đánh giá từ 2 sao lên 4 sao và kiểm tra điểm trung bình của sân được tính lại; xóa đánh giá đó và kiểm tra nó biến mất, số lượt đánh giá giảm 1.

**Acceptance Scenarios**:

1. **Given** người chơi đã đánh giá một đơn, **When** sửa số sao hoặc bình luận và lưu, **Then** đánh giá hiện nội dung mới kèm nhãn "Đã chỉnh sửa", và điểm trung bình của sân được tính lại.
2. **Given** người chơi bấm "Xóa đánh giá", **When** xác nhận, **Then** đánh giá và ảnh của nó bị xóa, điểm trung bình và số lượt đánh giá của sân được tính lại.
3. **Given** đánh giá đã có phản hồi của chủ sân, **When** người chơi sửa đánh giá, **Then** phản hồi vẫn được giữ nguyên dưới đánh giá.
4. **Given** một người khác (không phải người viết), **When** cố sửa hoặc xóa đánh giá, **Then** hệ thống từ chối.
5. **Given** đã quá 7 ngày kể từ khi đơn hoàn thành, **When** người viết mở đánh giá của mình, **Then** không còn nút "Sửa", chỉ còn "Xóa đánh giá"; nếu cố sửa bằng cách khác thì hệ thống từ chối.

---

### User Story 6 - Xem tất cả đánh giá của một sân (Priority: P3)

Từ phần tổng quan đánh giá ở chi tiết sân (spec 030), người chơi bấm "Xem tất cả" để mở danh sách đầy đủ đánh giá của sân: điểm trung bình từng tiêu chí, số lượt đánh giá, và từng đánh giá (tên, ảnh đại diện, số sao, bình luận, ảnh, ngày, phản hồi của chủ sân). Người chơi lọc theo số sao và theo "Có ảnh".

**Why this priority**: Spec 030 đã hiện phần tổng quan đủ để ra quyết định; danh sách đầy đủ là phần mở rộng.

**Independent Test**: Với một sân có ít nhất 25 đánh giá (dữ liệu demo), mở "Xem tất cả", cuộn tải thêm, lọc "5 sao" và "Có ảnh" và thấy kết quả đúng.

**Acceptance Scenarios**:

1. **Given** sân có nhiều đánh giá, **When** mở "Xem tất cả", **Then** thấy đánh giá mới nhất ở đầu, danh sách tải thêm khi cuộn xuống cuối.
2. **Given** người chơi chọn lọc "4 sao", **When** danh sách cập nhật, **Then** chỉ còn các đánh giá có điểm tổng làm tròn bằng 4.
3. **Given** một đánh giá đã bị admin ẩn, **When** bất kỳ ai (trừ người viết) xem danh sách, **Then** đánh giá đó không xuất hiện và không được tính vào điểm trung bình.
4. **Given** người viết một đánh giá đã xóa tài khoản, **When** người khác xem đánh giá đó, **Then** thấy tên "Người dùng đã xóa" và ảnh mặc định.
5. **Given** sân chưa có đánh giá nào, **When** mở danh sách, **Then** thấy màn hình trống "Sân chưa có đánh giá".

---

### Edge Cases

- **Đơn nhiều sân con/khung giờ:** một đơn gồm nhiều sân con hoặc nhiều khung giờ của cùng một cơ sở chỉ được đánh giá một lần, đánh giá gắn với cơ sở.
- **Đặt cố định theo tuần (spec 041):** mỗi đơn trong chuỗi là một đơn riêng, mỗi đơn hoàn thành được đánh giá một lần.
- **Hết hạn đánh giá:** quá 7 ngày kể từ khi đơn hoàn thành thì không còn nút "Đánh giá" và hệ thống từ chối tạo mới; đánh giá đã có không sửa được nữa nhưng vẫn xóa được.
- **Xóa rồi đánh giá lại:** sau khi xóa, người chơi được đánh giá lại đơn đó nếu vẫn còn trong hạn 7 ngày.
- **Chủ sân đặt sân của chính mình:** chủ sân không được đánh giá cơ sở mình sở hữu.
- **Sân bị ẩn/khóa:** người chơi vẫn đánh giá được đơn đã hoàn thành ở sân đã bị ẩn; đánh giá không hiển thị công khai khi sân đang bị khóa và hiện lại khi sân mở lại.
- **Đánh giá bị admin ẩn:** người viết vẫn thấy đánh giá của mình kèm nhãn "Đánh giá đã bị ẩn"; không ai khác thấy; không tính vào điểm trung bình.
- **Người viết xóa tài khoản:** đánh giá vẫn còn, ẩn danh thành "Người dùng đã xóa" (theo spec 011), vẫn được tính vào điểm trung bình.
- **Hai thao tác cùng lúc:** nhiều người gửi đánh giá cho cùng một sân cùng thời điểm thì điểm trung bình và số lượt đánh giá cuối cùng vẫn đúng (không mất lượt nào).
- **Bấm "Gửi" nhiều lần:** chỉ tạo một đánh giá.
- **Rời màn hình khi đang soạn:** app hỏi "Bỏ đánh giá đang viết?" trước khi thoát.
- **Bình luận chứa số điện thoại hoặc liên kết:** vẫn cho gửi; việc xử lý nội dung vi phạm thuộc báo cáo/kiểm duyệt (spec 120).
- **Sân trong danh sách yêu thích bị xóa hẳn:** sân vẫn hiện từ thông tin đã lưu, ghi chú "Sân không còn tồn tại", chỉ còn thao tác bỏ yêu thích.

## Requirements *(mandatory)*

### Functional Requirements

**Đánh giá**

- **FR-001**: Hệ thống PHẢI chỉ cho người chơi tạo đánh giá cho một đơn đặt sân khi đơn đó thuộc chính người chơi, ở trạng thái hoàn thành và chưa quá 7 ngày kể từ khi hoàn thành. Điều kiện này PHẢI được kiểm tra ở phía server, không chỉ ẩn nút trên app.
- **FR-002**: Mỗi đơn đặt sân PHẢI có tối đa một đánh giá; tạo trùng (bấm nhiều lần, thử lại khi mất mạng, gửi từ hai thiết bị) KHÔNG ĐƯỢC tạo đánh giá thứ hai.
- **FR-003**: Đánh giá PHẢI có điểm 1–5 sao (số nguyên) cho đủ 3 tiêu chí: chất lượng sân, vệ sinh, thái độ phục vụ. Điểm tổng PHẢI bằng trung bình cộng 3 tiêu chí, làm tròn 1 chữ số thập phân, và do hệ thống tính.
- **FR-004**: Bình luận là tùy chọn, tối đa 1000 ký tự; app PHẢI hiện bộ đếm ký tự.
- **FR-005**: Người chơi PHẢI đính kèm được 0–3 ảnh (chụp hoặc chọn từ thư viện). Mỗi ảnh PHẢI được nén trước khi tải lên và sau khi nén không quá 2 MB, định dạng JPEG, PNG hoặc WebP.
- **FR-006**: Đánh giá chỉ được hiển thị khi mọi ảnh đính kèm đã tải lên thành công; nếu ảnh lỗi, app PHẢI giữ nội dung đã nhập và cho thử lại từng ảnh hoặc bỏ ảnh lỗi.
- **FR-007**: Người viết PHẢI sửa được số sao, bình luận và ảnh của đánh giá mình cho đến hết hạn 7 ngày kể từ khi đơn hoàn thành; quá hạn thì hệ thống từ chối sửa (kiểm tra ở phía server). Đánh giá đã sửa PHẢI hiện nhãn "Đã chỉnh sửa".
- **FR-008**: Người viết PHẢI xóa được đánh giá của mình bất cứ lúc nào (kể cả sau hạn 7 ngày) sau khi xác nhận; xóa đánh giá thì ảnh của đánh giá cũng bị xóa.
- **FR-009**: Chỉ người viết được sửa/xóa đánh giá; mọi người dùng khác (kể cả chủ sân) KHÔNG ĐƯỢC sửa nội dung hay số sao của đánh giá.

**Điểm trung bình của sân**

- **FR-010**: Điểm trung bình và số lượt đánh giá của sân PHẢI do hệ thống tự cập nhật mỗi khi có đánh giá được tạo, sửa, xóa, bị ẩn hoặc hiện lại; người dùng (kể cả chủ sân) KHÔNG ĐƯỢC tự ghi hai giá trị này.
- **FR-011**: Điểm trung bình của sân PHẢI chỉ tính các đánh giá đang hiển thị (không tính đánh giá bị admin ẩn) và PHẢI đúng khi nhiều đánh giá được gửi cùng lúc.
- **FR-012**: Hệ thống PHẢI tính được điểm trung bình riêng cho từng tiêu chí của sân để hiển thị trong danh sách đánh giá.

**Hiển thị đánh giá**

- **FR-013**: Danh sách đánh giá của sân PHẢI sắp xếp mới nhất trước, tải theo trang (mỗi lần 20 đánh giá), lọc được theo số sao (1–5, theo điểm tổng làm tròn) và theo "Có ảnh".
- **FR-014**: Mỗi đánh giá PHẢI hiển thị tên và ảnh đại diện người viết, điểm tổng và điểm từng tiêu chí, bình luận, ảnh, ngày viết, nhãn "Đã chỉnh sửa" nếu có, và phản hồi của chủ sân nếu có.
- **FR-015**: Đánh giá bị admin ẩn KHÔNG ĐƯỢC hiển thị với ai ngoài người viết (người viết thấy nhãn "Đánh giá đã bị ẩn") và admin.
- **FR-016**: Đánh giá của người đã xóa tài khoản PHẢI hiện tên "Người dùng đã xóa" và ảnh mặc định, không còn liên kết tới thông tin cá nhân.
- **FR-017**: Mỗi màn hình có dữ liệu (danh sách đánh giá, danh sách yêu thích, danh sách đánh giá của chủ sân) PHẢI có đủ 4 trạng thái: đang tải, có dữ liệu, rỗng, lỗi kèm nút thử lại.

**Phản hồi của chủ sân**

- **FR-018**: Chỉ chủ sân đã được duyệt và sở hữu cơ sở được đánh giá mới được tạo và sửa phản hồi; KHÔNG AI (kể cả chủ sân) được xóa phản hồi đã gửi. Kiểm tra này PHẢI ở phía server.
- **FR-019**: Mỗi đánh giá có tối đa một phản hồi, nội dung 1–500 ký tự; phản hồi PHẢI hiển thị công khai dưới đánh giá kèm thời gian phản hồi và nhãn "Đã chỉnh sửa" nếu phản hồi đã được sửa.
- **FR-020**: Chủ sân PHẢI có màn hình "Đánh giá của khách" liệt kê đánh giá trên mọi cơ sở của mình, lọc theo cơ sở và theo "Chưa phản hồi".

**Sân yêu thích**

- **FR-021**: Người chơi đã đăng nhập PHẢI lưu và bỏ được một sân khỏi yêu thích bằng một lần bấm ở chi tiết sân; giao diện PHẢI đổi trạng thái ngay, kể cả khi đang mất mạng, và trở về trạng thái cũ kèm thông báo nếu đồng bộ thất bại.
- **FR-022**: Mỗi sân chỉ xuất hiện tối đa một lần trong danh sách yêu thích của một người.
- **FR-023**: Danh sách "Sân yêu thích" PHẢI hiển thị ảnh, tên, địa chỉ và điểm đánh giá của từng sân, sân lưu gần nhất ở đầu, và mở được chi tiết sân khi bấm.
- **FR-024**: Sân bị ẩn hoặc bị khóa PHẢI vẫn nằm trong danh sách yêu thích với ghi chú "Sân tạm ngừng hoạt động" và không đặt được; sân không còn tồn tại hiện ghi chú "Sân không còn tồn tại". Người chơi vẫn bỏ yêu thích được trong mọi trường hợp.
- **FR-025**: Danh sách yêu thích chỉ người sở hữu đọc và ghi được.
- **FR-025a**: Sân yêu thích đồng thời là sân người chơi đang theo dõi: danh sách yêu thích là nguồn duy nhất để nhóm thông báo (spec 100) xác định ai nhận thông báo khuyến mãi và buổi vãng lai mới của sân. Không có nút "Theo dõi" riêng; việc bật/tắt loại thông báo này thuộc cài đặt thông báo của spec 100.

**Liên kết với tính năng khác**

- **FR-026**: Hệ thống PHẢI phát sinh các sự kiện để nhóm thông báo (spec 100) gửi: nhắc người chơi đánh giá sau khi đơn hoàn thành, báo chủ sân có đánh giá mới, báo người viết khi chủ sân phản hồi. Nội dung và cách gửi thuộc spec 100.
- **FR-027**: Mỗi đánh giá PHẢI có điểm vào để người dùng khác báo cáo vi phạm; luồng báo cáo và ẩn đánh giá thuộc spec 120.

### Key Entities

- **Đánh giá (Review)**: Nhận xét của một người chơi về một cơ sở sau một đơn đã hoàn thành. Gắn với đúng một đơn đặt sân và một cơ sở. Gồm: người viết (kèm bản sao tên, ảnh tại thời điểm viết), điểm 3 tiêu chí, điểm tổng, bình luận, danh sách ảnh, phản hồi của chủ sân (nội dung, thời gian), trạng thái hiển thị (hiển thị / bị ẩn), thời gian tạo và sửa. Đánh giá buổi vãng lai dùng chung loại dữ liệu này nhưng thuộc spec 080.
- **Ảnh đánh giá**: Ảnh người chơi đính kèm, thuộc về một đánh giá, ai cũng xem được; bị xóa cùng đánh giá.
- **Phản hồi của chủ sân**: Một phản hồi duy nhất của chủ cơ sở cho một đánh giá; sửa được, không xóa được.
- **Sân yêu thích (Favorite)**: Một sân người chơi đã lưu (đồng thời là sân đang theo dõi), thuộc riêng người chơi đó. Gồm: sân, bản sao thông tin hiển thị (tên, ảnh, địa chỉ), thời gian lưu.
- **Cơ sở (Venue)** (của spec 111): có điểm trung bình và số lượt đánh giá do hệ thống tính từ các đánh giá đang hiển thị.
- **Đơn đặt sân (Booking)** (của spec 040/060): trạng thái "hoàn thành" và thời điểm hoàn thành là điều kiện để đánh giá.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người chơi viết và gửi xong một đánh giá (3 tiêu chí, bình luận ngắn, không ảnh) trong dưới 1 phút kể từ khi mở chi tiết đơn.
- **SC-002**: 100% lần thử tạo đánh giá cho đơn chưa hoàn thành, đơn của người khác, đơn quá hạn 7 ngày hoặc đơn đã có đánh giá đều bị từ chối, kể cả khi gửi không qua app.
- **SC-003**: 100% lần thử tự ghi điểm trung bình của sân, sửa đánh giá của người khác, hoặc phản hồi đánh giá trên sân không thuộc mình đều bị từ chối.
- **SC-004**: Sau khi gửi, sửa hoặc xóa đánh giá, điểm trung bình và số lượt đánh giá của sân hiển thị giá trị mới trong vòng 10 giây.
- **SC-005**: Khi 10 người gửi đánh giá cho cùng một sân cùng lúc, số lượt đánh giá tăng đúng 10 và điểm trung bình đúng bằng trung bình của các đánh giá đang hiển thị.
- **SC-006**: Bấm trái tim yêu thích thấy trạng thái đổi ngay (dưới 0,5 giây), kể cả khi mất mạng.
- **SC-007**: Danh sách đánh giá và danh sách sân yêu thích tải xong trang đầu trong dưới 3 giây trên mạng 4G.
- **SC-008**: Ít nhất 4/5 người thử (thành viên nhóm khác) tự viết được đánh giá kèm ảnh và tự tìm được danh sách sân yêu thích mà không cần hướng dẫn.

## Assumptions

- **Số ảnh tối đa:** mô tả gốc để "N", đã chốt **3 ảnh** mỗi đánh giá ở Clarifications để tiết kiệm dung lượng gói miễn phí (đặc tả CSDL mục 8).
- **Hạn đánh giá 7 ngày** sau khi đơn hoàn thành đã chốt ở Clarifications. Giới hạn bình luận 1000 ký tự, phản hồi 500 ký tự, trang 20 đánh giá: giá trị mặc định theo thông lệ.
- Đơn được chuyển sang "hoàn thành" ở phía server khi qua giờ chơi, theo máy trạng thái đơn của B trong đặc tả CSDL mục 4.5; spec này chỉ đọc trạng thái đó. Cần B xác nhận lưu lại thời điểm hoàn thành để tính hạn 7 ngày.
- "Sân yêu thích" đồng thời là "sân đang theo dõi" (đã chốt ở Clarifications, FR-025a); spec này chỉ lưu danh sách, việc gửi thông báo thuộc spec 100. D cần xác nhận lại phía spec 100 (câu #4 mục 9 đặc tả CSDL).
- Phản hồi của chủ sân sửa được không giới hạn số lần, không xóa được (đã chốt ở Clarifications).
- Chủ sân xem đánh giá ở chế độ quản lý sân (spec 011 chuyển chế độ); màn hình "Đánh giá của khách" thuộc spec này, menu chứa nó thuộc spec 111/112.
- Nút trái tim nằm trên màn hình chi tiết sân của spec 030 (A); spec này định nghĩa hành vi của nút. Phần tổng quan đánh giá ở chi tiết sân (spec 030) mở danh sách đầy đủ của User Story 6.
- Nút "Đánh giá" nằm trên chi tiết đơn của spec 060 (B); spec này định nghĩa màn hình đánh giá.
- Danh sách loại thông báo ở FR-026 phải gửi cho D trước 14/10 theo phân công tuần 1–2.
- Người dùng đã đăng nhập (spec 010); khách chưa đăng nhập không dùng được tính năng này.
