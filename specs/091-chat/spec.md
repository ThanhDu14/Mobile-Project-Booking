# Feature Specification: Chat nhóm và chat với chủ sân

**Feature Branch**: `feature/09-community-chat`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người dùng trò chuyện theo thời gian thực. Mỗi nhóm/CLB (spec 090) có một cuộc trò chuyện chung, thành viên trong đó đúng bằng danh sách thành viên nhóm. Người chơi nhắn riêng cho chủ sân từ chi tiết sân để hỏi lịch, giá; chủ sân trả lời trong chế độ quản lý sân. Gửi tin nhắn chữ và ảnh (bucket chat-media của Supabase, chỉ người trong cuộc trò chuyện xem được). Danh sách cuộc trò chuyện sắp xếp theo tin nhắn mới nhất, có số tin chưa đọc. Báo cáo tin nhắn vi phạm (spec 120). Có xử lý mất mạng (tin chờ gửi, gửi lại), gửi lỗi, trạng thái rỗng. Không bao gồm: tạo/quản lý nhóm và thành viên (spec 090), xử lý báo cáo của admin (spec 120), gửi thông báo đẩy (spec 100). Dữ liệu: chats/{chatId} (+ messages), reports theo docs/design/database/README.md mục 4.11, 4.12, 8."

**Nhóm tính năng**: [09 — Cộng đồng và chat](../../docs/features/09-community-chat/README.md) · **Phụ trách**: C · **Liên quan**: [spec 090](../090-community-group/spec.md) (danh sách thành viên nhóm), [spec 011](../011-account-profile/spec.md) (chế độ chủ sân, xóa tài khoản), spec 030 (chi tiết sân – nút nhắn tin), spec 100 (thông báo tin nhắn mới), spec 120 (xử lý báo cáo)

## Clarifications

### Session 2026-10-06

- Q: Người chơi có được nhắn tin riêng cho người chơi khác không? → A: Không ở phiên bản này; chat riêng chỉ giữa người chơi và chủ sân, người chơi trò chuyện với nhau qua chat nhóm.
- Q: Thành viên mới vào nhóm có đọc được tin nhắn cũ của chat nhóm không? → A: Có, đọc được toàn bộ lịch sử; mất quyền đọc ngay khi rời hoặc bị mời ra khỏi nhóm.
- Q: Người gửi có được thu hồi hoặc sửa tin nhắn đã gửi không? → A: Thu hồi được trong 10 phút sau khi gửi (hiện "Tin nhắn đã được thu hồi"); không có sửa tin nhắn.
- Q: Trong chat riêng, người dùng có chặn được bên kia không? → A: Có, cả hai bên chặn và bỏ chặn được; bên bị chặn không gửi được tin vào cuộc trò chuyện đó.
- Q: Khi chủ sân có nhiều cơ sở, mỗi cơ sở là một cuộc trò chuyện riêng hay một cuộc chung? → A: Một cuộc chung cho mỗi cặp người chơi – chủ sân, hiển thị cơ sở được hỏi gần nhất.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhắn tin riêng với chủ sân (Priority: P1)

Người chơi đang xem chi tiết một sân muốn hỏi lịch trống hoặc giá thuê theo tháng, bấm "Nhắn tin cho chủ sân" và gửi câu hỏi. Chủ sân thấy cuộc trò chuyện trong chế độ quản lý sân, biết khách đang hỏi về cơ sở nào và trả lời. Hai bên thấy tin nhắn của nhau gần như ngay lập tức.

**Why this priority**: Proposal nêu rõ nhu cầu "chat riêng với chủ sân để hỏi về lịch, giá"; đây là kênh duy nhất trong app để người chơi liên hệ chủ sân trước khi đặt.

**Independent Test**: Tài khoản người chơi A mở chi tiết sân của chủ sân B, gửi "Tối thứ 7 còn sân không?"; B (chế độ quản lý sân) thấy cuộc trò chuyện mới kèm tên cơ sở, trả lời; A thấy câu trả lời trong vòng vài giây mà không cần tải lại.

**Acceptance Scenarios**:

