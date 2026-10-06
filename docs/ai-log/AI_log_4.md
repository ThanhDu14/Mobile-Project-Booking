# Nhật ký sử dụng AI – Thành viên 4 (Minh Nhựt)

Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| \#  | Ngày       | Nội dung                                                          | Nhóm |
| --- | ---------- | ----------------------------------------------------------------- | ---- |
| 1   | 2026-10-06 | Viết và làm rõ spec 100 (thông báo) bằng AI và `$speckit-clarify` | 10   |

---

## Mục 1: Viết và làm rõ spec 100 (thông báo)

| Trường                   | Nội dung                                                                                                                                           |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-06                                                                                                                                         |
| Người thực hiện          | Minh Nhựt                                                                                                                                          |
| Nhóm tính năng liên quan | 10 – Thông báo (spec `specs/100-notification`)                                                                                                     |
| Công cụ AI               | Codex (GPT-6), skill `$speckit-clarify`                                                                                                            |
| Mục đích                 | Đối chiếu tài liệu dự án, soạn đặc tả nghiệp vụ cho Notification và làm rõ các quyết định ảnh hưởng tới người nhận, nhắc lịch và cài đặt thông báo |
| Nhánh Git                | `feature/10-notification`                                                                                                                          |

### Prompt đã dùng

**Yêu cầu viết spec**:

```
Đọc và tuân thủ team-contract.md, feature-workflow.md,
w1-w2-spec-assignment.md, tài liệu liên quan đến Feature 10,
cấu trúc specs/ và docs/features/10-notification/.

Tôi đang làm Feature 10 – Notification cho dự án mobile app đặt sân cầu lông.

Hãy đọc và tuân thủ các tài liệu sau trong repository trước khi làm:
- team-contract.md
- feature-workflow.md
- w1-w2-spec-assignment.md
- các tài liệu liên quan đến Feature 10
- cấu trúc specs/ hiện tại
- docs/features/10-notification/ nếu có

Vai trò của tôi là Member D và tôi phụ trách Feature 10 – Notification.

Nhiệm vụ hiện tại CHỈ là SPECIFY, chưa được PLAN, TASKS hoặc IMPLEMENT.

Hãy tạo/cập nhật đặc tả tại:
specs/100-notification/spec.md

Yêu cầu:
1. Bám sát yêu cầu và terminology của repository, không tự ý thêm chức năng ngoài phạm vi.
2. Xác định rõ:
   - mục tiêu của Notification
   - actor/user liên quan
   - user stories
   - functional requirements
   - notification types/events nếu tài liệu dự án có quy định
   - behavior khi người dùng nhận, đọc/chưa đọc notification
   - acceptance scenarios
   - edge cases
   - các ràng buộc cần thiết
3. Nếu repository chưa cung cấp đủ thông tin cho một yêu cầu thì KHÔNG tự bịa. Đánh dấu phần cần clarification.
4. Kiểm tra các feature khác để phát hiện dependency/cross-feature requirement, đặc biệt với booking, owner và admin.
5. Giữ phạm vi ở mức đặc tả nghiệp vụ; chưa thiết kế implementation chi tiết, chưa viết code.
6. Sau khi viết xong, tự kiểm tra spec theo tiêu chí:
   - không mâu thuẫn với tài liệu nhóm
   - requirements có thể kiểm thử
   - acceptance scenarios rõ ràng
   - không có requirement mơ hồ nếu repository đã có đủ thông tin.

Sau khi hoàn thành, hãy báo:
- file đã tạo/cập nhật
- những requirement chính
- những điểm còn thiếu thông tin cần tôi clarify
- những dependency với feature khác
```

**Lệnh làm rõ**:

```
$speckit-clarify
```

Feature context được chọn bằng `SPECIFY_FEATURE_DIRECTORY=specs/100-notification`.
Spec Kit không yêu cầu tạo lại spec để chọn feature hiện có.

### Tóm tắt phản hồi của AI

