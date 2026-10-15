# Nhật ký sử dụng AI – Thành viên 4 (Minh Nhựt)

Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| \#  | Ngày       | Nội dung                                                                                                                   | Nhóm |
| --- | ---------- | -------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1   | 2026-10-06 | Viết và làm rõ spec 100 (thông báo) bằng AI và `$speckit-clarify`                                                          | 10   |
| 2   | 2026-10-07 | Viết spec 110 (đăng ký chủ sân) bằng `$speckit-specify` và làm rõ bằng `$speckit-clarify`                                  | 11   |
| 3   | 2026-10-08 | Viết và làm rõ spec 111 (quản lý cơ sở, sân con, bảng giá và khóa giờ) bằng AI, `$speckit-clarify` và `$speckit-checklist` | 11   |
| 4   | 2026-10-08 | Viết và làm rõ spec 112 (vận hành cơ sở chủ sân) bằng AI và `$speckit-clarify`                                             | 11   |
| 5   | 2026-10-08 | Viết và làm rõ spec 120 (quản trị, hồ sơ chủ sân và báo cáo) bằng `$speckit-specify` và `$speckit-clarify`                 | 12   |
| 6   | 2026-10-15 | Lập implementation plan cho Feature 100 (thông báo) bằng `$speckit-plan`                                                   | 10   |
| 7   | 2026-10-15 | Lập implementation plan cho Feature 110 (đăng ký chủ sân) bằng `$speckit-plan` và viết lại tài liệu bằng tiếng Việt        | 11   |
| 8   | 2026-10-16 | Lập implementation plan cho Feature 111 (thiết lập cơ sở chủ sân) bằng `$speckit-plan`                                     | 11   |
| 9   | 2026-10-16 | Lập implementation plan cho Feature 112 (vận hành chủ sân) bằng `$speckit-plan`                                            | 11   |

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