1. **Given** người chơi xem chi tiết một sân đang hoạt động, **When** bấm "Nhắn tin cho chủ sân", **Then** mở cuộc trò chuyện với chủ sân đó, đầu màn hình hiện tên và ảnh cơ sở đang hỏi.
2. **Given** người chơi đã từng nhắn với chủ sân này (kể cả về cơ sở khác của cùng chủ), **When** bấm "Nhắn tin cho chủ sân", **Then** mở lại đúng cuộc trò chuyện cũ (không tạo cuộc mới), cơ sở đang hỏi được cập nhật theo sân vừa xem.
3. **Given** cuộc trò chuyện đang mở trên cả hai máy, **When** một bên gửi tin, **Then** bên kia thấy tin mới trong vòng 3 giây mà không cần thao tác.
4. **Given** chủ sân ở chế độ quản lý sân, **When** mở mục "Tin nhắn khách hàng", **Then** thấy các cuộc trò chuyện với người chơi, mỗi cuộc kèm tên cơ sở được hỏi gần nhất; ở chế độ người chơi thì chủ sân thấy các cuộc trò chuyện của mình với tư cách người chơi.
5. **Given** chủ sân xem chi tiết một cơ sở của chính mình, **When** màn hình hiện, **Then** không có nút "Nhắn tin cho chủ sân".
6. **Given** người chơi khác (không ở trong cuộc trò chuyện), **When** cố đọc hoặc gửi tin vào cuộc trò chuyện đó bằng bất kỳ cách nào, **Then** hệ thống từ chối.

---

### User Story 2 - Chat nhóm của CLB (Priority: P1)

Mỗi nhóm/CLB có một cuộc trò chuyện chung. Thành viên mở nhóm, bấm "Chat nhóm" để nhắn với mọi người; ai vào nhóm thì tự có trong cuộc trò chuyện, ai rời nhóm hoặc bị mời ra thì không còn đọc và gửi được.

**Why this priority**: Chat nhóm là cách CLB hẹn lịch đánh hằng ngày và là lý do người chơi quay lại app; cùng P1 với chat riêng.

**Independent Test**: Nhóm có A, B, C. A gửi tin trong chat nhóm, B và C thấy ngay. D tham gia nhóm và thấy được cuộc trò chuyện. C bị mời ra khỏi nhóm thì không mở được chat nhóm nữa và không gửi được.

**Acceptance Scenarios**:

1. **Given** một nhóm vừa được tạo (spec 090), **When** chủ nhóm mở trang nhóm, **Then** có ngay mục "Chat nhóm" với chủ nhóm là thành viên duy nhất.
2. **Given** người chơi vừa tham gia nhóm (hoặc được duyệt vào nhóm riêng tư), **When** mở "Chat nhóm", **Then** đọc được toàn bộ tin nhắn trước đó và gửi được tin.
3. **Given** thành viên rời nhóm hoặc bị mời ra, **When** mở lại cuộc trò chuyện của nhóm, **Then** cuộc trò chuyện biến mất khỏi danh sách và hệ thống từ chối mọi thao tác đọc/gửi.
4. **Given** nhiều thành viên gửi tin cùng lúc, **When** các tin được lưu, **Then** mọi thành viên thấy cùng một thứ tự tin nhắn theo thời gian server.
5. **Given** nhóm bị xóa (spec 090), **When** thành viên mở danh sách trò chuyện, **Then** chat nhóm không còn; nhóm bị admin khóa thì chat nhóm chỉ đọc được, không gửi được.
6. **Given** mỗi tin trong chat nhóm, **When** hiển thị, **Then** có tên và ảnh đại diện người gửi; tin của mình hiện ở phía phải.

---

### User Story 3 - Danh sách trò chuyện và tin chưa đọc (Priority: P2)

Người dùng mở mục "Tin nhắn" để thấy mọi cuộc trò chuyện (chat nhóm và chat với chủ sân), sắp xếp theo tin nhắn mới nhất, mỗi cuộc có tin cuối, thời gian và số tin chưa đọc. Biểu tượng "Tin nhắn" trên thanh điều hướng có huy hiệu tổng số cuộc trò chuyện chưa đọc.

**Why this priority**: Cần để không bỏ lỡ tin, nhưng chat vẫn dùng được qua lối vào từ chi tiết sân và trang nhóm.

