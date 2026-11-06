# Quickstart: Kiểm thử bản đồ sân và sân gần bạn (spec 021)

Hướng dẫn chạy và kiểm tra spec 021 từ đầu đến cuối. Thiết kế ở [plan.md](plan.md), dữ liệu thử ở [research R13](research.md#r13-dữ-liệu-seed-và-dữ-liệu-thử-cho-bản-đồ), quy tắc ở [contracts/](contracts/).

## 0. Chuẩn bị

1. Đã merge 020 tới hết US3 (lọc, sắp xếp, vị trí) và phần `core/map` của 021. Kiểm tra: `./gradlew assembleDebug` qua.
2. Mở Emulator Firestore và Auth:
   ```bash
   npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow
   ```
3. Nạp dữ liệu (terminal khác):
   ```bash
   cd scripts/seed && npm install && npm run check
   node seed.mjs --emulator --reset --with-map-demo     # 10 sân seed + 16 sân mapdemo-*
   ```
   `npm run check` phải in "geohash khớp vector" cho mọi cơ sở.
4. Chạy app debug trỏ vào Emulator (cấu hình `useEmulator` của 010). Máy ảo Android có Google Play hoặc AOSP đều được (không dùng Play Services).
5. **Đặt vị trí giả** trong Extended Controls → Location của máy ảo:

| Tên | Tọa độ | Dùng cho |
|---|---|---|
| P1 Tân Bình | 10.7990, 106.6480 | 8 sân trong 5 km, gần nhất Nụ Cười 0,55 km |
| P2 Quận 1 | 10.7769, 106.7009 | Không có sân seed trong 5 km (gần nhất 6,8 km); `mapdemo` Quận 10 cách khoảng 3,7 km |
| P3 Quận 10 | 10.7735, 106.6670 | Ngay giữa cụm 8 sân `mapdemo` |

## 1. Unit test

```bash
./gradlew testDebugUnitTest --tests "*.core.map.*" --tests "*.feature.search.data.*Area*" \
  --tests "*.feature.search.data.GridClustererTest" --tests "*.feature.search.data.CardOrderTest" \
  --tests "*.feature.search.data.NearbySelectorTest" --tests "*.feature.search.ui.map.*" \
  --tests "*.feature.search.ui.nearby.*"
```

Phải xanh, gồm: SC-002 (tập bản đồ = tập danh sách trong cùng vùng, 8 bộ điều kiện), FR-006 (150 cơ sở giả → 100 gần tâm nhất, `capped`), SC-006 (vị trí không vào `SavedStateHandle`/`criteria`), SC-007 (60 lần kéo trong 1 phút, engine không được gọi), vector geohash.

## 2. Test Rules trên Emulator

```bash
cd firestore-tests && npx vitest run test/map.test.ts
```

M-1, M-2, M-5, M-6 xanh; M-3, M-4 là `todo` cho tới khi D áp Rules `venues` theo 020 R13 ([contracts/rules-and-indexes.md §3](contracts/rules-and-indexes.md#3-bảng-test-firestore-teststestmaptestts)).

## 3. Kịch bản thủ công trên máy ảo

Ghi kết quả (Đạt/Không đạt, ảnh chụp) vào `docs/features/02-court-search/screenshots/021/`.

| # | Bước | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|
| K1 | Khách (chưa đăng nhập), chưa cấp quyền vị trí. Màn hình tìm kiếm → "Bản đồ" | Bản đồ ở trung tâm TP.HCM mức 12; **không** có hộp xin quyền; banner "Đang hiện khu vực trung tâm TP.HCM…"; marker `mapdemo` Quận 10/Quận 3; sân seed gần nhất (Tân Bình) nằm sát rìa trái hoặc ngoài màn hình | FR-012, FR-013, FR-022, US3-6 |
| K2 | Bấm "Danh sách" rồi lại "Bản đồ" | Bộ lọc giữ nguyên; bản đồ không đổi vùng | FR-001 |
| K3 | Ở mức 12 nhìn Quận 10 | Một cụm có số (≥ 8). Chạm cụm → phóng vào, tách thành marker riêng | FR-003, US2-1, US2-2 |
| K4 | Phóng tới mức tối đa ở Quận 3 tại cụm 2 sân trùng tọa độ, chạm cụm | Không phóng nữa; dãy thẻ 2 sân để vuốt | Edge case cùng tọa độ |
| K5 | Chạm một marker | Marker lớn lên; thẻ tóm tắt có ảnh, tên, quận, giá "từ X đ/giờ", điểm hoặc "Chưa có đánh giá"; không có khoảng cách (chưa có vị trí) | FR-007, US1-3 |
| K6 | Vuốt thẻ sang trái 3 lần, sang phải 1 lần | Mỗi lần sang cơ sở gần kế tiếp, marker và camera đi theo; vuốt phải về đúng cơ sở trước; **không** hiện nút "Tìm ở khu vực này" | FR-008 |
| K7 | Chạm vùng trống | Thẻ ẩn, marker hết nổi bật | FR-010 |
| K8 | Bộ lọc "Mai 19:00–21:00 · ≤100k/giờ" (chip của 020), xem thẻ một cơ sở, rồi chuyển sang danh sách xem cùng cơ sở | Giá trên thẻ bản đồ = giá trên thẻ danh sách, dạng "X đ/giờ · 19:00–21:00" | US1-4, SC-003 |
| K9 | Chạm thẻ → chi tiết sân → quay lại | Chi tiết sân có ngày và khung giờ của bộ lọc; quay lại đúng vùng, mức phóng, cơ sở đang chọn; **không** tải lại (thanh tiến trình không chạy) | FR-009, US1-5 |
| K10 | Kéo bản đồ từ Quận 10 sang Quận 3, kéo/phóng liên tục 1 phút | Nút "Tìm ở khu vực này" hiện sau khi dừng; marker cũ giữ nguyên; thanh tiến trình không chạy lần nào | FR-004, US2-4, SC-007 |
| K11 | Bấm "Tìm ở khu vực này" | Tải sân Quận 3; nút ẩn | FR-005, US2-5 |
| K12 | Danh sách: chọn quận "Quận 12" → "Bản đồ" | Camera bao đủ 5 sân Quận 12 | research R3 |
| K13 | Kéo sang Gò Vấp → "Tìm ở khu vực này" | Chip "Quận 12" biến mất; snackbar "Đã tìm theo khu vực trên bản đồ"; hiện sân Gò Vấp; bộ lọc khác giữ nguyên | US2-6 |
| K14 | Thu nhỏ tới khi thấy cả Củ Chi và Quận 1 (mức ~10) → "Tìm ở khu vực này" | "Phóng gần hơn để tìm sân", không tải | US2-7 |
| K15 | Bộ lọc tiện ích không cơ sở nào có (seed chưa có tiện ích) | "Không có sân phù hợp trong khu vực này" + "Thu nhỏ bản đồ", "Xóa bộ lọc" | Edge case, FR-019 |
| K16 | Bấm "Vị trí của tôi" (chưa có quyền) → "Tiếp tục" → hệ thống: **Từ chối** | Giữ vùng, banner mặc định, snackbar có "Cài đặt"; mọi thứ khác dùng được | US3-1, US3-4 |
| K17 | Vị trí giả P1. "Vị trí của tôi" → cho phép "Gần đúng" | Camera về Tân Bình mức 15, chấm xanh, thẻ có khoảng cách ("550 m", "1,1 km" theo `DistanceFormatter`) giống thẻ danh sách | US3-2, US3-3, FR-014 |
| K18 | Đổi vị trí giả sang P3 trong lúc mở bản đồ | Chấm di chuyển; camera **không** đi theo; không tải lại | Edge case vị trí thay đổi |
| K19 | Tắt Location của máy ảo, bấm "Vị trí của tôi" | Sau ≤ 10 giây: "Không lấy được vị trí…", giữ vùng | US3-5 |
| K20 | Chế độ máy bay khi đang có marker; bấm "Tìm ở khu vực này" | Marker còn; banner offline; snackbar lỗi có "Thử lại"; không crash | FR-021, Edge case mất mạng |
| K21 | Xóa dữ liệu app (mất cache tile), bật chế độ máy bay, mở "Bản đồ" | Sau ≤ 8 giây: "Không tải được bản đồ, đã chuyển về danh sách"; bộ lọc giữ nguyên; nút "Bản đồ" vẫn còn | FR-020, SC-005 |
| K22 | Xoay màn hình; đổi sáng/tối khi có cơ sở đang chọn | Giữ vùng, mức phóng, cơ sở đang chọn, bộ lọc; tile đảo màu khi tối, cụm đọc rõ | Edge case xoay, FR-023 |
| K23 | `adb shell am kill` khi app ở nền rồi mở lại | Bản đồ về đúng vùng và cơ sở đang chọn, tải lại một lần; chấm vị trí lấy lại nếu còn quyền | research R10 |
| K24 | Ô AI của 022 (nếu đã có) điền "sân quận 3 tối mai" khi đang ở bản đồ | Marker cập nhật theo bộ lọc mới | Edge case 022 |
| K25 | Cơ sở `mapdemo-no-location` | Có trong danh sách khi lọc theo tên, **không** có trên bản đồ | Edge case thiếu vị trí |
| K26 | Admin/chủ sân ẩn một cơ sở đang hiện marker (sửa `status` trong Emulator UI) rồi chạm marker → thẻ | Marker còn tới lần tải sau; chi tiết sân hiện ghi chú tạm ngừng (030) | Edge case cơ sở bị ẩn |

### "Sân gần bạn"

| # | Bước | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|
| N1 | Cài lại app, mở màn hình tìm kiếm | "Sân gần bạn" ngay dưới thanh tìm kiếm, dòng giải thích + "Bật vị trí"; **không** tự hiện hộp xin quyền | FR-015a, research R9 |
| N2 | Vị trí giả P1, bấm "Bật vị trí" → cho phép | Dải thẻ: Nụ Cười, K3, Trường Chinh 909, … tối đa 8 sân, tăng dần theo khoảng cách; **không** có Quang Vinh (5,47 km), Minh Tâm CN2 (5,80 km) | US4-2, FR-016 |
| N3 | Bấm một thẻ | Mở chi tiết đúng sân | US4-3 |
| N4 | "Xem tất cả trên bản đồ" | Chế độ bản đồ quanh P1 mức 14, có chấm vị trí; không có chip bộ lọc | US4-4, FR-017 |
| N5 | Vị trí giả P2 (chạy lại khi chưa có `mapdemo`, chỉ seed) | "Chưa có sân trong bán kính 5 km quanh bạn" + "Tìm rộng hơn", "Tìm theo quận". "Tìm rộng hơn" mở bản đồ mức 12 quanh P2 | US4-5, FR-018 |
| N6 | Thu hồi quyền vị trí trong Cài đặt rồi quay lại | Lời giải thích + "Bật vị trí" + "Tìm theo quận", không có danh sách | US4-6 |
| N7 | Gõ từ khóa / chọn bộ lọc / chạm thanh tìm kiếm | Mục thu gọn; xóa hết điều kiện thì hiện lại | US4-7, US4-8 |
| N8 | Mở chi tiết sân rồi quay lại 3 lần, vị trí không đổi | Mục không tải lại (log `MapReads` không tăng) | research R9 |

### Máy tính bảng và trợ năng

| # | Bước | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|
| T1 | Máy ảo tablet (`w600dp`), màn hình tìm kiếm | Danh sách trái, bản đồ phải; không có nút chuyển chế độ | FR-023 |
| T2 | Chạm một thẻ danh sách | Marker nổi bật, camera di tới, thẻ tóm tắt hiện; chạm marker khác thì danh sách tô thẻ tương ứng | FR-023, research R11 |
| T3 | Bật TalkBack, vuốt qua bản đồ | Đọc "Sân Nụ Cười, 550 m, chưa có đánh giá", "Cụm 8 sân, chạm hai lần để phóng to"; chạm hai lần mở thẻ / phóng | FR-023, research R12 |
| T4 | Layout Inspector / Accessibility Scanner | Mọi nút ≥ 48dp, không có lỗi tương phản ở chế độ tối | FR-023 |

## 4. Project thật (trước demo)

1. Deploy index: `npx firebase-tools@15 deploy --only firestore:indexes --project mobile-22e6c`, chờ index `venues (status, geohash)` sang trạng thái *Enabled*.
2. **Không** nạp `map-demo.json` (script từ chối). Dùng seed 10 sân.
3. Chạy K1, K5, K9, K11, K12, K17 (vị trí giả P1), N2. Logcat không có `FAILED_PRECONDITION … requires an index`.

## 5. Đo thời gian và lượt đọc (SC-001)

- Bản debug ghi log `MapTiming` (từ lúc gửi `LoadRequest` tới khi marker đầu tiên hiện, và tới khi xong) và `MapReads` (cộng `size()` mỗi snapshot, +1 cho query rỗng).
- Bật giả lập mạng 4G của máy ảo (Extended Controls → Cellular → LTE, signal Good). Mỗi kịch bản đo 20 lần, ghi trung vị và lần chậm thứ 19 (≈ p95):

| # | Kịch bản | Mục tiêu |
|---|---|---|
| 1 | Vào bản đồ, mức 13 ở Quận 10, không lọc | p95 ≤ 3 giây, ≤ 30 lượt |
| 2 | Như 1, khung giờ "Mai 19:00–21:00", lần đầu | p95 ≤ 3 giây tới marker cuối |
| 3 | Như 2, lần sau (đã cache) | p95 ≤ 2 giây |
| 4 | "Tìm ở khu vực này" sau khi kéo sang Quận 3 | p95 ≤ 3 giây |
| 5 | Chế độ `DISTRICT` Quận 12 | p95 ≤ 2 giây |
| 6 | "Sân gần bạn" ở P1 | ≤ 15 lượt |

- Ghi kết quả vào bảng trong `docs/features/02-court-search/README.md`. Nếu kịch bản 2 vượt 3 giây thì kiểm tra marker có hiện theo đợt không (research R14).

## 6. Kiểm tra cuối

```bash
./gradlew lint testDebugUnitTest assembleDebug
cd firestore-tests && npx vitest run
cd scripts/seed && npm run check
```
