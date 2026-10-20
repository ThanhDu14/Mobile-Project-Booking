# Tasks: Quản trị và kiểm duyệt (Feature 120)

**Input**: `specs/120-admin/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/admin-boundaries.md`, `quickstart.md`, Constitution 1.2.0, database README và assignment guide.

**Tổ chức**: Theo bốn User Stories của spec. Các task backend, Rules, Storage và test chỉ thuộc Feature 120 và phần được giao cho nhóm D; không sửa code/tài liệu sở hữu của Feature 010/011/050/100/110/111/112/070/080/081/090/091.

**Quy ước**: Các path là đích triển khai theo `plan.md`; chưa khẳng định file/thư mục đã tồn tại. Không tạo schema, trạng thái, endpoint hay nghiệp vụ ngoài các quyết định được duyệt. Task có ghi “gate” phải chờ hợp đồng tương ứng được chốt.

## Phase 1: Setup và hợp đồng triển khai

**Purpose**: Chốt các dependency gates và thiết lập bề mặt kiểm thử; các mục chưa có hợp đồng không được tự triển khai.

- [ ] T001 [P] Xác nhận Feature 120 chỉ triển khai trong `app/src/main/java/com/example/booking_app/feature/admin/{ui,data,domain}/`, `app/src/main/res/`, `app/src/test/java/com/example/booking_app/feature/admin/`, `app/src/androidTest/java/com/example/booking_app/feature/admin/`, `firestore.rules`, `firestore-tests/test/`, `supabase/functions/` và phần Storage RLS cần thiết trong `supabase/migrations/` theo `specs/120-admin/plan.md`.
- [ ] T002 Chốt trước implementation hợp đồng xác thực Edge Function, thu hồi/refresh claim `appRole`, kiểm tra tài khoản hợp lệ và boundary Rules; ghi quyết định trong tài liệu hợp đồng được chủ sở hữu duyệt trước khi triển khai `firestore.rules` và `supabase/functions/`.
- [ ] T003 Chốt trước implementation cách nhất quán/idempotency/recovery giữa Firestore, Firebase Auth custom claims, tạo venue và notification cho duyệt/từ chối/thu hồi; không thêm field/outbox nếu chưa được phê duyệt, tham chiếu `specs/120-admin/contracts/admin-boundaries.md`.
- [ ] T004 Chốt schema/validation CRUD của `districts` và `amenities`, field/metrics/time provenance của `systemStats` và `venues/{venueId}/dailyStats`, cách phân trang/tìm kiếm, và nghĩa “tài khoản đang hoạt động” cho broadcast; dependency owners gồm Feature 010, 100, nhóm D và database owner, tham chiếu `docs/design/database/README.md`.
- [ ] T005 Dependency gate: xác nhận nguồn dữ liệu, quyền đọc từ Feature 120 và hợp đồng target detail/action với owners của Feature 070 (`REVIEW`), Feature 080/081/112 (`DROP_IN`), Feature 090 (`POST`), Feature 091 (`MESSAGE`), Feature 010 (`USER`) và Feature 111 (`VENUE`); tham chiếu `docs/design/database/README.md` mục 4.7, 4.9–4.12 và spec tương ứng. Gate chỉ cho phép Feature 120 đọc target để kiểm duyệt report, không trao quyền sửa schema/code cho Feature 120; chưa có chấp thuận thì không đọc/hiển thị target detail.
- [ ] T006 Chốt tương thích `systemVouchers` với schema/validation và kiểm tra mã duy nhất xuyên voucher cơ sở theo Feature 050; chốt server path notification với Feature 100; chốt application/venue draft với Feature 110/111 trước khi tích hợp tại các boundary được nêu trong `specs/120-admin/contracts/admin-boundaries.md`.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Bảo đảm kiểm soát truy cập mặc định deny, cấu trúc MVVM và kiểm thử cục bộ dùng chung trước các stories.

