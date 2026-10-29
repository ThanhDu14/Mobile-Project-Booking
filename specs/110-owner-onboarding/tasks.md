---
description: "Danh sách task triển khai Feature 110 — Đăng ký làm chủ sân"
---

# Tasks: Đăng ký làm chủ sân (Feature 110)

**Input**: `specs/110-owner-onboarding/spec.md`, `plan.md`, `data-model.md`, `contracts/owner-onboarding-boundaries.md`, `research.md`, `quickstart.md`, và `checklists/requirements.md`.

**Organization**: Theo User Story và ưu tiên trong spec. Mỗi task gắn FR/Acceptance Scenario (AS) để truy vết; test được đưa vào vì Constitution bắt buộc kiểm thử logic, Security Rules và Storage RLS.

**Repository hiện trạng (2026-10-26)**: Đã có package `feature/owner/{ui,data,domain}`, status mapper/UI, form/review shell, repository/API và các unit test; Supabase có shared auth/Firestore, status/submit, district catalog và owner document download functions. Submit vẫn fail-closed vì chưa có upload verifier; chưa có sign-upload, Storage bucket/policy local hoặc Android UI/integration tests đầy đủ. District picker hiện nối với catalog function; map marker, file upload/download client flow và tích hợp Storage vẫn còn mở. Đường dẫn trong task mô tả vị trí đích; checkbox chỉ được đánh dấu sau khi acceptance criteria và kiểm chứng tương ứng hoàn tất.

**Ghi nhận kiểm chứng implementation (2026-10-26)**: Android `testDebugUnitTest` hiện chạy thành công với 214 test, 0 failure/error/skip; `assembleDebug` chạy thành công. Đóng T019/T022/T024/T028 theo code và unit tests hiện có. T002/T003/T015/T016/T026 vẫn mở vì contract chưa chốt các quyết định storage/transaction cần thiết và handler Deno chưa được chạy; submit vẫn fail-closed nếu không xác minh được object Storage thực tế. Firestore Emulator chưa khởi động nên T007/T008/T029 chưa đủ bằng chứng runtime. T009/T010/T013 bị chặn bởi path/policy upload ảnh draft chưa được duyệt và chưa có verifier; T012 còn thiếu provider/marker bản đồ được duyệt; T017/T018/T023/T025/T029/T032 chưa đủ upload/UI/integration coverage. T033/T034 có code nối signed-download và unit/source tests nhưng còn mở đến khi handler/Storage local test và URL expiry behavior được xác nhận. Deno, Firebase CLI/Emulator và Supabase CLI chưa sẵn sàng trong môi trường hiện tại. Không commit hoặc push.

## Phase 1: Setup và xác nhận ranh giới

**Purpose**: Chốt các hợp đồng và tiền đề kỹ thuật trước những phần triển khai bị phụ thuộc; không tự thêm schema/API hay đổi nghiệp vụ.

- [X] T001 [P] Đưa ra quyết định để nhóm xác nhận và ghi nhận trong boundary contract: cách truy vấn application hiện hành khi có lịch sử APPROVED sau revoke, resubmit từ REJECTED (cập nhật document hay document mới), lưu lịch sử, và phân biệt retry cùng lần nộp với lần nộp mới; kiểm tra tính nhất quán với ID tự sinh trong README và `users.ownerStatus`; không tự chọn field, ID, index hoặc phương án schema — `specs/110-owner-onboarding/contracts/owner-onboarding-boundaries.md` (FR-006, FR-018; US1-AS5, US3-AS2; SC-006)
- [ ] T002 [P] Định nghĩa đầu ra contract để tích hợp app→Supabase sau khi nhóm xác nhận lựa chọn: cơ chế xác thực Firebase Auth/Supabase và yêu cầu token/refresh; cách app gọi thao tác Edge Function/Storage; boundary `sign-upload`/`sign-download`, input/output, lỗi hết hạn và retry; quyền applicant tải giấy tờ vào `owner-docs` và ảnh draft vào bucket `venue-photos` hiện có; xác minh danh tính/quyền phía server và không cho applicant xem hồ sơ/tệp người khác. Ghi rõ các quyết định chưa được nhóm chốt và không tự chọn protocol, claims, SDK, token format hay Security Rules — `specs/110-owner-onboarding/contracts/owner-onboarding-boundaries.md` (FR-003, FR-012, FR-013; US1-AS2; SC-005, SC-007)
- [ ] T003 Chốt boundary tin cậy submit/resubmit, authoritative read trước/sau timeout, idempotency cho cùng lần nộp và conflict khi Admin/thiết bị khác đổi trạng thái; xác định bằng cơ chế/schema đã được nhóm chấp thuận, không tự đặt endpoint, request ID/version, field hoặc thuật toán mới — `specs/110-owner-onboarding/contracts/owner-onboarding-boundaries.md` (FR-005, FR-006, FR-017, FR-019; US1-AS2, US1-AS5, US3-AS3; SC-002, SC-006)
- [ ] T004 Chốt người chịu trách nhiệm và cơ chế dọn object upload dở, xóa application/`owner-docs` khi xóa tài khoản, và lưu bản nháp có thể phục hồi; giữ nguyên giới hạn hiện được spec xác nhận, không tự đặt thời hạn retention — `specs/110-owner-onboarding/contracts/owner-onboarding-boundaries.md` (FR-016; US1-AS2, US1-AS5, US3-AS2)

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Hoàn thiện nền tảng app và server security dùng chung sau khi các quyết định tương ứng ở Phase 1 được chốt.

