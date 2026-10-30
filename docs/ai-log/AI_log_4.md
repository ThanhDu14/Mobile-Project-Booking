# Nhật ký sử dụng AI – Thành viên 4 (Minh Nhựt)

Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| \#  | Ngày       | Nội dung                                                                                                                                                                     | Nhóm |
| --- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1   | 2026-10-06 | Viết và làm rõ spec 100 (thông báo) bằng AI và `$speckit-clarify`                                                                                                            | 10   |
| 2   | 2026-10-07 | Viết spec 110 (đăng ký chủ sân) bằng `$speckit-specify` và làm rõ bằng `$speckit-clarify`                                                                                    | 11   |
| 3   | 2026-10-08 | Viết và làm rõ spec 111 (quản lý cơ sở, sân con, bảng giá và khóa giờ) bằng AI, `$speckit-clarify` và `$speckit-checklist`                                                   | 11   |
| 4   | 2026-10-08 | Viết và làm rõ spec 112 (vận hành cơ sở chủ sân) bằng AI và `$speckit-clarify`                                                                                               | 11   |
| 5   | 2026-10-08 | Viết và làm rõ spec 120 (quản trị, hồ sơ chủ sân và báo cáo) bằng `$speckit-specify` và `$speckit-clarify`                                                                   | 12   |
| 6   | 2026-10-15 | Lập implementation plan cho Feature 100 (thông báo) bằng `$speckit-plan`                                                                                                     | 10   |
| 7   | 2026-10-15 | Lập implementation plan cho Feature 110 (đăng ký chủ sân) bằng `$speckit-plan` và viết lại tài liệu bằng tiếng Việt                                                          | 11   |
| 8   | 2026-10-16 | Lập implementation plan cho Feature 111 (thiết lập cơ sở chủ sân) bằng `$speckit-plan`                                                                                       | 11   |
| 9   | 2026-10-16 | Lập implementation plan cho Feature 112 (vận hành chủ sân) bằng `$speckit-plan`                                                                                              | 11   |
| 10  | 2026-10-16 | Lập implementation plan cho Feature 120 (quản trị và kiểm duyệt) bằng `$speckit-plan`                                                                                        | 12   |
| 11  | 2026-10-19 | Tạo Tasks Feature 100 bằng `$speckit-tasks`, phân tích bằng `$speckit-analyze` và cập nhật Tasks theo kết quả phân tích                                                      | 10   |
| 12  | 2026-10-19 | Tạo Tasks Feature 110 bằng `$speckit-tasks`, rà soát bằng `$speckit-analyze` và cập nhật Tasks theo kết quả phân tích                                                        | 11   |
| 13  | 2026-10-19 | Tạo Tasks Feature 111 bằng `$speckit-tasks`, phân tích bằng `$speckit-analyze` và cập nhật Tasks theo báo cáo                                                                | 11   |
| 14  | 2026-10-19 | Tạo Tasks Feature 112 bằng `$speckit-tasks`, phân tích bằng `$speckit-analyze` và cập nhật Tasks theo các findings                                                           | 11   |
| 15  | 2026-10-19 | Tạo Tasks Feature 120 bằng `$speckit-tasks`, phân tích bằng `$speckit-analyze` và cập nhật Tasks theo findings U1–U3                                                         | 12   |
| 16  | 2026-10-29 | Implement Feature 111: validator, Firestore Rules, unit test và Emulator test bằng `$speckit-implement`                                                                      | 11   |
| 17  | 2026-10-29 | Tiếp tục implement Feature 111: repository, venue management, court management, UI và test bằng `$speckit-implement`                                                         | 11   |
| 18  | 2026-10-29 | Tiếp tục implement Feature 111: price coverage, slot UI shell, public availability assertions và kiểm thử cross-feature bằng `$speckit-implement`                            | 11   |
| 19  | 2026-10-29 | Tiếp tục implement Feature 111: price-rules Emulator tests, error/offline/conflict review, UI polish, boundary traceability và kiểm thử build/lint bằng `$speckit-implement` | 11   |
| 20  | 2026-10-29 | Tiếp tục implement Feature 111: hoàn thiện error/retry/conflict/cache freshness, responsive/accessibility review và kiểm chứng T034/T038 bằng `$speckit-implement`           | 11   |
| 21  | 2026-10-29 | Tiếp tục implement Feature 111: chốt typed write-gate cho T007, giữ an toàn T008/T011, hoàn thiện T037 và kiểm chứng Emulator/build/lint                                     | 11   |
| 22  | 2026-10-30 | Kiểm toán kỹ thuật & giải tỏa blocker Feature 111: chốt quyết định 1A, 2A, 3A, 4A, hoàn thiện Bảng giá (CRUD, UI, sync minPricePerHour) và điều hướng thêm venue             | 11   |
| 23  | 2026-10-30 | Triển khai Phase 3 Feature 111: Edge Function manage-slot-blocks, Calendar Grid UI, và Emulator race condition tests | 11   |

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

---

## Mục 2: Viết và làm rõ spec 110 (đăng ký chủ sân)

| Trường                   | Nội dung                                                                                                                                  |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-07                                                                                                                                |
| Người thực hiện          | Minh Nhựt                                                                                                                                 |
| Nhóm tính năng liên quan | 11 – Quản lý sân (chủ sân), onboarding chủ sân (spec `specs/110-owner-onboarding`)                                                        |
| Công cụ AI               | Codex (GPT-6), skill `$speckit-specify` và `$speckit-clarify`                                                                             |
| Mục đích                 | Đối chiếu tài liệu dự án, viết đặc tả đăng ký chủ sân và làm rõ các quyết định về giấy tờ, hồ sơ đang chờ và chỉnh sửa sau khi được duyệt |
| Nhánh Git                | `110-owner-onboarding`                                                                                                                    |

### Prompt đã dùng

**Yêu cầu viết spec**:

```
$speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/110-owner-onboarding

Người chơi muốn trở thành chủ sân chọn "Tôi là chủ sân" để đăng ký. Tài khoản ban đầu vẫn là người chơi. Người dùng điền hồ sơ cơ sở gồm: tên cơ sở, địa chỉ, vị trí ghim bản đồ (tọa độ), số sân, giờ mở cửa, người đại diện, số điện thoại; tải lên ảnh cơ sở và giấy tờ chứng minh. Sau khi nộp, ownerStatus = pending; trong lúc chờ duyệt, người dùng vẫn chỉ dùng app như người chơi và chưa được đăng hay quản lý sân. Người dùng xem được trạng thái hồ sơ (pending / approved / rejected) ngay trong app. Admin duyệt hoặc từ chối: nếu duyệt thì role chuyển thành owner, ownerStatus = approved và mở khóa chức năng quản lý sân; nếu từ chối thì ownerStatus = rejected kèm lý do bắt buộc, người dùng xem được lý do, sửa hồ sơ và nộp lại. Giấy tờ chứng minh là dữ liệu nhạy cảm, chỉ người nộp và admin được xem. Quyền phải được kiểm tra ở phía server/Security Rules, không chỉ ẩn trên giao diện. Có xử lý trạng thái đang tải, trống, lỗi validate, lỗi tải file, mất mạng khi nộp (không tạo hồ sơ trùng khi bấm nộp nhiều lần), và nộp lại sau rejected.

Không bao gồm: màn hình duyệt/từ chối của admin (spec 120-admin), đăng và quản lý sân sau khi được duyệt, thông báo đẩy (nếu chưa có spec riêng), đăng nhập và hồ sơ cá nhân (spec 010, 011).

Dữ liệu: users (role, ownerStatus), owner application/hồ sơ chủ sân và giấy tờ theo docs/design/database/README.md; quy tắc trong .specify/memory/constitution.md.

Yêu cầu bổ sung khi viết spec:
- Đọc và đối chiếu Proposal.html, constitution, hướng dẫn phân công W1–W2, database README, spec 010, 011, spec 120 nếu có, và tài liệu liên quan đến owner.
- Viết bằng tiếng Việt, giữ nguyên tên kỹ thuật.
- Chỉ mô tả WHAT/WHY; không viết implementation code, không chạy plan/tasks, không tạo data-model/plan/tasks.
- Có Actors, user stories, acceptance scenarios, trạng thái hồ sơ, functional requirements, validation, permission/security, error/empty/loading, offline/network failure, resubmission, dependency, edge cases và success criteria.
- Đối chiếu trạng thái “chưa nộp” với schema; tối đa 3 marker NEEDS CLARIFICATION.
- Nếu spec 120 chưa có, ghi điểm giao admin như giả định; không tự thiết kế phía admin.
```

**Lệnh làm rõ**:

```
$speckit-clarify
```

### Tóm tắt phản hồi của AI

- Đối chiếu Proposal, constitution, hướng dẫn phân công W1–W2, database README, spec 010 và 011, cùng README nhóm 11. Không tìm thấy `specs/120-admin/spec.md`.
- Tạo `specs/110-owner-onboarding/spec.md` và checklist `specs/110-owner-onboarding/checklists/requirements.md`.
- Dùng `ownerStatus = NONE` cho trạng thái chưa nộp theo schema; các trạng thái còn lại lưu theo enum `PENDING`, `APPROVED`, `REJECTED` và hiển thị tương ứng là pending, approved, rejected.
- Ghi rõ tài khoản giữ `role = PLAYER` khi chờ duyệt hoặc bị từ chối; chỉ sau khi được duyệt mới chuyển thành `role = OWNER`. Giấy tờ chỉ người nộp và admin được xem; quyền phải được kiểm tra phía server.
- Hoàn thành ba câu hỏi clarify và cập nhật câu trả lời vào spec:
  1. Giấy tờ chỉ nhận ảnh JPEG, PNG hoặc WebP, tối đa 2 MB mỗi tệp.
  2. Hồ sơ `PENDING` không được sửa hoặc rút; người nộp chờ quyết định, rồi nộp lại nếu bị từ chối.
  3. Chỉnh sửa vận hành theo spec 111 không cần duyệt lại; đổi người đại diện hoặc giấy tờ chứng minh cần admin duyệt lại.
- Sau clarify, checklist đạt 16/16 mục. Không chạy plan, tasks hoặc test.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                      | Quyết định                                                                                                | Lý do                                                                                       |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Trạng thái chưa nộp                       | Dùng `ownerStatus = NONE`                                                                                 | Database README đã định nghĩa enum `NONE`, không cần thêm trạng thái mới                    |
| Định dạng giấy tờ                         | Dùng JPEG, PNG hoặc WebP, tối đa 2 MB mỗi tệp                                                             | Đồng bộ giới hạn ảnh hiện có trong tài liệu CSDL và giữ giấy tờ trong vùng lưu trữ riêng tư |
| Sửa/rút hồ sơ đang `PENDING`              | Không cho sửa hoặc rút                                                                                    | Khớp bảng quyền CSDL chỉ cho cập nhật hồ sơ khi `REJECTED`; schema chưa có trạng thái rút   |
| Chỉnh sửa sau khi duyệt                   | Chỉnh sửa vận hành theo spec 111 không cần duyệt lại; đổi người đại diện hoặc giấy tờ cần admin duyệt lại | Tách thay đổi vận hành cơ sở khỏi thay đổi thông tin dùng để xác minh                       |
| Màn hình/quy trình admin                  | Không đưa vào spec 110; chỉ ghi điểm giao như giả định                                                    | Spec 120 chưa có và nằm ngoài phạm vi yêu cầu                                               |
| Plan, tasks, data-model và implementation | Không tạo                                                                                                 | Phạm vi công việc chỉ là specify và clarify                                                 |

### Cách kiểm chứng

- Đối chiếu trạng thái chưa nộp với enum trong database README: `NONE`, `PENDING`, `APPROVED`, `REJECTED`.
- Rà lại các yêu cầu về hồ sơ pending, nộp lại sau rejected, giấy tờ riêng tư, xác thực quyền phía server, lỗi tải tệp và mất mạng.
- Xác nhận spec không còn marker `[NEEDS CLARIFICATION]`; checklist đạt **16/16** sau clarify.
- Phát hiện Proposal mô tả chọn “Tôi là chủ sân” lúc đăng ký tài khoản, trong khi spec 010 đã chốt mọi tài khoản mới là người chơi và đăng ký chủ sân sau đăng nhập. Spec 110 bám theo quyết định mới hơn trong spec 010.
- Ghi nhận điểm cần đối chiếu với nhóm phụ trách CSDL: mục 4.2 đánh dấu `ownerApplications.status` chỉ server ghi, nhưng bảng quyền mục 7 cho phép người nộp cập nhật hồ sơ khi `REJECTED`. Không chỉnh tài liệu CSDL trong công việc này.
- Spec 120 chưa có; các hành vi duyệt/từ chối được ghi là dependency/giả định, không thiết kế giao diện admin.
- Bước tiếp theo: thống nhất điểm quyền ghi `ownerApplications` với nhóm phụ trách CSDL trong quá trình lập kế hoạch.

---

## Mục 3: Viết và làm rõ spec 111 (quản lý cơ sở và cấu hình sân)

| Trường                   | Nội dung                                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-08                                                                                                                                              |
| Người thực hiện          | Minh Nhựt                                                                                                                                               |
| Nhóm tính năng liên quan | 11 – Quản lý sân (chủ sân), spec `specs/111-owner-venue-setup`                                                                                          |
| Công cụ AI               | Codex (GPT-6), `$speckit-specify`, `$speckit-clarify`, `$speckit-checklist`                                                                             |
| Mục đích                 | Đối chiếu tài liệu dự án, viết đặc tả quản lý venue/court, cấu hình `priceRules`, khóa giờ; làm rõ các quyết định ảnh hưởng đến booking và dữ liệu CSDL |
| Nhánh Git                | `110-owner-onboarding`                                                                                                                                  |

### Prompt đã dùng

**Yêu cầu viết spec**:

```
$speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/111-owner-venue-setup

Chủ sân đã được admin duyệt có thể quản lý thông tin cơ sở và các sân thuộc cơ sở của mình. Chủ sân có thể thêm, sửa và ẩn cơ sở; quản lý các sân con thuộc cơ sở; cấu hình bảng giá theo khung giờ và theo thứ; đồng thời khóa các khung giờ của sân để phục vụ bảo trì, sự kiện hoặc khách đặt trực tiếp. Chỉ owner đã được duyệt mới được thực hiện các thao tác quản lý này.

Không bao gồm: đăng ký trở thành chủ sân và quy trình duyệt hồ sơ (spec 110-owner-onboarding), duyệt/từ chối booking, drop-in, voucher và thống kê vận hành (spec 112-owner-operations), màn hình/quy trình admin (spec 120-admin).

Dữ liệu liên quan: users (role, ownerStatus), venues, courts, priceRules và slots theo docs/design/database/README.md.

Đọc và đối chiếu Proposal.html, constitution, hướng dẫn W1–W2, database README, spec 010, 011, 110, các spec booking liên quan và spec 120 nếu có. Viết tiếng Việt; chỉ mô tả WHAT/WHY; không chạy plan/tasks/implement; không tạo data-model, plan hoặc tasks. Đặc tả cần có Actors, user stories, acceptance scenarios, functional requirements, validation, permission/security, error/empty/loading, offline/network failure, edge cases, dependencies và success criteria. Không tự thêm field hoặc trạng thái chưa có trong database README.
```

**Các lệnh tiếp theo**:

```
$speckit-clarify
$speckit-checklist
$speckit-clarify
```

### Tóm tắt phản hồi của AI

- Đối chiếu `docs/proposal/Proposal.html`, constitution, hướng dẫn W1–W2, database README và specs 010, 011, 110.
- Tạo/cập nhật `specs/111-owner-venue-setup/spec.md` bằng tiếng Việt, giữ nguyên thuật ngữ kỹ thuật và không viết implementation code.
- Làm rõ 9 câu hỏi ban đầu về tính giá nhiều slot, ẩn venue/court đang có booking, căn chỉnh khóa giờ theo slot 30 phút, khoảng thời gian qua nửa đêm, giá booking hiện có khi sửa rule, xóa `priceRules`, giờ hoạt động theo thứ, cách nhận biết rule đã dùng và cạnh tranh giữa thao tác khóa với booking; sau đó làm rõ thêm 3 câu còn lại theo checklist.
- Ba quyết định bổ sung: venue cần ít nhất một ảnh, tiện ích có thể để trống nhưng ID phải tồn tại, số điện thoại phải hợp lệ theo định dạng Việt Nam; tạm thời cấm xóa `priceRules` cho đến khi hợp đồng dữ liệu xác định được booking đã dùng rule; danh sách/lịch tải dưới 3 giây trên 4G, thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường.
- Checklist chất lượng yêu cầu tại `specs/111-owner-venue-setup/checklists/requirements.md` đạt **23/23** mục.
- Không chạy `$speckit-plan`, `$speckit-tasks`, `$speckit-implement` hoặc test.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                          | Quyết định                                                                                                                                | Lý do                                                                               |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Quyền quản lý                 | Chỉ owner có `role = OWNER`, `ownerStatus = APPROVED` và `venues.ownerId` khớp UID được quản lý dữ liệu của venue đó                      | Khớp constitution và ngăn owner sửa dữ liệu venue khác                              |
| Giá theo `priceRules`         | Tính từng slot 30 phút theo ngày/giờ; thiếu rule khớp thì booking không khả dụng                                                          | Không suy diễn `pricePerSlot` thành giá mỗi giờ                                     |
| Giá booking đã tạo            | Booking hiện có giữ giá đã ghi nhận; rule mới áp dụng cho booking mới                                                                     | Tránh thay đổi giá sau khi người chơi đã tạo booking                                |
| Ẩn venue/court                | Chặn booking mới nhưng giữ booking đã xác nhận                                                                                            | Bảo toàn cam kết với người chơi và lịch sử                                          |
| Khóa giờ                      | Khoảng khóa khớp ranh giới slot 30 phút; ghi `slots.status = BLOCKED` và `blockReason`                                                    | Đồng bộ với schema slot hiện có                                                     |
| Khóa và booking đồng thời     | Transaction được ghi nhận trước thành công; thao tác sau bị từ chối và tải lại trạng thái                                                 | Không để một slot vừa được booking vừa bị BLOCKED                                   |
| Khoảng thời gian qua nửa đêm  | Không cho một khoảng kéo qua nửa đêm; tách thành các khoảng thuộc hai ngày liên tiếp                                                      | Database README dùng ngày trong tuần và giờ `HHmm`, chưa định nghĩa khoảng qua ngày |
| Giờ mở cửa venue              | `openTime` và `closeTime` áp dụng giống nhau mọi ngày                                                                                     | Dùng schema hiện có, không tự mở rộng thành lịch theo thứ                           |
| Xóa `priceRules`              | Tạm thời cấm xóa; chỉ mở lại khi hợp đồng dữ liệu xác định được booking nào đã dùng rule. Thay đổi rule chỉ áp dụng cho booking mới       | Database README chưa định nghĩa liên kết booking–rule; không tự thêm field          |
| Điều kiện dữ liệu venue       | Cần ít nhất một ảnh; `amenityIds` có thể để trống nhưng ID đã chọn phải tồn tại; số điện thoại bắt buộc và hợp lệ theo định dạng Việt Nam | Chốt điều kiện tạo venue và kiểm tra dữ liệu                                        |
| Mục tiêu hiệu năng            | Danh sách và lịch tải dưới 3 giây trên mạng 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường          | Tạo tiêu chí đo được cho nghiệm thu                                                 |
| Plan, tasks và implementation | Không tạo hoặc chạy                                                                                                                       | Phạm vi công việc chỉ gồm specify, clarify và checklist                             |

### Cách kiểm chứng

