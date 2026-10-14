# Data Model: Hàng chờ buổi vãng lai (spec 081)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md) · **Nền tảng**: [data-model 080](../080-drop-in-session/data-model.md)

Spec 081 **không tạo collection mới**. Spec này dùng lại `dropInSessions` và `dropInSessions/{id}/registrations/{uid}` của spec 080.

- 🔒 nghĩa là chỉ server (Edge Function) ghi.
- **[MỚI]** là trường mới so với data-model 080 và đặc tả CSDL.

---

## 1. Trường thêm vào `dropInSessions/{sessionId}`

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `waitlistNextTicket` 🔒 **[MỚI]** | Int | Bộ đếm cấp số vé chờ, bắt đầu từ 1, chỉ tăng |
| `waitlistTickets` 🔒 **[MỚI]** | Array\<Int> | Số vé của các lượt đang `WAITLISTED`, tăng dần. Chỉ có số, không có danh tính (FR-006) |
| `waitlistCount` 🔒 **[MỚI]** | Int | `= waitlistTickets.size`; hiện "N người đang chờ" |

Mọi thao tác đổi hàng chờ (vào, rời, đẩy lên, tự rời do trùng giờ, đóng) **đều ghi vào document buổi trong cùng transaction**. Nhờ đó mọi thao tác hàng chờ đều tranh chấp trên document buổi và được xếp tuần tự ([research R3](research.md#r3-đẩy-người-chờ-lên-trong-cùng-transaction-tạo-ra-chỗ-trống-fr-007-fr-008-fr-009)).

**Ràng buộc luôn đúng:**
- `waitlistTickets` chứa đúng số vé của các lượt `WAITLISTED` thuộc buổi.
- Hàng chờ chỉ có người khi buổi `FULL`, hoặc khi buổi `OPEN` nhưng chỗ trống nhỏ hơn số người của mọi lượt đang chờ.
- Sau mốc `startAt − 30 phút` và sau khi `dropin-close` chạy: `waitlistTickets = []`.

## 2. Trường thêm vào `registrations/{uid}` (lượt chờ)

Các trường dùng chung với đăng ký (`userId`, `sessionId`, `venueId`, `sessionStartAt`, `sessionEndAt`, `partySize`, `paymentMethod`, `pricePerPerson`, `amount`, `requestId`…) xem [data-model 080 mục 2](../080-drop-in-session/data-model.md#2-dropinsessionssessionidregistrationsuid--c).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `status` 🔒 | String | `WAITLISTED` khi đang chờ; `REGISTERED` sau khi được đẩy lên; `CANCELLED` khi kết thúc chờ |
| `waitlistedAt` 🔒 | Timestamp | Server ghi lúc vào hàng chờ (hiển thị) |
| `waitlistTicket` 🔒 **[MỚI]** | Int | Số vé chờ, quyết định thứ tự (FR-004) |
| `pricePerPerson`, `amount` 🔒 | Long | **Chốt lúc vào hàng chờ** (Edge Cases: giá đổi khi đang chờ) |
| `paymentStatus` 🔒 | String? | `null` khi đang chờ; `PAID`/`UNPAID` khi được đẩy lên |
| `qrToken`, `ticketCode` 🔒 | String? | `null` khi đang chờ; được sinh lúc đẩy lên |
| `promotedAt` 🔒 **[MỚI]** | Timestamp? | Có giá trị thì hiện nhãn "Được nhận từ hàng chờ"; dùng cho hạn 30 phút hoàn 100% (FR-011) |
| `waitlistEndReason` 🔒 **[MỚI]** | String? | `LEFT`, `AUTO_LEFT_OVERLAP`, `CLOSED`, `SESSION_CANCELLED` (khi lượt chờ kết thúc mà không được nhận) |

### Máy trạng thái lượt chờ

```
(chưa có) / CANCELLED ──register-drop-in(joinWaitlist)──▶ WAITLISTED

WAITLISTED ──promoteFromWaitlist (hủy / tăng sức chứa, trước T−30 phút)──▶ REGISTERED (promotedAt)
                                                                           └─ tiếp theo theo spec 080
WAITLISTED ──cancel-drop-in (chủ lượt)──────────────▶ CANCELLED (LEFT, USER)
WAITLISTED ──dropin-close (T−30 phút)───────────────▶ CANCELLED (CLOSED, SYSTEM)
WAITLISTED ──hủy buổi (D)───────────────────────────▶ CANCELLED (SESSION_CANCELLED, OWNER)
WAITLISTED ──được đẩy lên buổi trùng giờ khác───────▶ CANCELLED (AUTO_LEFT_OVERLAP, SYSTEM)
```

Một lượt đã `REGISTERED` thì không quay về `WAITLISTED`. Thao tác rời hàng chờ với lượt đã được đẩy lên bị từ chối (`ALREADY_PROMOTED`, FR-013).

## 3. Quy tắc kiểm tra

### Vào hàng chờ: `register-drop-in` với `joinWaitlist: true` (trong transaction)

| Quy tắc | Lỗi | FR |
|---|---|---|
| Đã đăng nhập, `accountStatus == ACTIVE` | `UNAUTHENTICATED` / `ACCOUNT_LOCKED` | FR-001 |
| `1 ≤ partySize ≤ 4`, `paymentMethod ∈ {MOCK_PAY, AT_VENUE}`, `requestId` là UUID | `INVALID_ARGUMENT` | FR-002 |
| Buổi tồn tại, không `CANCELLED`/`COMPLETED` | `SESSION_NOT_FOUND` / `SESSION_CANCELLED` | FR-001 |
| `session.status == FULL` | `SESSION_HAS_SPOTS` (kèm `remaining`) | FR-001, AC 1.5 |
| `serverNow < startAt − 30 phút` | `WAITLIST_CLOSED` | FR-001, AC 4.3 |
| Cơ sở `ACTIVE`; người gọi không phải chủ buổi | `VENUE_INACTIVE` / `OWN_SESSION` | FR-001, Edge Cases |
| Đăng ký hiện có: `WAITLISTED` cùng `requestId` → trả bản cũ (idempotent); `WAITLISTED` khác `requestId` → `ALREADY_WAITLISTED`; `REGISTERED`/`CHECKED_IN` → `ALREADY_REGISTERED`; `CANCELLED` → ghi đè và xếp cuối hàng | — | FR-003, AC 3.3 |

Khi hợp lệ, trong cùng `commit`:
- **Lượt chờ:**
  - `status = WAITLISTED`, `waitlistTicket = waitlistNextTicket`, `waitlistedAt = serverNow`;
  - chốt `pricePerPerson`, `amount`, `paymentMethod`;
  - `paymentStatus = null`, `qrToken = null`, `ticketCode = null`, `waitlistEndReason = null`, `promotedAt = null`.
- **Buổi:** `waitlistNextTicket += 1`, thêm số vé vào `waitlistTickets`, `waitlistCount += 1`.

### Rời hàng chờ: `cancel-drop-in` khi lượt đang `WAITLISTED`

| Quy tắc | Kết quả | FR |
|---|---|---|
| Đăng ký của chính người gọi | `NOT_FOUND` nếu không có | FR-012, AC 3.4 |
| `status == WAITLISTED` | `CANCELLED`, `cancelledBy = USER`, `waitlistEndReason = LEFT`; bỏ số vé khỏi buổi; không phát sinh tiền | FR-012, SC-005 |
| `status == REGISTERED`, có `promotedAt`, request gửi `expectWaitlisted: true` | `409 ALREADY_PROMOTED` | FR-013, AC 3.2 |
| Đã `CANCELLED` với cùng `cancelRequestId` | Trả lại kết quả cũ (idempotent) | — |

### Đẩy lên: `promoteFromWaitlist(tx, session, serverNow)`

Thuật toán xem [research R3](research.md#r3-đẩy-người-chờ-lên-trong-cùng-transaction-tạo-ra-chỗ-trống-fr-007-fr-008-fr-009). Với mỗi lượt được đẩy lên:
- ghi các trường ở [R4](research.md#r4-lượt-chờ-được-đẩy-lên-trở-thành-đăng-ký-chính-thức-fr-010);
- tự cho rời các lượt chờ trùng giờ của cùng người ([R5](research.md#r5-tự-rời-hàng-chờ-của-các-buổi-trùng-giờ-fr-010a)).

Hàm trả về `promoted: [{ uid, sessionId, autoLeftSessionIds }]` để gửi thông báo sau khi commit.

### Đóng hàng chờ: `closeWaitlistInTx(tx, session, reason)`

`reason ∈ {CLOSED, SESSION_CANCELLED}`. Mọi lượt `WAITLISTED` chuyển `CANCELLED`:
- `cancelledBy = SYSTEM` (khi `CLOSED`) hoặc `OWNER` (khi `SESSION_CANCELLED`);
- `waitlistEndReason = reason`.

Buổi được ghi `waitlistTickets = []`, `waitlistCount = 0`. Hàm trả `closed: [uid]`.

### Hoàn tiền: `computeRefund` (mở rộng spec 080)

Thêm nhánh `PROMOTED_GRACE`: hoàn 100% khi đủ các điều kiện sau ([research R8](research.md#r8-hủy-được-hoàn-100-trong-30-phút-khi-được-đẩy-lên-sát-giờ-fr-011)):
- `promotedAt` có giá trị;
- `startAt − promotedAt < 240 phút`;
- `serverNow − promotedAt ≤ 30 phút`.

## 4. Index cần thêm vào `firestore.indexes.json`

| Collection | Trường | Dùng cho |
|---|---|---|
| `registrations` (collection, trong buổi) | `status`, `waitlistTicket` | Lấy danh sách chờ theo thứ tự trong `promoteFromWaitlist` và `closeWaitlistInTx` **[MỚI]** |
| `registrations` (collection group) | `userId`, `status`, `sessionStartAt` | Tìm lượt chờ trùng giờ (R5); index này spec 080 đã có |
| `dropInSessions` | `status`, `startAt` | `dropin-close` tìm buổi tới mốc T−30 phút; index này spec 080 đã có |
| `dropInSessions` | `status`, `endAt` | `dropin-close` chuyển buổi đã qua sang `COMPLETED` **[MỚI]** |

## 5. Mô hình phía app (Kotlin, mở rộng `feature/dropin/domain`)

| Lớp | Thay đổi |
|---|---|
| `DropInSession` | Thêm `waitlistTickets: List<Int>`, `waitlistCount: Int`, `isWaitlistOpen(now) = status == FULL && now < startAt − 30 phút` |
| `DropInRegistration` | Thêm `waitlistTicket: Int?`, `promotedAt: Instant?`, `waitlistEndReason`; `isFromWaitlist = promotedAt != null` |
| `WaitlistPosition` **[MỚI]** | `position: Int` (`indexOf + 1`), `total: Int`; tính từ `session.waitlistTickets` và `registration.waitlistTicket` |
| `RegistrationOutcome` | Thêm `WAITING`, `LEFT_WAITLIST`, `NOT_ADMITTED`; `SESSION_CANCELLED` dùng chung |
| `RegisterRequest` | Thêm `joinWaitlist: Boolean` |
| `CancelQuote.reason` | Thêm `PROMOTED_GRACE`, `WAITLIST_LEAVE` (luôn 0 đ) |

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.9 `dropInSessions`:** thêm `waitlistNextTicket` 🔒, `waitlistTickets` 🔒, `waitlistCount` 🔒.
2. **Mục 4.9 `registrations`:**
   - thêm `waitlistTicket` 🔒, `promotedAt` 🔒, `waitlistEndReason` 🔒;
   - ghi rõ lượt chờ dùng chung document, giá được chốt lúc vào hàng chờ.
3. **Mục 6:** thêm index `registrations (status, waitlistTicket)` và `dropInSessions (status, endAt)`.
4. **Mục 8a:**
   - thêm `dropin-close` (pg_cron mỗi phút): đóng hàng chờ lúc T−30 phút, chuyển buổi đã qua sang `COMPLETED`;
   - ghi rõ `register-drop-in`/`cancel-drop-in` xử lý cả vào và rời hàng chờ, và đẩy người lên trong transaction.
