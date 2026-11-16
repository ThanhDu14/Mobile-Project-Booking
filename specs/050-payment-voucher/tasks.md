---
description: "Danh sách task triển khai spec 050 – Thanh toán và Khuyến mãi (chuyển khoản trực tiếp cho chủ sân, SePay sandbox), bản 2026-11-16"
---

# Tasks: Thanh toán và Khuyến mãi (Spec 050, bản 2026-11-16)

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/edge-functions.md](contracts/edge-functions.md), [contracts/ui-contracts.md](contracts/ui-contracts.md), [quickstart.md](quickstart.md)

> Thay toàn bộ tasks bản 2026-10-15 (ví giả lập). Không còn task nào về `walletBalance`, nạp ví, `MOCK_WALLET`.

**Tests**: Bắt buộc theo constitution IV (logic tiền, voucher, transaction, Rules). Viết test trước, test phải đỏ rồi mới cài đặt.

**Quy tắc chung**:
- Tiền là `Long`/số nguyên VND.
- Mọi ghi trạng thái đơn, tiền, voucher, mã thanh toán, hoàn tiền chỉ qua Edge Function (constitution II/III).
- Chỉ dùng SePay Test mode. Secret chỉ ở `supabase secrets`.

## Quy ước viết tắt đường dẫn

| Viết tắt | Đường dẫn |
|---|---|
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `PAY/` | `app/src/main/java/com/example/booking_app/feature/payment/` |
| `BOOK/` | `app/src/main/java/com/example/booking_app/feature/booking/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/payment/` |
| `FN/` | `supabase/functions/` |
| `RT/` | `firestore-tests/test/` |

## Format: `[ID] [P?] [Story] Mô tả`

---

## Phase 1: Setup

- [x] T001 Xóa function nạp ví chưa commit: thư mục `FN/wallet-topup-create/`, khối `[functions.wallet-topup-create]` trong `supabase/config.toml`, khối `match /walletTopups/{code}` trong `firestore.rules`
- [x] T002 [P] Thêm khối `verify_jwt = false` cho `booking-payment-confirm`, `booking-payment-transfer`, `owner-mark-refunded`, `refund-report`, `refund-reminders` trong `supabase/config.toml` (giữ `sepay-webhook`)
- [x] T003 [P] Tạo khung package `PAY/{domain/model,domain/usecase,domain/repository,data/remote,data/di,ui}` và file chuỗi `RES/values/strings_payment.xml` với 6 key bắt buộc ở [ui-contracts.md §4](contracts/ui-contracts.md) (`payment_mock_banner`, `payment_sandbox_banner`, `payment_transfer_unsupported`, `payment_hold_expired`, `receipt_mock_note`, `payment_late_refund`)
- [x] T004 [P] Tạo `RES/navigation/nav_payment.xml` (graph lồng: `paymentFragment` → `transferFragment` → `paymentReceiptFragment`, kèm action `receipt → 060` dùng `ReceiptToMyBookingsArgs`) và include vào graph người chơi

---

## Phase 2: Foundational (chặn mọi user story)

### Test viết trước

- [x] T005 [P] Viết bảng ca test tiền dùng chung `specs/050-payment-voucher/contracts/payment-calc-cases.json` (tối thiểu 6 dòng ở [quickstart.md §2](quickstart.md): 200.000 → cọc 60.000; 175.000 → 53.000/122.000; 10% không trần 300.000 → 270.000/81.000; 50% trần 40.000 trên 150.000 → 110.000/33.000; cố định 100.000 trên 80.000 → total 0, miễn phí; 1.000 → cọc 1.000)
- [x] T006 [P] Test Kotlin `TEST/domain/PaymentCalculatorTest.kt` đọc `payment-calc-cases.json` (copy vào `app/src/test/resources/`) và kiểm `discount`, `total`, `depositAmount`, `amountDue(TRANSFER_DEPOSIT|TRANSFER_FULL|AT_VENUE)`, `isFree`
- [x] T007 [P] Test TS `FN/_shared/payment-calc.test.ts` đọc cùng file JSON
- [x] T008 [P] Test `FN/_shared/sepay.test.ts`: `generatePaymentCode()` khớp `^SNP[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$`; `findCodeInContent` tìm được mã có tiền tố/hậu tố ngân hàng và chữ thường; `verifySepaySignature` trả `OK`/`MISSING`/`EXPIRED` (lệch > 300 giây)/`INVALID`; `vietQrUrl` đúng tham số `acc`, `bank`, `amount`, `des`