- Đối chiếu schema venue, court, `priceRules`, `slots`, enum `BLOCKED` và `blockReason` trong database README.
- Rà quyền owner theo `role`, `ownerStatus` và `ownerId`; xác nhận spec không tự thêm schema.
- Kiểm tra các câu trả lời đã được ghi vào mục `Clarifications` và các phần Acceptance scenarios, Functional requirements, Validation, Edge cases được cập nhật nhất quán.
- Checklist chất lượng: **23/23** mục đạt.
- Không tìm thấy spec booking 040/041/050 hoặc spec admin 120 trong repo để đối chiếu hợp đồng chi tiết.
- Không chạy test, plan, tasks hoặc implementation.
- Việc xóa `priceRules` chỉ được mở lại sau khi nhóm booking/CSDL chốt và ghi nhận quan hệ sử dụng rule trong hợp đồng dữ liệu.

---

## Mục 4: Viết và làm rõ spec 112 (vận hành cơ sở chủ sân)

| Trường                   | Nội dung                                                                                                                                 |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-08                                                                                                                               |
| Người thực hiện          | Minh Nhựt                                                                                                                                |
| Nhóm tính năng liên quan | 11 – Quản lý sân (chủ sân), spec `specs/112-owner-operations`                                                                            |
| Công cụ AI               | Codex (GPT-6), `$speckit-specify`, `$speckit-clarify`                                                                                    |
| Mục đích                 | Đối chiếu tài liệu dự án, viết đặc tả vận hành cho owner đã được duyệt và làm rõ các quyết định về booking, drop-in, voucher và thống kê |
| Nhánh Git                | `feature/11-owner-specs`                                                                                                                 |

### Prompt đã dùng

**Yêu cầu viết spec**:

```text
$speckit-specify

Chủ sân đã được admin duyệt có thể vận hành cơ sở: duyệt hoặc từ chối booking, xem lịch tổng hợp ngày/tuần, mở buổi drop-in, xem đăng ký và check-in bằng QR, tạo voucher, xem thống kê doanh thu, tỷ lệ lấp đầy và khung giờ cao điểm.

Không bao gồm onboarding chủ sân (spec 110), thiết lập venue/court/giá/khóa giờ (spec 111) và quy trình admin (spec 120).

Đọc và đối chiếu Proposal.html, constitution, hướng dẫn W1–W2, database README, specs 110/111, các spec booking/drop-in/voucher liên quan và spec 120 nếu có. Viết tiếng Việt, giữ tên kỹ thuật và trạng thái hiện có. Chỉ mô tả WHAT/WHY; không viết code, không chạy plan/tasks/implement, không tạo data-model.md, plan.md hoặc tasks.md.

Chỉ owner có ownerStatus = approved được vận hành và chỉ trên venue/court thuộc quyền sở hữu của mình. Đối chiếu database trước khi dùng field hoặc trạng thái; ghi nhận mâu thuẫn thay vì tự sửa tài liệu khác.
```

**Lệnh làm rõ**:

```text
$speckit-clarify
```

### Tóm tắt phản hồi của AI

- Đối chiếu Proposal, constitution, hướng dẫn W1–W2, database README, specs 110 và 111, cùng README các nhóm voucher, drop-in và chủ sân.
- Tạo `specs/112-owner-operations/spec.md` với actors, user stories, acceptance scenarios, requirements, validation, permission/security, lỗi, offline, edge cases, dependencies và success criteria.
- Không tìm thấy specs booking, voucher, drop-in và admin được yêu cầu đối chiếu (`040/041/050/060/080/081/120`); giới hạn này được ghi nhận trong spec.
- Prerequisite của `$speckit-clarify` trỏ tới spec 110 do `.specify/feature.json` cũ. Giữ mục tiêu spec 112 theo ngữ cảnh; không sửa feature.json.
- Hai lượt clarify đã hỏi và ghi nhận tổng cộng 4 câu trả lời:
  - Owner xem danh sách đăng ký drop-in và check-in; không duyệt riêng từng người.
  - Báo cáo tách `DEPOSITED`, `PAID`, `REFUNDED`; `AT_VENUE` chỉ tính sau khi `paymentStatus = PAID`.
  - Booking `REFUNDED` được đếm riêng; chỉ hiển thị tiền hoàn nếu dữ liệu hiện có xác định được số tiền, không giả định hoàn toàn bộ.
  - Khung giờ cao điểm gom theo khoảng 60 phút trong `Asia/Ho_Chi_Minh`.
- Lượt `$speckit-clarify` tiếp theo không phát hiện câu hỏi người dùng nào cần hỏi thêm. Schema `dailyStats` vẫn là điểm cần thống nhất với người phụ trách CSDL.
- Checklist có 16/16 checkbox đạt. Phần Notes trong checklist vẫn giữ nội dung cũ nói hai câu hỏi clarify đang chờ trả lời.
- Không chạy plan, tasks, implementation hoặc test.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                      | Quyết định                                                                                               | Lý do                                                 |
| ----------------------------------------- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| Quyền owner                               | Chỉ owner có `role = OWNER`, `ownerStatus = APPROVED` và venue có `ownerId` khớp UID được vận hành       | Khớp constitution và ngăn truy cập dữ liệu venue khác |
| Duyệt booking                             | Chỉ chuyển `PENDING → CONFIRMED` hoặc `PENDING → REJECTED`; kiểm tra trạng thái booking và slot mới nhất | Khớp máy trạng thái trong database README             |
| Đăng ký drop-in                           | Owner xem danh sách, không duyệt từng người; check-in qua QR                                             | Schema không có trạng thái chờ owner duyệt            |
| Slot drop-in                              | Dùng slot 30 phút, `slots.status = BLOCKED`, `blockReason = DROP_IN`                                     | Khớp Feature 111 và database README                   |
| Voucher                                   | Owner chỉ tạo voucher gắn venue thuộc mình; không tự sửa `usedCount` hoặc redemption                     | Khớp quyền và schema voucher hiện có                  |
| Doanh thu                                 | Tách `DEPOSITED`, `PAID`, `REFUNDED`; chỉ tính `AT_VENUE` khi đã `PAID`                                  | Phân biệt tiền cọc, thanh toán đủ và hoàn tiền        |
| Tiền hoàn                                 | Chỉ hiển thị số tiền nếu dữ liệu hiện có xác định được; không suy đoán hoàn toàn bộ                      | Schema chưa biểu diễn số tiền hoàn một phần           |
| Khung giờ cao điểm                        | Xếp theo thời lượng booking xác nhận trong từng khoảng 60 phút theo `Asia/Ho_Chi_Minh`                   | Cung cấp cách gom nhất quán giữa các ngày             |
| Plan, tasks, data-model và implementation | Không tạo hoặc chạy                                                                                      | Phạm vi công việc chỉ là specify và clarify           |

### Cách kiểm chứng

- Đối chiếu schema và trạng thái `bookings`, `paymentStatus`, `slots`, `blockReason`, `vouchers`, `dropInSessions`, `registrations` và `dailyStats` trong database README.
- Kiểm tra câu trả lời clarify đã được ghi nhất quán trong `Clarifications`, Acceptance scenarios, Functional Requirements và Assumptions.
- Xác nhận spec không còn marker `[NEEDS CLARIFICATION]`.
- Checklist `specs/112-owner-operations/checklists/requirements.md`: **16/16** checkbox đạt;
- Ghi nhận database README chưa định nghĩa schema trường/chỉ số của `dailyStats`; cần thống nhất hợp đồng báo cáo với người phụ trách CSDL.

---

## Mục 5: Viết và làm rõ spec 120 (quản trị)

| Trường                   | Nội dung                                                                                                                                |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-08                                                                                                                              |
| Người thực hiện          | Minh Nhựt                                                                                                                               |
| Nhóm tính năng liên quan | 12 – Quản trị (spec `specs/120-admin`)                                                                                                  |
| Công cụ AI               | Codex (GPT-6), `$speckit-specify`, `$speckit-clarify`                                                                                   |
| Mục đích                 | Đối chiếu tài liệu dự án, viết đặc tả chức năng Admin và làm rõ các quyết định về duyệt hồ sơ, quyền owner, xử lý báo cáo và khóa venue |
| Nhánh Git                | `120-admin`                                                                                                                             |

### Prompt đã dùng

**Yêu cầu viết spec**:

```text
$speckit-specify Admin quản lý và kiểm duyệt các nội dung cần quyền quản trị trong hệ thống. Admin có thể xem danh sách hồ sơ đăng ký chủ sân, kiểm tra thông tin và giấy tờ chứng minh, sau đó duyệt hoặc từ chối hồ sơ. Khi từ chối phải có lý do để người dùng biết và có thể chỉnh sửa, nộp lại. Admin cũng có thể quản lý các báo cáo/vấn đề cần xử lý theo phạm vi được quy định trong proposal và các feature liên quan.

Không bao gồm: luồng người dùng đăng ký trở thành chủ sân và theo dõi hồ sơ (spec 110-owner-onboarding), quản lý venue/court/price rules của owner (spec 111-owner-venue-setup), các thao tác vận hành booking/drop-in/voucher của owner (spec 112-owner-operations), đăng nhập và hồ sơ cá nhân (spec 010, 011).

Dữ liệu liên quan: users, ownerApplications, venues và các dữ liệu quản trị/báo cáo theo docs/design/database/README.md.

Đọc và đối chiếu Proposal.html, constitution, hướng dẫn W1–W2, database README và các spec liên quan. Viết bằng tiếng Việt, chỉ mô tả WHAT/WHY, không viết implementation code. Không chạy plan/tasks/implement; không tạo data-model.md, plan.md hoặc tasks.md. Đặc tả cần có Actors, user stories, acceptance scenarios, trạng thái xử lý, functional requirements, validation, permission/security, error/empty/loading, offline/network failure, edge cases, dependencies và success criteria. Chỉ Admin được truy cập và thực hiện thao tác quản trị. Từ chối hồ sơ phải lưu lý do để người nộp xem và nộp lại; giấy tờ chỉ Admin và người nộp được xem. Không tự đặt field hoặc trạng thái ngoài database README; ghi nhận mâu thuẫn thay vì sửa tài liệu khác.
```

**Lệnh làm rõ**:

```text
$speckit-clarify
```

### Tóm tắt phản hồi của AI

- Đối chiếu Proposal, constitution, hướng dẫn W1–W2, database README, specs 010/011/110/111/112 và các feature liên quan.
- Tạo `specs/120-admin/spec.md` và checklist `specs/120-admin/checklists/requirements.md`.
- Spec bao gồm duyệt/từ chối owner application, quản lý tài khoản và venue, xử lý reports, thống kê, danh mục, system notification, permission/security, lỗi, offline, edge cases và success criteria.
- Các lượt `$speckit-clarify` đã ghi nhận 7 câu trả lời:
  1. Thu hồi quyền owner: `users.role = PLAYER`, `users.ownerStatus = NONE`; `ownerApplications.status` giữ `APPROVED`.
  2. Báo cáo “tin đăng” dùng `targetType = VENUE`.
  3. Report `VENUE` dùng `LOCK_VENUE`; `HIDE_CONTENT` dành cho nội dung.
  4. Duyệt hồ sơ tạo venue từ `venueDraft`; spec 111 quản lý thay đổi sau đó.
  5. `SYSTEM` notification gửi tới tất cả tài khoản đang hoạt động.
  6. Thu hồi quyền owner không tự đổi trạng thái venue; Admin khóa riêng khi cần.
  7. Admin có thể mở khóa venue `LOCKED` về `ACTIVE` hoặc `HIDDEN`; owner không được tự mở khóa.
- Không chạy `$speckit-plan`, `$speckit-tasks`, `$speckit-implement` hoặc test.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                    | Quyết định                                                                                                                                        | Lý do                                                                              |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Quy trình duyệt hồ sơ                   | Duyệt đặt `role = OWNER`, `ownerStatus = APPROVED`, tạo venue từ `venueDraft`; từ chối lưu lý do và đặt `role = PLAYER`, `ownerStatus = REJECTED` | Khớp database README và cho phép người nộp xem lý do, sửa và nộp lại theo spec 110 |
| Thu hồi quyền owner                     | Đặt `role = PLAYER`, `ownerStatus = NONE`; hồ sơ giữ `APPROVED`                                                                                   | Dùng enum hiện có và giữ lịch sử hồ sơ đã duyệt                                    |
| Báo cáo “tin đăng”                      | Ánh xạ sang `targetType = VENUE`                                                                                                                  | Database không có target type riêng cho tin đăng                                   |
| Hành động theo loại report              | `VENUE` dùng `LOCK_VENUE`; nội dung dùng `HIDE_CONTENT`                                                                                           | Khớp tập action đã có trong schema                                                 |
| Venue khi thu hồi quyền                 | Giữ nguyên trạng thái; Admin quyết định khóa riêng                                                                                                | Proposal mô tả thu hồi owner và khóa venue là các hành động tách biệt              |
| Mở khóa venue                           | Chỉ Admin được mở khóa về `ACTIVE` hoặc `HIDDEN`                                                                                                  | Owner không được tự mở venue đã bị Admin khóa                                      |
| System notification                     | Gửi đến tất cả tài khoản đang hoạt động                                                                                                           | Proposal chưa quy định nhóm nhận riêng                                             |
| Mâu thuẫn giữa database và spec 110/111 | Ghi nhận trong spec 120; tạo venue là tác động của duyệt hồ sơ, spec 111 quản lý cập nhật sau đó                                                  | Không tự sửa các tài liệu liên quan                                                |

### Cách kiểm chứng

- Đối chiếu các trường và trạng thái với database README; không thêm `targetType`, enum hoặc field mới.
- Kiểm tra câu trả lời đã được ghi vào `Clarifications` và các phần Acceptance Scenarios, trạng thái xử lý, Functional Requirements, Validation và Open Questions / Assumptions.
- Checklist chất lượng: **16/16** mục được đánh dấu đạt.
- `git diff --check` không báo lỗi định dạng.
- Ghi nhận còn thiếu chi tiết về thao tác quản lý danh mục, chỉ số thống kê và nội dung system notification trong tài liệu hiện có.
- Không chạy plan, tasks, implementation hoặc test.

---

## Mục 6: Lập implementation plan Feature 100 – Notification

| Trường                   | Nội dung                                                                                                                                 |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-15                                                                                                                               |
| Người thực hiện          | Minh Nhựt                                                                                                                                |
| Nhóm tính năng liên quan | 10 – Thông báo (spec `specs/100-notification`)                                                                                           |
| Công cụ AI               | Codex (GPT-6), `$speckit-plan`                                                                                                           |
| Mục đích                 | Đối chiếu spec, checklist, constitution, hướng dẫn W1–W2, CSDL và cấu trúc code để lập kế hoạch triển khai Notification, không viết code |
| Nhánh Git                | `feature/100-notification-plan`                                                                                                          |

### Prompt đã dùng

```text
$speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/100-notification

Lập Implementation Plan cho Feature 100 – Notification dựa trên specification hiện có.

Đọc và đối chiếu trước khi lập plan:
- specs/100-notification/spec.md
- specs/100-notification/checklists/requirements.md nếu tồn tại
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Các spec liên quan đến account/profile, booking, review/favorite, drop-in/waitlist, chat và admin nếu tồn tại
- Cấu trúc code và quy ước kỹ thuật hiện có

Xác định thành phần cần thay đổi cho notification center/inbox, đánh dấu đã đọc, preference theo type; tích hợp sự kiện và recipient từ feature nguồn; inbox, push, quyền OS và preference; schema/quyền truy cập hiện có; idempotency, booking reminder, chat, deep link/fallback; bảo mật, privacy, phân trang, lỗi, retry, kiểm thử và dependency.

Không thay đổi nghiệp vụ hoặc thiết kế lại feature nguồn; không tự bịa collection, field, trạng thái hay event contract. Ghi mâu thuẫn và điểm cần làm rõ. Chỉ tạo/cập nhật tài liệu bước Plan; không chạy tasks/implement, không viết code. Cuối cùng báo file, quyết định kiến trúc, thành phần, dependency, rủi ro và câu hỏi còn mở.
```

### Tóm tắt phản hồi của AI

- Đọc spec 100, checklist, constitution, hướng dẫn W1–W2, database README, các spec liên quan và cấu trúc Android/Firebase/Supabase hiện có.
- Tạo `plan.md`, `research.md`, `data-model.md`, `quickstart.md` và hai contract `contracts/event-integration.md`, `contracts/firestore-security.md` trong `specs/100-notification/`.
- Plan chọn Firestore path `users/{uid}/notifications/{id}` cùng các field hiện có; preferences và FCM tokens ở `users/{uid}`. Không thêm collection hoặc field mới.
- Kiến trúc dự kiến theo Android MVVM/package feature và Supabase Edge Functions/pg_cron; notification inbox được lưu độc lập với kết quả gửi FCM.
- Chưa chốt event transport/payload, idempotency key, trạng thái reminder, retry/backoff, presence của chat, page size/index, token lifecycle, định nghĩa account active và chi tiết deep link. Các điểm này được ghi trong `research.md` thay vì tự suy diễn.
- Ghi nhận mâu thuẫn quyền tạo `SYSTEM` notification: bảng phân quyền CSDL ghi Admin có thể create, trong khi §4.13 và các spec mô tả server-side creation. Cũng cần thống nhất DataStore trong constitution với `users.notificationPrefs` là nguồn chuẩn.
- Không chạy `$speckit-tasks`, `$speckit-implement` hoặc test; không tạo code. Không có `.specify/extensions.yml`, nên không có extension hook cần chạy.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                      | Quyết định                                                                                                | Lý do                                                              |
| ------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| Inbox và schema           | Giữ `users/{uid}/notifications/{id}` và các field đã có trong CSDL; client chỉ sửa `isRead`               | Khớp database README và FR-004/005; tránh tự thêm collection/field |
| Pipeline và feature nguồn | Feature nguồn cung cấp sự kiện; nhóm D xác thực recipient, ghi inbox và xử lý push phía server            | Giữ ownership nghiệp vụ và nguyên tắc bảo mật server-side          |
| Push và preferences       | Preference chỉ điều khiển push; PROMOTION tắt mặc định; BOOKING_NEW không tắt; OS permission là lớp riêng | Tuân thủ clarifications và FR-006–012                              |
| Reminder                  | Một reminder cho booking CONFIRMED, 2 giờ trước startAt sớm nhất                                          | Tuân thủ clarification/FR-010; không sửa state booking             |
| Idempotency/retry         | Ghi thành điểm cần làm rõ; không tự tạo event ID, delivery state hay outbox schema                        | CSDL/spec chưa xác nhận hợp đồng hoặc field để enforce và retry    |
| Quyền SYSTEM              | Ghi nhận xung đột giữa bảng quyền CSDL và mô tả tạo qua server; yêu cầu đồng bộ trước implementation      | Tránh tự mở quyền tạo từ client hoặc sửa spec của feature khác     |
| Tasks/implementation      | Không tạo hoặc chạy                                                                                       | Phạm vi công việc chỉ là Plan                                      |

### Cách kiểm chứng

- Đối chiếu mapping notification types và recipient với spec 100, database README và các spec nguồn; không sửa nghiệp vụ nguồn.
- Ghi rõ collection path, field, access rule và các điểm schema/index chưa được xác nhận trong `data-model.md`/contracts.
- Rà lại danh sách artifact: chỉ gồm tài liệu Plan; không tạo `tasks.md` hoặc code.
- Không chạy test hoặc lệnh implementation.

---

## Mục 7: Lập implementation plan Feature 110 – Đăng ký làm chủ sân

