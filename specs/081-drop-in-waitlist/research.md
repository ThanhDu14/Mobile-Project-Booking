# Research: Hàng chờ buổi vãng lai (spec 081)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Nền tảng**: [research 080](../080-drop-in-session/research.md)

Mỗi mục ghi: quyết định, lý do, phương án đã cân nhắc. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Dùng lại dữ liệu và Edge Function của spec 080

**Quyết định:**
- **Dữ liệu:** lượt chờ là một document `dropInSessions/{id}/registrations/{uid}` với `status = WAITLISTED`. Không tạo collection mới.
- **Vào hàng chờ:** dùng `register-drop-in` với tham số `joinWaitlist: true`.
- **Rời hàng chờ:** dùng `cancel-drop-in`. Khi đăng ký đang `WAITLISTED` thì function này hiểu là rời hàng chờ.
- **Function mới duy nhất:** `dropin-close`, chạy theo lịch, để đóng hàng chờ đúng mốc (R7).

**Lý do:**
- Đặc tả CSDL mục 4.9 đã có `WAITLISTED` và `waitlistedAt`. Spec ghi rõ "dùng chung loại dữ liệu đăng ký của spec 080".
- Một document cho mỗi người mỗi buổi tự bảo đảm FR-003: không thể vừa đăng ký vừa chờ.
- Đăng ký, hủy và đẩy lên cùng chạy trên một document buổi, nên mọi thao tác tranh chấp cùng một chỗ (constitution III).

**Đã cân nhắc:**
- *Collection riêng `waitlist`*: phải đồng bộ thêm một nơi, và khó bảo đảm "một người một lượt mỗi buổi". Loại.
- *Function riêng `join-waitlist`/`leave-waitlist`*: bị lặp phần xác thực và kiểm tra buổi đã có trong function của 080. Loại.

## R2. Thứ tự hàng chờ và vị trí công khai mà không lộ danh tính (FR-004, FR-005, FR-006)

**Quyết định:** thêm vào document buổi các trường 🔒 do server ghi:
- `waitlistNextTicket` (Int): bộ đếm tăng dần.
- `waitlistTickets` (Array\<Int>): số vé chờ đang hiệu lực, xếp tăng dần. **Chỉ có số, không có `uid`.**
- `waitlistCount` (Int): bằng `waitlistTickets.size`.

Khi vào hàng chờ, transaction gán `waitlistTicket = waitlistNextTicket` cho lượt chờ, tăng bộ đếm và thêm số vào mảng.
- **Vị trí của tôi** = `waitlistTickets.indexOf(myTicket) + 1`.
- **Số người đang chờ** = `waitlistCount`.

App đã nghe realtime document buổi ở màn hình chi tiết (spec 080 R9), nên vị trí tự cập nhật mà không cần thêm listener.

**Lý do:**
- Rules không cho người chơi đọc lượt của người khác (FR-006), nên client không tự đếm được.
- Mảng số vé không lộ danh tính.
- Số vé do server cấp trong transaction nên không trùng và đúng thứ tự vào. Dùng `serverTimestamp` thì hai người có thể trùng thời điểm.

**Đã cân nhắc:**
- *Edge Function `waitlist-position` trả vị trí*: không cập nhật realtime, tốn thêm lời gọi. Loại.
- *Đếm bằng truy vấn aggregation `count()`*: Rules chặn đọc document của người khác. Loại.

**Giới hạn:** hàng chờ không giới hạn số lượt, nhưng mảng số nguyên rất nhỏ so với giới hạn 1 MB của document. Ở quy mô đồ án (vài chục người chờ) không có vấn đề gì.

## R3. Đẩy người chờ lên trong cùng transaction tạo ra chỗ trống (FR-007, FR-008, FR-009)

**Quyết định:** cài hook `afterSpotsReleased(tx, session, serverNow)` mà spec 080 để trống, thành `promoteFromWaitlist`:

```
if serverNow ≥ session.startAt − 30 phút  → không đẩy ai (hàng chờ đã đóng)
free = capacity − registeredCount
candidates = runQuery trong tx:
             registrations where status == WAITLISTED orderBy waitlistTicket asc
for c in candidates:
    if free == 0: break
    if c.partySize > free: continue        // bỏ qua nhưng giữ vị trí
    chuyển c thành REGISTERED (R4); free −= c.partySize; ghi lại để gửi thông báo
cập nhật registeredCount, status (FULL/OPEN), waitlistTickets, waitlistCount
```

Hook được gọi ở mọi nơi tạo ra chỗ trống:
- `cancel-drop-in` (người chơi hủy);
- `cancelRegistrationInTx` (xóa tài khoản);
- Edge Function sửa buổi của D khi **tăng sức chứa** (spec 112 FR-010a).

**Vì sao không bị đẩy trùng, kể cả khi nhiều lượt hủy cùng lúc (FR-008):** mọi thao tác thay đổi hàng chờ (vào, rời, đẩy lên, đóng) đều ghi vào document buổi. Vì vậy các transaction luôn tranh chấp trên document buổi, và Firestore xếp chúng tuần tự. Truy vấn danh sách người chờ chạy trong cùng transaction (REST `runQuery` có `transaction`) nên luôn thấy dữ liệu mới nhất.

