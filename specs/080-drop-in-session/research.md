# Research: Đặt lịch chơi vãng lai theo lượt (spec 080)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Mỗi mục ghi: quyết định, lý do, phương án đã cân nhắc. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Đăng ký và hủy chạy ở đâu khi gói Spark không có trigger Firestore

**Quyết định:** Hai Supabase Edge Function `register-drop-in` và `cancel-drop-in` (tên đã có trong đặc tả CSDL mục 8a). Mỗi function chạy **một Firestore transaction qua REST API** (`beginTransaction` → đọc `dropInSessions/{id}` và `registrations/{uid}` → kiểm tra → `commit`) bằng service account, dùng helper trong `supabase/functions/_shared/firestore.ts` (B phụ trách). Logic nghiệp vụ dùng chung đặt ở `supabase/functions/_shared/dropin.ts` để spec 081 (hàng chờ), spec 112 (chủ sân hủy buổi) và `delete-account` (A) gọi lại trong transaction của họ.

**Lý do:** Constitution III bắt buộc kiểm tra chỗ và ghi trong cùng một bước ở server. Client không được ghi `registeredCount`, `amount`, `status`, `qrToken` (FR-009, SC-004). Gói Spark không có Cloud Functions nên không có trigger.

**Đã cân nhắc:**
- *Client tự chạy Firestore transaction + Rules kiểm tra `registeredCount`*: Rules không so được tổng `partySize` của mọi đăng ký với `capacity` một cách an toàn, và không sinh được `qrToken` bí mật. Loại.
- *Một function `drop-in` nhiều action (như `review` của 070)*: gọn hơn nhưng đặc tả CSDL và spec đã đặt tên hai function; spec 081 sẽ thêm `join-waitlist`/`leave-waitlist`. Giữ hai tên để khớp tài liệu chung.

## R2. Chống vượt sức chứa khi nhiều người đăng ký cùng lúc (SC-002)

**Quyết định:** Mọi thay đổi số chỗ đều đi qua document buổi `dropInSessions/{id}` (trường 🔒 `registeredCount`). Transaction đọc buổi, kiểm tra `registeredCount + partySize ≤ capacity`, rồi ghi buổi và đăng ký trong cùng `commit`. Firestore khóa document đọc trong transaction nên hai transaction cùng sửa một buổi sẽ có một bên bị `ABORTED`. Edge Function thử lại tối đa **8 lần**, backoff 50–400 ms có ngẫu nhiên. Hết số lần thử thì trả `503 BUSY`; app tự gửi lại **cùng `requestId`** (R3) sau 1 giây, tối đa 2 lần, rồi mới báo lỗi.

**Lý do:** Một document tranh chấp duy nhất giống cách slot dùng ID tất định (constitution III). Ở quy mô đồ án (20 người cùng lúc), 8 lần thử là đủ. Người thua luôn nhận `SESSION_FULL` kèm số chỗ còn lại ở lần thử sau.

**Đã cân nhắc:** *Distributed counter (shard)*: dùng cho ghi tần suất cao, không cần ở đây và làm khó việc kiểm tra tổng. Loại.

## R3. Chống đăng ký trùng và trừ tiền hai lần (FR-008, SC-003)

**Quyết định:**
- ID đăng ký = `uid` (đặc tả CSDL mục 5) nên mỗi người chỉ có một document mỗi buổi.
- App sinh `requestId` (UUID) mỗi lần mở màn hình xác nhận, giữ nguyên khi bấm lại hoặc thử lại.
- Trong transaction:
  - Đã có đăng ký `REGISTERED`/`CHECKED_IN` với cùng `requestId` → trả `200` với bản hiện có (idempotent).
  - Đã có với `requestId` khác → `409 ALREADY_REGISTERED`.
  - Đã có `CANCELLED` (hoặc spec 081: lượt chờ đã kết thúc) → ghi đè thành đăng ký mới (Edge Cases: đăng ký lại sau khi hủy).
- `cancel-drop-in` nhận `requestId` riêng. Đăng ký đã `CANCELLED` → trả `200` với kết quả cũ, không trả lại chỗ lần nữa.

**Lý do:** Cùng mẫu với `bookings` (`bookingId = requestId`) nhưng ID document đã là `uid` nên `requestId` lưu thành trường.