| Trường                   | Nội dung                                                                                                                                                                                 |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-15                                                                                                                                                                               |
| Người thực hiện          | Minh Nhựt                                                                                                                                                                                |
| Nhóm tính năng liên quan | 11 – Chủ sân (spec `specs/110-owner-onboarding`)                                                                                                                                         |
| Công cụ AI               | Codex (GPT-6), `$speckit-plan`                                                                                                                                                           |
| Mục đích                 | Đối chiếu spec 110, constitution, hướng dẫn W1–W2, database README, các spec 011/111/112/120 và cấu trúc code để lập kế hoạch onboarding chủ sân; chỉ tạo tài liệu Plan, không viết code |
| Nhánh Git                | `feature/110-owner-onboarding-plan`                                                                                                                                                      |

### Prompt đã dùng

```text
$speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/110-owner-onboarding

Lập Implementation Plan cho Feature 110 – Owner Onboarding dựa trên specs/110-owner-onboarding/spec.md.

Đọc và đối chiếu:
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Các spec account/profile, Feature 111, 112 và 120 nếu tồn tại
- Cấu trúc code và quy ước kỹ thuật hiện có

Plan cần xác định:
- Kiến trúc và thành phần cho nộp hồ sơ, tải giấy tờ, theo dõi trạng thái và nộp lại sau khi bị từ chối.
- Tích hợp với users, role, ownerStatus và quy trình admin duyệt.
- Phân quyền, bảo vệ giấy tờ nhạy cảm và xử lý lỗi/mất mạng/gửi trùng.
- Tác động dữ liệu, dependency và chiến lược kiểm thử.

Không bao gồm: triển khai quy trình admin, quản lý cơ sở/sân và các chức năng vận hành thuộc Feature 111, 112.

Không tự bịa schema hoặc thay đổi nghiệp vụ trong spec. Nếu phát hiện mâu thuẫn hoặc thiếu thông tin, ghi nhận rõ. Chỉ tạo tài liệu thuộc bước Plan; không chạy /speckit-tasks, /speckit-implement hoặc viết implementation code.

Cuối cùng báo cáo file đã tạo, quyết định kỹ thuật, dependency, rủi ro và điểm cần làm rõ.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec 110 với constitution, hướng dẫn W1–W2, database README, spec 011/111/112/120 và cấu trúc Android/Supabase hiện có.
- Tạo/cập nhật `plan.md`, `research.md`, `data-model.md`, `quickstart.md` và `contracts/owner-onboarding-boundaries.md` trong `specs/110-owner-onboarding/`; sau đó chuyển toàn bộ nội dung các tài liệu này sang tiếng Việt, giữ nguyên tên kỹ thuật và schema.
- Plan giữ Firestore làm nguồn dữ liệu nghiệp vụ; dùng bucket riêng tư `owner-docs` và signed URL có thời hạn; máy chủ quản lý `role`, `ownerStatus` và trạng thái xét duyệt. Tổ chức dự kiến theo MVVM/package `feature/owner`.
- Xác định Feature 011 là điểm vào/trình bày trạng thái; Feature 120 sở hữu duyệt/từ chối và tạo venue từ `venueDraft`; Feature 111/112 sở hữu quản lý và vận hành sau khi được duyệt; Feature 100 cung cấp loại thông báo kết quả.
- Ghi nhận các điểm cần làm rõ: định danh hồ sơ hiện hành và lịch sử khi nộp lại/sau thu hồi quyền; hợp đồng Android–Supabase và thao tác submit; quyền tải ảnh venue trước khi duyệt; dọn tệp mồ côi/xóa tài khoản; một số quy tắc xác thực và xử lý đồng thời.
- Ghi nhận app hiện là bộ khung: chưa có package theo feature, Hilt/Coroutines, thư viện Supabase, Edge Functions hoặc migration Storage liên quan.
- Không tạo `tasks.md`, không chạy `$speckit-tasks`, `$speckit-implement` hoặc test; không viết mã triển khai.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                          | Quyết định                                                                                                           | Lý do                                                                                                    |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Nguồn dữ liệu và trạng thái   | Dùng `users`, `ownerApplications` và bucket đã có; trạng thái/vai trò do máy chủ quản lý                             | Tuân theo database README và nguyên tắc bảo mật trong constitution; không thêm schema                    |
| Giấy tờ nhạy cảm              | Dùng bucket private `owner-docs`, path hiện có và signed URL hết hạn sau 5 phút                                      | Khớp database README và giới hạn riêng tư của giấy tờ                                                    |
| Nộp lại và hồ sơ lịch sử      | Chưa chọn cách dùng lại document hay tạo document mới; ghi thành điểm cần làm rõ                                     | Spec cho phép nộp mới sau thu hồi nhưng chưa định nghĩa cách tìm hồ sơ hiện hành và lưu lịch sử          |
| Tải ảnh venue trước khi duyệt | Ghi nhận policy hiện tại dành cho owner đã duyệt chưa đáp ứng rõ luồng applicant                                     | Feature 110 cần ảnh trong `venueDraft` trước khi duyệt; cần quyền upload riêng mà không nới policy chung |
| Hợp đồng Android–Supabase     | Chưa chọn SDK, REST hay cách tích hợp khác; chưa tự đặt endpoint/request schema                                      | Tài liệu dự án chưa quy định cách app truyền Firebase token hoặc hợp đồng upload/submit                  |
| Kiểm thử                      | Đề xuất unit test, Firebase Emulator, Supabase local và UI test                                                      | Theo constitution; ghi chú các dependency test còn thiếu trong app hiện tại                              |
| Ngôn ngữ tài liệu             | Viết lại các artifact Plan bằng tiếng Việt; giữ nguyên tên trường, enum, package và thuật ngữ kỹ thuật cần đối chiếu | Yêu cầu bổ sung để đồng nhất với tài liệu dự án                                                          |
| Tasks/implementation          | Không tạo hoặc chạy                                                                                                  | Phạm vi công việc chỉ gồm bước Plan                                                                      |

### Cách kiểm chứng

- Đối chiếu tên trường, trạng thái, bucket và quyền với database README cùng các spec liên quan; không thêm schema hay thay đổi nghiệp vụ.
- Kiểm tra các tài liệu Plan được tạo/cập nhật đều bằng tiếng Việt và vẫn giữ nguyên các identifier kỹ thuật.
- Xác nhận không tạo `tasks.md` hoặc mã triển khai; không chạy test.

---

## Mục 8: Lập implementation plan Feature 111 – Thiết lập cơ sở của chủ sân

| Trường                   | Nội dung                                                                                                                                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Ngày                     | 2026-10-16                                                                                                                                                                                                         |
| Người thực hiện          | Minh Nhựt                                                                                                                                                                                                          |
| Nhóm tính năng liên quan | 11 – Chủ sân (spec `specs/111-owner-venue-setup`)                                                                                                                                                                  |
| Công cụ AI               | Codex (GPT-6), `$speckit-plan`                                                                                                                                                                                     |
| Mục đích                 | Đối chiếu spec 111 với constitution, hướng dẫn W1–W2, database README, specs 110/112/120, các spec booking và cấu trúc code để lập kế hoạch triển khai quản lý venue, court, bảng giá và khóa giờ; không viết code |
| Nhánh Git                | `feature/111-owner-venue-setup-plan`                                                                                                                                                                               |

### Prompt đã dùng

```text
$speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/111-owner-venue-setup

Lập Implementation Plan cho Feature 111 – Owner Venue Setup dựa trên specs/111-owner-venue-setup/spec.md.

Đọc và đối chiếu:
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Specs 110, 112, các spec booking liên quan và 120 nếu tồn tại
- Cấu trúc code và quy ước kỹ thuật hiện có

Plan cần xác định:
- Thành phần triển khai thêm, sửa và ẩn cơ sở; quản lý sân con.
- Cách cấu hình bảng giá theo khung giờ và thứ.
- Cách khóa giờ sân, lý do khóa và ảnh hưởng đến khả năng đặt sân.
- Tích hợp với venues, courts, priceRules, slots.status, BLOCKED, blockReason theo tài liệu CSDL hiện có.
- Phân quyền owner đã được duyệt, kiểm tra quyền sở hữu cơ sở/sân, xử lý lỗi và chiến lược kiểm thử.

Không bao gồm: đăng ký chủ sân (Feature 110), duyệt booking, drop-in, voucher và thống kê vận hành (Feature 112), quy trình admin (Feature 120).

Không tự bịa schema, API hoặc thay đổi nghiệp vụ trong spec. Nếu phát hiện mâu thuẫn hoặc thiếu thông tin, ghi nhận rõ. Chỉ tạo tài liệu thuộc bước Plan; không chạy /speckit-tasks, /speckit-implement hoặc viết implementation code.

Cuối cùng báo cáo file đã tạo, quyết định kỹ thuật, dependency, rủi ro và điểm cần làm rõ.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec 111 với constitution, hướng dẫn W1–W2, database README, specs 110/112/120, specs booking liên quan và cấu trúc Android/Firestore/Supabase hiện có.
- Tạo các tài liệu Plan trong `specs/111-owner-venue-setup/`: `plan.md`, `research.md`, `data-model.md`, `quickstart.md` và `contracts/firestore-boundary.md`.
- Giữ nguyên schema hiện có cho `venues`, `courts`, `priceRules` và `slots`; không thêm field, enum, collection hay API.
- Xác định quản lý cơ sở bằng `status = HIDDEN`, quản lý sân bằng `isActive`, bảng giá dùng `pricePerHour` và tính giá mỗi slot 30 phút bằng `pricePerHour / 2`; booking cũ giữ nguyên giá đã lưu.
- Quy định khóa slot bằng `status = BLOCKED` và các lý do owner `MAINTENANCE`, `EVENT`, `WALK_IN`; thao tác khóa/mở phải nguyên tử, kiểm tra xung đột với `HOLD`/`BOOKED`/`BLOCKED`. `DROP_IN` thuộc spec 112/080.
- Ghi nhận dependency với Feature 110 về trạng thái owner đã duyệt và venue ban đầu; Feature 120 sở hữu khóa venue của admin; specs 030/040/041/050 tiêu thụ dữ liệu venue, court, giá và slot.
- Ghi rõ các điểm cần làm rõ: nguồn đáng tin cậy cho `ownerStatus`; hợp đồng server cho khóa/mở slot và cách xác định block owner được quyền gỡ; bên cập nhật `minPricePerHour`; một số quy tắc đọc venue ẩn/khóa và giới hạn `courtCount`.
- Không tạo `tasks.md`, không chạy `$speckit-tasks`, `$speckit-implement` hoặc test; không viết mã triển khai.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                 | Quyết định                                                                                               | Lý do                                                                                    |
| -------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Venue                | Dùng các field hiện có; owner có thể ẩn bằng `status = HIDDEN`; không xóa cứng venue có dữ liệu liên kết | Khớp spec 111 và schema; booking đã xác nhận được giữ nguyên                             |
| Court                | Quản lý bằng `name`, `surfaceType`, `isActive`, `sortOrder`; ngừng hoạt động để chặn booking mới         | Không thêm trạng thái hoặc hành vi xóa chưa có trong schema                              |
| Bảng giá             | Dùng `pricePerHour` kiểu `Long`, rule theo thứ và khung giờ; giá slot 30 phút bằng một nửa giá giờ       | Đồng bộ spec 111, 040 và database README                                                 |
| Xóa price rule       | Cho phép xóa theo clarification mới nhất; booking cũ giữ `items[].price`                                 | Không cần quan hệ rule-booking mới; ghi nhận các câu trả lời cũ còn mâu thuẫn trong spec |
| Khóa slot            | Dùng `slots.status = BLOCKED`, `blockReason` hiện có và giao dịch server nguyên tử                       | Bảo đảm không khóa một phần hoặc ghi đè booking/hold; không tự đặt API                   |
| Phân quyền           | Kiểm tra `appRole`, trạng thái owner đã duyệt và `venues.ownerId` ở phía server                          | Tuân thủ Constitution; Rules hiện chưa xác định đầy đủ nguồn `ownerStatus`               |
| Hợp đồng server      | Chỉ ghi ranh giới Firestore và bất biến; để tên function/request/response thành điểm cần thống nhất      | Tài liệu dự án chưa định nghĩa API khóa/mở slot                                          |
| Tasks/implementation | Không tạo hoặc chạy                                                                                      | Phạm vi công việc chỉ gồm bước Plan                                                      |

### Cách kiểm chứng

- Đối chiếu field, enum, đường dẫn collection và quyền với database README cùng các spec liên quan; không thêm schema hoặc thay đổi nghiệp vụ.
- Rà lại bộ artifact chỉ thuộc bước Plan; không tạo `tasks.md` hoặc code triển khai.
- `git diff --check` không báo lỗi định dạng.
- Không chạy test, tasks hoặc implementation.

---

## Mục 10: Lập implementation plan cho Feature 120 (quản trị và kiểm duyệt)

| Trường                   | Nội dung                                                                                                                                                            |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-16                                                                                                                                                          |
| Người thực hiện          | Minh Nhựt                                                                                                                                                           |
| Nhóm tính năng liên quan | 12 – Quản trị (`specs/120-admin`)                                                                                                                                   |
| Công cụ AI               | Codex (GPT-6), skill `$speckit-plan`                                                                                                                                |
| Mục đích                 | Đối chiếu spec 120 với constitution, hướng dẫn W1–W2, database README, các spec account/owner/thông báo liên quan và cấu trúc code để lập kế hoạch; không viết code |
| Nhánh Git                | `feature/120-admin-plan`                                                                                                                                            |

### Prompt đã dùng

```text
$speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/120-admin

Lập Implementation Plan cho Feature 120 – Admin dựa trên specs/120-admin/spec.md.

Đọc và đối chiếu:
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Các spec 100, 110, 111, 112 và các spec account liên quan
- Cấu trúc code và quy ước kỹ thuật hiện có

Plan cần xác định:
- Thành phần quản trị người dùng và xử lý các báo cáo/vi phạm theo phạm vi spec.
- Quy trình xem xét, duyệt hoặc từ chối hồ sơ chủ sân.
- Cách quản lý dữ liệu và thống kê hệ thống theo yêu cầu đã đặc tả.
- Phân quyền admin, bảo vệ dữ liệu nhạy cảm và kiểm tra quyền ở phía server/Security Rules.
- Tác động đến CSDL, dependency, xử lý lỗi và chiến lược kiểm thử.

Không bao gồm: tự mở rộng nghiệp vụ ngoài specs/120-admin/spec.md, thay đổi quy tắc của các feature khác hoặc triển khai code.

Không tự bịa schema, API, trạng thái hoặc công nghệ mới. Đối chiếu tài liệu và code hiện có; nếu phát hiện mâu thuẫn hoặc thiếu thông tin, ghi nhận rõ. Chỉ tạo tài liệu thuộc bước Plan; không chạy /speckit-tasks, /speckit-implement hoặc viết implementation code.

Cuối cùng báo cáo file đã tạo, quyết định kỹ thuật chính, dependency, rủi ro và điểm cần làm rõ.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec 120 với Constitution 1.2.0, hướng dẫn phân công W1–W2, database README, specs 010/011/100/110/111/112 và cấu trúc Android, Firestore Rules, test scaffold.
- Tạo các artifact Plan trong `specs/120-admin/`: `plan.md`, `research.md`, `data-model.md`, `quickstart.md` và `contracts/admin-boundaries.md`.
- Ghi nhận các ranh giới quản trị: duyệt/từ chối hồ sơ từ trạng thái `PENDING`; tạo venue từ `venueDraft` khi duyệt; xử lý report bằng các action và trạng thái hiện có; quản lý trạng thái tài khoản, thu hồi quyền owner và khóa/mở khóa venue.
- Xác định quyền admin phải được xác minh ở server bằng `appRole`; giấy tờ chủ sân dùng bucket riêng `owner-docs` và signed URL hết hạn sau 5 phút.
- Giữ nguyên schema và enum có trong tài liệu nguồn. Không thêm API, field, collection, dependency hay trạng thái mới.
- Ghi nhận các điểm chưa được đặc tả đầy đủ: tính nhất quán/recovery giữa Firestore, custom claims và notification; schema và chỉ số thống kê; quy tắc quản lý danh mục; hành vi ẩn đối với report `DROP_IN`; cách xác định tài khoản hoạt động để gửi broadcast.
- Ghi nhận code hiện tại mới là Android starter; `firestore.rules` chưa có collection-specific rules, `supabase/functions` chưa có code triển khai và version catalog còn thiếu một số dependency kiến trúc/kiểm thử.
- `setup-plan.sh` không nhận feature directory dưới dạng argument; chạy với biến môi trường `SPECIFY_FEATURE_DIRECTORY=specs/120-admin` và nhận đúng thư mục `specs/120-admin`.
- Không tạo `tasks.md`, không chạy `$speckit-tasks`, `$speckit-implement` hoặc tests; không viết implementation code.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                        | Quyết định                                                                                                                                           | Lý do                                                                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Duyệt hồ sơ                 | Dùng các trạng thái và field đã có; quyết định phải dựa trên hồ sơ hiện còn `PENDING`; từ chối cần lý do không rỗng; duyệt tạo venue từ `venueDraft` | Khớp spec 120, spec 110 và database README; tránh quyết định lỗi thời hoặc mở rộng nghiệp vụ |
| Báo cáo                     | Dùng `OPEN → RESOLVED/DISMISSED` và các action hiện có; kiểm tra action tương thích với target                                                       | Giữ đúng schema và clarifications trong spec; chưa giả định `DROP_IN` có hỗ trợ ẩn           |
| Quyền và giấy tờ            | Xác minh Admin phía server; chỉ người nộp và Admin được truy cập giấy tờ qua signed URL có thời hạn                                                  | Tuân Constitution và giới hạn riêng tư trong database README                                 |
| User, owner và venue        | Thu hồi owner theo các field/trạng thái đã chốt; không tự đổi trạng thái venue khi thu hồi quyền; Admin quản lý khóa/mở khóa venue                   | Khớp spec 120 và phụ thuộc spec 010/011/112                                                  |
| Thống kê và danh mục        | Chỉ dùng collection đã nêu; không tự định nghĩa metrics, field hay thao tác CRUD chưa có đặc tả                                                      | Tài liệu nguồn chưa có schema chi tiết và quy tắc nghiệp vụ                                  |
| Server contract và recovery | Ghi các thiếu hụt về API, transaction, idempotency, claim và notification thành điểm cần làm rõ                                                      | Chưa có hợp đồng đủ căn cứ để thiết kế chi tiết                                              |
| Tasks/implementation        | Không tạo hoặc chạy                                                                                                                                  | Phạm vi yêu cầu chỉ gồm bước Plan                                                            |

### Cách kiểm chứng

- Đối chiếu các field, trạng thái, quyền và collection với spec 120, database README và các spec liên quan.
- Rà soát cấu trúc code hiện tại để phân biệt phần đã tồn tại với vị trí dự kiến cho implementation.
- Chạy `git diff --check`; không có lỗi whitespace được báo cáo.
- Không chạy test, `$speckit-tasks` hoặc `$speckit-implement`.

---

## Mục 9: Lập implementation plan Feature 112 – Owner Operations

| Trường                   | Nội dung                                                                                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-16                                                                                                                                                                                              |
| Người thực hiện          | Minh Nhựt                                                                                                                                                                                               |
| Nhóm tính năng liên quan | 11 – Chủ sân (spec `specs/112-owner-operations`)                                                                                                                                                        |
| Công cụ AI               | Codex (GPT-6), `$speckit-plan`                                                                                                                                                                          |
| Mục đích                 | Đối chiếu spec 112 với constitution, hướng dẫn W1–W2, database README, specs 110/111/120, các spec booking/drop-in/voucher liên quan và cấu trúc code để lập kế hoạch vận hành chủ sân; không viết code |
| Nhánh Git                | `feature/112-owner-operations-plan`                                                                                                                                                                     |

### Prompt đã dùng

```text
$speckit-plan SPECIFY_FEATURE_DIRECTORY=specs/112-owner-operations

