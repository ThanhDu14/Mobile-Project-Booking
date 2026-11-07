# Quickstart: Kiểm chứng chi tiết sân (spec 030)

**Ngày**: 2026-10-25 · **Plan**: [plan.md](plan.md)

Mọi kịch bản chạy với **Firebase Emulator** và dữ liệu seed của nhóm, **không** chạy trên project thật (constitution IV). Chi tiết model và quy tắc nằm ở [data-model.md](data-model.md), [contracts/](contracts/).

## 1. Chuẩn bị

1. `local.properties`: `USE_EMULATOR=true` (đọc qua Secrets Gradle Plugin như 010).
2. Mở Emulator ở một terminal: `npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow`.
3. Nạp dữ liệu seed của nhóm ([scripts/seed/README.md](../../scripts/seed/README.md)):
   ```bash
   cd scripts/seed && npm install && node seed.mjs --emulator --reset
   ```
   Kết quả: 22 quận, 4 tiện ích, 10 cơ sở `venues/demo-*`.
4. Nạp đánh giá demo cho `demo-k3` (research R13): seed của 070 (T025) nếu đã có, không thì `node seed-reviews-030.mjs --emulator` (script do task của 030 tạo, từ chối chạy trên project thật).
5. Tạo trong Auth Emulator:
   - `player@test.local`: người chơi, đã qua `on-signup`;
   - tài khoản có `uid = demo-owner-1`, claim `appRole = OWNER` (dùng `scripts/admin/grant-role.ts` với Emulator), dùng cho FR-018.
6. Tạo thêm trạng thái để kiểm thử, sửa tay trên Emulator UI (`localhost:4000`):
   - `demo-gia-han.status = HIDDEN`;
   - `demo-nu-cuoi.status = DELETED`;
   - một sân con của `demo-minh-tam-cn1` có `isActive = false`.

**Lưu ý dữ liệu seed:**
- Tọa độ là gần đúng, marker có thể lệch vài trăm mét.
- Số điện thoại bị che (`0944xxx486`) nên không mở được trình gọi; đây là hành vi đúng (R9).
- Ảnh tải từ máy chủ Alobo, cần mạng thật; không tải được thì hiện ảnh mặc định.

## 2. Test tự động

| Lệnh | Kiểm chứng |
|---|---|
| `./gradlew testDebugUnitTest` | Hàm thuần và ViewModel: `SearchWindowArgs`, `OpeningHours`, `CourtSummary`, `PriceTableBuilder`, `PhoneDisplay`, `PendingActionResolver`, `VenueRatingSummary`, `PhotoUrlResolver`, ma trận `ZoomableImageView`, `CheckVenueBookableUseCase`, `CourtDetailViewModel` (Turbine, repository giả), test vector giá dùng chung |
| `cd firestore-tests && npm test` | VD-1 → VD-10 ([contracts/security-rules.md](contracts/security-rules.md)); V-1 → V-5 của 020 và test `reviews` của 070 vẫn xanh. **Sẽ đỏ cho tới khi D, C sửa Rules**: ghi rõ trong PR |
| `./gradlew connectedDebugAndroidTest` (nên có) | Espresso `CourtDetailFlowTest` trên Emulator: mở `demo-truong-chinh-909` từ deep link có khung giờ → thấy thẻ "Khung bạn chọn"; khách bấm "Đặt sân" → hộp mời đăng nhập → đăng nhập → lưới giờ mở |
| `./gradlew lint` | 0 lỗi mức Error |

## 3. Ca giá dùng chung (SC-002)

Các ca này nằm trong `app/src/test/resources/pricing/window-price-vectors.json` (R4). Test của 020, 030 và 040 cùng nạp file này. Ngày: Thứ ba 27/10/2026, Thứ bảy 31/10/2026, Chủ nhật 01/11/2026, Thứ hai 26/10/2026 (`daysOfWeek` theo ISO như seed).

