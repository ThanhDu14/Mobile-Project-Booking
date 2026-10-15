# Contract: Edge Functions `register-drop-in`, `cancel-drop-in`

**Vị trí mã**:
- `supabase/functions/register-drop-in/index.ts`
- `supabase/functions/cancel-drop-in/index.ts`
- Logic dùng chung: `supabase/functions/_shared/dropin.ts`
- Truy cập Firestore: `supabase/functions/_shared/firestore.ts` (B)

**Gọi từ app**: `supabase.functions.invoke("<tên>", body)` với header `Authorization: Bearer <Firebase ID token>`. Phương thức `POST`, kiểu `application/json`.

**Xác thực**: kiểm tra chữ ký, `aud` và `exp` của Firebase ID token, lấy `uid = sub`. Token thiếu hoặc sai → `401 UNAUTHENTICATED`. Đọc `users/{uid}.accountStatus`; nếu `LOCKED` → `403 ACCOUNT_LOCKED`.

**Transaction**: chạy theo các bước `beginTransaction`, đọc, kiểm tra, `commit`. Khi gặp `ABORTED` thì thử lại tối đa 8 lần với backoff 50–400 ms có ngẫu nhiên. Hết số lần thử → `503 BUSY`.

**Thời gian**: mọi mốc thời gian dùng `serverNow` (giờ của Edge Function). Không nhận thời gian từ client.

---

## 1. `register-drop-in`

### Request

```json
{
  "sessionId": "s_8f2a…",
  "partySize": 2,
  "paymentMethod": "MOCK_PAY",
  "requestId": "3b9e2c1a-…-uuid"
}
```

