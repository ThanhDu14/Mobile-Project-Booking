---

description: "Danh sách task triển khai spec 081 – Hàng chờ buổi vãng lai"
---

# Tasks: Hàng chờ buổi vãng lai

**Input**: tài liệu thiết kế trong `specs/081-drop-in-waitlist/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Xây trên spec 080**: spec 081 không có Setup/Foundational riêng cho hạ tầng. Mọi task ở đây phụ thuộc [tasks 080](../080-drop-in-session/tasks.md) và chỉ tham chiếu ID task 080 (ghi `080-T###`), không làm lại.

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test:
- transaction hàng chờ trên Firebase Emulator, gồm **Q6: 5 lượt hủy với 10 người chờ chạy đồng thời, lặp 20 lần** và **Q7: rời hàng chờ cùng lúc bị đẩy lên**;
- Security Rules: mỗi dòng phân quyền có ít nhất một `assertSucceeds` và một `assertFails`;
- ViewModel, UseCase và logic hoàn tiền.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US4)

## Quy ước đường dẫn

Giống [tasks 080](../080-drop-in-session/tasks.md#quy-ước-đường-dẫn):

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `DI/` | `app/src/main/java/com/example/booking_app/feature/dropin/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/dropin/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/feature/dropin/` |
| `FN/` | `supabase/functions/` |

**Test Rules** dùng cấu trúc Vitest đã merge trên main (`firestore-tests/test/<collection>.test.ts`, môi trường lấy từ `createTestEnv()` trong `firestore-tests/test/setup.ts`, project `demo-smashnow`). File của nhóm 08 là `firestore-tests/test/dropInSessions.test.ts`, dùng chung với 080-T008.

---

## Phase 1: Setup

**Purpose**: kiểm tra nền của spec 080 và thêm những gì riêng của hàng chờ.

- [ ] T001 Kiểm tra Phase 1–2 của tasks 080 đã xong (080-T001 → 080-T016), đặc biệt:
  - 080-T006: `FN/_shared/dropin.ts` có `cancelRegistrationInTx` và hook `afterSpotsReleased` để trống;
  - 080-T009: Rules `dropInSessions`/`registrations` trong `firestore.rules`;
  - 080-T011, 080-T013: model và `DropInError`;
  - 080-T015: nav graph `nav_dropin.xml`.

  Chưa xong thì làm theo tasks 080 trước, không viết lại ở spec này
- [ ] T002 [P] Thêm chuỗi tiền tố `dropin_waitlist_` vào `RES/values/strings_dropin.xml`:
  - "Vào hàng chờ", "N người đang chờ", "Bạn đang ở vị trí thứ N", "Xem lượt chờ", "Rời hàng chờ";
  - "Chưa giữ chỗ, chưa thanh toán; bạn sẽ được tự động đăng ký khi có đủ chỗ", "Số tiền sẽ ghi nhận khi được nhận: X đ" (FR-002);
  - nhãn "Đang chờ – vị trí N", "Được nhận từ hàng chờ", "Hủy miễn phí đến HH:mm", "Đã rời hàng chờ", "Đã rời hàng chờ (trùng giờ)", "Không được nhận chỗ", "Buổi bị hủy" (FR-015, research R6);
  - lỗi `SESSION_HAS_SPOTS`, `WAITLIST_CLOSED` ("Hàng chờ đã đóng"), `ALREADY_PROMOTED` ("Bạn đã được nhận vào buổi"), `ALREADY_WAITLISTED` ([contract mục 6](contracts/edge-functions-waitlist.md#6-mã-lỗi-thêm-so-với-spec-080));
  - lý do hoàn tiền "Bạn vừa được nhận từ hàng chờ, hủy trong 30 phút được hoàn 100%" (research R8).
- [ ] T003 [P] Tạo migration `supabase/migrations/<timestamp>_dropin_close_cron.sql`:
  - `create extension if not exists pg_cron;` và `pg_net`;
  - `cron.schedule('dropin-close', '* * * * *', ...)` gọi `net.http_post` tới `/functions/v1/dropin-close` với header `x-cron-secret`;
  - `CRON_SECRET` đặt bằng `npx supabase secrets set` (cloud) hoặc `supabase/.env` (local, đã có trong `.gitignore`). Không ghi secret vào file migration.

  Cần cả nhóm đồng ý bật `pg_cron`, `pg_net` (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))
- [ ] T004 [P] Viết `scripts/seed/081-waitlist.ts` theo [quickstart mục 1](quickstart.md#1-chuẩn-bị), chạy sau `scripts/seed/080-dropin.ts` (080-T016):
  - buổi `W1`: 10 chỗ, `FULL`, bắt đầu sau 2 ngày;
  - buổi `W2`: trùng giờ với `W1`, `FULL`;
  - buổi `W3`: `FULL`, bắt đầu sau 35 phút;
  - tài khoản `player3`…`player12`;
  - mỗi buổi có `waitlistNextTicket = 1`, `waitlistTickets = []`, `waitlistCount = 0`.

**Checkpoint**: `npx supabase start` chạy migration không lỗi; seed 080 + 081 chạy được trên Emulator.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: truy vấn trong transaction, Rules, index, model và khung hàm dùng chung mà cả 4 story cần.

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào.

### Server dùng chung

- [ ] T005 Kiểm tra `FN/_shared/firestore.ts` (B) có `runQuery` **bên trong transaction** (REST `runQuery` có trường `transaction`) để lấy danh sách chờ trong cùng transaction (research R3). Nếu B chưa có thì viết bản tối thiểu `runQueryInTx(tx, structuredQuery)` cạnh `runTransaction` của 080-T004, rồi báo B để gộp
- [ ] T006 [P] Tạo khung `FN/_shared/waitlist.ts` với đúng chữ ký ở [contract mục 5](contracts/edge-functions-waitlist.md#5-hàm-dùng-chung-_sharedwaitlistts): `promoteFromWaitlist`, `closeWaitlistInTx`, `leaveWaitlistInTx`, kiểu `PromoteResult = { promoted: { uid: string; autoLeftSessionIds: string[] }[] }`. Thân hàm tạm `throw new Error("not implemented")`. Mục đích: D (spec 112) và A (`delete-account`) import được sớm
- [ ] T007 [P] Tạo `FN/_shared/waitlist_fixtures.ts` dùng chung cho mọi test Deno của spec này:
  - `seedFullSession({ capacity, registered: partySize[], waitlist: partySize[], startInMinutes })` ghi buổi, các đăng ký `REGISTERED` và các lượt `WAITLISTED` (đúng `waitlistTicket`, `waitlistTickets`, `waitlistCount`) lên Emulator;
  - `assertWaitlistInvariant(sessionId)`: `waitlistTickets` khớp đúng tập lượt `WAITLISTED`, tăng dần, `waitlistCount == waitlistTickets.size`, `registeredCount == Σ partySize` của lượt `REGISTERED`/`CHECKED_IN`, `registeredCount ≤ capacity`.

### Rules và index

- [ ] T008 Viết test Rules cho hàng chờ trong `firestore-tests/test/dropInSessions.test.ts` (cùng file với 080-T008). Mỗi dòng phân quyền có cả ca được phép và ca bị chặn:

  | Dòng Rules | `assertSucceeds` | `assertFails` |
  |---|---|---|
  | `dropInSessions` read | Khách chưa đăng nhập đọc buổi có `waitlistTickets`, `waitlistCount` | — (đọc công khai; ca bị chặn nằm ở dòng write) |
  | `dropInSessions` write | — (`write: if false`; ca được phép là Edge Function, không qua Rules) | **W2:** người chơi tự ghi `waitlistTickets`, `waitlistCount`, `waitlistNextTicket` |
  | `registrations` get (chủ lượt) | `player3` đọc lượt `WAITLISTED` của chính mình | **W1:** `player4` đọc lượt `WAITLISTED` của `player3` |
  | `registrations` list (chủ cơ sở) | `owner1` list `status == WAITLISTED` của buổi mình, `orderBy waitlistTicket` | `owner2` (cơ sở khác) list lượt chờ của buổi đó |
  | `registrations` write | — (`write: if false`) | **W2:** `player3` tự ghi `waitlistTicket`, `waitlistedAt`, `status`, `promotedAt` trên lượt của mình |
  | collection group `registrations` list | `player3` list lượt của mình `where userId == player3` | `player3` list `where status == WAITLISTED` không lọc `userId` |

  Chạy `cd firestore-tests && npm test`: các ca `assertSucceeds` phải fail cho tới khi 080-T009 xong
- [ ] T009 Chạy lại T008 sau 080-T009. Theo [contract mục 7](contracts/edge-functions-waitlist.md#7-rules) không cần thêm rule mới; nếu ca nào đỏ thì sửa `match /dropInSessions/{sessionId}` trong `firestore.rules`, không nới quyền ghi. T008 phải xanh
- [ ] T010 [P] Thêm vào `firestore.indexes.json` 2 index **[MỚI]** ở [data-model mục 4](data-model.md#4-index-cần-thêm-vào-firestoreindexesjson): `registrations` (collection, trong buổi) `status` + `waitlistTicket`; `dropInSessions` `status` + `endAt`. Kiểm tra 2 index dùng lại của 080-T010 (`registrations` collection group `userId`,`status`,`sessionStartAt`; `dropInSessions` `status`,`startAt`) đã có

### Domain và data dùng chung (app)

- [ ] T011 [P] Unit test `TEST/domain/WaitlistPositionTest.kt`: `position = waitlistTickets.indexOf(myTicket) + 1`, `total = waitlistTickets.size`; vé không còn trong mảng → `null`; mảng rỗng → `null`; `DropInSession.isWaitlistOpen(now)` đúng khi `status == FULL && now < startAt − 30 phút`, sai ở đúng mốc `startAt − 30 phút`
- [ ] T012 Mở rộng model trong `DI/domain/model/` theo [data-model mục 5](data-model.md#5-mô-hình-phía-app-kotlin-mở-rộng-featuredropindomain), cho T011 xanh:
  - `DropInSession`: `waitlistTickets: List<Int>`, `waitlistCount: Int`, `isWaitlistOpen(now)`;
  - `DropInRegistration`: `waitlistTicket: Int?`, `promotedAt: Instant?`, `waitlistEndReason: WaitlistEndReason?`, `isFromWaitlist = promotedAt != null`;
  - enum `WaitlistEndReason`: `LEFT`, `AUTO_LEFT_OVERLAP`, `CLOSED`, `SESSION_CANCELLED`;
  - `WaitlistPosition.kt` **[MỚI]** (`position: Int`, `total: Int`, hàm `of(session, registration)`);
  - `RegistrationOutcome` thêm `WAITING`, `LEFT_WAITLIST`, `NOT_ADMITTED`;
  - `RegisterRequest` thêm `joinWaitlist: Boolean`;
  - `CancelQuote.reason` thêm `PROMOTED_GRACE`, `WAITLIST_LEAVE` (luôn 0 đ).
- [ ] T013 [P] Mở rộng `DI/data/dto/SessionDto.kt`, `RegistrationDto.kt`, `EdgeDtos.kt` và `Mappers.kt` (080-T013) với các trường `waitlist*`, `promotedAt`, `waitlistEndReason`, request `joinWaitlist`, `expectWaitlisted`, response `waitlist { position, total }`, `promotedCount`. `DropInError` thêm `SessionHasSpots(remaining)`, `WaitlistClosed`, `AlreadyPromoted`; `SessionFull` thêm `waitlistOpen: Boolean`
- [ ] T014 Thêm vào `DI/domain/DropInRegistrationRepository.kt`: `joinWaitlist(req): Result<WaitlistJoinResult>`, `leaveWaitlist(sessionId, requestId): Result<DropInRegistration>`. Trong `RES/navigation/nav_dropin.xml`: thêm `WaitlistEntryFragment` (Safe Args `sessionId`), thêm argument `mode: REGISTER | WAITLIST` (mặc định `REGISTER`) cho `DropInConfirmFragment` ([ui-screens mục 1](contracts/ui-screens.md#1-màn-hình))

**Checkpoint**: Rules test hàng chờ xanh; app build được với model mở rộng; `waitlist.ts` import được.

---

## Phase 3: User Story 1 – Vào hàng chờ khi buổi đã đủ người (Priority: P1) 🎯 MVP

**Goal**: ở buổi `FULL` còn hơn 30 phút nữa mới bắt đầu, người chơi vào hàng chờ (1–4 người, chọn cách thanh toán, chưa trừ tiền) và thấy vị trí realtime.

**Independent Test**: với buổi `W1` đã đủ người, 3 tài khoản lần lượt vào hàng chờ; mỗi tài khoản thấy đúng vị trí 1, 2, 3; buổi vẫn "Đã đủ người" và số chỗ không đổi (quickstart M1, M6, M7; Q1–Q3).

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T015 [P] [US1] Test Deno `FN/register-drop-in/waitlist.test.ts` (Firebase Emulator, fixture T007). Tách file khỏi `index.test.ts` của 080 để không đụng nhau:
  - **Q1:** 3 người lần lượt vào hàng chờ buổi `FULL` → response `201` với `waitlist.position` 1, 2, 3; `registeredCount` không đổi; `waitlistTickets = [1,2,3]`.
  - **Q2:** cùng `requestId` gửi 5 lần (cả song song) → 1 lượt chờ, `waitlistCount` tăng 1 lần, 4 lần sau trả `200`.
  - **Q3:** buổi `OPEN` → `409 SESSION_HAS_SPOTS` kèm `remaining`; còn đúng 30 phút → `WAITLIST_CLOSED`; chủ buổi → `OWN_SESSION`; buổi `CANCELLED` → `SESSION_CANCELLED`; cơ sở `HIDDEN` → `VENUE_INACTIVE`.
  - Đã `WAITLISTED` với `requestId` khác → `ALREADY_WAITLISTED`; đã `REGISTERED` → `ALREADY_REGISTERED`.
  - Bản `CANCELLED` cũ → ghi đè và nhận số vé mới ở cuối hàng (AC 3.3).
  - `partySize` 0 hoặc 5 → `INVALID_ARGUMENT`; `partySize = 4` hợp lệ dù buổi không còn chỗ nào.
  - Lượt chờ có `paymentStatus = null`, `qrToken = null`, `ticketCode = null`; `pricePerPerson`, `amount = pricePerPerson × partySize` chốt tại lúc vào.
  - Không gọi `send-notification` (mock đếm 0 lần).
  - Đăng ký thường vào buổi `FULL` → `SESSION_FULL` có `waitlistOpen: true`; vào lúc còn ≤ 30 phút → `waitlistOpen: false`.
  - Sau mỗi ca gọi `assertWaitlistInvariant`.
- [ ] T016 [P] [US1] Unit test `TEST/ui/detail/DropInDetailViewModelWaitlistTest.kt` (MockK + Turbine):
  - buổi `FULL`, hàng chờ mở, chưa có lượt → `actions` có `JOIN_WAITLIST` kèm `waitlistCount` (AC 1.1);
  - đang `WAITLISTED` → `VIEW_WAITLIST` và `waitlist = WaitlistPosition(n, total)`; mảng `waitlistTickets` đổi thì vị trí đổi (AC 1.4);
  - buổi `OPEN` → chỉ `REGISTER`, không `JOIN_WAITLIST` (AC 1.5);
  - còn ≤ 30 phút hoặc buổi hủy → không `JOIN_WAITLIST` (AC 4.3);
  - chủ cơ sở xem buổi của mình → không `JOIN_WAITLIST`;
  - khách chưa đăng nhập bấm "Vào hàng chờ" → sự kiện yêu cầu đăng nhập.
- [ ] T017 [P] [US1] Unit test `TEST/ui/confirm/DropInConfirmViewModelWaitlistTest.kt`:
  - `mode = WAITLIST` → `maxParty = 4` (không giới hạn theo chỗ trống); số tiền sẽ ghi nhận = giá × số người (AC 1.3);
  - `requestId` giữ nguyên qua `SavedStateHandle` khi thử lại (AC 1.6);
  - `SessionHasSpots` → sự kiện chuyển sang `mode = REGISTER` (AC 1.5);
  - `WaitlistClosed` → `Failed`;
  - `Network` → giữ lựa chọn, cho thử lại (FR-016);
  - thành công → sự kiện mở `WaitlistEntryFragment`.
- [ ] T018 [P] [US1] Unit test `TEST/ui/waitlist/WaitlistEntryViewModelTest.kt` (phần hiển thị): `Loading → Content(position, total, partySize, amount, paymentMethod)`; vị trí cập nhật khi `waitlistTickets` đổi; lượt không còn `WAITLISTED` → `Empty`; lỗi → `Error` + thử lại

### Implementation cho User Story 1

- [ ] T019 [US1] Cài nhánh `joinWaitlist: true` trong `FN/register-drop-in/index.ts` (080-T034) theo [data-model mục 3 "Vào hàng chờ"](data-model.md#vào-hàng-chờ-register-drop-in-với-joinwaitlist-true-trong-transaction), kiểm tra đúng thứ tự trong bảng, trong cùng `runTransaction`:
  - lượt chờ: `status = WAITLISTED`, `waitlistTicket = waitlistNextTicket`, `waitlistedAt = serverNow`, chốt `pricePerPerson`, `amount`, `paymentMethod`; `paymentStatus`, `qrToken`, `ticketCode`, `waitlistEndReason`, `promotedAt` = `null`;
  - buổi: `waitlistNextTicket += 1`, thêm số vé vào `waitlistTickets`, `waitlistCount += 1`;
  - response `{ registration, waitlist: { position, total } }`;
  - nhánh đăng ký thường: `SESSION_FULL` thêm `waitlistOpen`.

  Sau đó T015 phải xanh
- [ ] T020 [US1] Cài `joinWaitlist` trong `DI/data/DropInRegistrationRepositoryImpl.kt` (gọi `register-drop-in` với `joinWaitlist = true`, map lỗi mới); `observeMine` đọc thêm `waitlistTicket`, `promotedAt`, `waitlistEndReason`
- [ ] T021 [US1] Cài UseCase `DI/domain/usecase/JoinWaitlist.kt`: dùng lại `CheckDropInWarnings` (080-T036) để cảnh báo trùng giờ (Edge Cases: được vào hàng chờ nhiều buổi trùng giờ, chỉ cảnh báo)
- [ ] T022 [US1] Mở rộng `DI/ui/detail/DropInDetailViewModel.kt` (080-T028) và vùng `waitlist_container` trong `RES/layout/fragment_dropin_detail.xml` / `DropInDetailFragment.kt` (080-T029): "N người đang chờ" + "Vào hàng chờ", hoặc "Bạn đang ở vị trí thứ N" + "Xem lượt chờ". Cho T016 xanh
- [ ] T023 [US1] Mở rộng `DI/ui/confirm/DropInConfirmViewModel.kt` và `DropInConfirmFragment.kt` (080-T037, 080-T038) cho `mode = WAITLIST`: tiêu đề "Vào hàng chờ", ô số người 1–4, dòng "Chưa giữ chỗ, chưa thanh toán…" kèm số tiền sẽ ghi nhận (FR-002). Thành công điều hướng tới `WaitlistEntryFragment` với `popUpTo` bỏ màn hình xác nhận. Cho T017 xanh
- [ ] T024 [US1] Tạo `DI/ui/waitlist/WaitlistEntryViewModel.kt`, `DI/ui/waitlist/WaitlistEntryFragment.kt` và `RES/layout/fragment_dropin_waitlist.xml`: vị trí N / tổng, số người, cách thanh toán, số tiền sẽ ghi nhận, `include_state_views` cho 4 trạng thái; chừa chỗ nút "Rời hàng chờ" cho US3. Kết hợp `observe(sessionId)` với `observeMine(sessionId)`. Theo wireframe `ui/08_drop-in_buoc1_hang-cho.png`. Cho T018 xanh
- [ ] T025 [US1] Ở `MyDropInsFragment` tab "Sắp tới" (080-T047): lượt `WAITLISTED` có nhãn "Đang chờ – vị trí N", tính từ `session.waitlistTickets` mà `MyDropInsViewModel` đã đọc theo lô; bấm vào mở `WaitlistEntryFragment` (FR-015)

**Checkpoint**: US1 chạy độc lập; Q1–Q3 xanh; quickstart M1, M6, M7 đúng.

---

## Phase 4: User Story 2 – Tự động được đẩy lên khi có chỗ (Priority: P1)

**Goal**: có chỗ trống (người hủy, chủ sân tăng sức chứa) thì trong cùng transaction hệ thống đẩy lên người chờ lâu nhất có số người vừa chỗ; người được đẩy lên có vé QR ngay. **Không bao giờ vượt sức chứa, không đẩy ai lên hai lần.**

**Independent Test**: buổi 10 chỗ đã đủ, hàng chờ A (2 người), B (1 người). Một lượt 1 người hủy: B được đẩy lên, A giữ vị trí 1. Một lượt 2 người hủy tiếp: A được đẩy lên. `registeredCount` luôn bằng 10 (Q4, quickstart M3, M4).

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T026 [P] [US2] Test Deno `FN/_shared/waitlist.test.ts`, gọi `promoteFromWaitlist` trực tiếp trong `runTransaction` trên Emulator (fixture T007):
  - **Q4:** chờ A(2), B(1); trả 1 chỗ → B lên, A giữ `waitlistTicket` và vị trí 1; trả tiếp 2 chỗ → A lên; `registeredCount == 10` sau mỗi bước.
  - **Q5:** lượt `MOCK_PAY` → `PAID`; `AT_VENUE` → `UNPAID`; có `qrToken` (43 ký tự), `ticketCode` (`^DI-[A-HJ-NP-Z2-9]{8}$`), `promotedAt = registeredAt = serverNow`; `amount` theo giá lúc vào hàng chờ dù `session.pricePerPerson` đã đổi.
  - **Q8:** người chờ cả `W1` và `W2` (trùng giờ), được đẩy lên `W1` → lượt `W2` thành `CANCELLED`, `cancelledBy = SYSTEM`, `waitlistEndReason = AUTO_LEFT_OVERLAP`; `W2.waitlistTickets` bỏ số vé đó, `waitlistCount` giảm; `promoted[0].autoLeftSessionIds` chứa `W2`.
  - **Q9:** còn 25 phút trước giờ bắt đầu, có người chờ → không đẩy ai; chỗ trống giữ cho đăng ký thường; `status` về `OPEN`.
  - **Q13:** tăng `capacity` thêm 3 rồi gọi `promoteFromWaitlist` → đẩy lên tối đa 3 chỗ theo thứ tự vé (AC 2.5).
  - Hàng chờ có người nhưng không ai vừa → `promoted = []`, buổi `OPEN` (AC 2.8).
  - Sau mỗi ca gọi `assertWaitlistInvariant`.
- [ ] T027 [P] [US2] **Test Emulator Q6 đồng thời** trong `FN/cancel-drop-in/waitlist.test.ts`:
  - buổi `capacity = 10`, `FULL`, 5 đăng ký `partySize = 1` sẽ hủy; 10 người chờ với `partySize` lẫn lộn 1–3;
  - bắn 5 lời gọi `cancel-drop-in` song song (`Promise.all`); **lặp 20 lần**, mỗi lần seed lại;
  - mỗi lần kiểm tra: `registeredCount ≤ capacity`; không lượt nào bị ghi `promotedAt` hai lần (đếm số lần ghi bằng `updateTime`); thứ tự được đẩy lên đúng thứ tự `waitlistTicket` sau khi bỏ qua lượt không vừa (so với kết quả mô phỏng tuần tự); `assertWaitlistInvariant` đúng;
  - in `runs=20 overCapacity=0 doublePromoted=0 orderViolations=0` (SC-003, FR-008).
- [ ] T028 [P] [US2] **Q12** bổ sung vào `FN/_shared/dropin.test.ts` (080-T005), cho `computeRefund`:
  - được đẩy lên lúc T−2 giờ, `PAID`, hủy sau 20 / 30 / 31 / 40 phút → 100% (`PROMOTED_GRACE`) / 100% / 0 (`LATE`) / 0 (`LATE`);
  - được đẩy lên lúc T−5 giờ (không sát giờ) → theo chính sách 080 (≥ 240 phút → 100%);
  - `AT_VENUE` được đẩy lên → `refundAmount = 0`, `reason = AT_VENUE`.
- [ ] T029 [P] [US2] Unit test app:
  - `TEST/ui/ticket/DropInTicketViewModelWaitlistTest.kt`: `promotedAt != null` → nhãn "Được nhận từ hàng chờ"; trong 30 phút `PROMOTED_GRACE` → hiện "Hủy miễn phí đến HH:mm" (giờ `Asia/Ho_Chi_Minh`), hết hạn thì ẩn;
  - `TEST/ui/ticket/CancelDropInViewModelTest.kt` (080-T052) thêm ca `reason = PROMOTED_GRACE` → chuỗi "Bạn vừa được nhận từ hàng chờ, hủy trong 30 phút được hoàn 100%";
  - `TEST/ui/waitlist/WaitlistEntryViewModelTest.kt` thêm ca lượt chuyển `REGISTERED` → sự kiện `Promoted` mở vé.

### Implementation cho User Story 2

- [ ] T030 [US2] Cài `promoteFromWaitlist(tx, session, serverNow)` trong `FN/_shared/waitlist.ts` theo [research R3](research.md#r3-đẩy-người-chờ-lên-trong-cùng-transaction-tạo-ra-chỗ-trống-fr-007-fr-008-fr-009) và [R4](research.md#r4-lượt-chờ-được-đẩy-lên-trở-thành-đăng-ký-chính-thức-fr-010):
  - `serverNow ≥ startAt − 30 phút` → trả `promoted = []`;
  - `free = capacity − registeredCount`; lấy ứng viên bằng `runQueryInTx` (T005) `status == WAITLISTED orderBy waitlistTicket asc`;
  - duyệt: `free == 0` thì dừng; `partySize > free` thì bỏ qua nhưng giữ vị trí; còn lại chuyển `REGISTERED` (`promotedAt`, `registeredAt`, `paymentStatus`, `qrToken`, `ticketCode` bằng hàm của 080-T006);
  - cập nhật `registeredCount`, `status` (`FULL`/`OPEN`), `waitlistTickets`, `waitlistCount` trên document buổi.
- [ ] T031 [US2] Cài tự rời hàng chờ trùng giờ trong `promoteFromWaitlist` theo [research R5](research.md#r5-tự-rời-hàng-chờ-của-các-buổi-trùng-giờ-fr-010a): collection group `registrations where userId == U, status == WAITLISTED, sessionStartAt < S.endAt`, lọc thêm `sessionEndAt > S.startAt`; mỗi lượt gọi `leaveWaitlistInTx(tx, sessionId, U, "AUTO_LEFT_OVERLAP", "SYSTEM")` (cài thật ở T038; tới lúc đó viết tạm nội tuyến). Trả `autoLeftSessionIds`. Sau T030, T031 thì T026 phải xanh
- [ ] T032 [US2] Trong `FN/_shared/dropin.ts` (080-T006):
  - `afterSpotsReleased(tx, session, serverNow)` gọi `promoteFromWaitlist` và trả kết quả lên trên;
  - `computeRefund` thêm nhánh `PROMOTED_GRACE` theo [data-model mục 3 "Hoàn tiền"](data-model.md#hoàn-tiền-computerefund-mở-rộng-spec-080): `promotedAt` có giá trị, `startAt − promotedAt < 240 phút`, `serverNow − promotedAt ≤ 30 phút`.

  Sau đó T028 phải xanh
- [ ] T033 [US2] Trong `FN/cancel-drop-in/index.ts` (080-T053): nhận `promoted[]` từ `cancelRegistrationInTx`; response thêm `promotedCount` (không có danh tính); **sau khi commit** gọi `send-notification` `DROPIN_SPOT_OPENED` cho từng người với `data = { sessionId, autoLeftSessionIds }` (lỗi gửi bỏ qua, không hoàn tác). Sau đó T027 phải xanh **20/20 lần**
- [ ] T034 [US2] Mở rộng `DI/ui/ticket/DropInTicketViewModel.kt` và `DropInTicketFragment.kt` (080-T040, 080-T048): nhãn "Được nhận từ hàng chờ", hạn "Hủy miễn phí đến HH:mm"; `CancelDropInViewModel` (080-T055) hiển thị lý do `PROMOTED_GRACE`. Cho T029 xanh
- [ ] T035 [US2] `WaitlistEntryViewModel` phát `Promoted` khi `observeMine` thấy `status == REGISTERED` → `WaitlistEntryFragment` mở `DropInTicketFragment`; `MyDropInsFragment` chuyển lượt từ "Đang chờ" sang "Sắp tới" kèm nhãn "Được nhận từ hàng chờ" (AC 2.4)

**Checkpoint**: US1 + US2 là MVP của spec 081; **Q6 xanh 20/20 lần**; quickstart M3, M4 đúng, đo SC-002 ≤ 10 giây.

---

## Phase 5: User Story 3 – Rời hàng chờ (Priority: P2)

**Goal**: chủ lượt chờ rời hàng chờ bất cứ lúc nào trước khi được đẩy lên, không mất phí; rời và được đẩy lên không bao giờ cùng thành công.

**Independent Test**: hàng chờ A, B, C; B rời hàng chờ; C thấy vị trí đổi từ 3 thành 2; B không còn lượt chờ và không bị ghi khoản nào (quickstart M2; Q7).

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T036 [P] [US3] Bổ sung vào `FN/cancel-drop-in/waitlist.test.ts`:
  - rời hàng chờ (`expectWaitlisted: true`) → `CANCELLED`, `cancelledBy = USER`, `waitlistEndReason = LEFT`; `refundAmount = 0`, `paymentStatus` vẫn `null`; số vé bị bỏ khỏi `waitlistTickets`, vị trí người phía sau tăng lên (AC 3.1, SC-005);
  - **Q7:** rời hàng chờ cùng lúc một lượt 1 người khác hủy làm lượt này được đẩy lên, chạy song song **20 lần** → mỗi lần đúng một kết quả: hoặc `LEFT`, hoặc `REGISTERED` + `409 ALREADY_PROMOTED`; không bao giờ cả hai; lượt `REGISTERED` không bị hủy nhầm (FR-013);
  - `dryRun: true` → `quote.refundAmount = 0`, `reason = WAITLIST_LEAVE`, không ghi gì;
  - token người khác → `NOT_FOUND` (AC 3.4);
  - cùng `cancelRequestId` gửi lại → trả kết quả cũ;
  - rời rồi vào lại → số vé mới ở cuối hàng (AC 3.3);
  - không gọi `send-notification`;
  - sau mỗi ca gọi `assertWaitlistInvariant`.
- [ ] T037 [P] [US3] Unit test `TEST/domain/LeaveWaitlistTest.kt` và bổ sung `TEST/ui/waitlist/WaitlistEntryViewModelTest.kt`: xác nhận → `Leaving → Left`; `AlreadyPromoted` → báo "Bạn đã được nhận vào buổi" rồi mở vé; `Network` → giữ hộp thoại, thử lại cùng `requestId`

### Implementation cho User Story 3

- [ ] T038 [US3] Cài `leaveWaitlistInTx(tx, sessionId, uid, reason, cancelledBy)` trong `FN/_shared/waitlist.ts`: lượt `CANCELLED` với `waitlistEndReason = reason`, `cancelledBy`, `cancelledAt`; buổi bỏ số vé khỏi `waitlistTickets`, `waitlistCount -= 1`. Thay phần viết tạm ở T031 bằng hàm này
- [ ] T039 [US3] Thêm nhánh lượt `WAITLISTED` vào `FN/cancel-drop-in/index.ts` theo [data-model mục 3 "Rời hàng chờ"](data-model.md#rời-hàng-chờ-cancel-drop-in-khi-lượt-đang-waitlisted) và [research R10](research.md#r10-rời-hàng-chờ-và-bị-đẩy-lên-cùng-lúc-fr-013): đọc lại đăng ký trong transaction; còn `WAITLISTED` → `leaveWaitlistInTx(..., "LEFT", "USER")`; đã `REGISTERED` có `promotedAt` mà request có `expectWaitlisted: true` → `409 ALREADY_PROMOTED`, không hủy. Sau đó T036 phải xanh
- [ ] T040 [US3] Cài `leaveWaitlist` trong `DI/data/DropInRegistrationRepositoryImpl.kt` (gửi `expectWaitlisted = true`) và UseCase `DI/domain/usecase/LeaveWaitlist.kt`
- [ ] T041 [US3] Thêm nút "Rời hàng chờ" và hộp thoại xác nhận ("Không mất phí") vào `WaitlistEntryFragment`; nối `WaitlistEntryViewModel`. Cho T037 xanh

**Checkpoint**: US3 chạy độc lập; **Q7 xanh 20/20 lần**; quickstart M2 đúng.

---

## Phase 6: User Story 4 – Hàng chờ đóng trước giờ bắt đầu hoặc khi buổi bị hủy (Priority: P2)

**Goal**: tới mốc 30 phút trước giờ bắt đầu, hoặc khi chủ sân hủy buổi, mọi lượt chờ kết thúc không mất phí và người chơi được báo.

**Independent Test**: buổi `W3` có 2 lượt chờ, tới mốc T−30 phút: cả 2 chuyển "Không được nhận chỗ", không bị ghi khoản nào. Buổi khác bị chủ sân hủy: lượt chờ chuyển "Buổi bị hủy" (quickstart M5; Q10, Q11, Q14).

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T042 [P] [US4] Test Deno `FN/dropin-close/index.test.ts` (Firebase Emulator):
  - **Q10:** buổi tới mốc T−30 phút có 2 lượt chờ → cả 2 `CANCELLED`, `cancelledBy = SYSTEM`, `waitlistEndReason = CLOSED`, `paymentStatus` vẫn `null`; `waitlistTickets = []`, `waitlistCount = 0`; gọi `send-notification` `DROPIN_WAITLIST_CLOSED` 2 lần với `data = { sessionId, reason: "CLOSED" }`; **chạy lại không đổi gì** và không gửi thêm;
  - buổi `OPEN`/`FULL` đã qua `endAt` → `COMPLETED`;
  - buổi còn > 30 phút → không bị đụng tới;
  - response `{ closedSessions, closedEntries, completedSessions }` đúng số;
  - **Q14:** thiếu hoặc sai `CRON_SECRET` → `401`, không ghi gì.
- [ ] T043 [P] [US4] **Q11** bổ sung vào `FN/_shared/waitlist.test.ts`: `closeWaitlistInTx(tx, session, "SESSION_CANCELLED")` → lượt chờ `CANCELLED`, `cancelledBy = OWNER`, `waitlistEndReason = SESSION_CANCELLED`; trả `closed = [uid…]`; không lượt nào có `paymentStatus` khác `null` (SC-005)
- [ ] T044 [P] [US4] Unit test `TEST/domain/RegistrationOutcomeResolverWaitlistTest.kt` theo [research R6](research.md#r6-biểu-diễn-lượt-chờ-đã-kết-thúc-mà-không-thêm-enum-fr-014-fr-015): `waitlistEndReason` được ưu tiên đọc — `LEFT` → `LEFT_WAITLIST` ("Đã rời hàng chờ"); `AUTO_LEFT_OVERLAP` → `LEFT_WAITLIST` ("Đã rời hàng chờ (trùng giờ)"); `CLOSED` → `NOT_ADMITTED`; `SESSION_CANCELLED` → `SESSION_CANCELLED`; `WAITLISTED` → `WAITING`. Các ca của 080-T042 vẫn xanh

### Implementation cho User Story 4

- [ ] T045 [US4] Cài `closeWaitlistInTx(tx, session, reason)` trong `FN/_shared/waitlist.ts` theo [data-model mục 3 "Đóng hàng chờ"](data-model.md#đóng-hàng-chờ-closewaitlistintx-tx-session-reason): mọi lượt `WAITLISTED` (lấy bằng `runQueryInTx`) chuyển `CANCELLED`, `cancelledBy = SYSTEM` khi `CLOSED` hoặc `OWNER` khi `SESSION_CANCELLED`; buổi `waitlistTickets = []`, `waitlistCount = 0`; trả `closed`. Sau đó T043 phải xanh
- [ ] T046 [US4] Tạo `FN/dropin-close/index.ts` theo [contract mục 4](contracts/edge-functions-waitlist.md#4-dropin-close-pg_cron-mỗi-phút) và [research R7](research.md#r7-đóng-hàng-chờ-30-phút-trước-giờ-bắt-đầu-fr-014-ac-41):
  - so khớp header `x-cron-secret` với `CRON_SECRET`, sai → `401`;
  - `dropInSessions where status in [OPEN, FULL], startAt ≤ now + 30 phút`, buổi có `waitlistCount > 0` → mỗi buổi một `runTransaction` gọi `closeWaitlistInTx(..., "CLOSED")`; sau commit gửi `DROPIN_WAITLIST_CLOSED`;
  - `dropInSessions where status in [OPEN, FULL], endAt < now` → `status = COMPLETED` (việc 080 R14 giao cho cron này).

  Sau đó T042 phải xanh
- [ ] T047 [US4] Cài `DI/domain/RegistrationOutcomeResolver.kt` (080-T046) đọc `waitlistEndReason`; `MyDropInsFragment` tab "Đã qua" hiện nhãn "Đã rời hàng chờ", "Không được nhận chỗ", "Buổi bị hủy" (AC 4.4, FR-015). Cho T044 xanh
- [ ] T048 [US4] `DropInDetailFragment` ẩn "Vào hàng chờ" khi hàng chờ đóng (`isWaitlistOpen(now) == false`); `WaitlistEntryFragment` hiển thị kết quả cuối khi lượt bị đóng (không crash, có nút "Tìm buổi khác") (AC 4.3, quickstart M5)

**Checkpoint**: cả 4 user story chạy độc lập; Q10, Q11, Q14 xanh; quickstart M5 đúng.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T049 [P] Espresso `ATEST/WaitlistFlowTest.kt` (Emulator + seed 081): chi tiết `W1` → "Vào hàng chờ" → xác nhận 2 người → thấy vị trí; một tài khoản khác hủy (gọi Edge Function trong test) → màn hình tự chuyển sang vé có nhãn "Được nhận từ hàng chờ"
- [ ] T050 [P] Kiểm tra 4 trạng thái (loading, content, empty, error + "Thử lại") trên `WaitlistEntryFragment` và phần hàng chờ của `DropInDetailFragment`; mất mạng không crash và giữ lựa chọn (FR-016); chế độ tối; bố cục `w600dp`; vùng chạm ≥ 48dp (quickstart M8)
- [ ] T051 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (mục 4.9, 6, 8a), **sau khi D đồng ý** 3 trường `waitlist*` trên `dropInSessions`
- [ ] T052 [P] Cập nhật `docs/features/08-drop-in/README.md` (link plan, tasks spec 081)
- [ ] T053 Chạy toàn bộ [quickstart.md](quickstart.md): test tự động mục 2; M1–M8 trên Emulator; đo SC-001, SC-002; hỏi 5 người thử cho SC-006; chụp ảnh hoặc quay video minh chứng
- [ ] T054 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `deno test -A supabase/functions`, `npm test` trong `firestore-tests/`; không còn cảnh báo Lint mức Error; ktlint sạch

---

## Dependencies & Execution Order

### Phụ thuộc vào tasks 080

| Task 081 | Cần task 080 |
|---|---|
| Toàn bộ | 080-T001 → 080-T016 (Setup, Foundational) |
| T008, T009 | 080-T008, 080-T009 (Rules `dropInSessions`/`registrations`) |
| T019, T023 | 080-T034, 080-T037, 080-T038 (`register-drop-in`, màn hình xác nhận) |
| T022 | 080-T028, 080-T029 (chi tiết buổi, vùng `waitlist_container`) |
| T025, T035, T047 | 080-T046, 080-T047 (lịch sử, resolver) |
| T030–T034 | 080-T006 (`afterSpotsReleased`, `computeRefund`), 080-T040, 080-T048, 080-T053, 080-T055 |

### Phụ thuộc giữa các phase

- **Setup (Phase 1)** → **Foundational (Phase 2)**: chặn mọi user story.
- **US1 (Phase 3)**: sau Phase 2. Không có lượt chờ thì không test được các story sau, nên làm trước.
- **US2 (Phase 4)**: cần US1 phía server (T019) để tạo lượt chờ thật. Test T026 dùng fixture T007 nên viết song song với US1 được.
- **US3 (Phase 5)**: cần T019. Q7 (T036) cần thêm T030–T033 vì phải có đẩy lên mới tranh chấp được.
- **US4 (Phase 6)**: cần T019; `closeWaitlistInTx` (T045) độc lập với US2/US3.
- **Polish (Phase 7)**: sau các story cần cho bản demo.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T005, T030, T045 | **B:** `runQuery` trong transaction ở `_shared/firestore.ts` | Viết bản tối thiểu `runQueryInTx` (REST `runQuery` có trường `transaction`) cạnh bản của 080-T004, rồi báo B gộp |
| T012, T019, T051 | **D:** đồng ý 3 trường `waitlistNextTicket`, `waitlistTickets`, `waitlistCount` trên `dropInSessions` (spec 112 FR-008 ghi "không thêm field") | Code và seed vẫn ghi 3 trường; chỉ hoãn T051. Nếu D không đồng ý thì phải quay lại research R2 trước khi làm US1 |
| T026 (Q13), T043 (Q11) | **D, spec 112:** Edge Function tăng sức chứa gọi `promoteFromWaitlist`, hủy buổi gọi `closeWaitlistInTx` | Test gọi trực tiếp hai hàm trên Emulator; khung T006 đã công bố chữ ký để D import |
| T033, T046 | **D, spec 100:** `send-notification` nhận `DROPIN_SPOT_OPENED` (`sessionId`, `autoLeftSessionIds`) và `DROPIN_WAITLIST_CLOSED` (`sessionId`, `reason`) | Lời gọi gửi sau commit, lỗi bị bỏ qua nên không chặn đẩy lên hay đóng hàng chờ; test dùng mock đếm số lần gọi |
| T038 | **A:** `delete-account` gọi `leaveWaitlistInTx(..., "LEFT", "SYSTEM")` cho các lượt đang chờ | Ghi chú trong PR và báo A; hàm đã sẵn trong `_shared/waitlist.ts` |
| T003, T046 | **Cả nhóm:** bật `pg_cron`, `pg_net` trên Supabase | Gọi tay `dropin-close` khi test (quickstart M5). Mốc 30 phút vẫn đúng vì `register-drop-in` và `promoteFromWaitlist` tự kiểm tra trong transaction (research R7) |

### Trong mỗi user story

Test (fail) → `_shared/waitlist.ts` / Edge Function → repository → UseCase → ViewModel (test xanh) → layout/Fragment.

### Cơ hội làm song song

- Phase 1: T002, T003, T004.
- Phase 2: T006, T007, T010, T011, T013 khác file nhau.
- US1: T015–T018 song song; T019 (server) song song với T020–T025 (app) khi đã có DTO T013.
- US2: T026–T029 song song.
- US3 T036–T037 và US4 T042–T044 viết song song với nhau sau khi xong T019.

## Parallel Example: User Story 2

```bash
# Viết test trước, cùng lúc:
Task: "T026 Test promoteFromWaitlist Q4, Q5, Q8, Q9, Q13 trong supabase/functions/_shared/waitlist.test.ts"
Task: "T027 Test Q6 5 lượt hủy x 10 người chờ x 20 lần trong supabase/functions/cancel-drop-in/waitlist.test.ts"
Task: "T028 Test PROMOTED_GRACE Q12 trong supabase/functions/_shared/dropin.test.ts"
Task: "T029 Unit test DropInTicketViewModel, CancelDropInViewModel, WaitlistEntryViewModel trong app/src/test/.../feature/dropin/"

# Sau đó: T030 → T031 → T032 → T033 (cùng chuỗi server); T034, T035 (app) song song với chuỗi server
```

## Implementation Strategy

### MVP (US1 + US2)

1. Xong Phase 1, 2 (sau khi Phase 1–2 của 080 đã xong).
2. US1 (vào hàng chờ, xem vị trí).
3. US2 (đẩy lên): **chạy T027 (Q6) 20/20 lần xanh** trước khi demo.
4. **Dừng và kiểm tra**: quickstart M1, M3, M4, M6, M7.

### Tăng dần

5. US3 (rời hàng chờ): Q7 20/20 lần xanh.
6. US4 (đóng hàng chờ): cần pg_cron, hoặc gọi tay `dropin-close`.
7. Polish → trước khi đóng băng tính năng (27/11).

Theo [tasks 080](../080-drop-in-session/tasks.md#tăng-dần), spec 081 làm ở tuần 7 cùng US3–US4 của 080, vì `afterSpotsReleased` và `cancel-drop-in` phải có trước.

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `dropin` (ví dụ `feat(dropin): promoteFromWaitlist trong transaction hủy`).
- Ghi log AI vào `docs/ai-log/AI_log_3.md` cho mỗi lần dùng AI.
- Không commit `supabase/.env`, `local.properties`, `google-services.json`.
