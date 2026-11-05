# Hướng dẫn tích hợp và kiểm thử: Feature 112

Tài liệu này ghi lệnh xác minh checkout và các kịch bản tích hợp. Feature 112 có owner home dashboard, calendar ngày/tuần đọc venues/courts/slots thật qua Firestore listener, booking queue/detail UI, QR/manual booking check-in UI, drop-in form validation và owner access Rules tests. Booking queue chưa có safe server projection; booking decisions, booking check-in API, drop-in server lifecycle/roster, voucher CRUD và report data source vẫn phụ thuộc contract/schema của owner tương ứng. Các repository chờ contract đều fail-closed và không hiển thị dữ liệu giả.

## Điều kiện chuẩn bị khi triển khai

- Android SDK/Gradle; Firebase Auth/Firestore Emulator; Supabase local/Edge Functions cho khi function contract và harness được chốt.
- Fixture venues/courts/priceRules, owner được duyệt, booking/slots, drop-in registrations, voucher và thống kê. Chỉ dùng field/enum đã được schema chung duyệt.
- Contract server cho booking, owner drop-in, voucher, check-in và schema `dailyStats` phải được nhóm phụ trách chốt; không điền bằng mock API tự chế.
- Booking queue dùng bộ lọc Chờ duyệt/Sắp tới/Lịch sử và chọn venue; cho tới khi owner-safe projection tồn tại, màn hình báo không có dịch vụ đọc booking thay vì đọc Firestore raw.
- Booking QR scanner dùng Google Play Services Code Scanner và có thể nhập mã thủ công; app chỉ gửi lệnh sau khi contract `checkin-booking` được Feature 060/database owner xác nhận.

## Kịch bản xác nhận

1. **Booking queue và lịch**: tạo PENDING hợp lệ + slot BOOKED; kiểm tra sắp theo deadline và lịch ngày/tuần. Duyệt thành công giữ slot; từ chối giải phóng đúng slot. Gửi lại sau deadline hoặc sau khi status/slot đổi phải không ghi đè và UI tải trạng thái mới.
2. **Quyền**: thử cùng đọc/ghi với owner không duyệt, venue khác, court ngoài venue, venue locked và quyền thu hồi trong lúc màn hình mở. Server từ chối, không rò dữ liệu chéo.
3. **Drop-in**: tạo trên slot trống trong giờ vận hành; xác nhận toàn bộ slot 30 phút `BLOCKED/DROP_IN`. Tạo đồng thời hoặc có một slot xung đột phải thất bại toàn phần. Sửa giờ/court và hủy phải đồng bộ slot; tăng capacity phải tuân thủ queue 081.
4. **Rosters và QR**: xem đủ status registration; quét QR hợp lệ chỉ check-in một lần. QR sai, lặp, sai session, cancelled hoặc ngoài quyền không đổi trạng thái.
5. **Booking check-in**: trong cửa sổ hợp lệ, quét QR/nhập booking code và xác nhận thu tiền theo FR-012a; kiểm tra server ghi `checkedInAt`/`PAID` theo hành động. `COMPLETED`/`NO_SHOW` do flow server hiện có.
6. **Voucher**: tạo voucher venue hợp lệ; duplicate code toàn hệ thống, sai điều kiện hoặc venue ngoài quyền bị từ chối. Xác minh owner không ghi `usedCount`/redemptions; áp dụng/giá giảm do Feature 050.
7. **Thống kê**: đối chiếu từng nhóm `DEPOSITED`, `PAID`, `REFUNDED`, canceled/rejected/expired; occupancy loại BLOCKED và không đếm slot hai lần; peak theo bucket 60 phút địa phương. Empty/stale/error không hiển thị thành số liệu mới.
8. **Trạng thái UI/network**: kiểm tra loading, empty, validation, stale/conflict, permission, retry và mất mạng; sau kết quả không xác định tải lại trước khi gửi lại để tránh trùng.
9. **Booking queue UI**: lọc từng trạng thái, đổi venue, refresh/retry, thời hạn đếm ngược và chi tiết. Contract chưa deploy thì không hiển thị booking mẫu; approve/reject bị vô hiệu hóa.
10. **Booking check-in UI**: mở scanner QR, nhập mã thủ công, xác nhận checkbox thu tiền, thử hủy/quyền camera của Google scanner và lỗi/offline. Không báo thành công khi server contract/function chưa có.

## Lệnh kiểm thử trong checkout