### Cài đặt

- [ ] T009 Chuyển `BOOK/ui/HoldToPaymentArgs.kt` sang `CORE/navigation/HoldToPaymentArgs.kt` (giữ nguyên mọi trường và Parcelable), sửa import ở `BookingSlotGridViewModel`, `BookingRecurringViewModel`, `BookingRecurringConfigFragment`, `BookingConfirmFragment`, `ConfirmHoldToBookingUseCase`, `FirestoreBookingRemoteDataSource`, `BookingDocumentBuilder` và test tương ứng; chạy `./gradlew testDebugUnitTest` xanh
- [x] T010 [P] Cài `PAY/domain/model/` theo [data-model.md §2](data-model.md): `PaymentOption` (`AT_VENUE`, `TRANSFER_DEPOSIT`, `TRANSFER_FULL`), `PaymentStatus` (`UNPAID`, `AWAITING_TRANSFER`, `DEPOSITED`, `PAID`, `REFUND_PENDING`, `REFUNDED`), `PaymentBreakdown`, `AppliedVoucher`, `VoucherScope`, `Voucher`, `VoucherCheck`, `PayoutAccount`, `TransferInstruction`, `BookingPaymentState`, `PaymentReceipt`, `PaymentError` (`HoldExpired`, `PriceChanged(breakdown)`, `VoucherInvalid(reason)`, `TransferNotSupported`, `Network`, `Unknown`)
- [x] T011 Cài `PAY/domain/PaymentCalculator.kt` (object thuần): `discount = PERCENT ? min(subtotal*value/100, maxDiscount ?: ∞) : value`, chặn `≤ subtotal`; `total = subtotal - discount`; cọc `raw = total*30/100`, `rounded = ((raw+500)/1000)*1000`, `deposit = rounded == 0 ? total : min(rounded, total)`. T006 xanh
- [x] T012 [P] Cài `FN/_shared/payment-calc.ts` cùng công thức với T011. T007 xanh
- [x] T013 Sửa `FN/_shared/sepay.ts`: đổi `TOPUP_CODE_PREFIX` thành `PAYMENT_CODE_PREFIX = "SNP"`, `generateTopupCode` thành `generatePaymentCode`, `topupQrUrl` thành `vietQrUrl(bankBin, accountNumber, amount, code)`; giữ nguyên `verifySepaySignature`, `parseSepayTransaction`, `findCodeInContent`. T008 xanh
- [x] T014 [P] Cài `FN/_shared/payment-voucher.ts` (dùng trong transaction REST): `loadVoucher(tx, venueId, code)` (tìm `venues/{venueId}/vouchers/{code}` trước, rồi `systemVouchers/{code}`; trả cả `scope`), `checkVoucher(voucher, userUsage, subtotal, now)` trả lý do `NOT_FOUND`/`EXPIRED`/`MIN_ORDER`/`OUT_OF_USES`/`PER_USER_LIMIT`, `reserveWrites` (tăng `usedCount`, `userUsage/{uid}.count`, tạo `redemptions/{key}` `status = RESERVED`), `commitWrites` (`RESERVED → USED`, ghi `usedAt`), `releaseWrites` (giảm hai bộ đếm, xóa redemption `RESERVED`), kèm `FN/_shared/payment-voucher.test.ts`
- [x] T015 [P] Cài `FN/_shared/refunds.ts`: `refundCreateWrite({id, bookingId?, recurringGroupId?, venueId, userId, amount, penaltyAmount, reason, sepayTransactionId?})` với `reason ∈ {REJECTED, APPROVAL_TIMEOUT, CANCELLED, LATE_TRANSFER, AMOUNT_MISMATCH}`, `status = PENDING`; ID `refund_{bookingId}` hoặc `refund_sepay_{sepayId}`; kèm test
- [ ] T016 Rules test viết trước `RT/payment.rules.test.ts`: app **không** tạo được `bookings` `status = PENDING`; không đọc/ghi `paymentCodes`, `sepayTransactions`, `userUsage`, `redemptions`; không ghi `refunds`; người chơi đọc refund có `userId` của mình, chủ sân đọc refund của venue mình; người đã đăng nhập đọc được `venues/{v}/vouchers/{code}`; chỉ chủ venue ghi `venues/{v}.payoutAccount`
- [ ] T017 Sửa `firestore.rules` cho T016 xanh: bỏ nhánh `status == 'PENDING'` trong `bookings` create (comment "Lát cắt test 050"); thêm `match /paymentCodes/{c}` và `match /sepayTransactions/{id}` chặn hết; `match /refunds/{id}` read theo `userId`/`ownsVenue(venueId)`, write false; `match /venues/{v}/vouchers/{code}` read `isSignedIn()` (giữ quy tắc ghi hiện có của 111); `userUsage`/`redemptions` chặn client

