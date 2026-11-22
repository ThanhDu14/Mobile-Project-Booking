# Gộp main vào nhánh owner — 2026-11-20

Gộp main tại fc924e37a42b7d687ac3c45c644986eb01bb5fa9 vào fix-feature/11-owner tại f6276057cb15a9d4dd6abafd40f1a4b181430588. Git đã stage các thay đổi và giải quyết conflict trước khi nhận yêu cầu hoàn tất merge. Chỉ Rules và tài liệu database được cả hai nhánh sửa so với merge base; kiểm tra giữ phần community cùng luồng owner/admin.

Giữ đủ 44 index và 7 field override, không mất mục nào của hai nhánh. Luồng Thêm cơ sở, private owner-docs và chặn tạo venue từ client còn nguyên. Admin preview bài/tin nhắn được giữ; community thêm quyền đọc theo membership. Backend group-manage chặn xóa nhóm LOCKED, group-post không cho nhóm hiện lại bài bị SYSTEM ẩn.

Ba job CI chạy local: Android BUILD SUCCESSFUL (1538 test, 2 skipped, 0 lỗi); TypeScript check pass và Edge 472 passed/0 failed; Rules 289 passed/4 skipped theo cấu hình CI. Chi tiết tại results.json và các log kèm theo. Đây không phải kết quả GitHub Actions.

Lượt Edge đầu có 9 lỗi do fixture cũ trong namespace demo-owner-concurrency còn từ lượt chạy trước (ALREADY_EXISTS và slot đã tồn tại). Chỉ dọn namespace emulator đó rồi chạy lại toàn bộ suite; không thay đổi mã nguồn để né lỗi.

Không chạy lại máy thật sau bản merge và không deploy hosted trong lượt này. Bằng chứng máy thật của luồng Thêm cơ sở trước merge nằm ở ../venue-addition-2026-11-20/.
