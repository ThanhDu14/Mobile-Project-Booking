# Research: Feature 120 Admin

Ngày đối chiếu: 2026-10-16. Nguồn: Constitution 1.2.0, Feature 120 spec, assignment guide, database README, specs 010/011/100/110/111/112 and related report producers, current Android/Rules/test tree.

## Quyết định và căn cứ

### D1. Nguồn quyền và nơi thực thi

- **Decision**: Xác thực Firebase user và claim `appRole == ADMIN` ở server cho mỗi request; không tin role gửi từ app. Firestore Rules phải chặn client access không được phép. Các mutation quyền cao phải qua Supabase Edge Functions theo Constitution; admin không có màn hình tự đăng ký.
- **Rationale**: Constitution II và database README mục 7 xác định custom claim `appRole`, Edge Functions cho trường 🔒; spec FR-001/016 yêu cầu kiểm tra từng lần và từ chối truy cập trực tiếp trái quyền. `firestore.rules` hiện chỉ có helper và không có collection-specific matches, do đó mặc định deny.
- **Alternatives considered**: chỉ ẩn UI / ghi client trực tiếp — bị loại bởi FR-001, Constitution II. Firebase Cloud Functions — không phù hợp Spark/Constitution.
- **Open**: Xác minh Edge Function với Firebase ID token và cách đồng bộ/revoke `appRole` cần được chốt trong hợp đồng server; không được suy diễn từ client claim đang cache.

### D2. Duyệt owner và nhất quán đa tài nguyên

- **Decision**: Gắn quyết định với `ownerApplications` hiện `PENDING`; không chấp nhận stale/duplicate decision. Đích dữ liệu theo nguồn: application `APPROVED/REJECTED`, user role/status, venue tạo từ `venueDraft`, claim `appRole`, notification tương ứng. Reject reason bắt buộc sau trim. Từ chối hoặc duyệt phải là một kết quả nhất quán, không hiển thị một phần thành công.
- **Rationale**: Feature 120 FR-004/005/007, Clarification tạo venue khi duyệt; database README 4.2 mô tả function `review-owner-application`; 110 gắn re-submit với hồ sơ bị từ chối.
- **Alternatives considered**: thao tác nhiều client writes — không bảo vệ claim/trường 🔒 và có thể để trạng thái lệch; dùng client snapshot làm điều kiện — không chống race.
- **Open**: Firestore transaction có thể bao gồm documents, nhưng Firebase Auth custom claims và notification không nằm cùng transaction. Cần quyết định rõ thứ tự/idempotency/recovery hoặc pattern xử lý đã có trước implementation. Không có schema outbox/review version được đặc tả, nên không thêm.

### D3. Truy cập tài liệu owner

- **Decision**: Giấy tờ chỉ qua private `owner-docs`, signed URL tối đa 5 phút; xác thực applicant UID hoặc Admin tại server/RLS. Không dùng public URL và không lưu URL đầy đủ trong Firestore.
- **Rationale**: Constitution II, database README mục 8; feature spec FR-003 và privacy requirement.
- **Alternative considered**: public bucket — vi phạm quyền riêng tư.
- **Open**: Database README ghi Edge Functions `sign-download` cấp signed URL sau khi kiểm tra Firestore. Cần xác nhận đây là đường cấp URL chính xác cho hồ sơ admin (function hiện được liệt kê chung cho private buckets), chưa thấy code triển khai.

### D4. Reports và đối tượng được kiểm duyệt

- **Decision**: Dùng đúng enum và mapping trong schema: `OPEN → RESOLVED|DISMISSED`; `NONE|HIDE_CONTENT|LOCK_USER|LOCK_VENUE`; mapping theo target đã ghi trong spec. Dùng targetPath hiện có để lấy đối tượng, kiểm tra tồn tại/hiện trạng trước mutation.
- **Rationale**: Database README 4.12 và clarifications của Feature 120; reports được tạo bởi 070/090/091. Không coi resolve là xóa vĩnh viễn.
- **Alternatives considered**: thêm action/status hoặc target type — ngoài spec và không có schema.
- **Open**: `DROP_IN` có thể là report target nhưng schema drop-in không định nghĩa trường `status=HIDDEN`; spec cho phép hide chỉ “khi đối tượng hỗ trợ ẩn”. Tài liệu nguồn không chỉ rõ target field/path/action cho DROP_IN. Chốt trước khi cho phép `HIDE_CONTENT` trên loại này.

