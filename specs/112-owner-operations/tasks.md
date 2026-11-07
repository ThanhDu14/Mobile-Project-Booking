# Tasks: Vận hành cơ sở của chủ sân

**Input**: `spec.md`, `plan.md`, `data-model.md`, `contracts/owner-operations-boundaries.md`, `quickstart.md`, Constitution và các tài liệu liên quan.

**Phạm vi mã nguồn theo plan**: Android tại `app/src/main/java/com/example/booking_app/feature/owner/{ui,domain,data}/`; resources ở `app/src/main/res/`; test ở `app/src/test/`, `app/src/androidTest/`, `firestore-tests/` và backend feature 112 ở `supabase/functions/` sau khi hợp đồng được chốt.

**Quy ước**: Chỉ thêm field/schema/API đã được tài liệu nguồn chốt. Các task bị ghi `gate` không bắt đầu triển khai trước khi dependency được giải quyết. Không sửa package/file do Feature 110, 111, 120, 040/041/050/060/080/081/100 sở hữu.

## Phase 1: Setup và truy vết yêu cầu

**Purpose**: Chuẩn bị truy vết task; không thay đổi hạ tầng hoặc tài liệu ngoài Feature 112.

- [ ] T001 Rà soát Coverage Matrix trong `specs/112-owner-operations/tasks.md` để mọi US/FR/SC có task và mọi task implementation có story, path theo ownership `feature/owner`, hoặc dependency gate cụ thể; ghi các ngoại lệ còn mở ngay trong matrix.

## Phase 2: Foundational — cổng hợp đồng và quyền dùng chung

**Purpose**: Xác định các hợp đồng/blocker dùng chung trước các luồng ghi nhạy cảm. Các task gate chỉ cập nhật/tạo hợp đồng trong phạm vi Feature 112 sau khi người phụ trách tương ứng thống nhất; không tự chọn nghiệp vụ.