- Đọc các quy định phát triển tính năng, phân công tuần 1–2, README Feature 10, đặc tả CSDL chung, constitution và các spec liên quan.
- Tạo/cập nhật `specs/100-notification/spec.md` bằng tiếng Việt, gồm mục tiêu, actors, user stories, requirements, danh mục notification type, edge cases, success criteria và dependencies.
- Dùng danh mục type và nguồn phát sinh trong đặc tả CSDL chung: `BOOKING_*`, `DROPIN_*`, `PROMOTION`, `OWNER_APPROVED`, `OWNER_REJECTED`, `REVIEW_REPLIED`, `CHAT_MESSAGE` và `SYSTEM`.
- Hoàn thành ba lượt `$speckit-clarify`; tổng cộng 14 câu hỏi được hỏi và trả lời, rồi ghi vào mục `Clarifications` trong spec.
- Bản đặc tả đầu tiên đã được tôi yêu cầu xóa. Sau đó tôi yêu cầu tạo lại spec theo đúng nội dung và giới hạn nghiệp vụ.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                        | Quyết định                                                                                                                    | Lý do                                                                                                                                                                                                  |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Nhắc lịch đặt sân                           | Dùng: một lời nhắc cho mỗi đơn `CONFIRMED`, gửi 2 giờ trước khung giờ bắt đầu sớm nhất                                        | Tôi chọn phương án A vì đơn đã xác nhận mới là đơn có lịch chơi chắc chắn, đồng thời chỉ gửi một lời nhắc giúp tránh gửi nhiều thông báo khi một đơn có nhiều khung giờ.                               |
| Cài đặt theo loại                           | Dùng: tắt một loại sẽ ngăn push nhưng vẫn lưu và hiển thị thông báo trong trung tâm                                           | Tôi chấp nhận đề xuất vì người dùng chỉ tắt kênh push của loại thông báo đó, nhưng vẫn cần có thể xem lại thông báo trong trung tâm.                                                                   |
| `BOOKING_CANCELLED`                         | Dùng: người đặt nhận khi chủ sân hoặc hệ thống hủy; không nhận khi tự hủy                                                     | Tôi chọn phương án A vì người đặt cần được thông báo khi việc hủy đến từ chủ sân hoặc hệ thống, còn khi tự hủy thì người dùng đã biết về hành động của mình.                                           |
| `DROPIN_CHANGED`                            | Dùng: chỉ người đã đăng ký buổi chơi nhận; người chỉ ở hàng chờ không nhận                                                    | Tôi chọn phương án A vì người đã đăng ký là người bị ảnh hưởng trực tiếp bởi việc đổi giờ hoặc hủy buổi chơi.                                                                                          |
| `SYSTEM`                                    | Dùng: tạo thông báo cho mọi tài khoản đang hoạt động, gồm người chơi và chủ sân; push tuân theo cài đặt và quyền hệ điều hành | Tôi chọn phương án A vì thông báo hệ thống có phạm vi toàn ứng dụng và tài liệu hiện tại chưa quy định cơ chế chọn nhóm người nhận riêng.                                                              |
| `PROMOTION`                                 | Dùng: chỉ follower đủ điều kiện sử dụng voucher nhận thông báo                                                                | Tôi chọn phương án B vì thông báo khuyến mãi nên hướng đến những người vừa theo dõi sân vừa có khả năng sử dụng voucher, tránh gửi thông báo không phù hợp.                                            |
| Đánh dấu đã đọc                             | Dùng: hỗ trợ đánh dấu từng thông báo và đánh dấu tất cả đã đọc                                                                | Tôi chọn phương án B vì người dùng cần có cả thao tác đọc từng thông báo và xử lý nhanh toàn bộ các thông báo chưa đọc.                                                                                |
| Thứ tự trung tâm                            | Dùng: thông báo mới nhất hiển thị trước                                                                                       | Tôi chọn phương án A vì người dùng thường cần ưu tiên các thông báo mới và các sự kiện vừa xảy ra.                                                                                                     |
| `CHAT_MESSAGE` khi người nhận đang xem chat | Dùng: vẫn tạo thông báo trong trung tâm nhưng không gửi push                                                                  | Tôi chọn phương án A vì tin nhắn vẫn cần được lưu trong lịch sử thông báo, nhưng không cần gửi push khi người dùng đang trực tiếp xem cuộc trò chuyện.                                                 |
| Mặc định cài đặt                            | Dùng: bật các loại nghiệp vụ cho tài khoản mới; tắt `PROMOTION`                                                               | Tôi chọn phương án B vì các thông báo nghiệp vụ liên quan trực tiếp đến hoạt động của tài khoản nên được bật mặc định, trong khi khuyến mãi có thể gây phiền nếu người dùng chưa chủ động bật.         |
| Mở thông báo có liên kết                    | Dùng: mở nội dung liên quan nếu còn khả dụng; nếu không, mở chi tiết thông báo từ nội dung đã lưu                             | Tôi chọn phương án A vì thông báo có liên kết nên đưa người dùng trực tiếp đến nội dung liên quan khi nội dung đó còn tồn tại, đồng thời vẫn cần có phương án dự phòng khi dữ liệu không còn khả dụng. |
| Sự kiện được xử lý lại                      | Dùng: không tạo bản trùng cho cùng sự kiện, người nhận và type                                                                | Tôi chọn phương án A vì cùng một sự kiện không nên tạo nhiều thông báo giống nhau cho cùng một người nhận, tránh trùng lặp và spam.                                                                    |
| Xóa thông báo                               | Bỏ chức năng người dùng xóa thông báo khỏi trung tâm                                                                          | Tôi chọn phương án A vì CSDL chung tập trung vào trạng thái `isRead`, không cần bổ sung thêm quyền xóa dữ liệu thông báo ở phía người dùng.                                                            |
| Thời hạn lưu thông báo                      | Dùng: giữ đến khi tài khoản bị xóa; khi xóa tài khoản thì xóa thông báo theo spec 011                                         | Tôi chọn phương án A vì cho phép người dùng xem lại lịch sử thông báo trong suốt vòng đời tài khoản và đồng bộ với quy tắc xóa dữ liệu khi tài khoản bị xóa.                                           |
| Nguồn `CHAT_MESSAGE`                        | Sửa thành Feature 09                                                                                                          | Đối chiếu bảng CSDL: `CHAT_MESSAGE` thuộc nhóm nguồn của Feature 09, đồng thời chức năng chat được quản lý trong Feature 09 nên nguồn của loại thông báo này được xác định là Feature 09.              |

### Cách kiểm chứng

- Đối chiếu với `docs/team-contract.md`, `docs/guides/feature-workflow.md`, `docs/guides/w1-w2-spec-assignment.md`, `docs/features/10-notification/README.md`, `docs/design/database/README.md`, constitution và các spec liên quan.
- Rà lại 14 câu trả lời trong `Clarifications`, cùng các phần user stories, functional requirements, danh mục type, edge cases và entity để bảo đảm thống nhất.
- Xác nhận Feature 10 vẫn tập trung vào nghiệp vụ Notification; không tạo plan, tasks hoặc implementation.
- Không chạy test. Thư mục spec không có `checklists/requirements.md`.
- Các chi tiết kỹ thuật như retry khi FCM lỗi hoặc xử lý thay đổi của sự kiện nguồn sau khi thông báo đã tạo được để lại cho bước lập kế hoạch.
