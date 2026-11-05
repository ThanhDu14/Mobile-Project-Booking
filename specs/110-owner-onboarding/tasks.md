---
description: "Danh sách task triển khai Feature 110 — Đăng ký làm chủ sân"
---

# Tasks: Đăng ký làm chủ sân (Feature 110)

**Input**: `specs/110-owner-onboarding/spec.md`, `plan.md`, `data-model.md`, `contracts/owner-onboarding-boundaries.md`, `research.md`, `quickstart.md`, và `checklists/requirements.md`.

**Organization**: Theo User Story và ưu tiên trong spec. Mỗi task gắn FR/Acceptance Scenario (AS) để truy vết; test được đưa vào vì Constitution bắt buộc kiểm thử logic, Security Rules và Storage RLS.

**Repository hiện trạng (cập nhật 2026-11-05)**: Android hiện đã có `feature/owner` cho Feature 111, Supabase Edge Functions dùng chung auth/Firestore REST và các dependency Hilt/Coroutines/Supabase SDK/Storage đã được khai báo. Feature 110 tái sử dụng các thành phần này; không thêm thư viện cho phần cốt lõi. Map provider được chủ dự án tạm hoãn nên phần chọn marker trên bản đồ còn phụ thuộc quyết định đó.

## Phase 1: Setup và xác nhận ranh giới

**Purpose**: Chốt các hợp đồng và tiền đề kỹ thuật trước những phần triển khai bị phụ thuộc; không tự thêm schema/API hay đổi nghiệp vụ.

- [X] T001 Ghi nhận lựa chọn C về vòng đời application, truy vấn theo UID/submittedAt, resubmit cùng document bị từ chối và document mới sau revoke trong `contracts/owner-onboarding-boundaries.md` (FR-006, FR-018; US1-AS5, US3-AS2; SC-006)
- [X] T002 Ghi contract app→Supabase cho Firebase token, `owner-applications` STATUS/SUBMIT, `owner-docs` SIGN_UPLOAD/SIGN_DOWNLOAD/DELETE_STAGED, payload/error/retry và private staging `owner-docs` cho evidence lẫn ảnh venue — `contracts/owner-application-api.md` (FR-003, FR-012, FR-013; US1-AS2; SC-005, SC-007)
- [X] T003 Ghi nhận lựa chọn B: Edge Function xác thực UID, dùng Firestore transaction đồng bộ application/user, tái sử dụng application ID, trả idempotent result hoặc conflict; không thêm idempotency/version field (FR-005, FR-006, FR-017, FR-019; US1-AS2, US1-AS5, US3-AS3; SC-002, SC-006)
- [X] T004 Ghi nhận cleanup: draft app-private; client best-effort; sweeper orphan sau 30 ngày; Feature 011 xóa dữ liệu/tệp theo contract; Feature 120 promote ảnh (FR-016; US1-AS2, US1-AS5, US3-AS2)

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Hoàn thiện nền tảng app và server security dùng chung sau khi các quyết định tương ứng ở Phase 1 được chốt.

- [X] T005 Tái sử dụng architecture/package conventions hiện có và dependency đã khai báo (Hilt, Coroutines, Navigation, Material, Firebase, Supabase Functions/Storage); không thêm dependency mới. Map SDK chưa được chọn nên không thêm SDK bản đồ; căn cứ ghi trong plan/contract (Constitution VII)
- [X] T006 Tạo repository/data mapping cho schema hiện có; client đọc status/application và giữ state draft app-private — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-005, FR-007, FR-009, FR-010, FR-014)
- [X] T007 Bổ sung Firestore Rules cho phép đọc application của chính người nộp/admin và chặn client ghi application; server Edge Function là writer duy nhất cho application/status — `firestore.rules` (FR-008, FR-012, FR-013, FR-014, FR-019)
- [X] T008 [P] Viết Firebase Emulator allow/deny cho applicant/admin, người khác, anonymous, PENDING/REJECTED/APPROVED và chặn mọi client create/update trực tiếp — `firestore-tests/test/owner-applications.test.ts` (FR-008, FR-013, FR-014, FR-017, FR-019; US1-AS3, US3-AS3)
- [X] T009 Tạo private bucket `owner-docs` với MIME JPEG/PNG/WebP và giới hạn 2 MB, cùng signed upload/download/delete qua Edge Function theo contract. Chưa có sweeper 30 ngày hoặc policy/promotion `venue-photos` của Feature 120 — `supabase/migrations/`, `supabase/functions/` (FR-003, FR-012, FR-013; SC-005, SC-007)
- [ ] T010 [P] Hoàn thiện Supabase local Storage integration tests cho signed upload/download: applicant/Admin/user khác, path không tham chiếu, URL hết hạn, MIME/kích thước, private bucket và quyền/promotion `venue-photos`. Handler authorization tests dùng mock Storage đã có; xác minh thật với Supabase local vẫn còn — `supabase/functions/owner-docs/handler.test.ts`, `supabase/tests/` (FR-003, FR-012, FR-013; US1-AS2; SC-005)

