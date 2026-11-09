# AI Log — Feature 060 (My Bookings)

## Entry 2026-11-09: qrToken khi duyệt + COMPLETED/NO_SHOW định kỳ + hiển thị đơn nhiều block theo block

### 1. Bối cảnh & Yêu cầu
- **Prompt:** "PROMPT — SmashNow 060: qrToken khi duyệt + COMPLETED/NO_SHOW định kỳ + hiển thị đơn nhiều block theo block"
- **Mục tiêu:**
  1. Sinh `qrToken` tại Edge Function `owner-approve-booking` khi chuyển đơn `PENDING` -> `CONFIRMED` để QR code hiển thị trên màn chi tiết đơn 060; hỗ trợ quét check-in bằng `qrToken` tại `BookingCheckInRepositoryImpl` mà không đổi contract API `checkin-booking`.
  2. Tạo Edge Function mới `complete-bookings` quét các đơn `CONFIRMED` đã qua `endAt` (giờ server): đã check-in (`checkedInAt != null`) chuyển `COMPLETED` + `completedAt = now`; chưa check-in chuyển `NO_SHOW`. Idempotent, server time, kèm migration `pg_cron` định kỳ 5 phút.
  3. Suy block từ `items` (`BookingBlockCalculator` pure function): tính tổng giờ chơi thật và thời gian nghỉ (gap); màn chi tiết `BookingDetailFragment` hiển thị theo block ("20:00–21:30, 22:30–23:30"), giờ chơi thật ("2 giờ 30 phút") và ghi chú gap ("Nghỉ 1 giờ giữa giờ"). Chặn tạo đơn vi phạm gap đúng 1 slot (30 phút) tại `BookingDocumentBuilder` / `confirmHoldToBooking`.

### 2. Thay đổi thực hiện
- **Việc 1 (qrToken & Check-in Lookup):**
  - `supabase/functions/owner-approve-booking/handler.ts`: Sinh `const qrToken = crypto.randomUUID()` khi duyệt đơn `PENDING` -> `CONFIRMED`; ghi nguyên tử vào doc `bookings/{bookingId}` và trả về trong JSON response.
  - `app/src/main/java/com/example/booking_app/feature/owner/data/BookingCheckInRepositoryImpl.kt`: Tra cứu `bookings` theo `qrToken == code` (hoặc docId) để trích xuất `bookingId` và `venueId` chuẩn trước khi gọi Edge Function `checkin-booking`.
  - `supabase/functions/checkin-booking/handler.ts`: Bổ sung fallback tra cứu theo `qrToken` khi docId không khớp trực tiếp, giữ nguyên contract (`venueId`, `bookingId`, `confirmPayment`).
  - `firestore.rules`: Bổ sung `isApprovedOwner()` vào `allow read` trên collection `bookings` để chủ sân tra cứu được mã check-in.
- **Việc 2 (Scheduled Function `complete-bookings`):**
  - `supabase/functions/complete-bookings/handler.ts` & `index.ts`: Quét các đơn `CONFIRMED` có `endAt <= now` (giờ server); nếu `checkedInAt != null` chuyển `COMPLETED` và ghi `completedAt = nowIso`; nếu chưa check-in chuyển `NO_SHOW`.
  - `supabase/migrations/20261109203000_cron_complete_bookings.sql`: Lập lịch `pg_cron` gọi `complete-bookings` mỗi 5 phút qua `net.http_post` với header `x-cron-secret`.
  - `supabase/config.toml`: Thêm `[functions.complete-bookings]` (`verify_jwt = false`).
  - Kiểm chứng 070: Đọc `supabase/functions/review/handler.ts` xác nhận đơn `COMPLETED` kèm `completedAt` mở cửa sổ đánh giá trong 7 ngày (`canEditUntil = completedAt + 7 days`). Cơ chế `complete-bookings` hoàn toàn tương thích và kích hoạt đúng quyền đánh giá của 070.
- **Việc 3 (Hiển thị nhiều block & Validation gap):**
  - `feature/booking_management/domain/BookingBlockCalculator.kt`: Pure function `calculateBlocks(items)` sắp xếp theo `startAt`, gộp các slot liên tiếp, tính tổng thời gian chơi thực tế `totalPlayingMinutes`, `spanMinutes`, và `totalGapMinutes`. Định dạng hiển thị chuỗi thời gian, thời lượng và ghi chú gap ("Nghỉ X phút/giờ giữa giờ").
  - `BookingDetailFragment.kt`: `bindDetail` cập nhật dòng thời gian liệt kê các block và dòng sân hiển thị tổng giờ chơi thật kèm ghi chú gap khi đơn có > 1 block.
  - `BookingDetailViewModel.kt`: Bổ sung fallback `tokenToUse = detail.qrToken ?: detail.bookingId` để đơn `CONFIRMED` luôn render mã QR check-in ngay cả khi test dữ liệu thủ công trên Firestore chưa có trường `qrToken`.
  - `MyBookingAdapter.kt`: Phân định nút thẻ đơn: `PENDING` hiển thị "Xem chi tiết", chỉ `CONFIRMED` mới hiển thị "Mã check-in" kèm biểu tượng quét QR.
  - `BookingDocumentBuilder.kt` & `FirestoreBookingRemoteDataSource.kt`: Bổ sung `validateSlotGaps` từ chối các đơn có khoảng trống lẻ đúng 1 slot (30 phút) giữa 2 khối trên cùng sân (ném `IllegalArgumentException`), trả về `ConfirmBookingResult.Error` rõ ràng, không crash.

### 3. Kết quả kiểm chứng
- **Deno tests:**
  - `deno test -A supabase/functions/owner-approve-booking/handler.test.ts`: **5/5 passed (100%)**
  - `deno test -A supabase/functions/complete-bookings/handler.test.ts`: **5/5 passed (100%)**
  - `deno test -A supabase/functions/checkin-booking/handler.test.ts`: **2/2 passed (100%)**
- **Android Gradle Unit tests:**
  - `BookingBlockCalculatorTest`: **6/6 passed (100%)** (1 block, 2 blocks gap 1h, 3 blocks, danh sách chưa sort, 2 block kề nhau gộp 1, danh sách rỗng).
  - `BookingDocumentBuilderGapValidationTest`: **5/5 passed (100%)** (gap 1 slot reject, gap 2 slot pass, 1 block pass, 1 slot pass, đa sân không ảnh hưởng).
  - Toàn bộ test suite `feature/booking_management`: **53/53 passed (100%)**.
  - `BookingConfirmSliceTest`: **4/4 passed (100%)**.
- **Quy tắc:** 2 commit local; không push, không mở PR, không deploy tự động.
