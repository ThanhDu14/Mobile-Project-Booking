# Hướng dẫn tích hợp và kiểm thử: Feature 112

Tài liệu này ghi lệnh xác minh checkout và các kịch bản tích hợp. Feature 112 hiện có owner home dashboard, calendar ngày/tuần đọc venues/courts/slots thật qua Firestore listener, drop-in form validation và owner access Rules tests. Booking decisions, drop-in server lifecycle/roster, QR, voucher và reports chưa được implement vì phụ thuộc contract/schema của owner tương ứng.

## Điều kiện chuẩn bị khi triển khai

- Android SDK/Gradle; Firebase Auth/Firestore Emulator; Supabase local/Edge Functions cho khi function contract và harness được chốt.
- Fixture venues/courts/priceRules, owner được duyệt, booking/slots, drop-in registrations, voucher và thống kê. Chỉ dùng field/enum đã được schema chung duyệt.
- Contract server cho booking, owner drop-in, voucher, check-in và schema `dailyStats` phải được nhóm phụ trách chốt; không điền bằng mock API tự chế.

## Kịch bản xác nhận

1. **Booking queue và lịch**: tạo PENDING hợp lệ + slot BOOKED; kiểm tra sắp theo deadline và lịch ngày/tuần. Duyệt thành công giữ slot; từ chối giải phóng đúng slot. Gửi lại sau deadline hoặc sau khi status/slot đổi phải không ghi đè và UI tải trạng thái mới.
2. **Quyền**: thử cùng đọc/ghi với owner không duyệt, venue khác, court ngoài venue, venue locked và quyền thu hồi trong lúc màn hình mở. Server từ chối, không rò dữ liệu chéo.
3. **Drop-in**: tạo trên slot trống trong giờ vận hành; xác nhận toàn bộ slot 30 phút `BLOCKED/DROP_IN`. Tạo đồng thời hoặc có một slot xung đột phải thất bại toàn phần. Sửa giờ/court và hủy phải đồng bộ slot; tăng capacity phải tuân thủ queue 081.
4. **Rosters và QR**: xem đủ status registration; quét QR hợp lệ chỉ check-in một lần. QR sai, lặp, sai session, cancelled hoặc ngoài quyền không đổi trạng thái.
5. **Booking check-in**: trong cửa sổ hợp lệ, quét QR/nhập booking code và xác nhận thu tiền theo FR-012a; kiểm tra server ghi `checkedInAt`/`PAID` theo hành động. `COMPLETED`/`NO_SHOW` do flow server hiện có.
6. **Voucher**: tạo voucher venue hợp lệ; duplicate code toàn hệ thống, sai điều kiện hoặc venue ngoài quyền bị từ chối. Xác minh owner không ghi `usedCount`/redemptions; áp dụng/giá giảm do Feature 050.
7. **Thống kê**: đối chiếu từng nhóm `DEPOSITED`, `PAID`, `REFUNDED`, canceled/rejected/expired; occupancy loại BLOCKED và không đếm slot hai lần; peak theo bucket 60 phút địa phương. Empty/stale/error không hiển thị thành số liệu mới.
8. **Trạng thái UI/network**: kiểm tra loading, empty, validation, stale/conflict, permission, retry và mất mạng; sau kết quả không xác định tải lại trước khi gửi lại để tránh trùng.

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

Task chỉ được đánh dấu hoàn thành khi có lệnh thực thi thành công và artifact kiểm chứng; các gate contract chưa được giải quyết tiếp tục mở trong `tasks.md`.
