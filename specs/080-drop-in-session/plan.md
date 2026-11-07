# Implementation Plan: Đặt lịch chơi vãng lai theo lượt

**Branch**: `feature/08-drop-in` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/080-drop-in-session/spec.md`

## Summary

Người chơi tìm buổi vãng lai (lọc theo quận, ngày, khung giờ, trình độ, giá), đăng ký 1–4 người, thanh toán giả lập hoặc trả tại sân, nhận vé có QR, hủy theo chính sách (từ 4 giờ trở lên hoàn 100%, dưới 4 giờ không hoàn, buổi đã đổi thì luôn hoàn), xem lịch sử, và đánh giá buổi đã tham gia.

**Cách làm:**
- **Đăng ký và hủy:** chạy trong hai Supabase Edge Function `register-drop-in` và `cancel-drop-in`. Mỗi function chạy **một Firestore transaction trên document buổi** (`registeredCount`) nên không bao giờ vượt sức chứa, kể cả 20 người đăng ký cùng lúc ([research R1, R2](research.md#r1-đăng-ký-và-hủy-chạy-ở-đâu-khi-gói-spark-không-có-trigger-firestore)). Gói Spark không có trigger nên không dùng trigger.
- **Chống trùng:** `registrations/{uid}` cộng `requestId` làm thao tác idempotent (R3).
- **Slot:** spec này **không ghi `slots`**. Slot 30 phút `BLOCKED` + `blockReason = DROP_IN` do D ghi trong transaction tạo/sửa/hủy buổi (spec 112 FR-009, FR-010a).
- **Client chỉ đọc:**
  - Danh sách buổi dùng truy vấn có index, phân trang 20 (R6).
  - Chi tiết buổi nghe realtime một document.
  - Vé đọc từ cache offline của Firestore (R9).
- **Đánh giá buổi:** mở rộng Edge Function `review` của spec 070 với `sessionId` (R10).
- **Dùng chung:** logic hủy, hoàn tiền và trả chỗ đặt trong `_shared/dropin.ts` để spec 081 (hàng chờ), spec 112 (chủ sân hủy buổi) và `delete-account` gọi lại trong transaction của họ.

## Technical Context

**Language/Version**: Kotlin (Kotlin tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function

**Primary Dependencies**:
- AndroidX Fragment/Navigation (+ Safe Args), ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt.
- Firebase BoM 34.x (Auth, Firestore).
- supabase-kt (Functions) + Ktor OkHttp.
- ZXing `zxing-android-embedded` để vẽ QR ([research R15](research.md#r15-thư-viện)).

**Storage**:
- Cloud Firestore: `dropInSessions`, `dropInSessions/{id}/registrations`, `reviews`; đọc thêm `venues`, `users`, `districts`.
- Spec này không có bucket ảnh riêng. Ảnh đánh giá dùng `review-photos` của spec 070.

**Testing**:
- Unit: JUnit4, MockK, Turbine, kotlinx-coroutines-test.
- Rules: Firebase Emulator + `@firebase/rules-unit-testing`.
- Edge Function: `deno test` với Firebase Emulator, có test 20 lời gọi đồng thời.
- UI: Espresso cho luồng đăng ký vãng lai (nên có, theo constitution IV).

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime

**Project Type**: Mobile app (Android, single Activity) + server function (Supabase)

**Performance Goals**:
- Trang đầu danh sách < 3 giây trên 4G; số chỗ cập nhật ≤ 5 giây (SC-006).
- Mở vé offline < 1 giây (SC-007).
- Từ "Vãng lai" tới khi có vé < 2 phút, ≤ 4 màn hình (SC-001).

**Constraints**:
- Gói miễn phí: phân trang 20, chỉ một listener realtime (document buổi đang xem) và gỡ khi rời màn hình.
- Mọi mốc thời gian (4 giờ, đã bắt đầu) tính theo giờ server.
- Tiền là `Long` VND.
- Không đăng ký một phần số người.

**Scale/Scope**: Quy mô đồ án (vài chục người dùng demo, ~20 buổi); 5 màn hình + 2 bottom sheet; 2 Edge Function mới + mở rộng 1 function của 070; Rules cho 2 collection

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | `feature/dropin/{ui,data,domain}`; Fragment → ViewModel → UseCase → Repository → Firestore/Supabase; `StateFlow<UiState>`; nhóm khác dùng `DropInRegistrationReader`, spec 080 dùng `UpcomingBookingsReader` (B) qua `domain/` ([ui-screens.md](contracts/ui-screens.md)) |
| II. Bảo mật phía server | ✅ | Client không ghi `dropInSessions`/`registrations`; Edge Function kiểm tra quyền, tính tiền, sinh `qrToken`; Rules chỉ cho người đăng ký, chủ cơ sở, admin đọc đăng ký ([security-rules.md](contracts/security-rules.md)) |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ | Đăng ký/hủy trong Firestore transaction trên document buổi; `requestId` idempotent; server timestamp; máy trạng thái đăng ký và buổi ghi trong [data-model.md](data-model.md); slot `DROP_IN` do D ghi trong transaction của spec 112 |
| IV. Kiểm thử có trọng tâm | ✅ | Unit test ViewModel/UseCase/`RegistrationOutcomeResolver`; Rules D1–D13; Edge Function E1–E15 gồm 20 lời gọi đồng thời và các mốc 239/240/241 phút ([quickstart.md](quickstart.md)) |
| V. Giao diện XML nhất quán | ✅ | XML + Material 3, DayNight; `strings_dropin.xml` tiền tố `dropin_`; 4 trạng thái mỗi màn hình; `ListAdapter` + `DiffUtil`; luồng 4 bước; `w600dp`; QR nền trắng ở chế độ tối |
| VI. Quyền riêng tư | ✅ | Người khác không thấy danh sách người đăng ký (FR-021); QR là token ngẫu nhiên, không chứa thông tin cá nhân; thanh toán ghi rõ là giả lập; xóa tài khoản thì hủy lượt sắp tới |
| VII. Đơn giản, minh bạch AI | ✅ | Chỉ thêm ZXing (có trong bảng công nghệ của constitution); không thêm module Gradle; không có cron riêng (R14); log AI trong `docs/ai-log/AI_log_3.md` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế có thêm các trường 🔒 trong `registrations` (`userId`, `sessionStartAt`, `paymentStatus`, `ticketCode`, `cancelledBy`...) và 2 trường suy ra trong `dropInSessions` (`timeSlot`, `venueSnapshot`). Các trường này chỉ phục vụ truy vấn, hiển thị và chính sách hủy, không thêm tầng hay module mới.

## Phối hợp với nhóm

Các việc dưới đây phải xong trước khi code phần liên quan.

| Với | Việc | Hạn |
|---|---|---|
| **D** (spec 112) | 1. Edge Function tạo/sửa buổi gọi `deriveSessionFields()` để ghi `timeSlot`, `venueSnapshot`, `date`. Cần D đồng ý vì 112 FR-008 ghi "không thêm field".<br>2. Khi **sửa giờ/sân/giá**: cập nhật `sessionStartAt`/`sessionEndAt` và đặt `sessionChanged = true` cho mọi đăng ký `REGISTERED`, gửi `DROPIN_CHANGED`.<br>3. Khi **hủy buổi**: gọi `cancelRegistrationInTx(..., "OWNER", { forceFullRefund: true })` cho từng đăng ký trong cùng transaction nhả slot `DROP_IN`.<br>4. **Check-in**: tìm bằng collection group `qrToken`/`ticketCode`, chỉ nhận `REGISTERED`, ghi `CHECKED_IN` + `checkedInAt`; lượt `AT_VENUE` khi thu tiền thì ghi `paymentStatus = PAID`. | Họp 16/10 |
| **D** (spec 100) | Nhận các type sau (đã có trong đặc tả CSDL mục 4.13):<br>- `DROPIN_REGISTERED`: spec 080 gửi cho người đăng ký sau khi đăng ký thành công, `data.sessionId`.<br>- `DROPIN_CHANGED`: D gửi khi sửa/hủy buổi.<br>Từ spec 081: `DROPIN_SPOT_OPENED`, `DROPIN_WAITLIST_CLOSED`.<br>Deep link `smashnow://dropin/session/{id}`, `smashnow://dropin/ticket/{id}`. | Họp 16/10 |
| **D** (spec 120) | Admin đọc `registrations` (Rules đã mở cho `appRole == ADMIN`). | — |
| **B** | 1. `_shared/firestore.ts` có hàm `runTransaction(fn, { maxRetries })` (R2).<br>2. Cung cấp `UpcomingBookingsReader` ở `feature/booking/domain/` cho cảnh báo trùng giờ (R11).<br>3. Dùng chung hàm tính mốc 4 giờ theo giờ server với chính sách hủy đặt sân.<br>4. (Đề xuất, chưa chốt) thêm `dropInSessionId` vào slot `DROP_IN` để lưới 040 mở chi tiết buổi; không bắt buộc cho spec 080. | Họp 16/10 |
| **A** | 1. `delete-account` gọi `cancelRegistrationInTx(..., "SYSTEM")` cho các lượt sắp tới (R13).<br>2. AI 022 chỉ điền `DropInFilterArgs(districtIds, date, timeSlot, level, maxPrice)`.<br>3. Cung cấp `UserProfileReader` (level, khu vực) ở `core/`.<br>4. Màn hình đăng nhập nhận `returnTo`. | Họp 16/10 |
| Cả nhóm | Thêm `zxing-android-embedded` vào PR `chore(core)` chung (spec 112 dùng để quét). | Trước khi code |
| C (mình) | Cập nhật đặc tả CSDL theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd); thêm action `create` theo `sessionId` vào `review` (070). | Trước họp 16/10 |

