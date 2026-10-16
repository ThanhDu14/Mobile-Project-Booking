# Contract: Edge Functions `delete-account`, `delete-account-sweeper`, `sync-account-email`

**Vị trí mã:**

| Thành phần | File |
|---|---|
| `delete-account` | `supabase/functions/delete-account/index.ts`, logic thuần ở `handler.ts` |
| `delete-account-sweeper` | `supabase/functions/delete-account-sweeper/index.ts` |
| `sync-account-email` | `supabase/functions/sync-account-email/index.ts` |
| Điều phối các bước xóa (A) | `supabase/functions/_shared/account-deletion.ts` |
| Helper của nhóm khác | `_shared/booking.ts` (B), `_shared/dropin.ts`, `_shared/waitlist.ts`, `_shared/reviews.ts`, `_shared/community.ts` (C), `_shared/venue.ts`, `_shared/owner.ts` (D) |
| Dùng chung từ 010 | `_shared/auth.ts` (thêm kiểm tra `deletionRequestedAt`), `_shared/identity.ts` (thêm `deleteUser`), `_shared/firestore.ts` (B) |

Body lỗi giống các function khác: `{ "error": { "code", "message", ...extra } }`. App gọi bằng `supabase.functions.invoke(...)` qua `invokeWithAuthRetry` của 010, timeout phía app **15 giây** cho `preview`, `sync-account-email`, và **120 giây** cho `confirm`.

---

## 1. `delete-account`

**Xác thực:** `preview` dùng `requireUser(req)` (010). `confirm` dùng `requireUser(req, { allowDeleting: true })` để lần gọi lặp sau khi chốt (đã có `deletionRequestedAt`, hoặc `users/{uid}` đã bị xóa) vẫn đi tiếp tới bước 4 và trả kết quả idempotent; `confirm` kiểm thêm `now − token.auth_time ≤ 300 giây` (research R8).

### 1.1 `preview` (chỉ đọc, FR-022)

Request: `{ "action": "preview" }`

Response `200`:

```json
{
  "impactVersion": "sha256:4b1f…",
  "walletBalance": 50000,
  "myBookings": [
    { "bookingId": "…", "venueName": "Sân cầu lông Hòa Bình", "startAt": "2026-10-20T12:00:00Z",
      "status": "PENDING", "paidAmount": 0, "refundAmount": 0 },
    { "bookingId": "…", "venueName": "Sân Kỳ Hòa Center", "startAt": "2026-10-22T11:00:00Z",
      "status": "CONFIRMED", "paidAmount": 60000, "refundAmount": 60000 }
  ],
  "myDropIns": [ { "sessionId": "…", "venueName": "…", "startAt": "…", "paidAmount": 0, "refundAmount": 0 } ],
  "waitlistCount": 1,
  "ownerImpact": { "venueCount": 2, "guestBookingCount": 7, "guestDropInCount": 3, "guestCount": 9 }
}
```

- "Sắp tới" = đơn `HOLD`, `PENDING`, `CONFIRMED` chưa `checkedInAt`, `endAt > now`; lượt `REGISTERED` của buổi chưa bắt đầu; lượt `WAITLISTED`.
- `refundAmount` tính bằng helper của B (`computeBookingRefund`) và C (`computeRefund` của 080) theo giờ server.
- `ownerImpact = null` khi không có cơ sở nào `ownerId == uid`.
- `impactVersion` = SHA-256 của chuỗi nối các ID (đơn, lượt, buổi, cơ sở) đã sắp xếp.

### 1.2 `confirm`

Request:

```json
{ "action": "confirm", "impactVersion": "sha256:4b1f…", "confirmText": "XÓA" }
```

Xử lý:

```text
1. user ← requireUser(req, { allowDeleting: true }); kiểm auth_time ≤ 300 s   // 403 REAUTH_REQUIRED
2. nếu appRole == ADMIN → 403 ADMIN_SELF_DELETE
3. nếu confirmText.trim().uppercase() != "XÓA" → 400 CONFIRM_TEXT_MISMATCH
4. del ← get accountDeletions/{uid}
   nếu del.state == DONE → 200 { status: "DELETED" }
   nếu del.state == COMMITTED → bỏ qua bước 5–6, sang bước 7
5. impact ← tính lại như preview
   nếu impact.version != body.impactVersion → 409 IMPACT_CHANGED { preview: impact }
   nếu estimateTransforms(impact) > 450 → 422 DELETE_TOO_LARGE
   // estimateTransforms = 2 × (đơn + lượt vãng lai + lượt chờ + buổi bị hủy + lượt của khách trong các buổi đó)
   //                    + 2 × (số ví được hoàn) + số cơ sở + 3
   // mỗi helper B, C, D export TRANSFORMS_PER_CALL; thiếu thì dùng 2
6. runTransaction (B: _shared/firestore.ts, thử lại khi ABORTED):
     đọc lại users/{uid}, các đơn, lượt, buổi, cơ sở trong impact
     cancelBookingInTx(...) cho myBookings            (refund: POLICY, cancelledBy: SYSTEM)
     cancelRegistrationInTx / leaveWaitlistInTx       (cancelledBy: SYSTEM)
     nếu ownerImpact: hideVenueForOwnerDeletionInTx, cancelBookingInTx(refund: FULL),
                      cancelSessionInTx(refund: FULL)
     users/{uid}: deletionRequestedAt = serverTime, fcmTokens = []
     accountDeletions/{uid}: state = COMMITTED, requestedAt, wasOwner, notify, steps = {},
                             finalSweepAfter = requestedAt + 65 phút
   lỗi → 500 INTERNAL (không có gì đổi)
7. runCleanup(uid, deadline = start + 100 s)          // _shared/account-deletion.ts
8. xong hết → 200 { status: "DELETED" }; chưa xong → 202 { status: "CLEANUP_SCHEDULED" }
```

### 1.3 Các bước dọn dẹp (`runCleanup`, idempotent)

Thứ tự cố định. Bước có `steps[name] == "DONE"` thì bỏ qua. Lỗi một bước: ghi `FAILED`, `lastError`, dừng lần chạy này (sweeper chạy lại sau).

| # | `name` | Việc | Chủ helper |
|---|---|---|---|
| 1 | `disableAuth` | `setUserDisabled(uid, true)` | A (`_shared/identity.ts`) |
| 2 | `notifyGuests` | Với mỗi phần tử `notify`: `send-notification` (`BOOKING_CANCELLED` / `DROPIN_CHANGED`, lý do "Cơ sở ngừng hoạt động"), khóa idempotent `{kind}_{id}_{recipientUid}_OWNER_DELETED` (2 khách cùng một buổi có 2 khóa khác nhau) | D |
| 3 | `anonymizeReviews` | `anonymizeReviews(uid)` | C |
| 4 | `anonymizeCommunity` | `anonymizeCommunity(uid)` | C |
| 5 | `anonymizeBookings` | `anonymizeBookings(uid)` | B |
| 6 | `deleteOwnerData` | `deleteOwnerApplications(uid)` (+ `owner-docs/{uid}/`) | D |
| 7 | `deleteSubcollections` | Xóa `users/{uid}/favorites`, `searchHistory`, `aiUsage`, `notifications` (batch 400) | A |
| 8 | `deleteAvatars` | Liệt kê và xóa `avatars/{uid}/*` (Storage API, service role) | A |
| 9 | `deleteUserDoc` | Xóa `users/{uid}` | A |
| 10 | `deleteAuthUser` | `deleteUser(uid)` (`accounts:delete`); `USER_NOT_FOUND` coi như xong | A |
| — | | `state = DONE`, `completedAt` | A |

Helper của nhóm khác chưa có: bước ghi `SKIPPED` và đi tiếp (research R12). Sweeper không thử lại bước `SKIPPED`; khi helper có, chạy `scripts/admin/rerun-deletion-step.ts <step>` cho các bản ghi `SKIPPED`.

### 1.4 Mã lỗi