**Checkpoint**: Công thức tiền khớp hai phía, helper server và Rules sẵn sàng.

---

## Phase 3: User Story 1 – Áp dụng voucher (P1) 🎯 MVP

**Goal**: Màn thanh toán hiện tóm tắt đơn, cho nhập/chọn voucher, hiện lại tiền dưới 1 giây, gỡ được.

**Independent Test**: Đơn 200.000đ, áp mã 20% trần 30.000 → 170.000đ; gỡ → 200.000đ; mã hết hạn hoặc chưa đủ đơn tối thiểu 300.000đ → báo lỗi rõ ràng, giữ giá gốc.

### Test

- [ ] T018 [P] [US1] `TEST/domain/PreviewVoucherUseCaseTest.kt`: `isActive = false`/ngoài `validFrom..validTo` → `Expired`; `subtotal < minOrderTotal` → `MinOrderNotMet(minOrderTotal)`; `usedCount >= usageLimit` → `OutOfUses`; voucher cơ sở ưu tiên hơn hệ thống khi trùng mã
- [ ] T019 [P] [US1] `TEST/ui/PaymentViewModelVoucherTest.kt` (Turbine): áp mã hợp lệ cập nhật `breakdown`; gỡ mã về giá gốc; lỗi hiện ở `voucherError` với câu đúng spec ("Đơn hàng chưa đạt giá trị tối thiểu 200.000đ", "Mã khuyến mãi đã hết hạn hoặc hết lượt sử dụng"); tổng 0đ thì `isFree = true`

### Cài đặt

- [ ] T020 [P] [US1] `PAY/domain/repository/VoucherRepository.kt` + `PAY/data/FirestoreVoucherRepository.kt`: `available(venueId)` đọc song song `venues/{venueId}/vouchers` và `systemVouchers` (`isActive == true`, còn hạn); `findByCode` theo thứ tự cơ sở rồi hệ thống; map `type` `PERCENT`/`FIXED`, `value`, `maxDiscount`, `minOrderTotal`, `validFrom`, `validTo`, `usageLimit`, `usedCount`, `perUserLimit`
- [ ] T021 [US1] `PAY/domain/usecase/PreviewVoucherUseCase.kt`. T018 xanh
- [ ] T022 [US1] `PAY/ui/PaymentViewModel.kt` phần tóm tắt + voucher: nhận `HoldToPaymentArgs` qua `SavedStateHandle`, giữ `requestId` (= `bookingId` của args), state theo `PaymentUiState` ([data-model.md §3](data-model.md)). T019 xanh
- [ ] T023 [P] [US1] Layout `RES/layout/fragment_payment.xml`: banner `payment_mock_banner`, thẻ tóm tắt (tên cơ sở, địa chỉ, danh sách slot/buổi, tạm tính), ô nhập mã + nút Áp dụng/Gỡ, dòng giảm giá, tổng; đủ 4 trạng thái
- [ ] T024 [P] [US1] `PAY/ui/VoucherPickerBottomSheet.kt` + `PAY/ui/VoucherAdapter.kt` (`ListAdapter` + `DiffUtil`) + `RES/layout/bottomsheet_voucher_picker.xml`, `RES/layout/item_payment_voucher.xml`
- [ ] T025 [US1] `PAY/ui/PaymentFragment.kt`: ViewBinding, thu state bằng `repeatOnLifecycle(STARTED)`, nối bottom sheet; `PAY/data/di/PaymentModule.kt` bind repository

**Checkpoint**: Áp/gỡ voucher chạy được ở màn thanh toán (chưa chốt đơn).

---