## Phase 3: User Story 1 — Nộp hồ sơ chủ sân (P1, MVP)

**Goal**: Người chơi `PLAYER` chưa nộp có thể điền, xác thực, tải tệp, xem lại và nộp một hồ sơ; vẫn là PLAYER khi chờ.

**Independent Test**: Với player `ownerStatus=NONE`, gửi hồ sơ hợp lệ cùng ít nhất một ảnh cơ sở và một giấy tờ; server xác nhận đúng một hồ sơ `PENDING`, user vẫn `PLAYER`; không thể quản lý venue. Thử dữ liệu sai, upload lỗi, request trực tiếp và timeout.

- [X] T011 [P] [US1] Tạo model/validator thuần Kotlin cho tên cơ sở, địa chỉ, district đã chọn từ danh mục, tọa độ, phone, số sân nguyên dương, giờ mở/đóng, người đại diện và phone; bắt buộc trim không rỗng, tọa độ hợp lệ, ảnh cơ sở và giấy tờ đều có; không tự đặt giới hạn số sân, chuẩn hóa phone hoặc quy tắc giờ qua đêm — `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-002, FR-004; US1-AS4; SC-001, SC-007)
- [ ] T012 [P] [US1] Tạo màn hình biểu mẫu XML theo Material 3 và `Theme.Material3.DayNight`, hỗ trợ light/dark, điện thoại và `w600dp`; dùng resource tiếng Việt/accessibility; hiển thị lỗi tại trường. Đảm bảo chọn marker vị trí thủ công không cần quyền runtime vị trí; chỉ lấy vị trí hiện tại khi user cấp quyền, không chặn submit khi quyền bị từ chối — `app/src/main/res/layout/fragment_owner_application_form.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/navigation/nav_graph.xml`, `app/src/main/res/layout-w600dp/` (FR-001, FR-002, FR-004; US1-AS1, US1-AS4; SC-001)
- [ ] T013 [US1] Tạo bộ chọn/tải ảnh cơ sở và giấy tờ với kiểm tra MIME/size, tiến trình từng tệp, retry riêng tệp lỗi và chỉ gắn path sau upload thành công; route upload theo contract T002/T009 đã được duyệt, lưu path chứ không lưu URL; giữ biểu mẫu khi lỗi có thể khôi phục — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/` (FR-003, FR-016; US1-AS2; SC-007)
- [X] T014 [US1] Tạo màn hình xem lại nội dung/tài liệu trước khi gửi, cho sửa theo từng phần và chặn gửi nếu validation chưa đạt — `app/src/main/res/layout/fragment_owner_application_review.xml`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-004; US1-AS2, US1-AS4)
- [X] T015 [US1] Submit qua boundary đã duyệt: server xác nhận role/status/district, kiểm tra object, transaction tạo application + chuyển user thành PENDING; client không ghi field server-only — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/`, `supabase/functions/` (FR-005, FR-008, FR-013, FR-014, FR-019; US1-AS2, US1-AS3; SC-002, SC-004)
- [X] T016 [US1] Retry cùng ID/payload trả kết quả đã commit; xung đột giữ draft, khóa submit khi đang xử lý và refresh trạng thái — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `supabase/functions/` (FR-006, FR-016, FR-017; US1-AS5; SC-006, SC-007)
- [ ] T017 [P] [US1] Viết unit test validator và ViewModel cho trường hợp hợp lệ/thiếu/sai, lỗi theo trường, tiến trình upload, retry, không mất dữ liệu, khóa submit và kết quả timeout — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-002–FR-006, FR-015–FR-017; US1-AS1, US1-AS2, US1-AS4, US1-AS5; SC-006, SC-007)
- [ ] T018 [US1] Viết UI test luồng mở form → điền → upload → xem lại → submit và xác nhận pending; kiểm tra người pending không vào được quản lý venue; kiểm tra màn form ở Material 3 light/dark, điện thoại/`w600dp`, và trạng thái loading/error cùng hành vi ghim thủ công khi từ chối quyền vị trí — `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-001, FR-005, FR-008; US1-AS1–AS3; SC-001, SC-002, SC-004)

## Phase 4: User Story 2 — Theo dõi kết quả duyệt (P1)

**Goal**: Người nộp xem trạng thái hiện hành từ server, thời điểm gửi và hành động tương ứng; quyết định admin và quyền vẫn thuộc Feature 120.

**Independent Test**: Tải lần lượt NONE/PENDING/APPROVED/REJECTED từ server và xác nhận nhãn, role, lý do, thời điểm và lối tiếp theo đúng; lỗi mạng không cấp quyền từ cache.

