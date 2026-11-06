# Nghiên cứu: Feature 110 — Đăng ký làm chủ sân

## Cập nhật quyết định được duyệt (2026-11-05)

Các quyết định trong phần này supersede mục “Quyết định chủ động để ngỏ” bên dưới:

- Lifecycle: một Firestore document cho mỗi chu kỳ; resubmit `REJECTED` cập nhật cùng document; sau revoke dùng application ID mới. `users.ownerStatus` là trạng thái authoritative; application hiện hành lấy theo `uid ASC, submittedAt DESC` bằng index đã có.
- Tích hợp: Firebase ID token được xác minh trong Supabase Edge Function. Android dùng Supabase Kotlin SDK hiện tại; Edge Function mới ký upload/download cho private `owner-docs`. Ảnh venue cũng staging ở bucket đó và Feature 120 promote khi duyệt.
- Submit: Firestore REST transaction phía server đồng bộ application/user. Application ID UUID được giữ qua retry/resubmit; không thêm field/index/collection.
- Cleanup: draft app-private; upload mồ côi được sweeper dọn sau 30 ngày; account deletion vẫn do Feature 011 xử lý.
- Dependencies: không thêm dependency lõi; map provider bị tạm hoãn, không thêm SDK bản đồ.
- Approval recovery: promote ảnh trước, Firestore transaction tạo venue với ID application và cập nhật application/user; sau đó sync Firebase custom claim rồi notification, các bước retry idempotent. Feature 120/100 phải tích hợp theo thứ tự này.

## Quyết định dựa trên tài liệu dự án

### Quyết định: Firestore lưu trạng thái nghiệp vụ, Supabase Storage lưu hình ảnh

- **Căn cứ**: Hiến chương và README CSDL yêu cầu Firebase Spark/Firestore cho dữ liệu nghiệp vụ, Supabase Storage (gói Free) cho tệp. Ứng dụng chỉ lưu đường dẫn object, không lưu URL.
- **Phương án đã cân nhắc**: Firebase Cloud Storage/Cloud Functions và cơ sở dữ liệu Supabase. Cả hai đều trái với hiến chương hiện hành.

### Quyết định: Trạng thái hồ sơ và vai trò do máy chủ quản lý

- **Căn cứ**: `users.role`, `users.ownerStatus`, trạng thái/lý do duyệt và metadata duyệt là các trường được bảo vệ. Duyệt/từ chối và cập nhật custom claim thuộc `review-owner-application` của Feature 120. Giao diện người nộp hiển thị kết quả máy chủ, không tự cấp quyền owner.
- **Phương án đã cân nhắc**: Client tự ghi trạng thái theo kiểu lạc quan hoặc coi việc ẩn/hiện màn hình là kiểm soát quyền. Cả hai đều vi phạm yêu cầu bảo mật.

### Quyết định: Dùng bucket `owner-docs` riêng tư và quyền truy cập ký sẵn có thời hạn

- **Căn cứ**: Thiết kế CSDL quy định object path `{uid}/{applicationId}/{filename}`, quyền người nộp/admin và signed URL hết hạn sau 5 phút. Giới hạn ảnh lấy từ Feature 110: JPEG/PNG/WebP, tối đa 2 MB mỗi giấy tờ.
- **Phương án đã cân nhắc**: URL công khai hoặc đưa thông tin xác thực đặc quyền vào ứng dụng. Cả hai trái với yêu cầu riêng tư/bảo mật.

### Quyết định: Đặt mã trong package owner và dùng lại ứng dụng Android hiện tại

- **Căn cứ**: Hiến chương yêu cầu MVVM theo package tính năng, Fragment XML/ViewBinding, Navigation, StateFlow, Coroutines/Flow, Hilt và ranh giới repository. Repo hiện chỉ có Fragment mẫu; kế hoạch hướng đến `feature/owner/{ui,domain,data}` nhưng không khẳng định cấu trúc này đã tồn tại.
- **Phương án đã cân nhắc**: Tạo module Gradle mới hoặc import trực tiếp giữa các feature. Chưa có nhu cầu phù hợp với YAGNI.

### Quyết định: Kiểm tra trạng thái mới nhất từ máy chủ trước mỗi lần ghi

- **Căn cứ**: Spec cấm sửa hồ sơ `PENDING`, chỉ cho nộp lại từ `REJECTED`, không cho tạo hồ sơ mới khi `APPROVED`, và nêu trường hợp nhiều thiết bị/admin thay đổi đồng thời. Client cần làm mới dữ liệu để có trải nghiệm đúng; Firestore Rules/máy chủ vẫn là nơi thực thi quyền.
- **Phương án đã cân nhắc**: Tin trạng thái cache hoặc chỉ khóa nút. Hai cách này không xử lý được dữ liệu cũ hay request trực tiếp.