- [ ] T003 Xác nhận nguồn xác thực owner đã duyệt (`role`/claim và `ownerStatus`) với Feature 110/120 và database owner; ghi quyết định vào `specs/112-owner-operations/contracts/owner-operations-boundaries.md` trước khi triển khai bất kỳ lệnh ghi nào (FR-001, FR-002; gate).
- [ ] T004 Chốt quyền sở hữu qua venue cha, xử lý venue/court bị khóa hoặc quyền owner bị thu hồi, và cách từ chối không tiết lộ dữ liệu; ghi vào `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (FR-001, FR-002, FR-006; gate).
- [ ] T005 Chốt riêng hợp đồng ghi booking: approve/reject theo Feature 040/050/060 và cancel CONFIRMED của owner (refund, release slot, notification) với Feature 050/060/100; ghi transaction boundary và tên function chỉ khi chủ sở hữu xác nhận trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (FR-003–FR-006; gate cho T012–T016; T013 chỉ phụ thuộc phần cancel booking này).
- [ ] T006 Chốt riêng hợp đồng owner/drop-in create/update/cancel và check-in registration với Feature 080/081; với voucher, chốt CRUD/ownership cùng Feature 111/050 và yêu cầu database owner xác nhận `venueId` trong FR-013 là context từ `venues/{venueId}/vouchers/{code}` hay field lưu. Ghi từng quyết định độc lập trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md`; không thêm field (FR-008–FR-014; gate cho task drop-in/voucher; booking tasks không chờ gate này).
- [ ] T007 Chốt nguồn/cấu trúc và độ mới của `dailyStats`, mẫu số thời lượng khả dụng khi priceRules/giờ hoạt động thiếu hoặc đổi, và cách phân bổ booking nhiều court/slot qua ngày/bucket 60 phút; ghi trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md` với database owner/D (FR-015–FR-019; gate).
- [ ] T008 Đối chiếu các trạng thái đăng ký drop-in, hủy/hoàn tiền, hàng chờ và quy tắc sức chứa với Feature 080/081; xác nhận schema README là nguồn dùng trước khi triển khai luồng liên quan và ghi phần chưa thống nhất trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (FR-008–FR-012; gate).
- [ ] T009 Tạo test riêng `firestore-tests/test/owner-operations.test.ts` theo harness hiện có trong `firestore-tests/README.md`, bao phủ đọc/ghi được phép của owner đã duyệt và chặn owner chưa duyệt, venue/court ngoài quyền, venue locked/owner revoked; chạy `npm test` từ `firestore-tests/` (FR-001–FR-002, SC-002; Constitution II, IV). File test chưa tồn tại; tạo theo setup `firestore-tests/test/setup.ts`, không sửa test file của feature khác.
- [ ] T010 [P] Tạo bộ test fake repository và coroutine test conventions cho `feature/owner` tại `app/src/test/java/com/example/booking_app/feature/owner/`; không thêm dependency hay schema chưa có trong `plan.md` (Constitution I, IV).

## Phase 3: User Story 1 — Xử lý booking và xem lịch (P1)

**Goal**: Owner chỉ xem/ra quyết định booking thuộc venue mình với trạng thái, hạn và slot hiện hành; lịch ngày/tuần phản ánh đúng booking/slot.

**Independent Test**: Seed booking/slot thuộc venue của owner và venue khác; xác minh danh sách PENDING theo deadline, approve/reject/cancel hợp lệ có kết quả nguyên tử, lịch đúng ngày/giờ/court và truy cập trái phép bị chặn.

- [ ] T011 [P] [US1] Viết test use case/repository cho danh sách booking theo venue, thứ tự `approvalDeadline`, đồng hồ đếm ngược, refresh khi trạng thái thay đổi và lọc trạng thái lịch trong `app/src/test/java/com/example/booking_app/feature/owner/` (FR-003, FR-007; SC-001, SC-008).
- [ ] T012 [US1] Viết Rules Emulator test trong `firestore-tests/test/owner-operations.test.ts` cho quyền đọc booking thuộc venue mình được phép và booking venue khác/field nhạy cảm bị chặn; chạy `npm test` từ `firestore-tests/`. Giao dịch approve/reject của Edge Function không được giả lập bằng Rules test; server-side transaction test được giao riêng T059 (FR-002–FR-006; SC-001–SC-002; dependency T003–T005 phần booking).
- [ ] T013 [P] [US1] Sau khi phần cancel booking của T005 được chốt và chủ sở hữu xác định đường dẫn test thực tế, thêm transaction test hủy CONFIRMED chưa bắt đầu/chưa check-in, lý do bắt buộc, refund 100%, nhả đúng slot và notification sau commit; chạy bằng lệnh test mà function harness đã xác nhận (FR-004a–FR-006; SC-001–SC-002; dependency chỉ phần booking của T005, không chờ T006/T008). Repo chưa có `supabase/functions/` hay Edge Function test harness nên chưa ấn định file/function test.
- [ ] T014 [US1] Định nghĩa model/domain và repository interface cho booking owner, approval deadline, slot và projection lịch trong `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-003–FR-007; phụ thuộc T003–T005).
- [ ] T015 [US1] Implement truy vấn/realtime repository chỉ giới hạn venue được server xác minh; ánh xạ booking và slot theo schema hiện có, không hiển thị HOLD như booking xác nhận, trong `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-002–FR-003, FR-007).
- [ ] T016 [US1] Implement server transaction approve/reject và cancel CONFIRMED theo đúng các contract booking tương ứng đã chốt ở T005; chỉ sửa function/path do chủ sở hữu 040/050/060/100 xác nhận, trong phạm vi 112 (FR-003–FR-006; SC-001–SC-002; dependency T003–T005 phần booking).
- [ ] T017 [US1] Implement ViewModel StateFlow cho hàng chờ, đồng hồ deadline, kết quả quyết định, tải lại trạng thái sau conflict và lịch ngày/tuần trong `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-003–FR-007).
- [ ] T018 [US1] Tạo Fragment/layout XML lịch và hàng chờ booking, phân biệt PENDING/CONFIRMED/lịch sử/HOLD/DROP_IN, hỗ trợ loading/content/empty/error/offline/retry bằng resources trong `app/src/main/res/layout/`, `app/src/main/res/values/`, `app/src/main/res/values-night/` (FR-003, FR-006–FR-007, FR-020; Constitution V).
- [ ] T019 [US1] Tạo test UI/Espresso cho xem lịch và xử lý booking, kiểm tra TalkBack content descriptions, vùng chạm tối thiểu 48dp, light/dark theme và phone/`w600dp` trong `app/src/androidTest/java/com/example/booking_app/feature/owner/` (SC-008–SC-009; Constitution IV–V).
- [ ] T020 [US1] Đo tải danh sách/lịch và phản hồi thao tác theo ngưỡng SC-008 trên môi trường test 4G, ghi kịch bản/kết quả trong `specs/112-owner-operations/quickstart.md` (SC-008; không sửa tài liệu feature khác).