- [ ] T007 [P] Thiết lập feature admin theo MVVM, Hilt, ViewModel/StateFlow, repository interface và data/domain/ui tách lớp trong `app/src/main/java/com/example/booking_app/feature/admin/`; Fragment không gọi Firebase trực tiếp.
- [ ] T008 Sau T002, thêm Firestore Rules chỉ cấp quyền đọc/ghi cần thiết cho dữ liệu admin; chặn client tự sửa trường 🔒 và mọi thao tác nhạy cảm không qua server trong `firestore.rules`.
- [ ] T009 Sau T002, thiết lập kiểm tra identity + quyền Admin hiện hành cho từng admin read/mutation trong phần dùng chung của `supabase/functions/_shared/`; tên file cụ thể chỉ chốt sau khi callable contract và cấu trúc server được xác nhận, không tự tạo tên function/endpoint.
- [ ] T010 [P] Bổ sung các ca Firebase Emulator allow/deny cho unauthenticated, PLAYER, OWNER, ADMIN hợp lệ, claim bị thu hồi/hết hạn và truy cập trực tiếp vào các collection admin trong `firestore-tests/test/admin-authorization.test.ts` (theo convention `.test.ts` hiện có tại `firestore-tests/test/base.test.ts`).
- [ ] T011 [P] Bổ sung harness kiểm thử local cho xác thực Edge Function và Supabase Storage RLS `owner-docs` (Admin, applicant đúng hồ sơ, người khác, signed URL hết hạn) trong `firestore-tests/test/owner-docs-access.test.ts`; tên/path file policy trong `supabase/migrations/` chốt sau khi cấu trúc Supabase được xác nhận vì thư mục hiện chưa tồn tại.
- [ ] T012 [P] Thiết lập unit test ViewModel/repository bằng JUnit4, MockK, coroutines-test và Turbine trong `app/src/test/java/com/example/booking_app/feature/admin/`; tên file test cụ thể chốt sau khi package/class feature được tạo theo plan, do source hiện chỉ có `ExampleUnitTest.kt` và chưa có convention cho feature test.

## Phase 3: User Story 1 - Duyệt hồ sơ chủ sân (Priority: P1)

**Goal**: Admin đọc hàng đợi/hồ sơ/giấy tờ và quyết định trên hồ sơ PENDING với dữ liệu nhất quán, thông báo đúng.

**Independent Test**: Tài khoản Admin đọc được PENDING và bằng chứng qua signed URL giới hạn 5 phút; approve/reject hợp lệ cho đúng trạng thái; quyết định cũ/thao tác không quyền bị từ chối; applicant tiếp tục theo Feature 110.

- [ ] T013 [P] [US1] Tạo model/repository cho `ownerApplications` và `users` chỉ với field/schema đã có, query theo `status`/`submittedAt`, ưu tiên PENDING và trạng thái phân trang trong package `app/src/main/java/com/example/booking_app/feature/admin/data/`; tên file cụ thể chốt sau khi class/file layout được duyệt vì plan chỉ xác định package.
- [ ] T014 [US1] Sau T002, tạo server read cho danh sách/chi tiết hồ sơ, xác nhận Admin ở server, kiểm tra trạng thái hiện hành và chỉ trả dữ liệu trong phạm vi schema; function/file path phải được xác nhận trong callable contract trước khi tạo vì hiện chưa có `supabase/functions/` hoặc tên endpoint.
- [ ] T015 [US1] Sau T002/T011, tích hợp truy cập giấy tờ `owner-docs` đúng application qua thiết kế `sign-download` hiện có, chỉ Admin/applicant đúng hồ sơ, signed URL hết hạn sau 5 phút; từ chối người khác kể cả biết path. Tên file adapter/policy chốt sau khi xác nhận cấu trúc Supabase; không tạo cơ chế truy cập mới.
- [ ] T016 [P] [US1] Tạo ViewModel/state cho hàng đợi, detail và giấy tờ gồm loading/content/empty/error/permission-denied/file-unavailable; hỗ trợ retry khi URL hết hạn và không cho cache làm căn cứ quyết định trong `app/src/main/java/com/example/booking_app/feature/admin/ui/`.
- [ ] T017 [US1] Tạo màn hình XML hàng đợi/detail theo schema và UI ownership trong `app/src/main/res/layout/`; dùng strings/dimens/themes resources, accessibility label/contentDescription phù hợp và vùng chạm tối thiểu 48dp.
- [ ] T018 [US1] Sau T003/T006, triển khai server review chỉ chấp nhận application PENDING và applicant còn tồn tại; reject trim reason không rỗng; apply đúng các trạng thái/role/ownerStatus/venueDraft/review metadata theo spec và database README; stale/duplicate request không tạo tác động lặp trong `supabase/functions/`.
- [ ] T019 [US1] Sau T003/T006, phát `OWNER_APPROVED` hoặc `OWNER_REJECTED` cùng lý do từ chối qua server path Feature 100; đảm bảo không báo thành công trước xác nhận và không phát trùng khi retry trong `supabase/functions/`.
- [ ] T020 [US1] Tích hợp form quyết định, khóa nút khi gửi, giữ reject reason khi lỗi, xử lý validation/conflict/timeout bằng tải lại authoritative state trước retry, và không cho mutation offline trong `app/src/main/java/com/example/booking_app/feature/admin/ui/` và `data/`.
- [ ] T021 [P] [US1] Viết unit/repository tests cho trim reason, trạng thái hồ sơ, lỗi, conflict, retry, giữ nội dung nhập và state transitions trong `app/src/test/java/com/example/booking_app/feature/admin/`; tên test file chốt sau khi class/package được xác định vì hiện chưa có convention test cho feature.
- [ ] T022 [US1] Viết Edge Function/Firestore Emulator tests cho approve/reject consistency, applicant missing, stale state, hai Admin tranh chấp, role không hợp lệ, direct unauthenticated/PLAYER/OWNER và quyền bị thu hồi; assert không có partial/duplicate effect theo contract tại `firestore-tests/test/owner-application-review.test.ts` (theo convention `.test.ts` hiện có). Function name/path chờ callable contract.
- [ ] T023 [US1] Viết Espresso kiểm tra luồng xét hồ sơ đầy đủ, loading/empty/error/retry, accessibility cơ bản, DayNight và layout phone/`w600dp` trong `app/src/androidTest/java/com/example/booking_app/feature/admin/`.

