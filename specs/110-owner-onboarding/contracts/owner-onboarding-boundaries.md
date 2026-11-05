# Ranh giới tích hợp: Đăng ký làm chủ sân

## Các quyết định được duyệt (2026-11-05)

Các lựa chọn dưới đây được chủ dự án xác nhận theo Design Decision Review. Chúng định hướng thiết kế; các mục “Cần xác nhận tiếp” vẫn là blocker, không được tự suy diễn trong implementation.

### T001 — Application lifecycle: phương án C

- Một document đại diện cho mỗi chu kỳ xét duyệt.
- Nộp lần đầu khi `ownerStatus=NONE` tạo application document mới.
- Resubmit chỉ từ `REJECTED` và cập nhật cùng document của chu kỳ đó; ghi nhận lần gửi lại bằng `submittedAt` mới. Hồ sơ `PENDING` là chỉ đọc và không nhận submit mới.
- Sau revoke, application `APPROVED` cũ được giữ; chu kỳ mới bắt đầu bằng document mới khi `ownerStatus=NONE`.
- `users.ownerStatus` là trạng thái hiện hành phía server. Khi `PENDING`/`REJECTED`, truy vấn application theo UID và `submittedAt` giảm dần bằng index hiện có; dữ liệu application phải nhất quán với trạng thái user.
- **Lịch sử:** lưu hồ sơ đã duyệt qua các chu kỳ; các lần resubmit trong cùng chu kỳ cập nhật hồ sơ bị từ chối, không giữ snapshot riêng từng lần từ chối.

### T002 — Authentication và Storage: phương án B

- Dùng Firebase ID token hiện có để gọi Supabase/Edge Functions. Server xác minh Firebase UID; client không gửi role hay trạng thái làm căn cứ quyền.
- Trước khi được duyệt, cả giấy tờ và ảnh venue của application được upload vào bucket private `owner-docs`, trong namespace UID/application; chỉ lưu object path.
- `sign-upload` chỉ cấp quyền cho UID đã xác minh và path thuộc application/lifecycle được phép. `sign-download` chỉ cấp signed URL cho evidence path được application tham chiếu và đúng applicant/Admin; thời hạn tối đa 5 phút.
- Khi Feature 120 duyệt hồ sơ, luồng review promote các ảnh venue từ private staging sang bucket public `venue-photos`; venue mới chỉ nhận public paths. Giấy tờ evidence tiếp tục private.
- Bucket giới hạn MIME `image/jpeg`, `image/png`, `image/webp` và tối đa 2 MB/tệp; giới hạn chung tối đa 5 ảnh venue/cơ sở theo Database README.
- **Không thêm bucket hoặc collection.** Tạm thời `venueDraft.photoPaths` tham chiếu private staging paths cho đến khi review promote ảnh. Feature 120 phải áp dụng rõ phép chuyển này khi tạo venue.

### T003 — Retry/idempotency: phương án B

- Dùng Firestore transaction phía server để đồng bộ application với `users.ownerStatus`; không dựa vào khóa nút hoặc client read-before-write.
- Application ID được sinh một lần cho chu kỳ nộp, giữ lại qua retry và dùng lại khi resubmit hồ sơ `REJECTED`. Server xác minh UID, trạng thái và application trước khi tạo/cập nhật.
- `NONE` → tạo application và chuyển user sang `PENDING` nguyên tử. `REJECTED` → cập nhật cùng application và chuyển user sang `PENDING` nguyên tử. `PENDING`/`APPROVED` → không tạo application cạnh tranh.
- Retry cùng application ID phải đọc trạng thái authoritative và trả kết quả đã commit nếu request đã thành công; trạng thái hoặc nội dung đổi bởi thiết bị/Admin khác phải trả conflict, giữ draft client.
- Không thêm idempotency/version field ở bước này. Firestore REST helper cần có transaction/read-your-writes/precondition hỗ trợ trước khi tích hợp.
- **Cần xác nhận tiếp:** ánh xạ application sang venue ID và recovery/idempotency của claim, venue, notification trong Feature 120; xem mục cuối.

### T004 — Draft và cleanup: phương án B

