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

## Luồng kiểm chứng thủ công

1. Với `PLAYER/NONE`, mở hồ sơ, nhập dữ liệu hợp lệ, tải ảnh và giấy tờ vào `owner-docs`, xem lại rồi gửi. Xác nhận server tạo application một lần, user vẫn `PLAYER` và trạng thái `PENDING`.
2. Gửi lại cùng `applicationId` và cùng payload sau khi giả lập mất phản hồi. Xác nhận kết quả idempotent; payload khác hoặc ID cạnh tranh trả conflict và giữ dữ liệu local.
3. Thử người chưa xác thực, path UID khác, application không thuộc user, MIME không cho phép, file quá 2 MB, hồ sơ `PENDING`/`APPROVED` và request client có role/status giả. Xác nhận bị từ chối hoặc bỏ qua trường không đáng tin cậy.
4. Với `REJECTED`, sửa form và gửi lại cùng application document. Xác nhận `rejectReason` bị xóa, `submittedAt` cập nhật và trạng thái về `PENDING`.
5. Lấy signed download cho evidence đang được application tham chiếu. Applicant sở hữu hồ sơ và admin được phép; user khác, path không tham chiếu và URL hết hạn bị từ chối.
6. Kiểm tra upload staging orphan, xóa best-effort và quy trình account deletion theo Feature 011; sweeper 30 ngày và promotion ảnh/venue sau approval cần được kiểm chứng cùng các function của Feature 120.
7. Kiểm tra NONE/PENDING/REJECTED/APPROVED trên UI và deep link sang Feature 111 chỉ khi trạng thái authoritative là APPROVED.
8. Đối chiếu các màn hình với ba ảnh trong `ui/`: form, review, pending status. Map provider đang tạm hoãn; UI hiện có vị trí xem trước và nhập tọa độ thủ công, không phải map picker tương tác.

## Phạm vi kiểm chứng chưa thể hoàn tất tại repo này

- Emulator test cho Edge Functions `owner-applications` và `owner-docs` cần Deno test runner/configuration và Firebase Auth/Firestore Emulator. Supabase local Storage signed URL, expiration, MIME/size và cleanup cũng chưa có integration suite được cấu hình.
- Review/approval, claim, notification, promotion từ `owner-docs` sang `venue-photos`, venue creation/recovery thuộc Feature 120. `venueId=applicationId` là quyết định được duyệt nhưng chưa được thực hiện bởi Feature 110.
- Sweeper orphan 30 ngày và account deletion xóa application/object prefix thuộc lifecycle của backend Feature 011/120; Feature 110 hiện chỉ có local draft và endpoint xóa object staging chưa tham chiếu.
- Map picker phụ thuộc quyết định provider của Feature 021; không thêm SDK bản đồ khi chưa được chọn.
- Usability SC-001 cần phiên thử với ít nhất 10 người dùng mục tiêu và không thể thay bằng automated test.

## Kết quả kiểm chứng lần triển khai 2026-11-05

- `:app:assembleDebug`: thành công.
- Hai test mục tiêu `OwnerApplicationValidationTest` và `OwnerUiAccessibilityTest`: thành công sau khi thêm touch target metadata và layout variants `w600dp`.
- `:app:testDebugUnitTest` đầy đủ: 494 tests, 7 failures, 2 skipped. Cả 7 lỗi là các assertion trong `FirebaseSessionManagerTest`; chưa đối chiếu baseline commit nên chưa thể khẳng định chúng tồn tại từ trước, nhưng test/source này không thuộc Feature 110 và không bị sửa trong lượt này.
- `:app:lintDebug`: thất bại với 1 error và 348 warnings. Error ở code ngoài phạm vi Feature 110: `feature/booking/ui/adapter/SlotMatrixRowAdapter.kt:203` (`DiffUtilEquals`, dùng `===`). Không sửa code Feature khác để làm lint pass.
- Firestore Emulator suite: thành công, 16 files/147 tests pass (chạy bằng JDK 21 portable tạm trong workspace); test mới xác nhận ownerApplications read/write boundary.
- Edge Function handler tests: Deno type-check + Auth/Firestore Emulator thành công, 8/8 tests pass (7 application, 1 docs). Supabase Storage HTTP được mock; cần Supabase local stack để xác nhận signed upload/download thực tế, expiration và MIME/size enforcement.