- [X] T005 Thiết lập package `core/` và `feature/owner/{ui,data,domain}` theo MVVM, ViewBinding, Navigation, Hilt, Coroutines/Flow và StateFlow; trước khi thêm dependency, lập danh sách từng artifact/version cần cho kiến trúc đã được chấp thuận, kiểm tra dependency hiện có và ghi lý do cần dùng theo Constitution; không thêm Supabase Android SDK hay thư viện chưa được chọn. Plan hiện ghi các dependency kiến trúc còn thiếu nhưng chưa có lý do theo từng dependency; task này bị chặn ở bước thêm dependency cho tới khi kiến trúc/chủ trương được nhóm duyệt và ghi nhận đúng nơi theo governance — `app/src/main/java/com/example/booking_app/core/`, `app/src/main/java/com/example/booking_app/feature/owner/`, `gradle/libs.versions.toml` (plan technical structure; Constitution VII)
- [X] T006 Tạo interface repository/use case và data mapping chỉ cho schema hiện có: `users/{uid}.role`, `users/{uid}.ownerStatus`, `ownerApplications/{id}` (`uid`, `venueDraft`, `representativeName`, `representativePhone`, `documentPaths`, `status`, `rejectReason`, `reviewedBy`, `reviewedAt`, `submittedAt`); giữ trường 🔒 read-only ở client — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-005, FR-007, FR-009, FR-010, FR-014)
- [ ] T007 Bổ sung Firestore Rules cho quyền đọc hồ sơ của chính người nộp và chặn đọc người khác; cho phép người nộp tạo/nộp lại chỉ theo trạng thái cho phép đã được hợp đồng xác định; chặn client ghi `role`, `ownerStatus`, `status`, `rejectReason`, `reviewedBy`, `reviewedAt` và đường truy cập owner khi chưa APPROVED — `firestore.rules` (FR-008, FR-012, FR-013, FR-014, FR-019)
- [ ] T008 [P] Viết kiểm thử Firebase Emulator allow/deny cho ownerApplications và trường/trạng thái được bảo vệ, gồm người khác, PENDING, APPROVED, REJECTED, request stale và gọi trực tiếp — `firestore-tests/test/owner-applications.test.ts` (FR-008, FR-013, FR-014, FR-017, FR-019; US1-AS3, US3-AS3)
- [ ] T009 Sau khi T002 được nhóm xác nhận, triển khai policy Supabase local cho `owner-docs` private và upload ảnh draft vào bucket `venue-photos` đã được README quy định; giới hạn quyền applicant vào đúng UID/application theo contract, giữ path giấy tờ `{uid}/{applicationId}/{filename}`, MIME JPEG/PNG/WebP, tối đa 2 MB mỗi giấy tờ, signed URL hết hạn 5 phút; không tự suy diễn policy server từ JWT nếu nguồn quyền chưa khả dụng — `supabase/migrations/`, `supabase/functions/`, `firestore-tests/` (FR-003, FR-012, FR-013; SC-005, SC-007)
- [ ] T010 [P] Viết kiểm thử Supabase local cho signed download và upload: applicant chỉ đọc giấy tờ của application mình; admin theo boundary Feature 120; user khác bị từ chối kể cả biết path; URL hết hạn bị từ chối và có thể xin lại theo cơ chế đã chốt; sai MIME/quá 2 MB bị từ chối; kiểm tra riêng quyền applicant cho `venue-photos` theo contract được duyệt — `firestore-tests/test/owner-docs-storage.test.ts` (FR-003, FR-012, FR-013; US1-AS2; SC-005)