## Phase 4: User Story 2 - Xử lý báo cáo và vi phạm (Priority: P1)

**Goal**: Admin xử lý OPEN reports, action phải khớp target và metadata phản ánh đúng kết quả.

**Independent Test**: Admin đọc report/target còn tồn tại, resolve action phù hợp hoặc dismiss bằng NONE; report cũ, target mất/đổi trạng thái hoặc action không khớp không gây side effect.

- [ ] T024 [P] [US2] Tạo model/repository report theo enum/schema hiện có (`targetType`, `targetPath`, `reason`, `detail`, `status`, `action`, `handledBy`, `handledAt`) trong `app/src/main/java/com/example/booking_app/feature/admin/data/`.
- [ ] T025 [US2] Đọc danh sách/chi tiết `reports` OPEN từ collection hiện có, xác minh quyền Admin cho từng request; chỉ đọc/hiển thị target theo `targetPath` sau khi T005 xác nhận source và read permission với owner tương ứng (070 REVIEW, 080/081/112 DROP_IN, 090 POST, 091 MESSAGE, 010 USER, 111 VENUE). Tách report data khỏi target data; không tự truy vấn dữ liệu private hoặc lộ target khi thiếu quyền. Tên file/function chờ callable contract và cấu trúc server.
- [ ] T026 [US2] Sau T005, triển khai mutation report chỉ từ OPEN; enforce mapping VENUE→LOCK_VENUE, USER→LOCK_USER, POST/MESSAGE/REVIEW→HIDE_CONTENT chỉ theo quyền/hợp đồng target owner; DISMISSED phải dùng NONE; lưu metadata report server-side. Feature 120 chỉ cập nhật report và gọi đường xử lý đã được owner xác nhận, không sửa schema/code target thuộc feature khác. Target stale/missing/đã xử lý không lặp side effect; tên file/function chờ callable contract.
- [ ] T027 [US2] Chỉ cho phép `DROP_IN` + `HIDE_CONTENT` sau khi T005 xác nhận với owners 080/081/112 rằng target có field/trạng thái hide hiện hữu và quyền xử lý tương ứng; nếu chưa xác nhận, UI/server không hiển thị action và giữ report unchanged. Không tạo trường/trạng thái/cơ chế truy cập mới; tên file/function chờ contract.
- [ ] T028 [P] [US2] Tạo report queue/detail/action states gồm loading/empty/error/permission-denied/conflict, retry, target missing và mismatched action trong `app/src/main/java/com/example/booking_app/feature/admin/ui/`.
- [ ] T029 [US2] Tạo XML report queue/detail với trạng thái xử lý và action hợp lệ theo target; resources, accessibility 48dp, DayNight và responsive phone/`w600dp` trong `app/src/main/res/layout/` và `values/`.
- [ ] T030 [P] [US2] Viết unit/repository tests cho action-target mapping, dismissed NONE, target missing, stale report, conflict và retry trong `app/src/test/java/com/example/booking_app/feature/admin/`; tên test file chốt khi class/package được xác định.
- [ ] T031 [US2] Sau T005, viết Emulator/Edge Function tests cho OPEN→RESOLVED/DISMISSED, `handledBy`/`handledAt`, direct access denial, incompatible action, nhiều reports cùng target và target không tồn tại/đã đổi trạng thái tại `firestore-tests/test/report-moderation.test.ts`; fixtures target chỉ theo schema được owner xác nhận.
- [ ] T032 [US2] Viết Espresso/accessibility/theme/phone/`w600dp` checks cho queue, detail, confirm action, dismissed và lỗi trong `app/src/androidTest/java/com/example/booking_app/feature/admin/`.

