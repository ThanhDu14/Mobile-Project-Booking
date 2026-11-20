# Spec 081 (hàng chờ vãng lai) – việc cần thành viên khác làm

C đã cài xong hàng chờ ở `supabase/functions/_shared/waitlist.ts`, `register-drop-in`, `cancel-drop-in`, `dropin-close`. Các việc dưới đây nằm trong file của người khác nên C không tự sửa. Mỗi mục có prompt dán thẳng cho AI của người nhận.

| # | Người nhận | Việc | Ảnh hưởng nếu chưa làm |
|---|---|---|---|
| 1 | A (011) | Cắm `leaveWaitlistInTx` vào `delete-account` | Người đang chờ mà xóa tài khoản: `confirm` trả 500 ở bước rời hàng chờ |
| 2 | D (112) | `owner-cancel-session` dùng `closeWaitlistInTx` của `_shared/waitlist.ts` | Hủy buổi không đóng hàng chờ đúng cách (bản cũ trong `dropin.ts`) |
| 3 | D (112) | `owner-update-session` tăng sức chứa thì đẩy người chờ lên | Tăng chỗ nhưng người chờ không được nhận |
| 4 | Cả nhóm (người deploy) | Bật cron cho `dropin-close` | Hàng chờ không tự đóng lúc T−30, buổi không chuyển `COMPLETED` |

## 1. A – `delete-account` rời hàng chờ

`supabase/functions/_shared/account-deletion-helpers.ts` đang để `defaultHelpers = {}`. Chữ ký `leaveWaitlistInTx` trong `_shared/waitlist.ts` khớp sẵn `DeletionHelpers.leaveWaitlistInTx` (đã `deno check`), không cần bọc:

```ts
import { leaveWaitlistInTx } from "./waitlist.ts";
export const defaultHelpers: DeletionHelpers = { leaveWaitlistInTx /*, ...helper khác */ };
```

> **Prompt cho AI của A:** Trong `supabase/functions/_shared/account-deletion-helpers.ts`, import `leaveWaitlistInTx` từ `./waitlist.ts` (spec 081 của C) và thêm vào `defaultHelpers`. Chữ ký đã khớp `DeletionHelpers.leaveWaitlistInTx`. Chạy lại test `supabase/functions/delete-account` trên Firebase Emulator; thêm một ca người dùng đang `WAITLISTED` xóa tài khoản → lượt chờ thành `CANCELLED`, `cancelledBy = SYSTEM`, `waitlistEndReason = LEFT`, buổi bỏ số vé khỏi `waitlistTickets`.

## 2. D – `owner-cancel-session` dùng hàm đóng hàng chờ mới

`supabase/functions/owner-cancel-session/handler.ts` đang `writes.push(closeWaitlistInTx(sessionId, "SESSION_CANCELLED"))` với bản cũ trong `../_shared/dropin.ts` (trả một `Write` ghi `waitlistStatus`/`closeReason` lên buổi, không đụng lượt chờ nào; app không đọc 2 trường này). Bản đúng trong `_shared/waitlist.ts`:

```ts
closeWaitlistInTx(tx: WaitlistTx, session: SessionDoc, reason: "CLOSED" | "SESSION_CANCELLED"): Promise<{ closed: string[] }>
```

- Tự `tx.query` các lượt `WAITLISTED` → `CANCELLED`, `cancelledBy = OWNER`, `waitlistEndReason = SESSION_CANCELLED`; tự ghi buổi `waitlistTickets = []`, `waitlistCount = 0` (các trường khác của buổi do người gọi ghi).
- Cần `tx` có `query` (`WaitlistTx`) và `session.id` (document đọc bằng `tx.get` KHÔNG có `id` → phải truyền `{ ...session, id: sessionId }`, thiếu thì ném "session.id là bắt buộc").
- KHÔNG gọi qua `bufferedTx` của handler: nó không có `query` và bỏ mọi ghi vào `sessionPath`, nên 2 trường trên bị mất.

> **Prompt cho AI của D:** Trong `supabase/functions/owner-cancel-session/handler.ts`: (1) bỏ dòng `closeWaitlistInTx(sessionId, "SESSION_CANCELLED")` trong `writes.push(...)` và bỏ import bản cũ từ `../_shared/dropin.ts`; (2) import `closeWaitlistInTx` từ `../_shared/waitlist.ts` (spec 081 của C) và gọi `const { closed } = await closeWaitlistInTx(tx, { ...session, id: sessionId }, "SESSION_CANCELLED")` bằng `tx` THẬT của transaction (không phải `bufferedTx`), sau khi đã đọc xong dữ liệu; trả `closed` ra khỏi callback `runTransaction`; (3) thêm `notify?` vào `OwnerCancelSessionDeps` (mặc định `sendDropInNotification`, giống `cancel-drop-in`) để test chèn được, rồi sau commit, với từng uid trong `closed`, gọi `sendDropInNotification` (`../_shared/dropin-notify.ts`) với `{ type: "DROPIN_WAITLIST_CLOSED", userId: uid, data: { sessionId, reason: "SESSION_CANCELLED" } }` (giống `dropin-close`, ở đó `reason: "CLOSED"`), lỗi gửi chỉ ghi log. Đọc `closeWaitlistInTx` và `dropin-close/handler.ts` (đang dùng đúng hàm này) trước khi sửa. Sửa test cũ trong `owner-update-session/handler.test.ts` (ca "owner cancel ... waitlist closes", đang assert `waitlistStatus === "CLOSED"` của bản cũ) sang các trường mới. Thêm test trên Emulator: buổi có 2 lượt `WAITLISTED` bị hủy → cả hai `CANCELLED` với `waitlistEndReason = SESSION_CANCELLED`, buổi `waitlistTickets = []`, `waitlistCount = 0`, `status = CANCELLED`.

