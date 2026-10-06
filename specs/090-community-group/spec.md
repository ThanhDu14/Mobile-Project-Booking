# Feature Specification: Nhóm/CLB và bảng tin cộng đồng

**Feature Branch**: `feature/09-community-chat`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Người chơi tạo và tham gia nhóm/CLB cầu lông, đăng bài và sự kiện giao lưu trên bảng tin nhóm. Tạo nhóm: tên, mô tả, ảnh đại diện, khu vực, chế độ công khai hoặc riêng tư; người tạo là chủ nhóm. Tìm nhóm theo tên và khu vực; tham gia nhóm công khai ngay, nhóm riêng tư phải được chủ nhóm/quản trị viên duyệt. Rời nhóm; chủ nhóm phân quyền quản trị viên, mời ra khỏi nhóm. Bảng tin nhóm: thành viên đăng bài (chữ, tối đa vài ảnh) hoặc sự kiện giao lưu (thời gian, địa điểm, số người dự kiến); thành viên bấm "Tham gia sự kiện". Người đăng sửa/xóa bài của mình, quản trị viên ẩn bài vi phạm trong nhóm. Báo cáo bài đăng vi phạm (gửi vào hàng đợi kiểm duyệt của admin, spec 120). Số thành viên do hệ thống cập nhật, người dùng không tự ghi. Ảnh lưu ở bucket group-media của Supabase. Có xử lý mất mạng, upload ảnh lỗi, trạng thái rỗng. Không bao gồm: chat nhóm và chat riêng (spec 091), xử lý báo cáo của admin (spec 120), gửi thông báo (spec 100). Dữ liệu: groups/{groupId} (+ members, posts), reports theo docs/design/database/README.md mục 4.10, 4.12, 8."

**Nhóm tính năng**: [09 — Cộng đồng và chat](../../docs/features/09-community-chat/README.md) · **Phụ trách**: C · **Liên quan**: spec 091 (chat nhóm dùng danh sách thành viên của spec này), [spec 011](../011-account-profile/spec.md) (khu vực, xóa tài khoản), spec 100 (thông báo), spec 120 (xử lý báo cáo)

## Clarifications

### Session 2026-10-06

- Q: Người chưa là thành viên có xem được bảng tin (bài, sự kiện, ảnh) của nhóm công khai không? → A: Không. Bảng tin chỉ thành viên xem được ở mọi nhóm; "công khai" nghĩa là tham gia ngay không cần duyệt, người ngoài chỉ thấy trang giới thiệu.
- Q: Bài đăng có bình luận và lượt thích không? → A: Không có ở phiên bản này; thảo luận qua chat nhóm (spec 091).
- Q: Khi số người tham gia sự kiện đã bằng số dự kiến, thành viên khác có còn tham gia được không? → A: Được; số người dự kiến chỉ để tham khảo, sự kiện hiện "Vượt số dự kiến".
- Q: Nhóm riêng tư có xuất hiện khi tìm kiếm không? → A: Có, hiện trang giới thiệu (tên, ảnh, mô tả, khu vực, số thành viên) để người chơi gửi yêu cầu tham gia.
- Q: Bài đăng của thành viên có cần chủ nhóm/quản trị viên duyệt trước khi hiện không? → A: Không; bài hiện ngay, chủ nhóm/quản trị viên ẩn sau nếu vi phạm.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tạo nhóm/CLB (Priority: P1)

Người chơi muốn lập CLB cho hội chơi quen mở mục "Cộng đồng", bấm "Tạo nhóm", nhập tên, mô tả, chọn ảnh đại diện, khu vực và chế độ công khai hoặc riêng tư rồi lưu. Người tạo trở thành chủ nhóm và được đưa vào trang nhóm vừa tạo.

**Why this priority**: Không có nhóm thì không có bảng tin, sự kiện hay chat nhóm; đây là nền của toàn bộ nhóm 09.

**Independent Test**: Tạo một nhóm công khai và một nhóm riêng tư; kiểm tra cả hai hiện trong "Nhóm của tôi" với vai trò chủ nhóm, số thành viên là 1, thông tin đúng như đã nhập.

