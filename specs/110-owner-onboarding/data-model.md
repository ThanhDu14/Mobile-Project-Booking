# Mô hình dữ liệu: Feature 110 — Đăng ký làm chủ sân

Tài liệu này ánh xạ nghiệp vụ đã duyệt vào mô hình CSDL chung. Không định nghĩa trường mới hoặc chọn cách định danh hồ sơ. Các tên trường/kiểu dưới đây chỉ lấy từ `docs/design/database/README.md` hoặc đặc tả tính năng.

## Các thực thể hiện có trong phạm vi

### `users/{uid}` (thực thể dùng chung hiện có)

Các trường liên quan:

| Trường | Kiểu/giá trị hiện có | Cách dùng trong tính năng |
|---|---|---|
| `role` 🔒 | String: `PLAYER`, `OWNER`, `ADMIN` | Ngữ cảnh hiển thị/ủy quyền. Feature 110 không ghi trường này. |
| `ownerStatus` 🔒 | String: `NONE`, `PENDING`, `APPROVED`, `REJECTED` | Nguồn trạng thái chính theo spec 011/110. Chỉ luồng gửi/xét duyệt phía máy chủ được tin cậy mới cập nhật. |
| `ownerRevokedReason` 🔒 | String? | Account flow có thể hiển thị sau khi bị thu hồi quyền theo spec 011; không phải lý do từ chối hồ sơ. |

Quan hệ: UID Firebase đã xác thực xác định người nộp (`ownerApplications.uid`). `role` vẫn là `PLAYER` trong lúc chờ duyệt và sau khi nộp lại. Feature 120 đặt `APPROVED`/`OWNER` khi duyệt; khi thu hồi quyền, `ownerStatus` có thể trở lại `NONE` trong khi hồ sơ `APPROVED` cũ vẫn được giữ.

### `ownerApplications/{id}` (thực thể dùng chung hiện có)

| Trường | Kiểu/giá trị hiện có | Nguồn và cách dùng |
|---|---|---|
| `uid` | String | UID Firebase của người nộp. |
| `venueDraft` | Map: tên venue, địa chỉ, `districtId`, tọa độ, điện thoại venue, số sân, giờ mở cửa, `photoPaths` | Thông tin venue ban đầu và đường dẫn ảnh; Feature 120 tạo venue từ draft khi duyệt. Không bổ sung trường trong map này. |
| `representativeName` | String | Trường biểu mẫu người đại diện. |
| `representativePhone` | String | Trường biểu mẫu số điện thoại người đại diện. |
| `documentPaths` | Array | Đường dẫn object trong bucket riêng tư `owner-docs`; không lưu URL công khai. |
| `status` 🔒 | String: `PENDING`, `APPROVED`, `REJECTED` | Trạng thái xét duyệt. Client không được tự đặt/ghi đè. |
| `rejectReason` 🔒 | String?; bắt buộc và không rỗng khi từ chối | Hiển thị cho người bị từ chối; Feature 120 ghi trường này. |
| `reviewedBy` 🔒 | String? | Metadata xét duyệt admin; người nộp không ghi. |
| `reviewedAt` 🔒 | Timestamp? | Metadata xét duyệt admin; người nộp không ghi. |
| `submittedAt` | Timestamp | Thời điểm gửi nếu có. |

Quan hệ: người nộp chỉ đọc hồ sơ của mình theo quyền đã định nghĩa. Cách chọn hồ sơ hiện hành và xử lý nhiều hồ sơ lịch sử chưa được làm rõ; xem phần câu hỏi bên dưới. Không giả định mỗi UID chỉ có đúng một document suốt đời vì Feature 110 cho phép nộp mới sau khi bị thu hồi quyền và Feature 120 giữ hồ sơ đã duyệt.

### Object trong Storage (bucket hiện có)

| Bucket | Đường dẫn object | Cách dùng | Quyền truy cập |
|---|---|---|---|
| `owner-docs` (riêng tư) | `{uid}/{applicationId}/{filename}` | Ảnh giấy tờ, được tham chiếu bởi `documentPaths` | Chỉ người nộp và admin; signed URL hết hạn sau 5 phút. |
| `venue-photos` (công khai) | `{venueId}/{filename}` theo README | Ảnh venue, được tham chiếu bởi `venueDraft.photoPaths` | README hiện cho upload qua `sign-upload` sau khi kiểm tra owner đã duyệt; quyền upload của người nộp trước khi duyệt chưa được xác định. |