Lập Implementation Plan cho Feature 112 – Owner Operations dựa trên specs/112-owner-operations/spec.md.

Đọc và đối chiếu:
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Specs 110, 111, các spec booking/drop-in/voucher liên quan và 120 nếu tồn tại
- Cấu trúc code và quy ước kỹ thuật hiện có

Plan cần xác định:
- Thành phần xử lý duyệt/từ chối booking và lịch tổng hợp ngày/tuần.
- Luồng tạo buổi drop-in, quản lý đăng ký và QR check-in.
- Luồng tạo và quản lý voucher theo nghiệp vụ đã đặc tả.
- Cách tính doanh thu, tỷ lệ lấp đầy và khung giờ cao điểm dựa trên dữ liệu hiện có.
- Tích hợp với bookings, slots, venues, courts, priceRules và các dữ liệu drop-in/voucher theo CSDL chung.
- Phân quyền owner đã được duyệt, kiểm tra quyền sở hữu cơ sở/sân, xử lý lỗi và chiến lược kiểm thử.

Không bao gồm: đăng ký chủ sân (Feature 110), quản lý thông tin cơ sở/sân, bảng giá và khóa giờ (Feature 111), quy trình quản trị hệ thống (Feature 120).

Không tự bịa schema, API, trạng thái hoặc thay đổi nghiệp vụ trong spec. Nếu phát hiện mâu thuẫn hoặc thiếu thông tin, ghi nhận rõ. Chỉ tạo tài liệu thuộc bước Plan; không chạy /speckit-tasks, /speckit-implement hoặc viết implementation code.

Cuối cùng báo cáo file đã tạo, quyết định kỹ thuật, dependency, rủi ro và điểm cần làm rõ.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec 112 với constitution, hướng dẫn W1–W2, database README, specs 110/111/120, các spec 040/041/050/060/080/081/100 và cấu trúc Android/Supabase hiện có.
- Tạo các tài liệu Plan trong `specs/112-owner-operations/`: `plan.md`, `research.md`, `data-model.md`, `quickstart.md` và `contracts/owner-operations-boundaries.md`.
- Xác định kiến trúc Android theo MVVM/package `feature/owner`; các thao tác nhạy cảm được xác minh phía server. Các cập nhật booking/slot và drop-in/slot phải nguyên tử theo transaction Firestore.
- Lịch ngày/tuần được tổng hợp từ booking, slot và drop-in session theo múi giờ `Asia/Ho_Chi_Minh`. `HOLD` chỉ là cảnh báo tạm thời; drop-in được trình bày từ dữ liệu session, không giả thành booking người chơi.
- Thống kê doanh thu phân nhóm theo `DEPOSITED`, `PAID`, `REFUNDED`; tỷ lệ lấp đầy và khung giờ cao điểm tuân theo định nghĩa trong spec. Không dùng `priceRules` thay cho số tiền booking thực tế.
- Giữ nguyên schema hiện có; không thêm collection, field, enum, endpoint hoặc thay đổi nghiệp vụ. Contract server chưa được nguồn quy định đầy đủ được ghi thành điểm cần thống nhất.
- Ghi nhận `setup-plan.sh` tự chọn Feature 100 theo trạng thái branch/config dù đầu vào yêu cầu Feature 112. Tài liệu được đặt theo đúng thư mục `specs/112-owner-operations`; không dùng đường dẫn setup trả về.
- Ghi nhận các điểm cần làm rõ: vai trò của Feature 111/112 trong tạo voucher; nguồn claim/trường quyền owner; schema và độ mới của `dailyStats`; hợp đồng server cho approve/reject/cancel booking, quản lý drop-in, check-in drop-in và voucher CRUD.
- Không tạo `tasks.md`, không chạy `$speckit-tasks`, `$speckit-implement` hoặc test; không viết mã triển khai.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                 | Quyết định                                                                                                                                  | Lý do                                                                                                        |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Booking và lịch      | Đọc theo venue/khoảng thời gian; quyết định booking phải kiểm tra lại quyền, deadline, status và slot phía server                           | Tuân theo spec 112, máy trạng thái booking và nguyên tắc toàn vẹn dữ liệu trong constitution                 |
| Drop-in và slot      | Dùng schema hiện có; tạo/sửa/hủy session phải đồng bộ slot `BLOCKED`/`DROP_IN` toàn phần; registration và queue tiếp tục theo specs 080/081 | Tránh vượt chỗ, ghi dở dang hoặc thay đổi luồng đăng ký hiện có                                              |
| QR check-in          | Dùng `checkin-booking` đã được database README định danh cho booking; check-in drop-in được ghi nhận là cần contract server thống nhất      | Không tự đặt tên function hoặc request/response chưa có trong tài liệu nguồn                                 |
| Voucher              | Dùng `venues/{venueId}/vouchers/{code}`, giữ quy tắc uniqueness và bảo vệ `usedCount`/redemptions; không quản lý `systemVouchers`           | Khớp schema chung và ranh giới admin của Feature 120; quyền tạo voucher giữa spec 111 và 112 chưa thống nhất |
| Thống kê             | Tính từ booking thực tế theo FR-016–018; không giả định schema `dailyStats`                                                                 | README có nhắc `dailyStats` nhưng chưa định nghĩa field/chỉ số                                               |
| Phân quyền           | Server xác minh owner đã duyệt và quyền sở hữu venue; không dựa vào việc ẩn nút                                                             | Tuân thủ Constitution và FR-001/002                                                                          |
| Schema/API           | Không thêm schema, trạng thái, endpoint hoặc mã lỗi; ghi các contract còn thiếu thành câu hỏi mở                                            | Tài liệu nguồn chưa xác định đủ để suy diễn an toàn                                                          |
| Tasks/implementation | Không tạo hoặc chạy                                                                                                                         | Phạm vi công việc chỉ gồm bước Plan                                                                          |

### Cách kiểm chứng

- Đối chiếu field, enum, đường dẫn collection và quy tắc nghiệp vụ với database README và các spec liên quan; không tự thêm schema.
- Xác nhận các artifact chỉ thuộc bước Plan; không tạo `tasks.md` hoặc code triển khai.
- Setup script trả về nhầm đường dẫn Feature 100; giữ nguyên nội dung Feature 100 và tạo tài liệu đúng trong `specs/112-owner-operations/`.
- Không chạy test, tasks hoặc implementation.

---

## Mục 11: Tạo và cập nhật Tasks Feature 100 – Notification

| Trường                   | Nội dung                                                                                                                   |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-19                                                                                                                 |
| Người thực hiện          | Minh Nhựt                                                                                                                  |
| Nhóm tính năng liên quan | 10 – Thông báo (spec `specs/100-notification`)                                                                             |
| Công cụ AI               | Codex (GPT-6), `$speckit-tasks`, `$speckit-analyze`                                                                        |
| Mục đích                 | Tạo danh sách task theo spec và plan hiện có, sau đó rà soát tính nhất quán và cập nhật Tasks theo các phát hiện phân tích |
| Nhánh Git                | `feature/100-notification-plan`                                                                                            |

### Prompt đã dùng

**Tạo Tasks:**

```text
$speckit-tasks Tạo danh sách Tasks cho Feature 100 – Notification dựa trên specification và implementation plan hiện có.

Trước khi tạo tasks, đọc và đối chiếu spec, plan, checklist requirements nếu có,
constitution, database README và tài liệu/spec liên quan đến feature nguồn.

Chia công việc thành task nhỏ, rõ ràng, có thể thực hiện và kiểm tra độc lập;
tuân thủ format, task ID, thứ tự và dependency của Spec Kit; nhóm theo kiến trúc thực tế;
liên kết với User Story hoặc Functional Requirement khi workflow hỗ trợ.

Ưu tiên bảo mật, chống thông báo trùng, xử lý lỗi và kiểm thử theo spec.
Không tự thêm nghiệp vụ hoặc thay đổi quyết định đã chốt; không viết implementation code
và không chạy /speckit-implement. Chỉ tạo hoặc cập nhật tài liệu Tasks.
```

**Phân tích và cập nhật Tasks:**

```text
$speckit-analyze

Chỉnh sửa Tasks của Feature 100 – Notification dựa trên báo cáo phân tích đặc tả.
Đọc lại spec, plan, tasks, research nếu có, constitution, database README và tài liệu
liên quan Feature 091/120.

Ghi nhận mâu thuẫn quyền Admin/SYSTEM; làm rõ dependency event transport, idempotency,
retry/reminder, query/index, chat presence và điều kiện tài khoản active. Không tự thêm
schema hoặc quyết định quyền chưa có căn cứ; chỉ chặn task phụ thuộc trực tiếp.

Bổ sung kiểm thử light/dark, phone/w600dp và inbox đã lưu nhưng FCM thất bại; xử lý
xung đột song song T026/T037; làm rõ MVP và các task setup T001–T003. Giữ task ID nếu có thể.
Không sửa spec, plan, database README hoặc tài liệu feature khác; không implement hay viết code.
```

### Tóm tắt phản hồi của AI

- Đọc và đối chiếu `specs/100-notification/spec.md`, `plan.md`, `checklists/requirements.md`, `data-model.md`, các contract, `research.md`, constitution và database README.
- Tạo `specs/100-notification/tasks.md` với 56 task theo năm User Story, các giai đoạn setup/nền tảng và polish; có dependency, cơ hội làm song song và MVP sequence.
- Chạy phân tích chỉ đọc đối với spec, plan, tasks và constitution. Prerequisite script theo nhánh hiện hành trỏ sang Feature 120; phân tích được tiếp tục trực tiếp trên thư mục Feature 100 đã chỉ định.
- Cập nhật Tasks để làm rõ mâu thuẫn quyền Admin/SYSTEM giữa DB README §7, §4.13 và luồng server-authoritative của Feature 120; không sửa schema, Security Rules hoặc quyền Admin.
- Ghi các quyết định còn thiếu là `NEEDS DECISION` vì repository chưa định nghĩa nhãn `BLOCKED`. Mỗi quyết định chỉ chặn task phụ thuộc trực tiếp; công việc độc lập có thể tiếp tục.
- Ghi dependency Feature 091 về chat presence/multi-device và Feature 120 về điều kiện xác định tài khoản active; không tự đặt tiêu chí thay thế.
- Bổ sung kiểm thử UI light/dark và phone/`w600dp`; bổ sung kiểm thử FCM thất bại sau khi inbox đã lưu và khẳng định inbox record vẫn khả dụng.
- Bỏ marker `[P]` khỏi T037 vì T026 và T037 cùng sửa `firestore-tests/test/notification-security.test.ts`; giữ nguyên ID task.
- Làm rõ T001–T003 là các task khảo sát, không tự cài dependency hoặc dựng hạ tầng. Nêu MVP hiện tại là backend booking notifications và cần nhóm xác nhận nếu cần bao gồm inbox tối thiểu US3.
- Không chạy `$speckit-implement`, không viết implementation code và không chạy tests.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                     | Quyết định                                                                                    | Lý do                                                              |
| ------------------------ | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| Nhóm task và thứ tự      | Giữ 56 task theo năm User Story, các phase setup/nền tảng/polish và thứ tự ưu tiên trong spec | Duy trì cấu trúc task đã tạo và quan hệ dependency                 |
| Quyền Admin/SYSTEM       | Ghi mâu thuẫn thành quyết định cần nhóm xác nhận; không sửa quyền/schema/Rules                | CSDL, Feature 120 và luồng server chưa đồng bộ                     |
| Event và delivery        | Ghi rõ các điểm chờ xác nhận về event transport, idempotency, retry, reminder và query/index  | Tránh tự chọn giao thức hoặc thêm field/collection/status          |
| Tích hợp Feature 091/120 | Chặn chat push suppression và SYSTEM fan-out tương ứng đến khi nguồn xác nhận                 | Presence và predicate tài khoản active chưa được định nghĩa đầy đủ |
| UI và FCM tests          | Bổ sung theme/kích thước màn hình và persistence khi FCM thất bại                             | Phản ánh constitution cùng hành vi trong spec/plan                 |
| Parallel marker          | Bỏ `[P]` ở T037, không đổi task ID                                                            | T026 và T037 cùng sửa một test file                                |
| MVP và setup             | Ghi rõ MVP backend hiện tại cần được nhóm xác nhận; T001–T003 chỉ khảo sát                    | Chưa có căn cứ để đổi phạm vi nghiệp vụ hoặc chọn dependency       |

### Cách kiểm chứng

- Đối chiếu task với spec, plan, research, constitution, database README và tài liệu liên quan Feature 091/120.
- Kiểm tra danh sách còn 56 task, ID không trùng và định dạng checklist hợp lệ.
- Không chạy implementation hoặc tests; lần cập nhật này chỉ bổ sung nhật ký sử dụng AI.

---

## Mục 12: Tạo và cập nhật Tasks Feature 110 – Owner Onboarding

| Trường                   | Nội dung                                                                                                                                                                                     |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-19                                                                                                                                                                                   |
| Người thực hiện          | Minh Nhựt                                                                                                                                                                                    |
| Nhóm tính năng liên quan | 11 – Chủ sân (spec `specs/110-owner-onboarding`)                                                                                                                                             |
| Công cụ AI               | Codex (GPT-6), `$speckit-tasks`, `$speckit-analyze`                                                                                                                                          |
| Mục đích                 | Tạo danh sách task triển khai Feature 110, phân tích tính nhất quán với spec/plan/Constitution, rồi cập nhật Tasks theo các phát hiện mà không tự quyết định nghiệp vụ hoặc schema còn thiếu |
| Nhánh Git                | `feature/110-owner-onboarding-tasks`                                                                                                                                                         |

### Prompt đã dùng

**Tạo Tasks:**

```text
$speckit-tasks Tạo danh sách Tasks cho Feature 110 – Owner Onboarding.

Đọc và đối chiếu spec.md, plan.md, checklist requirements nếu có,
constitution, docs/guides/w1-w2-spec-assignment.md, database README,
các spec account/profile và Feature 111, 112, 120 nếu tồn tại, cùng cấu trúc
code và quy ước repository.

Chia task nhỏ, có thứ tự/dependency; bao quát nộp hồ sơ, tải giấy tờ, theo dõi,
rejected/resubmit, validation, bảo mật giấy tờ, kiểm tra quyền server/Security Rules
và chống hồ sơ trùng. Liên kết task với User Stories, FR và Acceptance Scenarios.

Không tự thiết kế nghiệp vụ thuộc Feature 120; đối chiếu database trước khi đề xuất
schema/API; ghi rõ mâu thuẫn và chỉ chặn task phụ thuộc quyết định chưa chốt.
Chỉ tạo/cập nhật Tasks trong specs/110-owner-onboarding/. Không sửa spec/plan,
không implement, không viết code và không chạy `$speckit-implement`.
```

**Phân tích:**

```text
$speckit-analyze

Phân tích chỉ đọc tính nhất quán giữa spec.md, plan.md, tasks.md và Constitution
của Feature 110. Prerequisite script trỏ nhầm Feature 120; tiếp tục trên thư mục
Feature 110 đã được chỉ định, không thay đổi file.
```

**Cập nhật theo Analyze:**

```text
$speckit-tasks Cập nhật danh sách Tasks Feature 110 theo báo cáo Analyze.

Làm rõ application identity, resubmission, lịch sử và chống gửi trùng; bổ sung
luồng applicant xem/tải giấy tờ qua sign-download, hết hạn và retry; làm rõ đầu ra
hợp đồng xác thực/app gọi Supabase và quyền upload applicant mà không tự chọn
protocol/token/Rules. Gắn task với SC, bổ sung usability evaluation SC-001,
Material 3/light-dark/phone/w600dp/loading-empty-error, kiểm tra dependency theo
Constitution và hành vi ghim thủ công khi từ chối quyền vị trí.

Giữ task ID ổn định nếu có thể; chỉ chặn task phụ thuộc quyết định chưa chốt;
không tự thêm schema, field hoặc ID. Chỉ sửa tasks.md; không sửa spec, plan, CSDL,
feature khác; không implement, viết code hoặc chạy `$speckit-implement`.
```

### Tóm tắt phản hồi của AI

- Đọc spec, plan, checklist requirements, data model, boundary contract, research, quickstart, Constitution, database README, hướng dẫn phân công và các tài liệu account/profile, Feature 111/112/120.
- Tạo danh sách Tasks ban đầu cho ba User Story: nộp hồ sơ (P1), theo dõi trạng thái (P1), sửa và nộp lại hồ sơ rejected (P2), cùng các phase setup, foundational và cross-cutting.
- Chạy `$speckit-analyze` ở chế độ chỉ đọc. Script prerequisite tự chọn Feature 120 và dừng vì thiếu `specs/120-admin/tasks.md`; tiếp tục phân tích trực tiếp các artifact Feature 110 đã chỉ định.
- Analyze phát hiện các điểm cần làm rõ: application hiện hành/lịch sử/resubmit/idempotency; applicant download giấy tờ; tích hợp app–Supabase và upload ảnh venue trước duyệt; SC-001 usability; tiêu chí giao diện theo Constitution; rationale của dependency; từ chối quyền vị trí.
- Cập nhật `specs/110-owner-onboarding/tasks.md`, giữ ID T001–T032, thêm T033–T035 và tổng cộng 35 task. T001–T003 mô tả quyết định nhóm cần xác nhận; các task submit, status query, upload/download và resubmit chỉ bị chặn tại phần thực sự phụ thuộc.
- T033/T034 bổ sung applicant signed-download cho hồ sơ của chính người nộp, kiểm tra UID khác, hết hạn URL, xin URL mới và lỗi không tiết lộ path.
- T035 mô tả usability evaluation thủ công cho SC-001: ít nhất 10 người thuộc nhóm mục tiêu, không hướng dẫn trực tiếp, ghi nhận tỷ lệ hoàn tất trong tối đa 5 phút; đạt ít nhất 9/10. Đây không phải automated test.
- Gắn liên kết SC-001–SC-007 vào task liên quan; thêm tiêu chí Material 3, DayNight, điện thoại/`w600dp`, các trạng thái loading/content/empty/error và marker vị trí thủ công không cần quyền vị trí.
- T005 yêu cầu rà từng dependency và không thêm thư viện chưa được kiến trúc chấp thuận. Plan hiện nêu dependency còn thiếu nhưng chưa giải thích từng dependency; không sửa plan trong lượt này.
- Không sửa spec, plan, database README hay tài liệu feature khác; không viết implementation code và không chạy `$speckit-implement` hoặc tests.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                         | Quyết định                               | Lý do                                                                                                                                    |
| ---------------------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| T001–T003                    | Sửa nội dung, giữ ID                     | Làm rõ quyết định về application identity, history, submit idempotency và app–Supabase contract; không chọn schema/protocol thay nhóm    |
| T005                         | Sửa nội dung, giữ ID                     | Rà dependency theo Constitution; chặn việc thêm dependency mới cho tới khi kiến trúc được duyệt và rationale được ghi nhận               |
| T009–T010                    | Sửa nội dung, giữ ID                     | Bao quát upload applicant cho `owner-docs` và `venue-photos`, signed download, access control và kiểm thử local theo contract được duyệt |
| T011–T018                    | Giữ ID, bổ sung truy vết/tiêu chí        | Gắn SC và bổ sung Material 3, theme, kích thước màn hình, marker thủ công và UI test                                                     |
| T019–T029                    | Giữ ID, làm rõ dependency và truy vết SC | Phân biệt task có thể phát triển bằng fake với phần query/submit/resubmit thật cần quyết định T001–T003                                  |
| T033–T034                    | Thêm task                                | Bổ sung luồng applicant xem/tải giấy tờ riêng, URL hết hạn, retry và từ chối truy cập hồ sơ người khác                                   |
| T035                         | Thêm task                                | Đo SC-001 bằng usability evaluation thủ công, không giả định đây là automated test                                                       |
| Spec, plan, database và code | Không sửa                                | Giữ đúng phạm vi chỉ cập nhật Tasks                                                                                                      |

