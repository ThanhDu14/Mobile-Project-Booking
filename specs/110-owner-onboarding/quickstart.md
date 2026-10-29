# Hướng dẫn kiểm chứng: Feature 110

Tài liệu này hướng dẫn kiểm tra trên môi trường local/emulator; không chạy kiểm thử với Firebase/Supabase production. Tại ngày 2026-10-26, repository có Android form/review/status flow, status/submit/catalog/document-download Edge Functions và Firestore Rules tests. Android có thể yêu cầu signed URL mới để mở giấy tờ; URL không được lưu. Submit vẫn fail-closed do verifier chưa xác minh object Storage; chưa có sign-upload hoặc upload UI. README chưa quyết định bucket/path cho ảnh venue trước khi application được duyệt, nên ảnh upload và end-to-end submit chưa sẵn sàng. Có source/test file không thay thế kiểm chứng local.

## Trạng thái triển khai hiện tại

| Phần | Hiện trạng |
|---|---|
| Firebase auth và Firestore status/submit | Có shared auth/transaction và handler; status trả dữ liệu applicant-owned hiện hành (gồm draft khi REJECTED); submit từ chối an toàn khi chưa xác minh được tệp. App đối chiếu submit với status read-back authoritative. |
| District catalog | Có authenticated Edge Function đọc `districts` và Android picker; cần chạy Edge Function/Android tests. |
| Private document download | Android gọi handler để lấy signed URL 5 phút sau khi chọn giấy tờ; handler xác minh caller, application ID và `documentPaths`. Android không lưu URL, lỗi có thể xin URL mới. Chưa chạy Deno/Supabase local test. |
| Upload giấy tờ / ảnh venue | Chưa triển khai. Cần sign-upload, local bucket configuration/policies và object verification; đường dẫn ảnh venue trước duyệt chưa được xác định trong README. |
| Applicant document download | Mỗi lần mở/thử lại đều gọi `owner-document-download` mới, không tái sử dụng URL cũ; handler xác minh UID, application và path. Cần chạy Deno/local Storage test để xác nhận trên môi trường local. |
| Local `owner-docs` bucket | Migration tạo bucket private nếu chưa tồn tại, giới hạn MIME JPEG/PNG/WebP và 2 MB; không thêm policy direct client. Owner docs access đi qua Edge Function đã xác minh Firestore. |
| Map marker | Chưa có SDK bản đồ trong dependency hiện tại; form mới nhận tọa độ thủ công. |
| UI / emulator integration | Android unit suite chạy thành công (214 tests); debug APK đã build thành công. Chưa có Android UI instrumentation tests cho luồng upload/submit vì upload boundary chưa sẵn sàng. |

## Lệnh kiểm thử

