# Implementation Plan: [050] Thanh toán và Khuyến mãi (chuyển khoản trực tiếp cho chủ sân)

**Branch**: `feature/050-payment-voucher` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/050-payment-voucher/spec.md` (clarify Session 2026-11-16)

> Bản này thay bản 2026-10-15 (ví giả lập, app tự chạy Firestore transaction).

## Summary

Người chơi áp voucher rồi chọn **trả tại sân** hoặc **chuyển khoản cho chủ sân**, với chuyển khoản thì chọn cọc 30% hoặc trả đủ. Mọi thao tác chốt tiền chạy ở Supabase Edge Function:
- `booking-payment-confirm` chốt đơn tại sân hoặc miễn phí.
- `booking-payment-transfer` gia hạn giữ chỗ 15 phút, giữ tạm lượt voucher, cấp mã `SNP…`, trả URL VietQR tới `venues.payoutAccount`.
- `sepay-webhook` (SePay **Test mode**, kiểm chữ ký HMAC, chống trùng theo `id`) khớp mã và số tiền thì chuyển đơn sang `PENDING`. Tiền về muộn hoặc sai số tiền thì sinh khoản cần hoàn.

App nghe realtime đơn để tự sang hóa đơn. Hoàn tiền là việc thủ công: collection `refunds` có `status` `PENDING`/`DONE`, chủ sân bấm "Đã hoàn tiền", cron nhắc sau 24 giờ, người chơi báo cáo Admin sau 3 ngày. Bỏ ví giả lập ở mọi nơi.

## Technical Context

**Language/Version**: Kotlin (JVM 11) cho app; TypeScript trên Deno 2 cho Supabase Edge Functions
**Primary Dependencies**: AndroidX Navigation + Safe Args, ViewBinding, Hilt, Coroutines/Flow, Material 3, Coil 3 (ảnh QR), Firebase BoM 34.19.0 (Auth, Firestore); Edge Functions dùng `_shared/firestore.ts` (REST + transaction), `_shared/auth.ts`, `_shared/sepay.ts`
**Storage**: Cloud Firestore (`bookings`, `recurringGroups`, `slots`, `paymentCodes`, `sepayTransactions`, `refunds`, voucher + `userUsage` + `redemptions`, `venues.payoutAccount`)
**External**: SePay Test mode (webhook "Tiền vào", ảnh QR `qr.sepay.vn`)
**Testing**: JUnit4, MockK, Turbine, coroutines-test (app); `deno test` với Firestore giả (function); Vitest + Firebase Emulator (`firestore-tests`, Rules)
**Target Platform**: Android minSdk 29 / targetSdk 37; Supabase cloud project `smashnow`
**Project Type**: Mobile app + serverless functions
**Performance Goals**: xem trước voucher < 1 giây (tính ở app); xác nhận tại sân / mở màn QR < 1,5 giây trên 4G; webhook → hóa đơn < 10 giây
**Constraints**: tiền `Long` VND; mọi ghi trạng thái/tiền qua Edge Function; không SePay Live; secret chỉ trong `supabase secrets`
**Scale/Scope**: đồ án; vài chục cơ sở demo; 3 màn mới (thanh toán, chuyển khoản, hóa đơn), 6 function mới hoặc đổi, 1 cron

## Constitution Check

*GATE: kiểm trước Phase 0, kiểm lại sau Phase 1.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | `feature/payment/{ui,domain,data}`; `HoldToPaymentArgs` chuyển lên `core/navigation` để 050 không import `feature/booking/ui` |
| II. Kiểm tra ở server | ✅ | Chốt tiền, trạng thái, voucher, mã thanh toán, hoàn tiền đều ở Edge Function; thu hồi rule "lát cắt test 050" cho app tạo đơn `PENDING`; secret SePay chỉ ở server |
| III. Toàn vẹn đặt lịch | ✅ | Transaction REST cho confirm/transfer/webhook/expire; `requestId` idempotent; sổ `sepayTransactions` chống ghi nhận trùng; không thêm trạng thái đơn (vẫn `HOLD`) |
| IV. Kiểm thử | ✅ | Bảng ca test tiền dùng chung Kotlin/TS; test handler cho 7 outcome webhook, giữ/nhả voucher, hai người tranh lượt cuối; Rules test trên emulator |
| V. Giao diện XML | ✅ | 3 Fragment XML + Material 3, đủ 4 trạng thái, chuỗi trong `strings_payment.xml` |
| VI. Thanh toán giả lập | ⚠️ có điều kiện | Chỉ SePay Test mode, banner sandbox trên mọi màn. **Rủi ro**: QR trỏ tài khoản thật của chủ sân nên người chơi có thể chuyển tiền thật ngoài hệ thống. Giảm rủi ro: banner "Không chuyển tiền thật", dữ liệu demo dùng tài khoản test SePay, sửa câu chữ constitution VI cho khớp (việc liên spec). Không phải vi phạm khi chạy đúng sandbox |

**Sau Phase 1**: không phát sinh vi phạm mới. Mục VI giữ trạng thái ⚠️ cho tới khi constitution được sửa câu chữ.

## Project Structure

### Documentation (this feature)

```text
specs/050-payment-voucher/
├── spec.md
├── plan.md               # file này
├── research.md           # R1–R11
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── edge-functions.md # API 6 function
│   └── ui-contracts.md   # điều hướng, repository, use case, chuỗi bắt buộc
├── checklists/requirements.md
└── ui/                   # wireframe cũ (nạp ví) — cần vẽ lại màn chuyển khoản
```

### Source Code

```text
app/src/main/java/com/example/booking_app/
├── core/navigation/HoldToPaymentArgs.kt          # chuyển từ feature/booking/ui
└── feature/payment/
    ├── domain/
    │   ├── model/        # PaymentOption, PaymentStatus, PaymentBreakdown, AppliedVoucher, VoucherCheck,
    │   │                 # PayoutAccount, TransferInstruction, BookingPaymentState, PaymentReceipt, PaymentError
    │   ├── PaymentCalculator.kt
    │   ├── repository/   # VoucherRepository, VenuePayoutRepository, PaymentRepository
    │   └── usecase/      # PreviewVoucher, ConfirmAtVenue, StartTransfer, ObserveBookingPayment
    ├── data/
    │   ├── FirestoreVoucherRepository.kt, FirestoreVenuePayoutRepository.kt
    │   ├── PaymentRepositoryImpl.kt              # gọi Edge Function + listener realtime
    │   ├── remote/PaymentFunctionsApi.kt         # OkHttp/JSON như AccountFunctionsApi
    │   └── di/PaymentModule.kt
    └── ui/
        ├── PaymentFragment.kt + PaymentViewModel.kt
        ├── TransferFragment.kt + TransferViewModel.kt
        ├── PaymentReceiptFragment.kt + ReceiptViewModel.kt
        └── VoucherPickerBottomSheet.kt + VoucherAdapter.kt