## Phase 3: User Story 1 — Nộp hồ sơ chủ sân (P1, MVP)

**Goal**: Người chơi `PLAYER` chưa nộp có thể điền, xác thực, tải tệp, xem lại và nộp một hồ sơ; vẫn là PLAYER khi chờ.

**Independent Test**: Với player `ownerStatus=NONE`, gửi hồ sơ hợp lệ cùng ít nhất một ảnh cơ sở và một giấy tờ; server xác nhận đúng một hồ sơ `PENDING`, user vẫn `PLAYER`; không thể quản lý venue. Thử dữ liệu sai, upload lỗi, request trực tiếp và timeout.

- [X] T011 [P] [US1] Tạo model/validator thuần Kotlin cho tên cơ sở, địa chỉ, district đã chọn từ danh mục, tọa độ, phone, số sân nguyên dương, giờ mở/đóng, người đại diện và phone; bắt buộc trim không rỗng, tọa độ hợp lệ, ảnh cơ sở và giấy tờ đều có; không tự đặt giới hạn số sân, chuẩn hóa phone hoặc quy tắc giờ qua đêm — `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-002, FR-004; US1-AS4; SC-001, SC-007)
- [ ] T012 [P] [US1] Tạo màn hình biểu mẫu XML theo Material 3 và `Theme.Material3.DayNight`, hỗ trợ light/dark, điện thoại và `w600dp`; dùng resource tiếng Việt/accessibility; hiển thị lỗi tại trường. Đảm bảo chọn marker vị trí thủ công không cần quyền runtime vị trí; chỉ lấy vị trí hiện tại khi user cấp quyền, không chặn submit khi quyền bị từ chối — `app/src/main/res/layout/fragment_owner_application_form.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/navigation/nav_graph.xml`, `app/src/main/res/layout-w600dp/` (FR-001, FR-002, FR-004; US1-AS1, US1-AS4; SC-001)
- [ ] T013 [US1] Tạo bộ chọn/tải ảnh cơ sở và giấy tờ với kiểm tra MIME/size, tiến trình từng tệp, retry riêng tệp lỗi và chỉ gắn path sau upload thành công; route upload theo contract T002/T009 đã được duyệt, lưu path chứ không lưu URL; giữ biểu mẫu khi lỗi có thể khôi phục — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/` (FR-003, FR-016; US1-AS2; SC-007)
- [ ] T014 [US1] Tạo màn hình xem lại đầy đủ nội dung và tài liệu trước khi gửi; yêu cầu sửa lỗi ở đúng trường/tệp và không gửi khi validation chưa đạt — `app/src/main/res/layout/fragment_owner_application_review.xml`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-004; US1-AS2, US1-AS4)
- [ ] T015 [US1] Sau khi T001 và T003 được nhóm chốt, thực hiện submit qua boundary đã duyệt: authoritative read trước ghi, chỉ tạo mới khi `role=PLAYER` và `ownerStatus=NONE`, gửi field applicant-owned; đảm bảo server/Rules đặt `PENDING` và client không ghi field server-only. Bị chặn nếu chưa có cơ chế xác định document hiện hành/ghi an toàn đã được chấp thuận — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/`, `supabase/functions/` (FR-005, FR-008, FR-013, FR-014, FR-019; US1-AS2, US1-AS3; SC-002, SC-004)
- [ ] T016 [US1] Sau T001/T003, đối chiếu authoritative state sau timeout/retry và chống submit trùng cho cùng lần nộp bằng cơ chế nhóm đã chốt; khóa nút khi xử lý, không báo thành công trước xác nhận server, giữ dữ liệu khi conflict. Không thực hiện bằng cách tự thêm request ID/version/field — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-006, FR-016, FR-017; US1-AS5; SC-006, SC-007)
- [ ] T017 [P] [US1] Viết unit test validator và ViewModel cho trường hợp hợp lệ/thiếu/sai, lỗi theo trường, tiến trình upload, retry, không mất dữ liệu, khóa submit và kết quả timeout — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-002–FR-006, FR-015–FR-017; US1-AS1, US1-AS2, US1-AS4, US1-AS5; SC-006, SC-007)
- [ ] T018 [US1] Viết UI test luồng mở form → điền → upload → xem lại → submit và xác nhận pending; kiểm tra người pending không vào được quản lý venue; kiểm tra màn form ở Material 3 light/dark, điện thoại/`w600dp`, và trạng thái loading/error cùng hành vi ghim thủ công khi từ chối quyền vị trí — `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-001, FR-005, FR-008; US1-AS1–AS3; SC-001, SC-002, SC-004)