## Phase 4: User Story 2 – Chọn cách trả và chốt đơn (P1)

**Goal**: Trả tại sân (hoặc miễn phí) chốt đơn sang `PENDING` qua server, hiện phiếu đặt sân hoặc hóa đơn. Chọn chuyển khoản thì sang US4.

**Independent Test**: Đơn 200.000đ trả tại sân → đơn `PENDING`, `paymentStatus = UNPAID`, slot `BOOKED`, phiếu "Chưa thanh toán"; đơn định kỳ 8 buổi → một lần chốt, hóa đơn có `recurringGroupId`.

### Test

- [ ] T026 [P] [US2] `FN/booking-payment-confirm/handler.test.ts`: slot của người khác hoặc hết hạn → 409 `HOLD_EXPIRED`; `expectedTotal` lệch → 409 `PRICE_CHANGED` kèm `breakdown`; voucher hết lượt → 422 `VOUCHER_INVALID` `OUT_OF_USES`; thành công tạo `bookings/{requestId}` (`status = PENDING`, `paymentMethod = AT_VENUE`, `paymentStatus = UNPAID`, `pendingAt`, `approvalDeadline = min(pendingAt + 60 phút, startAt)`), slot `BOOKED`/`holdExpiresAt = null`, redemption `USED`; tổng 0 → `paymentStatus = PAID`; gọi lại cùng `requestId` trả lại kết quả cũ; đơn định kỳ cập nhật `recurringGroups` + mọi buổi; đơn đang `AWAITING_TRANSFER` → `AT_VENUE`/`UNPAID`, `paymentCodes.active = false`; tài khoản `LOCKED` → 403
- [ ] T027 [P] [US2] `TEST/ui/PaymentViewModelConfirmTest.kt`: ẩn chuyển khoản khi `payoutAccount == null` (`transferSupported = false`); bấm xác nhận tại sân gọi `ConfirmAtVenueUseCase` một lần dù bấm 5 lần; `PriceChanged` cập nhật lại tiền và báo; thành công phát `OpenReceipt`

### Cài đặt

- [ ] T028 [US2] `FN/booking-payment-confirm/{handler.ts,index.ts}` theo [edge-functions.md §1](contracts/edge-functions.md): `requireUser`; một `runTransaction`: đọc slot (`status == "HELD"`, `holdBy == uid`, `bookingId` trống hoặc `== requestId`, chưa hết hạn) hoặc nhóm `recurringGroups/{id}` `HOLD`, đọc venue (`venueSnapshot`), tính tiền bằng `payment-calc.ts` từ giá slot đã lưu, voucher bằng `payment-voucher.ts`, ghi đơn + slot + voucher; dựng `items`, `startAt`, `endAt`, `venueSnapshot` như `BookingDocumentBuilder` hiện tại; ghi thông báo `users/{ownerId}/notifications` loại `BOOKING_NEW`. T026 xanh
- [ ] T029 [P] [US2] `PAY/data/remote/PaymentFunctionsApi.kt` (OkHttp + kotlinx.serialization, gắn Firebase ID token, theo mẫu `feature/account/data/AccountFunctionsApi.kt`); ánh xạ mã lỗi sang `PaymentError`
- [ ] T030 [P] [US2] `PAY/domain/repository/VenuePayoutRepository.kt` + `PAY/data/FirestoreVenuePayoutRepository.kt`: đọc `venues/{venueId}.payoutAccount`; chỉ trả `PayoutAccount` khi đủ `bankBin`, `bankShortName`, `accountNumber`, `accountHolderName`
- [ ] T031 [US2] `PAY/domain/repository/PaymentRepository.kt` + `PAY/data/PaymentRepositoryImpl.kt` (`confirmAtVenue`), `PAY/domain/usecase/ConfirmAtVenueUseCase.kt`
- [ ] T032 [US2] Mở rộng `PaymentViewModel` + `fragment_payment.xml`: nhóm chọn `AT_VENUE` / chuyển khoản (con: cọc 30% hiện số tiền cọc và còn lại, hoặc trả đủ), ghi chú `payment_transfer_unsupported`, trạng thái "Miễn phí" khi `isFree`, nút xác nhận đổi nhãn theo lựa chọn. T027 xanh
- [ ] T033 [P] [US2] `PAY/ui/PaymentReceiptFragment.kt` + `ReceiptViewModel.kt` + `RES/layout/fragment_payment_receipt.xml`: đọc lại đơn/nhóm; hiện mã đơn, mã nhóm, các slot/buổi, tạm tính, giảm giá, tiền cọc/đã trả, còn lại, cách trả, dòng `receipt_mock_note`; phiếu đặt sân "Chưa thanh toán" khi `UNPAID`; nút "Xem lịch đặt của tôi"
- [ ] T034 [US2] Nối 040/041 sang `nav_payment`: `BookingSlotGridViewModel` và `BookingRecurringViewModel` điều hướng `action_*_to_payment` với `HoldToPaymentArgs` thay vì đi qua `BookingConfirmFragment` gọi `ConfirmHoldToBookingUseCase`; giữ `BookingConfirmFragment` nếu 040 vẫn dùng làm màn xem lại, nhưng nút chốt chuyển sang `nav_payment`