## Phase 5: User Story 3 - Quản lý tài khoản và venue vi phạm (Priority: P2)

**Goal**: Admin tra cứu user, khóa/mở khóa account, thu hồi owner và khóa/mở khóa venue theo các state đã chốt.

**Independent Test**: Chỉ Admin hợp lệ đổi được trạng thái; account ACTIVE↔LOCKED; venue LOCKED mở về ACTIVE/HIDDEN; revoke lưu reason, giữ application APPROVED và giữ nguyên venue status.

- [ ] T033 [P] [US3] Tạo model/repository tra cứu user và đọc trạng thái venue dùng field hiện có, không hiển thị dữ liệu cá nhân ngoài schema/need-to-know trong `app/src/main/java/com/example/booking_app/feature/admin/data/`.
- [ ] T034 [US3] Sau T002/T005 đối với target USER, triển khai server-side user lookup và accountStatus ACTIVE/LOCKED mutation, xác minh Admin và hiện trạng trước ghi; chỉ đọc field user được phép theo Feature 010/database README. Tên file/function chờ callable contract và cấu trúc server được xác nhận.
- [ ] T035 [US3] Sau T003/T006, triển khai owner revocation với lý do trim không rỗng; đặt `role=PLAYER`, `ownerStatus=NONE`, lưu `ownerRevokedReason`, giữ application APPROVED và mọi venue status, phát `OWNER_REVOKED` qua Feature 100 trong `supabase/functions/`.
- [ ] T036 [US3] Triển khai server lock venue và Admin-only unlock về lựa chọn ACTIVE hoặc HIDDEN; không thay đổi court/price rules/bookings hoặc owner operations trong `supabase/functions/`.
- [ ] T037 [P] [US3] Tạo ViewModel/UI state cho lookup, confirm lock/unlock/revoke, validation, permission-denied, conflict, timeout reload và offline disabled trong `app/src/main/java/com/example/booking_app/feature/admin/ui/`.
- [ ] T038 [US3] Tạo XML quản lý user/venue với lý do thu hồi bắt buộc, xác nhận thao tác nhạy cảm, trạng thái rõ ràng, strings/resources, accessibility 48dp, DayNight, phone/`w600dp` trong `app/src/main/res/layout/` và `values/`.
- [ ] T039 [P] [US3] Viết unit tests cho account/venue transitions, revoke reason validation, timeout/conflict refresh và form state trong `app/src/test/java/com/example/booking_app/feature/admin/`.
- [ ] T040 [US3] Viết Emulator/Edge Function tests cho allow/deny account và venue mutations, owner không tự unlock LOCKED venue, revoke bảo toàn APPROVED/venue state, và bảo vệ khỏi client sửa field quản trị trong `firestore-tests/test/`.
- [ ] T041 [US3] Viết integration checks với Feature 010/011/112/100 owners để xác nhận account LOCKED chặn auth/session, owner revocation cập nhật trải nghiệm/lý do và mất quyền operation, notification OWNER_REVOKED được nhận; chỉ sửa file Feature 120 nếu phát hiện thiếu, ghi dependency cho owner kia trong `app/src/androidTest/java/com/example/booking_app/feature/admin/`.

## Phase 6: User Story 4 - Theo dõi tình hình và công cụ quản trị (Priority: P3)