## Phase 4: User Story 2 — Mở và quản lý buổi drop-in (P1)

**Goal**: Tạo/sửa/hủy session cùng slot nguyên tử; xem roster hiện có và check-in QR một lần mà không thay nghiệp vụ đăng ký của Feature 080/081.

**Independent Test**: Tạo session trên court/slot hợp lệ; thử xung đột từng trạng thái slot để bảo đảm không có session/slot một phần; xem REGISTERED/WAITLISTED; check-in QR hợp lệ một lần và từ chối token sai/đã dùng/sai buổi/đã hủy.

- [ ] T021 [P] [US2] Viết test validation/domain cho `capacity` số nguyên dương, `pricePerPerson` Long VND không âm, sức chứa không thấp hơn người đang chiếm chỗ và không cho owner sửa `registeredCount` trong `app/src/test/java/com/example/booking_app/feature/owner/` (FR-010–FR-011; SC-003).
- [ ] T022 [P] [US2] Sau khi T006 phần drop-in chốt transaction contract và test location, viết Edge Function transaction tests cho create/update/cancel session: owner/ACTIVE/court/giờ hợp lệ, slot 30 phút; mọi xung đột HOLD/BOOKED/BLOCKED rollback toàn bộ. Chạy `deno test` chỉ khi harness/path được xác nhận theo contract; repo hiện chưa có `supabase/functions/` hay Deno harness (FR-008–FR-010a; SC-002–SC-003; dependency T003–T006 phần drop-in, T008).
- [ ] T023 [P] [US2] Sau khi T006 phần check-in registration xác định function/harness, viết Edge Function transaction tests cho QR đúng registration/session được chấp nhận một lần và QR sai/replay/session khác/registration CANCELLED/venue ngoài quyền bị từ chối; dùng lệnh test đã xác nhận. Không dùng Rules Emulator thay kiểm tra token server-side; repo hiện chưa có Edge Function harness (FR-011–FR-012; SC-004; dependency T003–T004, T006 phần check-in registration, T008).
- [ ] T024 [US2] Định nghĩa domain models/repository interfaces cho session, registration roster và command owner theo schema README/Feature 080/081 đã thống nhất trong `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-008–FR-012; gate T008).
- [ ] T025 [US2] Implement transaction create/update/cancel session cùng slots DROP_IN, dùng `promoteFromWaitlist`/`_shared/dropin.ts` theo Feature 080/081 đã chốt; function location/name chỉ lấy từ quyết định T006, không sửa file thuộc 080/081 (FR-008–FR-010a; SC-003; dependency T003–T006 phần drop-in, T008).
- [ ] T026 [US2] Implement server authorization và QR check-in registration một lần, chỉ chuyển trạng thái hiện có sang CHECKED_IN theo contract T006; function location/name chỉ dùng sau xác nhận chủ sở hữu (FR-011–FR-012; SC-004; dependency T003–T004, T006 phần check-in registration, T008).
- [ ] T027 [US2] Implement repository đọc sessions/registrations theo venue cha và gửi commands qua contract server trong `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-002, FR-011–FR-012).
- [ ] T028 [US2] Implement ViewModel tạo/sửa/hủy form, roster, scan result và trạng thái loading/content/empty/error/offline/retry trong `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-008–FR-012, FR-020).
- [ ] T029 [US2] Tạo Fragment và XML cho session form/roster/QR scanner, kiểm tra quyền camera runtime và đường nhập QR nếu người dùng từ chối quyền trong `app/src/main/res/layout/`, `app/src/main/res/values/`, `app/src/main/res/values-night/` (FR-009–FR-012; Constitution V–VI).
- [ ] T030 [US2] Tạo unit tests capacity/price trong `app/src/test/java/com/example/booking_app/feature/owner/DropInValidationTest.kt` và Espresso tests roster/session form trong `app/src/androidTest/java/com/example/booking_app/feature/owner/OwnerDropInTest.kt`; đây là file mới theo package/source-set convention, xác nhận Gradle source sets trước khi tạo vì thư mục owner tests chưa có. Transaction/QR server tests thuộc T022–T023, theme/size integration được rà soát cuối tại T054. Chạy unit tests bằng `./gradlew testDebugUnitTest`; Espresso target `connectedDebugAndroidTest` cần thiết bị/emulator Android (SC-003–SC-004, SC-009; Constitution IV–V).

## Phase 5: User Story 5 — Check-in đơn đặt sân (P1)

**Goal**: Owner check-in booking QR hoặc mã chữ qua `checkin-booking`; thu tiền chỉ khi owner xác nhận; trạng thái kết thúc vẫn thuộc server.

**Independent Test**: Với booking CONFIRMED thuộc owner, xác nhận QR/mã hợp lệ trong cửa sổ thời gian, retry/replay, sai venue/trạng thái/thời gian và hành động xác nhận thu tiền; xác minh chỉ ghi `checkedInAt`/PAID được phép, không tự chuyển COMPLETED/NO_SHOW.

- [ ] T031 [P] [US5] Viết test ViewModel/use case cho QR và nhập mã chữ, số dư còn phải thu theo `total`/`depositAmount`, tùy chọn xác nhận tiền đã thu và xử lý kết quả lỗi trong `app/src/test/java/com/example/booking_app/feature/owner/` (FR-012a–FR-012b; SC-004).
- [ ] T032 [P] [US5] Viết server transaction tests cho function được database README định danh `checkin-booking`: owner/venue ownership, CONFIRMED, chưa check-in, cửa sổ 30 phút trước start đến end muộn nhất, replay/sai mã bị từ chối và PAID chỉ khi owner xác nhận thu tiền. Database README nêu function nhưng repo hiện chưa có `supabase/functions/checkin-booking/` hoặc harness; xác nhận vị trí test, function ownership với database owner/Feature 060 và lệnh `deno test` (nếu được harness xác nhận) trước khi tạo test (FR-012a–FR-012b; SC-002, SC-004; dependency T003–T004 và contract check-in riêng, không phụ thuộc T005 booking cancellation).
- [ ] T033 [US5] Implement domain interface/request mapping cho `checkin-booking`, checked-in booking và số tiền còn thu trong `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-012a–FR-012b).
- [ ] T034 [US5] Implement client adapter gọi function đã có `checkin-booking`, đọc trạng thái mới có thẩm quyền sau kết quả timeout/không rõ trong `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-012a–FR-012b; SC-009).
- [ ] T035 [US5] Hoàn tất integration handoff cho `checkin-booking`: xác nhận với database owner/Feature 060 rằng function do họ sở hữu đáp ứng FR-012a/b và ghi contract/owner/path vào `specs/112-owner-operations/contracts/owner-operations-boundaries.md`; Feature 112 chỉ tích hợp qua adapter thuộc `feature/owner`, không sửa function hoặc `booking-finalize` của feature khác. Repo hiện chưa có source directory nên chưa xác nhận được function hiện hữu trong checkout (FR-012a–FR-012b; SC-002, SC-004; dependency T003–T004, T032 contract check-in, T033; độc lập T005 cancellation).
- [ ] T036 [US5] Implement ViewModel và Fragment/XML check-in booking với camera permission lúc cần, nhập mã thay thế, khách/sân/giờ và số tiền còn thu; giữ trạng thái xác nhận rõ ràng trong `app/src/main/java/com/example/booking_app/feature/owner/ui/` và `app/src/main/res/layout/` (FR-012a–FR-012b; Constitution V–VI).
- [ ] T037 [US5] Viết UI/accessibility/theme/size tests cho check-in booking, bao gồm camera bị từ chối, offline/retry, phone/`w600dp`, light/dark, và kiểm tra không hiển thị thao tác COMPLETED/NO_SHOW phía owner trong `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-012a; SC-004, SC-007–SC-009; Constitution IV–V).