**Acceptance Scenarios**:

1. **Given** người chơi đã đăng nhập, **When** nhập tên hợp lệ, chọn khu vực, chế độ "Công khai" và bấm "Tạo nhóm", **Then** nhóm được tạo, người tạo là chủ nhóm, số thành viên là 1 và app mở trang nhóm.
2. **Given** người chơi bỏ trống tên hoặc tên quá ngắn/quá dài, **When** bấm "Tạo nhóm", **Then** app chỉ rõ lỗi ở ô tên và không tạo.
3. **Given** người chơi chọn ảnh đại diện, **When** ảnh tải lên lỗi, **Then** app báo lỗi, giữ nguyên thông tin đã nhập và cho thử lại hoặc tạo nhóm không có ảnh (dùng ảnh mặc định).
4. **Given** người chơi đã làm chủ 5 nhóm, **When** cố tạo thêm, **Then** app báo đã đạt giới hạn và không tạo.
5. **Given** chủ nhóm mở trang nhóm, **When** bấm "Sửa nhóm", **Then** sửa được tên, mô tả, ảnh, khu vực và chế độ; thành viên thường không thấy nút này và không sửa được bằng bất kỳ cách nào.
6. **Given** người chơi bấm "Tạo nhóm" nhiều lần hoặc thử lại khi mất mạng, **When** hệ thống xử lý, **Then** chỉ một nhóm được tạo.

---

### User Story 2 - Tìm và tham gia nhóm (Priority: P1)

Người chơi tìm nhóm theo tên và khu vực, xem trang giới thiệu nhóm (tên, ảnh, mô tả, khu vực, số thành viên, chế độ) rồi bấm "Tham gia". Nhóm công khai cho vào ngay; nhóm riêng tư gửi yêu cầu chờ chủ nhóm hoặc quản trị viên duyệt.

**Why this priority**: Nhóm chỉ có giá trị khi người khác tìm và vào được; cùng P1 với tạo nhóm.

**Independent Test**: Tài khoản B tìm thấy nhóm công khai của A theo tên và theo khu vực, tham gia và thấy ngay bảng tin; B gửi yêu cầu vào nhóm riêng tư của A, A duyệt, B trở thành thành viên; số thành viên của mỗi nhóm tăng đúng 1.

**Acceptance Scenarios**:

1. **Given** có nhiều nhóm, **When** người chơi gõ một phần tên (không dấu, không phân biệt hoa thường) và/hoặc chọn khu vực, **Then** thấy danh sách nhóm khớp, mỗi nhóm có ảnh, tên, khu vực, số thành viên, nhãn "Công khai"/"Riêng tư"; hồ sơ có khu vực hay chơi (spec 011) thì bộ lọc khu vực được điền sẵn.
2. **Given** người chơi xem một nhóm công khai chưa tham gia, **When** bấm "Tham gia", **Then** trở thành thành viên ngay, số thành viên tăng 1 và thấy nút đăng bài.
3. **Given** người chơi xem một nhóm riêng tư, **When** bấm "Xin tham gia", **Then** trạng thái đổi thành "Đang chờ duyệt"; người chơi hủy được yêu cầu của mình khi chưa được duyệt.
4. **Given** chủ nhóm hoặc quản trị viên mở "Yêu cầu tham gia", **When** bấm "Duyệt" hoặc "Từ chối", **Then** người xin vào trở thành thành viên (số thành viên tăng 1) hoặc yêu cầu bị xóa; thành viên thường không duyệt được.
5. **Given** người chơi đã là thành viên, **When** bấm "Rời nhóm" và xác nhận, **Then** không còn là thành viên, số thành viên giảm 1.
6. **Given** không có nhóm nào khớp, **When** danh sách cập nhật, **Then** thấy màn hình trống gợi ý bỏ bớt bộ lọc hoặc "Tạo nhóm của bạn".
7. **Given** nhiều người tham gia cùng lúc, **When** hệ thống xử lý, **Then** số thành viên cuối cùng đúng bằng số thành viên thực tế.