**Goal**: Admin đọc số liệu có nguồn, quản lý voucher hệ thống theo Feature 050 và thực hiện danh mục/broadcast chỉ sau khi semantics được chốt.

**Independent Test**: Admin chỉ thấy dữ liệu thực có; empty khi thiếu; voucher obeys Feature 050/global uniqueness; SYSTEM broadcast tới mọi account active theo Feature 100; category CRUD tuân hợp đồng đã duyệt.

- [ ] T042 [US4] Sau T004, triển khai read-only stats từ `systemStats` và `venues/{venueId}/dailyStats`; chỉ render field tồn tại, nguồn/thời điểm nếu schema có, không tự tính metric, trong `supabase/functions/` và `app/src/main/java/com/example/booking_app/feature/admin/data/`.
- [ ] T043 [US4] Sau T004, triển khai danh mục trên đúng `districts`/`amenities` với schema/validation/CRUD đã được owners chốt; không đổi logic sử dụng của Feature 020/022 trong `supabase/functions/`.
- [ ] T044 [US4] Sau T006, triển khai tạo/sửa/tắt `systemVouchers/{code}` theo cùng schema và validation Feature 050; kiểm tra mã trùng toàn hệ thống trước ghi; không cho Admin ghi `usedCount`/`redemptions` trong `supabase/functions/`.
- [ ] T045 [US4] Sau T004/T006, phát `SYSTEM` qua server path của Feature 100 tới toàn bộ tài khoản active; không fan-out từ client, không tạo schema notification mới và không hiện thành công trước xác nhận trong `supabase/functions/`.
- [ ] T046 [P] [US4] Tạo ViewModel/repository states cho stats, categories, system vouchers và broadcast gồm loading/empty/error/retry/permission-denied/offline; khóa thao động trong lúc gửi và tránh optimistic success trong `app/src/main/java/com/example/booking_app/feature/admin/`.
- [ ] T047 [US4] Tạo XML dashboard/tools dùng resources, accessibility 48dp, DayNight, responsive phone/`w600dp`; chỉ render thao tác đã được gate/schema chốt trong `app/src/main/res/layout/`, `values/` và `values-night/`.
- [ ] T048 [P] [US4] Viết unit/repository tests cho stats empty/source, validation voucher theo Feature 050, uniqueness conflict, category contract và broadcast error/retry trong `app/src/test/java/com/example/booking_app/feature/admin/`.
- [ ] T049 [US4] Viết Emulator/Edge Function tests cho stats read privacy, voucher fields/uniqueness/client protected usage fields, category allow/deny sau khi chốt schema và SYSTEM recipient/fan-out/retry theo Feature 100 contract trong `firestore-tests/test/`.
- [ ] T050 [US4] Viết Espresso checks cho stats empty/content, category/voucher forms và broadcast success/error, accessibility, DayNight, phone/`w600dp` trong `app/src/androidTest/java/com/example/booking_app/feature/admin/`.

## Phase 7: Polish và cross-cutting

- [ ] T051 [P] Hoàn tất kiểm tra UI XML/ViewBinding, strings mặc định tiếng Việt, resources thay hardcode, accessibility labels/touch targets, DayNight và phone/`w600dp` cho toàn bộ màn hình admin trong `app/src/main/res/` và `app/src/androidTest/java/com/example/booking_app/feature/admin/`.
- [ ] T052 [P] Rà soát mọi Admin read/mutation có server authorization, denied direct request, không log owner-docs/rejectReason hoặc dữ liệu nhạy cảm và không dùng cache làm căn cứ mutation trong `firestore.rules`, `supabase/functions/`, `supabase/migrations/`.
- [ ] T053 Đối chiếu luồng owner application/notification với Feature 110/100, account/venue/operation với 010/011/111/112, voucher với 050 và report producers/targets với 070/080/081/090/091; ghi interface gaps cho đúng owner, không sửa code/tài liệu của feature khác trong `specs/120-admin/tasks.md`.
- [ ] T054 Đo SC-006 theo cùng kịch bản trên Firebase Emulator/Supabase local, không dùng project thật: bắt đầu đồng hồ tại lúc Admin chọn hồ sơ PENDING trong hàng đợi để bắt đầu xét (trước khi tải detail/giấy tờ); dừng khi app nhận xác nhận authoritative từ server và hiển thị trạng thái kết quả APPROVED hoặc REJECTED. Chạy và ghi nhận riêng luồng duyệt lẫn từ chối. Hồ sơ đầy đủ dùng định nghĩa từ Feature 110 FR-002/Validation: các trường bắt buộc tên cơ sở, địa chỉ, quận/huyện, vị trí ghim, số điện thoại cơ sở, số sân, giờ mở cửa, tên và số điện thoại người đại diện; có ít nhất 1 ảnh cơ sở và ít nhất 1 giấy tờ chứng minh hợp lệ; hồ sơ đang PENDING theo Feature 120. Ghi timestamp bắt đầu/kết thúc và elapsed time cho từng lượt trong kết quả quickstart; đạt nếu mỗi luồng hoàn tất ≤120 giây, không đạt nếu quá 120 giây hoặc không có xác nhận cuối. Trước khi cố định fixture, xác nhận với owner Feature 110 liệu danh sách trường/ảnh/giấy tờ này có phải định nghĩa đầy đủ duy nhất hay còn mục bắt buộc khác tại `specs/120-admin/quickstart.md`.

