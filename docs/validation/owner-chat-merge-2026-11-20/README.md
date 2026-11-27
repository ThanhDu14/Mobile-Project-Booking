# Gộp chat 091 vào nhánh owner — 2026-11-20

Gộp main tại 17fe46858011e35eec69a01c900ff315668f8f4c vào fix-feature/11-owner tại 75706b4b6ed93101978ee370b6532b5d692ffd77. Git đã giải quyết và stage các conflict trước lượt kiểm tra này; hoàn tất merge sau kiểm chứng. Hai nhánh cùng sửa Rules và tài liệu database so với merge base. Giữ luồng owner/admin cùng bổ sung chat 091, đồng bộ thành viên nhóm/chat và điều hướng deep link khi mở ứng dụng lạnh.

Giữ đủ 46 index và 9 field override của hai nhánh. Handler Thêm cơ sở, owner-docs, admin review/moderation không thay đổi. Rules chat giữ quyền đọc thành viên và get từng tin cho admin kiểm duyệt; client không ghi message. Nhóm LOCKED vẫn bị chặn xóa tại backend.

Ba job CI local: Android BUILD SUCCESSFUL (1628 test, 2 skipped, 0 lỗi); TypeScript check pass, Edge 499 passed/0 failed; Rules 304 passed/4 skipped theo cấu hình workflow. Log kèm theo và results.json là bằng chứng của bản merge. Dọn namespace fixture demo-owner-concurrency trước khi chạy để không dùng dữ liệu test cũ.

AI_log_4 được giữ nguyên theo yêu cầu. Không chạy lại máy thật sau bản merge, không deploy hosted và không push trong lượt này. Ảnh trong specs/091-chat/evidence là bằng chứng từ nhánh được gộp, không phải lượt kiểm thử mới ở đây. Các kiểm tra trên không phải kết quả GitHub Actions.
