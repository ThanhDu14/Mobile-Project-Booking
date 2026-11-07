# Data Model: Đánh giá sân và sân yêu thích (spec 070)

**Ngày**: 2026-10-05 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

Khớp với [đặc tả CSDL chung](../../docs/design/database/README.md) (quy ước mục 1). 🔒 = chỉ server (Edge Function) ghi, Rules chặn client. Các thay đổi so với đặc tả CSDL hiện tại được đánh dấu **[MỚI]** và liệt kê ở cuối file để cập nhật vào `docs/design/database/README.md` trước 14/10.

---

## 1. `reviews/{reviewId}` — C

`reviewId` = `bookingId` (đánh giá sân). Đánh giá buổi vãng lai (`{sessionId}_{uid}`, `targetType = DROP_IN`) thuộc spec 080 và dùng chung schema.

Client **chỉ đọc**. Mọi ghi đi qua Edge Function `review` ([contracts/edge-function-review.md](contracts/edge-function-review.md)).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `targetType` 🔒 | String | `VENUE` (spec này), `DROP_IN` (spec 080) |
| `bookingId` 🔒 | String | = `reviewId` khi `VENUE` |
| `venueId` 🔒 | String | Lấy từ đơn, không lấy từ client |
| `sessionId` 🔒 | String? | Chỉ khi `DROP_IN` |
| `userId` 🔒 | String? | `null` sau khi người viết xóa tài khoản |
| `userSnapshot` 🔒 | Map | `displayName` (String?), `avatarPath` (String?) tại thời điểm viết |
| `authorDeleted` 🔒 **[MỚI]** | Boolean | `true` sau khi người viết xóa tài khoản (FR-016) |
| `ratings` 🔒 | Map\<String, Int> | `court`, `cleanliness`, `service`, mỗi giá trị 1–5 |
| `overall` 🔒 | Double | Trung bình 3 tiêu chí, làm tròn 1 chữ số (FR-003) |
| `starBucket` 🔒 **[MỚI]** | Int | `round(overall)` (1–5), dùng cho lọc theo sao |
| `comment` 🔒 | String | 0–1000 ký tự, đã cắt khoảng trắng đầu/cuối |
| `photoPaths` 🔒 | Array\<String> | 0–3 phần tử, dạng `{uid}/{reviewId}/{uuid}.jpg` |
| `hasPhotos` 🔒 **[MỚI]** | Boolean | `photoPaths.size > 0`, dùng cho lọc "Có ảnh" |
| `ownerReply` 🔒 | Map? | `text` (1–500 ký tự), `repliedAt` (Timestamp), `editedAt` (Timestamp?) **[MỚI]** |
| `hasOwnerReply` 🔒 **[MỚI]** | Boolean | Dùng cho lọc "Chưa phản hồi" của chủ sân |
| `status` 🔒 | String | `VISIBLE`, `HIDDEN` (admin ẩn, spec 120) |
| `completedAt` 🔒 **[MỚI]** | Timestamp | Sao chép `bookings.completedAt` để tính hạn sửa mà không cần đọc lại đơn |
| `editedAt` 🔒 **[MỚI]** | Timestamp? | Có giá trị → hiện nhãn "Đã chỉnh sửa" (FR-007) |
| `createdAt`, `updatedAt` 🔒 | Timestamp | Server timestamp |

### Quy tắc kiểm tra (Edge Function `review`)