## 3. D – tăng sức chứa thì đẩy người chờ lên

`supabase/functions/owner-update-session/handler.ts` chỉ tính lại `status` theo `capacity`, chưa gọi `promoteFromWaitlist` (`_shared/waitlist.ts`):

```ts
promoteFromWaitlist(tx: WaitlistTx, session: SessionDoc, serverNow: Date): Promise<{ promoted: { uid: string; autoLeftSessionIds: string[] }[]; sessionPatch: Record<string, unknown> }>
```

- Gọi SAU khi đã đặt giá trị mới lên object `session` (`session.capacity = input.capacity`, và `startAt` mới nếu đổi giờ) và có `session.id`; TRƯỚC khi ghi buổi. Hàm đọc `session.capacity` để tính chỗ trống, không đặt thì không đẩy ai.
- Hàm tự bỏ qua khi hàng chờ đã đóng (`serverNow ≥ startAt − 30 phút`) hoặc buổi không `OPEN`/`FULL`, nên gọi luôn mỗi khi `capacity` tăng là đủ.
- Hàm KHÔNG ghi document buổi: người gọi gộp `sessionPatch` (`registeredCount`, `status`, có thể `waitlistTickets`, `waitlistCount`) vào đúng một lần ghi buổi cùng `capacity`.

> **Prompt cho AI của D:** Trong `supabase/functions/owner-update-session/handler.ts`, dời `const now = new Date()` (hiện ở ngay trước `tx.write` đầu tiên) lên trước chỗ gọi `promoteFromWaitlist`, tức sau các lần đọc và trước mọi lần ghi; khi `capacity` mới lớn hơn cũ: đặt `session.capacity = input.capacity` (và `startAt` mới nếu có) trên object buổi đã đọc trong transaction, gọi `const { promoted, sessionPatch } = await promoteFromWaitlist(tx, { ...session, id: sessionId }, serverNow)` của `../_shared/waitlist.ts` (spec 081 của C) bằng `tx` thật, rồi ghi buổi MỘT lần gồm các trường sửa của chủ sân + `sessionPatch`, trong đó `...sessionPatch` phải đặt SAU dòng `status: registeredCount >= input.capacity ? "FULL" : "OPEN"` hiện có (dòng đó dùng `registeredCount` cũ) hoặc bỏ dòng đó khi có `sessionPatch`. Sau commit, với từng `p` trong `promoted`, gọi `sendDropInNotification` (`../_shared/dropin-notify.ts`) với `{ type: "DROPIN_SPOT_OPENED", userId: p.uid, data: { sessionId, autoLeftSessionIds: p.autoLeftSessionIds } }` (giống `cancel-drop-in/handler.ts`), lỗi gửi chỉ ghi log. Đọc `promoteFromWaitlist` và `cancel-drop-in/handler.ts` trước khi sửa. Thêm test trên Emulator: buổi `FULL` 10/10 có 3 lượt chờ (seed cả `waitlistTickets = [1, 2, 3]`, `waitlistCount = 3` trên buổi và `waitlistTicket` trên từng lượt), mỗi lượt `partySize = 1`, tăng lên 12 → 2 lượt đầu thành `REGISTERED` có `promotedAt`, lượt thứ 3 vẫn chờ, buổi `waitlistCount = 1`.

Ghi chú cho D (spec 100): thông báo vãng lai dùng envelope `{ type, userId, data }` (`_shared/dropin-notify.ts`), còn nhóm/chat dùng `{ type, recipientUid, data }` (xem `specs/091-chat/handoffs/README.md`). Khi chốt `send-notification`, chọn một tên trường và báo C sửa phía còn lại.

## 4. Cả nhóm – bật cron `dropin-close`

Migration `supabase/migrations/20261112100000_cron_dropin_close.sql` gọi `dropin-close` mỗi phút qua `pg_cron` + `pg_net`, đọc URL và secret từ Vault.

> **Prompt cho AI của người deploy:** Trên project Supabase thật: bật extension `pg_cron`, `pg_net`; tạo Vault secret `project_url` (`https://<project-ref>.supabase.co`) và `cron_secret`; `supabase secrets set CRON_SECRET=<cùng giá trị>`; deploy function `dropin-close` rồi chạy migration. Kiểm tra `select * from cron.job` có job của `dropin-close` và log function có request mỗi phút trả 200.
