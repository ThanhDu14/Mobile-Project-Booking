# Kiểm chứng Thêm cơ sở — 2026-11-20

Backend kiểm thử là Firebase Auth/Firestore Emulator và Supabase local, không phải hosted. Điện thoại vivo V2247, Android 15, serial `10ADBY2GH30038C`; APK kiểm thử dùng package `.adminlocal` riêng. File picker Android được cung cấp ảnh TEST; form, ViewModel, repository, upload Storage, signed download và admin review đều chạy thật.

Nguyên nhân: nút Thêm cơ sở chỉ ghi thông báo; owner-applications chặn tài khoản APPROVED; status gộp hồ sơ mới nhất với trạng thái tài khoản; admin review luôn thay đổi role/ownerStatus. Upload cũng chỉ nhận PLAYER.

Bản sửa dùng `purpose=OWNER_ONBOARDING|VENUE_ADDITION`, tương thích hồ sơ cũ thiếu purpose. Hồ sơ bổ sung được tạo/nộp lại theo UUID ổn định, duyệt tạo `venues/{applicationId}` bằng transaction và create precondition. Quyền OWNER/APPROVED, claims và venue cũ không đổi khi xét hồ sơ bổ sung. Transaction kiểm tra lại applicant và admin hiện tại. Client không được tự tạo hồ sơ/venue hoặc sửa trường server.

Android có form/review/status và lịch sử hồ sơ bổ sung. Draft tách theo UID/ngữ cảnh; loading khóa trường nhập, tránh ghi đè dữ liệu vừa nhập. Bộ chọn quận dùng một dropdown; trạng thái bổ sung không điều hướng theo ownerStatus của tài khoản. SessionManager đọc quyền tài khoản, truy vấn onboarding loại hồ sơ bổ sung. Danh sách venue đọc SERVER khi quay lại và khi mở ứng dụng.

## Kiểm thử

Kết quả chốt được ghi trong `results.json`, `device.log`, `backend.json` và các log CI kèm thư mục này. Ảnh chụp nằm trong `screenshots/`.

| Kiểm tra | Kết quả cuối |
| --- | --- |
| Android lint, unit test, assemble | BUILD SUCCESSFUL; 1.369 test, 0 lỗi, 2 skipped |
| Edge Functions | Type check pass; 396 passed, 0 failed |
| Firestore Rules | 27 file pass; 265 passed, 4 skipped |
| vivo V2247 / Android 15 | OK (2 tests), gồm đăng ký lần đầu và bổ sung từ chối/nộp lại/duyệt |
| Đối chiếu backend | OWNER/APPROVED; owner cũ có 2 venue, owner mới có 1 venue; giấy tờ private |
| Ảnh chụp | 19 ảnh, gồm form/review/status, admin, lịch sử và hai cơ sở sau mở lại |

Backend regression bao gồm đăng ký lần đầu, hồ sơ cũ, thêm nhiều cơ sở, nộp lại, quyền bị thu hồi, giả ownership, upload/signed URL, gửi lặp, hai admin duyệt đồng thời và thu hồi quyền giữa bước publication với commit. Rules xác minh owner đọc hồ sơ của mình, tiếp tục đọc venue cũ, không tự tạo venue/hồ sơ hoặc sửa trạng thái.

Máy thật kiểm tra đăng ký lần đầu đến APPROVED; mở thêm cơ sở qua PENDING → REJECTED → nộp lại → APPROVED, xem giấy tờ private và mở lại ứng dụng. Mỗi giai đoạn bổ sung kiểm tra OWNER/APPROVED và venue cũ; sau duyệt kiểm tra đúng hai venue thuộc owner.

## Giới hạn

Ba job được chạy local theo `.github/workflows/ci.yml`; đây không phải kết quả GitHub Actions. Rules giữ 4 skipped theo `SKIP_BLOCKED=1` của CI hiện có. Chưa deploy thay đổi lên hosted; chưa xác minh mạng 4G, SLA hoặc thiết bị tablet thật. Các lượt bị gián đoạn và fixture/draft TEST cũ được sửa trước lượt chốt; chỉ log/ảnh của lượt thành công dùng làm bằng chứng cuối.