## Quyết định chủ động để ngỏ (bản ghi lịch sử, đã được supersede ở trên)

### Định danh và lịch sử hồ sơ

- **Phát hiện**: Quy tắc chung của CSDL dùng ID Firestore tự sinh. Spec 110 giả định một hồ sơ đang hiệu lực và nộp lại trong cùng chuỗi xét duyệt; FR-018 cho phép nộp mới sau khi thu hồi quyền nhưng vẫn giữ hồ sơ `APPROVED` cũ. Chưa quy định cách tìm hồ sơ hiện hành, chọn ID hay lưu lịch sử.
- **Quyết định tại thời điểm lập research**: Chưa chọn phương án. Đã được chủ dự án thay thế bằng lựa chọn T001/T003 bên trên; không còn là blocker quyết định.
- **Phương án đang cân nhắc**: Dùng lại hồ sơ bị từ chối; tạo document mới cho mỗi lần nộp; hoặc chỉ tạo document mới sau khi bị thu hồi quyền. Các phương án dẫn đến lịch sử xét duyệt khác nhau nên không thể tự chọn dựa trên tài liệu hiện có.

### Tích hợp Android với Supabase và tải ảnh venue trước khi được duyệt

- **Phát hiện**: Hiến chương quy định Firebase Auth làm xác thực bên thứ ba cho Supabase. README có tên `sign-upload` và `sign-download` nhưng chưa có request/response hoặc cách gọi từ ứng dụng Android. README mô tả tải `venue-photos` cho owner đã duyệt, trong khi Feature 110 cần tải ảnh venue trước khi được duyệt.
- **Quyết định tại thời điểm lập research**: Chưa chọn phương án. Đã được chủ dự án thay thế bằng T002-B và API contract trong `contracts/owner-application-api.md`; không còn là blocker lựa chọn kiến trúc.
- **Phương án đang cân nhắc**: Supabase Android SDK/Storage API, gọi REST trực tiếp, hoặc quy trình upload riêng do máy chủ cấp quyền. Tài liệu dự án chưa chọn cách nào.

### Vòng đời bản nháp và tệp mồ côi

- **Phát hiện**: Biểu mẫu phải giữ được dữ liệu khi gặp lỗi có thể khôi phục; spec 011 yêu cầu xóa hồ sơ khi xóa tài khoản, nhưng chưa quy định thời gian lưu bản nháp, dọn upload dở hay cách thử lại.
- **Quyết định tại thời điểm lập research**: Chỉ giữ trong phiên và chưa có retention. Đã được chủ dự án thay thế bằng T004-B và thời hạn 30 ngày bên trên.
- **Phương án đang cân nhắc**: Chỉ giữ trong bộ nhớ, lưu trạng thái instance, lưu cục bộ bền vững; dọn tệp ngay hoặc theo lịch. Mỗi phương án có đánh đổi riêng về riêng tư và trải nghiệm.

## Tài liệu đã đối chiếu

- `.specify/memory/constitution.md`: kiến trúc, trường được bảo vệ, Storage riêng tư, kiểm thử và ràng buộc công nghệ.
- `docs/guides/w1-w2-spec-assignment.md`: Feature 110 thuộc nhóm D; phân công CSDL và ranh giới tích hợp với Feature 011/120/111/112.
- `docs/design/database/README.md`: `users`, `ownerApplications`, `owner-docs`, hàm ký Storage, quyền và index.
- `specs/011-account-profile/spec.md`: lối vào/trạng thái tài khoản; đọc trạng thái từ `users.ownerStatus`, lý do từ chối từ `ownerApplications.rejectReason`.
- `specs/111-owner-venue-setup/spec.md`: chỉ owner đã duyệt được quản lý; venue đầu tiên lấy từ draft hồ sơ.
- `specs/112-owner-operations/spec.md`: vận hành chỉ dành cho owner đã duyệt và nằm ngoài phạm vi.
- `specs/120-admin/spec.md`: quyết định admin, lý do, vai trò/trạng thái/custom claim và tạo venue thuộc luồng duyệt.
- Kiểm tra mã nguồn: ứng dụng Android hiện là bộ khung; đã khai báo Firebase Auth/Firestore nhưng chưa có Coroutines, Hilt, thư viện Supabase, fake cho kiểm thử hay package theo feature. `supabase/` hiện chỉ có cấu hình.