## Phase 4: User Story 2 — Theo dõi kết quả duyệt (P1)

**Goal**: Người nộp xem trạng thái hiện hành từ server, thời điểm gửi và hành động tương ứng; quyết định admin và quyền vẫn thuộc Feature 120.

**Independent Test**: Tải lần lượt NONE/PENDING/APPROVED/REJECTED từ server và xác nhận nhãn, role, lý do, thời điểm và lối tiếp theo đúng; lỗi mạng không cấp quyền từ cache.

- [X] T019 [P] [US2] Sau quyết định T001, ánh xạ `ownerStatus` IN HOA sang nhãn/trạng thái UI; lấy `submittedAt`/`rejectReason` từ application hiện hành đã xác định, không suy ra từ hồ sơ lịch sử sai. Task mapper có thể phát triển với fake, nhưng tích hợp query thật bị chặn bởi T001 — `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-007, FR-009, FR-010, FR-018; US2-AS1–AS4; SC-002, SC-003)
- [ ] T020 [US2] Tạo màn hình trạng thái theo Material 3/DayNight, light/dark, điện thoại và `w600dp`, có loading/content/empty/error/retry và refresh server khi mở/tiếp tục; NONE cho phép bắt đầu, PENDING hiển thị submittedAt, APPROVED đã duyệt, REJECTED lý do và sửa/nộp lại — `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/fragment_owner_application_status.xml`, `app/src/main/res/layout-w600dp/`, `app/src/main/res/values/strings.xml` (FR-007, FR-015; US2-AS1–AS4; SC-002, SC-003)
- [ ] T021 [US2] Đảm bảo PENDING/REJECTED giữ PLAYER, APPROVED phản ánh quyền server từ Feature 120 và liên kết chức năng 111/112; khi `ownerStatus=NONE` sau revoke, cho phép bắt đầu luồng mới nhưng chỉ sau khi T001 xác định cách phân biệt application lịch sử và hiện hành; không triển khai nghiệp vụ quản lý venue — `app/src/main/java/com/example/booking_app/feature/owner/`, `app/src/main/res/navigation/nav_graph.xml` (FR-008, FR-009, FR-018; US2-AS1–AS4; SC-002, SC-004)
- [X] T022 [P] [US2] Viết unit test ánh xạ 4 ownerStatus, role tương ứng, lý do/thời điểm, trạng thái cũ khi offline và không mở quyền owner từ cache — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-007–FR-010, FR-015; US2-AS1–AS4; SC-002, SC-003)
- [ ] T023 [US2] Viết UI test cho NONE/PENDING/APPROVED/REJECTED, refresh, retry, permission error không lộ dữ liệu, và đủ loading/content/empty/error; kiểm tra Material 3 light/dark, điện thoại và `w600dp` — `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-007–FR-015; US2-AS1–AS4; SC-002, SC-003, SC-004)
- [ ] T033 [US2] Tạo luồng người nộp xem/tải giấy tờ của application thuộc chính mình: chỉ yêu cầu signed URL qua cơ chế `sign-download` đã được README xác nhận; không lưu URL công khai, không hiển thị giấy tờ cho user khác; khi URL hết hạn hoặc tải lỗi, báo lỗi và xin signed URL mới/thử tải lại theo contract, không tiết lộ path hoặc dữ liệu hồ sơ khác — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-012, FR-013, FR-015; US2-AS1–AS3; SC-005, SC-007)
- [ ] T034 [P] [US2] Viết test riêng cho applicant signed-download: hồ sơ/tệp của mình mở được, hồ sơ/tệp UID khác bị chặn, URL hết hạn không được dùng lại và retry lấy URL mới; kiểm tra permission/network error không lộ document path — `app/src/test/java/com/example/booking_app/feature/owner/`, `app/src/androidTest/java/com/example/booking_app/feature/owner/`, `firestore-tests/test/owner-docs-storage.test.ts` (FR-012, FR-013, FR-015; US2-AS1–AS3; SC-005, SC-007)