---

### User Story 3 - Đăng bài trên bảng tin nhóm (Priority: P2)

Thành viên mở bảng tin nhóm, viết bài (chữ, kèm tối đa 4 ảnh) và đăng. Bảng tin hiện bài mới nhất trước, kèm tên, ảnh đại diện người đăng và thời gian. Người đăng sửa hoặc xóa bài của mình.

**Why this priority**: Bảng tin giữ nhóm hoạt động, nhưng nhóm đã dùng được để tập hợp thành viên (và chat ở spec 091) trước khi có bài đăng.

**Independent Test**: Thành viên đăng một bài có 2 ảnh; thành viên khác thấy bài ở đầu bảng tin với đủ ảnh; người đăng sửa nội dung (hiện "Đã chỉnh sửa") rồi xóa, bài biến mất.

**Acceptance Scenarios**:

1. **Given** thành viên ở bảng tin, **When** viết nội dung, chọn 2 ảnh và bấm "Đăng", **Then** bài hiện ở đầu bảng tin với đủ 2 ảnh theo đúng thứ tự.
2. **Given** thành viên đã chọn 4 ảnh, **When** cố thêm ảnh, **Then** app báo đã đạt tối đa 4 ảnh.
3. **Given** một ảnh tải lên lỗi, **When** quá trình đăng kết thúc, **Then** app giữ nguyên nội dung, đánh dấu ảnh lỗi, cho thử lại hoặc bỏ ảnh đó; bài không được hiển thị với ảnh hỏng.
4. **Given** người đăng mở bài của mình, **When** sửa nội dung/ảnh và lưu, **Then** bài hiện nội dung mới kèm nhãn "Đã chỉnh sửa"; **When** xóa và xác nhận, **Then** bài và ảnh của bài bị xóa.
5. **Given** người không phải thành viên (hoặc thành viên không phải người đăng), **When** cố đăng bài vào nhóm hoặc sửa/xóa bài của người khác bằng bất kỳ cách nào, **Then** hệ thống từ chối.
6. **Given** bảng tin có nhiều bài, **When** cuộn xuống cuối, **Then** tải thêm bài cũ hơn; nhóm chưa có bài thì thấy màn hình trống "Hãy là người đầu tiên đăng bài".
7. **Given** người chưa là thành viên xem một nhóm (công khai hoặc riêng tư), **When** mở trang nhóm, **Then** chỉ thấy trang giới thiệu (tên, ảnh, mô tả, khu vực, số thành viên) và nút "Tham gia"/"Xin tham gia", không thấy bài, sự kiện hay ảnh của bảng tin.
8. **Given** thành viên bấm "Đăng", **When** bài được lưu, **Then** bài hiện ngay trên bảng tin, không cần chủ nhóm hay quản trị viên duyệt.

---

### User Story 4 - Sự kiện giao lưu (Priority: P2)

Thành viên đăng một sự kiện giao lưu: tiêu đề, thời gian, địa điểm, số người dự kiến và mô tả. Thành viên khác bấm "Tham gia sự kiện" hoặc "Bỏ tham gia"; sự kiện hiện số người đã tham gia và danh sách tên của họ cho các thành viên nhóm.

**Why this priority**: Sự kiện giao lưu là điểm khác của CLB so với nhóm chat thường, nhưng xây trên bảng tin (User Story 3).

**Independent Test**: Thành viên A đăng sự kiện thứ Bảy 19:00 tại một sân, dự kiến 8 người; B và C bấm tham gia; mọi thành viên thấy "2/8 người tham gia" và tên B, C; B bỏ tham gia thì còn 1.

**Acceptance Scenarios**:

1. **Given** thành viên chọn "Tạo sự kiện", **When** nhập tiêu đề, thời gian trong tương lai, địa điểm, số người dự kiến và đăng, **Then** sự kiện hiện trên bảng tin với nhãn "Sự kiện", thời gian, địa điểm và "0/N người tham gia".
2. **Given** thời gian sự kiện ở quá khứ hoặc thiếu tiêu đề/địa điểm, **When** bấm đăng, **Then** app báo lỗi và không đăng.
3. **Given** thành viên xem sự kiện chưa diễn ra, **When** bấm "Tham gia sự kiện", **Then** số người tham gia tăng 1 và tên người đó hiện trong danh sách; bấm "Bỏ tham gia" thì giảm 1.
4. **Given** số người tham gia đã bằng số dự kiến, **When** thành viên khác bấm "Tham gia sự kiện", **Then** vẫn tham gia được và sự kiện hiện "Vượt số dự kiến" (số dự kiến chỉ để tham khảo).
5. **Given** sự kiện đã qua thời gian diễn ra, **When** thành viên xem, **Then** sự kiện có nhãn "Đã diễn ra" và không còn nút tham gia/bỏ tham gia.
6. **Given** người đăng sửa thời gian hoặc địa điểm sự kiện, **When** lưu, **Then** sự kiện hiện thông tin mới kèm "Đã chỉnh sửa" và hệ thống phát sự kiện để báo những người đã tham gia.
7. **Given** nhiều người bấm tham gia cùng lúc, **When** hệ thống xử lý, **Then** số người tham gia đúng bằng danh sách thực tế, không ai bị tính hai lần.

---

### User Story 5 - Quản lý thành viên (Priority: P2)

Chủ nhóm xem danh sách thành viên, phong hoặc gỡ quản trị viên, mời thành viên ra khỏi nhóm, chuyển quyền chủ nhóm cho người khác, và xóa nhóm khi không dùng nữa. Quản trị viên duyệt yêu cầu tham gia, mời thành viên thường ra khỏi nhóm.

**Why this priority**: Cần để nhóm tự quản lý được khi đông người, nhưng nhóm nhỏ vẫn hoạt động được khi chưa có phần này.

**Independent Test**: Chủ nhóm A phong B làm quản trị viên; B mời C (thành viên thường) ra khỏi nhóm; B không mời được A; A chuyển quyền chủ nhóm cho B rồi rời nhóm; B là chủ nhóm mới.

**Acceptance Scenarios**:

1. **Given** thành viên mở danh sách thành viên, **When** danh sách hiện, **Then** thấy tên, ảnh đại diện, vai trò (Chủ nhóm, Quản trị viên, Thành viên), chủ nhóm và quản trị viên ở đầu.
2. **Given** chủ nhóm chọn một thành viên, **When** bấm "Phong quản trị viên" hoặc "Gỡ quản trị viên", **Then** vai trò đổi ngay; chỉ chủ nhóm làm được việc này.
3. **Given** chủ nhóm hoặc quản trị viên chọn một thành viên thường, **When** bấm "Mời ra khỏi nhóm" và xác nhận, **Then** người đó không còn là thành viên, số thành viên giảm 1; quản trị viên KHÔNG mời được chủ nhóm hoặc quản trị viên khác.
4. **Given** người bị mời ra khỏi nhóm, **When** muốn vào lại (kể cả nhóm công khai), **Then** phải gửi yêu cầu tham gia và chờ duyệt.
5. **Given** chủ nhóm muốn rời nhóm, **When** bấm "Rời nhóm", **Then** app yêu cầu chuyển quyền chủ nhóm cho một thành viên khác trước; nếu nhóm chỉ còn mình chủ nhóm thì gợi ý xóa nhóm.
6. **Given** chủ nhóm bấm "Xóa nhóm" và xác nhận bằng cách gõ tên nhóm, **When** xóa xong, **Then** nhóm không còn trong tìm kiếm và "Nhóm của tôi" của mọi thành viên; bài và ảnh của nhóm bị xóa.

---

### User Story 6 - Báo cáo và ẩn bài vi phạm (Priority: P3)

Người xem một bài (hoặc sự kiện) thấy nội dung vi phạm thì bấm "Báo cáo", chọn lý do. Quản trị viên nhóm ẩn bài vi phạm trong nhóm mình; báo cáo được chuyển cho admin hệ thống xử lý (spec 120).

