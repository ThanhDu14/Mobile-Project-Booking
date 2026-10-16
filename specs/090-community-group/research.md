# Research: Nhóm/CLB và bảng tin cộng đồng (spec 090)

**Ngày**: 2026-10-16 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Mỗi mục ghi: quyết định, lý do, phương án đã cân nhắc. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Mọi thao tác ghi đi qua Edge Function, Rules chỉ mở quyền đọc

**Quyết định:**
- `group-manage`: `create`, `update`, `delete`, `join`, `cancelRequest`, `leave`, `approve`, `reject`, `setRole`, `transferOwner`, `remove`.
- `group-post`: `create`, `update`, `delete`, `hide`, `unhide`, `attend`, `unattend`, `report`.
- `group-media-url`: cấp signed URL tải lên/tải xuống ảnh nhóm (R8).

Rules của `groups` và các collection con đều `write: if false`. Rules chỉ mở quyền đọc đúng phạm vi (R6).

**Lý do:**
- Hầu hết thao tác phải đổi nhiều document cùng lúc: `memberCount`, thành viên chat nhóm (spec 091), yêu cầu tham gia, `attendeeCount`. Constitution III yêu cầu transaction.
- Kiểm tra vai trò (chủ nhóm, quản trị viên), giới hạn 5 nhóm, người bị mời ra không tự vào lại (FR-008) đều cần đọc nhiều document. Viết bằng Rules sẽ rất dài và khó test.
- Xóa bài phải xóa ảnh trong Storage, việc này cần `service_role` (constitution II).
- Cùng cách làm với spec 070 (`review`) và 080 (`register-drop-in`): một function có nhiều `action`.

**Đã cân nhắc:**
- *Cho client tạo bài trực tiếp, Rules kiểm tra*: Rules không kiểm tra được ảnh đã thật sự tải lên chưa (FR-011), cũng không xóa được ảnh khi xóa bài. Loại.
- *Mỗi thao tác một function*: khoảng 20 function, lặp phần xác thực, tốn hạn mức deploy. Loại.

## R2. Thành viên, yêu cầu tham gia và người bị mời ra là ba collection con riêng

**Quyết định:**
- `groups/{groupId}/members/{uid}`: document tồn tại nghĩa là đang là thành viên. Thêm trường `userId` **[MỚI]** (bằng ID document) để truy vấn collection group "Nhóm của tôi".
- `groups/{groupId}/joinRequests/{uid}` **[MỚI]**: yêu cầu đang chờ duyệt.
- `groups/{groupId}/removals/{uid}` **[MỚI]**: người đã bị mời ra. Có document này thì `join` vào nhóm công khai cũng thành yêu cầu chờ duyệt (FR-008). Được duyệt vào lại thì xóa.

**Lý do:**
- Rules kiểm tra thành viên chỉ cần `exists(/groups/$(g)/members/$(request.auth.uid))`, không phải đọc thêm trường trạng thái.
- Chat nhóm (spec 091) và mọi truy vấn "thành viên" không phải lọc bỏ người đang chờ hay đã bị mời ra (FR-022).
- Người chờ duyệt đọc được yêu cầu của chính mình để hiện "Đang chờ duyệt".

**Đã cân nhắc:** *Một document `members/{uid}` có `status` (`ACTIVE`, `PENDING`, `REMOVED`)*: spec cho phép cách này, nhưng mọi Rules và truy vấn đều phải thêm `status == ACTIVE`; quên một chỗ là lộ bảng tin cho người chưa được duyệt (FR-014). Loại.

## R3. Số thành viên và số người tham gia sự kiện đúng khi nhiều người thao tác cùng lúc (FR-003, FR-016, SC-003)

**Quyết định:**
- `join`, `approve`, `leave`, `remove` chạy trong transaction có ghi `groups/{groupId}.memberCount`.
- `attend`, `unattend` chạy trong transaction có ghi `posts/{postId}.attendeeCount`.
- Document thành viên và người tham gia dùng ID là `uid`. Transaction đọc document đó trước: đã có thì trả kết quả cũ, không cộng lại.

**Lý do:** các transaction cùng tranh chấp một document cha nên Firestore xếp chúng tuần tự (giống `registeredCount` của spec 080). Dùng ID là `uid` thì bấm nhiều lần cũng chỉ có một bản ghi (Edge Cases).

