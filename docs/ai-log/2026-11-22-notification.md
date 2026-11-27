# AI log: Feature 100 discovery and inbox security

Date: 2026-11-22. Tool: Codex. Feature: 100 — Notification.

User requested implementation of specs/100-notification/tasks.md, direct inspection of both UI references, reuse of existing contracts, no invented schema/protocol, build/tests and screenshot verification.

Used: repository/contract inventory, notification domain catalog and explicit new-account push defaults, inbox ownership Rules, Emulator regression tests, and implementation-review.md recording proposals and blockers. User selected grouped settings as pictured and requested proposals for unresolved decisions. No proposal was treated as approval.

Verification: 3 new JUnit tests pass; 26 focused Emulator tests pass. Debug APK assembled; lint reports 0 errors/849 warnings. Aggregate Gradle run status is recorded in Feature 100 implementation review. UI, delivery and end-to-end completion are not claimed. Checklist markers were not modified.

Follow-up: user approved paging/default/group decisions and requested durable delivery-state/job design. Added Firestore inbox/preferences repositories and 2 more pure unit tests (5 total passing), focused build passes. Earlier aggregate build/unit/lint passed; final repository behavior tests, UI and server integration remain pending. Delivery schema proposal is reviewable but not implemented.

## Tiếp tục luồng Android

Người dùng yêu cầu kiểm tra hai repository, thêm kiểm thử hành vi, triển khai inbox/settings và route an toàn, rồi chạy UI ở phone/w600dp light/dark. Trong lượt này người dùng duyệt thêm hàng Kết quả hồ sơ chủ sân và hàng Thông báo hệ thống để bao phủ đủ 19 type; chưa duyệt schema delivery.

Đã chuyển repository sang SessionManager, thêm DI, ViewModel, Fragment, adapter, XML và navigation. Kiểm thử máy thật phát hiện loading vẫn true sau tải thành công; đã sửa và thêm ca hồi quy. Đối chiếu ảnh giúp sửa inset, khoảng cách/icon và tint trong dark mode. Route có source/server access check; phần chưa đủ contract dùng title/body đã lưu. Phát hiện producer admin ghi createdAt dạng chuỗi ISO, trái contract Timestamp; ghi lại vấn đề backend và giữ nguyên schema.

Kết quả cuối: 1.666 test JVM, 0 failure/error, 2 ca DataStoreSessionPrefsTest bị bỏ qua theo điều kiện Windows đã có; 21 test notification đều đạt. Build/lint đạt, 0 lỗi và 856 cảnh báo lint. Trên vivo V2247 với package TEST riêng, 32/32 ca UI đạt qua 393dp và 720dp ở light/dark; lưu 20 ảnh, log và hash tại specs/100-notification/validation/. Đã khôi phục density 440, không sửa ứng dụng chính hoặc deploy backend. Tasks đạt 19/56; T035 và T039 vẫn ghi phần còn thiếu, các quyết định backend/schema giữ mở.

## Routing, quyền hệ thống và tính toàn vẹn producer

Prompt công việc: tiếp tục Feature 100 từ Android continuation và báo cáo validation; ưu tiên routing còn thiếu, POST_NOTIFICATIONS, kiểm thử xóa tài khoản/index và sửa producer ghi createdAt thành chuỗi. Chuẩn bị đề xuất migration và delivery có receipt từng token; không chạy migration thật hoặc tự duyệt schema.

Đã thêm route REVIEW_REMINDER và REVIEW_REPLIED với kiểm tra nguồn/quyền từ server. Người dùng duyệt chuẩn hóa producer sang REVIEW_REPLIED và đọc/routing tương thích REVIEW_REPLY cũ. Các route hồ sơ chủ sân, owner review, promotion và group còn thiếu contract được giữ fallback; không tự đặt identifier hoặc quyền mới.

Đã bổ sung xin quyền Android 13+ khi bấm nút trong cài đặt. Từ chối vẫn dùng inbox/preference, nút chuyển sang cài đặt hệ thống và cập nhật trạng thái khi quay về. Bộ kiểm thử máy thật được sửa sau lỗi ActivityScenario lồng nhau và nhận diện package cài đặt riêng của vivo. Ảnh chụp được kiểm tra trực tiếp; ảnh rơi giữa animation được chụp lại sau khi chuyển màn hoàn tất.

Ba producer admin được sửa riêng notification.createdAt sang Timestamp. Bốn ca kiểm thử handler xác nhận kiểu dữ liệu và retry không nhân đôi/reset isRead. Planner chuyển đổi dữ liệu cũ chỉ đọc snapshot offline và xuất kế hoạch cập nhật riêng createdAt với precondition updateTime; không có chức năng commit. Emulator tái hiện thứ tự sai khi trộn chuỗi/Timestamp và xác nhận chuyển riêng trường thời gian khôi phục thứ tự, giữ nội dung/trạng thái đọc.

Kiểm chứng: 1.671 test JVM, không failure/error, hai skip Windows cũ; 26 test notification đạt. Deno: delete-account 21/21, producer/admin/planner 28/28, review reply 5/5. Firestore Rules/query 28/28; UI regression phone/light 8/8, permission 2/2. Build và lint đạt, không tắt detector hay bỏ test mới. T009 được xác nhận từ contract Broadcast hiện có của Feature 120; T050/T054/T055 hoàn thành, tổng 23/56. Không thay ma trận UI cũ vì bố cục không đổi. Job/receipt schema, event identity/transaction handoff và các quyết định backend còn thiếu vẫn chờ duyệt. Bằng chứng cuối lưu tại specs/100-notification/validation/continuation-2026-11-22.md.
