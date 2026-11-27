# Contract: Quyền đọc dữ liệu cho chi tiết sân

Spec 030 không sở hữu collection nào. Ngày 27/11/2026 chốt giữ chính sách hiện hành: chỉ cơ sở ACTIVE được công khai; chủ sân đã duyệt và admin có quyền quản lý theo Rules. Nội dung dưới đây thay đề xuất cũ trong research R10. Kiểm thử bằng Firestore Emulator; không mở rộng quyền trên server để phân biệt cơ sở bị ẩn hay không tồn tại.

## 1. Quyền đọc hiện hành (rút gọn)

```text
match /venues/{venueId} {
  allow read: if isAdmin() || resource.data.status == 'ACTIVE'
              || (isApprovedOwner() && resource.data.ownerId == request.auth.uid);
  // courts và priceRules: đọc nếu cơ sở ACTIVE, là admin hoặc chủ sân hợp lệ.
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
| VD-1b | Khách | Đọc `courts`, `priceRules` của cơ sở HIDDEN | ❌ |
| VD-3 | Khách | `get venues/v-missing` | ❌, Rules hiện tại từ chối |
| VD-4 | Người chơi đã khởi tạo | `get venues/v-hidden`, `v-locked`, `v-deleted` | ❌ |
| VD-5 | Người chơi | `list venues` không có điều kiện `status` | ❌ |
| VD-6 | Khách | `list reviews` where `venueId == v-active`, `status == VISIBLE`, order `createdAt desc`, limit 3 | ✅ |
| VD-7 | Khách | `list reviews` where `venueId == v-active` không lọc `status` | ❌ |
| VD-8 | Người chơi | `get` đánh giá `HIDDEN` của người khác | ❌ |
| VD-9 | Khách, người chơi | Ghi bất kỳ vào `venues`, `courts`, `priceRules`, `reviews` | ❌ |
| VD-10 | Khách | `get venues/v-deleted` | ❌ |

Các test V-1 → V-5 của 020 và test `reviews` của 070 vẫn phải xanh.

## 3. Phía server

- `create-booking` (B) phải đọc `venues/{venueId}` trong transaction và từ chối khi `status != 'ACTIVE'` (constitution II). Việc 030 kiểm tra lại trạng thái trước khi mở lưới giờ (`CheckVenueBookableUseCase`) chỉ để trải nghiệm tốt hơn.
- Chat với chủ sân (091) phải tự kiểm tra cơ sở `ACTIVE` và người gửi khác `ownerId` ở server. Nút ẩn trên giao diện không phải biện pháp bảo mật.