## Dependency & Execution Order

### Phase dependencies

- Phase 1 gates là dependency trước các mutation/read implementation liên quan: T002 chặn mọi endpoint/Rules admin; T003 chặn review/revoke, consistency/retry/notification; T004 chặn stats/category/broadcast-recipient; T005 chặn mọi target detail read và action report cho tới khi owner của từng target xác nhận nguồn/quyền, đồng thời chặn riêng DROP_IN hide nếu chưa có field/state hỗ trợ; T006 chặn tích hợp Feature 050/100/110/111.
- Phase 2 phụ thuộc T002 trước khi hoàn tất Rules/server auth; T007, T010–T012 có thể bắt đầu độc lập. Phase 2 hoàn tất trước tích hợp UI/data stories.
- US1 và US2 có thể chạy song song sau auth foundation; US3 phụ thuộc auth foundation, tích hợp notification và có giao điểm với action của US2; US4 phần stats/voucher/category/broadcast phụ thuộc gates tương ứng.
- Phase 7 phụ thuộc các stories được chọn; T054 chỉ chạy sau khi các gate của các luồng kiểm thử đã được giải quyết.

### Dependency graph

```text
T001 ─┬─ T007 ─┬─ US1 (T013–T023)
T002 ─┤         ├─ US2 (T024–T032)
      └─ T008–T011
T003 ───────────── US1 review / US3 revoke
T004 ───────────── US4 stats / categories / broadcast recipients
T005 ───────────── US2 T025–T027 target detail/action by owner-confirmed source and permission; T034 USER lookup boundary
T006 ───────────── US1 notifications/venue, US3 revoke notification, US4 voucher/SYSTEM
US1 + US2 ──────── US3 integration points; all selected stories ─ Phase 7
```

### Parallel opportunities

- Sau T002, T010 và T011 tách biệt; T012 có thể song song với backend test harness.
- Trong từng story, model/state/UI có thể chạy song song theo các task `[P]` khi hợp đồng/schema đã qua gate; server mutation và tests phụ thuộc phải chạy theo thứ tự đã liệt kê.
- US1 và US2 có thể được triển khai song song bởi người khác nhau sau Phase 2. US3/US4 cũng có thể làm song song ở phần độc lập, nhưng không được vượt gates và phải phối hợp với cùng server/auth boundary.

## Traceability: FR / SC coverage

Danh mục requirement lấy nguyên theo `specs/120-admin/spec.md`: **FR-001–FR-014, FR-014a, FR-015–FR-016** (17 mục) và **SC-001–SC-006** (6 mục). Không có FR-017 trong Admin spec.