**Independent Test**: A có 3 cuộc trò chuyện; B gửi 2 tin vào một cuộc; danh sách của A đưa cuộc đó lên đầu với số "2"; A mở cuộc đó thì số biến mất.

**Acceptance Scenarios**:

1. **Given** người dùng có nhiều cuộc trò chuyện, **When** mở "Tin nhắn", **Then** thấy danh sách sắp xếp theo thời gian tin cuối mới nhất, mỗi cuộc có tên (tên nhóm hoặc tên người/cơ sở), ảnh, tin cuối (ảnh thì hiện "[Hình ảnh]"), thời gian và số tin chưa đọc.
2. **Given** có tin mới trong một cuộc trò chuyện, **When** danh sách đang mở, **Then** cuộc đó nhảy lên đầu và số chưa đọc tăng mà không cần tải lại.
3. **Given** người dùng mở một cuộc trò chuyện, **When** xem tới tin mới nhất, **Then** số chưa đọc của cuộc đó về 0 và huy hiệu tổng giảm tương ứng.
4. **Given** người dùng chưa có cuộc trò chuyện nào, **When** mở "Tin nhắn", **Then** thấy màn hình trống gợi ý "Tham gia một nhóm" hoặc "Tìm sân để hỏi chủ sân".
5. **Given** người dùng muốn tắt thông báo của một cuộc trò chuyện, **When** bấm "Tắt thông báo", **Then** không nhận thông báo đẩy của cuộc đó nữa nhưng vẫn thấy số chưa đọc.

---

### User Story 4 - Gửi ảnh trong chat (Priority: P2)

Người dùng chụp hoặc chọn một ảnh từ thư viện để gửi (ví dụ ảnh chụp lịch, hóa đơn chuyển khoản, mặt sân). Ảnh hiện trong cuộc trò chuyện, bấm vào để xem lớn; chỉ người trong cuộc trò chuyện xem được.

**Why this priority**: Hữu ích nhưng tin nhắn chữ đã đủ cho nhu cầu cốt lõi.

**Independent Test**: A gửi một ảnh vào chat với chủ sân B; B thấy ảnh và xem lớn được; một tài khoản C không thuộc cuộc trò chuyện không mở được ảnh kể cả khi có đường dẫn.

**Acceptance Scenarios**:

1. **Given** người dùng ở cuộc trò chuyện, **When** chọn một ảnh và gửi, **Then** ảnh hiện ngay ở trạng thái "Đang gửi" rồi chuyển sang đã gửi; người kia thấy ảnh.
2. **Given** ảnh tải lên lỗi hoặc mất mạng giữa chừng, **When** quá trình gửi kết thúc, **Then** tin ảnh hiện "Gửi lỗi" kèm nút "Gửi lại" và nút xóa; không ai khác thấy ảnh hỏng.
3. **Given** người dùng chọn tệp không phải ảnh hợp lệ hoặc ảnh quá lớn dù đã nén, **When** gửi, **Then** app báo lỗi và không gửi.
4. **Given** người không thuộc cuộc trò chuyện, **When** cố mở ảnh của cuộc trò chuyện đó, **Then** không xem được.
5. **Given** người dùng từ chối quyền camera hoặc truy cập ảnh, **When** bấm gửi ảnh, **Then** app giải thích vì sao cần quyền, vẫn cho dùng cách còn lại và vẫn nhắn chữ bình thường.

---

### User Story 5 - Thu hồi, báo cáo tin nhắn và chặn người dùng (Priority: P3)

Người gửi thu hồi được tin nhắn vừa gửi nhầm. Người nhận thấy tin vi phạm thì báo cáo cho admin. Trong chat riêng, mỗi bên chặn được bên kia để không nhận tin nữa.

**Why this priority**: Constitution nguyên tắc VI yêu cầu nội dung người dùng tạo báo cáo được; các thao tác còn lại tăng an toàn nhưng ít dùng.

**Independent Test**: A gửi tin rồi thu hồi trong 10 phút, B thấy "Tin nhắn đã được thu hồi". B báo cáo một tin của A với lý do "Lừa đảo", báo cáo vào hàng đợi admin. Chủ sân chặn A: A không gửi được tin vào cuộc trò chuyện đó.

**Acceptance Scenarios**:

1. **Given** người gửi giữ vào tin của mình trong vòng 10 phút sau khi gửi, **When** chọn "Thu hồi" và xác nhận, **Then** mọi người trong cuộc trò chuyện thấy "Tin nhắn đã được thu hồi" thay cho nội dung, ảnh của tin bị xóa; quá 10 phút thì không còn lựa chọn này.
2. **Given** người dùng giữ vào tin của người khác, **When** chọn "Báo cáo", chọn lý do (Spam, Ngôn từ xúc phạm, Lừa đảo, Nội dung không phù hợp, Khác kèm mô tả) và gửi, **Then** báo cáo vào hàng đợi kiểm duyệt của admin; mỗi người báo cáo một tin tối đa một lần.
3. **Given** admin ẩn tin nhắn theo báo cáo (spec 120), **When** người trong cuộc trò chuyện xem, **Then** thấy "Tin nhắn đã bị ẩn" thay cho nội dung.
4. **Given** một bên trong chat riêng bấm "Chặn" và xác nhận, **When** bên bị chặn cố gửi tin, **Then** hệ thống từ chối và app báo "Bạn không thể gửi tin trong cuộc trò chuyện này"; bên chặn bỏ chặn được bất cứ lúc nào.
5. **Given** chat nhóm, **When** thành viên muốn ngăn một người làm phiền, **Then** việc này do chủ nhóm/quản trị viên xử lý bằng cách mời ra khỏi nhóm (spec 090); không có chức năng chặn trong chat nhóm.

---

### Edge Cases

- **Mất mạng khi gửi:** tin hiện ngay với trạng thái "Đang gửi", tự gửi khi có mạng lại; quá 1 phút chưa gửi được thì hiện "Gửi lỗi" kèm "Gửi lại". Gửi lại nhiều lần không tạo tin trùng.
- **Người dùng xóa tài khoản:** tin đã gửi vẫn còn, hiện "Người dùng đã xóa" và ảnh mặc định (theo spec 011); chat riêng với người đó chuyển sang chỉ đọc.
- **Chủ sân không còn là chủ sân (bị khóa/thu hồi):** cuộc trò chuyện vẫn còn để đọc; người chơi vẫn gửi được cho người đó như một người dùng thường.
- **Cơ sở bị ẩn/khóa:** chat với chủ sân vẫn dùng được; nút "Nhắn tin cho chủ sân" chỉ có trên sân đang hoạt động.
- **Nhóm rất đông:** chat nhóm vẫn hiện tin theo thời gian, tải 30 tin mới nhất, cuộn lên để tải tin cũ hơn.
- **Tin nhắn rỗng hoặc chỉ có khoảng trắng:** không gửi được. Tin dài quá giới hạn: app chặn và hiện bộ đếm.
- **Tin chứa số điện thoại hoặc liên kết:** vẫn gửi được; xử lý vi phạm qua báo cáo.
- **Hai người cùng mở chat riêng lần đầu cùng lúc:** chỉ có một cuộc trò chuyện giữa hai người.
- **Đồng hồ điện thoại sai:** thứ tự và thời gian tin nhắn theo giờ server.

## Requirements *(mandatory)*

### Functional Requirements

**Cuộc trò chuyện**

- **FR-001**: Có hai loại cuộc trò chuyện: chat nhóm (mỗi nhóm/CLB đúng một cuộc) và chat riêng giữa một người chơi và một chủ sân đã được duyệt. Người chơi KHÔNG nhắn riêng được cho người chơi khác ở phiên bản này.
- **FR-002**: Chat nhóm PHẢI được tạo cùng lúc với nhóm; người trong cuộc trò chuyện luôn đúng bằng danh sách thành viên nhóm của spec 090 (vào nhóm thì có, rời/bị mời ra thì mất quyền ngay). Thành viên mới đọc được toàn bộ lịch sử tin nhắn của nhóm.
- **FR-003**: Chat riêng PHẢI được mở từ nút "Nhắn tin cho chủ sân" ở chi tiết một sân đang hoạt động; mỗi cặp người chơi – chủ sân có tối đa một cuộc trò chuyện (kể cả khi hai bên mở cùng lúc hoặc hỏi về nhiều cơ sở của cùng chủ). Cuộc trò chuyện lưu và hiển thị cơ sở được hỏi gần nhất. Chủ sân không nhắn cho chính mình.
- **FR-004**: Chỉ người trong cuộc trò chuyện (và admin hệ thống khi kiểm duyệt) được đọc tin và ảnh; chỉ người trong cuộc trò chuyện được gửi tin. Kiểm tra ở phía server.
- **FR-005**: Chủ sân PHẢI thấy các cuộc trò chuyện với khách ở mục "Tin nhắn khách hàng" trong chế độ quản lý sân (spec 011), tách khỏi các cuộc trò chuyện với tư cách người chơi.