### D5. Thống kê, danh mục, broadcast

- **Decision**: Chỉ đọc `systemStats`/`dailyStats` và hiển thị trường tồn tại kèm ngày/thời điểm; không tính hoặc suy diễn metrics mới. Danh mục ở `districts`/`amenities`; broadcast theo `SYSTEM` tới account đang hoạt động qua Feature 100 server path. Voucher toàn hệ thống theo schema Feature 050.
- **Rationale**: Feature 120 FR-013/014/014a, database README mục 2, 4.6, 4.13 và assignment guide phân công collection D.
- **Alternatives considered**: tự định nghĩa metrics, category CRUD semantics hoặc bảng người nhận — không được mô tả trong nguồn.
- **Open**: README không đưa ra field schema cho `districts`, `amenities`, `dailyStats`, `systemStats`, danh sách metrics, thao tác danh mục/validation, nội dung/kiểu nhận broadcast, hoặc cơ chế xác định “đang hoạt động”. Đây là quyết định nghiệp vụ/schema cần được bổ sung bởi chủ sở hữu tài liệu; plan không tạo fields mới.

### D6. Môi trường và cấu trúc code

- **Decision**: Android package theo Constitution (`feature/admin/{ui,data,domain}`), backend Supabase Edge Functions, Rules/tests ở vị trí dự án hiện có; không thêm Gradle module. Dùng thư viện đã có khi phù hợp.
- **Evidence**: app chỉ có `MainActivity`, `FirstFragment`, `SecondFragment`, basic XML/navigation; Gradle catalog có AndroidX Navigation, Material, Firebase Auth/Firestore/FCM, JUnit/Espresso. Chưa có ViewModel/Coroutines/Hilt, các test libs MockK/Turbine, feature packages, `supabase/functions`, Rules collection matches. `firestore-tests` chỉ có Vitest scaffold.
- **Open**: dependency architecture/runtime đang thiếu so với Constitution, nhưng việc thêm phải qua version catalog và ghi rationale. Cần quyết định cách tích hợp Edge Functions (HTTP client hiện có hay dependency mới); chưa chọn thư viện Supabase Android khi chưa cần.

## Điểm cần chốt trước khi implementation

1. Giao kèo gọi và xác thực server mutation; custom claim revocation/refresh và cách bảo vệ mutation khỏi token cũ.
2. Tính nhất quán và recovery khi Firestore records, Firebase Auth custom claims, venue creation và notification không thể commit nguyên tử cùng nhau.
3. Schema/metric và quy tắc lọc của `systemStats`/`dailyStats`; schema và CRUD rules của `districts`/`amenities`; nghĩa chính xác của active accounts khi broadcast.
4. `DROP_IN` có cho phép `HIDE_CONTENT` không và trường/behavior ẩn hiện hữu nào hỗ trợ nó.
5. Cách phân trang/giới hạn hàng đợi, tìm user, báo cáo; index bổ sung sau khi query được chốt. Hiện dự kiến chỉ có index `reports(status, createdAt)` và `ownerApplications(status, submittedAt)`.
6. Bảng quyền README mục 7 nói Admin đọc/ghi trực tiếp một số collection, trong khi spec yêu cầu kiểm tra server mỗi lần và Constitution yêu cầu Edge Functions với mutation nhạy cảm. Cần đồng bộ bảng quyền/Rules với boundary server.
7. Tên và phiên bản dependency Android cần bổ sung để tuân Constitution; hiện chưa có Hilt/ViewModel/coroutines và test libraries.

Không có thông tin ngoài các nguồn repo được dùng để chọn công nghệ hay đặt schema mới.