Giới hạn Storage: giấy tờ JPEG/PNG/WebP, tối đa 2 MB mỗi ảnh (Feature 110); bucket chung giới hạn ảnh tối đa 2 MB và cùng nhóm MIME. Feature 110 yêu cầu tối thiểu một ảnh venue và một giấy tờ. Firestore chỉ lưu path, không lưu URL đầy đủ.

## Trạng thái và thao tác người nộp

| `users.ownerStatus` | Ý nghĩa hồ sơ | Thao tác người nộp trong Feature 110 |
|---|---|---|
| `NONE` | Chưa có hồ sơ hiện hành (có thể còn hồ sơ `APPROVED` cũ sau thu hồi quyền) | Bắt đầu gửi hồ sơ mới nếu máy chủ xác nhận đủ điều kiện. |
| `PENDING` | Đang chờ xét duyệt | Chỉ xem trạng thái; không sửa hoặc rút. `role` vẫn là `PLAYER`. |
| `REJECTED` | Hồ sơ hiện tại bị từ chối, có `rejectReason` | Xem, sửa các trường người nộp sở hữu và nộp lại sau khi làm mới trạng thái. Không sửa trường review do máy chủ quản lý. |
| `APPROVED` | Hồ sơ được duyệt | Không tạo hồ sơ mới qua luồng này. `role=OWNER`; vận hành tiếp thuộc 111/112. |

Giá trị lưu của `ownerApplications.status` viết hoa (`PENDING`, `APPROVED`, `REJECTED`); nhãn trên giao diện có thể Việt hóa. Quan hệ giữa trạng thái này và `users.ownerStatus` do luồng gửi/xét duyệt đáng tin cậy duy trì, không phải chuyển trạng thái phía client.

## Quy tắc kiểm tra lấy từ đặc tả

- Các trường venue và người đại diện bắt buộc được nêu ở FR-002 và mục Validation của Feature 110; nội dung chỉ có khoảng trắng được xem là trống.
- Bắt buộc ghim vị trí; tọa độ phải trong phạm vi địa lý hợp lệ.
- Số sân là số nguyên dương; chưa có giới hạn tối đa.
- Giờ mở/đóng phải hợp lệ và đầy đủ; chưa quy định mở qua đêm hoặc ngày nghỉ.
- Số điện thoại venue và người đại diện phải hợp lệ; chưa quy định chuẩn hóa/độ dài cụ thể.
- Cần ít nhất một ảnh venue và một giấy tờ. Định dạng/kích thước giấy tờ như trên.
- Khi kiểm tra thất bại, không tạo hồ sơ đã gửi và giữ các giá trị hợp lệ trong biểu mẫu.

## Index và tác động schema

- README CSDL đã liệt kê index ghép `ownerApplications.status`, `submittedAt` cho hàng đợi hồ sơ admin. Chưa quy định index truy vấn hồ sơ hiện hành theo applicant.
- Kế hoạch không đề xuất collection, trường, enum hay index mới. Trước khi thêm index, xác nhận liệu truy vấn hiện có có thể xác định hồ sơ hiện hành sau nộp lại/thu hồi quyền hay không.

## Câu hỏi mô hình dữ liệu còn bỏ ngỏ

1. Ứng dụng xác định hồ sơ hiện hành theo UID thế nào khi vẫn lưu hồ sơ `APPROVED` cũ và người dùng được nộp mới sau thu hồi quyền?
2. Nộp lại cập nhật document bị từ chối hay tạo document cho lần nộp mới? Lịch sử xét duyệt được giữ ra sao?
3. Xóa tài khoản có xóa mọi hồ sơ và object Storage liên quan, kể cả lịch sử đã duyệt không? Nếu chỉ xóa được một phần thì xử lý thế nào?
4. Thao tác tin cậy nào nhận hồ sơ mới/nộp lại và cập nhật trường được bảo vệ? Schema chưa định nghĩa request ID, version hay hợp đồng ghi idempotent/xử lý đồng thời.