Chạy từ thư mục repository, khi đã cài JDK/Android SDK, Deno, Firebase CLI và Supabase CLI theo hướng dẫn dự án:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
Push-Location firestore-tests; npm.cmd test; Pop-Location
supabase start
supabase functions serve --env-file .env.local
```

`npm.cmd test` tự khởi chạy Firebase Firestore Emulator qua `firebase-tools`; lệnh Supabase khởi động dịch vụ local và Edge Functions. Chạy riêng Deno handler tests bằng `deno test --allow-env --allow-net` theo từng file `handler.test.ts` trong các thư mục `owner-district-catalog`, `owner-document-download`, `owner-application-submit` và `get-owner-application-status`. Cấu hình local các biến Firebase service-account và Supabase service-role cần thiết trong `.env.local` không commit; không đưa secret vào source hoặc log. Ghi kết quả thực chạy ở cuối AI log, không xem lệnh chưa chạy là pass.

### Kết quả kiểm chứng thực tế gần nhất (2026-10-26)

- `.\gradlew.bat testDebugUnitTest --offline --no-daemon`: thành công; 214 unit tests, 0 failure, 0 error, 0 skipped.
- `.\gradlew.bat testDebugUnitTest assembleDebug --offline --no-daemon`: thành công trước đó trong cùng lượt triển khai; unit tests pass và debug APK được assemble.
- `firestore-tests`: `vitest run test/owner-applications.test.ts` không khởi tạo được do Firestore Emulator host/port chưa cấu hình; 13 test bị skip, không tính là pass.
- Deno, Firebase CLI/Emulator và Supabase CLI chưa có/sẵn để chạy handler, Security Rules hoặc Storage integration tests. Chưa chạy Android instrumentation tests.
- `git diff --check`: thành công; chỉ có cảnh báo line ending CRLF từ Git.

## Điều kiện chuẩn bị

- Cấu hình Android dùng Firebase Emulator Suite và Supabase local; function custom auth cần tắt gateway JWT verification theo `supabase/config.toml` vì handler tự verify Firebase ID token.
- Tài khoản kiểm thử: player đã đăng nhập có `ownerStatus=NONE`; player `PENDING`; player `REJECTED` kèm lý do; owner đã duyệt; người dùng không liên quan; tài khoản admin phục vụ kiểm tra policy.
- Khi Storage upload được triển khai, policy/hàm local và fixture Rules Emulator phải khớp [mô hình dữ liệu](data-model.md). Hiện không có policy upload owner applicant; không cấp quyền thủ công để tiếp tục test.
- Tệp ảnh hợp lệ JPEG, PNG, WebP; tệp MIME không hợp lệ, hỏng, quá lớn (>2 MB) và upload bị ngắt.
- Khi bổ sung phụ thuộc/lệnh kiểm thử, ghi lại lệnh đã xác nhận trong hướng dẫn này; tài liệu Plan không tự đặt tên tác vụ Gradle chưa kiểm chứng.

## Các tình huống kiểm chứng

1. **Nộp hồ sơ mới**: Dùng `PLAYER` có `NONE`, nhập đủ trường, chọn vị trí, thêm một ảnh venue và một giấy tờ, xem lại rồi gửi. Kỳ vọng: hồ sơ được máy chủ xác nhận đúng một lần, `users.ownerStatus=PENDING`, `role=PLAYER`, giao diện hiện đang chờ và không có quyền quản lý venue.
2. **Lỗi xác thực và thử tải lại**: Bỏ trống trường bắt buộc hoặc chọn giấy tờ sai định dạng/quá lớn/bị hỏng. Kỳ vọng: lỗi đúng trường/tệp, không tạo hồ sơ đã gửi. Ngắt một lượt tải, tải lại riêng tệp đó và xác nhận dữ liệu biểu mẫu được giữ.
3. **Gửi trùng/mất phản hồi**: Bấm gửi nhanh hai lần, sau đó giả lập timeout sau khi máy chủ có thể đã ghi. Mở lại/làm mới rồi thử tiếp. Kỳ vọng: tối đa một hồ sơ đang hiệu lực và không báo thành công `PENDING` trước xác nhận của máy chủ.
4. **Không sửa hồ sơ đang chờ**: Mở hồ sơ `PENDING`, thử sửa/nộp lại qua UI và request trực tiếp trái phép. Kỳ vọng: dữ liệu không đổi; chức năng player vẫn dùng được và máy chủ từ chối quyền quản lý owner.
5. **Đồng bộ trạng thái**: Tải lần lượt `NONE`, `PENDING`, `APPROVED`, `REJECTED`. Kiểm tra nhãn Việt hóa, vai trò tương ứng và khả năng thử lại. Với `REJECTED`, hiển thị lý do máy chủ cùng dữ liệu cũ; với `APPROVED`, điều hướng tới lối vào owner hiện có nhưng không triển khai màn hình quản lý ở đây.
6. **Nộp lại**: Sửa hồ sơ bị từ chối và gửi lại. Kỳ vọng: trạng thái về `PENDING`, role vẫn là `PLAYER`, lý do cũ không còn được hiển thị như quyết định hiện hành. Kiểm tra cách lưu document/lịch sử sau khi đã chốt định danh hồ sơ.
7. **Trạng thái thay đổi đồng thời**: Khi biểu mẫu mở, thay trạng thái trong emulator thành `PENDING`/`APPROVED` hoặc giả lập thiết bị khác vừa nộp lại. Gửi từ biểu mẫu cũ. Kỳ vọng: máy chủ trả xung đột/từ chối quyền, không ghi dở dang, tải trạng thái mới và giữ dữ liệu bản nháp.
8. **Quyền truy cập giấy tờ**: Người nộp mở giấy tờ của mình bằng signed URL còn hạn; player khác thử đọc bằng cách đoán object path. Kỳ vọng: chủ hồ sơ được phép, người khác bị từ chối. URL hết hạn phải thất bại; muốn tải lại cần xin URL mới có xác thực. Kiểm tra policy trên Supabase local.
9. **Mất mạng**: Tắt mạng khi đọc trạng thái, tải tệp và gửi hồ sơ. Kỳ vọng: dữ liệu cũ được đánh dấu, có thể tải lại tệp mà không nhập lại biểu mẫu, không báo gửi thành công sai và không cấp quyền owner từ cache.
10. **Dọn dữ liệu khi xóa tài khoản**: Dùng hợp đồng xóa đã thống nhất để xóa hồ sơ và các tệp riêng tư liên quan; giả lập xóa dở và xác minh cơ chế thử lại/kết quả được định nghĩa trước khi phát hành.

## Các tầng kiểm thử

- **Unit**: kiểm tra dữ liệu biểu mẫu, ánh xạ trạng thái sang UI, điều kiện gửi, xử lý phản hồi cũ và thử lại bằng repository giả.
- **Firebase Emulator**: kiểm tra được phép/bị chặn khi đọc/ghi hồ sơ của mình/người khác và trường được bảo vệ; kiểm tra gửi đồng thời/trạng thái cũ.
- **Supabase local**: kiểm tra `owner-docs` private RLS, quyền applicant/admin/người khác, MIME/kích thước và hạn signed URL; kiểm tra upload ảnh venue chỉ sau khi path trước duyệt được nhóm chốt. Handler tests hiện kiểm tra ownership/path và URL mới khi retry, nhưng chưa thay thế local Storage integration.
- **UI**: luồng gửi mới và nộp lại hồ sơ bị từ chối, trạng thái tải/rỗng/lỗi, tiến trình upload, thử lại và accessibility.
