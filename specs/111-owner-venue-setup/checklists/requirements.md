# Quản lý venue: Checklist chất lượng yêu cầu

**Purpose**: Rà soát tính đầy đủ, rõ ràng, nhất quán và khả năng kiểm chứng của yêu cầu trong spec quản lý venue, court, `priceRules` và khóa giờ.
**Created**: 2026-10-08
**Feature**: [spec.md](../spec.md)

**Note**: Checklist tùy chỉnh này được tạo từ ngữ cảnh và yêu cầu của feature.
**Review Ownership**: Đây là artifact rà soát chất lượng yêu cầu do reviewer sở hữu. Chỉ đánh dấu `[x]` khi reviewer xác nhận tiêu chí chất lượng yêu cầu đã đạt.
**Marker Semantics**: `[x]` nghĩa là tiêu chí đã được rà soát và đạt về chất lượng yêu cầu; không có nghĩa là tính năng đã được triển khai.

## Requirement Completeness

- [x] CHK001 - Các thao tác thêm, sửa, ẩn venue và quản lý court được phân biệt rõ với chức năng ngoài phạm vi như duyệt booking và voucher? [Completeness, Spec §Functional requirements FR-001–FR-007, FR-014]
- [x] CHK002 - Các điều kiện bắt buộc để tạo venue được xác định rõ cho từng trường trong schema, kể cả `photoPaths`, `amenityIds` và thông tin liên hệ? [Completeness, Spec §FR-003, Validation]
- [x] CHK003 - Yêu cầu về tạo, sửa, xung đột và ngừng sử dụng `priceRules` bao phủ vòng đời cần thiết mà không ngầm yêu cầu field chưa có trong schema? [Completeness, Spec §FR-008, Open Questions / Assumptions]
- [x] CHK004 - Trạng thái loading, empty, lỗi, mất mạng và xung đột đã được mô tả cho các luồng xem và ghi chính? [Completeness, Spec §Error / Empty / Loading states, Offline / network failure]

## Requirement Clarity

- [x] CHK005 - Phạm vi quyền owner được diễn đạt bằng cả `role = OWNER`, `ownerStatus = APPROVED` và quan hệ `venues.ownerId` khớp UID? [Clarity, Spec §FR-001, Permission / Security]
- [x] CHK006 - Quy tắc tính giá cho từng slot 30 phút, trường hợp không có rule khớp và thời điểm áp dụng rule mới đủ cụ thể để các màn hình đưa ra cùng một kết quả? [Clarity, Spec §FR-008–FR-009, Clarifications]
- [x] CHK007 - Hành vi ẩn venue hoặc ngừng hoạt động court phân biệt rõ việc chặn booking mới với việc giữ booking đã xác nhận? [Clarity, Spec §FR-005, FR-007, Clarifications]
- [x] CHK008 - Quy tắc khung giờ không qua nửa đêm và yêu cầu căn mốc khóa theo slot 30 phút được nêu nhất quán giữa Validation và Functional requirements? [Clarity, Spec §FR-010, Validation, Clarifications]

## Requirement Consistency

- [x] CHK009 - Quyền chỉnh sửa venue, court và `priceRules` nhất quán giữa yêu cầu chức năng, bảo mật và các kịch bản chấp nhận? [Consistency, Spec §Acceptance scenarios, FR-001–FR-008, Permission / Security]
- [x] CHK010 - Quy tắc giữ giá booking đã tạo không mâu thuẫn với yêu cầu tính giá phía server hoặc với phụ thuộc Feature 05? [Consistency, Spec §FR-008–FR-009, Dependencies]
- [x] CHK011 - Cách ẩn court bằng `isActive = false` và venue bằng `status = HIDDEN` nhất quán với phạm vi field/status hiện có trong database README? [Consistency, Spec §FR-005–FR-007, Open Questions / Assumptions]

## Acceptance Criteria Quality

- [x] CHK012 - Tiêu chí thành công về phân quyền, bảo toàn booking, tính giá và khóa slot có thể đánh giá khách quan từ yêu cầu đã nêu? [Measurability, Spec §Success criteria]
- [x] CHK013 - Kịch bản chấp nhận xác định rõ kết quả khi khóa slot xung đột với HOLD/BOOKED hoặc khi booking xảy ra đồng thời? [Coverage, Spec §Acceptance scenarios 5–7, FR-011–FR-012]

## Scenario Coverage

- [x] CHK014 - Các kịch bản chính cho owner đã duyệt, owner chưa duyệt, người chơi và venue không thuộc owner được bao phủ đủ? [Coverage, Spec §Actors, Acceptance scenarios, Permission / Security]
- [x] CHK015 - Yêu cầu phân biệt booking mới với booking tương lai đã xác nhận khi venue/court bị ẩn có được áp dụng nhất quán cho cả venue và court? [Coverage, Spec §FR-005, FR-007, Edge cases]

## Edge Case Coverage

- [x] CHK016 - Quy tắc cập nhật đồng thời, mất mạng sau khi gửi và khóa nhiều slot gặp xung đột mô tả rõ kết quả dữ liệu có thể chấp nhận? [Coverage, Spec §Edge cases, Offline / network failure]
- [x] CHK017 - Quy tắc ranh giới giữa hai `priceRules`, booking kéo qua nhiều rule và thiếu rule được định nghĩa đủ để tránh nhiều cách hiểu? [Ambiguity, Spec §FR-009, Edge cases, Clarifications]

## Non-Functional Requirements

- [x] CHK018 - Spec có nêu mục tiêu đo lường phù hợp cho thời gian tải danh sách/lịch hoặc phản hồi thao tác quản lý hay xác định rõ đây là yêu cầu chưa chốt? [Gap, Spec §Success criteria]
- [x] CHK019 - Yêu cầu bảo mật nói rõ quyền được kiểm tra phía server và ngăn truy cập chéo venue qua ID do client cung cấp? [Completeness, Spec §Permission / Security]

## Dependencies & Assumptions

- [x] CHK020 - Các phụ thuộc vào booking 040/041/050, court detail 030 và admin 120 được nêu cùng những hợp đồng cần đối chiếu trước khi thiết kế? [Dependency, Spec §Dependencies]
- [x] CHK021 - Các giả định kế thừa quyền qua venue cha, lý do `WALK_IN`, và cách xử lý `DROP_IN` được gắn nhãn rõ để reviewer phân biệt với schema đã chốt? [Assumption, Spec §Open Questions / Assumptions]

## Ambiguities & Conflicts

- [x] CHK022 - Câu hỏi còn mở về lưu giữ/ngừng áp dụng `priceRules` được phân biệt rõ với quyết định đã chốt là giữ giá booking hiện có? [Ambiguity, Spec §Open Questions / Assumptions, Clarifications]
- [x] CHK023 - Các điểm chưa thể đối chiếu do spec booking/admin chưa tồn tại được ghi nhận mà không tự suy đoán hoặc sửa tài liệu nguồn? [Conflict, Spec §Dependencies, Mâu thuẫn tài liệu]

## Notes

- Chỉ đánh dấu `[x]` sau khi reviewer xác nhận chất lượng yêu cầu; không dùng checklist này để xác nhận triển khai.
- `$speckit-implement` đọc trạng thái checkbox như một cổng checklist nhưng không tự thay đổi marker.
- Checklist `checklists/requirements.md` có vòng đời riêng do `$speckit-specify` và `$speckit-clarify` quản lý.
- Ghi nhận nhận xét hoặc liên kết tài liệu liên quan bên cạnh item cần làm rõ.