| Quy tắc | FR |
|---|---|
| Người gọi đã đăng nhập, `accountStatus == ACTIVE` | — |
| `bookings/{bookingId}.userId == uid` | FR-001 |
| `bookings/{bookingId}.status == COMPLETED` | FR-001 |
| `now ≤ bookings.completedAt + 7 ngày` (tạo) / `now ≤ reviews.completedAt + 7 ngày` (sửa) | FR-001, FR-007 |
| `venues/{venueId}.ownerId != uid` (chủ không tự đánh giá) | Edge Cases |
| Tạo: `reviews/{bookingId}` chưa tồn tại; nếu đã tồn tại do cùng người gửi lại → trả về bản hiện có (idempotent) | FR-002 |
| `ratings.*` là số nguyên 1–5, đủ 3 khóa | FR-003 |
| `comment.length ≤ 1000` | FR-004 |
| `photoPaths.size ≤ 3`, mỗi path có tiền tố `{uid}/{bookingId}/` và object tồn tại | FR-005 |
| Sửa/xóa: `reviews.userId == uid` | FR-009 |
| Phản hồi: `appRole == OWNER`, `ownerStatus == APPROVED`, `venues.ownerId == uid`; `text` 1–500 ký tự; không có thao tác xóa phản hồi | FR-018, FR-019 |

### Vòng đời

```
(chưa có) ──create──▶ VISIBLE ──update (≤ 7 ngày)──▶ VISIBLE (editedAt ≠ null)
                         │  ▲
            admin ẩn ────┘  └──── admin hiện lại        (spec 120, dùng applyReviewVisibility)
                         ▼
                       HIDDEN
VISIBLE/HIDDEN ──delete (bất cứ lúc nào, chỉ người viết)──▶ (xóa document + ảnh)
```

Sau khi xóa, người viết được tạo lại nếu còn trong hạn 7 ngày (Edge Cases).

### Ảnh hưởng tới tổng điểm của cơ sở

| Thao tác | `ratingCount` | `ratingSums` |
|---|---|---|
| create (VISIBLE) | +1 | + điểm mới |
| update (VISIBLE) | 0 | − điểm cũ + điểm mới |
| delete (VISIBLE) | −1 | − điểm cũ |
| delete (HIDDEN) | 0 | 0 |
| ẩn (VISIBLE → HIDDEN) | −1 | − điểm |
| hiện lại (HIDDEN → VISIBLE) | +1 | + điểm |
| phản hồi của chủ sân | 0 | 0 |
| người viết xóa tài khoản | 0 | 0 |

## 2. `users/{uid}/favorites/{venueId}` — C

ID document = `venueId` (đặc tả CSDL mục 5) → không trùng (FR-022). Client (chủ tài khoản) ghi trực tiếp, Rules kiểm tra.

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `venueId` **[MỚI]** | String | = ID document; cần để truy vấn collection group "ai yêu thích cơ sở X" |
| `venueSnapshot` | Map | `name` (String), `photoPath` (String?), `address` (String) tại thời điểm lưu |
| `createdAt` | Timestamp | Server timestamp; sắp xếp mới nhất trước (FR-023) |

- Yêu thích = theo dõi (Clarifications, FR-025a): Edge Function của D (service account) truy vấn collection group `favorites` where `venueId == X` để tìm người nhận khuyến mãi. Rules không cho client đọc collection group.
- Khi người dùng xóa tài khoản: `delete-account` (A) xóa toàn bộ subcollection (đã có trong đặc tả CSDL mục 4.1).

## 3. Trường bổ sung ở collection của người khác

| Collection | Trường | Người phụ trách | Lý do |
|---|---|---|---|
| `venues` | `ratingSums` 🔒 Map\<String, Long> (`court`, `cleanliness`, `service`, `overall10`) **[MỚI]** | D | Tính trung bình từng tiêu chí, cập nhật O(1) (research R2) |
| `venues` | `ratingAvg` 🔒, `ratingCount` 🔒 (đã có) | D | Do Edge Function `review` ghi, không phải `on-review-created` |
| `bookings` | `completedAt` 🔒 Timestamp **[MỚI]** | B | Tính hạn 7 ngày (research R3) |

## 4. Ảnh trong Supabase Storage

| Bucket | Loại | Object | Ghi | Đọc | Xóa |
|---|---|---|---|---|---|
| `review-photos` | public | `{uid}/{reviewId}/{uuid}.jpg` **[ĐỔI từ `{reviewId}/{tên file}`]** | Người dùng có `sub == uid` (thư mục đầu) | Mọi người | Chỉ service role (Edge Function `review`) |