**Why this priority**: Constitution nguyên tắc VI yêu cầu nội dung người dùng báo cáo được, nhưng tần suất thấp so với các luồng chính.

**Independent Test**: B báo cáo bài của C với lý do "Spam"; báo cáo xuất hiện trong hàng đợi của admin; quản trị viên nhóm ẩn bài, các thành viên khác không còn thấy bài, C thấy bài với nhãn "Đã bị ẩn".

**Acceptance Scenarios**:

1. **Given** người chơi xem bài của người khác, **When** bấm "Báo cáo", chọn lý do (Spam, Ngôn từ xúc phạm, Lừa đảo, Nội dung không phù hợp, Khác kèm mô tả) và gửi, **Then** app báo "Đã gửi báo cáo" và báo cáo vào hàng đợi kiểm duyệt của admin.
2. **Given** người chơi đã báo cáo bài này, **When** cố báo cáo lần nữa, **Then** app báo đã báo cáo trước đó và không tạo báo cáo trùng.
3. **Given** quản trị viên hoặc chủ nhóm xem một bài, **When** bấm "Ẩn bài" và xác nhận, **Then** bài không còn hiện với thành viên khác; người đăng vẫn thấy bài kèm nhãn "Đã bị ẩn"; quản trị viên bấm "Hiện lại" được.
4. **Given** admin hệ thống ẩn bài theo báo cáo (spec 120), **When** quản trị viên nhóm xem bài, **Then** không hiện lại được bài đó.
5. **Given** người chơi xem bài của chính mình, **When** mở menu bài, **Then** không có nút "Báo cáo".

---

### Edge Cases

- **Tên nhóm trùng:** cho phép trùng tên giữa các nhóm; nhóm phân biệt bằng ảnh, khu vực và số thành viên.
- **Đổi chế độ nhóm:** chế độ chỉ ảnh hưởng cách tham gia. Đổi từ công khai sang riêng tư không ảnh hưởng thành viên hiện có; người mới phải xin tham gia. Đổi từ riêng tư sang công khai thì các yêu cầu đang chờ được duyệt tự động.
- **Chủ nhóm xóa tài khoản:** quyền chủ nhóm tự chuyển cho quản trị viên tham gia lâu nhất, nếu không có thì cho thành viên tham gia lâu nhất; nhóm không còn ai thì bị xóa. Bài của người đã xóa tài khoản hiện "Người dùng đã xóa" (theo spec 011).
- **Thành viên rời nhóm hoặc bị mời ra:** bài đã đăng vẫn giữ trên bảng tin; tên tham gia sự kiện sắp tới của người đó bị bỏ.
- **Sự kiện bị xóa:** những người đã tham gia được báo (qua spec 100).
- **Bài chứa số điện thoại hoặc liên kết:** vẫn cho đăng; xử lý vi phạm qua báo cáo.
- **Rời màn hình khi đang soạn bài:** app hỏi "Bỏ bài đang viết?" trước khi thoát.
- **Bấm "Tham gia"/"Tham gia sự kiện" nhiều lần:** chỉ tính một lần.
- **Nhóm bị admin khóa (spec 120):** nhóm không hiện trong tìm kiếm, thành viên thấy thông báo "Nhóm đã bị tạm khóa" và không đăng bài được.
- **Người chưa đăng nhập:** xem được trang giới thiệu nhóm nhưng phải đăng nhập (spec 010) để tham gia.

## Requirements *(mandatory)*

### Functional Requirements

**Nhóm**

