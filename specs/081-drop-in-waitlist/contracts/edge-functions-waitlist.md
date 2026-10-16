# Contract: hàng chờ trong Edge Functions

**Nền tảng**: [contract Edge Function của spec 080](../../080-drop-in-session/contracts/edge-functions-dropin.md). Phần xác thực, transaction (thử lại tối đa 8 lần) và dạng body lỗi giữ nguyên.

**Vị trí mã:**
- Mở rộng: `supabase/functions/register-drop-in/`, `supabase/functions/cancel-drop-in/`, `supabase/functions/_shared/dropin.ts`.
- Mới: `supabase/functions/_shared/waitlist.ts`, `supabase/functions/dropin-close/`.

---

## 1. Vào hàng chờ: `register-drop-in` với `joinWaitlist: true`

### Request

```json
{ "sessionId": "s_8f2a…", "partySize": 2, "paymentMethod": "AT_VENUE", "requestId": "uuid", "joinWaitlist": true }
```

Quy tắc kiểm tra xem [data-model.md mục 3](../data-model.md#vào-hàng-chờ-register-drop-in-với-joinwaitlist-true-trong-transaction).

### Response `201` (lượt chờ mới), `200` (gửi lại cùng `requestId`)

```json
{
  "registration": { "sessionId": "s_8f2a…", "status": "WAITLISTED", "partySize": 2, "paymentMethod": "AT_VENUE",
                    "pricePerPerson": 60000, "amount": 120000, "waitlistedAt": "…" },
  "waitlist": { "position": 3, "total": 3 }
}
```

Không gửi thông báo vì người chơi tự vào hàng chờ.

**Lưu ý khi đăng ký thường** (không có `joinWaitlist`): nếu buổi đang `FULL`, lỗi `SESSION_FULL` có thêm `waitlistOpen: true|false` để app hiện nút "Vào hàng chờ".

## 2. Rời hàng chờ: `cancel-drop-in` khi lượt đang `WAITLISTED`

### Request

```json
{ "sessionId": "s_8f2a…", "requestId": "uuid", "expectWaitlisted": true, "dryRun": false }
```

- `expectWaitlisted: true`: app gửi khi người chơi bấm "Rời hàng chờ". Nếu lượt đã được đẩy lên thì trả `409 ALREADY_PROMOTED` và **không** hủy đăng ký (FR-013).
- `dryRun: true`: trả `quote.refundAmount = 0`, `reason = WAITLIST_LEAVE`.

### Response `200`

```json
{ "registration": { "status": "CANCELLED", "waitlistEndReason": "LEFT" }, "quote": { "refundAmount": 0, "reason": "WAITLIST_LEAVE" } }
```

## 3. Hủy đăng ký có đẩy người chờ lên: `cancel-drop-in` (lượt `REGISTERED`)

Request giữ như spec 080. Bên trong cùng transaction:
1. `cancelRegistrationInTx(...)`;
2. `afterSpotsReleased(tx, session, serverNow)`, chính là `promoteFromWaitlist`.

**Sau khi commit:** gửi `DROPIN_SPOT_OPENED` cho từng người được đẩy lên, với `data = { sessionId, autoLeftSessionIds }`.

Response giữ nguyên dạng của spec 080, thêm `"promotedCount": 1`. Danh tính người được đẩy lên không có trong response.

Hoàn tiền: `computeRefund` có thêm lý do `PROMOTED_GRACE` ([research R8](../research.md#r8-hủy-được-hoàn-100-trong-30-phút-khi-được-đẩy-lên-sát-giờ-fr-011)).

## 4. `dropin-close` (pg_cron mỗi phút)

- **Gọi bằng:** `pg_cron` + `net.http_post` tới `/functions/v1/dropin-close`. Header chứa secret `CRON_SECRET`; function từ chối nếu thiếu hoặc sai.
- **Việc làm:** xem [research R7](../research.md#r7-đóng-hàng-chờ-30-phút-trước-giờ-bắt-đầu-fr-014-ac-41).
  - Đóng hàng chờ của các buổi đã tới mốc T−30 phút bằng `closeWaitlistInTx(..., "CLOSED")`, mỗi buổi một transaction.
  - Gửi `DROPIN_WAITLIST_CLOSED` với `data = { sessionId, reason: "CLOSED" }`.
  - Chuyển buổi đã qua `endAt` sang `COMPLETED`.
- **Idempotent:** chạy lại thì không đổi gì, vì `waitlistTickets` đã rỗng và `status` đã là `COMPLETED`.
- **Response `200`:** `{ "closedSessions": 2, "closedEntries": 5, "completedSessions": 3 }`.

## 5. Hàm dùng chung (`_shared/waitlist.ts`)

Các spec khác gọi bên trong transaction của chính họ:

```ts
// Gắn vào hook afterSpotsReleased của spec 080. D gọi sau khi tăng sức chứa (spec 112)
export async function promoteFromWaitlist(tx: Transaction, session: SessionDoc, serverNow: Date):
  Promise<{ promoted: { uid: string; autoLeftSessionIds: string[] }[] }>

// D gọi khi hủy buổi (reason = "SESSION_CANCELLED"); dropin-close gọi khi tới mốc T−30 phút (reason = "CLOSED")
export async function closeWaitlistInTx(tx: Transaction, session: SessionDoc, reason: "CLOSED" | "SESSION_CANCELLED"):
  Promise<{ closed: string[] }>

// delete-account (A) gọi để rời hàng chờ khi người dùng xóa tài khoản
export async function leaveWaitlistInTx(tx: Transaction, sessionId: string, uid: string,
  reason: "LEFT" | "AUTO_LEFT_OVERLAP", cancelledBy: "USER" | "SYSTEM"): Promise<void>
```

## 6. Mã lỗi thêm so với spec 080

| HTTP | `error.code` | Khi nào | App hiển thị |
|---|---|---|---|
| 409 | `SESSION_HAS_SPOTS` | Vào hàng chờ khi buổi còn chỗ (kèm `remaining`) | Chuyển sang màn hình đăng ký thường |
| 409 | `WAITLIST_CLOSED` | Còn ≤ 30 phút hoặc buổi đã hủy | "Hàng chờ đã đóng" |
| 409 | `ALREADY_PROMOTED` | Rời hàng chờ khi đã được đẩy lên | "Bạn đã được nhận vào buổi" → mở vé |
| 409 | `ALREADY_WAITLISTED` | Đã có lượt chờ (khác `requestId`) | Mở lượt chờ |

## 7. Rules

Không cần thêm Rules mới:
- Lượt chờ là document `registrations`, nên Rules của spec 080 (chỉ chủ lượt, chủ cơ sở, admin đọc; client không ghi) đã che đủ FR-006 và SC-004.
- Các trường `waitlist*` trên buổi do server ghi, và Rules của buổi đã là `write: if false`.

Bổ sung 2 ca vào `firestore-tests/test/dropInSessions.test.ts`:

| # | Ca | Kỳ vọng |
|---|---|---|
| W1 | Người chơi đọc lượt `WAITLISTED` của người khác | Bị chặn |
| W2 | Người chơi tự ghi `waitlistTicket`, `waitlistedAt`, `status` hoặc `waitlistTickets` của buổi | Bị chặn |

## 8. Test Edge Function (`deno test`, Firebase Emulator)

| # | Ca | Kỳ vọng | Liên quan |
|---|---|---|---|
| Q1 | 3 người lần lượt vào hàng chờ buổi `FULL` | Vị trí 1, 2, 3; `registeredCount` không đổi | US1 |
| Q2 | Gửi cùng `requestId` 5 lần | 1 lượt chờ, `waitlistCount` tăng 1 lần | AC 1.6, FR-003 |
| Q3 | Vào hàng chờ khi buổi `OPEN` / còn 30 phút / chủ buổi | `SESSION_HAS_SPOTS` / `WAITLIST_CLOSED` / `OWN_SESSION` | FR-001 |
| Q4 | Buổi 10 chỗ; chờ A(2), B(1). Hủy lượt 1 người | B được đẩy lên, A giữ vị trí 1; sau đó hủy lượt 2 người thì A được đẩy lên; `registeredCount` luôn bằng 10 | US2 Independent Test, AC 2.1–2.2 |
| Q5 | Đẩy lên với `MOCK_PAY` / `AT_VENUE` | `PAID` / `UNPAID`, có `qrToken`, `ticketCode`, `promotedAt`; `amount` theo giá lúc vào hàng chờ dù giá đã đổi | AC 2.3, Edge Cases |
| Q6 | **5 lượt hủy đồng thời, 10 người chờ (partySize 1–3), lặp 20 lần** | Không vượt sức chứa; không ai được đẩy lên 2 lần; thứ tự đúng `waitlistTicket` (bỏ qua lượt không vừa); `waitlistTickets` khớp với các lượt `WAITLISTED` | SC-003, FR-008 |
| Q7 | Rời hàng chờ cùng lúc bị đẩy lên (chạy song song 20 lần) | Đúng một kết quả: hoặc `LEFT`, hoặc `REGISTERED` + `ALREADY_PROMOTED`; không bao giờ cả hai | FR-013 |
| Q8 | Người chờ 2 buổi trùng giờ, được đẩy lên buổi 1 | Lượt chờ buổi 2 chuyển `AUTO_LEFT_OVERLAP`; `waitlistTickets` buổi 2 bỏ số vé đó; `autoLeftSessionIds` có buổi 2 | FR-010a, AC 2.7 |
| Q9 | Hủy khi còn 25 phút trước giờ bắt đầu, hàng chờ còn người | Không đẩy ai lên; chỗ trống mở cho đăng ký thường | FR-007, Edge Cases |
| Q10 | `dropin-close` lúc T−30 phút với 2 lượt chờ | Cả 2 chuyển `CLOSED`, `paymentStatus` vẫn `null`; gửi 2 `DROPIN_WAITLIST_CLOSED`; chạy lại không đổi gì | FR-014, SC-005 |
| Q11 | `closeWaitlistInTx(…, "SESSION_CANCELLED")` | Lượt chờ chuyển `SESSION_CANCELLED`, `cancelledBy = OWNER` | AC 4.2 |
| Q12 | Được đẩy lên lúc T−2 giờ, hủy sau 20 phút / sau 40 phút | Hoàn 100% (`PROMOTED_GRACE`) / không hoàn (`LATE`) | FR-011 |
| Q13 | Chủ sân tăng sức chứa thêm 3 (gọi `promoteFromWaitlist`) | Đẩy lên tối đa 3 chỗ theo thứ tự | AC 2.5 |
| Q14 | `dropin-close` không có `CRON_SECRET` | `401` | Bảo mật |