| # | Cơ sở | Khung | Giá mong đợi | Dòng được tô | Kiểm chứng |
|---|---|---|---|---|---|
| P1 | `demo-truong-chinh-909` | T3 19:00–21:00 | **130.000 đ/giờ** | 18:00–24:00 | US2-2 |
| P2 | `demo-truong-chinh-909` | T3 17:00–19:00 | **110.000 đ/giờ** (90k + 130k, chia 2 giờ) | 16:00–18:00 và 18:00–24:00 | US2-3, khung giữa hai mức |
| P3 | `demo-truong-chinh-909` | T3 04:00–06:00 | **Liên hệ** (không có giá trước 05:00) | — | Bảng giá không phủ hết khung |
| P4 | `demo-truong-chinh-909` | T7 22:00–24:00 | **100.000 đ/giờ** | 05:00–24:00 | `endTime = "2400"` |
| P5 | `demo-k3` | T7 11:00–13:00 | **75.000 đ/giờ** (110k + 40k) | 04:00–12:00, 12:00–14:00 | |
| P6 | `demo-quang-vinh` | T2 16:00–18:00 | **80.000 đ/giờ** | nhóm "Thứ 2" | Nhóm ngày riêng cho Thứ 2 |
| P7 | `demo-quang-vinh` | T3 16:00–18:00 | **90.000 đ/giờ** | nhóm "Thứ 3 – Thứ 6" | |
| P8 | `demo-son-ta` | CN 23:00–24:00 | **90.000 đ/giờ** | nhóm "Chủ nhật" | |
| P9 | `demo-minh-tam-cn1` | T3 18:00–20:00 | **76.000 đ/giờ** | 18:00–23:00 | Seed làm tròn 75k lên 76k (README seed) |

## 4. Kịch bản thủ công trên máy ảo (Emulator)