| HTTP | `code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `INVALID_ARGUMENT` | `action` lạ, thiếu `impactVersion` | "Đã có lỗi, vui lòng thử lại" |
| 400 | `CONFIRM_TEXT_MISMATCH` | `confirmText` khác "XÓA" | Lỗi dưới ô xác nhận |
| 401 | `UNAUTHENTICATED` | Token sai (010) | Theo 010 |
| 403 | `REAUTH_REQUIRED` | `auth_time` quá 5 phút | Mở lại hộp thoại xác nhận danh tính |
| 403 | `ADMIN_SELF_DELETE` | `appRole == ADMIN` | "Tài khoản quản trị không tự xóa được, liên hệ nhóm phát triển" |
| 403 | `ACCOUNT_LOCKED` | (010) | Đăng xuất, thông báo khóa |
| 403 | `ACCOUNT_DELETING` | Đã chốt xóa trước đó (gọi từ function khác; `delete-account confirm` không trả mã này) | Coi là đã xóa: `signOut(ACCOUNT_DELETED)`, "Tài khoản đã được xóa" |
| 409 | `IMPACT_CHANGED` | Có đơn/lượt mới từ lúc xem trước | Cập nhật màn hình với `preview` mới, bỏ đánh dấu ô "Tôi hiểu…" |
| 422 | `DELETE_TOO_LARGE` | Ước tính > 450 field transform | "Tài khoản có quá nhiều đơn sắp tới để xóa tự động. Vui lòng hủy bớt hoặc liên hệ hỗ trợ" |
| 500 | `INTERNAL` | Transaction lỗi | "Chưa xóa được tài khoản. Tài khoản vẫn dùng bình thường, vui lòng thử lại" (FR-021) |

### 1.5 Response thành công và app

| Status | Body | App làm |
|---|---|---|
| `200` | `{ "status": "DELETED" }` | `SessionManager.signOut()` với lý do `ACCOUNT_DELETED` (bỏ qua `SignOutHook` vì token đã chết), về `WelcomeFragment` với thông báo "Tài khoản đã được xóa" |
| `202` | `{ "status": "CLEANUP_SCHEDULED" }` | Giống `200` |

**Hết giờ (120 giây) hoặc mất mạng khi gọi `confirm`:** server có thể đã chốt. App gọi lại `confirm` **một lần** với cùng `impactVersion` (idempotent); vẫn lỗi thì báo "Chưa rõ kết quả, vui lòng kiểm tra kết nối rồi thử lại", không khẳng định tài khoản còn dùng được. Chỉ `500 INTERNAL` mới hiện câu "Tài khoản vẫn dùng bình thường".

## 2. `delete-account-sweeper` (pg_cron mỗi 5 phút)

- Gọi bằng `pg_cron` + `net.http_post`, header chứa `CRON_SECRET` (giống `dropin-close` của 081). Thiếu hoặc sai → `401`.
- Mỗi lần chạy: tối đa 5 bản ghi `accountDeletions where state == COMMITTED order by requestedAt`, chạy `runCleanup` cho từng bản ghi trong giới hạn 120 giây tổng.
- Bản ghi `DONE` có `finalSweepAfter <= now` và chưa có `steps.finalSweep`: chạy lại bước 7 (`deleteSubcollections`) một lần, ghi `steps.finalSweep = DONE`.
- `attempts >= 20`: ghi log mức error để nhóm xử lý tay.
- Cần index: `accountDeletions` (`state`, `requestedAt`).

## 3. `sync-account-email`

- Request: body rỗng `{}`. Xác thực `requireUser(req)`.
- Lấy `email`, `email_verified` **từ token**. Nếu `email_verified == true` và `email.lowercase() != users.email` thì ghi `users.email`, `updatedAt = serverTime`.
- Response `200 { "updated": true | false }`. Lỗi: như `requireUser`; `500 INTERNAL`.
- Không nhận email từ body (chống giả, constitution II).

## 4. Thay đổi cho code dùng chung của 010

| File | Thay đổi |
|---|---|
| `_shared/auth.ts` | `requireUser`: `users/{uid}.deletionRequestedAt != null` → `403 ACCOUNT_DELETING`; tham số tùy chọn `opts?: { allowDeleting?: boolean }` bỏ kiểm tra này và cho phép `users/{uid}` không còn (`profile = null`), chỉ `delete-account` dùng; trả thêm `authTime` trong `VerifiedToken` |
| `_shared/identity.ts` | Thêm `deleteUser(uid)` (`accounts:delete`, Emulator dùng `Bearer owner`) |
| `core/network` (app) | Ánh xạ `ACCOUNT_DELETING` → `AppError.AccountDeleted` + `AccountDeletedBus` → `SessionManager.signOut(ACCOUNT_DELETED)` (thông báo "Tài khoản đã được xóa", không phải thông báo khóa) |

## 5. Test (`deno test`, Firebase Emulator Auth + Firestore)

Dữ liệu mẫu cho test do helper của B, C, D tạo; nếu helper chưa có thì dùng bản giả trong `delete-account/test_fakes.ts` có cùng chữ ký.

| # | Kịch bản | Kỳ vọng |
|---|---|---|
| X1 | Người chơi không có đơn, `preview` | `myBookings = []`, `ownerImpact = null`, có `impactVersion` |
| X2 | Có 2 đơn sắp tới (1 `PENDING` chưa trả, 1 `CONFIRMED` cọc 60.000 còn > 4 giờ) | `preview` trả 2 đơn, `refundAmount` = 0 và 60.000 |
| X3 | `confirm` đúng | `200 DELETED`; 2 đơn `CANCELLED`, `cancelledBy = SYSTEM`; slot của 2 đơn bị xóa; `users/{uid}` không còn; Auth không còn; `avatars/{uid}/` rỗng; favorites rỗng; `accountDeletions/{uid}.state = DONE` |
| X4 | `confirm` với `auth_time` cũ hơn 5 phút | `403 REAUTH_REQUIRED`; không có gì đổi |
| X5 | `confirmText = "xoa"` | `400 CONFIRM_TEXT_MISMATCH` |
| X6 | Sau `preview`, tạo thêm 1 đơn rồi `confirm` với `impactVersion` cũ | `409 IMPACT_CHANGED`, body có `preview` mới; không có gì đổi |
| X7 | Transaction lỗi giữa chừng (helper giả ném lỗi ở đơn thứ 2) | `500`; **không** đơn nào bị hủy, `users/{uid}` nguyên vẹn, không có `accountDeletions/{uid}`, Auth vẫn bật (FR-021) |
| X8 | Chủ sân có 1 cơ sở, 2 đơn khách sắp tới, 1 buổi vãng lai có 2 người đăng ký | Cơ sở `HIDDEN` + `hiddenReason = OWNER_DELETED`; 2 đơn khách `CANCELLED` hoàn 100% (ví khách tăng đúng số đã trả); buổi bị hủy, 2 lượt hoàn 100%; `notify` có 4 phần tử; `send-notification` giả nhận đúng 4 khóa khác nhau, 2 người cùng buổi đều được báo (SC-008) |
| X9 | Lỗi ở bước dọn dẹp `anonymizeReviews` lần đầu | `202 CLEANUP_SCHEDULED`; `steps.anonymizeReviews = FAILED`; Auth đã tắt; chạy sweeper → `DONE` |
| X10 | Gọi `confirm` 2 lần liên tiếp, 2 lần song song, và 1 lần sau khi `users/{uid}` đã bị xóa | Lần sau không bị chặn bằng `ACCOUNT_DELETING`, trả `200 DELETED` hoặc tiếp tục dọn dẹp; đơn chỉ bị hủy một lần; thông báo cho khách chỉ gửi một lần |
| X11 | `appRole = ADMIN` | `403 ADMIN_SELF_DELETE` |
| X12 | Sau khi chốt, gọi function khác dùng `requireUser` với token cũ còn hạn | `403 ACCOUNT_DELETING` |
| X13 | Đánh giá của người dùng sau khi xóa | `userId = null`, `authorDeleted = true`, `userSnapshot.displayName = null` |
| X14 | Ước tính > 450 field transform (seed 250 đơn sắp tới đã trả cọc: `2 × 250 + 2 + 3 = 505`); ca biên 220 đơn (`445`) | 250 đơn: `422 DELETE_TOO_LARGE`, không có gì đổi; 220 đơn: xóa được |
| X15 | Đơn đã `checkedInAt` hoặc đã qua | Không bị hủy; đơn đã qua được ẩn danh (`userDeleted = true`) |
| X16 | Sweeper thiếu `CRON_SECRET` | `401` |
| E1 | `sync-account-email` khi token có email mới đã xác minh | `users.email` = email mới (chữ thường), `updated: true` |
| E2 | `sync-account-email` gọi lại | `updated: false` |
| E3 | Token có email nhưng `email_verified = false` | Không ghi, `updated: false` |