## Phase 5: User Story 3 — Sửa và nộp lại hồ sơ bị từ chối (P2)

**Goal**: Người nộp REJECTED xem lý do/dữ liệu cũ, sửa phần applicant-owned và nộp lại; quay về PENDING, vẫn PLAYER.

**Independent Test**: Với hồ sơ REJECTED có lý do, nạp dữ liệu, chỉnh sửa hợp lệ, submit lại; server xác nhận trạng thái hiện hành PENDING/PLAYER. PENDING không được sửa/rút; kiểm tra xung đột khi hồ sơ đổi trong lúc mở.

- [X] T024 [US3] Sau khi T001 xác định application hiện hành, nạp đúng hồ sơ REJECTED và `rejectReason` cùng dữ liệu `venueDraft`, người đại diện, path tệp; không đưa trường review/server-only vào form sửa — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-010, FR-018; US3-AS1; SC-003)
- [ ] T025 [US3] Dùng lại form/review cho sửa hồ sơ rejected; cho thay nội dung/tệp, hiển thị lý do hiện hành và giữ dữ liệu hợp lệ khi validation/upload lỗi — `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/fragment_owner_application_form.xml`, `app/src/main/res/layout/fragment_owner_application_review.xml` (FR-004, FR-010, FR-016; US3-AS1)
- [ ] T026 [US3] Chỉ sau khi T001/T003 được nhóm chốt, resubmit khi server xác nhận REJECTED; áp dụng đúng phương án document/history đã duyệt, đưa trạng thái về PENDING, giữ PLAYER; pending không thể sửa/rút và không tạo application cạnh tranh — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `supabase/functions/`, `firestore.rules` (FR-006, FR-011, FR-013, FR-014, FR-017, FR-019; US3-AS2, US3-AS3; SC-003, SC-004, SC-006)
- [ ] T027 [US3] Sau T003, xử lý Admin/thiết bị khác đổi trạng thái khi form mở: authoritative refresh, từ chối stale write, tải trạng thái hiện hành và giữ draft có thể phục hồi; không tự triển khai version field — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-017, FR-019; US3-AS3; SC-006, SC-007)
- [X] T028 [P] [US3] Viết unit test resubmit REJECTED, trạng thái không hợp lệ, lý do, giữ PLAYER, response conflict và không mất draft — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-010, FR-011, FR-017, FR-019; US3-AS1–AS3; SC-003, SC-006, SC-007)
- [ ] T029 [US3] Sau T001/T003, viết UI/emulator integration test sửa+nộp lại và request trái phép khi PENDING; xác nhận application hiện hành theo quyết định nhóm, không phát sinh hồ sơ trùng; kiểm tra UI rejected/loading/error ở light/dark và phone/`w600dp` — `app/src/androidTest/java/com/example/booking_app/feature/owner/`, `firestore-tests/test/owner-applications.test.ts` (FR-006, FR-011, FR-013, FR-019; US3-AS2–AS3; SC-003, SC-004, SC-006)

## Phase 6: Polish và cross-cutting