| Requirement | Task IDs | Đánh giá coverage / ghi chú |
|---|---|---|
| FR-001 | T002, T008–T010, T052 | Có task cho contract/Rules/server auth và allow/deny; implementation phụ thuộc gate T002. |
| FR-002 | T013–T014, T016–T017, T021–T023 | Có task query ưu tiên PENDING, server read, UI và tests cho hàng đợi/detail. |
| FR-003 | T011, T015–T016, T022–T023, T052 | Có task kiểm tra quyền applicant/Admin, signed URL 5 phút, lỗi file và test truy cập. T011/T015 còn phụ thuộc cấu trúc Storage đã xác nhận. |
| FR-004 | T003, T006, T018–T023 | Có task approve, cập nhật role/status/venueDraft và kiểm tra consistency/race. Phụ thuộc quyết định recovery/idempotency tại T003 và contract application/venue tại T006. |
| FR-005 | T018–T023; T053 (phần giao diện applicant thuộc Feature 110) | Server reject/reason/notification được giao. Xem lý do và resubmit do Feature 110 sở hữu; T053 chỉ xác minh boundary, không thay thế task thực hiện/test phía Feature 110. Vì vậy coverage của yêu cầu liên feature còn phụ thuộc Feature 110. |
| FR-006 | T053 (xác minh boundary với Feature 110) | Admin task không triển khai resubmission; hành vi thuộc Feature 110. Coverage chức năng phụ thuộc Feature 110 và không được tính là implementation bởi T053. |
| FR-007 | T003, T018, T021–T022 | Có task quyết định recovery/idempotency, từ chối stale state và test cạnh tranh; còn phụ thuộc T003. |
| FR-008 | T002, T009–T010, T033–T041 | Có task server lookup/mutation, account transitions và kiểm tra tích hợp Feature 010; contract/auth phụ thuộc T002. |
| FR-009 | T003, T006, T035–T041 | Có task revoke, reason, giữ application/venue state, notification và integration check; phụ thuộc T003/T006. |
| FR-010 | T005, T024–T025, T028–T032 | Report read có task; target detail chỉ sau xác nhận source/quyền ở T005. Gate giữ nguyên. |
| FR-011 | T005, T026–T032 | Có task status/action/metadata, resolve/dismiss và tests; mutation theo target phụ thuộc T005. |
| FR-012 | T005, T026–T027, T030–T031 | Mapping action, target tồn tại/đủ điều kiện và stale handling có task. `DROP_IN` hide vẫn gated; không đánh dấu action đó đã được hỗ trợ. |
| FR-013 | T004, T042, T046–T050 | Có task read-only stats, empty/source states và tests; schema/metrics phụ thuộc T004. |
| FR-014a | T006, T044, T048–T049 | Có task tạo/sửa/tắt voucher, uniqueness và bảo vệ usage fields; phụ thuộc contract/validation Feature 050 tại T006. |
| FR-014 | T004, T043, T045–T050 | Category CRUD và SYSTEM broadcast có task/tests; schema category, active-account predicate và Feature 100 server path còn phụ thuộc T004/T006. |
| FR-015 | T003, T006, T019, T022–T023 | Có task phát OWNER_APPROVED/OWNER_REJECTED, lý do từ chối và test; retry/notification contract phụ thuộc T003/T006. |
| FR-016 | T002, T008–T010, T022, T031, T040, T049, T052 | Có task kiểm tra quyền hiện hành, claim/session invalidation và direct denial. Hành vi claim/account validity phụ thuộc T002. |
| SC-001 | T018, T021–T022 | Có assert consistency trong approve/reject tests; phụ thuộc contract recovery T003. |
| SC-002 | T018–T023; T053 (tích hợp với Feature 110) | Admin lưu/trả lý do và phát notification; khả năng applicant xem đúng lý do và resubmit thuộc Feature 110, cần xác minh tích hợp. Không coi Feature 120 tự bao phủ phần đó. |
| SC-003 | T011, T015–T016, T022–T023, T052 | Có tests cho applicant/Admin và các vai trò khác, cùng UI/error behavior; signed access còn phụ thuộc Storage contract/setup. |
| SC-004 | T010, T022, T031, T040, T049, T052 | Có test unauthenticated/PLAYER/OWNER và rà soát direct requests cho các nhóm thao tác Admin. |
| SC-005 | T026, T030–T031 | Có task ghi/assert trạng thái cuối, action, handledBy/handledAt và dismissed NONE; action target phụ thuộc T005. |
| SC-006 | T016–T020, T023, T054 | Có cách đo riêng approve/reject tới server-confirmed result ≤120 giây; fixture đầy đủ vẫn cần Feature 110 xác nhận theo T054. |

**Coverage chưa hoàn tất trong phạm vi Feature 120:** phần resubmission và trải nghiệm applicant của FR-005/FR-006/SC-002 thuộc Feature 110; T053 là boundary check, không phải task triển khai Feature 110. `DROP_IN` + `HIDE_CONTENT` của FR-012 chưa được phép cho tới khi T005 owners xác nhận field/state và quyền tương ứng. Các mục còn lại có task hiện tại, nhưng một số chỉ có thể hoàn thành sau dependency gate nêu trong cột trên.