**Hệ quả:** Đăng ký lại sau khi hủy ghi đè bản đã hủy. "Lượt vãng lai của tôi" chỉ còn trạng thái mới nhất của buổi đó. Chấp nhận được vì spec không yêu cầu lịch sử nhiều lần trong cùng một buổi.

## R4. Thanh toán giả lập và chính sách hủy

**Quyết định:**
- `paymentMethod`: `MOCK_PAY` (thanh toán ngay giả lập) hoặc `AT_VENUE`.
- `paymentStatus` 🔒 (mới): `PAID` (MOCK_PAY), `UNPAID` (AT_VENUE), `REFUNDED`.
- `amount` 🔒 = `pricePerPerson` (đọc trong transaction) × `partySize`, kiểu `Long`.
- Hoàn tiền tính trong `_shared/dropin.ts`:

  ```
  refund = 0
  if paymentStatus == PAID:
      if sessionChanged || (session.startAt − serverNow) ≥ 240 phút: refund = amount
  ```

  `refund > 0` → `paymentStatus = REFUNDED`, ghi `refundAmount`. `AT_VENUE` không phát sinh tiền nên hủy luôn "Không mất phí".
- App gọi `cancel-drop-in` với `dryRun: true` để hiện số tiền được hoàn trước khi xác nhận (FR-017). Kết quả thật vẫn tính lại khi xác nhận vì thời gian có thể qua mốc 4 giờ.

