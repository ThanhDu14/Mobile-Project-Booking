# Quickstart: kiểm chứng spec 020

Hướng dẫn chạy và kiểm tra. Không chứa code triển khai. Chi tiết dữ liệu và API: [data-model.md](data-model.md), [contracts/](contracts/).

## 0. Chuẩn bị

- Đã có phần chuẩn bị của [quickstart 010 §0](../010-account-auth/quickstart.md#0-chuẩn-bị) (Node 20+, Deno 2, JDK 17+, `google-services.json`, `local.properties`, `USE_EMULATOR`).
- PR `chore(core)` của plan 010 đã merge (Hilt, `core/network`, `core/session`, `core/di`).
- Bộ dữ liệu demo có đáp án biết trước (SC-003): `scripts/seed/search-demo.json`, gồm:
  - 30 cơ sở ở Quận 10, Tân Bình, Bình Thạnh; 2 cơ sở `HIDDEN`, 1 cơ sở `LOCKED`.
  - Cơ sở "Sân cầu lông Hòa Bình" (Q10, thường 80k, cao điểm 19:00–22:00 giá 150k, có Gửi xe).
  - Cơ sở A trống 19:00–21:00 ngày mai; cơ sở B kín khung đó; cơ sở C có sân 1 trống 19:00–20:00 và sân 2 trống 20:00–21:00 (US2-4); cơ sở D có một slot `HOLD` đã hết hạn trong khung (US2-5).
  - 1 cơ sở chưa có bảng giá (`minPricePerHour = null`), 1 cơ sở chưa có đánh giá, 1 cơ sở không có ảnh.
  - File `scripts/seed/search-demo.expected.json`: kết quả đúng (theo thứ tự) cho 12 bộ điều kiện mẫu.
- Ngày trong dữ liệu demo tính theo "hôm nay" lúc seed, script nhận `--today=yyyyMMdd`.

## 1. Test Security Rules (bắt buộc, nguyên tắc II và IV)

```bash
cd firestore-tests
npm test
```

Kỳ vọng: test nền, U1–U18 (010) và C-1…S-2 của [security-rules-and-indexes.md §4](contracts/security-rules-and-indexes.md#4-bảng-test) đều xanh. V-1…S-2 chỉ xanh khi D, B đã viết Rules của `venues`, `courts`, `priceRules`, `slots`; trước đó đánh dấu `it.todo` và ghi trong PR.

## 2. Unit test app (bắt buộc)

```bash
./gradlew testDebugUnitTest --tests "*core.text*" --tests "*core.pricing*" \
  --tests "*core.schedule*" --tests "*core.location*" --tests "*feature.search*"
```

| Nhóm test | Kiểm chứng |
|---|---|
| `KeywordGeneratorTest`, `KeywordQueryTest` | Đọc `supabase/functions/_shared/search-keywords.vectors.json`; US1-1, US1-2, FR-001 |
| `SlotPricingTest` | US2-2 (80k vs 150k cao điểm), khung vắt qua hai mức giá, khung có chỗ không có rule → `null` |
| `AvailabilityEvaluatorTest` | US2-3, US2-4, US2-5, US2-6; số sân trống |
| `VenueMatcherTest` | Tiện ích "đủ tất cả" (US2-7), nhiều token, giờ mở cửa |
| `ResultSorterTest` | Ba cách sắp xếp, cùng điểm thì nhiều lượt trước, chưa có đánh giá/giá đứng cuối (FR-011) |
| `SearchCriteriaSanitizerTest` | Mọi dòng bảng [search-criteria.md §2](contracts/search-criteria.md#2-sanitizer) |
| `DistanceFormatterTest` | "850 m", "1,2 km" (FR-013) |
| `FirestoreVenueSearchEngineTest` (fake data source) | Chọn S1/S2 đúng; S1 đọc tiếp tới đủ 20 hoặc 4 lô; pha 2 của sắp xếp giá; giới hạn 150 |
| `SearchViewModelTest` (Turbine) | FR-018: gõ "ho" rồi "hoa" nhanh, kết quả của "ho" về sau vẫn không hiện; FR-020: kết quả từ cache bật `staleBanner`; quyền vị trí bị từ chối → sắp xếp trở về lựa chọn cũ; khách không gọi repository lịch sử |
| `SearchHistoryRepositoryTest` | ID tất định; tìm lại tổ hợp cũ không thêm mục; dọn còn 10 |

```bash
cd supabase/functions && deno test _shared/search-keywords.test.ts
```

Kỳ vọng: bản TypeScript cho cùng kết quả với cùng file vector.

## 3. Kiểm thử với dữ liệu demo

**Trên Emulator** (`USE_EMULATOR=true`):

```bash
npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow
node scripts/seed/seed.mjs --emulator --today=$(TZ=Asia/Ho_Chi_Minh date +%Y%m%d)
```

Chạy app, lần lượt nhập 12 bộ điều kiện trong `search-demo.expected.json`, so kết quả trên màn hình với đáp án (SC-003, SC-004). `VenueSearchEngineEmulatorTest` (androidTest, nên có) tự động hóa bước này.

**Trên project thật trước demo** (bắt lỗi thiếu index, Emulator không kiểm tra index):

```bash
npx firebase-tools@15 deploy --only firestore:indexes --project mobile-22e6c
node scripts/seed/seed.mjs --project=mobile-22e6c --today=...
```

Chạy lại 12 bộ điều kiện. Logcat có `FAILED_PRECONDITION … requires an index` → thêm index theo link trong lỗi vào `firestore.indexes.json`, ghi vào research R8.

## 4. Đo thời gian và lượt đọc (SC-001, quota)

- Bật giả lập mạng 4G của Android Emulator (Extended controls → Cellular → Network type LTE, Signal good) hoặc dùng máy thật trên 4G.
- App bản debug ghi log `SearchTiming` (thời gian từ lúc đổi điều kiện tới khi hiện trang đầu) và `SearchReads` (số document đọc theo `QuerySnapshot.size()`, cộng 1 cho query rỗng).
- Mỗi kịch bản chạy 20 lần: (1) không bộ lọc, (2) quận + giá, (3) quận + khung giờ, (4) khung giờ không chọn quận, sắp xếp giá, (5) "Gần nhất", (6) tệ nhất S2: seed `--stress` (150 cơ sở, hầu hết kín khung) + khung giờ, không chọn quận, sắp xếp giá.
- Kỳ vọng: ≥ 19/20 lần dưới 3 giây ở kịch bản 1–5; kịch bản 6 hiện kết quả đầu tiên dưới 3 giây (trang có thể ít hơn 20, research R4); lượt đọc gần bảng ước lượng ở research R4. Ghi kết quả vào PR.

## 5. Kịch bản thủ công trên máy

| # | Bước | Kỳ vọng |
|---|---|---|
| 1 | Chưa đăng nhập, mở "Trang chủ", gõ "hoa binh" | Thấy "Sân cầu lông Hòa Bình"; không bị bắt đăng nhập (FR-026) |
| 2 | Chọn Quận 10, Mai 19:00–21:00, ≤100k, Gửi xe | 4 chip; chỉ cơ sở thỏa cả bốn; thẻ hiện "X đ/giờ · 19:00–21:00" và "N sân trống khung này" |
| 3 | Bỏ chip khung giờ | Cơ sở B xuất hiện lại (US2 Independent Test) |
| 4 | "Xóa bộ lọc" | Bỏ giá, khung giờ, tiện ích; giữ từ khóa và quận |
| 5 | Chọn "Gần nhất", bấm "Để sau" ở hộp giải thích | Sắp xếp giữ như cũ |
| 6 | Chọn "Gần nhất", cho phép "Gần đúng" | Xếp theo khoảng cách, thẻ hiện "850 m", "1,2 km" |
| 7 | Thu hồi quyền trong Cài đặt, quay lại, chọn "Gần nhất", từ chối | Snackbar có nút "Cài đặt"; thẻ không còn khoảng cách; mọi chức năng khác vẫn chạy (SC-005) |
| 8 | Cuộn tới cuối khi có > 20 kết quả | Tải thêm (FR-016) |
| 9 | Mở một thẻ, quay lại | Giữ nguyên chip, sắp xếp, vị trí cuộn (FR-017) |
| 10 | Bật chế độ máy bay, đổi sắp xếp | Không crash; còn kết quả cũ kèm thông báo; nếu chưa có thì trạng thái lỗi + "Thử lại" (FR-020) |
| 11 | Đăng nhập, tìm 3 lần khác nhau, mở ô tìm kiếm | 3 mục, mới nhất ở đầu; lặp lại tổ hợp thứ 2 → lên đầu, không trùng |
| 12 | Sửa giờ máy sang ngày kia, bấm mục lịch sử có "Mai" | Khôi phục các bộ lọc khác, bỏ khung giờ, báo "Ngày đã qua, vui lòng chọn lại" |
| 13 | Đăng nhập tài khoản thứ hai | Không thấy lịch sử của tài khoản đầu (SC-006) |
| 14 | Xóa tài khoản (spec 011), kiểm tra Firestore | `users/{uid}/searchHistory` không còn document |
| 14a | Khách có bộ lọc đang áp dụng, mở ô tìm kiếm, bấm "Đăng nhập" ở lời mời, đăng nhập tài khoản người chơi | Quay lại màn hình tìm kiếm, chip, sắp xếp và vị trí cuộn còn nguyên (Edge case "Khách… bấm đặt sân", 010 FR-026) |
| 15 | Chế độ tối, máy tính bảng `w600dp`, TalkBack | Đúng màu theo theme; lưới 2 cột; đọc đủ tên, giá, điểm |

## 5a. Thử với người dùng (SC-002, SC-007)

- 5 người chưa dùng app (bạn cùng lớp ngoài nhóm), máy có dữ liệu demo, không hướng dẫn trước.
- Nhiệm vụ 1 (SC-002): "Tìm một sân ở Quận 10, ngày mai 19:00–21:00, dưới 100k, có gửi xe, rồi mở chi tiết sân." Bấm giờ từ lúc vào màn hình tìm kiếm tới khi mở chi tiết. Kỳ vọng: dưới 1 phút.
- Nhiệm vụ 2 (SC-007): "Đặt khung giờ tối mai, sau đó bỏ riêng bộ lọc khung giờ." Kỳ vọng: ≥ 4/5 người tự làm được.
- Ghi thời gian, chỗ người thử lúng túng và đề xuất sửa vào mô tả PR.

## 6. Hoàn tất

- Ghi kết quả §1–§5a vào mô tả PR, kèm ảnh chụp sáng/tối.
- Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).
- Ghi log AI vào `docs/ai-log/AI_log_1.md`.