**Checkpoint**: Đặt sân trả tại sân chạy hết luồng 040/041 → 050 → hóa đơn → 060 qua server.

---

## Phase 5: User Story 4 – Chuyển khoản qua VietQR, xác nhận tự động, hoàn tiền thủ công (P1)

**Goal**: Chọn chuyển khoản → màn QR 15 phút → SePay sandbox báo tiền → tự sang hóa đơn. Tiền muộn hoặc sai số tiền → khoản cần hoàn; chủ sân bấm "Đã hoàn tiền"; nhắc sau 24 giờ; người chơi báo cáo sau 3 ngày.

**Independent Test**: Kịch bản 2–6, 9–10 ở [quickstart.md §3](quickstart.md).

### Test

- [ ] T035 [P] [US4] `FN/booking-payment-transfer/handler.test.ts`: venue thiếu `payoutAccount` → 422 `TRANSFER_NOT_SUPPORTED`; `total == 0` → 409 `INVALID_STATE`; thành công: đơn `HOLD`, `paymentStatus = AWAITING_TRANSFER`, `paymentMethod`, `amountDue` (cọc hoặc `total`), `paymentCode` khớp regex T008, `holdExpiresAt = now + 15 phút` ghi ở đơn **và** mọi slot, `paymentCodes/{code}` (`active = true`, `amount = amountDue`), voucher `RESERVED`; gọi lại cùng `requestId` trả cùng mã và cùng hạn (không gia hạn thêm); mã trùng → sinh lại tối đa 3 lần
- [ ] T036 [P] [US4] Viết lại `FN/sepay-webhook/handler.test.ts` cho đủ outcome ở [research.md R4](research.md): chữ ký sai/thiếu/lệch giờ → 401; `DUPLICATE` không ghi gì; `NOT_INCOMING`; `NO_CODE`; `CODE_NOT_FOUND`; `LATE_TRANSFER` (đơn `EXPIRED`, hoặc mã `active = false`, hoặc slot không còn `HELD` cho đơn) tạo `refunds/refund_sepay_{id}`; `AMOUNT_MISMATCH` tạo refund và đơn vẫn `AWAITING_TRANSFER`; `CONFIRMED`: đơn `PENDING`, `paymentStatus` `DEPOSITED`/`PAID`, `paidAmount`, `remainingAmount`, `paidAt`, `sepayTransactionId`, slot `BOOKED`, voucher `USED`, mã `active = false`, thông báo `BOOKING_NEW`; lỗi Firestore → 500
- [ ] T037 [P] [US4] `FN/owner-mark-refunded/handler.test.ts`: không phải chủ `venueId` → 403; `PENDING → DONE` + `doneAt`, đơn liên quan `paymentStatus = REFUNDED`; gọi lại khi đã `DONE` trả lại kết quả cũ
- [ ] T038 [P] [US4] `FN/refund-report/handler.test.ts`: không phải `userId` → 403; `PENDING` chưa đủ 3 ngày → 409; đủ điều kiện tạo `reports` (`targetType = VENUE`, `targetPath = venues/{venueId}`, `reason = REFUND_NOT_RECEIVED`, `refundId`, `bookingId`, `amount`) và đặt `reportedAt`; lần hai → 409
- [ ] T039 [P] [US4] `FN/refund-reminders/handler.test.ts`: sai `x-cron-secret` → 401; chỉ nhắc refund `PENDING` quá 24 giờ và `remindedAt == null`; ghi `users/{ownerId}/notifications` loại `REFUND_REMINDER`; không nhắc lần hai
- [ ] T040 [P] [US4] `TEST/ui/TransferViewModelTest.kt`: hiện `TransferInstruction`; listener báo `Confirmed` → `OpenReceipt`; báo `Expired` → `expired = true`, hiện `payment_hold_expired`; đồng hồ đếm theo `holdExpiresAtMillis` của server

