# Quickstart: kiểm chứng spec 081 (hàng chờ)

Làm theo [quickstart 080](../080-drop-in-session/quickstart.md) mục 1 để dựng Emulator và Supabase local. Chi tiết xem [data-model.md](data-model.md) và [contracts/edge-functions-waitlist.md](contracts/edge-functions-waitlist.md).

> Chỉ chạy trên Firebase Emulator và Supabase local (constitution IV).

## 1. Chuẩn bị

```bash
npx tsx scripts/seed/080-dropin.ts      # dữ liệu nền của spec 080
npx tsx scripts/seed/081-waitlist.ts    # thêm dữ liệu cho hàng chờ
```

Script `081-waitlist.ts` tạo:
- Buổi `W1`: 10 chỗ, đã đủ người, bắt đầu sau 2 ngày.
- Buổi `W2`: trùng giờ với `W1`, đã đủ người.
- Buổi `W3`: đủ người, bắt đầu sau 35 phút. Dùng để thử đóng hàng chờ.
- Tài khoản `player3`…`player12`.

## 2. Test tự động

```bash
cd firestore-tests && npm test -- dropin                                   # gồm W1, W2
deno test -A supabase/functions/register-drop-in supabase/functions/cancel-drop-in \
             supabase/functions/dropin-close supabase/functions/_shared/waitlist.test.ts   # Q1–Q14
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.dropin.*"
```

**Kết quả mong đợi:** tất cả đều xanh. Ca Q6 in ra `runs=20 overCapacity=0 doublePromoted=0 orderViolations=0`.

## 3. Kịch bản thủ công

| # | Bước | Kết quả đúng | Liên quan |
|---|---|---|---|
| M1 | 3 máy (`player3`, `player4`, `player5`) lần lượt mở `W1` và bấm "Vào hàng chờ" | Thấy vị trí 1, 2, 3; buổi vẫn "Đã đủ người"; màn hình ghi "Chưa giữ chỗ, chưa thanh toán" kèm số tiền sẽ ghi nhận | US1, SC-001 |
| M2 | `player4` bấm "Rời hàng chờ" | Máy `player5` đổi từ vị trí 3 sang 2 mà không cần tải lại; `player4` không bị ghi khoản nào | US3, FR-005 |
| M3 | Một người đã đăng ký `W1` (1 người) hủy | Người chờ đầu tiên có nhóm vừa chỗ thấy vé trong "Lượt vãng lai của tôi" trong ≤ 10 giây, có nhãn "Được nhận từ hàng chờ"; nhận thông báo "Đã có chỗ cho bạn" | US2, SC-002 |
| M4 | `player6` chờ cả `W1` và `W2`, rồi được đẩy lên `W1` | Lượt chờ ở `W2` chuyển sang "Đã rời hàng chờ" ở tab "Đã qua" | AC 2.7 |
| M5 | Đợi `W3` tới mốc 30 phút trước giờ bắt đầu (hoặc gọi tay `dropin-close`) | Lượt chờ chuyển "Không được nhận chỗ"; nhận thông báo; nút "Vào hàng chờ" biến mất | US4, FR-014 |
| M6 | Mở `W1` khi còn chỗ | Không có nút "Vào hàng chờ", chỉ có "Đăng ký" | AC 1.5 |
| M7 | Bật chế độ máy bay khi đang xác nhận vào hàng chờ, sau đó bấm lại | Báo mất mạng, giữ lựa chọn; cuối cùng chỉ có 1 lượt chờ | AC 1.6 |
| M8 | Chế độ tối, màn hình `w600dp` | Đủ 4 trạng thái, bố cục không vỡ | constitution V |

## 4. Đo nhanh

- **SC-001:** bấm giờ từ lúc mở chi tiết buổi `FULL` tới khi thấy vị trí, mục tiêu < 30 giây.
- **SC-002:** bấm giờ từ lúc hủy trên máy A tới khi vé hiện trên máy B, mục tiêu ≤ 10 giây.
- **SC-006:** hỏi 5 người thử "bạn đang ở vị trí nào, có chỗ thì chuyện gì xảy ra?", ghi kết quả vào báo cáo tuần.