**Lý do:** Theo Clarifications của spec, không dùng ví của spec 050. Mốc tính theo giờ server, cùng mốc 4 giờ với chính sách hủy đặt sân (đặc tả CSDL mục 9 #3), nên hai bên dùng chung hàm tính mốc.

**Đã cân nhắc:** *Tính số tiền hoàn ở app*: đồng hồ điện thoại có thể sai (Edge Cases). Chỉ dùng để hiển thị qua `dryRun`.

## R5. Mã QR và mã vé (FR-013, FR-014)

**Quyết định:**
- `qrToken` 🔒: 32 byte ngẫu nhiên (`crypto.getRandomValues`), mã hóa base64url, sinh trong transaction đăng ký.
- `ticketCode` 🔒 (mới): 8 ký tự in hoa không dễ nhầm (bỏ `0 O 1 I`), dạng `DI-XXXXXXXX`, để chủ sân nhập tay khi không quét được.
- App vẽ QR từ `qrToken` bằng ZXing (constitution). QR không chứa thông tin cá nhân.
- Hủy lượt hoặc hủy buổi thì xóa `qrToken` (đặt `null`). Check-in (D, spec 112) tìm đăng ký bằng collection group query `registrations where qrToken == X` hoặc `ticketCode == X`, và chỉ chấp nhận khi `status == REGISTERED`.

**Lý do:** Token đủ dài để không đoán được. Hủy là xóa token nên mã cũ tự hết hiệu lực. Rules chỉ cho người đăng ký và chủ cơ sở đọc document (R8).

## R6. Danh sách buổi và bộ lọc (FR-001 → FR-004)

**Quyết định:** Một truy vấn Firestore có index:

```
dropInSessions
  where status in [OPEN, FULL]
  where startAt >= now  and  startAt < now + 30 ngày
  [where districtId in [...]]        (tối đa 15 quận, vì tích với status ≤ 30 nhánh)
  [where date == yyyyMMdd]
  [where timeSlot == MORNING|NOON|AFTERNOON|EVENING]   (trường mới)
  [where level == ...]
  [where pricePerPerson <= X]        (bất đẳng thức thứ hai, Firestore hỗ trợ nhiều trường bất đẳng thức)
  orderBy startAt asc, limit 20      (phân trang bằng startAfter)
```

- `timeSlot` (mới) được tính một lần khi chủ sân tạo hoặc sửa buổi, theo giờ bắt đầu (Sáng 05:00–11:00, Trưa 11:00–13:00, Chiều 13:00–18:00, Tối 18:00–23:00). Hàm tính đặt trong `_shared/dropin.ts::deriveSessionFields()` để Edge Function của D gọi.
- `venueSnapshot` (mới): `name`, `address`, `districtName`, ghi lúc tạo buổi, để thẻ danh sách không phải đọc thêm `venues`.
- Bỏ buổi của cơ sở bị ẩn/khóa (AC 1.5): tầng data đọc theo lô các `venues` của trang hiện tại (≤ 20 `get`, cache trong bộ nhớ 5 phút), rồi lọc buổi có `venue.status != ACTIVE`. Rules `venues` cho `get` mọi trạng thái (đề xuất R6 của plan 070).

**Lý do:** Lọc chính xác ở server, mỗi trang đúng 20 buổi, nằm trong hạn mức gói miễn phí.

**Đã cân nhắc:**
- *Lọc giờ và giá ở client*: trang có thể ít hơn 20 buổi hoặc rỗng giả. Loại.
- *Lưu `venueStatus` trên buổi*: D phải cập nhật hàng loạt mỗi khi ẩn/khóa cơ sở, thêm việc cho D. Loại ở phiên bản này.

## R7. "Lượt vãng lai của tôi" (FR-019, FR-020)

**Quyết định:** Thêm vào đăng ký các trường 🔒 `userId`, `sessionStartAt`, `sessionEndAt`, `venueId` để truy vấn collection group `registrations`:
- **Sắp tới:** `userId == uid`, `status in [REGISTERED, WAITLISTED]`, `sessionStartAt >= now`, `orderBy sessionStartAt asc`. `WAITLISTED` dành cho spec 081.
- **Đã qua:** `userId == uid`, `orderBy sessionStartAt desc`, trang 20. Client bỏ những lượt thuộc tab "Sắp tới". Mỗi người chỉ có vài chục lượt nên chấp nhận được.

Thông tin hiển thị (tên cơ sở, giờ mới, trạng thái buổi) lấy bằng cách đọc theo lô các `dropInSessions` của trang (≤ 20).

**Nhãn kết quả** suy ra, không thêm enum (spec 112 FR-011 giữ 4 trạng thái):

| Nhãn | Điều kiện |
|---|---|
| Đã tham gia | `status == CHECKED_IN` |
| Đã hủy | `status == CANCELLED`, `cancelledBy == USER` |
| Buổi bị hủy | `status == CANCELLED`, `cancelledBy == OWNER` (hoặc `session.status == CANCELLED`) |
| Không đến | `status == REGISTERED` và `session.endAt < now` |

`cancelledBy` (mới): `USER`, `OWNER`, `SYSTEM` (xóa tài khoản), giống `bookings`.

**Lý do:** Không thêm enum mới. Spec 112 đã chốt "không thêm field hoặc enum mới" cho `dropInSessions`, còn các trường thêm vào `registrations` là của C.

## R8. Ai được đọc đăng ký (FR-021)

**Quyết định:** Rules của `dropInSessions/{id}/registrations/{uid}`:
- **Đọc:** người có `request.auth.uid == uid`, hoặc chủ cơ sở (`get(dropInSessions/{id}).data.ownerId == request.auth.uid`), hoặc `appRole == ADMIN`.
- **Ghi:** không client nào được ghi (mọi ghi qua Edge Function).
- **Truy vấn collection group:** cho phép khi có `where userId == request.auth.uid`. Rules kiểm tra `resource.data.userId == request.auth.uid`.

`dropInSessions` cho mọi người đọc, kể cả khách chưa đăng nhập (Assumptions: khách xem được danh sách). Chỉ chủ sân ghi qua Edge Function của spec 112, còn `registeredCount` 🔒 chỉ server ghi.

## R9. Số chỗ cập nhật thời gian thực và vé khi mất mạng (FR-005, FR-015, SC-006, SC-007)

**Quyết định:**
- Chi tiết buổi dùng snapshot listener trên một document `dropInSessions/{id}`, bọc trong `callbackFlow`, gỡ khi Fragment dừng (constitution I và ràng buộc hạn mức).
- Vé dùng cache offline của Firestore (bật mặc định trên Android). Màn hình vé đọc `Source.CACHE` trước để hiện ngay (< 1 giây), sau đó nghe server để cập nhật. QR vẽ lại từ `qrToken` trong cache, không cần mạng.

**Đã cân nhắc:** *Lưu ảnh QR vào bộ nhớ app*: thừa, vì cache Firestore đã đủ và tránh lộ token qua file.

## R10. Đánh giá buổi chơi (FR-022, FR-023)

**Quyết định:** Mở rộng Edge Function `review` của spec 070 (cũng do C viết), không tạo function mới:
- `create` nhận `sessionId` thay cho `bookingId`. Khi đó `reviewId = {sessionId}_{uid}`, `targetType = DROP_IN`.
- Điều kiện:
  - `registrations/{uid}.status == CHECKED_IN`.
  - `session.endAt ≤ now`.
  - `now ≤ session.endAt + 7 ngày` (cùng hạn với 070). Ghi `reviews.completedAt = session.endAt`.
  - Người viết không phải chủ cơ sở.
- Điểm cộng vào `venues.ratingSums` như đánh giá sân (FR-023). Ảnh dùng tiền tố `{uid}/{reviewId}/`.
- Mã lỗi mới: `409 NOT_CHECKED_IN` ("Bạn chỉ đánh giá được buổi đã tham gia").

**Lý do:** Dùng lại toàn bộ quy tắc sửa/xóa/phản hồi của 070, chỉ khác bước kiểm tra điều kiện. Cần sửa câu "Rules chỉ cho tạo khi đơn tương ứng `COMPLETED`" ở đặc tả CSDL mục 4.7 cho đúng.

## R11. Cảnh báo trùng giờ và trình độ (FR-012)

**Quyết định:** ViewModel màn hình xác nhận gọi UseCase `CheckDropInWarnings`, chỉ cảnh báo chứ không chặn:
- **Trùng lượt vãng lai:** đọc lượt "Sắp tới" của mình (R7), so khoảng giờ.
- **Trùng đơn đặt sân:** dùng interface `UpcomingBookingsReader` do B cung cấp ở `feature/booking/domain/` (truy vấn `bookings` theo index `userId, status, startAt` đã có). Lời gọi chéo tính năng chỉ qua interface ở `domain/` (constitution I).
- **Trình độ:** so `session.level` với `users.level`.

## R12. Thông báo (FR-026)

**Quyết định:**
- Sau khi `register-drop-in` commit thành công, gọi `send-notification` (D) với `type = DROPIN_REGISTERED`, người nhận là người đăng ký, `data = { sessionId }`. Lỗi gửi thông báo được bỏ qua, không làm hỏng đăng ký.
- `cancel-drop-in` do người chơi tự hủy thì **không** gửi (đặc tả CSDL mục 4.13).
- `DROPIN_CHANGED` do Edge Function sửa/hủy buổi của D (spec 112) gửi. Spec này chỉ hiển thị kết quả.

## R13. Xóa tài khoản (Edge Cases, spec 011 FR-022a)

**Quyết định:** `_shared/dropin.ts` export `cancelRegistrationInTx(tx, sessionId, uid, cancelledBy)`. `delete-account` (A) tìm lượt "Sắp tới" của người dùng và gọi hàm này với `cancelledBy = SYSTEM` cho từng lượt. Chỗ được trả lại và chính sách hoàn áp dụng như người chơi tự hủy.

## R14. Buổi "đã kết thúc"

**Quyết định:** Spec 080 suy ra "đã bắt đầu/đã kết thúc" từ `startAt`/`endAt` so với giờ server (trong Edge Function) hoặc giờ hiện tại (ở UI, chỉ để hiển thị). Spec 080 không cần cron. Việc ghi `status = COMPLETED` cho buổi và đóng hàng chờ 30 phút trước giờ bắt đầu sẽ do cron của spec 081 làm (`dropin-close`), vì 081 bắt buộc phải có tác vụ định kỳ đó.

**Lý do:** YAGNI. Danh sách đã lọc `startAt >= now`, còn đánh giá kiểm tra `endAt ≤ now`, nên không phụ thuộc trạng thái `COMPLETED`.

## R15. Thư viện

**Quyết định:** Không thêm thư viện ngoài danh sách của constitution và PR `chore(core)` chung đã nêu ở plan 070 (Hilt, Firebase, supabase-kt, Coil). Thêm **ZXing `zxing-android-embedded`** (có trong bảng công nghệ của constitution) để vẽ QR. Khai báo qua version catalog. Spec 112 dùng cùng thư viện để quét nên nên đưa vào PR core.