**Tin nhắn**

- **FR-006**: Người dùng PHẢI gửi được tin chữ (1–1000 ký tự, không chỉ khoảng trắng) hoặc một ảnh kèm chú thích tùy chọn; ảnh được nén trước khi tải lên, sau khi nén không quá 2 MB, định dạng JPEG, PNG hoặc WebP.
- **FR-007**: Tin nhắn PHẢI hiện cho những người khác trong cuộc trò chuyện đang mở trong vòng 3 giây, theo thứ tự thời gian do server ghi. Mỗi tin hiện người gửi (tên, ảnh đại diện trong chat nhóm), nội dung, thời gian.
- **FR-008**: Tin gửi khi mất mạng PHẢI hiện ngay với trạng thái "Đang gửi" và tự gửi khi có mạng; gửi lỗi thì hiện "Gửi lỗi" kèm "Gửi lại"/"Xóa". Gửi lại hoặc bấm lặp KHÔNG ĐƯỢC tạo tin trùng.
- **FR-009**: Cuộc trò chuyện tải 30 tin mới nhất khi mở, cuộn lên để tải thêm tin cũ hơn.
- **FR-010**: Người gửi PHẢI thu hồi được tin của mình trong vòng 10 phút sau khi gửi (tính theo giờ server); tin thu hồi hiện "Tin nhắn đã được thu hồi" với mọi người và ảnh của tin bị xóa. Không có sửa tin nhắn.

**Danh sách và chưa đọc**

- **FR-011**: Danh sách "Tin nhắn" PHẢI sắp xếp theo thời gian tin cuối mới nhất, hiện tên, ảnh, tin cuối, thời gian và số tin chưa đọc của từng cuộc; cập nhật theo thời gian thực khi đang mở. Thanh điều hướng có huy hiệu số cuộc trò chuyện còn tin chưa đọc.
- **FR-012**: Số tin chưa đọc của một cuộc về 0 khi người dùng mở cuộc đó và xem tới tin mới nhất. Không có trạng thái "đã xem" cho từng tin ở phiên bản này.
- **FR-013**: Người dùng PHẢI bật/tắt được thông báo cho từng cuộc trò chuyện; tắt thông báo không ảnh hưởng số chưa đọc.

**An toàn và kiểm duyệt**

- **FR-014**: Người dùng PHẢI báo cáo được tin nhắn của người khác với một lý do (Spam, Ngôn từ xúc phạm, Lừa đảo, Nội dung không phù hợp, Khác kèm mô tả tối đa 500 ký tự); mỗi người báo cáo một tin tối đa một lần; báo cáo vào hàng đợi kiểm duyệt của admin (spec 120).
- **FR-015**: Tin bị admin ẩn PHẢI hiện "Tin nhắn đã bị ẩn" với mọi người trong cuộc trò chuyện, ảnh của tin không xem được.
- **FR-016**: Trong chat riêng, mỗi bên PHẢI chặn và bỏ chặn được bên kia; khi bị chặn, bên bị chặn không gửi được tin vào cuộc trò chuyện đó (kiểm tra ở phía server) và chỉ thấy thông báo "Bạn không thể gửi tin trong cuộc trò chuyện này". Chat nhóm không có chặn; xử lý bằng quyền quản lý thành viên của spec 090.

**Trải nghiệm chung**