Quy tắc kiểm tra và các thao tác ghi: xem [data-model.md](../data-model.md#quy-tắc-kiểm-tra-của-register-drop-in-trong-transaction).

### Response `201` (đăng ký mới), `200` (trả lại kết quả cũ khi gửi trùng `requestId`)

```json
{
  "registration": {
    "sessionId": "s_8f2a…",
    "status": "REGISTERED",
    "partySize": 2,
    "paymentMethod": "MOCK_PAY",
    "paymentStatus": "PAID",
    "pricePerPerson": 60000,
    "amount": 120000,
    "ticketCode": "DI-7K3QXZ9M",
    "registeredAt": "2026-10-20T11:00:00Z"
  },
  "session": { "registeredCount": 10, "capacity": 12, "status": "OPEN" }
}
```

Response không trả `qrToken`. App đọc token từ document đăng ký của mình qua Firestore, vì Rules chỉ cho chính người đó đọc. Cách này giúp token không nằm trong log của HTTP client.

**Sau khi commit:** gọi `send-notification` (D) với `{ type: "DROPIN_REGISTERED", userId: uid, data: { sessionId } }`. Chỉ gọi khi là đăng ký mới (`201`). Nếu gọi lỗi thì ghi log rồi bỏ qua.

## 2. `cancel-drop-in`

### Request

```json
{
  "sessionId": "s_8f2a…",
  "requestId": "a71c…-uuid",
  "dryRun": false
}
```

- `dryRun: true`: chỉ tính và trả `refundAmount`/`reason`, không ghi gì. App dùng chế độ này để hiện "Bạn được hoàn 100% (X đ)" / "Không được hoàn tiền" / "Không mất phí" trước khi người chơi xác nhận (FR-017).
- `dryRun: false`: hủy thật. Quy tắc kiểm tra xem [data-model.md](../data-model.md#quy-tắc-kiểm-tra-của-cancel-drop-in).

### Response `200`

```json
{
  "registration": { "status": "CANCELLED", "paymentStatus": "REFUNDED", "refundAmount": 120000, "cancelledAt": "…" },
  "quote": { "refundAmount": 120000, "reason": "EARLY" },
  "session": { "registeredCount": 8, "capacity": 12, "status": "OPEN" }
}
```

`reason`:
- `EARLY`: hủy trước giờ bắt đầu từ 240 phút trở lên.
- `LATE`: hủy dưới 240 phút, không hoàn tiền.
- `SESSION_CHANGED`: chủ sân đã đổi buổi, luôn hoàn 100%.
- `AT_VENUE`: lượt trả tại sân, không có tiền phải hoàn.

Không gửi thông báo vì người chơi tự hủy (đặc tả CSDL mục 4.13).

---

## 3. Hàm dùng chung trong `_shared/dropin.ts`

Các spec khác gọi những hàm này **bên trong transaction của chính họ**:

```ts
// Tính trường suy ra khi chủ sân tạo hoặc sửa buổi (D, spec 112)
export function deriveSessionFields(input: { startAt: Date; venue: VenueDoc; districtName: string }):
  { timeSlot: "MORNING" | "NOON" | "AFTERNOON" | "EVENING"; date: string; venueSnapshot: VenueSnapshot }

// Tính số tiền hoàn: dùng cho cancel-drop-in, hủy buổi (D) và delete-account (A)
export function computeRefund(reg: RegistrationDoc, session: SessionDoc, serverNow: Date):
  { refundAmount: number; reason: "EARLY" | "LATE" | "SESSION_CHANGED" | "AT_VENUE" }

// Hủy một đăng ký và trả chỗ trong transaction tx của người gọi
export async function cancelRegistrationInTx(
  tx: Transaction, sessionId: string, uid: string,
  cancelledBy: "USER" | "OWNER" | "SYSTEM", opts?: { forceFullRefund?: boolean }
): Promise<{ refundAmount: number }>

// Hook sau khi trả chỗ. Spec 080 để trống; spec 081 thay bằng logic đẩy người từ hàng chờ lên
export async function afterSpotsReleased(tx: Transaction, session: SessionDoc): Promise<void>

// Sinh mã
export function newQrToken(): string    // 32 byte, base64url
export function newTicketCode(): string // "DI-" + 8 ký tự [A-HJ-NP-Z2-9]
```

## 4. Mã lỗi

Body lỗi có dạng `{ "error": { "code": "SESSION_FULL", "message": "...", "remaining": 1 } }`.

| HTTP | `error.code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `INVALID_ARGUMENT` | `partySize` ngoài 1–4, sai `paymentMethod`, `requestId` không phải UUID | Lỗi theo trường (thường đã chặn ở app) |
| 401 | `UNAUTHENTICATED` | Token thiếu hoặc hết hạn | Làm mới token rồi thử lại 1 lần; vẫn lỗi thì chuyển sang đăng nhập |
| 403 | `ACCOUNT_LOCKED` | `accountStatus == LOCKED` | "Tài khoản đang bị khóa" |
| 403 | `OWN_SESSION` | Chủ sân đăng ký buổi của chính mình | "Bạn không thể đăng ký buổi do mình mở" |
| 404 | `SESSION_NOT_FOUND` / `NOT_FOUND` | Không có buổi hoặc không có đăng ký của người gọi | "Không tìm thấy buổi/lượt" |
| 409 | `SESSION_FULL` | Không đủ chỗ (kèm `remaining`) | "Buổi không còn đủ chỗ (còn N chỗ)" |
| 409 | `SESSION_STARTED` | `serverNow ≥ startAt` | "Buổi đã bắt đầu" |
| 409 | `SESSION_CANCELLED` | Buổi `CANCELLED`/`COMPLETED` | "Buổi đã bị hủy" |
| 409 | `VENUE_INACTIVE` | Cơ sở bị ẩn hoặc khóa | "Cơ sở tạm ngừng hoạt động" |
| 409 | `ALREADY_REGISTERED` | Đã có đăng ký còn hiệu lực với `requestId` khác | Mở vé hiện có |
| 409 | `ALREADY_WAITLISTED` | Đang ở hàng chờ (spec 081) | Mở lượt chờ |
| 409 | `NOT_CANCELLABLE` | Đăng ký đã `CHECKED_IN` | "Lượt đã check-in, không hủy được" |
| 503 | `BUSY` | Hết số lần thử lại transaction | App tự gửi lại cùng `requestId`, tối đa 2 lần; sau đó hiện lỗi kèm nút thử lại |
| 500 | `INTERNAL` | Lỗi khác | "Có lỗi xảy ra, vui lòng thử lại" + nút thử lại |

Mất mạng ở app (không có response): giữ nguyên lựa chọn và cho thử lại với **cùng `requestId`** (AC 2.8).

## 5. Mở rộng Edge Function `review` (spec 070) cho buổi vãng lai

Action `create` nhận thêm dạng:

```json
{ "action": "create", "sessionId": "s_8f2a…", "ratings": { "court": 5, "cleanliness": 4, "service": 5 }, "comment": "…", "photoPaths": ["<uid>/s_8f2a…_<uid>/a1.jpg"] }
```

- `reviewId = {sessionId}_{uid}`, `targetType = DROP_IN`.
- Điều kiện tạo: xem [research R10](../research.md#r10-đánh-giá-buổi-chơi-fr-022-fr-023).
- Mã lỗi mới: `409 NOT_CHECKED_IN` → "Bạn chỉ đánh giá được buổi đã tham gia".
- Các action `update`, `delete`, `reply`, `discardPhotos` dùng nguyên logic của [contract 070](../../070-review-favorite/contracts/edge-function-review.md).