**Lý do:** đúng yêu cầu "trong cùng bước nguyên tử với thao tác tạo ra chỗ trống" (FR-007) và constitution III.

**Đã cân nhắc:** *Cron quét định kỳ để đẩy người lên*: chậm hơn yêu cầu ≤ 10 giây (SC-002), và chỗ trống có thể bị người đăng ký thường lấy mất, trái FR-009. Loại.

## R4. Lượt chờ được đẩy lên trở thành đăng ký chính thức (FR-010)

**Quyết định:**
- **Lúc vào hàng chờ, server chốt:** `pricePerPerson`, `amount = pricePerPerson × partySize`, `paymentMethod`. Để `paymentStatus = null`, `qrToken = null`.
- **Lúc đẩy lên, ghi:**
  - `status = REGISTERED`, `promotedAt = serverNow`, `registeredAt = serverNow`;
  - `paymentStatus = PAID` (nếu `MOCK_PAY`) hoặc `UNPAID` (nếu `AT_VENUE`);
  - `qrToken`, `ticketCode` mới (dùng hàm của spec 080);
  - xóa số vé khỏi `waitlistTickets`.

Nhãn "Được nhận từ hàng chờ" suy ra từ `promotedAt != null`.

**Lý do:** thanh toán giả lập luôn thành công (Clarifications của spec 080), nên bước đẩy lên không thể thất bại vì tiền.

## R5. Tự rời hàng chờ của các buổi trùng giờ (FR-010a)

**Quyết định:** khi đẩy người chơi U lên buổi S, trong cùng transaction:
1. Truy vấn collection group `registrations where userId == U, status == WAITLISTED, sessionStartAt < S.endAt`.
2. Lọc thêm các lượt có `sessionEndAt > S.startAt`.
3. Với mỗi lượt tìm được:
   - lượt chờ chuyển `CANCELLED`, `cancelledBy = SYSTEM`, `waitlistEndReason = AUTO_LEFT_OVERLAP`;
   - document buổi tương ứng bỏ số vé khỏi `waitlistTickets` và giảm `waitlistCount`.

Thông báo `DROPIN_SPOT_OPENED` gửi cho U có thêm `data.autoLeftSessionIds` (theo FR-017).

**Lý do:** spec yêu cầu xử lý "trong cùng lần xử lý". Một transaction Firestore ghi được tối đa 500 document, nên vài buổi trùng giờ là đủ chỗ.

**Index:** cần thêm collection group `registrations (userId, status, sessionStartAt)`. Index này spec 080 đã có cho tab "Sắp tới".

## R6. Biểu diễn lượt chờ đã kết thúc mà không thêm enum (FR-014, FR-015)

**Quyết định:** giữ 4 trạng thái của đặc tả CSDL (spec 112 FR-011). Lượt chờ kết thúc mà không được nhận thì chuyển `CANCELLED` và ghi trường mới `waitlistEndReason` 🔒:

| `waitlistEndReason` | `cancelledBy` | Nhãn trong "Đã qua" |
|---|---|---|
| `LEFT` | `USER` | Đã rời hàng chờ |
| `AUTO_LEFT_OVERLAP` | `SYSTEM` | Đã rời hàng chờ (trùng giờ) |
| `CLOSED` | `SYSTEM` | Không được nhận chỗ |
| `SESSION_CANCELLED` | `OWNER` | Buổi bị hủy |

`RegistrationOutcomeResolver` của spec 080 ưu tiên đọc `waitlistEndReason` khi trường này có giá trị.

## R7. Đóng hàng chờ 30 phút trước giờ bắt đầu (FR-014, AC 4.1)

**Quyết định:** Edge Function `dropin-close`, gọi bằng **pg_cron mỗi phút** (constitution: tác vụ định kỳ dùng pg_cron). Mỗi lần chạy:
1. Truy vấn `dropInSessions where status in [OPEN, FULL], startAt ≤ now + 30 phút`. Với buổi có `waitlistCount > 0`, chạy transaction:
   - mọi lượt `WAITLISTED` chuyển `CANCELLED`, `cancelledBy = SYSTEM`, `waitlistEndReason = CLOSED`;
   - `waitlistTickets = []`, `waitlistCount = 0`.

   Sau khi commit, gửi `DROPIN_WAITLIST_CLOSED` cho từng người.
2. Truy vấn `dropInSessions where status in [OPEN, FULL], endAt < now` và chuyển `status = COMPLETED`. Việc này spec 080 (R14) đã giao cho cron của 081.

**Vì sao không bị trễ mốc:** cron chạy mỗi phút nên có thể trễ tới 1 phút. Để chắc chắn, `promoteFromWaitlist` và `register-drop-in` (khi `joinWaitlist`) **tự kiểm tra mốc 30 phút trong transaction** (giống cách constitution yêu cầu kiểm tra `holdExpiresAt` trong transaction). Do đó dù cron chưa chạy, cũng không có ai được đẩy lên hay vào hàng chờ sau mốc.