- [X] T019 [P] [US2] Ánh xạ ownerStatus authoritative cùng submittedAt/rejectReason từ application hiện hành — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-007, FR-009, FR-010, FR-018; US2-AS1–AS4; SC-002, SC-003)
- [X] T020 [US2] Tạo màn hình trạng thái theo Material 3/DayNight, light/dark, phone/`w600dp`, loading/error/retry và refresh server khi mở; hiển thị NONE/PENDING/APPROVED/REJECTED cùng hành động theo trạng thái — `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/fragment_owner_application_status.xml`, `app/src/main/res/layout-w600dp/`, `app/src/main/res/values/strings_owner_application.xml` (FR-007, FR-015; US2-AS1–AS4; SC-002, SC-003)
- [X] T021 [US2] PENDING/REJECTED giữ quyền PLAYER; APPROVED refresh trạng thái/token rồi điều hướng tới luồng owner hiện có; NONE cho phép application lifecycle mới — `app/src/main/java/com/example/booking_app/feature/owner/`, `app/src/main/res/navigation/nav_account.xml` (FR-008, FR-009, FR-018; US2-AS1–AS4; SC-002, SC-004)
- [ ] T022 [P] [US2] Viết unit test ánh xạ 4 ownerStatus, role tương ứng, lý do/thời điểm, trạng thái cũ khi offline và không mở quyền owner từ cache — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-007–FR-010, FR-015; US2-AS1–AS4; SC-002, SC-003)
- [ ] T023 [US2] Viết UI test cho NONE/PENDING/APPROVED/REJECTED, refresh, retry, permission error không lộ dữ liệu, và đủ loading/content/empty/error; kiểm tra Material 3 light/dark, điện thoại và `w600dp` — `app/src/androidTest/java/com/example/booking_app/feature/owner/` (FR-007–FR-015; US2-AS1–AS4; SC-002, SC-003, SC-004)
- [ ] T033 [US2] Tạo luồng người nộp xem/tải giấy tờ của application thuộc chính mình: chỉ yêu cầu signed URL qua cơ chế `sign-download` đã được README xác nhận; không lưu URL công khai, không hiển thị giấy tờ cho user khác; khi URL hết hạn hoặc tải lỗi, báo lỗi và xin signed URL mới/thử tải lại theo contract, không tiết lộ path hoặc dữ liệu hồ sơ khác — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-012, FR-013, FR-015; US2-AS1–AS3; SC-005, SC-007)
- [ ] T034 [P] [US2] Viết Android/UI và Storage integration tests cho applicant signed-download: hồ sơ/tệp của mình mở được, hồ sơ/tệp UID khác bị chặn, URL hết hạn xin mới, permission/network error không lộ path. Backend handler authorization coverage đã có tại `supabase/functions/owner-docs/handler.test.ts`; UI và signed-URL expiry vẫn chưa xác minh qua Supabase local — `app/src/test/java/com/example/booking_app/feature/owner/`, `app/src/androidTest/java/com/example/booking_app/feature/owner/`, `supabase/functions/owner-docs/handler.test.ts` (FR-012, FR-013, FR-015; US2-AS1–AS3; SC-005, SC-007)

## Phase 5: User Story 3 — Sửa và nộp lại hồ sơ bị từ chối (P2)

**Goal**: Người nộp REJECTED xem lý do/dữ liệu cũ, sửa phần applicant-owned và nộp lại; quay về PENDING, vẫn PLAYER.

**Independent Test**: Với hồ sơ REJECTED có lý do, nạp dữ liệu, chỉnh sửa hợp lệ, submit lại; server xác nhận trạng thái hiện hành PENDING/PLAYER. PENDING không được sửa/rút; kiểm tra xung đột khi hồ sơ đổi trong lúc mở.

