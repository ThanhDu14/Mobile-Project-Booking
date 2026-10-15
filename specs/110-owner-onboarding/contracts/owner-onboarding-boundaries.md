# Ranh giới tích hợp: Đăng ký làm chủ sân

Đây là sơ đồ ranh giới trách nhiệm, không phải hợp đồng API. Tài liệu dự án chưa quy định endpoint HTTP, JSON request/response hoặc cách ứng dụng Android gọi Supabase. Không suy diễn tên endpoint hay trường payload từ tài liệu này.

## Ranh giới ứng dụng người nộp

Luồng người nộp cần các khả năng logic sau:

1. Đọc trạng thái owner hiện tại của người dùng đã xác thực và hồ sơ/lý do từ chối phù hợp.
2. Đọc hồ sơ thuộc người nộp để xem lại/nộp lại khi có quyền.
3. Tải ảnh venue và giấy tờ đã chọn, nhận về đường dẫn object.
4. Gửi hồ sơ mới hoặc nộp lại hồ sơ bị từ chối; kết quả cần đối chiếu được khi phản hồi bị mất.
5. Yêu cầu quyền truy cập ngắn hạn cho giấy tờ riêng tư của chính mình.

Hợp đồng cuối cùng phải quy định việc truyền/làm mới Firebase ID token, nguồn kiểm tra quyền, quyền upload, quy ước đường dẫn object, giới hạn tệp, thời hạn URL, kết quả idempotency/xung đột, lỗi có kiểu và quy tắc thử lại. Hợp đồng không được cho phép client gửi `role`, `ownerStatus`, `status`, `rejectReason`, `reviewedBy` hoặc `reviewedAt` như dữ liệu đáng tin cậy.

## Trách nhiệm backend dùng chung hiện có

| Trách nhiệm hiện có | Đơn vị phụ trách | Tương tác với Feature 110 |
|---|---|---|
| Dữ liệu hồ sơ/user trên Firestore và Rules | Firebase/Firestore | Người nộp đọc dữ liệu của mình, chỉ ghi khi Rules hiện có cho phép. Trường trạng thái/vai trò được bảo vệ vẫn do máy chủ quản lý. |
| Bucket riêng `owner-docs` và signed download ngắn hạn | Chính sách Storage dùng chung; README nêu `sign-download` | Người nộp đọc giấy tờ của mình; quyền admin thuộc Feature 120. |
| `sign-upload` | README có nêu tên hàm; chưa có triển khai | Chưa rõ thao tác cụ thể và có cho người chưa được duyệt tải lên hay không. |
| `review-owner-application` | Feature 120 | Admin quyết định hồ sơ, cập nhật user/claim, tạo venue từ `venueDraft` và gửi thông báo. Feature 110 không triển khai phần này. |
| Thông báo `OWNER_APPROVED` / `OWNER_REJECTED` | Feature 100 / luồng xét duyệt | Người nộp vẫn xem được trạng thái trong app kể cả khi bỏ lỡ thông báo. |

## Các quyết định hợp đồng cần chốt trước khi triển khai

- Chọn và ghi lại cách ứng dụng Android ký/tải tệp cùng cách truyền Firebase token sang Supabase.
- Định nghĩa quyền upload tối thiểu cho ảnh `venueDraft.photoPaths`; policy hiện tại của `venue-photos` chỉ mô tả owner đã duyệt.
- Định nghĩa thao tác gửi/nộp lại, cách định danh hồ sơ hiện hành, xử lý thử lại/idempotency và tính nhất quán giữa hồ sơ với `users.ownerStatus`.
- Định nghĩa phản hồi xung đột khi admin hoặc thiết bị khác đổi trạng thái trong lúc sửa biểu mẫu.
- Xác định signed URL tải giấy tờ do người nộp yêu cầu trực tiếp hay qua thao tác máy chủ đã xác thực; giữ thời hạn 5 phút.