## Phase 6: User Story 3 — Tạo voucher (P2)

**Goal**: Owner quản lý voucher venue theo schema chung, không chạm voucher hệ thống, counter hoặc redemption.

**Independent Test**: Tạo mã hợp lệ cho venue mình; kiểm tra mã trùng toàn hệ thống/validation/venue khác bị từ chối; voucher đã dùng không cho sửa usedCount/redemptions.

- [ ] T038 [P] [US3] Viết unit tests validation voucher trong `app/src/test/java/com/example/booking_app/feature/owner/` sau khi T006 và Feature 050 chốt contract/validation; bám schema README (code, type, value, maxDiscount, minOrderTotal, validFrom/validTo, usageLimit, perUserLimit, isActive), không tự thêm field/cách áp dụng (FR-013–FR-014; SC-005; dependency T006 phần voucher, Feature 050).
- [ ] T039 [P] [US3] Sau khi T006 chốt contract và function test harness, viết server-side tests cho mã duy nhất toàn hệ thống, venue ownership, từ chối `systemVouchers` và bảo vệ `usedCount`/redemptions; vị trí test/lệnh lấy từ contract. Rules suite ở `firestore-tests/test/owner-operations.test.ts` chạy `npm test` chỉ xác minh thao tác client do Rules kiểm soát, không uniqueness transaction phía server (FR-013–FR-014; SC-002, SC-005; dependency T003–T006 phần voucher).
- [ ] T040 [US3] Implement domain model/repository interface chỉ cho voucher venue theo schema được xác nhận; không coi `venueId` là document field trừ khi được database owner chốt và artifact schema có thẩm quyền đã cập nhật (không sửa artifact ngoài phạm vi) trong `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-013–FR-014; dependency T006 phần voucher).
- [ ] T041 [US3] Implement server-side voucher CRUD và uniqueness toàn hệ thống theo contract T006; nếu database owner xác nhận phải lưu field `venueId`, dừng triển khai cho đến khi chủ sở hữu cập nhật artifact schema có thẩm quyền. Không sửa Feature 120 `systemVouchers` hoặc luồng áp dụng Feature 050 (FR-013–FR-014; SC-005; dependency T003–T006 phần voucher).
- [ ] T042 [US3] Implement repository voucher adapter trong `app/src/main/java/com/example/booking_app/feature/owner/data/VoucherRepositoryImpl.kt` và ViewModel trong `app/src/main/java/com/example/booking_app/feature/owner/ui/VoucherViewModel.kt` theo contract/schema đã chốt, gồm conflict, ownership, offline/retry; phụ thuộc T006 phần voucher (FR-013–FR-014, FR-020).
- [ ] T043 [US3] Tạo Fragment/XML voucher và UI tests cho loading/content/empty/error, accessibility, light/dark, phone/`w600dp` trong `app/src/main/res/layout/`, `app/src/main/res/values/`, `app/src/main/res/values-night/`, `app/src/androidTest/java/com/example/booking_app/feature/owner/` (SC-005, SC-007–SC-009; Constitution IV–V).

## Phase 7: User Story 4 — Xem thống kê vận hành (P2)

**Goal**: Hiển thị doanh thu, occupancy và peak hours có thể truy ngược tới booking thực tế đúng venue/khoảng thời gian; báo rõ dữ liệu rỗng/cũ.

**Independent Test**: Đối chiếu fixture payment status và booking/slot với số liệu, gồm DEPOSITED, PAID, REFUNDED, AT_VENUE chưa thu, booking loại trừ, BLOCKED, NO_SHOW và kỳ không dữ liệu.

- [ ] T044 [P] [US4] Viết test công thức doanh thu dùng depositAmount/total/refundAmount và loại booking theo FR-016; cover AT_VENUE chưa PAID, trạng thái chưa thanh toán và tiền refund thiếu trong `app/src/test/java/com/example/booking_app/feature/owner/` (FR-016; SC-006).
- [ ] T045 [P] [US4] Viết test occupancy/peak cho slot 30 phút, booking CONFIRMED/COMPLETED/NO_SHOW, loại BLOCKED, slot trùng, ngày và bucket giờ địa phương trong `app/src/test/java/com/example/booking_app/feature/owner/` (FR-017–FR-018; SC-006; quy tắc phân bổ cần gate T007).
- [ ] T046 [US4] Chốt và ghi truy vấn/nguồn dữ liệu báo cáo cho booking nhiều court, `dailyStats` freshness/schema và giờ khả dụng trước khi chọn aggregate trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (FR-015–FR-019; gate T007).
- [ ] T047 [US4] Implement domain models/use case tính và trình bày doanh thu/occupancy/peak chỉ từ dữ liệu booking/slot thực tế đã xác định trong `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-015–FR-019; gate T007).
- [ ] T048 [US4] Implement truy vấn dữ liệu theo venue/kỳ và chỉ đọc dailyStats khi schema/nguồn đã được chốt; không tự ghi aggregate trong `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-015–FR-019; gate T007).
- [ ] T049 [US4] Implement ViewModel biểu thị rõ kỳ rỗng, dữ liệu cũ/chưa cập nhật, lỗi/offline và thao tác refresh trong `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-019–FR-020).
- [ ] T050 [US4] Tạo Fragment/XML biểu đồ và trạng thái thống kê, dùng resource/theme và hỗ trợ accessibility/light-dark/phone/`w600dp` trong `app/src/main/res/layout/`, `app/src/main/res/values/`, `app/src/main/res/values-night/`, `app/src/main/res/values-w600dp/` (SC-006–SC-009; Constitution V).
- [ ] T051 [US4] Viết đối chiếu kết quả thống kê với fixture và UI tests kỳ rỗng/dữ liệu cũ/retry cho `app/src/test/java/com/example/booking_app/feature/owner/` và `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-015–FR-020; SC-006, SC-009; Constitution IV–V).

## Phase 8: Polish và kiểm thử xuyên suốt

**Purpose**: Bao phủ các tiêu chí toàn feature và xác nhận các luồng không lấn quyền sở hữu package/nghiệp vụ.

- [ ] T052 [P] Rà soát unit test ViewModel/use case/repository đã tạo trong từng story và chạy lại `./gradlew testDebugUnitTest` từ repo root; chỉ ghi khoảng trống test trong `specs/112-owner-operations/tasks.md`, không tạo lại test trùng (Constitution I, IV).
- [ ] T053 [P] Rà soát layout/resources owner đã tạo trong từng story về bốn trạng thái, strings tiếng Việt, contentDescription/vùng chạm 48dp, light/dark và phone/`w600dp`; chạy `./gradlew lint` từ repo root và ghi khoảng trống trong `specs/112-owner-operations/tasks.md`, không tạo lại layout/test (FR-020; SC-009; Constitution V).
- [ ] T054 Rà soát coverage tổng thể của test đã tạo ở T009, T012–T013, T022–T023, T032, T039, T059; chạy Rules suite bằng `npm test` từ `firestore-tests/`. Chạy lại Edge Function tests bằng harness/lệnh đã xác nhận trong từng contract; nơi chưa có harness phải ghi gap, không tạo test mới hay suy đoán lệnh. Ghi kết quả allow/deny, replay, conflict, rollback và gap trong `specs/112-owner-operations/tasks.md` (FR-001–FR-014; SC-001–SC-005; Constitution II–IV).
- [ ] T055 Rà soát các task triển khai không thay file/package nghiệp vụ của Feature 110, 111, 120, booking, drop-in hoặc notification; ghi integration handoff/gate còn tồn trong `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (SC-002, ranh giới plan).
- [ ] T056 Cập nhật kịch bản thực thi và kết quả tiêu chí thời gian/độ tin cậy trong `specs/112-owner-operations/quickstart.md` sau khi các luồng đã triển khai; không chỉnh spec/plan hoặc tài liệu feature khác (SC-001–SC-009).
- [ ] T057 Rà soát bằng chứng quyết định database owner về `venueId` đã ghi qua T006 trước khi voucher story được đánh dấu hoàn tất; nếu chưa có xác nhận, giữ US3 blocked. Không thêm field vào schema/data model trong task này; ghi trạng thái gate tại `specs/112-owner-operations/contracts/owner-operations-boundaries.md` (I1; dependency T006).
- [ ] T058 Lập và chạy usability study với owner thử nghiệm; mỗi người tự thực hiện bốn thao tác SC-007: duyệt/từ chối booking, mở drop-in, check-in QR và tạo voucher. Tính tỷ lệ = số owner hoàn tất các thao tác mà không nhận hỗ trợ trực tiếp / tổng owner tham gia × 100%; chỉ tính hoàn thành khi đạt kết quả mong muốn theo spec mà không có moderator hint, hướng dẫn từng bước hoặc takeover. Ghi kết quả trong `specs/112-owner-operations/quickstart.md` và đối chiếu ngưỡng ≥90% của SC-007. Spec chưa quy định cỡ mẫu hay chi tiết điều kiện; xác nhận với product/feature owner trước khi study được coi là nghiệm thu chính thức, không tự đặt cỡ mẫu (SC-007).
- [ ] T059 Sau khi phần approve/reject của T005 được chốt và owner của Edge Function xác nhận test location/harness, viết transaction tests cho PENDING còn hạn/slot khớp thành CONFIRMED hoặc REJECTED; hết hạn, status cũ, slot lệch, mất quyền hoặc venue locked phải bị từ chối an toàn và không ghi một phần. Chạy bằng lệnh test do harness xác nhận; repo chưa có `supabase/functions/`/test harness nên không tự đặt file/function path (US1, FR-003–FR-006; SC-001–SC-002; dependency T003–T005 phần approve/reject).

