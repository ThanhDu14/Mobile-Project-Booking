# Contract: Quyền đọc dữ liệu cho chi tiết sân

Spec 030 không sở hữu collection nào nên **không tự sửa** `firestore.rules`. File này ghi Rules đề nghị D (`venues`, `courts`, `priceRules`) và C (`reviews`) sửa, kèm test Emulator do A viết để kiểm chứng (giống cách 020 đã làm). Lý do: [research R10](../research.md#r10-trạng-thái-cơ-sở-và-rules-đọc-venues-fr-019-fr-021-fr-022-fr-023-sc-005).

## 1. Rules đề nghị

```text
// D — thay rule đọc của match /venues/{venueId} mà 020 §2 đã đề nghị
match /venues/{venueId} {
  allow get:  if resource == null                        // biết được "không tồn tại"
              || resource.data.status in ['ACTIVE', 'DELETED']  // khách thấy "Sân không còn tồn tại" (FR-022)
              || isSignedIn()                            // 070 R6: mở cơ sở tạm ngừng từ yêu thích, liên kết
              || isAdmin();
  allow list: if resource.data.status == 'ACTIVE'
              || (isInitialized() && resource.data.ownerId == request.auth.uid)
              || isAdmin();

  match /courts/{courtId}  { allow read: if true; }      // giữ như 020 §2
  match /priceRules/{id}   { allow read: if true; }
}

// C — sửa rule đọc của match /reviews/{reviewId} trong contract 070
allow get, list: if resource.data.status == 'VISIBLE'
                 || (isSignedIn() && (resource.data.userId == request.auth.uid
                                      || request.auth.token.appRole == 'ADMIN'));
```

Truy vấn của app phải khớp Rules: danh sách đánh giá luôn có `where('status', '==', 'VISIBLE')`; tìm kiếm luôn có `where('status', '==', 'ACTIVE')`.

## 2. Bảng test (`firestore-tests/test/court-detail.test.ts`)

Mỗi dòng ít nhất một test. Dữ liệu: `venues/v-active` (`ACTIVE`), `v-hidden` (`HIDDEN`), `v-locked` (`LOCKED`), `v-deleted` (`DELETED`), không có `v-missing`. Mỗi cơ sở có 1 `courts` và 1 `priceRules`; `reviews` có 1 `VISIBLE` và 1 `HIDDEN` cho `v-active`.

| # | Người gọi | Thao tác | Kết quả |
|---|---|---|---|
| VD-1 | Khách | `get venues/v-active`, đọc `courts`, `priceRules` của nó | ✅ |
| VD-2 | Khách | `get venues/v-hidden`, `v-locked` | ❌ (giữ test V-3 của 020) |
| VD-3 | Khách | `get venues/v-missing` | ✅, `exists == false` |
| VD-4 | Người chơi đã khởi tạo | `get venues/v-hidden`, `v-locked`, `v-deleted` | ✅ |
| VD-5 | Người chơi | `list venues` không có điều kiện `status` | ❌ |
| VD-6 | Khách | `list reviews` where `venueId == v-active`, `status == VISIBLE`, order `createdAt desc`, limit 3 | ✅ |
| VD-7 | Khách | `list reviews` where `venueId == v-active` không lọc `status` | ❌ |
| VD-8 | Người chơi | `get` đánh giá `HIDDEN` của người khác | ❌ |
| VD-9 | Khách, người chơi | Ghi bất kỳ vào `venues`, `courts`, `priceRules`, `reviews` | ❌ |
| VD-10 | Khách | `get venues/v-deleted` | ✅, `status == DELETED` (app hiện "Sân không còn tồn tại") |

Các test V-1 → V-5 của 020 và test `reviews` của 070 vẫn phải xanh.

## 3. Phía server

- `create-booking` (B) phải đọc `venues/{venueId}` trong transaction và từ chối khi `status != 'ACTIVE'` (constitution II). Việc 030 kiểm tra lại trạng thái trước khi mở lưới giờ (`CheckVenueBookableUseCase`) chỉ để trải nghiệm tốt hơn.
- Chat với chủ sân (091) phải tự kiểm tra cơ sở `ACTIVE` và người gửi khác `ownerId` ở server. Nút ẩn trên giao diện không phải biện pháp bảo mật.
