# Nhật ký sử dụng AI – Thành viên 4 (Minh Nhựt)

Mỗi lần dùng AI là một mục mới, mục mới nhất ở cuối.

| \#  | Ngày       | Nội dung                                                                                                                   | Nhóm |
| --- | ---------- | -------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1   | 2026-10-06 | Viết và làm rõ spec 100 (thông báo) bằng AI và `$speckit-clarify`                                                          | 10   |
| 2   | 2026-10-07 | Viết spec 110 (đăng ký chủ sân) bằng `$speckit-specify` và làm rõ bằng `$speckit-clarify`                                  | 11   |
| 3   | 2026-10-08 | Viết và làm rõ spec 111 (quản lý cơ sở, sân con, bảng giá và khóa giờ) bằng AI, `$speckit-clarify` và `$speckit-checklist` | 11   |

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