## Dependencies & Execution Order

### Phase dependencies

- Phase 1 có thể làm ngay và không phụ thuộc implementation.
- Phase 2: T003–T008 chỉ chặn phần nghiệp vụ liên quan; T009–T010 chuẩn bị test nền. T005 chỉ gồm booking; T006 gồm các hợp đồng riêng cho drop-in, check-in registration và voucher.
- US1 approve/reject/cancel phụ thuộc T003–T005 phần booking; T013 chỉ chờ phần cancel booking của T005.
- US2 phụ thuộc T003–T004, T006 phần drop-in/check-in registration và T008/Feature 080/081.
- US5 phụ thuộc T003–T004 và contract `checkin-booking` riêng do database owner/Feature 060 xác nhận (T032/T035); không phụ thuộc T005 cancellation.
- US3 phụ thuộc T003–T004, T006 phần voucher (bao gồm xác nhận nghĩa `venueId`) và Feature 050; T057 là audit gate cuối, không thay thế quyết định T006. US4 phụ thuộc T007/database owner.
- Phase 8 phụ thuộc các user story được chọn phát hành; chỉ rà soát và chạy lại suite đã tạo, không sinh test trùng. T058 đo SC-007; T057 xác minh gate voucher.

### User story dependency graph

```text
T003 + T004 + T005
  ├── US1 (booking/lịch)
  ├── T008 + Feature 080/081 ── US2 (drop-in)
  ├── Feature 060 + checkin-booking ── US5 (booking check-in)
  └── T006 + Feature 050 ── US3 (voucher)
T007 + database owner ── US4 (statistics)
All selected stories ── Phase 8
```