app/src/main/res/{layout,navigation/nav_payment.xml,values/strings_payment.xml}

supabase/functions/
├── _shared/sepay.ts            # đổi tiền tố SNP, đổi tên topupQrUrl → vietQrUrl
├── _shared/payment-calc.ts     # công thức tiền (mới) + test
├── _shared/payment-voucher.ts  # đọc/giữ/chốt/nhả voucher trong transaction (mới)
├── _shared/refunds.ts          # tạo refund PENDING dùng chung cho webhook, 060, 112 (mới)
├── booking-payment-confirm/    # mới
├── booking-payment-transfer/   # mới
├── sepay-webhook/              # sửa: walletTopups → paymentCodes + bookings
├── owner-mark-refunded/        # mới
├── refund-report/              # mới
├── refund-reminders/           # mới (cron mỗi giờ)
├── expire-holds/               # sửa: nhả voucher RESERVED, paymentCodes.active = false
└── wallet-topup-create/        # XÓA (chưa commit)

firestore.rules                 # thu hồi tạo đơn PENDING từ app; rule paymentCodes/sepayTransactions/refunds;
                                # đọc voucher cơ sở; payoutAccount chỉ chủ venue ghi; bỏ walletTopups
firestore-tests/test/           # rules test 050
supabase/config.toml            # verify_jwt = false cho function mới; bỏ wallet-topup-create
supabase/migrations/            # pg_cron cho refund-reminders
```

**Structure Decision**: Mobile + serverless như các tính năng 112/120. Logic tiền viết hai bản (Kotlin cho xem trước, TS cho chốt) và ràng buộc bằng chung một bảng ca test, vì app không được tin để chốt tiền nhưng cần phản hồi dưới 1 giây.

## Phạm vi theo người

- **050 (B, hoặc người được giao)**: toàn bộ `feature/payment`, các function trên, Rules phần thanh toán, sửa `expire-holds`.
- **Việc liên spec** (ghi trong tasks.md thành phase riêng, cần người phụ trách đồng ý):
  - A (011, `on-signup`): bỏ `walletBalance`.
  - B (060, `player-cancel-booking`, `RefundPolicy`): bỏ ví, dùng refund `PENDING`.
  - D (111): form `payoutAccount`. D (112): màn "Cần hoàn tiền" + `owner-mark-refunded`; `owner-reject-booking`/`owner-cancel-booking`/`booking-timeout` chuyển sang refund `PENDING`.
  - Cả nhóm: đặc tả CSDL, constitution VI, wireframe màn chuyển khoản.

## Complexity Tracking

| Lựa chọn | Vì sao cần | Phương án đơn giản hơn bị loại vì |
|---|---|---|
| Công thức tiền viết 2 bản (Kotlin + TS) | Xem trước < 1 giây, server vẫn là nguồn tin cậy | Gọi function mỗi lần áp mã: cold start vượt 1 giây |
| Bảng tra `paymentCodes` riêng | Webhook tìm đơn lẻ và nhóm định kỳ bằng một lần đọc trong transaction | Query `bookings` theo `paymentCode`: không làm được trong transaction REST và cần index |
| Giữ tạm voucher (`RESERVED`) | Spec câu 2: không để người đã chuyển tiền bị từ chối voucher | Đếm khi tiền về: phát sinh hoàn tiền vô lý |