### Cách kiểm chứng

- Đối chiếu task với 19 FR, 7 SC, User Stories/Acceptance Scenarios, Constitution, database README và các dependency liên quan.
- Kiểm tra 35 checklist task có ID duy nhất, format checkbox/label/file path hợp lệ; `git diff --check` không báo lỗi whitespace.
- Không chạy test, implementation hoặc `$speckit-implement`; không sửa file ngoài Tasks.

---

## Mục 13: Tạo và cập nhật Tasks Feature 111 – Owner Venue Setup

| Trường                   | Nội dung                                                                                                                           |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-19                                                                                                                         |
| Người thực hiện          | Minh Nhựt                                                                                                                          |
| Nhóm tính năng liên quan | 11 – Quản lý sân (spec `specs/111-owner-venue-setup`)                                                                              |
| Công cụ AI               | Codex (GPT-6), `$speckit-tasks`, `$speckit-analyze`                                                                                |
| Mục đích                 | Tạo danh sách Tasks Feature 111, phân tích nhất quán spec/plan/tasks và cập nhật các điểm chưa rõ mà không tự quyết định nghiệp vụ |
| Nhánh Git                | `feature/111-owner-venue-setup-tasks`                                                                                              |

### Prompt đã dùng

**Tạo Tasks lần đầu:**

```text
$speckit-tasks Tạo danh sách Tasks cho Feature 111 – Owner Venue Setup.

Đọc và đối chiếu spec.md, plan.md, constitution, hướng dẫn phân công W1–W2,
database README, Specs 110/112 và các spec booking liên quan nếu có.

Chia task nhỏ, có thứ tự/dependency; bao phủ thêm/sửa/ẩn venue, quản lý court,
bảng giá theo thứ/khung giờ, khóa giờ sân, validation, phân quyền, kiểm thử,
xử lý lỗi và edge cases. Bảo đảm chỉ owner đã được duyệt quản lý venue/court
thuộc quyền sở hữu của mình. Đối chiếu venues, courts, priceRules, slots.status,
BLOCKED và blockReason với database README; không tự bịa schema/field/status.
Liên kết task với User Stories, FR và SC theo quy ước repository. Ghi dependency
Feature 110, 112 và booking; không tự giải quyết nghiệp vụ thuộc feature khác.

Chỉ tạo/cập nhật Tasks trong specs/111-owner-venue-setup/. Không sửa spec/plan,
không chạy analyze/implement và không viết implementation code.
```

**Phân tích:**

```text
$speckit-analyze
```

**Cập nhật theo Specification Analysis Report:**

```text
$speckit-tasks Cập nhật danh sách Tasks Feature 111 theo Specification Analysis Report.

Chỉ chỉnh specs/111-owner-venue-setup/tasks.md. Đọc lại spec, plan, tasks,
data-model, research, constitution, database README và specs liên quan 110/120/030/040/041.

Giữ kiểm tra quyền server bắt buộc; chặn mọi thao tác ghi đến khi Feature 110/120
xác nhận nguồn đáng tin cậy cho ownerStatus=APPROVED. Chặn mở khóa slot đến khi
nhóm booking chốt hợp đồng xác minh ownership, trạng thái block và quyền mở khóa an toàn;
không thêm schema. Ghi rõ courtCount consistency/maximum và quy tắc so sánh tên court
trùng chưa được định nghĩa; chỉ kiểm tra/test phần đã xác định. Chặn price-rule
integration phụ thuộc quyết định bên cập nhật minPricePerHour khi rule đổi hoặc không
còn rule. Phân biệt task thuộc Feature 111 với task chỉ xác minh hợp đồng/tích hợp
Feature 030/040; không giao sửa package/chức năng ngoài phạm vi.

Giữ task ID nếu có thể, không tự quyết định nghiệp vụ. Không sửa spec, plan,
data-model, research, database schema hoặc spec feature khác; không chạy
analyze/implement trong lượt cập nhật.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec, plan, data-model, research, boundary contract, quickstart, constitution, database README và tài liệu/spec liên quan Feature 110, 120, 030, 040, 041.
- Tạo `specs/111-owner-venue-setup/tasks.md` với 39 task, năm User Story, phase setup/foundational/polish, dependency, cơ hội song song, MVP và truy vết FR/Acceptance Scenarios.
- Phân tích chỉ đọc ghi nhận sáu findings: nguồn xác minh ownerStatus (A1), an toàn mở khóa (A2), courtCount (A3), tên court trùng (A4), nguồn cập nhật minPricePerHour (A5), và ranh giới Feature 030/040 (A6).
- Cập nhật Tasks, giữ nguyên 39 task và ID T001–T039; không thêm schema, field, status, endpoint hoặc quyết định nghiệp vụ.
- T003/T004/T006 đặt xác nhận nguồn ownerStatus từ Feature 110/120 làm prerequisite trước khi bật bất kỳ thao tác ghi quản lý nào.
- T007 thành cổng hợp đồng với nhóm booking về transaction và cách chứng minh ownership/eligibility. T029 cùng thao tác và kiểm thử phụ thuộc không được triển khai khi chưa xác định block nào owner được phép mở khóa.
- T008/T022/T023 chặn price-rule mutation/integration phụ thuộc cho tới khi xác định bên có thẩm quyền cập nhật `minPricePerHour` và hành vi khi rules thay đổi hoặc không còn rule.
- T009 chỉ kiểm tra `courtCount` là số nguyên dương; không áp đặt equality/maximum chưa được xác định. T016 chỉ kiểm tra các trường court đã nêu; không tự thực thi hoặc test chính sách tên trùng.
- T033/T035 chỉ rà soát hợp đồng và kiểm thử tích hợp; không sửa source/UI/package của Feature 030/040.
- Không sửa spec, plan, data-model, research, database README hoặc spec feature khác; không chạy implementation hay viết code.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                     | Quyết định                                                                                             | Lý do                                                                       |
| ------------------------ | ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| T003/T004/T006           | Bổ sung prerequisite xác minh ownerStatus trước khi cho phép ghi                                       | Không tự chọn claim/document/field làm nguồn quyền                          |
| T007/T028/T029/T031/T032 | Làm rõ hợp đồng booking, kiểm tra ownership/trạng thái và chặn mở khóa khi chưa chứng minh eligibility | Schema `slots` không định danh block theo owner/thao tác; không thêm schema |
| T008/T022/T023           | Chặn rule mutations/integration phụ thuộc `minPricePerHour` đến khi xác định authority                 | Tài liệu chưa quy định bên cập nhật và hành vi khi hết rules                |
| T009                     | Tách validation đã rõ khỏi equality/maximum chưa có quyết định                                         | Không tự đặt quy tắc hoặc giới hạn                                          |
| T016                     | Chỉ validate trường court đã xác định; để chính sách tên trùng mở                                      | Spec chưa định nghĩa so sánh/chuẩn hóa                                      |
| T033/T035                | Chuyển thành xác minh hợp đồng/tích hợp, không sửa Feature 030/040                                     | Giữ ranh giới package theo plan                                             |
| Task IDs                 | Giữ 39 task, T001–T039                                                                                 | Tránh làm sai tham chiếu hiện có                                            |
| Artifact khác            | Không sửa                                                                                              | Giữ đúng phạm vi Feature 111 Tasks                                          |

### Cách kiểm chứng

- Kiểm tra 39 checklist task có ID liên tục, không trùng và đúng format; xác nhận các dependency chưa chốt được đánh dấu là điều kiện chặn.
- Không chạy tests, `$speckit-analyze` hoặc `$speckit-implement` sau lần cập nhật Tasks.

---

## Mục 14: Tạo, phân tích và cập nhật Tasks Feature 112 – Owner Operations

| Trường                   | Nội dung                                                                                                                                                              |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-19                                                                                                                                                            |
| Người thực hiện          | Minh Nhựt                                                                                                                                                             |
| Nhóm tính năng liên quan | 11 – Chủ sân (spec `specs/112-owner-operations`)                                                                                                                      |
| Công cụ AI               | Codex (GPT-6), `$speckit-tasks`, `$speckit-analyze`                                                                                                                   |
| Mục đích                 | Lập danh sách task Feature 112, rà soát tính nhất quán với spec/plan/Constitution và cập nhật Tasks theo findings mà không tự quyết định nghiệp vụ hoặc schema còn mở |
| Nhánh Git                | `feature/112-owner-operations-tasks`                                                                                                                                  |

### Prompt đã dùng

**Tạo Tasks lần đầu:**

```text
$speckit-tasks

Tạo danh sách Tasks cho Feature 112 – Owner Operations.

Trước khi tạo tasks, đọc và đối chiếu spec, plan, Constitution, hướng dẫn W1–W2,
database README, spec Feature 110, 111, 120 và booking nếu có.

Chia task nhỏ, có thứ tự/dependency; bao phủ User Stories, FR, SC, authorization
server-side, quyền sở hữu dữ liệu, validation, loading/empty/error/offline/retry
và edge cases. Đối chiếu schema hiện có, không tự bịa schema/field/status/nghiệp vụ.
Bổ sung kiểm thử theo Constitution: MVVM, XML/accessibility, light/dark và phone/w600dp.
Ghi dependency Feature 110/111/120 và booking; không giải quyết nghiệp vụ feature khác.
Giữ package/file ownership theo plan.

Chỉ tạo/cập nhật tài liệu Tasks trong specs/112-owner-operations/. Không sửa spec,
plan, database schema hoặc spec feature khác; không chạy analyze/implement và không
viết implementation code.
```

**Phân tích:**

```text
$speckit-analyze
```

**Cập nhật theo Specification Analysis Report:**

```text
$speckit-tasks

Cập nhật Tasks Feature 112 theo findings I1, U1, U2, U3, U4 và D1.
Chỉ sửa specs/112-owner-operations/tasks.md.

Không tự thêm venueId vào schema/data model; yêu cầu database owner xác nhận ý nghĩa
venueId trong FR-013 và chặn task voucher đến khi hợp đồng dữ liệu được xác nhận.
Bổ sung usability study đo SC-007 với nhóm owner, công thức tỷ lệ, thao tác và điều kiện
hoàn tất không cần hỗ trợ; không tự đặt cỡ mẫu/ngưỡng mới.
Gộp task setup trùng; làm rõ test file, chức năng, lệnh/harness có thật, còn thiếu thì
ghi dependency thay vì bịa đường dẫn/function.
Tách dependency booking cancellation với checkin-booking; Phase 8 chỉ rà soát coverage
và chạy lại suite, không lặp test đã giao trong story.
Giữ task ID nếu có thể, cập nhật tham chiếu khi gộp/xóa task. Không sửa spec, plan,
data-model, database README hoặc feature khác; không chạy analyze/implement.
```

### Tóm tắt phản hồi của AI

- Đối chiếu spec, plan, data-model, contract boundary, quickstart, Constitution, database README và spec liên quan. Setup/prerequisite scripts trỏ nhầm Feature 120; tài liệu Feature 112 được phân tích trực tiếp theo đường dẫn đã chỉ định.
- Tạo Tasks ban đầu gồm 56 task, năm User Story, phase nền tảng/polish, dependency, MVP và Coverage Matrix.
- `$speckit-analyze` ghi nhận sáu findings: ý nghĩa `venueId` của voucher; SC-007 chưa có phép đo usability; T001–T002 trùng lặp; test thiếu path/harness cụ thể; dependency giữa booking cancellation và booking check-in chưa tách; test bị lặp ở Phase 8.
- Cập nhật Tasks thành 58 task checklist có ID duy nhất. Gộp setup task trùng thành T001; thêm gate xác nhận database owner về `venueId`, usability study SC-007 và transaction test approve/reject.
- T006 ghi rõ database owner cần xác nhận `venueId` trong FR-013 là context từ đường dẫn `venues/{venueId}/vouchers/{code}` hay field cần lưu. Không thêm field schema/data model; voucher tasks bị chặn khi chưa có quyết định.
- Usability study SC-007 đo bốn thao tác: duyệt/từ chối booking, mở drop-in, check-in QR và tạo voucher. Tỷ lệ là số owner hoàn thành không cần hỗ trợ trực tiếp chia tổng owner tham gia. Cỡ mẫu và điều kiện chi tiết cần được xác nhận.
- Làm rõ Rules test dùng `firestore-tests` và `npm test`. Edge Function test location/harness chưa có trong checkout nên các task liên quan yêu cầu xác nhận trước khi tạo test. Tách T005 cho booking và T006 cho drop-in, registration check-in, voucher; `checkin-booking` không phụ thuộc booking cancellation.
- Phase 8 chỉ rà soát coverage và chạy lại suite đã tạo. Không sửa spec, plan, data-model, database README hoặc artifact feature khác; không chạy implementation hay tests.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                  | Quyết định                                                                     | Lý do                                                                   |
| --------------------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| T001–T002             | Gộp thành task rà soát coverage và file ownership                              | Hai task setup trùng mục tiêu                                           |
| T005/T006             | Tách booking contract khỏi drop-in, registration check-in và voucher contracts | Tránh dependency không liên quan                                        |
| Voucher `venueId`     | Thêm xác nhận database owner và gate cho voucher tasks                         | Tài liệu chưa thống nhất; không tự thêm schema                          |
| T009, T012–T013       | Chỉ rõ Rules test path/command; đặt điều kiện cho function transaction tests   | Rules harness có sẵn; Edge Function harness/path chưa có trong checkout |
| T022–T023, T032, T039 | Ghi rõ test behavior và dependency xác nhận function path/harness              | Không dùng Rules tests để khẳng định transaction/token phía server      |
| T035                  | Chuyển thành integration handoff cho `checkin-booking`                         | Không sửa function thuộc feature khác                                   |
| T038–T042             | Chặn voucher implementation đến khi dữ liệu/contract được xác nhận             | Không suy đoán `venueId` là context hay stored field                    |
| T052–T054             | Chuyển Phase 8 thành rà soát coverage và chạy lại suite                        | Tránh lặp test/layout đã giao trong từng story                          |
| T058                  | Bổ sung usability study đo SC-007, giữ ngưỡng ≥90%                             | Cỡ mẫu/điều kiện nghiên cứu chưa có trong spec                          |
| Artifact khác         | Không chỉnh sửa                                                                | Giữ phạm vi ở Tasks Feature 112                                         |

### Cách kiểm chứng

- Kiểm tra có 58 checklist task, ID không trùng và format task hợp lệ.
- Các task test dùng harness/lệnh đã có khi repository cung cấp; phần còn thiếu được ghi thành dependency/gate.
- Không chạy tests, `$speckit-analyze` hoặc `$speckit-implement` sau lần cập nhật Tasks.

## Mục 15: Tạo, phân tích và cập nhật Tasks Feature 120 – Admin

| Trường                   | Nội dung                                                                                                                                       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-19                                                                                                                                     |
| Người thực hiện          | Minh Nhựt                                                                                                                                      |
| Nhóm tính năng liên quan | 12 – Quản trị và kiểm duyệt (spec `specs/120-admin`)                                                                                           |
| Công cụ AI               | Codex (GPT-6), `$speckit-tasks`, `$speckit-analyze`                                                                                            |
| Mục đích                 | Tạo Tasks Feature 120, phân tích độ khớp spec/plan/tasks và cập nhật theo findings mà không tự quyết định schema hoặc hợp đồng tích hợp còn mở |
| Nhánh Git                | Chưa ghi nhận                                                                                                                                  |

### Prompt đã dùng

**Tạo Tasks lần đầu:**

```text
$speckit-tasks

Tạo danh sách Tasks cho Feature 120 – Admin.

Trước khi tạo tasks, đọc và đối chiếu:
- specs/120-admin/spec.md
- specs/120-admin/plan.md
- .specify/memory/constitution.md
- docs/guides/w1-w2-spec-assignment.md
- docs/design/database/README.md
- Các spec liên quan Feature 100, 110, 111, 112 và booking nếu có.

Chia task nhỏ, có thứ tự/dependency; bao phủ User Stories, FR, SC, server-side
authorization, quyền Admin, kiểm tra truy cập trái phép, validation, loading,
empty, error, offline, retry và edge cases. Đối chiếu database, không tự bịa
schema/field/status/nghiệp vụ. Bổ sung kiểm thử theo Constitution gồm MVVM,
XML/accessibility, light/dark và phone/w600dp.

Ghi dependency với notification, owner onboarding, venue setup, owner operations
và booking; không giải quyết nghiệp vụ feature khác. Giữ package/file ownership
theo plan. Chỉ tạo/cập nhật Tasks trong specs/120-admin/. Không sửa spec, plan,
schema hoặc spec feature khác; không chạy analyze/implement và không viết code.
```

**Phân tích:**

```text
$speckit-analyze
```

**Cập nhật theo Specification Analysis Report:**

```text
$speckit-tasks

Cập nhật Tasks cho Feature 120 theo findings U1, U2, U3.

Chỉ sửa specs/120-admin/tasks.md. Đọc lại spec, plan, tasks, Constitution,
database README và các spec sở hữu report target.

U1: Xác minh cấu trúc repository trước khi ghi file path cụ thể. Bổ sung path
khi cấu trúc và contract đã xác định; nếu chưa đủ thông tin, ghi điều kiện cần
chốt, không tự bịa endpoint, schema hoặc cấu trúc triển khai.

U2: Định nghĩa cách đo SC-006: thời điểm bắt đầu/kết thúc, hồ sơ đầy đủ theo spec,
ghi nhận thời gian và tiêu chí đạt/không đạt. Nếu spec chưa định nghĩa đủ thì
ghi câu hỏi cần xác nhận.

U3: Rà soát T025–T027. Thêm dependency gate xác nhận nguồn dữ liệu và quyền đọc
target với owners Feature 070, 080, 081, 090, 091 và các feature sở hữu dữ liệu
được report sử dụng. Tách việc xử lý report khỏi việc đọc target; chỉ đọc target
sau khi hợp đồng và quyền truy cập được xác nhận. Không giao Feature 120 sửa
schema/code feature khác.