Không có dependency cho phép Feature 112 tự triển khai thay nghiệp vụ Feature 110/111/120/040/041/050/060/080/081/100. Feature 110/120 cung cấp trạng thái owner/venue; Feature 111 cung cấp venue/court/priceRules/slot và cần chốt ranh giới voucher; booking cung cấp trạng thái, thanh toán, refund, QR và `checkin-booking`; Feature 080/081 cung cấp đăng ký/queue. Các tích hợp chỉ dùng hợp đồng/đầu ra đã được chủ feature chốt.

### Parallel opportunities

- Sau gate auth/ownership, US1 booking/lịch và phần read-only của US2 có thể tách việc domain/data/UI theo file riêng.
- Trong US1, T011 và Rules test T012 độc lập; T013 chỉ chờ contract booking cancellation ở T005; T016 chờ contract booking tương ứng.
- Trong US2, T021 độc lập; T022/T023 chờ từng contract/harness tương ứng T006; UI XML chờ field/status schema chốt.
- US3 và US4 có thể chạy song song sau gate riêng; T057 xác minh gate voucher đã được đóng trước nghiệm thu cuối.
- Trong mỗi story, tests và UI/domain ở file khác nhau có thể chạy song song khi interface/schema của story đã thống nhất; task phụ thuộc ghi rõ trong mô tả/gate.