- Android build: `./gradlew :app:assembleDebug`
- Android unit tests: `./gradlew :app:testDebugUnitTest`
- Android UI tests (cần emulator/thiết bị): `./gradlew :app:connectedDebugAndroidTest`
- Android lint: `./gradlew :app:lintDebug`
- Firestore Rules emulator tests: `npm test` trong `firestore-tests/`.
- Edge Function tests: chưa có owner function hoặc test harness riêng để chạy `deno test`; không tính helper tests thuộc Feature 080/081 là coverage của Feature 112.
- Diff whitespace check: `git diff --check`.

## Kết quả phiên audit/implementation

| Lệnh | Kết quả |
|---|---|
| `:app:assembleDebug` | **PASS** — `BUILD SUCCESSFUL` (2026-11-05). |
| `:app:testDebugUnitTest` | **FAIL** — 515 tests completed, 7 failed, 2 skipped. All 7 failures are in existing `FirebaseSessionManagerTest`; OwnerUiAccessibilityTest's two assertions pass after the 48dp and `w600dp` layout fixes. |
| Focused owner unit tests | **PASS** — `OwnerUiAccessibilityTest` (2), `OwnerCalendarViewModelTest` (3), and `DropInValidationTest` (4), after rerun for the current fake-repository implementation (2026-11-05). |
| `:app:compileDebugAndroidTestKotlin` | **PASS** — instrumentation test APK and `OwnerCalendarLayoutTest` compile. |
| `:app:connectedDebugAndroidTest` | **BLOCKED** — Android test APK compiled, then Gradle reported `No connected devices!`; UI test did not execute. |
| `:app:lintDebug` | **FAIL** — 1 error and 357 warnings. The error is outside Feature 112: `feature/booking/ui/adapter/SlotMatrixRowAdapter.kt:203`, `DiffUtilEquals` (`===` vs `==`). The Feature 112 `SetTextI18n` warning found earlier was fixed by moving display text into string resources. |
| `firestore-tests npm test` | **PASS** — Firebase CLI 15 + Firestore Emulator: 17 files, 151 tests passed (2026-11-05), including the new owner access matrix. Transaction/Edge Function suites remain unavailable because the owning functions/contracts and harness are not present. |
| `deno test` | **BLOCKED** — no Feature 112 Edge Function path/test harness; `deno` is not installed. |
| `git diff --check` | PASS after final code/document edits (2026-11-05); Git only reports configured LF-to-CRLF normalization notices. |

### Verification after booking/check-in UI implementation (2026-11-05)

| Command | Result |
|---|---|
| `:app:assembleDebug` | **PASS** after QR scanner package fix. |
| Focused unit tests (owner accessibility, booking queue, booking check-in ViewModel) | **PASS** — 8 tests. |
| Full `:app:testDebugUnitTest` | **FAIL** — 521 tests, 7 failures and 2 skipped; all 7 failures are existing `FirebaseSessionManagerTest` assertions; no Feature 112 failures. |
| `:app:compileDebugAndroidTestKotlin` | **PASS** — booking/check-in layout test compiles. |
| `:app:connectedDebugAndroidTest` | **BLOCKED** — Android Gradle reports `No connected devices!`. |
| `:app:lintDebug` | **FAIL** — 1 existing Feature 040 `DiffUtilEquals` error at `SlotMatrixRowAdapter.kt:203`, 362 warnings; no new booking/check-in/voucher-file lint findings. |
| Voucher validator test | **PASS** — 3 focused tests after correcting the expected validation error count. |

## Tiến độ phiên implementation tiếp theo

- Đã thêm booking domain/queue filtering, `OwnerBookingsViewModel`, fail-closed read repository, booking queue Fragment/XML và Owner bottom-nav route.
- Đã thêm booking check-in domain input/result/interface, fail-closed repository, ViewModel, QR scanner bằng Google Play Services và đường nhập mã thủ công. Request body chưa được tạo vì README chỉ nêu tên `checkin-booking`, không định nghĩa payload/response.
- Đã thêm queue sorting/countdown tests, check-in ViewModel tests, Android layout/theme/size test; kết quả focused đang chờ lần chạy cuối. Build đầu tiên với scanner gặp import package sai; đã sửa import theo package thực trong AAR. Build tiếp theo dừng ở quyền truy cập Kotlin/Android SDK cache ngoài workspace; cần chạy qua sandbox escalation để có kết quả hợp lệ.
- Chưa chạy connected UI tests; emulator/device cần được kết nối. Chưa tạo booking/drop-in/voucher/statistics backend, vì server contract/ownership còn mở.

Task chỉ được đánh dấu hoàn thành khi có lệnh thực thi thành công và artifact kiểm chứng; các gate contract chưa được giải quyết tiếp tục mở trong `tasks.md`.