Giữ Task ID nếu có thể; cập nhật dependency và truy vết. Không sửa spec, plan,
database README hoặc artifact feature khác; không chạy analyze, plan, implement
hoặc viết implementation code.
```

### Tóm tắt phản hồi của AI

- Tạo `specs/120-admin/tasks.md` với **54 task**, theo bốn User Stories và các phase setup, foundational, polish; có dependency graph, traceability FR/SC, tiêu chí nghiệm thu độc lập và MVP đề xuất.
- `$speckit-analyze` ghi nhận ba finding MEDIUM:
  - **U1:** nhiều task chỉ nêu thư mục, chưa xác định file đích.
  - **U2:** SC-006 có giới hạn hai phút nhưng chưa quy định cách đo và định nghĩa hồ sơ đầy đủ.
  - **U3:** chưa chốt quyền đọc target được tham chiếu bởi `reports.targetPath`.
- Cập nhật Tasks, giữ nguyên 54 task IDs.
- U1: xác minh repository hiện chỉ có Android starter; package admin và `supabase/functions/`, `supabase/migrations/` chưa tồn tại. `firestore-tests/test/base.test.ts` xác nhận convention test `.test.ts`. Tasks dùng path test TypeScript cụ thể theo convention; tên file Android và server function tiếp tục là điều kiện cần chốt vì plan/contracts chưa quy định class/file layout hoặc callable endpoint.
- U2: T054 quy định bắt đầu đo khi Admin chọn hồ sơ PENDING trong hàng đợi, dừng khi app hiển thị kết quả được server xác nhận, chạy riêng luồng duyệt và từ chối, ghi timestamp và elapsed time; đạt nếu mỗi luồng không quá 120 giây.
- Định nghĩa hồ sơ đầy đủ ở T054 dựa trên Feature 110 FR-002/Validation: các trường bắt buộc, ít nhất một ảnh cơ sở, ít nhất một giấy tờ chứng minh hợp lệ và trạng thái PENDING. Cần Feature 110 owner xác nhận các mục này đã là danh sách đầy đủ trước khi cố định fixture.
- U3: T005 thành gate xác nhận nguồn target và quyền đọc theo từng loại với owners Feature 070/080/081/090/091 và data owners 010/111/112. T025 tách đọc report khỏi đọc target; T026–T027 chỉ cho xử lý target theo hợp đồng owner xác nhận. Feature 120 không sửa schema/code của feature khác.
- Không sửa spec, plan, database README, code hoặc tài liệu feature khác; không chạy tests, `$speckit-plan`, `$speckit-implement` hay analyze lần nữa sau cập nhật.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                                            | Quyết định                                                  | Lý do                                                                         |
| --------------------------------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------------------------------- |
| T005                                                            | Mở rộng thành dependency gate cho report targets            | Quyền đọc report không tự động cấp quyền đọc dữ liệu target                   |
| T009, T014–T015                                                 | Ghi điều kiện chốt server function/file path                | Repository chưa có Supabase function layout hoặc callable contract            |
| T010–T011, T022, T031                                           | Ghi path test `.test.ts` cụ thể theo convention đã xác minh | `firestore-tests/test/base.test.ts` là test file hiện có                      |
| T012, T013, T021, T030                                          | Giữ tên file Android là nội dung cần xác định sau           | Chưa có package admin hoặc convention test theo feature trong source          |
| T025–T027, T034                                                 | Gắn target read/action với gate và owner tương ứng          | Phân biệt moderation report với quyền truy cập dữ liệu do feature khác sở hữu |
| T054                                                            | Bổ sung quy trình đo SC-006 và điều kiện hồ sơ đầy đủ       | Cần kết quả đo có thể lặp lại, nhưng không tự đặt nghiệp vụ ngoài spec        |
| Dependency graph, traceability, acceptance criteria, open gates | Cập nhật tham chiếu T005 và SC-006                          | Phản ánh các điều kiện mới và giữ coverage FR/SC                              |
| Các task còn lại                                                | Giữ nguyên ID và nội dung                                   | Không liên quan các findings                                                  |

### Cách kiểm chứng

- Xác nhận các task IDs vẫn duy nhất và tổng số task là 54.
- Kiểm tra cấu trúc hiện tại: có `firestore-tests/test/base.test.ts`; chưa có `app/src/main/java/com/example/booking_app/feature/admin`, `supabase/functions` hoặc `supabase/migrations`.
- Đối chiếu report target với database README và spec Feature 070, 080, 081, 090, 091; ghi rõ quyền/source vẫn cần owners xác nhận.
- Đối chiếu SC-006 với Feature 110 FR-002/Validation; để lại câu hỏi xác nhận nếu còn trường bắt buộc chưa được tài liệu xác định.
- Không chạy tests hoặc implementation. Không sửa file nào ngoài `specs/120-admin/tasks.md` trong lượt cập nhật Tasks.

---

## Mục 16: Implement Feature 111 — Owner Venue Setup

| Trường                   | Nội dung                                                                                      |
| ------------------------ | --------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-29                                                                                    |
| Người thực hiện          | Minh Nhựt                                                                                     |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                       |
| Công cụ AI               | Codex, `$speckit-implement`                                                                   |
| Mục đích                 | Implement các phần có thể triển khai của Feature 111, kiểm tra phân quyền server và viết test |

### Prompt đã dùng

```text
$speckit-implement Bắt đầu implement Feature 111 — Owner Venue Setup.

Thư mục spec chính xác là `specs/111-owner-venue-setup/`.

Đọc trực tiếp các file:
- `specs/111-owner-venue-setup/spec.md`
- `specs/111-owner-venue-setup/plan.md`
- `specs/111-owner-venue-setup/data-model.md`
- `specs/111-owner-venue-setup/research.md`
- `specs/111-owner-venue-setup/quickstart.md`
- Toàn bộ file trong `specs/111-owner-venue-setup/contracts/`, `checklists/` và `ui/`
- `specs/111-owner-venue-setup/tasks.md` nếu tồn tại; nếu chưa có, xác định workflow triển khai theo cấu trúc Spec Kit hiện có, không tự tạo backlog thay thế khi chưa kiểm tra.

Sau đó đọc Constitution, database README/schema và code hiện tại liên quan đến Feature 110, 120, venue, courts, pricing và slots.

Bắt đầu implement ngay, không chỉ audit hoặc lập kế hoạch.

1. Kiểm tra từng chức năng đã có để tránh implement trùng lặp.
2. Xác định task đầu tiên có dependency sẵn sàng và sửa source code/test thực tế.
3. Tiếp tục các chức năng còn thiếu của Feature 111 theo spec, bao gồm venue management, courts, `priceRules` và block slots nếu chúng thuộc phạm vi được quy định.
4. Bảo đảm server xác minh Owner đã được duyệt và venue thuộc quyền sở hữu của Owner hiện tại.
5. Tôn trọng lifecycle tạo venue của Feature 120; không tự tạo venue để vượt qua dependency.
6. Tuân thủ schema và contract hiện có. Không tự thêm field, collection, endpoint hoặc dependency khi chưa có căn cứ.
7. Viết và chạy các test phù hợp. Không tuyên bố test pass nếu chưa chạy.
8. Nếu một phần bị block bởi Feature 110 hoặc quyết định chưa được xác nhận, tiếp tục những phần độc lập.
9. Cập nhật task theo bằng chứng thực tế.
10. Bảo toàn các thay đổi hiện có; không commit hoặc push.

Kết thúc bằng báo cáo những file đã thay đổi, chức năng đã implement, kết quả kiểm thử và blocker còn lại.

Hãy sửa code ngay trong lượt này và tiếp tục qua các task có thể triển khai, không dừng sau khi đọc tài liệu.
```

### Tóm tắt phản hồi của AI

- Đọc spec, plan, data model, research, quickstart, contract, checklist, Constitution, database README và code liên quan.
- Xác định các cổng chưa đủ điều kiện: contract transaction lock/unlock T007 và authority cập nhật `minPricePerHour` T008.
- Thêm validator domain cho venue, court, `priceRules` và khoảng khóa slot 30 phút; không thêm schema/enum mới.
- Cập nhật `firestore.rules` để yêu cầu đồng thời `appRole = OWNER`, `users/{uid}.ownerStatus = APPROVED` và `venues/{venueId}.ownerId == uid`; public chỉ đọc venue `ACTIVE`, owner được đọc venue của mình.
- Không mở quyền client tự tạo venue đầu tiên; giữ lifecycle tạo venue của Feature 110/120.
- Thêm unit tests và Emulator Rules tests cho approved/pending/foreign owner, protected fields và hidden venue.
- Đánh dấu các task đã có bằng chứng: T001, T003, T004, T006, T009, T016, T021, T027, T032.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                       | Quyết định      | Lý do                                                                               |
| ------------------------------------------ | --------------- | ----------------------------------------------------------------------------------- |
| Validation domain                          | Dùng và chỉnh   | Bám schema hiện có, kiểm tra field/enum/ranh giới đã được đặc tả                    |
| Firestore Rules                            | Dùng và chỉnh   | Bắt buộc xác minh owner đã được duyệt và ownership ở server                         |
| Unit/Emulator tests                        | Dùng và chỉnh   | Kiểm chứng validation và zero cross-owner write                                     |
| Venue/court/price CRUD UI                  | Chưa triển khai | Cần tiếp tục các task còn lại theo dependency và package hiện có                    |
| Price mutation ảnh hưởng `minPricePerHour` | Chưa triển khai | Chờ quyết định T008                                                                 |
| Atomic slot lock/unlock                    | Chưa triển khai | Chờ contract server/booking T007; không tự tạo endpoint hoặc client read-then-write |

### Cách kiểm chứng

- `./gradlew.bat --no-daemon testDebugUnitTest --tests 'com.example.booking_app.feature.owner.domain.OwnerValidationTest'`: `BUILD SUCCESSFUL`.
- Firebase Emulator Rules suite: 4 test files, 38 tests passed.
- `git diff --check`: không có lỗi whitespace.
- Không commit hoặc push.

---

## Mục 17: Tiếp tục implement Feature 111 — Venue và Court Management

| Trường                   | Nội dung                                                                         |
| ------------------------ | -------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-29                                                                       |
| Người thực hiện          | Minh Nhựt                                                                        |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                          |
| Công cụ AI               | Codex, `$speckit-implement`                                                      |
| Mục đích                 | Hoàn thiện repository, venue/court management UI và kiểm thử theo tasks hiện tại |

### Prompt đã dùng

```text
$speckit-implement Tiếp tục implement Feature 111 — Owner Venue Setup từ trạng thái hiện tại.

Đọc specs/111-owner-venue-setup/tasks.md và code hiện có. Giữ nguyên các task đã hoàn thành; không implement lại validation hoặc authorization đã có nếu không phát hiện lỗi cụ thể.

Mục tiêu lượt này: hoàn thiện Venue Management và Court Management bằng code thực tế; hoàn thiện T002/T005, venue list/detail/add/edit, hide, acceptsRecurring, court list/create/update/reorder/deactivate, unit tests và Emulator tests. Tuân thủ MVVM, schema hiện có, lifecycle Feature 110/120, không sửa Feature 030/040/041/050/120, không mở rộng Rules tùy tiện, chạy test/build nếu môi trường cho phép, cập nhật tasks, không commit/push. Nếu blocker thật sự thì tiếp tục task độc lập và ghi rõ blocker; bắt đầu implement ngay, ưu tiên venue CRUD có test rồi court management.
```

### Tóm tắt phản hồi của AI

- Hoàn thiện domain models, repository interface, Firestore data source/mapping và Hilt binding cho `venues`, `courts`, `priceRules`, `slots`.
- Thêm `StateFlow` ViewModel cho venue list/detail, loading/empty/error/retry, server-confirmed update, hide và recurring toggle; giữ dữ liệu form khi ghi thất bại.
- Thêm XML/ViewBinding UI cho venue list/detail/edit, court list/add/edit/reorder/deactivate, phone và `w600dp` layouts.
- Giữ quyền tạo venue đầu tiên theo Feature 110/120; nút thêm venue ghi rõ server boundary, không tự mở quyền client tạo venue.
- Thêm fixtures Kotlin/TypeScript và Emulator tests cho venue/court ownership, pending/foreign owner, protected fields, `LOCKED`, hide, recurring toggle và slot history.
- Cập nhật task đã có bằng chứng: T002, T005, T010, T012–T015, T017–T020; T011 vẫn chưa đánh dấu vì client-side venue creation bị chặn đúng contract lifecycle.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                  | Quyết định                              | Lý do                                                             |
| ------------------------------------- | --------------------------------------- | ----------------------------------------------------------------- |
| Domain/repository/data source hiện có | Dùng và mở rộng                         | Bổ sung mapping và repository interface theo schema hiện tại      |
| Venue/court UI MVVM                   | Dùng và chỉnh                           | Thêm list/detail, StateFlow, loading/error/retry và court actions |
| Venue-first creation                  | Giữ nguyên boundary                     | Không bypass lifecycle tạo venue của Feature 110/120              |
| Firestore Rules                       | Dùng và chỉnh trong phạm vi Feature 111 | Xác minh approved owner, ownership và protected fields            |
| Feature 030/040/041/050/120 source    | Không chỉnh sửa                         | Nghiệp vụ thuộc feature khác                                      |
| Hard-delete venue/court               | Bỏ                                      | Dùng HIDDEN/isActive=false để giữ lịch sử                         |

### Cách kiểm chứng

- Android owner unit tests: `BUILD SUCCESSFUL`.
- `assembleDebug`: `BUILD SUCCESSFUL`.
- Firebase Emulator suite: 5 test files, 42 tests passed.
- Không commit hoặc push.

---

## Mục 18: Tiếp tục implement Feature 111 — Price rules, Slot UI và Public Availability

| Trường                   | Nội dung                                                                                                                                          |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-29                                                                                                                                        |
| Người thực hiện          | Minh Nhựt                                                                                                                                         |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                                                                           |
| Công cụ AI               | Codex, `$speckit-implement`                                                                                                                       |
| Mục đích                 | Tiếp tục implement từ code hiện tại, chỉ làm các phần không bị T007/T008 block và không viết lại CRUD, validator hoặc authorization đã hoàn thành |

### Prompt đã dùng

```text
$speckit-implement Tiếp tục implement Feature 111 — Owner Venue Setup từ code hiện tại.

Đọc specs/111-owner-venue-setup/tasks.md, các spec liên quan và kiểm tra các file code/test thực tế. Giữ nguyên các task đã hoàn thành; không implement lại venue/court CRUD, validator hoặc authorization nếu chúng đã đáp ứng acceptance criteria.

## Ưu tiên trong lượt này

### 1. Price rules — làm phần không bị block

- Kiểm tra T021 và các validator/test hiện có.
- Thực hiện T024: đối chiếu contract hiển thị giá và cách tính giá cho slot 30 phút với Feature 040/050.
- Thực hiện phần độc lập của T025: unit tests cho price-rule validation, overlap, boundary và bảo toàn giá trong booking history.
- Có thể chuẩn bị UI shell xem coverage và cảnh báo khoảng giờ thiếu rule, nhưng không cho tạo/sửa/xóa price rule nếu T008 chưa xác định authority cập nhật `minPricePerHour`.
- Không tự đặt giá trị `minPricePerHour`, không sửa `bookings.items[].price`, không thay đổi code của Feature 040/050.

### 2. Slot management — hoàn thiện phần UI độc lập

- Thực hiện T030 ở phạm vi được phép: calendar UI, chọn khoảng giờ, chọn lý do `MAINTENANCE`/`EVENT`/`WALK_IN`, loading/progress, ngăn submit trùng, trạng thái stale/offline và giữ dữ liệu khi lỗi.
- Tôn trọng T007: chưa nối thao tác ghi lock/unlock nếu contract atomic server-side chưa được xác nhận.
- Không dùng client read-then-write để thay thế transaction.
- Không implement unlock khi chưa chứng minh được block có thể được Owner gỡ an toàn.

### 3. Public availability và cross-feature verification

Tiếp tục T033–T036:

- Kiểm tra các field Feature 111 ghi có phù hợp với contract đọc hiện tại của Feature 030/040 không.
- Xác minh `ACTIVE`/`HIDDEN`/`LOCKED`, court `isActive`, coverage của price rules và slot `BLOCKED`.
- Kiểm tra toggle `acceptsRecurring` tương thích với Feature 041: tắt recurring không tạo chuỗi mới nhưng không hủy chuỗi cũ.
- Kiểm tra điều kiện đóng sân cả ngày theo contract Feature 040.
- Thêm integration assertions trong `firestore-tests/test/owner-public-availability.test.ts`.
- Chỉ sửa test và contract thuộc phạm vi Feature 111; không sửa source/UI của Feature 030/040/041/050.

### 4. Polish và kiểm thử

Tiếp tục T037–T039:

- Kiểm tra loading/content/empty/error, retry, permission error, conflict refresh, retained form values và dữ liệu cache cũ.
- Kiểm tra accessibility, tiếng Việt, light/dark, phone và `w600dp`.
- Cập nhật quickstart với lệnh chạy và kết quả kiểm thử thực tế.
- Rà soát ranh giới Feature 110, 112/080, 030/040/041/050 và 120.

### Quy tắc

- Đọc contract và code hiện tại trước khi sửa.
- Chỉ implement phần còn thiếu, không viết lại phần đã hoàn thành.
- Không tự thay đổi schema, endpoint, field hoặc nghiệp vụ đã được duyệt.
- Không tự đánh dấu T008/T007 hoàn thành nếu chưa có quyết định thực tế từ bên chịu trách nhiệm.
- Chạy unit tests, Gradle build và Firebase Emulator tests nếu môi trường cho phép.
- Cập nhật checkbox.
- Không commit hoặc push.

**Bắt đầu sửa code ngay và tiếp tục qua các task có thể triển khai an toàn. Không dừng ở audit hoặc lập kế hoạch.**

Báo cáo cuối cùng: file đã thay đổi, task hoàn thành, kết quả test, task bị chặn và quyết định cần xác nhận.
```

### Tóm tắt phản hồi của AI

| Hạng mục            | Thay đổi thực tế                                                                                                                                                                              | Bằng chứng                                          |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Price rules         | Thêm `PriceRuleCoverage`: `pricePerHour / 2`, tìm rule theo ngày/giờ, phát hiện mốc thiếu rule và cảnh báo deletion gap; không ghi `minPricePerHour` và không sửa booking history             | Android owner unit tests pass                       |
| Slot UI             | Thêm `OwnerSlotBlockViewModel`, `OwnerSlotBlockFragment` và layout; có range preview, reason selection, validation, progress, stale/offline, retry, retained form; submit không ghi Firestore | Android owner unit tests pass; T007 vẫn block write |
| Public availability | Thêm assertions cho visibility venue, court inactive, price coverage, BLOCKED slot, full-day closure, recurring group hiện có và historical price snapshot                                    | Emulator 6 files / 46 tests passed                  |
| Tài liệu            | Cập nhật `tasks.md`, `quickstart.md` và AI log                                                                                                                                                | Đối chiếu task markers và lệnh test thực tế         |

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                   | Quyết định                    | Lý do                                                                       |
| -------------------------------------- | ----------------------------- | --------------------------------------------------------------------------- |
| PriceRuleCoverage và validator hiện có | Dùng và mở rộng test/coverage | Đối chiếu giá slot 30 phút và phát hiện gap mà không ghi dữ liệu            |
| Slot block UI shell                    | Dùng và chỉnh                 | Bổ sung preview range, reason, stale/offline/loading/retry và retained form |
| Slot lock/unlock write                 | Chưa dùng                     | T007 chưa có atomic server contract và điều kiện unlock an toàn             |
| `minPricePerHour` mutation             | Chưa dùng                     | T008 chưa xác định trusted authority                                        |
| Feature 030/040/041/050 source/UI      | Không chỉnh sửa               | Chỉ thêm contract assertions trong phạm vi Feature 111                      |

### Task cập nhật

- Đánh dấu hoàn thành: T024, T025, T033, T035, T036.
- Giữ nguyên chưa hoàn thành: T007, T008, T011, T022, T023, T026, T028, T029, T030 (chỉ UI shell), T031, T034, T037, T038, T039.

### Blocker và quyết định cần xác nhận

- T007 cần contract server-side atomic cho lock/unlock; không được thay bằng read-then-write client.
- T008 cần authority đáng tin cậy cho `minPricePerHour`; không được tự cho phép price-rule mutation ảnh hưởng field này.
- T011 và venue đầu tiên vẫn thuộc lifecycle Feature 110/120.
- T034 cần boundary/API từ Feature 041 để chứng minh đầy đủ việc tắt recurring không tạo chain mới; lượt này chỉ chứng minh chain hiện có không bị thay đổi.

### Cách kiểm chứng

- `./gradlew.bat --no-daemon testDebugUnitTest --tests 'com.example.booking_app.feature.owner.*' assembleDebug`: `BUILD SUCCESSFUL`.
- Firebase Emulator: `6 test files, 46 tests passed`.
- `git diff --check`: không có lỗi whitespace; chỉ có cảnh báo line-ending CRLF hiện hữu.
- Không commit hoặc push.

---

## Mục 19: Tiếp tục implement Feature 111 — T026, T037, T038 và T039

| Trường                   | Nội dung                                                                                                                                   |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Ngày                     | 2026-10-29                                                                                                                                 |
| Người thực hiện          | Minh Nhựt                                                                                                                                  |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                                                                    |
| Công cụ AI               | Codex, `$speckit-implement`                                                                                                                |
| Mục đích                 | Hoàn thiện các phần độc lập của price-rule Emulator tests, error handling, UI polish và boundary traceability; không bypass T007/T008/T011 |

### Prompt đã dùng

```text
$speckit-implement Tiếp tục implement Feature 111 — Owner Venue Setup từ code hiện tại.