## Independent acceptance criteria by story

- **US1**: Hàng đợi/detail chỉ đọc bởi Admin; giấy tờ đúng applicant/Admin và URL ≤5 phút; approve/reject cập nhật đúng schema/state, reject reason hợp lệ; stale/race/unauthorized không gây thay đổi; đo từ lúc mở hồ sơ PENDING để bắt đầu xét tới lúc app hiển thị xác nhận server và trạng thái kết quả, hồ sơ đầy đủ theo Feature 110 FR-002/Validation, đạt ≤120 giây.
- **US2**: OPEN report resolve hoặc dismiss với đúng action/metadata; report được đọc riêng với target; target details/action chỉ truy cập theo T005 owner-confirmed source/permission; target missing/stale/incompatible và unauthorized không có side effect; DROP_IN hide chỉ khi gate có kết luận hỗ trợ.
- **US3**: ACTIVE↔LOCKED account; venue LOCKED→ACTIVE/HIDDEN chỉ Admin; revoke ghi reason, role/status đúng, application APPROVED và venue status giữ nguyên; Feature 010/011/112/100 phản ánh thay đổi qua giao diện của chính họ.
- **US4**: Stats chỉ render nguồn hiện có/empty; category CRUD chỉ sau schema gate; system voucher tuân Feature 050 và global uniqueness; SYSTEM tới mọi account active theo hợp đồng Feature 100; mọi tác vụ bị từ chối cho caller không Admin.

## MVP / Implementation strategy

MVP là **US1** (duyệt hồ sơ P1), sau khi hoàn tất Phase 1 gates T002/T003/T006 liên quan và Phase 2; ưu tiên approve/reject, bằng chứng riêng tư, xử lý race và notification. P1 US2 tiếp theo. US3 rồi US4 theo thứ tự ưu tiên; US4 chỉ mở các phần đã có schema/hợp đồng rõ. Stories có thể phát hành độc lập khi acceptance criteria tương ứng đạt và dependency feature owners xác nhận.

## Open implementation gates (do not infer)

- Admin token verification, claim revocation/refresh, account validity và callable server interfaces chưa có contract.
- Không có cơ chế atomic/recovery/idempotency đã chốt cho Firestore + Auth claim + venue creation + notifications.
- Chưa có schema/metrics cho stats, schema/CRUD/validation cho districts/amenities, và tiêu chí truy vấn “active account”/broadcast fan-out.
- Chưa xác định DROP_IN có hỗ trợ ẩn và trạng thái/field nào.
- User lookup, page limits/indexes và query filters chưa chốt; không tự thêm index/schema.
- Quyền admin trong DB README, client Rules và yêu cầu Edge Function của Constitution/spec cần được đồng bộ trong gate T002.
- Hilt/ViewModel/Coroutines/test dependencies chưa hiện diện; mọi dependency mới phải qua version catalog và lý do theo Constitution/plan.
- Tên file Android model/repository/ViewModel/test chưa thể xác minh vì source chỉ có starter và plan mới quy định package layout; chốt class/file layout trong bước thiết kế implementation trước khi thay directory targets bằng file paths cụ thể.
- Supabase function/migration names/layout chưa thể xác minh vì `supabase/functions/` và `supabase/migrations/` hiện không tồn tại; callable contract phải chốt function/path trước implementation.
- Feature 110 cần xác nhận danh sách trường/giấy tờ ở FR-002/Validation là định nghĩa đầy đủ cho “hồ sơ đầy đủ” dùng làm fixture SC-006.
- Target read gates cần xác nhận riêng từ owners 070, 080, 081, 090, 091 và data owners 010/111/112 theo target type trước T025/T026/T034; quyền đọc reports không tự động cấp quyền đọc dữ liệu target.
- Tích hợp Feature 010 (account lock), 011 (hiển thị lý do revoke), 100 (notification), 110 (resubmit/application), 111 (venue lifecycle), 112 (owner operation), 050 (voucher) và report producers 070/080/081/090/091 chỉ trong boundary Feature 120; thay đổi phía feature owner thuộc owner đó.