**Đã cân nhắc:** *Không có cron, suy ra trạng thái khi đọc*: không gửi được `DROPIN_WAITLIST_CLOSED` đúng lúc (FR-017). Loại.

## R8. Hủy được hoàn 100% trong 30 phút khi được đẩy lên sát giờ (FR-011)

**Quyết định:** mở rộng `computeRefund` của spec 080. Với lượt đã thanh toán (`paymentStatus == PAID`):

```
if promotedAt != null
   && (session.startAt − promotedAt) < 240 phút
   && (serverNow − promotedAt) ≤ 30 phút:
       refund = amount, reason = PROMOTED_GRACE
```

Trường hợp khác giữ nguyên quy tắc của spec 080. Bản `dryRun` hiện lý do: "Bạn vừa được nhận từ hàng chờ, hủy trong 30 phút được hoàn 100%".

## R9. Phản ứng với thao tác của chủ sân (spec 112)

| Thao tác của D | Hàm C cung cấp | Hiệu ứng |
|---|---|---|
| Tăng sức chứa | `afterSpotsReleased(tx, session, serverNow)` | Đẩy người chờ lên tối đa số chỗ tăng thêm (AC 2.5) |
| Giảm sức chứa | (không gọi) | Hàng chờ không đổi (Edge Cases) |
| Hủy buổi | `closeWaitlistInTx(tx, session, "SESSION_CANCELLED")` | Mọi lượt chờ chuyển "Buổi bị hủy"; D gửi `DROPIN_WAITLIST_CLOSED` (AC 4.2) |

## R10. Rời hàng chờ và bị đẩy lên cùng lúc (FR-013)

**Quyết định:** `cancel-drop-in` đọc lại đăng ký trong transaction:
- Còn `WAITLISTED` → rời hàng chờ: `CANCELLED`, `waitlistEndReason = LEFT`, xóa số vé, không phát sinh tiền.
- Đã là `REGISTERED` và `promotedAt` có giá trị, mà request gửi `expectWaitlisted: true` → trả `409 ALREADY_PROMOTED`. App báo "Bạn đã được nhận vào buổi" và mở màn hình vé.

Cờ `expectWaitlisted` giúp không biến nhầm một lần bấm "Rời hàng chờ" thành hủy đăng ký có thể mất tiền.

**Lý do:** hai transaction cùng tranh chấp document buổi nên chỉ một bên thắng. Phía thua đọc lại dữ liệu mới nhất và nhận lỗi rõ ràng.

## R11. Điều kiện vào hàng chờ (FR-001)

**Quyết định:** `register-drop-in` với `joinWaitlist: true` kiểm tra:
- buổi đang `FULL`;
- `serverNow < startAt − 30 phút`;
- cơ sở `ACTIVE`;
- người gọi không phải chủ buổi;
- chưa có đăng ký hay lượt chờ còn hiệu lực.

Nếu buổi đang `OPEN` → `409 SESSION_HAS_SPOTS` kèm `remaining`. App chuyển người chơi về luồng đăng ký thường (AC 1.5).

**Giới hạn đã biết:** buổi còn 1 chỗ mà nhóm 2 người muốn đi thì không đăng ký được, mà cũng chưa vào hàng chờ được (vì buổi chưa "đủ người"). Đây là đúng chữ của FR-001. Có thể đề xuất nới thành "còn ít chỗ hơn số người của nhóm" trong lần clarify sau. Không đổi ở bản này.

## R12. Thông báo (FR-017)

| type | Phát từ | Người nhận | `data` |
|---|---|---|---|
| `DROPIN_SPOT_OPENED` | Sau commit của `cancel-drop-in`, Edge Function sửa sức chứa của D, `delete-account` | Người được đẩy lên | `sessionId`, `autoLeftSessionIds` |
| `DROPIN_WAITLIST_CLOSED` | `dropin-close` (lý do `CLOSED`); Edge Function hủy buổi của D (lý do `SESSION_CANCELLED`) | Người đang chờ | `sessionId`, `reason` |

Danh sách người nhận lấy từ kết quả mà các hàm dùng chung trả về sau khi commit (`promoted[]`, `closed[]`). Vào hàng chờ và tự rời hàng chờ không gửi thông báo (đặc tả CSDL mục 4.13).

## R13. Kiểm thử đồng thời (SC-003)

**Quyết định:** test `deno test` trên Firebase Emulator cho buổi 10 chỗ đã đủ và 10 người chờ có `partySize` lẫn lộn (1–3). Bắn đồng thời 5 lượt hủy và lặp 20 lần. Mỗi lần kiểm tra:
- `registeredCount ≤ capacity`;
- không lượt nào có `promotedAt` bị ghi hai lần;
- thứ tự đẩy lên đúng thứ tự `waitlistTicket` (bỏ qua lượt không vừa);
- `waitlistTickets` khớp với tập lượt còn `WAITLISTED`.

Có thêm test cho đúng tình huống AC 2.1/2.2 (A 2 người, B 1 người) và test rời hàng chờ cùng lúc bị đẩy lên (R10).
