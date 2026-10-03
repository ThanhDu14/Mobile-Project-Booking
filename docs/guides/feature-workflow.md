# Quy trình phát triển một tính năng

Mỗi nhóm tính năng đi qua các bước sau, kết hợp [Spec Kit](https://github.com/github/spec-kit) với thư mục `docs/features/`.

1. **Cập nhật phạm vi:** kiểm tra lại `docs/features/NN-<nhom>/README.md`, chụp ảnh app tham khảo và lưu vào `screenshots/`.
2. **Tạo nhánh:** tạo `feature/NN-<ten>` từ `develop`.
3. **Viết spec:** chạy `/speckit-specify <mô tả>`. Spec Kit sẽ tạo `specs/NNN-<ten>/spec.md`.
4. **Làm rõ yêu cầu:** chạy `/speckit-clarify` (không bắt buộc).
5. **Lập kế hoạch:** chạy `/speckit-plan` để sinh `plan.md`, `data-model.md` và `contracts/`.
6. **Chia task:** chạy `/speckit-tasks`, sau đó có thể chạy thêm `/speckit-analyze` để kiểm tra độ khớp.
7. **Triển khai:** chạy `/speckit-implement`, hoặc tự code theo `tasks.md`.
8. **Cập nhật tài liệu:** dán link spec và cập nhật trạng thái trong `docs/features/README.md` và README của nhóm. Ghi log AI vào `docs/ai-log/`.
9. **Mở PR vào `develop`:** đối chiếu với checklist Definition of Done trong README của nhóm.

> Số thứ tự `NNN` trong `specs/` do Spec Kit tự tăng, nên có thể khác mã nhóm `NN`. Một nhóm tính năng lớn có thể chia thành nhiều spec (ví dụ `04-booking` → `005-booking-time-grid`, `006-weekly-recurring-booking`). Vì vậy mọi spec của một nhóm phải được liệt kê trong README của nhóm đó.