- [X] T030 [P] Đối chiếu luồng account/profile: 011 sở hữu entry point và summary tài khoản; 110 cung cấp form/status và không nhân đôi login/profile — `app/src/main/java/com/example/booking_app/feature/owner/`, `app/src/main/res/navigation/nav_graph.xml` (FR-001, FR-007)
- [X] T031 [P] Đối chiếu quyền handoff: 120 sở hữu approve/reject/revoke, role/claim, rejectReason và tạo venue từ `venueDraft`; 111/112 chỉ mở sau APPROVED và sở hữu quản lý/vận hành; không thêm nghiệp vụ của các feature đó — `app/src/main/java/com/example/booking_app/feature/owner/` (FR-009–FR-011)
- [ ] T032 Chạy và ghi kết quả kiểm chứng theo Feature 110 quickstart trên Firebase Emulator và Supabase local, gồm private docs signed-download, submit/retry/resubmit, trạng thái stale, quyền và luồng UI; không dùng project production — `specs/110-owner-onboarding/quickstart.md`, `firestore-tests/`, `app/src/test/`, `app/src/androidTest/` (FR-003, FR-006, FR-012–FR-017, FR-019; SC-002–SC-007)
- [ ] T035 Đánh giá usability cho SC-001 bằng phiên thử có người tham gia: tối thiểu 10 người thuộc nhóm người dùng mục tiêu, không hướng dẫn trực tiếp; bắt đầu tính giờ khi mở luồng đăng ký và dừng khi app xác nhận hồ sơ hợp lệ đã gửi; ghi số người hoàn tất trong ≤5 phút, lỗi gặp phải và phiên bản UI. Đạt khi ít nhất 90% hoàn tất; đây là usability evaluation thủ công, không phải automated test — `specs/110-owner-onboarding/quickstart.md` (FR-001–FR-004; US1; SC-001)

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1**: T001–T004 ghi nhận/chờ xác nhận các quyết định; chỉ T001/T003 chặn submit/status query/resubmit liên quan, T002 chặn tích hợp thật với Supabase/upload, T004 chỉ chặn cleanup lifecycle.
- **Phase 2**: T005 có thể thiết lập cấu trúc với dependency đã có; thêm dependency mới bị chặn đến khi kiến trúc được duyệt và có rationale theo Constitution. T006–T008 chuẩn bị data boundary/Rules; T009–T010 bắt buộc đợi đầu ra đã duyệt của T002.
- **US1**: T011/T012/T014/T017 có thể làm sau nền tảng với fake; T013 cần T002/T009; T015 cần T001–T003; T016 cần T001/T003. T018 tích hợp end-to-end cần submit backend sẵn.
- **US2**: status mapper/UI có thể phát triển bằng fake sau T005; authoritative application lookup T019 cần T001; T021 revoke/reapply cần T001; T033–T034 applicant document download cần T002/T009 và `sign-download` authorization đã chốt.
- **US3**: T024 cần T001 để nạp đúng rejected application; T025 UI có thể dựng trước nhưng dữ liệu thật đợi T001; T026 cần T001/T003/T009; T027 cần T003; T028 có thể viết fake trước, còn test integration T029 đợi T001/T003. US3 phụ thuộc form/upload của US1.
- **Polish**: T030–T032 sau các story liên quan; T031 chỉ xác nhận ranh giới với 120/111/112. T035 usability evaluation cần bản US1 đủ ổn định nhưng không phụ thuộc backend admin.

### Dependency graph

```text
T001 ─┬─> T003 ──> T015/T016 ──> US1 ──> T024/T026/T027/T029 ──> US3
      ├──────────> T019/T021 ──> authoritative US2 application state
      └───────────────────────> T024/T026 (current rejected application)
T002 ──> T009/T010 ──> T013/T015 ──> US1
  └────> T033/T034 (applicant signed-download) ──> US2 docs
T003 ──> T016/T026/T027/T029 (idempotency, timeout, conflict)
T004 ──> cleanup lifecycle only (not a submit MVP blocker)
T005 ──> T006/T011/T012/T017/T019/T020; dependency additions await architecture approval
T007 ──> T008 ──> secure application reads/writes in US1/US2/US3
US1 + T120 decision outcome ──> US2 approved/rejected state refresh
US1 stable ──> T035 (manual usability evaluation)
```

### Cross-feature dependencies

- **010 account auth**: authenticated Firebase UID/session; không sửa luồng xác thực.
- **011 account profile**: entry point và summary `users.ownerStatus`; task 110 làm form, trạng thái chi tiết, submit/resubmit.
- **120 admin**: nguồn duy nhất cho approve/reject/revoke, `rejectReason`, đổi role/status/claim, và tạo venue từ draft. Admin UI không phải điều kiện để hoàn thành client shell, nhưng dữ liệu quyết định cần có để kiểm chứng trạng thái end-to-end.
- **111/112**: tiêu thụ quyền owner đã duyệt; mọi thao tác venue/court/operations ngoài 110.
- **100 notifications**: phát thông báo duyệt/từ chối thuộc luồng server hiện có; status trong app vẫn phải đọc được khi không có notification.

## Unresolved decisions and scoped blockers