Giới hạn bucket: ≤ 2 MB, MIME `image/jpeg`, `image/png`, `image/webp` (đã có ở mục 8).

## 5. Index cần thêm vào `firestore.indexes.json`

| Collection | Trường | Dùng cho |
|---|---|---|
| `reviews` | `venueId`, `status`, `createdAt desc` | Danh sách đánh giá (đã có ở mục 6) |
| `reviews` | `venueId`, `status`, `starBucket`, `createdAt desc` **[MỚI]** | Lọc theo sao (FR-013) |
| `reviews` | `venueId`, `status`, `hasPhotos`, `createdAt desc` **[MỚI]** | Lọc "Có ảnh" (FR-013) |
| `reviews` | `venueId`, `hasOwnerReply`, `createdAt desc` **[MỚI]** | "Đánh giá của khách", lọc chưa phản hồi (FR-020) |
| `reviews` | `userId`, `createdAt desc` **[MỚI]** | Người viết xem đánh giá của mình kể cả bị ẩn; `delete-account` tìm đánh giá cần ẩn danh |
| `favorites` (collection group) | `venueId` **[MỚI]** | D tìm người theo dõi cơ sở để gửi khuyến mãi |

## 6. Mô hình phía app (Kotlin, tầng domain)

| Lớp | Nội dung |
|---|---|
| `Review` | `id`, `venueId`, `author: ReviewAuthor`, `ratings: Ratings`, `overall: Double`, `comment`, `photoUrls: List<String>`, `ownerReply: OwnerReply?`, `isHidden`, `isEdited`, `createdAt`, `canEditUntil` |
| `ReviewAuthor` | `displayName: String?`, `avatarUrl: String?`, `isDeleted: Boolean` → UI hiện "Người dùng đã xóa" khi `isDeleted` |
| `Ratings` | `court`, `cleanliness`, `service` (Int 1–5) |
| `OwnerReply` | `text`, `repliedAt`, `isEdited` |
| `VenueRatingSummary` | `average`, `count`, `courtAvg`, `cleanlinessAvg`, `serviceAvg` |
| `ReviewDraft` | `bookingId`, `ratings` (có thể thiếu), `comment`, `photos: List<DraftPhoto>` — trạng thái màn hình viết đánh giá |
| `DraftPhoto` | `localUri`, `remotePath?`, `state` (`PENDING`, `UPLOADING`, `UPLOADED`, `FAILED`) |
| `FavoriteVenue` | `venueId`, `name`, `photoUrl`, `address`, `ratingAvg?`, `availability` (`ACTIVE`, `SUSPENDED`, `GONE`) |

URL ảnh được ghép từ path + URL public của bucket ở tầng data (đặc tả CSDL mục 8: Firestore chỉ lưu path).

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. Mục 4.7 `reviews`: thêm `authorDeleted`, `starBucket`, `hasPhotos`, `hasOwnerReply`, `completedAt`, `editedAt`, `ownerReply.editedAt`; ghi rõ client chỉ đọc.
2. Mục 4.8 `favorites`: thêm trường `venueId`; ghi "yêu thích = theo dõi" đã chốt.
3. Mục 4.3 `venues` (D): thêm `ratingSums` 🔒.
4. Mục 4.5 `bookings` (B): thêm `completedAt` 🔒.
5. Mục 6: thêm 5 index ở bảng trên.
6. Mục 7: dòng `reviews` → người chơi chỉ R; mọi ghi qua Edge Function `review`; dòng `venues` → tách `get`/`list` (research R6).
7. Mục 8: đổi object của `review-photos` thành `{uid}/{reviewId}/{uuid}.jpg`.
8. Mục 8a: đổi `on-review-created` thành `review` (tạo/sửa/xóa/phản hồi, cập nhật `ratingAvg`).
9. Mục 9: đánh dấu câu #4 đã chốt (yêu thích = theo dõi).