- **FR-001**: Người chơi đã đăng nhập PHẢI tạo được nhóm với: tên (3–50 ký tự, bắt buộc), mô tả (tối đa 500 ký tự), ảnh đại diện (tùy chọn, có ảnh mặc định), khu vực (một quận từ danh mục), chế độ "Công khai" hoặc "Riêng tư". Người tạo là chủ nhóm. Mỗi người làm chủ tối đa 5 nhóm.
- **FR-002**: Chỉ chủ nhóm được sửa thông tin và chế độ của nhóm và xóa nhóm (kiểm tra ở phía server). Xóa nhóm PHẢI xác nhận bằng cách gõ lại tên nhóm và xóa luôn bài, sự kiện, ảnh của nhóm.
- **FR-003**: Số thành viên của nhóm PHẢI do hệ thống tự cập nhật khi có người vào, rời, bị mời ra; người dùng KHÔNG ĐƯỢC tự ghi giá trị này, và giá trị PHẢI đúng khi nhiều người vào/rời cùng lúc.
- **FR-004**: Tạo nhóm nhiều lần do bấm lặp hoặc thử lại khi mất mạng KHÔNG ĐƯỢC tạo nhóm trùng.

**Tìm và tham gia**

- **FR-005**: Người chơi PHẢI tìm được nhóm theo tên (khớp phần đầu của từ, không dấu, không phân biệt hoa thường) và lọc theo khu vực; bộ lọc khu vực điền sẵn theo khu vực hay chơi trong hồ sơ. Kết quả gồm cả nhóm công khai và riêng tư (chỉ phần giới thiệu), tải theo trang (20 nhóm), không hiện nhóm bị khóa.
- **FR-006**: Nhóm công khai: người chơi tham gia ngay bằng một lần bấm. Nhóm riêng tư: người chơi gửi yêu cầu tham gia, hủy được yêu cầu khi chưa được duyệt; chủ nhóm hoặc quản trị viên duyệt hoặc từ chối.
- **FR-007**: Người chơi PHẢI rời nhóm được bất cứ lúc nào, trừ chủ nhóm phải chuyển quyền chủ nhóm trước (hoặc xóa nhóm nếu chỉ còn một mình).
- **FR-008**: Người bị mời ra khỏi nhóm KHÔNG ĐƯỢC tự tham gia lại ngay; muốn vào lại phải gửi yêu cầu và được duyệt, kể cả với nhóm công khai.

**Vai trò và quản lý thành viên**

- **FR-009**: Nhóm có 3 vai trò: Chủ nhóm (đúng một người), Quản trị viên, Thành viên. Chỉ chủ nhóm phong/gỡ quản trị viên và chuyển quyền chủ nhóm. Chủ nhóm và quản trị viên duyệt yêu cầu tham gia, mời thành viên thường ra khỏi nhóm, ẩn/hiện bài trong nhóm. Quản trị viên KHÔNG ĐƯỢC mời chủ nhóm hoặc quản trị viên khác ra khỏi nhóm. Mọi kiểm tra quyền PHẢI ở phía server.
- **FR-010**: Danh sách thành viên PHẢI hiển thị tên, ảnh đại diện và vai trò; chỉ thành viên của nhóm (và admin hệ thống) xem được danh sách thành viên, kể cả với nhóm công khai. Người ngoài chỉ thấy số thành viên.

**Bảng tin và bài đăng**

- **FR-011**: Thành viên PHẢI đăng được bài gồm nội dung chữ (1–2000 ký tự) và 0–4 ảnh; mỗi ảnh được nén trước khi tải lên, sau khi nén không quá 2 MB, định dạng JPEG, PNG hoặc WebP. Bài chỉ hiển thị khi mọi ảnh đã tải lên thành công; ảnh lỗi thì giữ nội dung và cho thử lại hoặc bỏ ảnh.
- **FR-012**: Bảng tin PHẢI sắp xếp mới nhất trước, tải theo trang (20 bài), mỗi bài hiện tên, ảnh đại diện người đăng, thời gian, nội dung, ảnh (bấm để xem lớn), nhãn "Đã chỉnh sửa" nếu có.
- **FR-013**: Bài hiện ngay khi đăng, không qua bước duyệt. Bài không có bình luận hay lượt thích ở phiên bản này. Chỉ người đăng được sửa hoặc xóa bài của mình; xóa bài thì xóa luôn ảnh của bài. Chủ nhóm và quản trị viên không sửa nội dung bài của người khác.
- **FR-014**: Bảng tin (bài, sự kiện, ảnh, người tham gia sự kiện) của mọi nhóm, kể cả nhóm công khai, chỉ thành viên (và admin hệ thống) đọc được; người ngoài chỉ thấy trang giới thiệu nhóm. Chỉ thành viên được đăng bài và tham gia sự kiện. Kiểm tra ở phía server.