## Project Structure

### Documentation (this feature)

```text
specs/080-drop-in-session/
├── spec.md
├── plan.md                          # file này
├── research.md                      # Phase 0 (R1–R15)
├── data-model.md                    # Phase 1
├── quickstart.md                    # Phase 1
├── contracts/
│   ├── edge-functions-dropin.md     # API register-drop-in, cancel-drop-in, _shared/dropin.ts, mở rộng review
│   ├── security-rules.md            # Rules + ca test D1–D13, E1–E15
│   └── ui-screens.md                # Màn hình, Safe Args, điểm nối với spec khác
├── ui/                              # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                         # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
└── feature/dropin/
    ├── domain/
    │   ├── model/                     # DropInSession, DropInFilter, DropInRegistration, RegistrationOutcome,
    │   │                              # RegisterRequest, CancelQuote, DropInWarning, TimeSlot, Level
    │   ├── DropInSessionRepository.kt # interface: page(filter, cursor), observe(sessionId)
    │   ├── DropInRegistrationRepository.kt  # interface: register, quoteCancel, cancel, observeMine, observeUpcoming, pagePast
    │   ├── DropInRegistrationReader.kt      # interface cho nhóm khác
    │   ├── RegistrationOutcomeResolver.kt   # nhãn Đã tham gia / Đã hủy / Buổi bị hủy / Không đến
    │   └── usecase/                   # LoadDropInPage, RegisterDropIn, QuoteCancel, CancelDropIn,
    │                                  # CheckDropInWarnings, LoadMyDropIns
    ├── data/
    │   ├── FirestoreDropInSessionRepository.kt
    │   ├── DropInRegistrationRepositoryImpl.kt   # đọc Firestore, ghi qua Supabase Functions
    │   ├── VenueStatusCache.kt                   # lọc cơ sở bị ẩn/khóa (R6)
    │   ├── dto/                                  # SessionDto, RegistrationDto, request/response Edge Function
    │   └── di/DropInModule.kt
    └── ui/
        ├── list/DropInListFragment.kt, DropInListViewModel.kt, DropInSessionAdapter.kt, DropInFilterBottomSheet.kt
        ├── detail/DropInDetailFragment.kt, DropInDetailViewModel.kt
        ├── confirm/DropInConfirmFragment.kt, DropInConfirmViewModel.kt
        ├── ticket/DropInTicketFragment.kt, DropInTicketViewModel.kt, CancelDropInBottomSheet.kt, CancelDropInViewModel.kt, QrRenderer.kt
        └── mine/MyDropInsFragment.kt, MyDropInsViewModel.kt, DropInRegistrationAdapter.kt

app/src/main/res/
├── layout/   fragment_dropin_list.xml, item_dropin_session.xml, bottom_sheet_dropin_filter.xml,
│             fragment_dropin_detail.xml, fragment_dropin_confirm.xml, fragment_dropin_ticket.xml,
│             bottom_sheet_dropin_cancel.xml, fragment_dropin_mine.xml, item_dropin_registration.xml
├── navigation/nav_dropin.xml
└── values/strings_dropin.xml

app/src/test/java/com/example/booking_app/feature/dropin/          # unit test
app/src/androidTest/java/com/example/booking_app/feature/dropin/   # Espresso: đăng ký vãng lai

supabase/functions/
├── register-drop-in/index.ts, index.test.ts
├── cancel-drop-in/index.ts, index.test.ts
├── _shared/dropin.ts, dropin.test.ts      # deriveSessionFields, computeRefund, cancelRegistrationInTx, afterSpotsReleased, mã QR/vé
└── review/index.ts                        # (spec 070) thêm nhánh sessionId

firestore.rules                            # phần dropInSessions, registrations
firestore.indexes.json                     # index ở data-model mục 5
firestore-tests/dropin.test.ts
scripts/seed/080-dropin.ts                 # dữ liệu demo cho quickstart
```

**Structure Decision**:
- **App:** một package tính năng `feature/dropin` theo nguyên tắc I; spec 081 (hàng chờ) cũng dùng package này.
- **Server:** mã server ở `supabase/functions/`, logic dùng chung ở `_shared/dropin.ts` để 081, 112 và `delete-account` gọi trong transaction của họ.
- **Rules và index:** dùng chung file gốc của repo, mỗi nhóm sửa phần của mình.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