Đọc specs/111-owner-venue-setup/tasks.md cùng code/test hiện tại. Giữ nguyên các phần đã hoàn thành; chỉ bổ sung phần còn thiếu. Không commit hoặc push.

Hoàn thiện T026 bằng owner-price-rules Emulator tests cho approved owner, ownership, foreign owner, protected minPricePerHour, schema invariant và booking snapshot; vì T008 chưa xác nhận, không implement price mutations. Hoàn thiện phần độc lập T037 cho permission-vs-empty, retry/retained form, duplicate submit, stale/cached data, uncertain mutation result và conflict refresh. Kiểm tra T038 về resources, accessibility, touch targets, light/dark, phone/w600dp, chạy lint/build/tests và chỉ ghi SLA khi có đo thực tế. Kiểm tra T034 theo boundary Feature 041, không sửa source/UI Feature 041; nếu không chứng minh được thì giữ mở. Xác minh T039 với Feature 110, 112/080, 030/040/041/050 và 120; giữ T007, T008, T011 là decision gates. Chạy Android unit tests, assembleDebug, Firebase Emulator tests và git diff --check; cập nhật tasks.
```

### Tóm tắt phản hồi của AI

- Thêm `firestore-tests/test/owner-price-rules.test.ts`: approved owner CRUD dưới venue sở hữu, pending/foreign deny, protected `minPricePerHour`, extra-field deny và booking snapshot preservation.
- Bổ sung `draft` vào `OwnerVenueDetailUiState` để retry/load lỗi không ghi đè dữ liệu form; thêm test phân biệt permission error với empty và duplicate-submit prevention.
- Không sửa Feature 041; T034 vẫn mở vì boundary hiện có không chứng minh được việc ngăn tạo recurring chain mới.
- Đánh dấu T026 và T039 hoàn thành. T037/T038 vẫn mở vì chưa có đầy đủ bằng chứng cho uncertain-network conflict refresh và SLA/performance đo trên thiết bị/mạng 4G thật.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                            | Quyết định          | Lý do                                                                   |
| ------------------------------- | ------------------- | ----------------------------------------------------------------------- |
| Owner venue ViewModel           | Chỉnh sửa tối thiểu | Lưu `draft` để retry/load lỗi không ghi đè form chưa lưu                |
| Unit test fake repository       | Mở rộng             | Kiểm tra permission-vs-empty và duplicate submit khi mutation đang chạy |
| Price-rule mutations            | Không implement     | T026 chỉ kiểm tra Rules/invariant; T008 vẫn mở                          |
| Feature 041 recurring source/UI | Không chỉnh sửa     | Không có boundary đủ để chứng minh ngăn chain mới; giữ T034 mở          |
| Performance SLA                 | Không tuyên bố đạt  | Chỉ chạy build/lint/test, chưa đo thiết bị/mạng 4G thật                 |

### Cách kiểm chứng

- Android owner unit tests: `BUILD SUCCESSFUL`.
- `assembleDebug lintDebug`: `BUILD SUCCESSFUL`; lint report đã được tạo.
- Firebase Emulator: **7 test files, 50 tests passed**.
- `git diff --check`: không có lỗi whitespace.
- Không commit hoặc push.

---

## Mục 20: Tiếp tục implement Feature 111 — T037, T038 và T034

| Trường                   | Nội dung                                                                                                                                    |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-29                                                                                                                                  |
| Người thực hiện          | Minh Nhựt                                                                                                                                   |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                                                                     |
| Công cụ AI               | Codex, `$speckit-implement`                                                                                                                 |
| Mục đích                 | Bổ sung error classification, conflict refresh, retained draft, no-blind-retry, freshness indicator và kiểm tra lại boundary Feature 041/UI |

### Prompt đã dùng

```text
$speckit-implement Tiếp tục implement Feature 111 — Owner Venue Setup từ code hiện tại.

Đọc specs/111-owner-venue-setup/tasks.md, quickstart và code/test thực tế. Giữ nguyên các task đã hoàn thành. Không commit hoặc push.

Hoàn thiện T037 cho permission/network/conflict/empty, uncertain mutation result, authoritative conflict refresh, retained form, duplicate submit và cache freshness; không tự thêm API hoặc server contract. Hoàn thiện T038 bằng kiểm tra/sửa Vietnamese resources, accessibility, touch targets, Material 3, phone/w600dp, loading/error/empty và chạy lint/build; không tuyên bố SLA nếu chưa đo trên thiết bị/4G. Kiểm tra T034 theo boundary Feature 041, không sửa source/UI Feature 041 và giữ mở nếu không có API/boundary chứng minh ngăn recurring chain mới. Giữ T007/T008/T011 là decision gates. Chạy Android unit tests, assembleDebug, lintDebug, Firebase Emulator tests và git diff --check; cập nhật tasks, quickstart.
```

### Tóm tắt phản hồi của AI

- Thêm `OwnerErrorClassifier` phân biệt permission, network, conflict và unknown error.
- Bổ sung `errorKind`, `isFresh` cho venue list/detail; hiển thị freshness server-confirmed và thông báo Vietnamese cho permission/network.
- Conflict chỉ kích hoạt authoritative read-back; timeout/network không tự retry mutation. Draft venue được giữ khi refresh conflict/error.
- Bổ sung unit tests cho classifier, conflict refresh, retained draft và no-blind-retry.
- Kiểm tra Feature 041: chỉ có spec/plan/schema recurring, chưa có boundary/API cho điều kiện không tạo chain mới; giữ T034 mở.
- T038 chưa đánh dấu vì chưa đo SLA trên thiết bị và mạng 4G thật.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                        | Quyết định          | Lý do                                                                      |
| --------------------------- | ------------------- | -------------------------------------------------------------------------- |
| Venue list/detail ViewModel | Chỉnh sửa tối thiểu | Thêm error kind, freshness và conflict read-back; không viết lại flow CRUD |
| Mutation retry              | Không tự động retry | Tránh gửi lại mù khi timeout/mất mạng; chỉ retry read sau conflict         |
| Cache repository            | Không thêm          | Code hiện tại không dùng cache; `isFresh` không giả lập cached authority   |
| Slot/price write            | Không thêm          | T007/T008 vẫn chưa được xác nhận                                           |
| Feature 041 source/UI       | Không chỉnh sửa     | Không có boundary được phép để kiểm chứng T034                             |
| Performance SLA             | Không tuyên bố đạt  | Chưa có thiết bị/mạng 4G phù hợp để đo                                     |

### Cách kiểm chứng

- Android owner unit tests: `BUILD SUCCESSFUL`.
- `assembleDebug lintDebug`: `BUILD SUCCESSFUL`; lint report đã được tạo.
- Firebase Emulator: **7 test files, 50 tests passed**.
- `git diff --check`: không có lỗi whitespace; chỉ có cảnh báo line-ending CRLF.
- Không commit hoặc push.

---

## Mục 21: Tiếp tục implement Feature 111 — Decision gates và T037

| Trường                   | Nội dung                                                                                                             |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Ngày                     | 2026-10-29                                                                                                           |
| Người thực hiện          | Minh Nhựt                                                                                                            |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                                              |
| Công cụ AI               | Codex, `$speckit-implement`                                                                                          |
| Mục đích                 | Chốt bằng chứng an toàn cho T007/T008/T011, hoàn thiện T037 và kiểm chứng UI/build/Emulator mà không bypass contract |

### Prompt đã dùng

```text
$speckit-implement Tiếp tục Feature 111 — Owner Venue Setup. Tôi muốn chốt các quyết định thuộc phạm vi Owner Venue Setup để tiếp tục triển khai, thay vì chỉ giữ backlog ở trạng thái chờ.

Đọc contract, spec và code hiện tại. Giữ nguyên các phần đã hoàn thành; không commit hoặc push.

T007: chỉ dùng server-controlled atomic transaction tương thích Feature 040; không client read-then-write; nếu chưa chứng minh server boundary và quyền unlock thì giữ write bị khóa, hoàn thiện UI và typed error. T008: client không ghi minPricePerHour, không bật price mutation khi authority/no-rule policy chưa được xác nhận. T011: giữ venue-first lifecycle ở Feature 110/120, không tự tạo venue đầu tiên. Tiếp tục T037/T038 độc lập, kiểm tra boundary Feature 041 cho T034 nhưng không sửa Feature 041, chạy unit/build/lint/Emulator/diff check và cập nhật tasks, quickstart, AI log theo bằng chứng thực tế.
```

### Tóm tắt phản hồi của AI

- Thêm `OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT` cho slot UI; submit preview không ghi Firestore.
- Bổ sung contract/research ghi rõ T007 chưa có server transaction boundary, T008 chưa có trusted authority/no-rule policy và T011 thuộc lifecycle Feature 110/120.
- Thêm Emulator assertion owner client không thể tự ghi slot `BLOCKED`.
- Giữ nguyên price-rule mutation, unlock write và venue-first create ở trạng thái bị khóa.
- Đánh dấu T037 hoàn thành dựa trên error classification, conflict read-back, retained draft, no-blind-retry và freshness tests. Giữ T034/T038 mở đúng bằng chứng còn thiếu.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                                | Quyết định           | Lý do                                                                     |
| ----------------------------------- | -------------------- | ------------------------------------------------------------------------- |
| Slot lock UI                        | Chỉnh sửa tối thiểu  | Thêm typed gate, vẫn chỉ preview và không ghi khi T007 chưa có boundary   |
| Slot Firestore write                | Không implement      | Rules/server contract hiện không chứng minh được atomic owner lock/unlock |
| Price-rule mutation/minPricePerHour | Không implement      | T008 thiếu authority và no-rule behavior                                  |
| Venue-first create                  | Giữ nguyên lifecycle | Feature 110/120 sở hữu approval và creation                               |
| Feature 041 source/UI               | Không chỉnh sửa      | Không có boundary được phép để hoàn tất T034                              |
| T037 error/retry handling           | Dùng và mở rộng      | Có test permission/network/conflict, retained draft và no-blind-retry     |

### Cách kiểm chứng

- Android owner unit tests, `assembleDebug`, `lintDebug`: **BUILD SUCCESSFUL**.
- Firebase Emulator: **7 test files, 51 tests passed**.
- `git diff --check`: không có lỗi whitespace; chỉ có cảnh báo line-ending CRLF.
- Không commit hoặc push.

---

## Mục 22: Kiểm toán kỹ thuật và triển khai Phase 2 Feature 111 — Bảng giá, minPricePerHour và Vòng đời Venue

| Trường                   | Nội dung                                                                                                                                                                                                                                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Ngày                     | 2026-10-30                                                                                                                                                                                                                                                                                                         |
| Người thực hiện          | Minh Nhựt                                                                                                                                                                                                                                                                                                          |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup                                                                                                                                                                                                                                                                                            |
| Công cụ AI               | Antigravity, Gemini 3.8 Flash                                                                                                                                                                                                                                                                                      |
| Mục đích                 | Thực hiện kiểm toán kỹ thuật toàn diện Feature 111 ở chế độ Read-Only, phân tích blocker T007/T008/T011/T034/T038; sau khi chốt các quyết định 1A, 2A, 3A, 4A, tiến hành triển khai Phase 2: CRUD Bảng giá, UI Bảng giá kèm cảnh báo gap, Edge Function sync-venue-price và điều hướng thêm cơ sở sang Feature 110 |

### Prompt đã dùng

**Lượt 1 — Read-only Technical Audit & Blocker Analysis**:

```text
# FEATURE 111 — OWNER VENUE SETUP
## READ-ONLY TECHNICAL AUDIT & BLOCKER ANALYSIS

Bạn là Senior Software Architect được giao tiếp nhận và đánh giá một Feature 111 đang triển khai dở dang trong dự án Android/Kotlin sử dụng Firebase.

Bạn không có context từ các Agent trước. Hãy tự đọc repository để hiểu toàn bộ kiến trúc, nghiệp vụ, tiến độ và các vấn đề còn tồn đọng.

# 1. STRICT READ-ONLY MODE — BẮT BUỘC

Đây là yêu cầu quan trọng nhất.

**Bạn chỉ được phép đọc và phân tích. Không được thay đổi bất kỳ nội dung nào trong repository.**

Các hành động bị cấm:

- Không sửa, tạo, xóa hoặc đổi tên bất kỳ file nào.
- Không chỉnh sửa source code.
- Không chỉnh sửa `tasks.md`, `spec.md`, `plan.md`, `research.md`, `quickstart.md` hoặc AI log.
- Không tạo `BLOCKER_ANALYSIS.md` hay bất kỳ file báo cáo nào.
- Không chạy formatter, linter ở chế độ sửa tự động hoặc code generator.
- Không chạy migration.
- Không thay đổi Firebase configuration, security rules hoặc database.
- Không cài đặt package hoặc cập nhật dependency.
- Không commit, push, checkout, reset, rebase hoặc stash.
- Không chạy lệnh có khả năng ghi hoặc xóa dữ liệu.

Được phép:
- Đọc file.
- Tìm kiếm nội dung trong repository.
- Đọc Git status, Git diff và Git log.
- Phân tích source code, tests, contracts và documentation.
- Chạy các lệnh kiểm tra chỉ đọc nếu chắc chắn chúng không tạo hoặc sửa file, không thay đổi trạng thái môi trường.

Nếu không chắc một lệnh có gây thay đổi hay không, không chạy lệnh đó.

**Toàn bộ báo cáo phải được in trực tiếp ra terminal. Không tạo file báo cáo.**

Trước khi bắt đầu, ghi rõ:
`READ-ONLY AUDIT MODE: ENABLED`

# 2. PROJECT CONTEXT

Feature 111 — Owner Venue Setup chịu trách nhiệm quản lý venue hiện hữu của Owner sau khi được phê duyệt.

Phạm vi có thể bao gồm:
- Venue list/detail.
- Venue information management.
- Court management.
- Price rule management.
- Slot management.
- Owner-controlled blocks.
- Recurring configuration.
- Loading, empty, error, permission, conflict và freshness states.

Hãy đọc spec để xác định phạm vi chính xác, không tự mở rộng chức năng.

Dự án sử dụng Android/Kotlin và Firebase/Firestore. Hãy xác minh chính xác kiến trúc và công nghệ từ repository.

# 3. FEATURE BOUNDARIES

Các ranh giới nghiệp vụ cần tôn trọng:

| Feature | Responsibility |
|---|---|
| Feature 030 | Các chức năng thuộc phạm vi Feature 030 |
| Feature 040 | Booking transaction và slot booking |
| Feature 041 | Recurring booking và recurring chain |
| Feature 050 | Booking price/charge |
| Feature 080/112 | Các chức năng block thuộc phạm vi tương ứng |
| Feature 110 | Owner onboarding, approval và initial venue creation |
| Feature 111 | Quản lý venue hiện hữu của Owner |
| Feature 120 | Admin venue locks và venue catalogs |

Nguyên tắc:
- Feature 111 không bypass Owner approval.
- Venue đầu tiên được tạo theo lifecycle Feature 110/120.
- Feature 111 không tự mở rộng quyền tạo venue.
- Không ghi đè hoặc gỡ admin lock.
- Không tự thay đổi booking transaction của Feature 040.
- Không sửa source/UI Feature 041.
- Không phá vỡ historical price thuộc Feature 050.
- Không thêm schema, field, enum, collection hoặc endpoint khi chưa có contract được duyệt.

# 4. CONTEXT VÀ CÁC BLOCKER ĐÃ BIẾT

Hãy xác minh từng nội dung dưới đây bằng code và tài liệu thực tế.

## T007 — Slot lock/unlock

Đã triển khai theo báo cáo trước:
- `OwnerWriteGate.SLOT_ATOMIC_SERVER_CONTRACT`.
- Slot UI submit chỉ preview khi thiếu server boundary.
- Client không ghi Firestore trực tiếp.
- Emulator assertion chứng minh Owner client không thể tự ghi slot `BLOCKED`.

Còn vướng:
- Thiếu server transaction boundary tương thích Feature 040.
- Thiếu bằng chứng phân biệt block Owner được gỡ với block thuộc Feature 120 hoặc Feature 112/080.
- Chưa chứng minh atomic operation trên toàn bộ slot range.

Phân tích:
- Schema slot.
- Security rules.
- Feature 040 transaction.
- Các implementation block/unblock hiện có.
- Server functions/services hiện hữu.
- Khả năng atomic lock/unlock không cần thay đổi schema.
- Các phương án an toàn và dependency cần xác nhận.

## T008 — Price rules và `minPricePerHour`

Đã xác định:
- Client không được ghi trực tiếp `venues.minPricePerHour`.
- Price-rule mutation đang bị khóa.

Còn vướng:
- Thiếu trusted authority cập nhật field.
- Thiếu no-rule policy.
- Chưa xác minh consistency giữa price rules và `minPricePerHour`.

Phân tích:
- Schema và nơi đọc/ghi field.
- Rule có hiệu lực.
- Feature 050 và historical price.
- Server authority hiện có.
- Tạo, sửa, xóa rule.
- Xóa rule cuối cùng.
- Transaction và consistency.
- Các quyết định nghiệp vụ còn thiếu.

Không tự chọn giá trị mặc định cho trường hợp không còn rule.

## T011 — Venue lifecycle

Đã xác định:
- Feature 110/120 sở hữu initial venue creation từ `venueDraft` sau approval.
- Feature 111 quản lý venue hiện hữu.

Còn vướng:
- Chưa có contract xác nhận quyền tạo venue bổ sung.

Phân tích lifecycle approval, ownership và cách xử lý UI khi Owner chưa có venue.

## T034 — Recurring booking

Đã xác định:
- Feature 111 quản lý `venues.acceptsRecurring`.
- Feature 041 sở hữu recurring booking và recurring chain.
- Chưa có boundary chứng minh `acceptsRecurring=false` chặn recurring chain mới.

Phân tích:
- Nơi tạo recurring chain.
- Nơi kiểm tra `acceptsRecurring`.
- Enforcement phía client/server/rules.
- Cách chặn chain mới.
- Bảo toàn recurring chain hiện có.
- Contract cần Feature 041 cung cấp.

Không sửa Feature 041.

## T037 — Error, Freshness và Recovery

Đã được đánh dấu hoàn thành.

Implementation được báo cáo:
- `OwnerErrorClassifier`.
- Phân loại permission/network-timeout/conflict-stale/unknown.
- `errorKind`, `isFresh`.
- Conflict authoritative read-back.
- Không blind retry mutation khi timeout.
- Giữ form draft.
- Unit tests cho các hành vi trên.

Hãy xác minh implementation, tests, cache/freshness và uncertain network outcome. Chỉ nêu vấn đề nếu có bằng chứng cụ thể.

## T038 — Accessibility, Responsive và SLA

Đã có:
- Accessibility content descriptions.
- Phone và `w600dp` layouts.
- Build/lint đã pass theo báo cáo trước.

Còn vướng:
- Chưa có số liệu SLA trên thiết bị và mạng 4G thật.

Phân tích:
- Accessibility.
- 48dp touch targets.
- Phone/`w600dp`.
- Light/dark.
- Material 3.
- Các UI states.
- List/calendar dưới 3 giây trên 4G.
- Write acknowledgment dưới 1 giây và completion dưới 3 giây, nếu spec xác nhận.

Phân biệt rõ điều có thể xác minh bằng code với điều phải đo thực tế.

