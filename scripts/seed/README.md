# Dữ liệu mẫu (seed)

Nạp vào Firestore:

- `districts` (22 quận/huyện TP.HCM) và `amenities` (4 tiện ích) từ `catalog.json`.
- 10 cơ sở cầu lông `venues/demo-*` kèm `courts` và `priceRules` từ `venues.json`.

Chưa nạp `slots`, `bookings`, `users`, `reviews`. Các spec cần những dữ liệu đó tự viết seed riêng (ví dụ spec 070 T025).

## Cách chạy

Cần Node 20+. Chạy Emulator thì cần thêm Java (dùng Java đi kèm Android Studio cũng được).

```bash
cd scripts/seed
npm install

npm run check                       # chỉ kiểm tra dữ liệu, không ghi gì

# Emulator (mở emulator ở terminal khác: npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow)
node seed.mjs --emulator
node seed.mjs --emulator --reset    # xóa venues/demo-* rồi nạp lại

# Project thật: đăng nhập ADC trước (gcloud auth application-default login), script sẽ hỏi xác nhận
node seed.mjs --project=mobile-22e6c
```

Chạy lại nhiều lần không tạo bản ghi trùng: ID cố định (`demo-…`, `court-01`, `rule-01`). `createdAt`, `ratingAvg` và `ratingCount` đã có thì được giữ nguyên.

## Nguồn dữ liệu

10 sân cầu lông có thật, lấy từ trang [datlich.alobo.vn](https://datlich.alobo.vn) ngày 24/10/2026, chỉ dùng cho mục đích học tập. Hiện chỉ có sân cầu lông; sân pickleball sẽ bổ sung sau khi xong phần cầu lông. Link trang gốc của từng sân nằm ở trường `source`.

## Những chỗ khác với dữ liệu gốc hoặc với schema

| Vấn đề | Cách xử lý hiện tại | Ai chốt |
|---|---|---|
| Alobo có 2 loại giá: **vãng lai** và **cố định** (đặt theo tuần). Schema `priceRules` chỉ có `pricePerHour` | `pricePerHour` = giá vãng lai. Giá cố định giữ trong `venues.json` (`recurringPricePerHour`), **chưa ghi lên Firestore** | D (schema `priceRules`), B (spec 041) |
| Schema yêu cầu `pricePerHour` chia hết cho 2.000. Minh Tâm CN1/CN2 có giá 65k, 75k, 85k | Script làm tròn lên (66k, 76k, 86k) và in cảnh báo. Giá gốc giữ trong `venues.json` | D |
| Quy ước `daysOfWeek` chưa chốt (spec 020 đề xuất ISO 1 = T2; spec 040/041 ghi 1 = CN) | Dùng ISO. Đổi hằng số `DAY_CONVENTION` trong `seed.mjs` nếu nhóm chốt khác | B + D |
| Giờ đóng cửa "24h" | Ghi `"2400"` cho `closeTime` và `endTime` | B + D |
| Ảnh nằm trên server Alobo, chưa có bucket `venue-photos` | `photoPaths` chứa **URL đầy đủ** (`https://m-files.alobo.vn/...`). App hiện ảnh: path bắt đầu bằng `http` thì dùng nguyên, không ghép URL Supabase | D (bucket), A (hiển thị) |
| Tọa độ: trang gốc không hiện tọa độ | Lấy gần đúng theo tên đường (OpenStreetMap), sai lệch có thể vài trăm mét (`location.approx = true` trong JSON) | — |
| Bảng giá theo ngày lễ (PAM, Gia Hân, Sơn Tạ) | Chỉ lấy bảng giá thường | — |
| Số điện thoại thật của sân | Che 3 số giữa (`0944xxx486`). App hiển thị được nhưng không gọi được; validate SĐT của spec 111 sẽ không qua với dữ liệu này | — |
| Loại mặt sân không có trên trang | Tất cả `surfaceType = "PU"` | — |
| Tiện ích, chủ sân | `amenityIds = []`, `ownerId = "demo-owner-1"` (chưa có tài khoản thật) | D |
| `nameKeywords` | Sinh theo [contract spec 020](../../specs/020-court-search/contracts/keyword-normalization.md). Thay bằng `_shared/search-keywords.ts` khi A làm xong T017 | A |