**Đã cân nhắc:** *Đếm bằng `count()` khi đọc*: người ngoài không đọc được `members` (FR-010), nên không đếm được; tốn thêm lượt đọc trên mỗi thẻ nhóm. Loại.

**Giới hạn:** Firestore khuyến nghị khoảng 1 lần ghi/giây trên một document. Với 20 người tham gia cùng lúc, transaction thử lại tối đa 8 lần (backoff 50–400 ms, giống spec 080), hết lượt thì trả `503 BUSY` và app tự gửi lại cùng `requestId`. Test SC-003 kiểm tra đúng kịch bản này.

## R4. Tạo nhóm không trùng và giới hạn 5 nhóm mỗi chủ (FR-001, FR-004)

**Quyết định:**
- `groupId = requestId` (UUID client sinh khi bấm "Tạo nhóm"). Transaction đọc `groups/{requestId}`: đã có và `ownerId == uid` thì trả nhóm cũ (`200`).
- Giới hạn 5 nhóm: trong transaction, truy vấn `groups where ownerId == uid, status in [ACTIVE, LOCKED]` bằng `runQueryInTx` (B, giống spec 081 T005) và đếm.
- Trong cùng transaction: tạo `members/{uid}` với `role = OWNER`, `memberCount = 1`, và gọi hook tạo chat nhóm (R10).

**Lý do:** giống cách `bookings` dùng `requestId` làm ID (đặc tả CSDL mục 5).

**Giới hạn đã biết:** nếu cùng một người gửi 2 yêu cầu tạo nhóm với 2 `requestId` khác nhau đúng cùng lúc khi đang có 4 nhóm, truy vấn trong transaction có thể không thấy nhóm kia, nên người đó có 6 nhóm. Đây là giới hạn mềm, không ảnh hưởng dữ liệu người khác; chấp nhận ở quy mô đồ án.

## R5. Tìm nhóm theo tên và khu vực (FR-005)

**Quyết định:**
- Thêm `nameKeywords` 🔒 **[MỚI]**: tiền tố của từng từ trong tên, chữ thường, bỏ dấu. Sinh bằng cùng hàm A dùng cho `venues.nameKeywords` (đặc tả CSDL mục 4.3), đặt ở `_shared/keywords.ts`.
- Truy vấn: `status == ACTIVE`, tùy chọn `nameKeywords array-contains <từ khóa đã chuẩn hóa>`, tùy chọn `districtId ==`, `orderBy memberCount desc`, `limit 20`, `startAfter`.

**Lý do:** cùng cách với tìm sân (spec 020), không cần dịch vụ tìm kiếm ngoài (constitution: không dịch vụ trả phí).

**Đã cân nhắc:** *Tải hết nhóm về lọc ở client*: vượt hạn mức đọc khi nhiều nhóm. Loại.

## R6. Phạm vi đọc: người ngoài chỉ thấy trang giới thiệu (FR-010, FR-014, FR-019)

**Quyết định:**

| Dữ liệu | Ai đọc được |
|---|---|
| `groups/{groupId}` (giới thiệu) | Mọi người, kể cả khách chưa đăng nhập, khi `status != DELETED` |
| `members` | Thành viên của nhóm, admin hệ thống |
| `joinRequests/{uid}` | Chính người xin; chủ nhóm, quản trị viên; admin |
| `removals` | Chủ nhóm, quản trị viên; admin |
| `posts` | Thành viên khi `status == VISIBLE`; người đăng (cả khi bị ẩn); chủ nhóm, quản trị viên; admin |
| `posts/{postId}/attendees` | Thành viên; admin |

Bảng tin của thành viên thường truy vấn `where status == VISIBLE` (Rules chỉ cho query có ràng buộc này). Bài bị ẩn của chính mình lấy bằng truy vấn thứ hai `where authorId == uid, status == HIDDEN` rồi gộp ở app.

**Lý do:** đã chốt ở Clarifications (bảng tin chỉ thành viên xem, kể cả nhóm công khai). Đặc tả CSDL mục 7 đang ghi "nhóm `PUBLIC` ai cũng R", cần sửa thành "giới thiệu nhóm ai cũng R, bảng tin chỉ thành viên" (spec 090, Assumptions).

## R7. Ẩn bài: phân biệt nhóm ẩn và admin hệ thống ẩn (FR-019, AC 6.4)