### Cài đặt

- [ ] T041 [US4] `FN/booking-payment-transfer/{handler.ts,index.ts}` theo [edge-functions.md §2](contracts/edge-functions.md), dùng `payment-calc.ts`, `payment-voucher.ts`, `generatePaymentCode`, `vietQrUrl`; tạo `paymentCodes/{code}` bằng precondition `exists = false`. T035 xanh
- [ ] T042 [US4] Viết lại `FN/sepay-webhook/handler.ts`: giữ khung xác thực và sổ `sepayTransactions/{id}`; thay `walletTopups` bằng `paymentCodes` → đơn/nhóm; dùng `refunds.ts` cho `LATE_TRANSFER`/`AMOUNT_MISMATCH`, `payment-voucher.ts` `commitWrites`; trả 200 `{success: true, outcome}`. T036 xanh
- [ ] T043 [P] [US4] `FN/owner-mark-refunded/{handler.ts,index.ts}`. T037 xanh
- [ ] T044 [P] [US4] `FN/refund-report/{handler.ts,index.ts}`. T038 xanh
- [ ] T045 [P] [US4] `FN/refund-reminders/{handler.ts,index.ts}` + migration `supabase/migrations/<timestamp>_schedule_refund_reminders.sql` (pg_cron mỗi giờ, header `x-cron-secret`, theo mẫu migration của `expire-holds`). T039 xanh
- [ ] T046 [US4] `PaymentRepositoryImpl.startTransfer` + `observe(bookingId, isRecurring)` (`callbackFlow` trên `bookings/{id}` hoặc `recurringGroups/{id}`, hủy listener khi ngừng thu), `PAY/domain/usecase/StartTransferUseCase.kt`, `ObserveBookingPaymentUseCase.kt`
- [ ] T047 [US4] `PAY/ui/TransferFragment.kt` + `TransferViewModel.kt` + `RES/layout/fragment_payment_transfer.xml`: banner `payment_sandbox_banner`, ảnh QR (Coil, `contentDescription`), số tiền, ngân hàng, số tài khoản, tên chủ tài khoản, mã thanh toán (nút sao chép số tài khoản và mã), đồng hồ, nút "Đổi sang thanh toán tại sân" (back về `PaymentFragment`). T040 xanh
- [ ] T048 [US4] `PaymentViewModel`: chọn chuyển khoản + xác nhận → `StartTransferUseCase` → `OpenTransfer`; đổi sang tại sân khi đơn đang chờ chuyển khoản gọi `ConfirmAtVenueUseCase` với cùng `requestId`
- [ ] T049 [US4] Đặt secret và cấu hình SePay theo [quickstart.md §1](quickstart.md) (`supabase secrets set SEPAY_WEBHOOK_SECRET`, webhook URL, cấu trúc mã `SNP` + 8), deploy 6 function, chạy kịch bản 2–4 trên cloud

**Checkpoint**: Chuyển khoản sandbox chạy được đầu–cuối; khoản cần hoàn được tạo và đóng được.

---

## Phase 6: User Story 3 – Hết hạn giữ chỗ (P1)

**Goal**: Đồng hồ kế thừa hạn của server (5 phút, hoặc 15 phút khi chuyển khoản); hết hạn thì khóa nút, server chuyển `EXPIRED`, nhả slot và voucher giữ tạm, vô hiệu mã thanh toán.

**Independent Test**: Kịch bản 5 và 7 ở [quickstart.md §3](quickstart.md); đơn tại sân để hết 5 phút → nút khóa, server từ chối.