| # | Bước | Kết quả mong đợi | Spec |
|---|---|---|---|
| Q1 | Đăng nhập `player`, tìm "Trường Chinh" ở 020 với T3 19:00–21:00, bấm thẻ | Phần đầu (ảnh, tên, điểm, giá thanh dưới, "Đặt sân") hiện **< 3 giây** (đo 20 lần bằng Logcat trên máy ảo giới hạn mạng LTE, đạt khi ≥ 19/20); "10 sân · PU"; giá P1 trùng giá trên thẻ 020. Bấm Back → về đúng danh sách kết quả, giữ bộ lọc và vị trí cuộn | US1-1, SC-001, SC-002, FR-002 |
| Q2 | Vuốt thư viện ảnh, chạm một ảnh, phóng to bằng hai ngón, vuốt sang ảnh khác | "1/3" → "3/3"; xem toàn màn hình phóng 1×–4×; đang phóng thì vuốt không chuyển ảnh | US1-2 |
| Q3 | Mở `smashnow://venue/demo-quang-vinh` lúc 20:00 và 23:30 (đổi giờ máy ảo) | "05:00–23:00 · Đang mở cửa" / "Đã đóng cửa · Mở lại 05:00" | US1-4 |
| Q4 | Mở `demo-son-ta` | "Mở cả ngày"; bảng giá 3 nhóm "Thứ 2 – Thứ 6", "Thứ 7", "Chủ nhật" | Edge case 24 giờ, R4 |
| Q5 | Mở `demo-quang-vinh` không qua tìm kiếm | Ba nhóm "Thứ 2", "Thứ 3 – Thứ 6", "Thứ 7, Chủ nhật"; không dòng nào được tô | US2-1, US2-5 |
| Q6 | Mở `demo-minh-tam-cn1` (đã tắt 1 sân con) | "2 sân · PU" | US1-3, FR-006 |
| Q7 | Mở bất kỳ cơ sở seed | Mục "Tiện ích" hiện "Chưa có thông tin tiện ích" (seed có `amenityIds = []`) | R13 |
| Q8 | Bấm "Chỉ đường" trên máy có Google Maps, rồi trên máy ảo không có ứng dụng bản đồ | Mở Google Maps với điểm đến là cơ sở / mở trình duyệt | US3-2, US3-3 |
| Q9 | Bấm số điện thoại (seed bị che) | Không mở trình gọi; nhấn giữ thì sao chép. Sửa tay `phone = 0901234567` rồi bấm → trình gọi mở với số điền sẵn | US3-4, R9 |
| Q10 | Tắt mạng máy ảo, mở mục "Vị trí" | Bản đồ ẩn, vẫn có địa chỉ và "Chỉ đường"; các mục khác bình thường | US3-5, FR-013 |
| Q11 | Mở `demo-k3` (đã có đánh giá demo) | Điểm và số lượt khớp 11 đánh giá `VISIBLE`; 3 đánh giá mới nhất không có đánh giá `HIDDEN`; đánh giá ẩn danh hiện "Người dùng đã xóa"; "Xem tất cả (11)" mở danh sách 070 | US4, SC-006 |
| Q12 | Mở cơ sở chưa có đánh giá | "Sân chưa có đánh giá", không có "Xem tất cả" | US4-6 |
| Q13 | Đăng xuất (khách), mở `demo-truong-chinh-909` có khung giờ, bấm "Đặt sân" → "Đăng nhập" → đăng nhập `player` | Quay về đúng cơ sở, lưới giờ 040 tự mở ở đúng ngày và khung | US5-3, SC-004 |
| Q14 | Như Q13 với trái tim; lặp lại với nút chat | Sân được lưu vào yêu thích / mở hội thoại 091 | US5-4, US5-5 |
| Q15 | Khách bấm "Đặt sân" → "Đăng nhập" rồi bấm Back ở màn hình đăng nhập | Ở lại chi tiết sân, không có thao tác nào chạy | US5-6 |
| Q16 | Đăng nhập `demo-owner-1`, chuyển sang chế độ người chơi (011), mở cơ sở seed, bấm "Đặt sân" và chat | "Đây là cơ sở của bạn", không mở lưới giờ hay chat | FR-018 (xem R11 về US5-7) |
| Q17 | Người chơi mở `demo-gia-han` (`HIDDEN`) | Thông tin đầy đủ kèm "Sân tạm ngừng hoạt động"; không có "Đặt sân", chat; trái tim không thêm được | US6-1, US6-3 |
| Q18 | Khách mở `smashnow://venue/demo-gia-han` | Màn hình rút gọn "Sân tạm ngừng hoạt động" kèm "Đăng nhập" | R10 |
| Q19 | Mở `demo-nu-cuoi` (`DELETED`) và `smashnow://venue/khong-co`, cả khi đăng nhập lẫn khách | "Sân không còn tồn tại" kèm "Quay lại" (thêm "Bỏ khỏi yêu thích" nếu đang yêu thích) | US6-4, FR-022 |
| Q20 | Đang xem `demo-k3`, sửa `status = LOCKED` trên Emulator UI, bấm "Đặt sân" | Báo "Sân tạm ngừng hoạt động", màn hình cập nhật, không mở lưới giờ | US6-5, FR-023 |
| Q21 | Mở một cơ sở, tắt mạng, quay lại rồi mở lại cơ sở đó; mở một cơ sở chưa từng xem | Hiện dữ liệu cũ kèm banner "thông tin có thể chưa cập nhật" / lỗi kèm "Thử lại", không crash | FR-026, SC-007 |
| Q22 | Xoay màn hình khi đang ở ảnh 3 và đã cuộn giữa trang; đổi sang chế độ tối | Giữ ảnh 3 và vị trí cuộn; màu đúng theme tối | Edge case, FR-027 |
| Q23 | Chạy trên máy ảo máy tính bảng (`w600dp`) | Hai cột; thanh "Đặt sân" vẫn ở cuối | FR-027 |
| Q24 | Bật TalkBack, vuốt qua màn hình | Đọc đúng thứ tự tên → địa chỉ → điểm → trạng thái → giá; mọi nút có nhãn | FR-027 |

| Q25 | Khi spec 111 đã có: chủ sân tạo cơ sở mới qua giao diện (ảnh tự chụp, có tiện ích, 2 loại mặt sân), rồi chạy lại Q1, Q6, Q9 trên cơ sở đó | Ảnh tải từ Supabase, tiện ích hiện đủ, "N sân · PU, M sân · Thảm", số điện thoại mở trình gọi | T077, quyết định C1 |
| Q26 | 5 người ngoài nhóm tìm giá cao điểm, giờ đóng cửa, số điện thoại của `demo-k3` | ≥ 4/5 người xong dưới 30 giây | SC-003, T078 |

Lưu ảnh chụp Q1, Q11, Q13, Q17 vào `docs/features/03-court-detail/screenshots/` theo tên `03_court-detail_buocX_mo-ta.png`. Mỗi ảnh dùng dữ liệu seed phải kèm ghi chú "Dữ liệu demo lấy từ datlich.alobo.vn, chỉ dùng cho mục đích học tập" (quyết định C1).