1. **Application identity/history** (T001): nhóm cần xác nhận cách query application hiện hành khi user bị revoke nhưng application APPROVED cũ được giữ; resubmit cập nhật hồ sơ rejected hay tạo hồ sơ mới; ranh giới giữa retry cùng lần nộp và lần nộp mới. Chặn authoritative query/submit/resubmit ở T003, T015–T016, T019, T021, T024, T026–T029; không chặn form/validator/UI fake. Không tự thêm con trỏ/index/ID.
2. **App–Supabase authentication and operation contract** (T002): nhóm cần chọn cơ chế xác thực, token handling, cách app gọi Supabase/Edge Functions và contract đầu vào/đầu ra/error. Chặn T009–T010, T013/T015 và T033–T034 dùng backend thật; không chặn UI/validator. T002 phải tạo boundary triển khai rõ, nhưng không tự chọn SDK/REST/token/claims.
3. **Applicant upload authorization** (T002): README xác nhận `owner-docs`, `venue-photos`, `sign-upload`/`sign-download`, nhưng quyền upload venue-photos hiện mô tả owner đã duyệt. Nhóm cần xác nhận quyền upload applicant giới hạn UID/application và cơ chế enforcement; chặn upload thật T009/T013 và test policy T010. Không nới policy owner thường hoặc tự suy diễn quyền từ JWT.
4. **Submit idempotency/concurrency** (T003): chưa có request ID/version hay contract ghi có điều kiện cho ownerApplications trong schema hiện tại. Nhóm cần chấp thuận cách thực hiện bằng cơ chế/schema có căn cứ; chặn T015–T016/T026–T029 end-to-end. Không tự mượn requestId booking hoặc thêm field mới.
5. **Applicant signed download**: README xác nhận `sign-download` và signed URL hết hạn sau 5 phút. T033–T034 còn cần T002/T009 xác định authorization thực tế và cách xin lại URL; người nộp phải chỉ mở tài liệu application của mình. Không cho phép public URL.
6. **Dependency approval** (T005): Constitution yêu cầu rationale trong plan cho dependency/library/module mới; plan hiện chỉ liệt kê dependency còn thiếu mà chưa giải thích riêng từng dependency, và Supabase Android mechanism chưa chọn. Cấu trúc package có thể tiến hành, nhưng thêm thư viện bị chặn tới khi kiến trúc và rationale được nhóm chấp thuận. Trong yêu cầu hiện tại không sửa plan.
7. **Draft/object cleanup and account deletion lifecycle** (T004): nhóm cần xác nhận owner cleanup/retry/retention theo contract xóa tài khoản. Chỉ chặn cleanup lifecycle, không chặn submit MVP đã tải tệp thành công.
8. **Validation details**: spec để ngỏ số sân tối đa, giờ qua nửa đêm/ngày nghỉ, khớp địa chỉ-ghim, chuẩn hóa/độ dài phone; tasks chỉ áp dụng điều kiện đã chốt. Không tự thêm policy.
9. **Location permission**: Constitution yêu cầu app vẫn hoạt động khi user từ chối quyền vị trí. T012/T018 yêu cầu marker thủ công không phụ thuộc permission; quyền này chỉ cần cho current-location tiện ích. Nếu map SDK đang được dự án chọn không cho thao tác marker thủ công không cần permission, cần nhóm xác nhận cách tương thích trước khi khóa thiết kế, không được làm permission bắt buộc để nộp.

## Parallel opportunities

- Sau T005 và khi chưa cần thêm dependency: T011 validator, T012 form shell, T017 unit test có thể làm song song; T019 status mapper bắt đầu bằng fake, query thật đợi T001.
- Sau khi T002 được nhóm chốt: T009 policy/function boundary và T010 test có thể chia việc; T013 upload client, T033 download client bắt đầu sau boundary ổn định.
- T007/T008 và T009/T010 là nhánh bảo mật khác nhau; không dùng chung file khi làm song song. T034 test download có thể viết mock trước, policy integration đợi T002.
- US2 UI/status và US1 form có thể phát triển độc lập sau nền tảng, nhưng tích hợp dữ liệu authoritative/status yêu cầu T001–T003 và kết quả từ Feature 120.
- US3 phụ thuộc submit/resubmit và mô hình hiện hành, không độc lập với US1.

## Implementation strategy

### MVP

Hoàn tất các quyết định T001–T003 cần cho submit, Phase 2, rồi US1 và phần trạng thái PENDING/NONE của US2. MVP phải có server authorization, private evidence boundary, validation và retry/idempotency; không thể coi chỉ hoàn thành UI là MVP an toàn. APPROVED/REJECTED end-to-end phụ thuộc quyết định Feature 120.

