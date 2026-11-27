# Log AI: tiếp tục transaction thông báo và kiểm thử điện thoại

| Trường | Nội dung |
|---|---|
| Ngày | 2026-11-24 |
| Người thực hiện | Thành viên phụ trách Feature 100, có Codex hỗ trợ |
| Nhóm tính năng liên quan | 10 – Thông báo; tích hợp 07/08/09 |
| Công cụ AI | Codex, speckit-implement |
| Mục đích | Triển khai producer đã duyệt, viết kiểm thử và xác minh trên emulator/điện thoại |

## Prompt đã dùng

Trích phần mở đầu của yêu cầu triển khai; toàn bộ phạm vi và kết quả được ghi trong implementation-review.md cùng validation/continuation-2026-11-24.md.

```text
$speckit-implement

Tiếp tục Feature 100 từ tiến độ thực tế trong repository. Mục tiêu là hoàn thành nhiều task còn lại nhất có thể và kiểm thử trực tiếp trên điện thoại thật đang kết nối.

Đọc tasks.md, phần cập nhật mới nhất trong implementation-review.md, backend-decision-package.md và các contract liên quan. Kiểm tra code và bằng chứng hiện có trước khi xác định phần còn thiếu.
```

Người dùng duyệt riêng D3, R1 và H1 sau khi xem gói quyết định. Các phê duyệt không bao gồm GROUP fanout/outbox, migration dữ liệu thật hay deploy. Người dùng cho phép bản kiểm thử cô lập, demo/fake data, instrumentation và screenshot trên điện thoại; cấm tự commit/push.

## Tóm tắt phản hồi của AI

Chuyển DROPIN_REGISTERED, review create/first reply và DIRECT chat sang enqueue cùng transaction nguồn, tái sử dụng shared writer/worker. Bổ sung review generation đã được duyệt và kiểm thử replay, recreate, rollback, người nhận, preference cùng retry từng token. Kiểm thử điện thoại phát hiện hàng cũ còn hiển thị trong hiệu ứng RecyclerView sau đăng xuất; đã sửa việc xóa/ẩn danh sách. Tách handler FCM để kiểm thử cùng đường xử lý bằng payload local. Không gọi FCM thật.

## Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Shared enqueue/worker và D3/R1/H1 | Dùng | Có phê duyệt rõ về identity và transaction |
| Callback sau commit tương ứng | Bỏ | Tránh tạo trùng và mất enqueue sau crash |
| GROUP callback hiện có | Giữ, chưa hoàn tất | Chưa được duyệt durable fanout/outbox |
| BOOKING_NEW payment source và các nguồn chưa chốt | Chưa triển khai thêm | Checkout thiếu nguồn hoặc contract cần xác nhận |

## Cách kiểm chứng

Backend fake, Auth/Firestore Emulator, Rules và Android JVM/build/lint; kiểm thử .adminlocal trên vivo bằng fake notification repositories/session và inject payload vào handler. Ma trận phone/w600dp light/dark, log, screenshot, các lần lỗi đã sửa và giới hạn môi trường được ghi tại specs/100-notification/validation/continuation-2026-11-24.md. Không dùng dữ liệu cá nhân, token thật, project thật, deploy hay migration thật. Task chỉ được đóng theo bằng chứng trong báo cáo đó.