## MVP / Implementation Strategy

1. Hoàn tất setup và gate booking T003–T005.
2. Làm MVP P1 gồm US1 danh sách/quyết định/lịch; kiểm thử authorization, slot integrity và UI states.
3. Làm US5 check-in booking qua `checkin-booking`; phụ thuộc contract với Feature 060/database owner.
4. Làm US2 sau khi contract/schema/queue/refund Feature 080/081 được xác nhận qua T006/T008.
5. Làm US3 chỉ sau T006 phần voucher; T057 xác minh gate đã đóng. Làm US4 sau T007/database owner.
6. Hoàn tất Phase 8; các chỉ số SC được xác nhận bằng test/kịch bản local, không dùng backend thật.

## Coverage Matrix

| Spec requirement | Tasks |
|---|---|
| US1, FR-001–FR-007, FR-004a | T003–T004, T009, T011–T020 |
| US2, FR-008–FR-012, FR-010a | T006, T008–T010, T021–T030 |
| US3, FR-013–FR-014 | T006, T038–T043, T057 |
| US4, FR-015–FR-019 | T007, T044–T051 |
| US5, FR-012a–FR-012b | T031–T037 |
| FR-020 and cross-cutting UI/error/offline/retry | T017–T019, T028–T030, T036–T037, T042–T043, T049–T053 |
| SC-001–SC-009 | T009, T011–T013, T020–T023, T030–T039, T044–T056, T058 |

## Format check

T001–T059 use checkbox + unique IDs; `[P]` appears only on parallelizable tasks; `[USn]` labels appear on user-story tasks; test tasks identify existing harness/command where available and explicitly gate missing test harness/path. Tests are included because the user explicitly requested Constitution-compliant coverage.