**Quyết định:** `posts.status` (`VISIBLE`, `HIDDEN`) kèm 2 trường 🔒 **[MỚI]**: `hiddenBy` (`GROUP`, `SYSTEM`) và `hiddenByUid`.
- Chủ nhóm, quản trị viên `hide` → `HIDDEN`, `hiddenBy = GROUP`; `unhide` chỉ khi `hiddenBy == GROUP`.
- Admin hệ thống (spec 120, `action = HIDE_CONTENT`) ghi `status = HIDDEN`, `hiddenBy = SYSTEM`. `unhide` của nhóm gặp bài này trả `403 HIDDEN_BY_SYSTEM`.

**Lý do:** spec 120 ẩn nội dung qua `targetPath` và chỉ đổi trạng thái (không xóa). Thêm `hiddenBy` để quản trị viên nhóm không gỡ được quyết định của admin.

## R8. Ảnh: bảng tin private, ảnh đại diện nhóm public

**Quyết định:**
- **Ảnh bài đăng:** bucket private `group-media`, object `{groupId}/{postId}/{n}.{ext}` (đúng đặc tả CSDL mục 8). RLS chặn mọi truy cập trực tiếp của người dùng. App gọi `group-media-url`:
  - `upload`: kiểm tra là thành viên, trả tối đa 4 signed upload URL cho bài đang soạn (`postId` = `requestId` của bài).
  - `download`: nhận tối đa 40 path (một trang bảng tin), kiểm tra là thành viên của từng nhóm, trả signed URL hết hạn sau 1 giờ. App cache URL theo path tới khi hết hạn.
  - `discard`: xóa ảnh đã tải lên của bài đang soạn mà người dùng bỏ.
- **Ảnh đại diện nhóm:** bucket public mới `group-avatars` **[MỚI]**, object `{groupId}/avatar.{ext}`. Chủ nhóm tải lên qua signed upload URL do `group-manage` cấp. Firestore lưu `avatarPath` **[MỚI]** thay cho `avatarUrl`.

**Lý do:**
- FR-014: ảnh bảng tin chỉ thành viên xem. RLS không đọc được Firestore nên phải qua Edge Function (đặc tả CSDL mục 8).
- Ảnh đại diện hiện trên kết quả tìm kiếm cho cả người ngoài và khách chưa đăng nhập. Nếu để private thì mỗi thẻ nhóm phải xin signed URL, giống lý do spec 011 chọn bucket public cho `avatars`.
- Đặc tả CSDL quy ước Firestore lưu path, không lưu URL (mục 8), nên đổi `avatarUrl` thành `avatarPath`.

**Đã cân nhắc:**
- *Dùng `sign-upload`/`sign-download` chung của D*: hai function này cần biết quyền thành viên nhóm (dữ liệu của C). Tách `group-media-url` để D không phụ thuộc schema của C; nếu D muốn gộp thì `group-media-url` chỉ còn là một handler.
- *Signed URL cho từng ảnh*: một trang 20 bài có thể tới 80 lời gọi. Loại, dùng lô.

**Ảnh mồ côi:** ảnh đã tải lên mà bài không được tạo (mất mạng, thoát app) được app gọi `discard` khi bỏ bài. Còn sót thì chấp nhận ở quy mô đồ án (không thêm job dọn dẹp, constitution VII).

## R9. Bài đăng chỉ hiện khi mọi ảnh đã tải lên (FR-011, AC 3.3)

**Quyết định:**
1. App xin signed upload URL, nén ảnh (≤ 2 MB, JPEG/PNG/WebP) và tải lên từng ảnh; ảnh lỗi thì đánh dấu, cho thử lại hoặc bỏ.
2. Chỉ khi mọi ảnh còn lại đã tải xong, app gọi `group-post` `create` với `requestId` và `photoPaths`.
3. Server kiểm tra mỗi path đúng tiền tố `{groupId}/{requestId}/` và object có thật trong Storage, rồi mới ghi bài `VISIBLE`.

`postId = requestId` nên bấm "Đăng" nhiều lần chỉ tạo một bài.

**Lý do:** bài không bao giờ hiện với ảnh hỏng, đúng AC 3.3.

## R10. Đồng bộ thành viên chat nhóm (FR-022, spec 091)

