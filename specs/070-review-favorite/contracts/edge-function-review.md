# Contract: Edge Function `review`

**Vị trí mã**: `supabase/functions/review/index.ts` · logic dùng chung: `supabase/functions/_shared/reviews.ts`
**Gọi từ app**: `supabase.functions.invoke("review", body)` với header `Authorization: Bearer <Firebase ID token>`
**Phương thức**: `POST`, `Content-Type: application/json`

Xác thực: Edge Function kiểm tra Firebase ID token (chữ ký, `aud`, `exp`), lấy `uid = sub`, `appRole`. Token thiếu/sai → `401 UNAUTHENTICATED`.

Mọi action ghi Firestore chạy trong **một transaction** (`beginTransaction` → đọc → `commit`), thử lại tối đa 5 lần khi xung đột.

---

## Request chung

```json
{ "action": "create" | "update" | "delete" | "reply" | "discardPhotos", ... }
```

## 1. `create`

```json
{
  "action": "create",
  "bookingId": "8d1c…",
  "ratings": { "court": 5, "cleanliness": 4, "service": 3 },
  "comment": "Sân sạch, đèn sáng",
  "photoPaths": ["<uid>/8d1c…/a1.jpg"]
}
```

Kiểm tra: xem [data-model.md](../data-model.md#quy-tắc-kiểm-tra-edge-function-review). Ghi `reviews/{bookingId}` (`status = VISIBLE`) và cộng `venues.ratingSums`, `ratingCount`, tính lại `ratingAvg`. Sau commit: gọi `send-notification` loại `REVIEW_NEW` cho chủ cơ sở (lỗi bỏ qua).

**Idempotent:** nếu `reviews/{bookingId}` đã tồn tại với `userId == uid` và cùng nội dung → trả `200` với bản hiện có; khác nội dung → `409 ALREADY_REVIEWED`.

**Response `201`** (hoặc `200` khi idempotent):

```json
{ "review": { "id": "8d1c…", "overall": 4.0, "createdAt": "2026-10-05T10:00:00Z", "canEditUntil": "2026-10-12T09:00:00Z" },
  "venue": { "ratingAvg": 4.3, "ratingCount": 12 } }
```

## 2. `update`

```json
{ "action": "update", "reviewId": "8d1c…", "ratings": {…}, "comment": "…", "photoPaths": […] }
```

Chỉ người viết, trong hạn 7 ngày từ `completedAt`. Trừ điểm cũ, cộng điểm mới (nếu `VISIBLE`); đặt `editedAt`. Ảnh bị bỏ khỏi `photoPaths` → xóa object trong Storage sau commit. Phản hồi của chủ sân giữ nguyên.

**Response `200`**: như `create`.

## 3. `delete`

```json
{ "action": "delete", "reviewId": "8d1c…" }
```

Chỉ người viết, bất cứ lúc nào. Xóa document; nếu đang `VISIBLE` thì trừ tổng điểm. Sau commit xóa mọi ảnh trong `{uid}/{reviewId}/`.

**Response `200`**: `{ "venue": { "ratingAvg": 4.2, "ratingCount": 11 } }`

## 4. `reply` (chủ sân)

```json
{ "action": "reply", "reviewId": "8d1c…", "text": "Cảm ơn bạn, sân sẽ thay đèn tuần sau." }
```

Chỉ `appRole == OWNER`, `ownerStatus == APPROVED`, `venues.ownerId == uid`. Chưa có phản hồi → tạo (`repliedAt`), gọi `send-notification` loại `REVIEW_REPLY` cho người viết (nếu `userId != null`). Đã có → sửa (`editedAt`). Không có action xóa phản hồi (Clarifications).

**Response `200`**: `{ "ownerReply": { "text": "…", "repliedAt": "…", "editedAt": null } }`

## 5. `discardPhotos`

```json
{ "action": "discardPhotos", "bookingId": "8d1c…", "photoPaths": ["<uid>/8d1c…/a1.jpg"] }
```

Xóa ảnh đã tải lên khi người dùng bỏ đánh giá đang viết hoặc bỏ một ảnh trước khi gửi. Chỉ xóa path có tiền tố `{uid}/{bookingId}/` **và** không nằm trong `photoPaths` của đánh giá đã lưu. Không ghi Firestore.

**Response `200`**: `{ "deleted": 1 }`

---

## Mã lỗi

| HTTP | `error.code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `INVALID_ARGUMENT` | Thiếu tiêu chí, sao ngoài 1–5, bình luận > 1000, > 3 ảnh, phản hồi rỗng/> 500, path sai tiền tố | Lỗi theo trường (thường đã chặn ở app) |
| 401 | `UNAUTHENTICATED` | Token thiếu/hết hạn | Làm mới token rồi thử lại 1 lần |
| 403 | `NOT_OWNER` | Đơn/đánh giá không thuộc người gọi; chủ sân không sở hữu cơ sở | "Bạn không có quyền thực hiện thao tác này" |
| 403 | `OWN_VENUE` | Chủ sân đánh giá cơ sở của mình | "Bạn không thể đánh giá sân của chính mình" |
| 403 | `ACCOUNT_LOCKED` | `accountStatus == LOCKED` | "Tài khoản đang bị khóa" |
| 404 | `NOT_FOUND` | Đơn/đánh giá không tồn tại | "Không tìm thấy đánh giá" |
| 409 | `BOOKING_NOT_COMPLETED` | Đơn chưa `COMPLETED` | "Bạn chỉ đánh giá được sau khi đã chơi" |
| 409 | `REVIEW_WINDOW_CLOSED` | Quá 7 ngày (tạo/sửa) | "Đã hết hạn đánh giá/sửa đánh giá" |
| 409 | `ALREADY_REVIEWED` | Đơn đã có đánh giá khác nội dung | Mở đánh giá hiện có |
| 409 | `PHOTO_MISSING` | Path ảnh chưa có trong Storage | Đánh dấu ảnh lỗi, cho thử lại (FR-006) |
| 500 | `INTERNAL` | Lỗi khác, transaction thất bại sau 5 lần | "Có lỗi xảy ra, vui lòng thử lại" + nút thử lại |

Body lỗi: `{ "error": { "code": "REVIEW_WINDOW_CLOSED", "message": "..." } }`

## Hàm dùng chung cho spec 120 (D)

```ts
// supabase/functions/_shared/reviews.ts
export async function applyReviewVisibility(tx: Transaction, reviewId: string, visible: boolean): Promise<void>
```

Đổi `status` giữa `VISIBLE`/`HIDDEN` và cộng/trừ `ratingSums`, `ratingCount`, `ratingAvg` của cơ sở trong transaction `tx` của người gọi. Không làm gì nếu trạng thái đã đúng.