### Incremental delivery

1. Foundation + US1 nộp hồ sơ mới và xác nhận pending.
2. US2 hiển thị refresh authoritative cho đủ bốn trạng thái khi có dữ liệu Feature 120.
3. US3 sửa/nộp lại hồ sơ rejected và applicant document access sau khi identity/history và Supabase contract được chốt.
4. Hoàn thiện cleanup lifecycle T004, usability evaluation SC-001 và kiểm chứng nhanh toàn bộ trên emulator/local.

## Tiến độ tiếp tục (2026-10-26)

- Đã nối Android status → `owner-document-download`: status response trả `documentPaths` của application hiện hành; app xin URL mới qua Edge Function mỗi lần mở và không lưu URL. Handler xác minh Firebase UID, application ownership và path thuộc `documentPaths`; bổ sung test code cho owner khác, path không được khai báo và cấp URL mới khi retry.
- Thêm migration idempotent để bootstrap bucket `owner-docs` private với MIME/size giới hạn theo README; không sửa cấu hình bucket đã tồn tại và không thêm policy client, nên direct Storage access vẫn bị deny mặc định.
- Status ViewModel hủy refresh cũ trước request mới để kết quả chậm không ghi đè trạng thái mới; thêm test hồi quy cho refresh cạnh tranh và lỗi offline sau khi từng có content. Timestamp status hiển thị theo `Asia/Ho_Chi_Minh`, phần lý do từ chối chỉ hiện ở trạng thái REJECTED.
- Mở rộng Firestore Rules test code cho đọc application của chính mình ở PENDING/APPROVED/REJECTED, request stale và direct write từ player/admin. Rules vẫn deny mọi client write; submit/resubmit chỉ qua transaction Edge Function.
- Status ViewModel hủy refresh cũ trước request mới để response chậm không ghi đè status mới; bổ sung test cho refresh cạnh tranh và lỗi offline sau content. Timestamp status hiển thị giờ `Asia/Ho_Chi_Minh`; lý do từ chối chỉ hiện khi status là REJECTED.
- Contract và quickstart ghi rõ auth, signed-download, submit/read-back, typed conflict cùng giới hạn upload hiện tại. T002/T003 chưa được đánh dấu vì integration test chưa chạy; T002 còn phần sign-upload/ảnh draft chưa có path đã duyệt.
- T012 hỗ trợ nhập tọa độ thủ công, không xin quyền vị trí; marker bản đồ vẫn chờ provider được nhóm chốt. Không đổi checkbox trong lượt này: Android build cần tải Gradle distribution; Firestore tests dừng vì Firestore Emulator host/port không có; Deno không cài đặt. T009/T010/T013 vẫn fail-closed do path ảnh venue trước duyệt chưa được xác định; T004 và T035 cần thao tác ngoài code.

## Traceability summary

- **US1** — AS1–AS5; FR-001–FR-006, FR-008, FR-013–FR-017, FR-019; SC-001, SC-002, SC-004, SC-006, SC-007.
- **US2** — AS1–AS4; FR-007–FR-010, FR-012–FR-015, FR-018; SC-002–SC-005, SC-007. T033/T034 cover riêng signed-download của applicant.
- **US3** — AS1–AS3; FR-004, FR-006, FR-010–FR-014, FR-016–FR-019; SC-003–SC-007.
- **FR-018** — T001, T015, T019, T021, T024, T026; luồng revoke do 120 đặt `ownerStatus=NONE` nhưng giữ application APPROVED và cho phép nộp mới đợi query/history được nhóm xác nhận.
- **Success Criteria** — SC-001: T035 usability evaluation; SC-002: T015/T018/T020/T021; SC-003: T019/T022/T026/T028/T029; SC-004: T007/T008/T015/T018/T021/T023/T026/T029; SC-005: T002/T009/T010/T033/T034; SC-006: T001/T003/T016/T026/T028/T029; SC-007: T002/T009/T013/T016/T017/T027/T032/T033/T034.
- Tất cả task checklist dùng format `- [ ] Tnnn [P?] [USn?] ...` với file path đích; setup/foundation/polish không gắn story label. Tests có trong kế hoạch vì Constitution yêu cầu.