**Quyết định:** `_shared/groups.ts` có hook `onGroupMembershipChanged(tx, groupId, { added, removed, created, deleted })`. Spec 090 gọi hook này trong **cùng transaction** ở mọi thao tác đổi danh sách thành viên: `create`, `join`, `approve`, `leave`, `remove`, `delete`, chuyển quyền khi xóa tài khoản. Spec 090 để thân hàm trống; spec 091 cài phần cập nhật `chats/{chatId}.memberIds`.

**Lý do:** spec 091 yêu cầu thành viên chat "đúng bằng danh sách thành viên nhóm" và mất quyền đọc "ngay khi rời". Ghi trong cùng transaction thì không có lúc nào hai danh sách lệch nhau. Cùng cách hook `afterSpotsReleased` của spec 080 dành cho 081.

## R11. Vai trò và các thao tác quản lý thành viên (FR-007, FR-009)

**Quyết định:** quy tắc kiểm tra trong `group-manage` (đọc `members/{caller}` trong transaction):

| Thao tác | Người gọi được phép | Ràng buộc |
|---|---|---|
| `setRole` (phong/gỡ quản trị viên) | Chủ nhóm | Đích là thành viên, không phải chính mình |
| `transferOwner` | Chủ nhóm | Đích là thành viên; chủ cũ thành `ADMIN`, `groups.ownerId` đổi |
| `remove` | Chủ nhóm: mời ai cũng được trừ chính mình. Quản trị viên: chỉ `MEMBER` | Ghi `removals/{uid}` |
| `approve`, `reject` | Chủ nhóm, quản trị viên | Có `joinRequests/{uid}` |
| `leave` | Mọi thành viên trừ chủ nhóm | Chủ nhóm nhận `409 OWNER_MUST_TRANSFER` (kèm `soleMember: true` nếu chỉ còn một mình) |
| `update`, `delete` | Chủ nhóm | `delete` gửi kèm `confirmName` phải khớp tên nhóm |

Rời nhóm hoặc bị mời ra thì trong cùng transaction xóa người đó khỏi danh sách tham gia các sự kiện **chưa diễn ra** của nhóm (Edge Cases), giảm `attendeeCount` tương ứng. Tìm bằng collection group `attendees where userId == uid, groupId == g, eventAt > now`.

## R12. Đổi chế độ nhóm (Edge Cases)

**Quyết định:**
- `PUBLIC → PRIVATE`: chỉ đổi `visibility`.
- `PRIVATE → PUBLIC`: trong cùng transaction duyệt mọi `joinRequests` đang chờ (chuyển thành `members`), trừ người có `removals` (vẫn phải chờ duyệt). Gửi `GROUP_ACTIVITY` `JOIN_APPROVED` cho từng người sau khi commit.

**Giới hạn:** một transaction ghi tối đa 500 document. Nếu có hơn 200 yêu cầu đang chờ thì trả `409 TOO_MANY_PENDING` và yêu cầu chủ nhóm duyệt bớt trước. Ở quy mô đồ án không xảy ra.

## R13. Xóa nhóm (FR-002, AC 5.6)

**Quyết định:**
1. Transaction: `status = DELETED`, gọi hook `onGroupMembershipChanged(..., { deleted: true })`. Nhóm biến mất khỏi tìm kiếm (`status == ACTIVE`) ngay.
2. Sau commit: xóa theo lô (≤ 500 mỗi lô) `posts/*/attendees`, `posts`, `members`, `joinRequests`, `removals`; xóa thư mục `{groupId}/` trong `group-media` và `group-avatars`. Cuối cùng xóa document nhóm.
3. Nếu bước 2 lỗi giữa chừng: nhóm vẫn ở `DELETED` nên không ai thấy; gọi lại `delete` sẽ chạy tiếp bước 2 (idempotent).

App lọc bỏ nhóm `DELETED` trong "Nhóm của tôi" để không hiện trong lúc đang xóa dở.

## R14. Báo cáo bài đăng (FR-018)

**Quyết định:** `group-post` `report` ghi `reports/{reportId}` (collection của D) với:
- `reportId = POST_{groupId}_{postId}_{uid}`: ID tất định nên mỗi người báo cáo một bài một lần. Đã có thì trả `409 ALREADY_REPORTED`.
- `targetType = POST`, `targetPath = groups/{groupId}/posts/{postId}`, `reason` (`SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER`), `detail` (≤ 500 ký tự, bắt buộc khi `OTHER`), `status = OPEN`, `action = NONE`.
- Kiểm tra: người báo cáo đọc được bài (là thành viên; bài `VISIBLE`) và không phải người đăng.