- Draft biểu mẫu được phục hồi từ app-private local state; không lưu draft nghiệp vụ chưa gửi vào Firestore. Object staging nằm trong `owner-docs`.
- Feature 110 dọn best-effort các tệp đã biết khi người dùng hủy hoặc khi upload/thay thế hoàn tất; server sweeper dọn object không còn được application tham chiếu sau một grace period được phê duyệt.
- Xóa tài khoản tiếp tục theo Feature 011 contract: helper của nhóm D xóa mọi `ownerApplications` của UID và prefix `owner-docs/{uid}/` thông qua `delete-account`/sweeper.
- Feature 120 chịu trách nhiệm promote ảnh venue lúc duyệt; không xóa evidence lịch sử theo quyết định duyệt/từ chối.
- **Cần xác nhận tiếp:** thời gian giữ draft local và grace period cho object mồ côi.

### T005 — Dependencies: phương án A

- Không thêm dependency cho MVVM, Hilt, Coroutines/Flow, Navigation, Material, Firebase, Supabase, picker hay test framework; các thành phần đó đã có trong version catalog/app hiện tại.
- Map picker phải dùng chung map provider mà Feature 021 xác nhận. Không thêm SDK bản đồ trước khi provider/billing được xác định.

## Các điểm cần xác nhận tiếp sau khi duyệt phương án

### Các quyết định bổ sung được duyệt (2026-11-05)

- **Venue identity/reapproval:** dùng `applicationId` làm `venueId` của venue đầu tiên để retry approval không tạo trùng. Sau revoke, chu kỳ application mới khi được duyệt sẽ tạo venue mới; không nối ngầm với venue cũ. Không thêm trường schema để lưu liên kết.
- **Partial approval recovery:** review promote ảnh trước; sau đó một Firestore transaction tạo venue và cập nhật application/user; chỉ sau commit mới cập nhật Firebase custom claim rồi phát notification. Các bước ngoài transaction phải retry idempotent; lỗi trước commit có thể để lại object staging/promoted không được tham chiếu để sweeper xử lý.
- **Retention:** server sweeper chỉ xóa object staging không được application tham chiếu sau 30 ngày. Draft cục bộ được giữ đến khi người dùng xóa, đăng xuất hoặc xóa tài khoản; không tự xóa sau submit để hỗ trợ xem lại hồ sơ bị từ chối.

Các quyết định về luồng review là ranh giới Feature 110; Feature 120/100 cần đối chiếu và áp dụng cùng thứ tự/retry khi triển khai. Tài liệu hiện tại chưa định nghĩa API hoặc cơ chế reconciliation cụ thể cho custom claim và notification.

### Tạm hoãn — map provider

Chưa chọn Google Maps hay osmdroid. Chưa thêm SDK và chưa triển khai map picker phụ thuộc provider; Feature 021 cần xác nhận lựa chọn và điều kiện billing/cấu hình trước.

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
| Bucket riêng `owner-docs` và signed download ngắn hạn | Chính sách Storage dùng chung; README nêu `sign-download` | Theo T002-B đã duyệt: evidence path được tham chiếu chỉ cấp cho applicant/Admin; signed URL tối đa 5 phút. |
| `sign-upload` | README có nêu tên hàm; chưa có triển khai | Theo T002-B đã duyệt: Edge Function xác minh Firebase UID và chỉ cho upload vào namespace application/lifecycle hợp lệ trong private `owner-docs`. |
| `review-owner-application` | Feature 120 | Admin quyết định hồ sơ, cập nhật user/claim, tạo venue từ `venueDraft` và gửi thông báo. Feature 110 không triển khai phần này. |
| Thông báo `OWNER_APPROVED` / `OWNER_REJECTED` | Feature 100 / luồng xét duyệt | Người nộp vẫn xem được trạng thái trong app kể cả khi bỏ lỡ thông báo. |

## Trạng thái các quyết định

T001–T005 và ba quyết định bổ sung về venue identity, recovery và retention đã được chốt. Map provider được tạm hoãn. Các endpoint/payload, mã lỗi và cơ chế reconciliation cụ thể phải được ghi vào contract API/review riêng trước khi Android/backend cùng tích hợp; không suy diễn chúng từ sơ đồ ranh giới này.