- **FR-017**: Mỗi màn hình có dữ liệu (danh sách trò chuyện, cuộc trò chuyện) PHẢI có đủ 4 trạng thái: đang tải, có dữ liệu, rỗng, lỗi kèm nút thử lại. Mất mạng PHẢI hiện thông báo, giữ nội dung đang soạn, không crash.
- **FR-018**: Hệ thống PHẢI phát sinh sự kiện để nhóm thông báo (spec 100) gửi thông báo tin nhắn mới cho những người trong cuộc trò chuyện không tắt thông báo và không đang mở cuộc đó. Nội dung và cách gửi thuộc spec 100.

### Key Entities

- **Cuộc trò chuyện (Chat)**: Loại (nhóm/riêng), danh sách người trong cuộc, nhóm liên quan (chat nhóm) hoặc cơ sở được hỏi gần nhất (chat riêng), tin cuối, thời gian cập nhật. Chat riêng có tối đa một cuộc cho mỗi cặp người dùng; chat nhóm có đúng một cuộc cho mỗi nhóm.
- **Tin nhắn (Message)**: Thuộc một cuộc trò chuyện. Gồm: người gửi, nội dung chữ, ảnh (nếu có), thời gian do server ghi, trạng thái (bình thường, đã thu hồi, bị ẩn), mã yêu cầu chống trùng.
- **Trạng thái đọc của người dùng**: Với mỗi người trong mỗi cuộc trò chuyện: thời điểm đọc gần nhất (để tính số chưa đọc), bật/tắt thông báo, đã chặn bên kia hay chưa (chat riêng).
- **Báo cáo (Report)** (của spec 120): người báo cáo, tin nhắn bị báo cáo, lý do, mô tả, trạng thái xử lý.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tin nhắn chữ hiện trên máy người nhận (đang mở cuộc trò chuyện) trong vòng 3 giây ở 95% lần gửi trên mạng 4G.
- **SC-002**: Người chơi gửi được câu hỏi đầu tiên cho chủ sân trong dưới 30 giây kể từ khi mở chi tiết sân.
- **SC-003**: 100% lần thử đọc tin/ảnh hoặc gửi tin vào cuộc trò chuyện mình không thuộc về (kể cả sau khi rời nhóm), gửi tin khi đã bị chặn, hoặc thu hồi tin của người khác đều bị từ chối, kể cả khi gửi không qua app.
- **SC-004**: Gửi một tin khi mất mạng rồi bật mạng lại: tin được gửi đúng một lần trong 100% lần thử.
- **SC-005**: Hai người cùng mở chat riêng lần đầu cùng lúc tạo ra đúng một cuộc trò chuyện trong 100% lần thử.
- **SC-006**: Danh sách trò chuyện và 30 tin mới nhất của một cuộc tải xong trong dưới 3 giây trên 4G.
- **SC-007**: Ít nhất 4/5 người thử (thành viên nhóm khác) tự nhắn được cho chủ sân, gửi ảnh trong chat nhóm và báo cáo một tin mà không cần hướng dẫn.

## Assumptions

- Phạm vi chat riêng (chỉ người chơi – chủ sân), thành viên mới đọc toàn bộ lịch sử nhóm, thu hồi tin trong 10 phút không sửa, chặn trong chat riêng, một cuộc trò chuyện cho mỗi cặp người chơi – chủ sân: đã chốt ở Clarifications. Cách một cuộc cho mỗi cặp khớp ID tất định ở đặc tả CSDL mục 5.
- Trạng thái chặn và thời điểm đọc gần nhất của từng người là dữ liệu mới so với đặc tả CSDL mục 4.11; cần bổ sung khi viết plan.
- Giới hạn mặc định: tin chữ 1000 ký tự, một ảnh mỗi tin, ảnh ≤ 2 MB (đặc tả CSDL mục 8), tải 30 tin mỗi lần.
- Ảnh chat chỉ người trong cuộc trò chuyện xem được (kho ảnh riêng tư theo đặc tả CSDL mục 8).
- Nút "Nhắn tin cho chủ sân" nằm trên chi tiết sân của spec 030 (A); spec này định nghĩa hành vi. Mục "Tin nhắn khách hàng" nằm trong menu chế độ quản lý sân (spec 111/112, D).
- Danh sách thành viên nhóm do spec 090 quản lý (FR-022 của spec 090).
- Loại thông báo `CHAT_MESSAGE` đã có trong đặc tả CSDL mục 4.13; cài đặt bật/tắt toàn cục thuộc spec 100.