**Sự kiện giao lưu**

- **FR-015**: Thành viên PHẢI tạo được sự kiện gồm: tiêu đề (bắt buộc, tối đa 100 ký tự), thời gian diễn ra (bắt buộc, trong tương lai), địa điểm (bắt buộc, chữ tự do), số người dự kiến (2–100), mô tả (tùy chọn); sự kiện hiện trên bảng tin với nhãn "Sự kiện".
- **FR-016**: Thành viên PHẢI bấm được "Tham gia sự kiện"/"Bỏ tham gia" trước thời gian diễn ra; số người tham gia do hệ thống tính, đúng khi nhiều người bấm cùng lúc, mỗi người tính tối đa một lần. Số người dự kiến chỉ để tham khảo: vượt số dự kiến vẫn tham gia được và sự kiện hiện "Vượt số dự kiến".
- **FR-017**: Thành viên của nhóm PHẢI xem được danh sách tên người đã tham gia sự kiện. Sự kiện đã qua thời gian diễn ra hiện "Đã diễn ra" và không nhận tham gia nữa.

**Báo cáo và ẩn bài**

- **FR-018**: Người dùng xem được một bài PHẢI báo cáo được bài đó (trừ bài của chính mình) với một lý do: Spam, Ngôn từ xúc phạm, Lừa đảo, Nội dung không phù hợp, Khác (kèm mô tả tối đa 500 ký tự). Mỗi người báo cáo một bài tối đa một lần; báo cáo vào hàng đợi kiểm duyệt của admin (spec 120).
- **FR-019**: Bài bị ẩn (bởi chủ nhóm/quản trị viên hoặc admin hệ thống) KHÔNG ĐƯỢC hiển thị với ai ngoài người đăng (thấy nhãn "Đã bị ẩn"), chủ nhóm, quản trị viên và admin. Bài do admin hệ thống ẩn thì quản trị viên nhóm không hiện lại được.

**Trải nghiệm chung**

- **FR-020**: Mỗi màn hình có dữ liệu (tìm nhóm, trang nhóm, bảng tin, danh sách thành viên, yêu cầu tham gia, người tham gia sự kiện) PHẢI có đủ 4 trạng thái: đang tải, có dữ liệu, rỗng, lỗi kèm nút thử lại. Mất mạng PHẢI hiện thông báo, giữ nguyên nội dung đang soạn, không crash.
- **FR-021**: Hệ thống PHẢI phát sinh sự kiện để nhóm thông báo (spec 100) gửi: có yêu cầu tham gia mới (cho chủ nhóm/quản trị viên), yêu cầu được duyệt/từ chối, bị mời ra khỏi nhóm, sự kiện mới trong nhóm, sự kiện mình tham gia bị sửa hoặc xóa. Nội dung và cách gửi thuộc spec 100.
- **FR-022**: Danh sách thành viên của nhóm là nguồn duy nhất để chat nhóm (spec 091) xác định ai ở trong cuộc trò chuyện của nhóm.

### Key Entities