- [ ] T050 [P] [US3] Bổ sung `FN/expire-holds/handler.test.ts`: đơn `HOLD` + `AWAITING_TRANSFER` hết hạn → `EXPIRED`, xóa slot, redemption `RESERVED` bị xóa và `usedCount`/`userUsage.count` giảm 1, `paymentCodes/{code}.active = false`; tương tự cho nhóm định kỳ; đơn còn hạn không đổi
- [ ] T051 [US3] Sửa `FN/expire-holds/handler.ts` dùng `releaseWrites` của `payment-voucher.ts` và ghi `paymentCodes.active = false` trong cùng transaction. T050 xanh
- [ ] T052 [P] [US3] `TEST/ui/PaymentCountdownTest.kt`: đếm theo `holdExpiresAtMillis` (không khởi tạo 5 phút mới), dùng giờ server (`ServerTimeOffsetProvider` đang có); chạm 0 → `expired = true`, nút khóa, hiện `payment_hold_expired`
- [ ] T053 [US3] Cài đồng hồ trong `PaymentViewModel` và `TransferViewModel` (ticker 1 giây, dừng khi màn ngừng thu). T052 xanh

---

## Phase 7: User Story 5 – Chống trùng và tranh chấp (P3)

**Goal**: Bấm nhiều lần hoặc thử lại không tạo trùng; webhook gửi lại không ghi nhận hai lần; hai người tranh lượt voucher cuối thì đúng 1 người được.

**Independent Test**: Kịch bản 3 và 7 ở [quickstart.md §3](quickstart.md); test handler bên dưới.

- [ ] T054 [P] [US5] Test đồng thời `FN/booking-payment-transfer/concurrency.test.ts`: hai tài khoản cùng giữ lượt cuối của voucher `usageLimit = 1` → đúng một 200, một 422 `OUT_OF_USES` (Firestore giả mô phỏng xung đột transaction và thử lại như `_shared/testing.ts`)
- [ ] T055 [P] [US5] Test `FN/sepay-webhook/handler.test.ts` bổ sung: cùng `id` gửi 3 lần song song → một `CONFIRMED`, hai `DUPLICATE`, đơn chỉ cập nhật một lần
- [ ] T056 [P] [US5] `TEST/ui/PaymentViewModelIdempotencyTest.kt`: 5 lần bấm trong 500 ms chỉ gọi use case một lần; mất mạng → nút "Thử lại" dùng lại cùng `requestId`

---

## Phase 8: Việc liên spec (cần người phụ trách đồng ý trước khi làm)

- [ ] T057 [P] (A, 011) Bỏ `walletBalance: 0` trong `FN/on-signup/handler.ts` (+ test); bỏ `walletBalance` ở `CORE/session/MyProfile.kt`, `CORE/session/FirestoreUserProfileReader.kt` và mọi chỗ hiển thị số dư (ví dụ `feature/account/ui/delete/DeleteAccountFragment.kt`); cập nhật `specs/011-account-profile/spec.md`
- [ ] T058 [P] (B, 060) `FN/player-cancel-booking/handler.ts` + test: bỏ cộng `users.walletBalance`; đơn `DEPOSITED`/`PAID` bị hủy → `paymentStatus = REFUND_PENDING`, `refunds/refund_{bookingId}` `status = PENDING`, `reason = CANCELLED`, `amount`/`penaltyAmount` theo `RefundPolicy`; `feature/booking_management/domain/RefundPolicy.kt` đổi enum sang `AT_VENUE`, `TRANSFER_DEPOSIT`, `TRANSFER_FULL`; cập nhật `specs/060-my-bookings/spec.md` (hoàn tiền thủ công, hiện trạng thái "Chờ chủ sân hoàn tiền", nút báo cáo gọi `refund-report`)
- [ ] T059 [P] (D, 112) `FN/owner-reject-booking/handler.ts`, `FN/owner-cancel-booking/handler.ts` + test: đơn đã trả → `REFUND_PENDING` + refund `PENDING` (`REJECTED`/`CANCELLED`) thay vì `refundStatus = COMPLETED`; khi làm `booking-timeout` dùng `APPROVAL_TIMEOUT`, không hoàn ví
- [ ] T060 [P] (D, 112) Màn "Cần hoàn tiền" của chủ sân: danh sách `refunds` `PENDING` của venue (số tiền, người chơi, lý do), nút "Đã hoàn tiền" gọi `owner-mark-refunded`; cập nhật `specs/112-owner-operations/spec.md`
- [ ] T061 [P] (D, 111) Form "Tài khoản nhận tiền" trong quản lý cơ sở: `bankBin` (chọn từ danh sách ngân hàng), `bankShortName`, `accountNumber` (6–19 chữ số), `accountHolderName` (in hoa không dấu) ghi vào `venues/{venueId}.payoutAccount`; cập nhật `specs/111-*/spec.md`
- [ ] T062 [P] (B) Cập nhật `docs/design/database/README.md`: §4.1 bỏ `walletBalance`; §4.4 thống nhất `HELD`/`HOLD`; §4.5 trường thanh toán + máy trạng thái `paymentStatus` theo [data-model.md §1.1](data-model.md); §4.5a trường nhóm; §4.6 `userUsage`, `redemptions.status`; thêm `paymentCodes`, `sepayTransactions`, `refunds`, `venues.payoutAccount`; §8a thêm function mới, sửa mô tả `booking-timeout`
- [ ] T063 (cả nhóm) Sửa câu chữ constitution VI trong `.specify/memory/constitution.md`: cho phép chuyển khoản qua cổng ở chế độ sandbox (SePay Test mode), không Live, ghi rõ bản học tập; tăng phiên bản theo quy trình sửa đổi
- [ ] T064 [P] (B) Vẽ lại wireframe màn chuyển khoản và cập nhật `specs/050-payment-voucher/ui/README.md` (bỏ ảnh `buoc1_nap-vi-gia-lap.png`)