# 5. REPOSITORY INVESTIGATION

Bắt đầu bằng:
- `git status --short`
- `git diff --stat`
- `git diff --`

Chỉ đọc, không thay đổi trạng thái Git.

Sau đó đọc toàn bộ Feature 111:
- `spec.md`
- `plan.md`
- `tasks.md`
- `research.md`
- `data-model.md`
- `contracts/`
- `quickstart.md`
- `AI_log_4.md`

Tiếp tục tìm hiểu:
- Android source code.
- ViewModels, repositories, UI và models.
- Firestore security rules.
- Firebase Functions/backend.
- Unit tests.
- Emulator tests.
- Các Feature 040, 041, 050, 110 và 120 có liên quan.

Không giả định các đường dẫn trên tồn tại. Hãy tìm đường dẫn thực tế.

Đọc toàn bộ `tasks.md`, không chỉ các task đang mở.

# 6. TASK-BY-TASK ANALYSIS

Với từng task chưa hoàn thành, báo cáo:

1. Task ID và tên.
2. Yêu cầu gốc.
3. Trạng thái trong `tasks.md`.
4. Dependency.
5. Code và contract liên quan.
6. Phần đã implement.
7. Phần còn thiếu.
8. Root cause.
9. Loại blocker:
   - Implementation.
   - Security.
   - Architecture.
   - Contract.
   - Business decision.
   - Cross-feature dependency.
   - Real-device testing.
10. Rủi ro nếu cố triển khai khi chưa giải quyết blocker.
11. Giải pháp đề xuất.
12. File/module dự kiến cần thay đổi.
13. Test cần bổ sung.
14. Acceptance criteria để đóng task.

Nếu task completed nhưng bằng chứng không đầy đủ, ghi thành finding riêng. Không tự đổi trạng thái task.

# 7. RESOLUTION PROPOSALS

Phân loại vấn đề thành bốn nhóm:

### A. Feature 111 tự giải quyết được
Ví dụ validation, UI states, tests, error mapping, accessibility và documentation.

### B. Cần phối hợp Feature khác
Nêu Feature owner, contract cần cung cấp, interface, tests và điều kiện tiếp tục.

### C. Cần quyết định nghiệp vụ/kiến trúc
Đưa ra 2–3 phương án, ưu nhược điểm, khuyến nghị và hậu quả nếu chưa quyết định.

### D. Cần kiểm thử môi trường thực tế
Đưa ra test matrix, thiết bị, điều kiện mạng, metric và tiêu chí pass.

# 8. OUTPUT — IN TERMINAL ONLY

In báo cáo trực tiếp ra terminal theo cấu trúc sau:

## 1. Executive Summary
- Tổng số task.
- Completed/open/blocked.
- Mức độ sẵn sàng của Feature 111.
- Các blocker quan trọng nhất.

## 2. Current Implementation Status
Mô tả code thực tế, không chỉ nhắc lại tài liệu.

## 3. Task-by-Task Analysis
Bảng:

| Task | Status | What Exists | What Is Missing | Root Cause | Proposed Resolution |

Sau bảng, phân tích chi tiết từng task còn vướng.

## 4. Cross-Feature Dependencies
Bảng:

| Dependency | Owner Feature | Required Contract | Current Gap | Recommended Next Action |

## 5. Business/Architecture Decisions
Mỗi quyết định cần có:
- Câu hỏi.
- Các phương án.
- Trade-offs.
- Khuyến nghị.
- Người/nhóm cần xác nhận.

## 6. Recommended Implementation Roadmap
Chia thành các phase có thứ tự:
- Mục tiêu.
- Tasks.
- Files/modules dự kiến.
- Dependencies.
- Tests.
- Acceptance criteria.
- Risks.

## 7. Test and Verification Plan
Phân biệt test đã có, test còn thiếu và kiểm thử thực tế.

## 8. Final Recommendations
Trả lời rõ:
- Có thể implement ngay những gì?
- Những gì phải chờ Feature khác?
- Những gì cần quyết định nghiệp vụ?
- Thứ tự giải quyết tối ưu?
- Điều kiện để hoàn thành Feature 111?

# 9. EVIDENCE REQUIREMENTS

Mọi kết luận kỹ thuật phải có bằng chứng:
- File path.
- Class/function hoặc contract.
- Test liên quan.
- Nếu có thể, line number.

Phân biệt rõ:
- VERIFIED: đã xác minh trong code/test.
- DOCUMENTED: chỉ được ghi trong tài liệu.
- INFERRED: suy luận từ implementation.
- PROPOSED: giải pháp đề xuất.
- EXTERNAL DECISION: cần người khác xác nhận.

Không bịa tên API, file, class hoặc test.

Nếu không tìm thấy contract, ghi rõ đã tìm ở đâu và không tìm thấy gì.

# 10. FINAL SAFETY CHECK

Trước khi kết thúc:
- Kiểm tra lại `git status --short`.
- Xác nhận không có file nào bị thay đổi bởi Agent.
- Xác nhận không tạo file báo cáo.
- Xác nhận không commit hoặc push.

Dòng cuối cùng phải ghi:

`READ-ONLY AUDIT COMPLETED — NO FILES CREATED OR MODIFIED`

Bắt đầu ngay. Không hỏi lại người dùng nếu repository đã có đủ tài liệu để tiến hành. Không dừng ở phần tóm tắt; phải phân tích từng blocker và đề xuất phương án cụ thể.
```

**Lượt 2 — Chốt quyết định giải tỏa Blocker**:

```text
1A, 2A, 3A, 4A
```

### Tóm tắt phản hồi của AI

- Thực hiện kiểm toán kỹ thuật độc lập không sửa đổi file: rà soát 39 task, phân tích chi tiết blocker T007 (slot lock/unlock), T008 (minPricePerHour), T011 (venue lifecycle), T034 (recurring booking) và T038 (SLA trên thiết bị thật).
- Đề xuất và được duyệt 4 phương án:
  - 1A: Server atomic slot lock/unlock qua Supabase Edge Function `manage-slot-blocks` chạy service role.
  - 2A: Authority cập nhật `venues.minPricePerHour` là Edge Function `sync-venue-price`, chính sách khi hết rule đưa về `null`.
  - 3A: Quy trình tạo thêm venue điều hướng sang luồng nộp hồ sơ của Feature 110 để Admin duyệt (Feature 120), Feature 111 chỉ quản lý venue đã duyệt.
  - 4A: Giữ nguyên schema `slots`, mở khóa an toàn dựa trên `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`, bảo vệ `DROP_IN`, `HOLD` và `BOOKED`.
- Cập nhật tài liệu hợp đồng và đặc tả: `contracts/firestore-boundary.md`, `research.md`, `tasks.md` (đánh dấu hoàn thành T008, T011, T022, T023).
- Triển khai Backend Edge Function: tạo `supabase/functions/sync-venue-price/` (`index.ts`, `handler.ts`, `handler.test.ts`), cập nhật `supabase/config.toml` và bổ sung helper `list` vào `_shared/firestore.ts`.
- Triển khai Android Domain & Data: cập nhật `OwnerVenueRepository`, `FirestoreOwnerVenueDataSource`, `OwnerVenueRepositoryImpl` và `SyncVenuePriceApi` (gọi Edge Function `sync-venue-price`).
- Triển khai Android UI: tạo `OwnerPriceRuleFragment`, `OwnerPriceRuleViewModel`, `OwnerPriceRuleAdapter`, các layout XML `fragment_owner_price_rule.xml`, `item_owner_price_rule.xml`, phiên bản `layout-w600dp`, thêm navigation trong `nav_owner.xml`, kết nối nút "Quản lý bảng giá" trong `OwnerVenueDetailFragment`, cập nhật chuỗi và xử lý cảnh báo coverage gap khi xóa rule.
- Điều hướng nút "Thêm cơ sở" trong `OwnerVenueListFragment` thông báo nộp hồ sơ qua Feature 110 theo quyết định 3A.
- Cập nhật lại toàn bộ nội dung nguyên văn của tất cả các lượt prompt trong `AI_log_4.md` mà không lược bỏ hay dùng dấu `...`.

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần                             | Quyết định            | Lý do                                                                                                           |
| -------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------- |
| Edge Function `sync-venue-price` | Tạo mới               | Cập nhật `venues.minPricePerHour` 🔒 bằng service role, đưa về `null` khi không còn rule theo Quyết định 2A     |
| Bảng giá (Price Rules CRUD)      | Dùng và mở rộng       | Mở quyền CRUD trên repository và data source; thêm giao diện quản lý bảng giá đầy đủ                            |
| Cảnh báo khoảng trống giá        | Dùng                  | Tích hợp `PriceRuleCoverage.deletionLeavesCoverageGap` hiển thị dialog cảnh báo trước khi xóa rule              |
| Vòng đời tạo thêm cơ sở          | Chỉnh sửa             | Nút thêm cơ sở hướng dẫn chuyển sang luồng nộp hồ sơ Onboarding (Feature 110) theo Quyết định 3A                |
| Slot lock/unlock write           | Tạm hoãn sang Phase 3 | Giữ nguyên typed gate `SLOT_ATOMIC_SERVER_CONTRACT` chờ triển khai Edge Function `manage-slot-blocks` ở Phase 3 |
| Feature 041 source/UI            | Không chỉnh sửa       | T034 tiếp tục mở chờ Feature 041 triển khai mã nguồn                                                            |

### Cách kiểm chứng

- Android owner unit tests: `./gradlew.bat testDebugUnitTest --tests 'com.example.booking_app.feature.owner.*'` -> **BUILD SUCCESSFUL** (24 tests passed, gồm 6 test mới cho `OwnerPriceRuleViewModelTest`).
- Build và Lint: `./gradlew.bat assembleDebug lintDebug` -> **BUILD SUCCESSFUL** (0 errors).
- Firestore Emulator tests: chạy vitest với `owner-price-rules.test.ts`, `owner-venue-authorization.test.ts`, `owner-courts.test.ts`, `owner-public-availability.test.ts` -> **4 files, 17 tests passed**.
- Không commit hoặc push.

---

## Mục 23: Triển khai hoàn thiện Phase 3 Feature 111 — Server Atomic Slot Lock & Unlock

| Trường | Nội dung |
|---|---|
| Ngày | 2026-10-30 |
| Người thực hiện | Minh Nhựt |
| Nhóm tính năng liên quan | 111 – Owner Venue Setup |
| Công cụ AI | Antigravity, Gemini 3.8 Flash, `$speckit-implement` |
| Mục đích | Triển khai hoàn thiện Phase 3: Server atomic slot lock/unlock qua Supabase Edge Function `manage-slot-blocks`, Calendar Grid UI shell (`OwnerSlotGridAdapter`, ViewModel, layouts phone & `w600dp`), và bổ sung kiểm thử Emulator transaction isolation (`owner-slot-blocks.test.ts`), giải tỏa và đóng T007, T028, T029, T030, T031. |

### Prompt đã dùng

```text
/speckit-implement Tiếp tục triển khai Feature 111 dựa trên toàn bộ context, spec, tasks.md và các quyết định đã thống nhất.

**Mục tiêu:** Hoàn thành tối đa các task còn mở trong `specs/111-owner-venue-setup/tasks.md` trong một lượt làm việc.

Yêu cầu:

1. Đọc trạng thái hiện tại của từng task, kiểm tra code thực tế trước khi sửa.
2. Ưu tiên triển khai theo dependency:
   - **T007:** Hoàn thiện và xác minh server-controlled slot lock/unlock contract.
   - **T028:** Implement atomic slot locking qua `manage-slot-blocks`.
   - **T029:** Implement atomic slot unlocking và bảo vệ HOLD, BOOKED, DROP_IN.
   - **T030:** Hoàn thiện Calendar Grid UI cho Owner Slot Block.
   - **T031:** Bổ sung Emulator transaction tests, đặc biệt các trường hợp race condition và partial conflict.
   - **T034:** Kiểm tra recurring toggle, bảo đảm chặn recurring booking mới nhưng không ảnh hưởng các booking group hiện có.
   - **T038:** Hoàn thiện UI, accessibility, responsive và kiểm thử hiệu năng trong phạm vi môi trường hiện có.
3. Kiểm tra lại T007, T008, T011, T022, T023 và T037 để xác nhận implementation thực tế khớp với trạng thái `[X]`. Không mở lại task nếu đã đáp ứng acceptance criteria.
4. Tuân thủ tuyệt đối schema, Security Rules, ownership, approved owner và các contract đã được thống nhất. Không tự ý thêm collection, field, enum, endpoint hoặc thay đổi contract.
5. Nếu phụ thuộc Feature 040, 041 hoặc backend chưa tồn tại, không giả lập implementation. Ghi rõ blocker và phần có thể hoàn thành độc lập.
6. Bổ sung unit tests, integration tests và Emulator tests tương ứng. Chạy build, lint và test liên quan.
7. Cập nhật `tasks.md`, tài liệu liên quan và AI log nếu thực sự có thay đổi.
8. Không đánh dấu task hoàn thành nếu chưa đáp ứng acceptance criteria và chưa có bằng chứng kiểm chứng.

**Nguyên tắc thực hiện:**
- Được phép chỉnh sửa code và test trong phạm vi Feature 111.
- Không mở rộng sang feature khác ngoài những phần tích hợp cần thiết.
- Không commit hoặc push.
- Không bỏ qua lỗi test bằng cách disable test hoặc nới lỏng assertion.
- Nếu phát hiện contract chưa đủ rõ, dừng phần phụ thuộc và báo cáo thay vì tự suy diễn.

**Báo cáo cuối cùng:**
- Danh sách task đã hoàn thành.
- Danh sách task còn mở và nguyên nhân.
- Các file đã tạo/sửa.
- Test, build, lint đã chạy và kết quả.
- Các blocker/dependency còn tồn tại.
- Những điểm cần tôi quyết định hoặc phê duyệt.

Hãy chủ động triển khai các task độc lập song song khi an toàn, nhưng phải bảo đảm không xung đột code và không vi phạm dependency.
```

### Tóm tắt phản hồi của AI

- Triển khai Backend Edge Function:
  - Tạo `supabase/functions/manage-slot-blocks/` (`handler.ts`, `index.ts`, `handler.test.ts`), đăng ký trong `supabase/config.toml`.
  - Cập nhật `supabase/functions/_shared/firestore.ts` bổ sung `deleteWrite` và `Write.delete` phục vụ mở khóa slot.
  - Xử lý xác thực approved owner, quyền sở hữu cơ sở, tính nguyên tử khi khóa sân (rollback toàn bộ nếu có bất kỳ slot nào bị chiếm bởi `BOOKED`, `HELD`, `BLOCKED`).
  - Xử lý mở khóa an toàn: chỉ cho phép mở khóa các slot `BLOCKED` có `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`; bảo vệ nghiêm ngặt các slot `DROP_IN` (Feature 112/080) và các slot `HOLD`/`BOOKED` (Feature 040).
- Triển khai Android Data Layer:
  - Tạo `ManageSlotBlocksApi.kt` (gọi Edge Function qua Supabase SDK với cơ chế retry xác thực).
  - Cập nhật `OwnerVenueRepository.kt` và `OwnerVenueRepositoryImpl.kt` bổ sung `lockSlots` và `unlockSlots`.
  - Cập nhật `OwnerModule.kt` bind `ManageSlotBlocksApi`.
- Triển khai Android UI Layer:
  - Tạo `OwnerSlotGridAdapter.kt` và layout `item_owner_slot_grid.xml` hiển thị lưới các khung giờ trong ngày theo mốc 30 phút.
  - Nâng cấp `OwnerSlotBlockViewModel.kt` hỗ trợ chọn khung giờ trực quan, phân loại trạng thái, khóa/mở khóa slot, ngăn duplicate submit, phát hiện dữ liệu cũ (`isStale`), tắt khi ngoại tuyến (`isOffline`), giữ nguyên biểu mẫu và lựa chọn khi gặp lỗi xung đột.
  - Nâng cấp `fragment_owner_slot_block.xml` và tạo bản máy tính bảng `layout-w600dp/fragment_owner_slot_block.xml` đáp ứng Touch targets $\ge 48\text{dp}$.
  - Cập nhật `OwnerSlotBlockFragment.kt` kết nối RecyclerView, xử lý chọn ngày, chọn lý do và kích hoạt khóa/mở khóa.
- Triển khai Emulator Tests:
  - Tạo `firestore-tests/test/owner-slot-blocks.test.ts` (7 tests passed): kiểm chứng Security Rules chặn owner client tự ghi/xóa `BLOCKED`, kiểm chứng transaction isolation giữa owner lock và player hold, kiểm chứng rollback khi xung đột dở dang (partial conflict), kiểm chứng bảo vệ ca `DROP_IN` và mở khóa an toàn ca `MAINTENANCE`.
- Cập nhật tài liệu:
  - Đóng `T007`, `T028`, `T029`, `T030`, `T031` trong `specs/111-owner-venue-setup/tasks.md` (37/39 tasks hoàn thành).
  - Cập nhật hợp đồng tại `specs/111-owner-venue-setup/contracts/firestore-boundary.md` và hướng dẫn kiểm thử tại `specs/111-owner-venue-setup/quickstart.md`.
  - Báo cáo rõ 2 task còn mở: `T034` (chờ Feature 041 cung cấp mã nguồn) và `T038` (chờ đo SLA trên thiết bị thật dùng mạng 4G).

### Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Edge Function `manage-slot-blocks` | Tạo mới | Thực thi khóa/mở khóa slot nguyên tử bằng service role, bảo vệ ca DROP_IN và HOLD/BOOKED theo Quyết định 1A & 4A |
| Helper `deleteWrite` trong `_shared/firestore.ts` | Tạo mới | Hỗ trợ xóa document nguyên tử qua Firestore REST API commit |
| Android API `ManageSlotBlocksApi` | Tạo mới | Gọi Edge Function `manage-slot-blocks` từ client |
| Lưới khung giờ (`OwnerSlotGridAdapter`, item layout) | Tạo mới | Hiển thị ma trận khung giờ trực quan thay cho việc nhập tay theo yêu cầu T030 |
| `layout-w600dp/fragment_owner_slot_block.xml` | Tạo mới | Hỗ trợ hiển thị 2 cột trên máy tính bảng, đảm bảo touch target $\ge 48\text{dp}$ theo T038 |
| `OwnerSlotBlockViewModel` & `OwnerSlotBlockFragment` | Chỉnh sửa | Mở rộng tính năng khóa/mở khóa slot thật qua repository, giữ nguyên tính tương thích ngược với các test trước |
| Emulator test `owner-slot-blocks.test.ts` | Tạo mới | Kiểm chứng ranh giới giao dịch đua giữa Owner Lock và Player Hold, partial conflict và bảo vệ DROP_IN theo T031 |
| Feature 041 source/UI | Không chỉnh sửa | T034 tiếp tục mở chờ Feature 041 triển khai mã nguồn theo nguyên tắc ranh giới |

### Cách kiểm chứng

- Android owner unit tests: `./gradlew.bat --no-daemon testDebugUnitTest --tests "com.example.booking_app.feature.owner.*"` -> **BUILD SUCCESSFUL** (30/30 tests passed, gồm 6 unit tests mới trong `OwnerSlotBlockViewModelTest`).
- Build và Lint: `./gradlew.bat --no-daemon assembleDebug lintDebug` -> **BUILD SUCCESSFUL** (0 lint errors).
- Firestore Emulator tests: chạy vitest với 5 test files (`owner-venue-authorization.test.ts`, `owner-courts.test.ts`, `owner-price-rules.test.ts`, `owner-public-availability.test.ts`, `owner-slot-blocks.test.ts`) -> **5 files, 24 tests passed** (100% pass).
- Không commit hoặc push.

