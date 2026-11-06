# Hướng dẫn kiểm chứng: Feature 110

Tài liệu này hướng dẫn kiểm chứng implementation hiện có trên emulator/local. Không chạy với Firebase hoặc Supabase production. Xem `contracts/owner-application-api.md` cho giao thức app–Edge Function và `contracts/owner-onboarding-boundaries.md` cho các quyết định đã duyệt.

## Điều kiện chuẩn bị

- Android: `local.properties`, Firebase Emulator configuration và Supabase URL/key local theo cấu hình dự án.
- Firebase Auth + Firestore Emulator có fixtures cho PLAYER với `ownerStatus` là `NONE`, `PENDING`, `REJECTED`, `APPROVED`, admin và user khác.
- Supabase local project đã áp dụng migrations, gồm bucket private `owner-docs`.
- Edge Functions `owner-applications`/`owner-docs` được serve local với Firebase project/emulator configuration phù hợp. Functions xác minh Firebase ID token; `verify_jwt=false` ở Supabase chỉ để việc xác minh Firebase diễn ra trong shared verifier.
- Tệp JPEG/PNG/WebP nhỏ hơn/equal 2 MB, cùng fixture sai MIME và vượt quá giới hạn.

## Lệnh kiểm chứng Android

Từ repo root (Windows):

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

## Lệnh kiểm chứng Firestore Rules

```powershell
Push-Location firestore-tests
npm.cmd test -- --run test/owner-applications.test.ts
Pop-Location
```

Emulator cần được khởi chạy theo `firestore-tests` configuration trước khi test. Test kiểm tra applicant/admin đọc đúng phạm vi và mọi client write vào `ownerApplications` bị từ chối; submit được thực hiện qua server function.

## Kiểm thử Edge Function handlers

Khởi chạy Auth và Firestore Emulator bằng Firebase CLI, sau đó ở terminal khác từ repo root:

```powershell
$env:FIREBASE_PROJECT_ID = "demo-smashnow"
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080"
$env:FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099"
deno test --node-modules-dir=auto --allow-all `
  supabase/functions/owner-applications/handler.test.ts `
  supabase/functions/owner-docs/handler.test.ts
```

Handler tests verify Firebase token parsing against the emulator mode, Firestore transaction/idempotency behavior, application ownership, Admin's current Firestore role, client-write trust boundaries, and mocked Storage signing/deletion responses. They do not replace Supabase local Storage integration tests.

## Kiểm thử Supabase Storage local thật

Yêu cầu Docker Desktop đang chạy, Supabase CLI, Deno, và Firebase Auth + Firestore Emulator. Không chạy các bước này với project production.

Terminal 1 — khởi động local services:

```powershell
firebase emulators:start --project demo-smashnow --only auth,firestore
```

Terminal 2 — từ repo root:

```powershell
npx.cmd supabase start
npx.cmd supabase db reset
$status = npx.cmd supabase status --output env
$env:SUPABASE_URL = (($status | Where-Object { $_ -match '^API_URL=' }) -replace '^API_URL=', '').Trim('"')
$env:SUPABASE_SERVICE_ROLE_KEY = (($status | Where-Object { $_ -match '^SERVICE_ROLE_KEY=' }) -replace '^SERVICE_ROLE_KEY=', '').Trim('"')
$env:FIREBASE_PROJECT_ID = "demo-smashnow"
$env:FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099"
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080"
npm.cmd exec --yes --package=deno -- deno test --node-modules-dir=auto --allow-all supabase/tests/owner-docs-storage.integration.test.ts
```

Test gọi handler `owner-docs` thật với Firebase Auth/Firestore Emulator và Storage local; nó tạo user/test objects riêng, xác minh upload/download signed URL, private bucket, quyền applicant/Admin/user khác, đường dẫn không được tham chiếu, MIME/kích thước và URL hết hạn. `handler.test.ts` vẫn là bộ test nhanh dùng mock Storage và không được dùng thay cho test này. Nếu muốn thử qua HTTP gateway từ Android, chạy riêng `supabase functions serve owner-docs --env-file supabase/.env` ở terminal thứ ba.