**Lý do:** bảo đảm không trùng (AC 6.2) và người ngoài nhóm không báo cáo bài mình không thấy được.

**Cần D đồng ý:** quy ước ID tất định và danh sách `reason` (spec 070 và 091 nên dùng chung danh sách này).

## R15. Thông báo (FR-021)

**Quyết định:** sau khi commit, gọi `send-notification` của D với `type = GROUP_ACTIVITY`, `data = { event, groupId, postId? }`:

| `data.event` | Phát từ | Người nhận |
|---|---|---|
| `JOIN_REQUESTED` | `join` (nhóm riêng tư hoặc người bị mời ra) | Chủ nhóm, quản trị viên |
| `JOIN_APPROVED`, `JOIN_REJECTED` | `approve`, `reject`, đổi sang công khai | Người xin |
| `REMOVED` | `remove` | Người bị mời ra |
| `EVENT_CREATED` | `group-post` `create` loại `EVENT` | Mọi thành viên trừ người đăng |
| `EVENT_UPDATED` | `update` sự kiện có đổi `eventAt` hoặc `eventLocation` | Người đã tham gia, trừ người sửa |
| `EVENT_DELETED` | `delete` sự kiện chưa diễn ra | Người đã tham gia |

Lỗi gửi thông báo bị bỏ qua, không hoàn tác thao tác. Thao tác người dùng tự làm (tự rời, tự hủy yêu cầu) không gửi (đặc tả CSDL mục 4.13).

**Cần D chốt:** danh sách `data.event` (spec 100 ghi "subtype schema chưa chốt"), và `EVENT_CREATED` gửi danh sách `recipientUids` hay để D tự lấy thành viên nhóm.

## R16. Chủ nhóm hoặc thành viên xóa tài khoản (Edge Cases, spec 011)

**Quyết định:** `_shared/groups.ts` có `anonymizeCommunityGroups(uid)` cho `delete-account` (A) gọi (spec 011 research R12, `anonymizeCommunity`):
- Bài của người đó: `authorSnapshot = null`, `authorDeleted = true` 🔒 **[MỚI]**; bài vẫn giữ.
- Mỗi nhóm người đó là thành viên: chạy như `leave`. Nếu là chủ nhóm: chuyển quyền cho quản trị viên tham gia lâu nhất, không có thì thành viên tham gia lâu nhất (`orderBy joinedAt`); không còn ai thì xóa nhóm (R13).
- Xóa `joinRequests/{uid}` đang chờ; bỏ tham gia các sự kiện chưa diễn ra.

## R17. Nhóm bị admin khóa (Edge Cases, spec 120)

**Quyết định:** `groups.status` 🔒 **[MỚI]**: `ACTIVE`, `LOCKED`, `DELETED`. `LOCKED` do spec 120 ghi.
- Tìm kiếm chỉ trả `ACTIVE`.
- Mọi thao tác ghi của `group-manage`, `group-post` trên nhóm `LOCKED` trả `423 GROUP_LOCKED` (trừ `leave`).
- App hiện "Nhóm đã bị tạm khóa" và ẩn nút đăng bài.

**Cần D đồng ý:** spec 120 hiện chỉ có `LOCK_VENUE`, `LOCK_USER`. Khóa nhóm cần thêm action hoặc làm thủ công; ghi vào bảng phối hợp.

## R18. Ảnh người đăng và tên hiển thị

**Quyết định:** bài và người tham gia sự kiện lưu `authorSnapshot` / `userSnapshot` (`displayName`, `avatarPath`) 🔒 **[MỚI]** do server chép từ `users/{uid}` lúc ghi. Danh sách thành viên đọc `members` (có `userSnapshot` 🔒 **[MỚI]**).

**Lý do:** đặc tả CSDL mục 1 cho phép phi chuẩn hóa trường hiển thị; tránh đọc `users` cho từng bài (SC-006 < 3 giây). Đổi tên sau này không cập nhật bài cũ, chấp nhận như `reviews.userSnapshot`.