- **Nhóm (Group)**: CLB/nhóm cầu lông. Gồm: tên, mô tả, ảnh đại diện, khu vực, chủ nhóm, chế độ (công khai/riêng tư), số thành viên (chỉ hệ thống ghi), trạng thái (hoạt động/bị khóa), thời gian tạo.
- **Thành viên nhóm (Member)**: Quan hệ giữa một người dùng và một nhóm. Gồm: vai trò (chủ nhóm, quản trị viên, thành viên), thời gian tham gia. Mỗi người tối đa một bản ghi mỗi nhóm.
- **Yêu cầu tham gia (Join request)**: Người xin vào nhóm riêng tư (hoặc người từng bị mời ra), trạng thái chờ duyệt, thời gian gửi. Có thể là một trạng thái của bản ghi thành viên.
- **Bài đăng (Post)**: Thuộc một nhóm. Gồm: người đăng, loại (bài thường/sự kiện), nội dung, danh sách ảnh, trạng thái (hiển thị/bị ẩn, ai ẩn), thời gian tạo và sửa. Sự kiện có thêm: tiêu đề, thời gian diễn ra, địa điểm, số người dự kiến, số người tham gia (chỉ hệ thống ghi).
- **Người tham gia sự kiện**: Một thành viên đã bấm "Tham gia sự kiện"; mỗi người tối đa một lần mỗi sự kiện.
- **Báo cáo (Report)** (của spec 120): người báo cáo, bài bị báo cáo, lý do, mô tả, trạng thái xử lý.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người chơi tạo xong một nhóm (có ảnh đại diện) trong dưới 1 phút.
- **SC-002**: Người chơi tìm và tham gia được một nhóm công khai trong dưới 30 giây kể từ khi mở mục "Cộng đồng".
- **SC-003**: Khi 20 người cùng lúc tham gia một nhóm (hoặc một sự kiện), số thành viên (số người tham gia) cuối cùng đúng bằng 20 cộng số trước đó, không ai bị tính hai lần.
- **SC-004**: 100% lần thử tự ghi số thành viên, sửa/xóa nhóm khi không phải chủ nhóm, đăng bài khi không phải thành viên, sửa/xóa bài của người khác, quản trị viên mời chủ nhóm ra, hoặc đọc bảng tin/ảnh của bất kỳ nhóm nào khi không phải thành viên đều bị từ chối, kể cả khi gửi không qua app.
- **SC-005**: Bài mới đăng hiện trên bảng tin của thành viên khác trong vòng 5 giây.
- **SC-006**: Danh sách nhóm và trang đầu bảng tin tải xong trong dưới 3 giây trên mạng 4G.
- **SC-007**: 100% bài bị ẩn không hiện với người không có quyền xem trong mọi màn hình (bảng tin, tìm kiếm, liên kết trực tiếp).
- **SC-008**: Ít nhất 4/5 người thử (thành viên nhóm khác) tự tạo nhóm, tham gia nhóm của người khác và đăng một sự kiện mà không cần hướng dẫn.

## Assumptions

- **Bảng tin chỉ thành viên xem được**, kể cả nhóm công khai (đã chốt ở Clarifications). Khớp với kho ảnh nhóm chỉ thành viên xem (đặc tả CSDL mục 8); cần sửa dòng `groups` ở mục 7 đặc tả CSDL thành "nhóm `PUBLIC` ai cũng đọc thông tin giới thiệu, bảng tin chỉ thành viên" khi viết plan.
- Không có bình luận/lượt thích, số người dự kiến của sự kiện chỉ để tham khảo, nhóm riêng tư vẫn hiện khi tìm kiếm, bài không cần duyệt: đã chốt ở Clarifications.
- Giới hạn mặc định theo thông lệ: tên nhóm 3–50 ký tự, mô tả 500, bài 2000 ký tự, tối đa 4 ảnh mỗi bài, mỗi người làm chủ tối đa 5 nhóm, trang 20 mục; ảnh ≤ 2 MB theo đặc tả CSDL mục 8.
- Không giới hạn số thành viên mỗi nhóm và số nhóm một người tham gia.
- Chat nhóm (tạo cuộc trò chuyện khi tạo nhóm, thêm/bớt người theo thành viên) thuộc spec 091; spec này chỉ quản lý danh sách thành viên.
- Xử lý báo cáo, khóa nhóm, ẩn bài ở cấp hệ thống thuộc spec 120 (D); spec này tạo báo cáo và tôn trọng kết quả.
- Danh sách loại thông báo ở FR-021 phải gửi cho D trước 14/10.
- Danh mục khu vực dùng chung với spec 011/020 (quận/huyện).