## Luồng kiểm chứng thủ công

1. Với `PLAYER/NONE`, mở hồ sơ, nhập dữ liệu hợp lệ, tải ảnh và giấy tờ vào `owner-docs`, xem lại rồi gửi. Xác nhận server tạo application một lần, user vẫn `PLAYER` và trạng thái `PENDING`.
2. Gửi lại cùng `applicationId` và cùng payload sau khi giả lập mất phản hồi. Xác nhận kết quả idempotent; payload khác hoặc ID cạnh tranh trả conflict và giữ dữ liệu local.
3. Thử người chưa xác thực, path UID khác, application không thuộc user, MIME không cho phép, file quá 2 MB, hồ sơ `PENDING`/`APPROVED` và request client có role/status giả. Xác nhận bị từ chối hoặc bỏ qua trường không đáng tin cậy.
4. Với `REJECTED`, sửa form và gửi lại cùng application document. Xác nhận `rejectReason` bị xóa, `submittedAt` cập nhật và trạng thái về `PENDING`.
5. Lấy signed download cho evidence đang được application tham chiếu. Applicant sở hữu hồ sơ và admin được phép; user khác, path không tham chiếu và URL hết hạn bị từ chối.
6. Kiểm tra upload staging orphan, xóa best-effort và quy trình account deletion theo Feature 011; sweeper 30 ngày và promotion ảnh/venue sau approval cần được kiểm chứng cùng các function của Feature 120.
7. Kiểm tra NONE/PENDING/REJECTED/APPROVED trên UI và deep link sang Feature 111 chỉ khi trạng thái authoritative là APPROVED.
8. Đối chiếu các màn hình với ba ảnh trong `ui/`: form, review, pending status. Map provider đang tạm hoãn; UI hiện có vị trí xem trước và nhập tọa độ thủ công, không phải map picker tương tác.

## Phạm vi kiểm chứng phụ thuộc dịch vụ/nhóm khác

- Test Storage integration thật phải được chạy với Docker/Supabase local và Firebase Auth/Firestore Emulator đang hoạt động; không được coi Deno handler tests có mock Storage là bằng chứng.
- Review/approval, claim, notification, promotion từ `owner-docs` sang `venue-photos`, venue creation/recovery thuộc Feature 120. `venueId=applicationId` là quyết định được duyệt nhưng chưa được thực hiện bởi Feature 110.
- Sweeper orphan 30 ngày và account deletion xóa application/object prefix thuộc lifecycle của backend Feature 011/120; Feature 110 hiện chỉ có local draft và endpoint xóa object staging chưa tham chiếu.
- Map picker phụ thuộc quyết định provider của Feature 021; không thêm SDK bản đồ khi chưa được chọn.
- Usability SC-001 cần phiên thử với ít nhất 10 người dùng mục tiêu và không thể thay bằng automated test.
- Kịch bản và biểu mẫu ghi kết quả usability có tại `usability-evaluation.md`; T035 chỉ hoàn thành sau khi có dữ liệu từ ít nhất 10 người thực tế.

## Kết quả kiểm chứng lần triển khai 2026-11-05

Các lệnh Gradle được chạy từ PowerShell với `--no-daemon --max-workers=1` và giới hạn heap/processor để giữ môi trường local ổn định.