- [X] T024 [US3] Tải hồ sơ REJECTED authoritative cùng reason, draft, representative và các path tệp vào form — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-010, FR-018; US3-AS1; SC-003)
- [X] T025 [US3] Dùng lại form/review cho hồ sơ rejected, sửa thông tin/tệp và giữ draft app-private — `app/src/main/java/com/example/booking_app/feature/owner/ui/`, `app/src/main/res/layout/fragment_owner_application_form.xml`, `app/src/main/res/layout/fragment_owner_application_review.xml` (FR-004, FR-010, FR-016; US3-AS1)
- [X] T026 [US3] Resubmit hồ sơ REJECTED trên cùng document ID; transaction đổi về PENDING, giữ PLAYER và xóa rejectReason hiện hành — `app/src/main/java/com/example/booking_app/feature/owner/data/`, `supabase/functions/`, `firestore.rules` (FR-006, FR-011, FR-013, FR-014, FR-017, FR-019; US3-AS2, US3-AS3; SC-003, SC-004, SC-006)
- [X] T027 [US3] Sau lỗi/conflict refresh trạng thái authoritative, từ chối stale write và giữ draft phục hồi được — `app/src/main/java/com/example/booking_app/feature/owner/domain/`, `app/src/main/java/com/example/booking_app/feature/owner/data/`, `app/src/main/java/com/example/booking_app/feature/owner/ui/` (FR-017, FR-019; US3-AS3; SC-006, SC-007)
- [ ] T028 [P] [US3] Viết unit test resubmit REJECTED, trạng thái không hợp lệ, lý do, giữ PLAYER, response conflict và không mất draft — `app/src/test/java/com/example/booking_app/feature/owner/` (FR-010, FR-011, FR-017, FR-019; US3-AS1–AS3; SC-003, SC-006, SC-007)
- [ ] T029 [US3] Sau T001/T003, viết UI/emulator integration test sửa+nộp lại và request trái phép khi PENDING; xác nhận application hiện hành theo quyết định nhóm, không phát sinh hồ sơ trùng; kiểm tra UI rejected/loading/error ở light/dark và phone/`w600dp` — `app/src/androidTest/java/com/example/booking_app/feature/owner/`, `firestore-tests/test/owner-applications.test.ts` (FR-006, FR-011, FR-013, FR-019; US3-AS2–AS3; SC-003, SC-004, SC-006)

## Phase 6: Polish và cross-cutting

- [ ] T030 [P] Đối chiếu luồng account/profile: 011 sở hữu entry point và summary tài khoản; 110 cung cấp form/status và không nhân đôi login/profile — `app/src/main/java/com/example/booking_app/feature/owner/`, `app/src/main/res/navigation/nav_graph.xml` (FR-001, FR-007)
- [ ] T031 [P] Đối chiếu quyền handoff: 120 sở hữu approve/reject/revoke, role/claim, rejectReason và tạo venue từ `venueDraft`; 111/112 chỉ mở sau APPROVED và sở hữu quản lý/vận hành; không thêm nghiệp vụ của các feature đó — `app/src/main/java/com/example/booking_app/feature/owner/` (FR-009–FR-011)
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

1. **Feature 120 approval integration**: chưa có server review/reject/revoke, photo promotion, venue creation, claim/notification recovery trong Feature 110. Feature 120 cần xác nhận đã áp dụng thứ tự recovery và `venueId=applicationId` đã duyệt; Feature 110 không cấp role/venue.
2. **Cleanup lifecycle**: quyết định retention đã được duyệt, nhưng sweeper 30 ngày và Feature 011 account deletion integration chưa được triển khai/kiểm thử ở đây.
3. **Local backend verification**: cần Firebase Auth/Firestore Emulator và Supabase local integration tests cho Edge Functions, signed storage URL, expiry, MIME/size, authorization và concurrent submit.
4. **Map picker**: provider chưa được chọn theo quyết định của chủ dự án; hiện chỉ có map preview và nhập tọa độ, không thể hoàn tất map picker mà không có quyết định Feature 021.
5. **Usability SC-001**: cần thử thủ công với tối thiểu 10 người dùng mục tiêu.
6. **Validation details**: spec để ngỏ số sân tối đa, giờ qua nửa đêm/ngày nghỉ, khớp địa chỉ-ghim và chuẩn hóa/độ dài phone; implementation chỉ áp dụng quy tắc đã chốt.

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

## Traceability summary

- **US1** — AS1–AS5; FR-001–FR-006, FR-008, FR-013–FR-017, FR-019; SC-001, SC-002, SC-004, SC-006, SC-007.
- **US2** — AS1–AS4; FR-007–FR-010, FR-012–FR-015, FR-018; SC-002–SC-005, SC-007. T033/T034 cover riêng signed-download của applicant.
- **US3** — AS1–AS3; FR-004, FR-006, FR-010–FR-014, FR-016–FR-019; SC-003–SC-007.
- **FR-018** — T001, T015, T019, T021, T024, T026; luồng revoke do 120 đặt `ownerStatus=NONE` nhưng giữ application APPROVED và cho phép nộp mới đợi query/history được nhóm xác nhận.
- **Success Criteria** — SC-001: T035 usability evaluation; SC-002: T015/T018/T020/T021; SC-003: T019/T022/T026/T028/T029; SC-004: T007/T008/T015/T018/T021/T023/T026/T029; SC-005: T002/T009/T010/T033/T034; SC-006: T001/T003/T016/T026/T028/T029; SC-007: T002/T009/T013/T016/T017/T027/T032/T033/T034.
- Tất cả task checklist dùng format `- [ ] Tnnn [P?] [USn?] ...` với file path đích; setup/foundation/polish không gắn story label. Tests có trong kế hoạch vì Constitution yêu cầu.
