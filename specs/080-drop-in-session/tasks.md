---

description: "Danh sách task triển khai spec 080 – Đặt lịch chơi vãng lai theo lượt"
---

# Tasks: Đặt lịch chơi vãng lai theo lượt

**Input**: tài liệu thiết kế trong `specs/080-drop-in-session/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho:
- ViewModel và UseCase;
- Security Rules và transaction đặt lịch trên Firebase Emulator, gồm **test nhiều người đăng ký chỗ cuối cùng một lúc**;
- logic tính tiền và chính sách hủy.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US5)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `DI/` | `app/src/main/java/com/example/booking_app/feature/dropin/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/dropin/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/feature/dropin/` |
| `FN/` | `supabase/functions/` |

---

## Phase 1: Setup (hạ tầng dùng chung)

**Purpose**: thư viện và cấu hình chung.

Hilt, Firebase, supabase-kt, `UiState`, Emulator và Supabase local đã có trong Phase 1 của [tasks 070](../070-review-favorite/tasks.md) (T001–T009) hoặc trong PR `chore(core)` của nhóm. **Phần nào đã có thì đánh dấu xong và dùng lại, không làm trùng.**

- [ ] T001 Thêm `zxing-android-embedded` vào `gradle/libs.versions.toml` (phiên bản ổn định mới nhất) và khai báo `implementation` trong `app/build.gradle.kts`. Ghi lý do trong mô tả PR (constitution: thêm phụ thuộc phải qua version catalog). Đề xuất đưa vào PR `chore(core)` vì spec 112 dùng để quét (research R15)
- [ ] T002 [P] Kiểm tra các task T001–T009 của spec 070 (hoặc PR core) đã xong: Hilt, `FirebaseModule` (Emulator, offline cache), `SupabaseModule` (Functions, token = Firebase ID token), `core/ui/UiState.kt`, `include_state_views.xml`. Thiếu phần nào thì làm theo đúng mô tả ở tasks 070. `firebase.json`, `.firebaserc`, `firestore.rules` (Rules nền), `firestore.indexes.json`, `firestore-tests/` (Vitest + `@firebase/rules-unit-testing`) đã có trên main từ PR #22 và `supabase/config.toml` từ PR #23: chỉ kiểm tra, không tạo lại
- [ ] T003 [P] Tạo `RES/values/strings_dropin.xml`: mọi chuỗi tiền tố `dropin_` cho 5 màn hình, 2 bottom sheet, nhãn, mã lỗi ở [contracts/edge-functions-dropin.md](contracts/edge-functions-dropin.md#4-mã-lỗi) và dòng "Thanh toán giả lập – không thu tiền thật" (FR-010)

**Checkpoint**: `./gradlew assembleDebug` chạy được; `firebase emulators:start --only auth,firestore` và `supabase start` chạy được.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: mô hình, interface, Rules, index, logic server dùng chung, điều hướng, dữ liệu demo.

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào.

### Server dùng chung

- [ ] T004 Kiểm tra `FN/_shared/firestore.ts` (B) có:
  - `runTransaction(fn, { maxRetries })` thử lại khi `ABORTED`;
  - `get`, `set`, `update` trong transaction;
  - `serverNow()`.

  Nếu B chưa làm thì viết bản tối thiểu theo [research R2](research.md#r2-chống-vượt-sức-chứa-khi-nhiều-người-đăng-ký-cùng-lúc-sc-002) (backoff 50–400 ms có ngẫu nhiên) và báo B
- [ ] T005 [P] Viết test `FN/_shared/dropin.test.ts` (chạy thấy fail):
  - `computeRefund`: các mốc 239/240/241 phút; `sessionChanged = true` còn 30 phút → hoàn 100%; `AT_VENUE` → `refundAmount = 0`, `reason = AT_VENUE`.
  - `deriveSessionFields`: `timeSlot` đúng biên 05:00/11:00/13:00/18:00; `date` theo `Asia/Ho_Chi_Minh`.
  - `newQrToken`: 43 ký tự base64url, không trùng sau 10.000 lần.
  - `newTicketCode`: đúng `^DI-[A-HJ-NP-Z2-9]{8}$`.
- [ ] T006 Cài `FN/_shared/dropin.ts` theo [contract mục 3](contracts/edge-functions-dropin.md#3-hàm-dùng-chung-trong-_shareddropints):
  - `deriveSessionFields`, `computeRefund` (hoàn 100% khi `paymentStatus == PAID` và (`sessionChanged` hoặc `startAt − serverNow ≥ 240 phút`)).
  - `cancelRegistrationInTx(tx, sessionId, uid, cancelledBy, opts)`:
    - đặt `status = CANCELLED`, `qrToken = null`, `ticketCode = null`;
    - ghi `cancelledAt`, `cancelledBy`, `refundAmount`; `paymentStatus = REFUNDED` khi `refundAmount > 0`;
    - `registeredCount -= partySize`; `FULL → OPEN`; gọi `afterSpotsReleased`.
  - `afterSpotsReleased` (để trống cho spec 081).
  - `newQrToken`, `newTicketCode`.

  Sau đó T005 phải xanh
- [ ] T007 [P] Tạo hàm xác thực dùng chung trong `FN/_shared/auth.ts` nếu chưa có (spec 070 có thể đã làm): kiểm tra Firebase ID token, trả `uid`, `appRole`; đọc `users/{uid}.accountStatus` để trả lỗi `ACCOUNT_LOCKED`; có helper `errorResponse(status, code, message, extra?)` cho đúng dạng `{ "error": { "code", "message", ... } }`

### Rules và index

- [ ] T008 Viết test Rules `firestore-tests/test/dropInSessions.test.ts` cho các ca **D1–D13** ở [contracts/security-rules.md](contracts/security-rules.md) (chạy thấy fail):
  - theo cấu trúc của `firestore-tests/test/base.test.ts`: `createTestEnv()` từ `./setup` (project `demo-smashnow`), `clearFirestore()` trong `beforeEach`, `cleanup()` trong `afterAll`; dữ liệu mẫu ghi bằng `withSecurityRulesDisabled`;
  - vai trò lấy từ custom claim `appRole` (`authenticatedContext(uid, { appRole: 'PLAYER' | 'OWNER' | 'ADMIN' })`);
  - mỗi dòng phân quyền ở mục 7 đặc tả CSDL có ít nhất một `assertSucceeds` và một `assertFails`. Dòng `write: if false` chỉ có ca bị chặn, vì ghi hợp lệ đi qua Edge Function chứ không qua Rules;
  - chạy bằng `cd firestore-tests && npm test` (script tự bật Firestore Emulator).
- [ ] T009 Cài phần `dropInSessions` và `registrations` trong `firestore.rules` theo bản phác ở [security-rules.md](contracts/security-rules.md#bản-phác-rules):
  - buổi: `read: if true`, `write: if false`;
  - đăng ký: `get, list` cho `isSelf(uid)`, `isSessionOwner(sessionId)`, `isAdmin()`; `write: if false`;
  - collection group `registrations`: `list` khi `resource.data.userId == request.auth.uid`.

  Sau đó T008 phải xanh
- [ ] T010 [P] Thêm vào `firestore.indexes.json` 10 index ở [data-model.md mục 5](data-model.md#5-index-cần-thêm-vào-firestoreindexesjson):
  - `dropInSessions`: (`status`,`startAt`), (`status`,`districtId`,`startAt`), (`status`,`date`,`startAt`), (`status`,`timeSlot`,`startAt`), (`status`,`level`,`startAt`), (`status`,`startAt`,`pricePerPerson`);
  - collection group `registrations`: (`userId`,`status`,`sessionStartAt`), (`userId`,`sessionStartAt` desc), (`qrToken`), (`ticketCode`).

### Domain và data dùng chung (app)

- [ ] T011 [P] Tạo model trong `DI/domain/model/`, đúng bảng mục 6 của [data-model.md](data-model.md#6-mô-hình-phía-app-kotlin-featuredropindomain):
  - `DropInSession.kt` (`remaining = capacity − registeredCount`; `isOwnedBy(uid)`);
  - `DropInFilter.kt`;
  - `DropInRegistration.kt`, `RegistrationOutcome.kt`, `RegisterRequest.kt`, `CancelQuote.kt`, `DropInWarning.kt`.
  - Enum:
    - `TimeSlot`: `MORNING`, `NOON`, `AFTERNOON`, `EVENING`;
    - `Level`: `BEGINNER`, `INTERMEDIATE`, `ADVANCED`;
    - `SessionStatus`: `OPEN`, `FULL`, `CANCELLED`, `COMPLETED`;
    - `RegistrationStatus`: `REGISTERED`, `WAITLISTED`, `CANCELLED`, `CHECKED_IN`;
    - `PaymentMethod`: `MOCK_PAY`, `AT_VENUE`;
    - `PaymentStatus`: `PAID`, `UNPAID`, `REFUNDED`;
    - `CancelledBy`: `USER`, `OWNER`, `SYSTEM`.

  Tiền là `Long`, thời gian là `Instant`
- [ ] T012 [P] Tạo interface:
  - `DI/domain/DropInSessionRepository.kt`: `page(filter, cursor): Page<DropInSession>`, `observe(sessionId): Flow<DropInSession?>`.
  - `DI/domain/DropInRegistrationRepository.kt`: `register(req): Result<DropInRegistration>`, `quoteCancel(sessionId, requestId): Result<CancelQuote>`, `cancel(sessionId, requestId): Result<DropInRegistration>`, `observeMine(sessionId): Flow<DropInRegistration?>`, `observeUpcoming(uid)`, `pagePast(uid, cursor)`.
  - `DI/domain/DropInRegistrationReader.kt` (cho nhóm khác).

  Theo [contracts/ui-screens.md](contracts/ui-screens.md)
- [ ] T013 [P] Tạo `DI/data/dto/SessionDto.kt`, `RegistrationDto.kt`, `EdgeDtos.kt` (request/response của `register-drop-in`, `cancel-drop-in` và body lỗi) và mapper trong `DI/data/dto/Mappers.kt`. Mã lỗi Edge Function → `sealed class DropInError` (`SessionFull(remaining)`, `SessionStarted`, `SessionCancelled`, `VenueInactive`, `OwnSession`, `AlreadyRegistered`, `AlreadyWaitlisted`, `NotCancellable`, `NotFound`, `AccountLocked`, `Busy`, `Network`, `Unknown`)
- [ ] T014 [P] Tạo `APP/core/format/MoneyFormat.kt` (`80.000 đ`) và `APP/core/format/TimeFormat.kt` (hiển thị theo `Asia/Ho_Chi_Minh`) nếu `core/` chưa có (FR-027); unit test trong `app/src/test/java/com/example/booking_app/core/format/FormatTest.kt`
- [ ] T015 Tạo `DI/data/di/DropInModule.kt` (Hilt) bind các repository; tạo nav graph lồng `RES/navigation/nav_dropin.xml` với 5 Fragment, 2 bottom sheet và Safe Args ở [ui-screens.md mục 1](contracts/ui-screens.md#1-màn-hình-fragment); `include` vào graph chính; khai báo deep link `smashnow://dropin/session/{sessionId}` và `smashnow://dropin/ticket/{sessionId}`
- [ ] T016 [P] Viết script `scripts/seed/080-dropin.ts` tạo dữ liệu demo đúng [quickstart mục 1](quickstart.md#1-chuẩn-bị) trên Emulator:
  - 3 quận, 4 cơ sở (1 cơ sở `HIDDEN`);
  - 20 buổi (đủ 3 trình độ, 4 khung giờ, giá 40.000–120.000 đ): 1 `FULL`, 1 `CANCELLED`, 1 đã qua, 1 còn 3 chỗ, 1 bắt đầu sau 3 giờ;
  - tài khoản `player1`, `player2`, `owner1`.

  Buổi phải có đủ `timeSlot`, `venueSnapshot` như khi tạo bằng `deriveSessionFields`

**Checkpoint**: Rules D1–D13 (`firestore-tests/test/dropInSessions.test.ts`) và `FN/_shared/dropin.test.ts` xanh; app build được với nav graph `nav_dropin`.

---

## Phase 3: User Story 1 – Tìm buổi vãng lai phù hợp (Priority: P1) 🎯 MVP

**Goal**: xem danh sách buổi sắp diễn ra, lọc theo khu vực/ngày/khung giờ/trình độ/giá, xem chi tiết với số chỗ cập nhật thời gian thực.

**Independent Test**:
1. Nạp dữ liệu demo 20 buổi.
2. Áp từng bộ lọc rồi kết hợp nhiều bộ lọc.
3. Chỉ còn đúng các buổi thỏa mãn. Không thấy buổi đã qua, đã hủy hay thuộc cơ sở `HIDDEN`.
4. Mở chi tiết thấy đủ thông tin. Máy khác đăng ký thì số chỗ đổi trong ≤ 5 giây (quickstart Q1–Q3).

### Tests cho User Story 1

- [ ] T017 [P] [US1] Unit test `TEST/domain/DropInFilterTest.kt`:
  - filter → danh sách chip và bỏ từng chip;
  - chọn quá 15 quận thì báo lỗi (giới hạn `in` ở research R6);
  - `timeSlot` theo biên Sáng 05:00–11:00, Trưa 11:00–13:00, Chiều 13:00–18:00, Tối 18:00–23:00.
- [ ] T018 [P] [US1] Unit test `TEST/ui/list/DropInListViewModelTest.kt` (MockK + Turbine):
  - `Loading → Content` sắp theo `startAt`;
  - điền sẵn khu vực từ hồ sơ (AC 1.2);
  - nhận `DropInFilterArgs` từ AI (AC 1.8);
  - `Empty(hasFilter = true)` khi lọc không có kết quả (AC 1.6);
  - `Error` + thử lại;
  - tải trang tiếp (20/trang).
- [ ] T019 [P] [US1] Unit test `TEST/ui/detail/DropInDetailViewModelTest.kt`:
  - `actions = REGISTER` khi còn chỗ và chưa đăng ký;
  - `FULL` khi đủ người (AC 1.4);
  - `CLOSED` khi đã bắt đầu, bị hủy hoặc cơ sở ngừng hoạt động;
  - `VIEW_TICKET` khi đã có đăng ký còn hiệu lực (AC 2.7);
  - chủ cơ sở xem buổi của mình thì không có `REGISTER`;
  - khách chưa đăng nhập bấm đăng ký → sự kiện yêu cầu đăng nhập.
- [ ] T020 [P] [US1] Test tích hợp trên Emulator `app/src/androidTest/java/com/example/booking_app/feature/dropin/data/FirestoreDropInSessionRepositoryTest.kt`:
  - mỗi bộ lọc trả đúng buổi;
  - không trả buổi `CANCELLED`, buổi đã qua, buổi của cơ sở `HIDDEN`;
  - phân trang bằng `startAfter` không trùng/sót.

### Implementation cho User Story 1

- [ ] T021 [US1] Cài `DI/data/FirestoreDropInSessionRepository.kt`:
  - truy vấn ở [research R6](research.md#r6-danh-sách-buổi-và-bộ-lọc-fr-001--fr-004): `status in [OPEN, FULL]`, `startAt >= now`, `startAt < now + 30 ngày`, lọc tùy chọn `districtId in`, `date ==`, `timeSlot ==`, `level ==`, `pricePerPerson <=`;
  - `orderBy startAt`, `limit 20`, `startAfter`;
  - `observe(sessionId)` bọc snapshot listener trong `callbackFlow`, gỡ listener khi hủy thu thập.
- [ ] T022 [US1] Cài `DI/data/VenueStatusCache.kt`: đọc theo lô các `venues` của trang (≤ 20 `get`), cache trong bộ nhớ 5 phút, lọc buổi có `venue.status != ACTIVE` (AC 1.5); dùng trong `FirestoreDropInSessionRepository.page`
- [ ] T023 [US1] Cài UseCase `DI/domain/usecase/LoadDropInPage.kt` và `ObserveDropInSession.kt`; lấy khu vực mặc định qua `UserProfileReader` (A, `core/`). Nếu A chưa có thì tạo interface tạm trong `APP/core/user/UserProfileReader.kt` (`level`, `favoriteDistrictIds`) và báo A
- [ ] T024 [US1] Cài `DI/ui/list/DropInListViewModel.kt` (`StateFlow<UiState>`, `Channel` cho điều hướng; giữ filter qua `SavedStateHandle`) cho T018 xanh
- [ ] T025 [P] [US1] Layout `RES/layout/fragment_dropin_list.xml` (thanh chip bộ lọc, `RecyclerView`, `include_state_views`) và `RES/layout/item_dropin_session.xml`. Thẻ buổi hiện: ngày, giờ bắt đầu–kết thúc, tên cơ sở, quận, trình độ, "còn X/Y chỗ", giá mỗi lượt, nhãn "Đã đủ người" (FR-002). Theo wireframe `ui/08_drop-in_buoc1_danh-sach-buoi.png`
- [ ] T026 [US1] Cài `DI/ui/list/DropInSessionAdapter.kt` (`ListAdapter` + `DiffUtil`) và `DI/ui/list/DropInListFragment.kt` (ViewBinding, `repeatOnLifecycle(STARTED)`, cuộn tới cuối thì tải trang tiếp, màn hình trống có nút "Bỏ bớt bộ lọc")
- [ ] T027 [US1] Cài `DI/ui/list/DropInFilterBottomSheet.kt` + `RES/layout/bottom_sheet_dropin_filter.xml`:
  - chọn nhiều quận (danh mục `districts`, tối đa 15);
  - ngày từ hôm nay tới 30 ngày tới;
  - 4 khung giờ, 3 trình độ;
  - giá tối đa;
  - nút "Áp dụng" và "Xóa hết".
- [ ] T028 [US1] Cài `DI/ui/detail/DropInDetailViewModel.kt` cho T019 xanh: kết hợp `observe(sessionId)` với `observeMine(sessionId)` (đăng ký của chính mình, nếu đã đăng nhập)
- [ ] T029 [US1] Layout `RES/layout/fragment_dropin_detail.xml` và `DI/ui/detail/DropInDetailFragment.kt`:
  - hiện địa chỉ, sân con (tên lấy từ `courts`), trình độ, giá, ghi chú của chủ sân, số chỗ realtime (FR-005);
  - nút "Đăng ký" / "Xem vé của bạn" / nhãn "Đã đủ người";
  - để trống vùng `waitlist_container` cho spec 081;
  - **không có** phần "Người tham gia" (FR-021).

  Theo wireframe `ui/08_drop-in_buoc2_chi-tiet-buoi.png`

**Checkpoint**: US1 chạy độc lập: tìm, lọc, xem chi tiết (quickstart Q1–Q3).

---

## Phase 4: User Story 2 – Đăng ký chơi theo lượt và nhận vé QR (Priority: P1)

**Goal**: đăng ký 1–4 người, thanh toán giả lập hoặc trả tại sân, nhận vé QR. **Không bao giờ vượt sức chứa khi nhiều người đăng ký cùng lúc.**

**Independent Test**:
- Đăng ký 2 người vào buổi còn 3 chỗ, chọn thanh toán giả lập. Vé có QR và số chỗ còn 1.
- Hai tài khoản cùng đăng ký chỗ cuối: đúng một người thành công.
- Bấm "Xác nhận" 5 lần chỉ tạo 1 đăng ký (quickstart Q4–Q6, E1–E3).

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T030 [P] [US2] **Test Emulator đăng ký đồng thời** trong `FN/register-drop-in/index.test.ts` (Firebase Emulator + `supabase functions serve`):
  - **E1:** buổi `capacity = 12`, `registeredCount = 7`. 20 tài khoản gọi song song (`Promise.all`), mỗi người `partySize = 1`. Kỳ vọng: đúng 5 response `201`, 15 response `409 SESSION_FULL`; sau cùng `registeredCount == 12`, `status == FULL`, có đúng 5 document `registrations` `REGISTERED`. In `success=5 full=15 registeredCount=12/12`.
  - **E2:** buổi còn 3 chỗ. 2 tài khoản cùng gửi `partySize = 2` song song. Kỳ vọng: đúng 1 thành công; người kia nhận `SESSION_FULL` với `remaining = 1`; `registeredCount` không vượt `capacity`.
  - Chạy mỗi ca 10 lần liên tiếp. Không lần nào được vượt sức chứa (SC-002, FR-007).
- [ ] T031 [P] [US2] Test idempotent và kiểm tra điều kiện trong `FN/register-drop-in/index.test.ts`:
  - **E3:** cùng `requestId` gửi 5 lần, kể cả song song. Kết quả: 1 đăng ký, `registeredCount` tăng 1 lần, 4 lần sau trả `200`.
  - Cùng người, `requestId` khác → `ALREADY_REGISTERED`.
  - **E8:** buổi đã bắt đầu → `SESSION_STARTED`; `CANCELLED` → `SESSION_CANCELLED`; cơ sở `HIDDEN` → `VENUE_INACTIVE`; chủ cơ sở tự đăng ký → `OWN_SESSION`.
  - **E9:** `partySize` = 0 hoặc 5 → `INVALID_ARGUMENT`.
  - `MOCK_PAY` → `paymentStatus = PAID`; `AT_VENUE` → `UNPAID`.
  - `amount = pricePerPerson × partySize`, chốt lúc đăng ký.
  - Response không chứa `qrToken`.
  - Gọi `send-notification` `DROPIN_REGISTERED` đúng 1 lần (mock).
- [ ] T032 [P] [US2] Unit test `TEST/ui/confirm/DropInConfirmViewModelTest.kt`:
  - `maxParty = min(4, remaining)` (AC 2.2);
  - tổng tiền = giá × số người (AC 2.1);
  - `requestId` giữ nguyên qua `SavedStateHandle` khi thử lại;
  - `SessionFull(remaining)` → `Failed` kèm số chỗ;
  - `Network` → giữ lựa chọn và cho thử lại (AC 2.8);
  - `Busy` → tự gửi lại cùng `requestId` tối đa 2 lần;
  - thành công → sự kiện điều hướng tới vé.
- [ ] T033 [P] [US2] Unit test `TEST/domain/CheckDropInWarningsTest.kt`: trùng giờ với lượt vãng lai khác, trùng với đơn đặt sân (fake `UpcomingBookingsReader`), lệch trình độ. Chỉ trả cảnh báo, không chặn (FR-012)

### Implementation cho User Story 2

- [ ] T034 [US2] Cài `FN/register-drop-in/index.ts` theo [contract mục 1](contracts/edge-functions-dropin.md#1-register-drop-in) và [quy tắc kiểm tra](data-model.md#quy-tắc-kiểm-tra-của-register-drop-in-trong-transaction):
  - xác thực bằng `_shared/auth.ts`;
  - trong `runTransaction` (tối đa 8 lần thử):
    - đọc buổi, `venues/{venueId}` và `registrations/{uid}`;
    - kiểm tra theo thứ tự trong bảng;
    - `registeredCount += partySize`; nếu bằng `capacity` thì `status = FULL`;
    - ghi đăng ký đủ các trường 🔒 (`userId`, `sessionId`, `venueId`, `sessionStartAt`, `sessionEndAt`, `partySize`, `status = REGISTERED`, `paymentMethod`, `paymentStatus`, `pricePerPerson`, `amount`, `qrToken`, `ticketCode`, `requestId`, `sessionChanged = false`, `registeredAt`, `createdAt`, `updatedAt`).
  - Ghi đè được bản `CANCELLED` cũ.
  - Hết số lần thử → `503 BUSY`.
  - Sau commit, nếu là đăng ký mới thì gọi `send-notification` `DROPIN_REGISTERED` (lỗi bỏ qua).

  Sau đó T030, T031 phải xanh
- [ ] T035 [US2] Cài phần `register` và `observeMine` trong `DI/data/DropInRegistrationRepositoryImpl.kt`:
  - gọi `supabase.functions.invoke("register-drop-in")`, làm mới token và thử lại 1 lần khi `401`;
  - map lỗi sang `DropInError`;
  - `observeMine` nghe `dropInSessions/{id}/registrations/{uid}`.
- [ ] T036 [US2] Cài `DI/domain/usecase/RegisterDropIn.kt` và `CheckDropInWarnings.kt`:
  - đọc đơn đặt sân qua `UpcomingBookingsReader` của B (`feature/booking/domain/`); nếu B chưa có thì tạo interface tạm cùng tên ở đó, kèm fake trả rỗng, rồi báo B;
  - đọc trình độ qua `UserProfileReader`.
- [ ] T037 [US2] Cài `DI/ui/confirm/DropInConfirmViewModel.kt` cho T032, T033 xanh
- [ ] T038 [US2] Layout `RES/layout/fragment_dropin_confirm.xml` và `DI/ui/confirm/DropInConfirmFragment.kt`:
  - thông tin buổi, ô chọn số người (1..`maxParty`);
  - 2 lựa chọn thanh toán, kèm dòng "thanh toán giả lập, không thu tiền thật" (FR-010);
  - tổng tiền, cảnh báo (FR-012), nút "Xác nhận" khóa khi đang gửi.
  - Thành công thì điều hướng tới vé với `popUpTo` bỏ màn hình xác nhận (FR-025).

  Theo wireframe `ui/08_drop-in_buoc3_xac-nhan-dang-ky.png`
- [ ] T039 [P] [US2] Cài `DI/ui/ticket/QrRenderer.kt`: dùng ZXing tạo `Bitmap` từ `qrToken`, cạnh ≥ 240dp, nền trắng; unit test `TEST/ui/ticket/QrRendererTest.kt` (giải mã lại ra đúng token)
- [ ] T040 [US2] Cài `DI/ui/ticket/DropInTicketViewModel.kt` và `DI/ui/ticket/DropInTicketFragment.kt` + `RES/layout/fragment_dropin_ticket.xml`:
  - mã QR, mã vé dạng chữ (chọn để sao chép được), thông tin buổi, số người;
  - số tiền và trạng thái thanh toán: "Đã thanh toán" hoặc "Thanh toán tại sân" kèm số tiền (AC 2.3, 2.4);
  - tăng độ sáng màn hình khi mở, trả lại khi rời.

  Theo wireframe `ui/08_drop-in_buoc4_ve-qr.png`
- [ ] T041 [US2] Nối nút "Đăng ký" ở `DropInDetailFragment` tới `DropInConfirmFragment`. Khách chưa đăng nhập thì chuyển sang đăng nhập (spec 010) kèm `returnTo = sessionId`, đăng nhập xong quay lại buổi

**Checkpoint**: US1 + US2 là MVP. Đăng ký chạy đúng; **E1, E2 xanh 10/10 lần**.

---

## Phase 5: User Story 3 – Xem vé và lịch sử lượt vãng lai (Priority: P2)

**Goal**: "Lượt vãng lai của tôi" có tab "Sắp tới" và "Đã qua", nhãn kết quả đúng; mở vé được khi mất mạng.

**Independent Test**:
1. Dùng tài khoản có 2 lượt sắp tới, 1 lượt đã tham gia và 1 lượt đã hủy.
2. Mỗi lượt nằm đúng tab và có đúng nhãn.
3. Bật chế độ máy bay, mở lại vé đã xem trước đó: hiện trong < 1 giây (quickstart Q7, Q12, Q13).

### Tests cho User Story 3

- [ ] T042 [P] [US3] Unit test `TEST/domain/RegistrationOutcomeResolverTest.kt` theo bảng [research R7](research.md#r7-lượt-vãng-lai-của-tôi-fr-019-fr-020):
  - `CHECKED_IN` → `ATTENDED`;
  - `CANCELLED` + `USER` (hoặc `SYSTEM`) → `CANCELLED_BY_ME`;
  - `CANCELLED` + `OWNER` (hoặc buổi `CANCELLED`) → `SESSION_CANCELLED`;
  - `REGISTERED` và `session.endAt < now` → `NO_SHOW`;
  - `REGISTERED` chưa bắt đầu → `UPCOMING`.
- [ ] T043 [P] [US3] Unit test `TEST/ui/mine/MyDropInsViewModelTest.kt`:
  - "Sắp tới" sắp theo giờ gần nhất;
  - "Đã qua" mới nhất trước, phân trang, không lặp lượt đã nằm ở "Sắp tới";
  - `Empty` kèm nút "Tìm buổi vãng lai" (AC 3.6);
  - nhãn "Buổi đã thay đổi" khi `sessionChanged` (AC 3.5).
- [ ] T044 [P] [US3] Unit test `TEST/ui/ticket/DropInTicketViewModelTest.kt`:
  - đọc cache trước → `Offline(cached)` khi không có mạng (FR-015);
  - buổi bị hủy → không còn QR, nhãn "Buổi đã bị hủy";
  - cơ sở ngừng hoạt động → nhãn "Cơ sở tạm ngừng hoạt động";
  - `canCancel = false` khi buổi đã bắt đầu (AC 4.4).

### Implementation cho User Story 3

- [ ] T045 [US3] Cài `observeUpcoming(uid)` và `pagePast(uid, cursor)` trong `DI/data/DropInRegistrationRepositoryImpl.kt`:
  - **Sắp tới:** collection group `registrations` `where userId == uid`, `status in [REGISTERED, WAITLISTED]`, `sessionStartAt >= now`, `orderBy sessionStartAt`.
  - **Đã qua:** `where userId == uid`, `orderBy sessionStartAt desc`, `limit 20`; bỏ những lượt thuộc "Sắp tới".
  - Đọc buổi theo lô (≤ 20) để hiển thị.
  - Cài luôn `DropInRegistrationReader` cho nhóm khác.
- [ ] T046 [US3] Cài `DI/domain/RegistrationOutcomeResolver.kt` và `DI/domain/usecase/LoadMyDropIns.kt` cho T042 xanh
- [ ] T047 [US3] Cài `DI/ui/mine/MyDropInsViewModel.kt`, `DI/ui/mine/DropInRegistrationAdapter.kt`, `DI/ui/mine/MyDropInsFragment.kt` + `RES/layout/fragment_dropin_mine.xml`, `RES/layout/item_dropin_registration.xml`:
  - 2 tab;
  - mỗi lượt hiện ngày giờ, cơ sở, số người, trạng thái thanh toán, nhãn kết quả.

  Theo wireframe `ui/08_drop-in_buoc5_luot-vang-lai-cua-toi.png`. Cho T043 xanh
- [ ] T048 [US3] Bổ sung `DropInTicketViewModel`:
  - đọc `Source.CACHE` trước để hiện ngay, sau đó nghe server;
  - nhãn "Buổi đã thay đổi", "Buổi đã bị hủy" (ẩn QR), "Cơ sở tạm ngừng hoạt động";
  - nút "Chỉ đường" mở Google Maps bằng `Intent` với tọa độ của cơ sở (AC 3.2).

  Cho T044 xanh
- [ ] T049 [US3] Thêm lối vào "Lượt vãng lai của tôi" (từ tab "Lịch của tôi" hoặc trang Tài khoản, thống nhất với A/B) và đăng ký `DropInRegistrationReader` trong `DropInModule`

**Checkpoint**: US3 chạy độc lập (quickstart Q7, Q12, Q13).

---

## Phase 6: User Story 4 – Hủy đăng ký theo chính sách (Priority: P2)

**Goal**:
- Hủy lượt của mình trước giờ bắt đầu.
- Thấy số tiền được hoàn trước khi xác nhận.
- Chỗ được trả lại đúng một lần.

**Independent Test**:
- Hủy lượt 2 người đã thanh toán khi còn ≥ 4 giờ: hoàn 100%, buổi tăng 2 chỗ.
- Hủy khi còn < 4 giờ: không hoàn tiền nhưng vẫn hủy được (quickstart Q8, Q9, E4–E7).

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T050 [P] [US4] Test `FN/cancel-drop-in/index.test.ts` (Firebase Emulator):
  - **E4:** cùng `requestId` gọi 5 lần, kể cả song song. Chỉ trả chỗ 1 lần, hoàn tiền 1 lần.
  - **E5:** hủy khi còn 239, 240, 241 phút → hoàn 0 / 100% / 100%.
  - **E6:** `sessionChanged = true`, còn 30 phút → hoàn 100%.
  - **E7:** lượt `AT_VENUE` → `refundAmount = 0`, `reason = AT_VENUE`, `paymentStatus` giữ `UNPAID`.
  - **E10:** token người khác → `NOT_FOUND`.
  - **E12:** buổi `FULL`, một người hủy → `OPEN`.
  - **E13:** sau khi hủy, `qrToken` và `ticketCode` bằng `null`.
  - Lượt `CHECKED_IN` → `NOT_CANCELLABLE`.
  - Buổi đã bắt đầu → `SESSION_STARTED`.
  - `dryRun: true` không ghi gì.
  - Không gọi `send-notification`.
- [ ] T051 [P] [US4] **Test Emulator hủy và đăng ký đồng thời** trong `FN/cancel-drop-in/index.test.ts`:
  - buổi `FULL`; 1 người hủy (`partySize = 2`) cùng lúc 3 người khác đăng ký (`partySize = 1`);
  - kỳ vọng: đúng 2 người đăng ký được; `registeredCount == capacity`; không vượt sức chứa;
  - chạy 10 lần.
- [ ] T052 [P] [US4] Unit test `TEST/ui/ticket/CancelDropInViewModelTest.kt`:
  - `Quoting → Quote` với từng `reason`; chuỗi "Bạn được hoàn 100% (X đ)" / "Không được hoàn tiền" / "Không mất phí";
  - xác nhận → `Cancelling → Done`;
  - mất mạng giữ sheet và cho thử lại cùng `requestId`.

### Implementation cho User Story 4

- [ ] T053 [US4] Cài `FN/cancel-drop-in/index.ts` theo [contract mục 2](contracts/edge-functions-dropin.md#2-cancel-drop-in):
  - xác thực;
  - `dryRun` → chỉ trả `computeRefund`;
  - nếu không: trong `runTransaction` gọi `cancelRegistrationInTx(tx, sessionId, uid, "USER")` và ghi `cancelRequestId`;
  - đã `CANCELLED` với cùng `cancelRequestId` → trả kết quả cũ;
  - không gửi thông báo.

  Sau đó T050, T051 phải xanh
- [ ] T054 [US4] Cài `quoteCancel` và `cancel` trong `DI/data/DropInRegistrationRepositoryImpl.kt`; UseCase `DI/domain/usecase/QuoteCancel.kt`, `CancelDropIn.kt`
- [ ] T055 [US4] Cài `DI/ui/ticket/CancelDropInViewModel.kt`, `DI/ui/ticket/CancelDropInBottomSheet.kt` + `RES/layout/bottom_sheet_dropin_cancel.xml`:
  - hiện chính sách hủy và số tiền hoàn;
  - nút xác nhận;
  - nối nút "Hủy đăng ký" trên màn hình vé (ẩn khi buổi đã bắt đầu).

  Cho T052 xanh

**Checkpoint**: US4 chạy độc lập (quickstart Q8, Q9).

---

## Phase 7: User Story 5 – Đánh giá buổi chơi sau khi tham gia (Priority: P3)

**Goal**: lượt đã check-in của buổi đã kết thúc được đánh giá một lần; đánh giá hiện ở danh sách của cơ sở với nhãn "Buổi vãng lai".

**Independent Test**:
- Với lượt `CHECKED_IN` của buổi đã kết thúc, gửi đánh giá 3 tiêu chí: đánh giá hiện với nhãn "Buổi vãng lai".
- Lượt "Không đến" hoặc "Đã hủy" thì không đánh giá được (quickstart Q11, Q12, E14, E15).

**Phụ thuộc**: Edge Function `review` và `WriteReviewFragment` của spec 070 đã có (tasks 070, US1).

### Tests cho User Story 5 ⚠️ viết trước, phải fail

- [ ] T056 [P] [US5] Bổ sung test vào `FN/review/index.test.ts` (spec 070):
  - **E14:** `create` với `sessionId` khi đăng ký `REGISTERED` → `409 NOT_CHECKED_IN`.
  - **E15:** lần 2 cho cùng lượt → `ALREADY_REVIEWED`.
  - Buổi chưa kết thúc → `NOT_CHECKED_IN`.
  - Quá 7 ngày sau `endAt` → `REVIEW_WINDOW_CLOSED`.
  - Chủ cơ sở tự đánh giá → `OWN_VENUE`.
  - Hợp lệ → `reviews/{sessionId}_{uid}` có `targetType = DROP_IN`, `completedAt = session.endAt`; `venues.ratingSums` và `ratingCount` được cộng.
- [ ] T057 [P] [US5] Unit test `TEST/domain/DropInReviewEligibilityTest.kt`: `ReviewEligibility.ofDropIn(registration, session, now)` → `CAN_WRITE` / `CAN_VIEW` / `EXPIRED` / `NOT_ELIGIBLE` (AC 5.1, 5.3)

### Implementation cho User Story 5

- [ ] T058 [US5] Mở rộng `FN/review/index.ts` (spec 070) theo [contract mục 5](contracts/edge-functions-dropin.md#5-mở-rộng-edge-function-review-spec-070-cho-buổi-vãng-lai):
  - nhánh `create` có `sessionId`: kiểm tra `registrations/{uid}.status == CHECKED_IN`, `session.endAt ≤ now ≤ session.endAt + 7 ngày`, người viết không phải chủ cơ sở;
  - `reviewId = {sessionId}_{uid}`;
  - ảnh có tiền tố `{uid}/{reviewId}/`;
  - thêm mã lỗi `NOT_CHECKED_IN`;
  - `update`, `delete`, `reply` dùng nguyên logic cũ.

  Sau đó T056 phải xanh
- [ ] T059 [US5] Mở rộng spec 070 phía app:
  - `REV/domain/ReviewEligibility.kt` thêm `ofDropIn`;
  - Safe Args của `WriteReviewFragment` thêm `sessionId: String?` (thay `bookingId` khi đánh giá buổi);
  - `ReviewRepository.create` gửi `sessionId`;
  - `item_review.xml` hiện nhãn "Buổi vãng lai" khi `targetType == DROP_IN`.

  Với `REV/` = `app/src/main/java/com/example/booking_app/feature/review/`. Cho T057 xanh
- [ ] T060 [US5] Ở `MyDropInsFragment` (tab "Đã qua"): hiện nút "Đánh giá buổi chơi" / "Xem đánh giá của bạn" theo `ofDropIn`, điều hướng tới `WriteReviewFragment(sessionId, mode)`

**Checkpoint**: cả 5 user story chạy độc lập.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T061 [P] Espresso `ATEST/RegisterDropInFlowTest.kt` (Emulator + dữ liệu demo): danh sách → chi tiết → xác nhận (2 người, thanh toán giả lập) → vé có QR; kiểm tra luồng không quá 4 màn hình (FR-025, SC-001)
- [ ] T062 [P] Kiểm tra 4 trạng thái (loading, content, empty, error + "Thử lại") trên cả 5 màn hình và 2 bottom sheet; mất mạng không crash (FR-024); chế độ tối (QR vẫn nền trắng); bố cục `w600dp` (`RES/layout-w600dp/` nếu cần); `contentDescription` cho QR và icon, vùng chạm ≥ 48dp
- [ ] T063 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (mục 4.7, 4.9, 6, 7, 8a), sau khi D đồng ý trường `timeSlot`, `venueSnapshot`
- [ ] T064 [P] Cập nhật `docs/features/08-drop-in/README.md` (link spec, plan, tasks) và cột *Spec* nhóm 08 trong `docs/features/README.md`
- [ ] T065 Chạy toàn bộ [quickstart.md](quickstart.md):
  - test tự động mục 2;
  - Q1–Q15 trên emulator;
  - đo SC-001, SC-006, SC-007;
  - chụp ảnh hoặc quay video minh chứng cho Definition of Done.
- [ ] T066 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `deno test`, `npm test` trong `firestore-tests/`; không còn cảnh báo Lint mức Error; ktlint sạch

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)**: không phụ thuộc. Nếu PR `chore(core)` hoặc Phase 1 của spec 070 đã xong thì chỉ còn T001, T003.
- **Foundational (Phase 2)**: sau Setup, **chặn mọi user story**.
- **US1 (Phase 3)**: sau Phase 2.
- **US2 (Phase 4)**: sau Phase 2. Phần app cần `DropInDetailFragment` của US1 (T029) để nối nút đăng ký (T041). Phần server (T030–T034) làm song song với US1 được.
- **US3 (Phase 5)**: cần màn hình vé của US2 (T040).
- **US4 (Phase 6)**: cần `cancelRegistrationInTx` (T006) và màn hình vé (T040). Server (T050–T053) làm song song với US2/US3 được.
- **US5 (Phase 7)**: cần US3 (tab "Đã qua") và Edge Function `review` của spec 070.
- **Polish (Phase 8)**: sau các story cần có trong bản demo.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T004 | B: `_shared/firestore.ts` | Viết bản tối thiểu rồi báo B |
| T023 | A: `UserProfileReader` | Interface tạm trong `core/user/` |
| T036 | B: `UpcomingBookingsReader` | Interface tạm + fake trả rỗng |
| T016, T063 | D: đồng ý `timeSlot`, `venueSnapshot` | Seed vẫn ghi hai trường, chỉ hoãn cập nhật đặc tả CSDL |
| T034 | D: `send-notification` nhận `DROPIN_REGISTERED` | Lời gọi lỗi được bỏ qua, không chặn đăng ký |

### Trong mỗi user story

Test (fail) → server/Edge Function → repository → UseCase → ViewModel (test xanh) → layout/Fragment.

### Cơ hội làm song song

- Phase 2: T005, T007, T010, T011, T012, T013, T014, T016 khác file nhau.
- US1: T017–T020 song song; T025 song song với T021–T024.
- US2: T030–T033 song song; T039 song song với T034–T038.
- Server US4 (T050–T053) song song với app US2/US3.

## Parallel Example: User Story 2

```bash
# Viết test trước, cùng lúc:
Task: "T030 Test Emulator 20 người / 2 người đăng ký chỗ cuối cùng lúc trong supabase/functions/register-drop-in/index.test.ts"
Task: "T032 Unit test DropInConfirmViewModel trong app/src/test/.../ui/confirm/DropInConfirmViewModelTest.kt"
Task: "T033 Unit test CheckDropInWarnings trong app/src/test/.../domain/CheckDropInWarningsTest.kt"

# Sau đó, song song:
Task: "T034 Cài supabase/functions/register-drop-in/index.ts"
Task: "T039 Cài QrRenderer trong app/src/main/java/.../feature/dropin/ui/ticket/QrRenderer.kt"
```

## Implementation Strategy

### MVP (US1 + US2)

1. Xong Phase 1, 2.
2. US1 (tìm, xem): demo được danh sách và bộ lọc.
3. US2 (đăng ký, vé QR): **chạy T030 (E1, E2) 10/10 lần xanh** trước khi demo.
4. **Dừng và kiểm tra**: quickstart Q1–Q6. Đây là phần demo cho mốc nhóm 08 (tuần 6 theo team contract).

### Tăng dần

5. US3 (lịch sử, vé offline) → tuần 7, cùng lúc với spec 081 (hàng chờ).
6. US4 (hủy) → tuần 7. `afterSpotsReleased` để trống cho 081 gắn vào.
7. US5 (đánh giá buổi) → sau khi spec 070 xong.
8. Polish → trước khi đóng băng tính năng (27/11).

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `dropin` (ví dụ `feat(dropin): register-drop-in transaction`).
- Ghi log AI vào `docs/ai-log/AI_log_3.md` cho mỗi lần dùng AI.
- Không commit `supabase/.env.local`, `local.properties`, `google-services.json`.