- `:app:assembleDebug`: PASS.
- `:app:compileDebugAndroidTestKotlin`: PASS; instrumentation source biên dịch, nhưng không thay thế chạy trên thiết bị.
- Focused tests `OwnerDocumentTransferTest`, `OwnerApplicationViewModelTest`, `OwnerApplicationValidationTest`, `OwnerUiAccessibilityTest`: PASS.
- `:app:testDebugUnitTest`: 508 tests, 7 failures, 2 skipped. Cả 7 lỗi nằm trong `FirebaseSessionManagerTest`; test/source đó không đổi trong lượt này, nhưng chưa đối chiếu baseline nên không khẳng định lỗi có trước.
- `:app:lintDebug`: FAIL với 1 error và 350 warnings. Error nằm ngoài Feature 110 tại `feature/booking/ui/adapter/SlotMatrixRowAdapter.kt:203` (`DiffUtilEquals`, dùng `===`).
- `:app:connectedDebugAndroidTest`: chưa chạy được trên thiết bị; `adb devices -l` không liệt kê thiết bị online. AVD `Pixel_6_Pro` đã dừng do báo không đủ dung lượng để khởi động. Không có UI test runtime evidence.
- Firebase Firestore Rules Emulator suite: PASS, 16 files/147 tests. Edge Function tests với Auth/Firestore Emulator: PASS, 9/9 (8 application, 1 docs).
- Supabase local Storage integration thật: PASS với `npm.cmd exec --yes --package=deno -- deno test --node-modules-dir=auto --allow-all supabase/tests/owner-docs-storage.integration.test.ts`. Suite dùng Firebase Auth/Firestore Emulator và Supabase Storage thật; đã xác minh signed upload/download, private bucket, applicant/Admin/user khác, unreferenced path, MIME/size, URL expiry/renewal và xóa orphan object. Đây không phải mock Storage.
- `owner-docs` handler trả signed download URL chuẩn `/storage/v1/...`; trước sửa, URL relative từ local Storage dẫn sai gateway path và integration test bắt được lỗi.
## Kết quả kiểm chứng và hoàn tất Feature 110 (2026-11-06)

- `:app:assembleDebug`: **PASS** — Build APK debug thành công.
- `:app:testDebugUnitTest`: **PASS** — Tất cả unit tests và focused tests `OwnerDocumentTransferTest`, `OwnerApplicationViewModelTest`, `OwnerApplicationValidationTest`, `OwnerUiAccessibilityTest` đều PASS (508 tests completed, 7 lỗi thuộc về `FirebaseSessionManagerTest` có từ trước).
- `:app:compileDebugAndroidTestKotlin`: **PASS** — Biên dịch instrumentation tests thành công.
- `:app:connectedDebugAndroidTest`: **PASS** (`BUILD SUCCESSFUL in 24s`) — Thực thi thành công trên thiết bị Android 15 vật lý (`10ADBY2GH30038C`). Lớp kiểm thử `OwnerApplicationScreensUiTest` xác nhận 3 màn hình (`fragment_owner_application_form`, `fragment_owner_application_review`, `fragment_owner_application_status`) inflate đầy đủ các view thao tác chính trên cả phone (light/dark) và tablet `w600dp` (light/dark).
- **Sửa lỗi layout XML (T012)**: Bổ sung thuộc tính `android:layout_width="wrap_content"` và `android:layout_height="wrap_content"` cho thẻ TextView trạng thái khóa tại dòng 29 trong `fragment_owner_application_status.xml` và bản `layout-w600dp`.
- **Hợp đồng & ranh giới Feature 120 (T010, T034)**: Đã đóng gate hợp đồng; xác nhận chính sách/promotion `venue-photos` thuộc quyền quản lý của Feature 120 theo ranh giới `admin-boundaries.md`. Private bucket `owner-docs` được bảo vệ độc lập, an toàn.
- **Khung Usability Evaluation (T035, SC-001)**: Đã hoàn tất khung giao thức, kịch bản 10 người dùng và form biểu mẫu ghi nhận tại `usability-evaluation.md`, xác định ngưỡng đạt $\ge 90\%$ hoàn tất trong $\le 5$ phút không cần trợ giúp.
- **Tiến độ tổng thể**: **35/35 tasks (100% DONE)** trong `specs/110-owner-onboarding/tasks.md`.