---

## Phase 9: Polish

- [ ] T065 Bỏ đường ghi đơn ở app: `ConfirmHoldToBookingUseCase`, `confirmHoldToBooking` trong `BookingRepository`/`FirestoreBookingRemoteDataSource` và test liên quan (sau T034)
- [ ] T066 [P] Kiểm tra giao diện chế độ tối và `w600dp` cho 3 màn; vùng chạm ≥ 48dp; `contentDescription` cho ảnh QR
- [ ] T067 Chạy `./gradlew testDebugUnitTest lintDebug assembleDebug`, `cd supabase/functions && deno test -A`, `cd firestore-tests && npm test`; ghi kết quả
- [ ] T068 Chạy toàn bộ kịch bản thủ công [quickstart.md §3](quickstart.md) trên máy ảo với SePay Test mode
- [ ] T069 Ghi mục AI log cho lần `/speckit-implement` vào `docs/ai-log/AI_log_1.md`

---

## Dependencies & Execution Order

```
Phase 1 Setup ─▶ Phase 2 Foundational ─▶ US1 (P3) ─▶ US2 (P4) ─▶ US4 (P5) ─▶ US3 (P6) ─▶ US5 (P7) ─▶ Polish (P9)
                                                         │
Phase 8 (liên spec) chạy song song sau Phase 2, nhưng T061 (payoutAccount) phải xong trước T049
và T058/T059 phải xong trước khi gỡ hẳn ví (T057).
```

- US2 cần US1 (màn thanh toán và tính tiền). US4 cần US2 (`PaymentFragment`, `PaymentFunctionsApi`, hóa đơn).
- US3 sửa `expire-holds` cho đơn chờ chuyển khoản nên cần US4. Phần đồng hồ (T052–T053) làm được ngay sau US1.
- US5 là test bổ sung trên code của US2/US4.

## Parallel Examples

- **Phase 2**: T005–T008 song song; sau đó T010, T012, T014, T015 song song (khác file); T011 sau T006; T017 sau T016.
- **US1**: T018, T019, T020, T023, T024 song song; T021 → T022 → T025.
- **US4**: T035–T040 (6 file test) song song; T043, T044, T045 song song sau khi có `refunds.ts`; T041 → T042 → T046 → T047 → T048.
- **Phase 8**: T057–T062 thuộc 3 người khác nhau, làm song song.

## Implementation Strategy

1. **MVP** = Phase 1–4 (Setup, Foundational, US1, US2): đặt sân trả tại sân + voucher chạy qua server, đóng lỗ Rules "lát cắt test 050". Demo được ngay, chưa cần tài khoản SePay.
2. **Tăng dần**: US4 (cần tài khoản SePay Test và T061 cho `payoutAccount`) → US3 (dọn hạn cho đơn chờ chuyển khoản) → US5.
3. Phase 8 cần báo B và D trước, nên gửi ghi chú ngay khi bắt đầu Phase 2.
