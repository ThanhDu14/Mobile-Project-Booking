# Quickstart: kiểm chứng spec 080 (đặt lịch vãng lai)

Hướng dẫn chạy để chứng minh tính năng đúng spec. Chi tiết dữ liệu xem [data-model.md](data-model.md). API xem [contracts/edge-functions-dropin.md](contracts/edge-functions-dropin.md). Ca test Rules và Edge Function xem [contracts/security-rules.md](contracts/security-rules.md).

> Chỉ chạy trên Firebase Emulator và Supabase local (constitution IV). Không dùng project thật.

## 1. Chuẩn bị

1. Cài sẵn: Firebase CLI, Supabase CLI, Deno, Node 20, Android Studio.
2. Khởi động môi trường local:

   ```bash
   firebase emulators:start --only auth,firestore
   supabase start
   supabase functions serve --env-file supabase/.env.local
   ```

   `.env.local` trỏ Firestore REST tới emulator (`FIRESTORE_EMULATOR_HOST`), không commit file này.
3. Nạp dữ liệu demo:

   ```bash
   npx tsx scripts/seed/080-dropin.ts
   ```

   Script tạo:
   - 3 quận, 4 cơ sở (1 cơ sở `HIDDEN`).
   - 20 buổi vãng lai: 3 trình độ, đủ 4 khung giờ, giá 40.000–120.000 đ. Trong đó 1 buổi `FULL`, 1 buổi `CANCELLED`, 1 buổi đã qua, 1 buổi còn đúng 3 chỗ, 1 buổi bắt đầu sau 3 giờ.
   - Tài khoản: `player1@test.local`, `player2@test.local`, `owner1@test.local` (chủ của các cơ sở).
4. App chạy bản `debug` với cờ `USE_EMULATORS=true` (trỏ tới `10.0.2.2`).

## 2. Test tự động

```bash
# Rules: các ca D1–D13
cd firestore-tests && npm test -- dropin

# Edge Function: các ca E1–E15, gồm 20 lời gọi đồng thời (SC-002)
deno test -A supabase/functions/register-drop-in supabase/functions/cancel-drop-in supabase/functions/_shared/dropin.test.ts

# Unit test app: ViewModel, UseCase, RegistrationOutcomeResolver, tính tiền hoàn (bản hiển thị)
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.dropin.*"
```

**Kết quả mong đợi:** tất cả đều xanh. Ca E1 in ra `success=5 full=15 registeredCount=12/12`.

## 3. Kịch bản thủ công trên app

| # | Bước | Kết quả đúng | Liên quan |
|---|---|---|---|
| Q1 | Đăng nhập `player1` (hồ sơ có khu vực Quận 10), mở "Vãng lai" | Danh sách sắp theo giờ bắt đầu, chip "Quận 10" đã chọn sẵn; không thấy buổi đã qua, buổi bị hủy, buổi của cơ sở `HIDDEN`; buổi `FULL` có nhãn "Đã đủ người" | US1, AC 1.1, 1.2, 1.4, 1.5 |
| Q2 | Thêm lọc thứ Bảy + Tối + Trung bình + ≤ 80.000 đ, sau đó bỏ từng chip | Chỉ còn buổi thỏa đủ điều kiện; bỏ chip thì danh sách mở rộng lại; lọc không có kết quả thì hiện màn hình trống | AC 1.3, 1.6 |
| Q3 | Mở chi tiết buổi còn 3 chỗ trên máy A. Trên máy B (`player2`) đăng ký 1 người | Máy A đổi "còn 3/…" thành "còn 2/…" trong ≤ 5 giây mà không cần tải lại | AC 1.7, SC-006 |
| Q4 | `player1` đăng ký 2 người, "Thanh toán ngay (giả lập)" | Ô số người tối đa là 2. Vé hiện "Đã thanh toán", có QR, mã `DI-…`, số người 2. Buổi chuyển "Đã đủ người". Có nhãn "thanh toán giả lập" | AC 2.1–2.3, FR-010 |
| Q5 | Ở màn hình xác nhận: bật chế độ máy bay, bấm "Xác nhận", tắt máy bay, bấm lại 3 lần | Báo mất mạng, giữ lựa chọn; cuối cùng chỉ có 1 đăng ký (kiểm tra trên Emulator UI) | AC 2.6, 2.8, SC-003 |
| Q6 | Mở lại chi tiết buổi đã đăng ký | Nút đổi thành "Xem vé của bạn" | AC 2.7 |
| Q7 | Mở vé một lần, bật chế độ máy bay, đóng rồi mở lại app, vào "Lượt vãng lai của tôi" và mở vé | Vé và QR hiện trong < 1 giây | FR-015, SC-007 |
| Q8 | Hủy lượt ở buổi còn > 4 giờ | Sheet hiện "Bạn được hoàn 100% (120.000 đ)". Sau khi xác nhận: lượt chuyển "Đã hủy", QR biến mất, buổi tăng 2 chỗ và trở về "Còn chỗ" | US4, AC 4.1, 4.3 |
| Q9 | Đăng ký rồi hủy buổi bắt đầu sau 3 giờ | Sheet báo không hoàn tiền; xác nhận vẫn hủy được | AC 4.2 |
| Q10 | Đăng nhập `owner1`, mở buổi của cơ sở mình | Không có nút "Đăng ký" (hoặc bị từ chối với "Bạn không thể đăng ký buổi do mình mở") | Edge Cases, FR-011 |
| Q11 | Trên Emulator UI, đặt đăng ký của `player2` thành `CHECKED_IN`, chỉnh `endAt` của buổi về quá khứ. Mở "Đã qua" | Lượt hiện "Đã tham gia" kèm nút "Đánh giá buổi chơi". Gửi đánh giá 3 tiêu chí → đánh giá hiện ở danh sách của cơ sở với nhãn "Buổi vãng lai" | US5, AC 5.1, 5.2 |
| Q12 | Với lượt `REGISTERED` của buổi đã qua | Nhãn "Không đến", không có nút đánh giá | AC 3.4, 5.3 |
| Q13 | Trên Emulator UI, đặt `sessionChanged = true` cho một lượt sắp tới, rồi mở vé | Nhãn "Buổi đã thay đổi"; khi hủy, sheet báo hoàn 100% dù còn < 4 giờ | AC 3.5, 4.5 |
| Q14 | Đăng xuất, mở "Vãng lai", bấm "Đăng ký" | Xem được danh sách và chi tiết; bấm "Đăng ký" thì chuyển sang đăng nhập, xong quay lại buổi đó | Assumptions |
| Q15 | Thử ở chế độ tối và trên máy `w600dp` | Đủ 4 trạng thái, QR vẫn nền trắng, bố cục không vỡ | constitution V |

## 4. Đo nhanh

- **SC-001:** 3 người chưa xem app trước, đi từ "Vãng lai" tới khi có vé. Bấm giờ, mục tiêu < 2 phút, ≤ 4 màn hình.
- **SC-006:** dùng Network profiler (4G) đo trang đầu danh sách, mục tiêu < 3 giây.
- **SC-008:** ghi kết quả 5 người thử vào báo cáo tuần.
