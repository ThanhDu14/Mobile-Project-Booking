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

## T001 — Định danh hồ sơ hiện hành và lịch sử

### Những điều tài liệu hiện đã xác nhận

| Nguồn | Điều đã xác nhận | Hệ quả |
|---|---|---|
| Database README §1, §4.2 | `ownerApplications/{id}` dùng ID tự sinh của Firestore; schema hiện có `uid`, `venueDraft`, thông tin đại diện, `documentPaths`, `status`, `rejectReason`, `reviewedBy`, `reviewedAt`, `submittedAt`; không có `applicationId`, `requestId`, `version`, con trỏ hồ sơ hiện hành hay collection lịch sử. | Không được tự suy ra document ID, khóa idempotency hoặc revision từ schema hiện tại. |
| Database README §7 | Applicant có `C`, `R` hồ sơ của mình và `U` khi `REJECTED`; owner đọc hồ sơ của mình; admin đọc và quyết định qua Edge Function. | Có căn cứ cho cập nhật applicant-owned fields của hồ sơ `REJECTED`, nhưng chưa đủ để chọn document khi có nhiều hồ sơ hoặc định nghĩa retry. |
| Feature 110 FR-018 | Khi revoke, `users.ownerStatus = NONE`; hồ sơ `APPROVED` cũ được giữ để lưu lịch sử; người dùng được phép nộp hồ sơ mới. | Một hồ sơ `APPROVED` có thể là lịch sử trong khi quyền hiện hành là `NONE`. |
| Feature 120 §Trạng thái/FR-006/FR-009 | `REJECTED → PENDING` khi nộp lại; hồ sơ `APPROVED` không xử lý lại trong hàng đợi; revoke đặt user về `PLAYER/NONE` và giữ application `APPROVED`. | Resubmit rejected và hồ sơ mới sau revoke là hai tình huống khác nhau. |
| Feature 011 FR-008/Clarification 2026-10-13 | Trang account lấy trạng thái hiện hành từ `users.ownerStatus`; sau revoke hiển thị bị thu hồi và cho mở form mới. | Không dùng một hồ sơ `APPROVED` cũ để cấp lại quyền hoặc thay thế `ownerStatus`. |
| Constitution II, IV | Quyền và trạng thái phải do server kiểm tra; retry/timeout phải được kiểm chứng bằng emulator/local, không tin cache hay ẩn UI. | T001/T003 phải chốt thao tác server nguyên tử và kết quả authoritative trước khi client ghi. |

### Các trường hợp cần phân biệt

1. `ownerStatus = PENDING`: hồ sơ `PENDING` của cùng UID là hồ sơ đang xét; không được sửa/rút hoặc tạo hồ sơ cạnh tranh.
2. `ownerStatus = REJECTED`: hồ sơ `REJECTED` của cùng UID là hồ sơ có thể sửa/nộp lại theo quyền đã chốt.
3. `ownerStatus = APPROVED`: hồ sơ `APPROVED` tương ứng là hồ sơ đã được duyệt; không tạo hồ sơ đăng ký mới.
4. `ownerStatus = NONE` sau revoke: hồ sơ `APPROVED` cũ vẫn phải giữ để lịch sử, nhưng không phải hồ sơ hiện hành và không cấp quyền. Hồ sơ mới sau revoke phải được phân biệt với hồ sơ cũ.
5. Không có hồ sơ: `NONE` và không có application phù hợp là trạng thái bắt đầu mới.

### Phương án A — Cập nhật hồ sơ `REJECTED`, tạo hồ sơ mới sau revoke (khuyến nghị có điều kiện)

**Cách làm**: giữ nguyên document khi `REJECTED → PENDING`; chỉ khi user ở `NONE` sau revoke mới tạo document auto-ID mới. Hồ sơ `APPROVED` cũ không sửa. `ownerStatus` vẫn là nguồn quyền hiện hành.

**Ưu điểm**:

- Phù hợp trực tiếp với quyền `U khi REJECTED` trong README và trạng thái `REJECTED → PENDING` của Feature 120.
- Không sửa hồ sơ `APPROVED` đã được xác nhận là lịch sử sau revoke.
- Giữ tối đa một hồ sơ đang hiệu lực cho mỗi user nếu server kiểm tra trạng thái trước ghi.
- Không cần thêm field/collection nếu nhóm chấp nhận truy vấn hồ sơ mới bằng các trường hiện có.

**Nhược điểm/rủi ro**:

- Cập nhật cùng document làm mất snapshot đầy đủ của các lần resubmit trước; schema hiện tại không có revision history.
- Sau revoke có nhiều document cùng UID; auto-ID không tự cho biết document hiện hành. Truy vấn phải dựa trên `uid`, trạng thái và `submittedAt`, nhưng index tương ứng chưa được xác nhận.
- Retry tạo hồ sơ mới sau revoke cần một thao tác server nguyên tử; nếu chỉ `add()` từ client có thể tạo trùng.

**Tác động**: Rules phải cho update applicant-owned fields chỉ khi document đang `REJECTED`; Edge Function/transaction phải bảo vệ chuyển trạng thái và tạo mới sau revoke. Feature 011 đọc `ownerStatus`; Feature 120 giữ hồ sơ approved cũ và xử lý hồ sơ pending mới; 111/112 chỉ dựa trên quyền approved từ server.

### Phương án B — Tạo document auto-ID mới cho mọi lần nộp/nộp lại

**Cách làm**: hồ sơ `REJECTED` được giữ nguyên; mỗi resubmit tạo application mới `PENDING`.

**Ưu điểm**:

- Giữ được snapshot từng lần nộp bằng các document schema hiện có.
- Lịch sử xét duyệt dễ đọc hơn nếu truy vấn được toàn bộ application của UID.
- Không ghi đè `rejectReason`, `reviewedAt` hoặc `venueDraft` của lần trước.

**Nhược điểm/rủi ro**:

- Trái hoặc ít nhất chưa rõ so với quyền `U khi REJECTED` và câu “đặt hồ sơ về PENDING” trong Feature 120; cần nhóm xác nhận semantics “nộp lại” là document mới.
- Có thể tạo nhiều hồ sơ `PENDING` nếu server không khóa theo UID và trạng thái.
- Cần định nghĩa chính xác “current application”, truy vấn/index và cách account chọn lý do hiện hành.
- Retry sau timeout khó phân biệt với lần nộp mới nếu không có cơ chế idempotency được nhóm chấp thuận.

**Tác động**: Rules phải chặn tạo khi đã có `PENDING`/`APPROVED`; Edge Function phải chọn hồ sơ mới nhất authoritative. Feature 120 cần xử lý hàng đợi theo application mới và Feature 011 phải không hiển thị hồ sơ rejected cũ thay cho hồ sơ pending mới.

### Phương án C — Một document duy nhất cho toàn bộ vòng đời

**Cách làm**: cập nhật cùng document từ `REJECTED` sang `PENDING`, rồi giữ document đó cho các lần duyệt/revoke.

**Ưu điểm**: truy vấn đơn giản, không có nhiều application cạnh tranh, retry có thể trở thành thao tác chuyển trạng thái trên cùng document.

**Nhược điểm/rủi ro**: không đáp ứng FR-018 vì hồ sơ `APPROVED` phải giữ nguyên khi revoke nhưng user lại được nộp hồ sơ mới; nếu tái sử dụng document sẽ mất lịch sử approved hoặc phải thêm schema lịch sử chưa được phép. Vì vậy phương án này không phù hợp với tài liệu hiện tại.

### Lưu lịch sử

- **Đã xác nhận tối thiểu**: hồ sơ `APPROVED` cũ phải tồn tại sau revoke.
- **Chưa xác nhận**: lịch sử đầy đủ từng lần resubmit, snapshot từng `venueDraft`, document paths và quyết định admin.
- Cập nhật cùng document (Phương án A) chỉ lưu trạng thái hiện hành và lịch sử approved sau revoke; không cung cấp audit trail đầy đủ.
- Tạo document mới mỗi lần (Phương án B) giữ được snapshot bằng schema hiện có nhưng phát sinh bài toán chọn current và index.
- Không tự thêm subcollection lịch sử, mảng revision, `version` hoặc field con trỏ để giải quyết thiếu sót này.

### Retry cùng lần nộp và lần nộp mới

Đây là quyết định riêng với việc giữ lịch sử:

- **Retry cùng lần nộp** phải đối chiếu trạng thái authoritative trước khi tạo/đổi hồ sơ. Nếu server đã ghi `PENDING`, retry phải trả về hồ sơ đó hoặc kết quả tương đương và không tạo document thứ hai.
- **Lần nộp mới** chỉ hợp lệ khi user đang `NONE` và không có hồ sơ đang hiệu lực; sau revoke, application `APPROVED` cũ không được coi là hồ sơ active.
- Schema hiện tại không có request ID/version. Không được dùng auto-ID client, thời gian hoặc so sánh payload làm idempotency ngầm mà chưa được nhóm phê duyệt.
- Một lựa chọn không thêm field là Edge Function/Firestore transaction nguyên tử: đọc `users.ownerStatus` và application phù hợp, tạo/chuyển trạng thái một lần, rồi retry đọc lại trạng thái hiện tại. Tuy nhiên cần nhóm xác nhận thao tác, tiêu chí chọn application và index/query trước khi triển khai.

### Quyết định đã chốt — Phương án 1 (A)

Nhóm Feature 110 đã chính thức chốt **Phương án 1** cho `T001-application-identity`:

1. Hồ sơ `REJECTED` được cập nhật ngay trên document hiện có và chuyển `REJECTED → PENDING` khi nộp lại.
2. Sau revoke, khi `users.ownerStatus = NONE`, lần đăng ký mới tạo document Firestore với auto-ID mới; document `APPROVED` cũ được giữ nguyên làm lịch sử.
3. `users.ownerStatus` là nguồn xác định quyền hiện hành. Application `APPROVED` cũ không tự cấp lại quyền owner sau revoke.
4. Không thêm revision collection, `requestId`, `version`, current pointer, field hoặc index mới để phục vụ quyết định này.

Quyết định trên giải quyết nghiệp vụ resubmit, đăng ký lại và lịch sử. Nó **không tự giải quyết** cách truy vấn duy nhất application hiện hành hoặc tính nguyên tử/idempotency của thao tác server.

### Tình trạng kỹ thuật còn mở của T001

| Hạng mục | Kết luận hiện tại | Việc phụ thuộc |
|---|---|---|
| Query sau revoke | Khi `ownerStatus = NONE`, các application `APPROVED` cũ chỉ là lịch sử; schema auto-ID hiện tại không có current pointer hoặc khóa duy nhất để chọn một application mới giữa nhiều document cùng `uid`. README mới xác nhận index `status`, `submittedAt`, chưa xác nhận query/index theo `uid` và current application. Không tự bịa query hoặc index. | T003 phải chốt thao tác đọc authoritative và tiêu chí chọn current; nếu schema/index hiện tại không đủ, cần quyết định contract riêng trước T019/T021/T024/T026–T029. |
| Resubmit `REJECTED` | Identity của document được cập nhật là document rejected đang được server xác nhận; không tạo document mới theo Phương án 1. | T003 phải định nghĩa kiểm tra trạng thái hiện tại và conflict khi document đã đổi. |
| Retry cùng lần nộp | T001 chỉ phân biệt nghiệp vụ retry với lần nộp mới. Không dùng auto-ID client, timestamp hoặc so sánh payload làm idempotency ngầm. | T003 phải thiết kế server-side atomicity, authoritative read trước/sau timeout, kết quả retry và stale-write mà không tự thêm field/schema. |

### Bảng quyết định chính thức

| Trường | Giá trị |
|---|---|
| Decision ID | `T001-application-identity` |
| Phạm vi | current application, resubmit, history, retry/idempotency |
| Phương án được chọn | **Phương án 1 / A** — cập nhật `REJECTED`; tạo auto-ID mới sau revoke; giữ `APPROVED` cũ |
| Nguồn quyền hiện hành | `users.ownerStatus` |
| Query/index | Chưa chốt; không có current pointer/index được phê duyệt trong schema hiện tại; ghi nhận vấn đề cho T003 |
| Server operation | Chưa chốt trong T001; atomicity, retry, idempotency và stale-write thuộc T003 |
| Người chốt | Nhóm Feature 110 (theo quyết định đã cung cấp) |
| Tài liệu đối chiếu | Feature 110, Feature 011, Feature 120 và Database README; chưa phát hiện mâu thuẫn nghiệp vụ |

### Cách ghi nhận quyết định chính thức

Repository không có công cụ phê duyệt quyết định riêng. Bảng **Bảng quyết định chính thức** ở trên là bản ghi quyết định nghiệp vụ hiện tại; các trường kỹ thuật chưa chốt được giữ nguyên là mở, không điền bằng giả định. Khi nhóm chốt T003, cập nhật cùng Decision ID và đồng bộ tài liệu liên quan.

| Trường | Giá trị cần ghi |
|---|---|
| Decision ID | `T001-application-identity` |
| Phạm vi | current application, resubmit, history, retry/idempotency |
| Phương án được chọn | Đã ghi nhận: Phương án 1 / A |
| Query/index | Cập nhật khi T003 chốt; không tự thêm index |
| Server operation | Cập nhật khi T003 chốt cơ chế nguyên tử trước/sau timeout |
| Người chốt | Nhóm Feature 110; tên đại diện có thể bổ sung trong biên bản nhóm |
| Ngày chốt | Theo biên bản nhóm; không tự suy đoán |
| Tài liệu đồng bộ | Feature 110, Feature 120, Feature 011 và database README nếu cần |

Phương án nghiệp vụ đã được nhóm xác nhận, nhưng T001 vẫn giữ **chưa hoàn thành** trong `tasks.md` vì tiêu chí task còn yêu cầu query application hiện hành và phân biệt retry; hai phần này cần contract kỹ thuật của T003. T003, T015–T016, T019, T021, T024, T026–T029 tiếp tục bị block ở các phần phụ thuộc. Không triển khai submit/resubmit hoặc sửa Rules trong lượt này.

## T003 — Tính nhất quán submit/resubmit và kết quả authoritative

Phần này là **đề xuất contract để nhóm xác nhận**, không phải triển khai. T001 đã chốt ý nghĩa nghiệp vụ của document; T003 cần chốt thao tác server, truy vấn và phản hồi xung đột trước khi cho phép client ghi.

### Bất biến áp dụng cho mọi thao tác

- `users.role` và `users.ownerStatus` được đọc từ server trong cùng luồng kiểm tra; client không gửi hoặc tự sửa các trường này.
- Client chỉ gửi các trường applicant-owned của `ownerApplications`; `status`, `rejectReason`, `reviewedBy`, `reviewedAt` và metadata server vẫn do server quản lý.
- Mọi quyết định ghi phải kiểm tra trạng thái authoritative ngay trước khi ghi. Dữ liệu cache, trạng thái UI hoặc kết quả đọc trước đó không đủ để cấp quyền ghi.
- Ghi chuyển trạng thái và việc tạo/cập nhật application phải là một thao tác server nguyên tử hoặc transaction tương đương. Không coi hai lệnh client nối tiếp là atomic.
- Nếu phản hồi bị mất, client phải đọc lại trạng thái authoritative trước khi tạo hoặc cập nhật lần nữa. Không dùng auto-ID, thời gian, thứ tự query hoặc so sánh payload làm idempotency ngầm.
- Khi phát hiện trạng thái đã đổi, server trả conflict/stale result; không ghi đè trạng thái mới hơn và không báo thành công dựa trên optimistic UI.

### Các tình huống cần contract

| Tình huống | Trạng thái trước → sau | Race cần chặn | Kiểm tra server và kết quả | Hành vi client | Schema hiện tại |
|---|---|---|---|---|---|
| Submit lần đầu | `role=PLAYER`, `ownerStatus=NONE`, không có application đang hiệu lực → tạo application auto-ID, `status=PENDING`; user vẫn `PLAYER` | Hai thiết bị cùng thấy `NONE` rồi cùng tạo | Transaction/Edge Function đọc `users` và application phù hợp; chỉ một thao tác được chấp nhận, thao tác còn lại trả conflict hoặc kết quả application đã tạo | Hiển thị PENDING khi có kết quả authoritative; conflict thì tải lại và giữ draft | Có các field cần ghi và auto-ID, nhưng chưa có khóa duy nhất để chống hai create đồng thời; cần contract server/index/query |
| Resubmit `REJECTED` | Document rejected hiện tại → cùng document `PENDING` | Admin/thiết bị khác đổi `REJECTED` sang trạng thái khác trong lúc sửa | Đọc đúng document rejected và kiểm tra trạng thái ngay trong transaction; chỉ cập nhật applicant-owned fields khi vẫn `REJECTED` | Thành công thì tải PENDING; stale thì giữ draft, tải trạng thái mới, không ghi đè | README cho U khi REJECTED; schema đủ cho update, nhưng Rules/transaction và document lookup chưa chốt |
| Đăng ký lại sau revoke | `users.ownerStatus=NONE`, application `APPROVED` cũ giữ nguyên → tạo application auto-ID mới `PENDING` | Thiết bị cũ gửi lại; nhiều document cùng UID; revoke/approve chạy đồng thời | Transaction phải đọc `ownerStatus`; chỉ tạo mới khi hiện vẫn `NONE` và không có application active theo tiêu chí đã duyệt | Retry đọc lại kết quả; nếu đã có PENDING thì dùng kết quả đó, không tạo thêm | Giữ được approved history và tạo auto-ID, nhưng không xác định duy nhất application mới nếu có nhiều document cùng UID |
| Timeout nhưng server đã xử lý | Ghi đã commit (`PENDING`) nhưng client không nhận response | Client tưởng thất bại và tạo document/update lần hai | Read-back authoritative theo UID/application identity; trả kết quả đã commit hoặc conflict, không tạo thao tác mới mù | Hiển thị kết quả server; chỉ cho retry sau read-back | Không có request ID/version; cần thao tác server và tiêu chí query được duyệt |
| Retry cùng lần submit | Trạng thái đã là kết quả của lần gửi trước → giữ nguyên | Retry có thể tạo application thứ hai hoặc áp dụng payload cũ lên hồ sơ mới | Server nhận diện bằng trạng thái/document identity trong transaction; nếu không chứng minh được cùng lần gửi thì trả ambiguous/conflict, không tự tạo | Đọc lại, yêu cầu người dùng xác nhận khi ambiguous; không báo thành công trước authoritative read | Schema không có idempotency key; không được tự thêm |
| Hai thiết bị submit đồng thời | Cùng trạng thái trước → chỉ một chuyển hợp lệ | Cả hai cùng resubmit hoặc một create, một resubmit | Transaction kiểm tra `users.ownerStatus` và document trạng thái; một commit thắng, commit kia nhận conflict/stale | Tải trạng thái mới nhất, giữ draft cục bộ nếu cần; không ghi đè | Firestore transaction có thể bảo vệ cùng document; lựa chọn application sau revoke vẫn chưa đủ |
| Admin duyệt/từ chối hoặc revoke trong lúc submit | Client đọc cũ → server đã đổi `PENDING`/`APPROVED`/`REJECTED` hoặc `NONE` | Client ghi applicant fields hoặc tạo hồ sơ dựa trên snapshot cũ | Server kiểm tra lại `ownerStatus`, application status và quyền; admin decision/revoke là authoritative; từ chối stale write | Hiển thị conflict, refresh; không tự chuyển lại trạng thái và không cấp owner từ cache | Protected fields có trong schema; cần contract kiểm tra atomic và lỗi stale |
| Trạng thái không rõ sau lỗi mạng | Không biết commit hay chưa → chưa được coi là thành công/thất bại | Retry mù gây duplicate hoặc ghi đè | Read authoritative trước mọi retry; nếu không phân biệt được thì trả `AMBIGUOUS_RESULT` và không tạo mới | Giữ draft, cho refresh/reconcile; chỉ retry khi server xác nhận thao tác chưa commit hoặc trả lại identity đã commit | Schema hỗ trợ đọc các field hiện có nhưng không cung cấp identity/idempotency độc lập |

### Query application hiện hành sau revoke

#### Những gì đã xác nhận

- `ownerApplications/{id}` dùng Firestore auto-ID; auto-ID không mang ý nghĩa thời gian và không được dùng để suy ra document mới nhất.
- Schema hiện có `uid`, `status`, `submittedAt` và dữ liệu hồ sơ, nhưng không có current pointer, request ID, version hay revision collection.
- Database README chỉ ghi index `ownerApplications(status, submittedAt)` cho hàng đợi hồ sơ chờ duyệt. Chưa có index/contract được xác nhận cho truy vấn theo `uid` để chọn duy nhất hồ sơ hiện hành.
- `users.ownerStatus=NONE` sau revoke xác định quyền hiện hành, nhưng không tự chỉ ra application document mới. Application `APPROVED` cũ phải được loại khỏi quyền hiện hành.

#### Các phương án

| Phương án | Cách hoạt động | Ưu điểm | Rủi ro/tác động | Tình trạng |
|---|---|---|---|---|
| A. Query server theo `uid` + trạng thái + `submittedAt` | Edge Function đọc các document của UID, lọc trạng thái, sắp xếp thời gian | Không thêm field/collection | Chưa có index theo UID; `submittedAt` có thể trùng; thứ tự auto-ID không bảo đảm; vẫn cần quy tắc khi có nhiều candidate | Chỉ là phương án phân tích, chưa được phép triển khai |
| B. Bổ sung composite index cho query theo UID/trạng thái/thời gian | Server dùng query được index và quy tắc tie-break đã duyệt | Hiệu năng và kết quả dễ kiểm chứng hơn | Là thay đổi database/index, cần owner CSDL và contract phê duyệt; index không tự giải quyết hai timestamp trùng | Chờ quyết định, không tự tạo |
| C. Thêm current pointer/ID hoặc version | User/server giữ document hiện hành rõ ràng | Truy vấn và stale-write rõ hơn | Vi phạm quyết định T001 không thêm field/schema; cần migration và Rules mới | Không được thực hiện |
| D. Ép client luôn giữ document ID | Resubmit dùng ID đã mở; create mới trả ID cho lần sau | Có thể xác định document cho một phiên đã biết | Không giải quyết initial query sau revoke, mất identity khi response timeout, và không đủ chống duplicate nếu chưa có idempotency | Chỉ có thể là một phần contract, không đủ làm current lookup |

**Khuyến nghị**: chưa chọn A–D để triển khai. Nhóm cần xác nhận một contract server authoritative (có thể qua Edge Function/Firestore transaction) và tiêu chí chọn duy nhất application `PENDING`/`REJECTED` sau revoke. Nếu tiêu chí đó không thể đáp ứng bằng schema/index hiện tại, phải phê duyệt thay đổi index hoặc schema trước T015/T019/T021/T024/T026–T029. Không dùng document trả về đầu tiên, thứ tự auto-ID hoặc `submittedAt` đơn độc.

### Các quyết định nhóm cần chốt cho T003

1. Boundary server nào thực hiện transaction: Edge Function nào, hoặc cơ chế server tương đương, và lỗi/conflict có kiểu gì.
2. Quy tắc chọn duy nhất application hiện hành sau revoke khi có nhiều document cùng UID; xác nhận có cho phép index mới hay phải thay đổi schema.
3. Cách trả identity của application đã commit để client reconcile sau timeout mà không cần request ID/version.
4. Quy tắc xử lý ambiguous result: khi nào đọc lại đủ để retry, khi nào phải dừng và yêu cầu người dùng làm mới.
5. Ưu tiên khi admin decision/revoke cạnh tranh với submit/resubmit và mã lỗi stale/conflict tương ứng.
6. Cách kiểm thử transaction, hai thiết bị, timeout và read-back trên Firebase Emulator/local Edge Function.

T003 **chưa hoàn thành** cho tới khi các quyết định trên được nhóm xác nhận và có test contract tương ứng. Không cập nhật checkbox task hoặc triển khai submit/resubmit trong khi các điểm này còn mở.

## Bảng quyết định kỹ thuật cần nhóm chốt cho T001/T003

Bảng này rút gọn các lựa chọn từ T001/T003 thành các câu hỏi có thể quyết định. Đây là tài liệu chuẩn bị; không phương án nào dưới đây được xem là đã phê duyệt.

| Decision ID | Vấn đề / phương án | Ưu điểm | Nhược điểm và tác động | Khuyến nghị có căn cứ | Bắt buộc nhóm chốt |
|---|---|---|---|---|---|
| T003-SERVER | **Client ghi trực tiếp**; **Edge Function dùng Firestore transaction**; hoặc server boundary khác được phê duyệt | Client đơn giản; Edge Function kiểm tra Firebase token, `users` và application trong một boundary, phù hợp Constitution/README | Client trực tiếp khó bảo đảm atomicity giữa nhiều document và không nên ghi protected state; Edge Function cần contract endpoint, auth và test local; repository hiện có `_shared/auth.ts`, `_shared/firestore.ts` và `on-signup`, chưa có submit-owner function | Edge Function/transaction là hướng phù hợp kiến trúc server-side hiện tại, nhưng phải chốt tên boundary, payload applicant-owned, lỗi và quyền | Có. Không bắt đầu T015/T026 nếu chưa chốt |
| T003-IDENTITY | Query server theo `uid`/status/`submittedAt`; thêm composite index; current pointer/version; hoặc giữ document ID do client | Query dùng field hiện có; index cải thiện hiệu năng; pointer/version làm current/stale rõ; document ID hữu ích khi đã biết hồ sơ | Query hiện chưa có index UID và timestamp có thể trùng; index là thay đổi CSDL; pointer/version bị T001 loại; document ID không giải quyết initial lookup/timeout | Không dùng kết quả đầu tiên, auto-ID hay `submittedAt` đơn độc. Nhóm cần chốt query/index hoặc xác nhận schema hiện tại không đủ | Có. Đây là blocker trực tiếp của current application sau revoke |
| T003-ATOMIC | Firestore transaction đọc `users` + application rồi create/update; batch/write; hoặc đọc rồi ghi ở client | Transaction kiểm tra snapshot và tự retry khi xung đột; batch chỉ atomic phần ghi đã biết; client read/write không chống race | Transaction vẫn cần truy vấn candidate xác định; batch không bảo vệ điều kiện đọc; client có race duplicate/stale | Transaction server cho submit/resubmit; batch chỉ dùng cho các ghi đã có identity, không thay transaction điều kiện | Có. Phải chốt phạm vi transaction cho create mới, resubmit và phối hợp revoke/decision |
| T003-RETRY | Read-back authoritative sau timeout; retry mù; request/idempotency key mới | Read-back không thêm schema và phù hợp ràng buộc hiện tại; key giải quyết duplicate rõ hơn | Read-back không luôn phân biệt cùng payload nếu có nhiều candidate; retry mù có thể duplicate; key/requestId bị cấm tự thêm | Bắt buộc read-back trước retry; nếu không xác định được commit thì trả ambiguous/conflict và giữ draft, không tự tạo mới | Có. Cần định nghĩa kết quả `committed`, `not-committed`, `ambiguous` |
| T003-STALE | So sánh trạng thái authoritative trong transaction; version/etag; hoặc chấp nhận last-write-wins | Transaction không thêm field; version/etag phát hiện stale rõ; last-write-wins đơn giản | Không có version/etag thì chỉ phát hiện được trạng thái chuyển đổi, không phát hiện mọi payload cũ; last-write-wins có thể ghi đè dữ liệu mới | Dùng transaction kiểm tra trạng thái (`REJECTED` cho resubmit, `NONE` cho create) và trả conflict khi đổi; không dùng last-write-wins | Có. Phải chốt mã lỗi và dữ liệu client được giữ lại |
| T003-TEST | Firebase Emulator Rules + transaction harness; Supabase local Edge Function; unit fake | Emulator kiểm chứng Rules/concurrency Firestore; Supabase local kiểm chứng auth boundary/Edge Function; fake nhanh cho ViewModel | Hiện có Firestore test harness và Edge Function shared tests, chưa có owner submit function/storage contract; emulator không chứng minh production | Bổ sung test sau khi boundary được chốt: duplicate create, concurrent create/resubmit, admin decision/revoke cạnh tranh, timeout sau commit, read-back và stale conflict | Có. Test là điều kiện hoàn thành T003 |

### Bộ kiểm thử tối thiểu sau khi contract được phê duyệt

| Nhóm test | Kịch bản | Kết quả bắt buộc |
|---|---|---|
| Duplicate submit | Hai request cùng UID/`NONE` chạy đồng thời | Tối đa một application `PENDING`; request còn lại nhận conflict hoặc cùng identity authoritative |
| Concurrent resubmit | Hai thiết bị cùng document `REJECTED` | Một chuyển `PENDING`; request còn lại không ghi đè và nhận stale/conflict |
| Admin cạnh tranh | Admin approve/reject hoặc revoke giữa lúc client submit | Quyết định server thắng; client refresh, không báo thành công dựa trên snapshot cũ |
| Timeout sau commit | Commit thành công nhưng response bị drop | Read-back nhận đúng application đã commit; retry không tạo document thứ hai |
| Ambiguous/read-back | Không xác định commit và query trả nhiều candidate | Không chọn tùy ý; trả ambiguous, giữ draft và yêu cầu refresh/contract xử lý |
| Rule bypass | Gọi trực tiếp Firestore với field protected hoặc trạng thái sai | Bị deny; test chạy Emulator, không dựa vào Android build |

### Câu hỏi ngắn cần nhóm trả lời

1. Chọn **Edge Function + Firestore transaction** làm server boundary cho submit/resubmit không?
2. Sau revoke, chấp nhận query hiện có theo `uid`/status/`submittedAt` với quy tắc xử lý tie hay phê duyệt composite index? Nếu không, schema hiện tại có được tuyên bố chưa đủ không?
3. Khi timeout mà read-back không xác định duy nhất, hệ thống trả `AMBIGUOUS_RESULT` và dừng retry có được chấp nhận không?
4. Chọn mã lỗi/stale contract nào cho admin decision/revoke cạnh tranh?
5. Cho phép mở T015/T016/T026 sau khi contract và bộ Emulator/local test trên được phê duyệt không?

## Trắc nghiệm quyết định kỹ thuật T001/T003

Các lựa chọn dưới đây được xây dựng từ kiến trúc repository, schema hiện có và các contract Feature 011/120. Dấu ⭐ chỉ là **khuyến nghị**, chưa phải quyết định được phê duyệt. Trả lời nhanh theo mẫu `1A, 2B, 3A...`.

### 1. T003-SERVER — Server boundary

**Câu hỏi:** Submit và resubmit nên được xử lý ở đâu?

- **A. Client ghi trực tiếp Firestore qua Rules**: client tự create/update application; Rules kiểm tra trạng thái.
- **B. ⭐ Edge Function gọi Firestore transaction**: Edge Function xác thực Firebase ID token, đọc `users` và application, rồi transaction create/update.
- **C. Supabase Edge Function lưu nghiệp vụ trong Supabase Database**: Firestore chỉ giữ bản sao trạng thái.
- **D. Client gọi một HTTP service mới ngoài Supabase**: service đó sở hữu toàn bộ submit/resubmit.

**⭐ Khuyến nghị:** **B**. Constitution yêu cầu thao tác nhạy cảm qua Supabase Edge Functions; README đã có shared auth/Firestore REST helper và Firestore vẫn là nguồn dữ liệu nghiệp vụ.

**Trade-off:** Cần triển khai function, xác định payload/lỗi và test local; thêm một boundary server thay vì gọi Firestore trực tiếp.

**Tác động:** Cần backend Edge Function và contract auth/error/transaction; không cần schema mới. Rules phải tiếp tục chặn client ghi protected state.

**Mức độ:** **Bắt buộc chốt trước T015/T016/T026.**

### 2. T003-IDENTITY — Application hiện hành sau revoke

**Câu hỏi:** Khi một UID có application `APPROVED` cũ và application mới sau revoke, server xác định hồ sơ hiện hành thế nào?

- **A. Query theo `uid`, lọc status rồi chọn document đầu tiên trả về.**
- **B. ⭐ Query server theo `uid` + status active, dùng composite index được nhóm phê duyệt và invariant tối đa một hồ sơ active.**
- **C. Thêm current pointer/version vào `users` hoặc `ownerApplications`.**
- **D. Luôn yêu cầu client gửi application document ID; không hỗ trợ current lookup theo UID.**

**⭐ Khuyến nghị:** **B**. Giữ schema nghiệp vụ hiện tại, loại `APPROVED` cũ bằng `users.ownerStatus`, và dùng query/index được xác nhận để tránh phụ thuộc thứ tự auto-ID. Invariant một hồ sơ `PENDING`/`REJECTED` active giúp query không phải đoán giữa nhiều ứng viên.

**Trade-off:** Cần phê duyệt composite index và định nghĩa chính xác tập trạng thái active; index không tự giải quyết dữ liệu lịch sử hoặc timestamp trùng nếu invariant bị vi phạm.

**Tác động:** Có thể cần thêm index và cập nhật contract/Rules/backend; không thêm field/collection. Feature 011 đọc `ownerStatus`; Feature 120 tiếp tục giữ approved history.

**Mức độ:** **Bắt buộc chốt trước T019/T021/T024/T026 và submit thật.**

### 3. T003-RETRY — Timeout, retry và idempotency

**Câu hỏi:** Client xử lý thế nào khi timeout và không biết submit đã commit chưa?

- **A. Retry mù ngay bằng create/update mới.**
- **B. Retry bằng `requestId`/idempotency key mới được thêm vào schema.**
- **C. ⭐ Read-back authoritative trước; nếu xác định hồ sơ đã commit thì trả kết quả đó, nếu chưa commit mới retry; nếu vẫn không xác định thì trả ambiguous và giữ draft.**
- **D. Chỉ dựa vào disable nút submit ở UI và không retry tự động.**

**⭐ Khuyến nghị:** **C**. Phù hợp schema hiện tại và không giả lập idempotency bằng auto-ID/timestamp; tránh duplicate và không báo thành công trước server confirmation.

**Trade-off:** Không thể chứng minh mọi retry là cùng một lần gửi nếu có nhiều ứng viên hoặc response mất trước khi trả identity; một số trường hợp phải yêu cầu người dùng refresh.

**Tác động:** Cần contract read-back, trạng thái `committed/not-committed/ambiguous`, backend query và UI giữ draft; không cần `requestId` nếu nhóm chấp nhận giới hạn này.

**Mức độ:** **Bắt buộc chốt trước T016/T026.**

### 4. T003-STALE — Stale-write và conflict

**Câu hỏi:** Khi admin hoặc thiết bị khác đổi trạng thái trong lúc client đang sửa, xử lý thế nào?

- **A. Last-write-wins: chấp nhận ghi đè nếu payload hợp lệ.**
- **B. Thêm `version`/etag vào schema để so sánh phiên bản.**
- **C. ⭐ Transaction kiểm tra trạng thái authoritative (`NONE` cho create, `REJECTED` cho resubmit); nếu đã đổi thì trả conflict/stale và không ghi.**
- **D. Chỉ kiểm tra khi mở form, không kiểm tra lại lúc ghi.**

**⭐ Khuyến nghị:** **C**. Không thêm field bị T001 loại, đồng thời ngăn client ghi đè quyết định của Admin hoặc trạng thái mới hơn.

**Trade-off:** Chỉ phát hiện được xung đột mà transaction đọc thấy; không lưu lịch sử phiên bản đầy đủ. Client phải refresh và giữ draft cục bộ khi conflict.

**Tác động:** Cần backend transaction, mã lỗi conflict/stale và test Rules/Emulator; không đổi schema. Feature 120 vẫn là nguồn quyết định approve/reject/revoke.

**Mức độ:** **Bắt buộc chốt trước T015/T016/T026/T027.**

### 5. T003-TEST — Phạm vi Emulator/local

**Câu hỏi:** Bộ kiểm thử tối thiểu nào phải đạt trước khi mở implementation submit/resubmit?

- **A. Chỉ unit test ViewModel và Android build.**
- **B. Chỉ Firebase Emulator Rules test; không chạy Edge Function local.**
- **C. Firebase Emulator cho Rules/transaction, cộng unit test fake; bỏ qua timeout và concurrency.**
- **D. ⭐ Firebase Emulator + local Edge Function harness: duplicate/concurrent submit, resubmit race, admin approve/reject/revoke cạnh tranh, timeout sau commit, read-back ambiguous, stale conflict và protected-field bypass.**

**⭐ Khuyến nghị:** **D**. Constitution yêu cầu test server authorization/transaction trên Emulator/local; Android build không chứng minh được atomicity hoặc quyền trực tiếp.

**Trade-off:** Cần dựng harness và kịch bản giả lập response mất; thời gian kiểm thử lớn hơn unit test đơn thuần.

**Tác động:** Cần test harness cho Edge Function/Firestore; không đổi schema. Nếu có Storage contract riêng, test Supabase local vẫn là một cổng độc lập của T009/T010.

**Mức độ:** **Bắt buộc chốt trước khi đánh dấu T003 hoàn thành.**

### 6. T003-AUTH — Xác thực boundary

**Câu hỏi:** Edge Function xác định người gọi và quyền ứng dụng bằng cách nào?

- **A. Tin UID/role do client gửi trong body.**
- **B. Dùng Firebase ID token, verify ở shared auth, đọc `users/{uid}` authoritative; không tin appRole từ body.**
- **C. Chỉ kiểm tra Firebase Authentication ở Android, function không verify lại token.**
- **D. Dùng Supabase anonymous session cho submit.**

**⭐ Khuyến nghị:** **B**. Phù hợp `supabase/functions/_shared/auth.ts`, Constitution và nguyên tắc server-side authorization.

**Trade-off:** Function phải xử lý token hết hạn/refresh và mapping Firebase UID với Firestore; cần contract lỗi auth rõ ràng.

**Tác động:** Cần contract auth/token refresh và Edge Function implementation; không đổi schema.

**Mức độ:** **Bắt buộc chốt trước T002/T003 và mọi integration backend.**

### 7. T003-ERROR — Kết quả và lỗi có kiểu

**Câu hỏi:** Client phân biệt kết quả submit/resubmit thế nào?

- **A. Chỉ trả boolean thành công/thất bại.**
- **B. Trả HTTP status tùy ý, client tự diễn giải message.**
- **C. ⭐ Contract hóa các nhóm `SUCCESS`, `CONFLICT/STALE`, `NOT_AUTHORIZED`, `AMBIGUOUS_RESULT`, `VALIDATION_ERROR`, `RETRYABLE_ERROR`; thành công phải kèm application identity đã authoritative xác nhận.**
- **D. Dùng exception nội bộ của Firestore trực tiếp làm API contract.**

**⭐ Khuyến nghị:** **C**. Cho phép UI giữ draft, refresh hoặc retry có điều kiện mà không phụ thuộc chuỗi message.

**Trade-off:** Cần thống nhất mã lỗi và mapping giữa Edge Function, repository và ViewModel.

**Tác động:** Cần cập nhật boundary contract/backend client mapping; không đổi schema.

**Mức độ:** Có thể chốt cùng T003, nhưng phải có trước T015/T016.

### Kết quả lựa chọn của nhóm

Các lựa chọn dưới đây được người yêu cầu xác nhận trong lượt trao đổi này. Đây là quyết định contract để làm cơ sở cho bước implementation tiếp theo; chưa có thay đổi code, schema, index, Rules hoặc backend.

| Decision ID | Lựa chọn đã xác nhận | Phạm vi được chốt | Việc còn phải triển khai |
|---|---|---|---|
| T003-SERVER | **B — Edge Function + Firestore transaction** | Submit/resubmit đi qua server boundary; Firestore vẫn là nguồn dữ liệu nghiệp vụ | Tạo contract endpoint, auth/error mapping, transaction và test local |
| T003-IDENTITY | **B — Query server theo `uid` + status active với composite index được phê duyệt** | Không dùng auto-ID/thứ tự query; giữ invariant tối đa một application active | Owner CSDL phải xác nhận index; backend phải định nghĩa active statuses và xử lý dữ liệu vi phạm invariant |
| T003-RETRY | **C — Read-back authoritative trước retry; ambiguous thì dừng và giữ draft** | Không retry mù, không tự thêm `requestId`/idempotency key | Định nghĩa kết quả committed/not-committed/ambiguous và read-back operation |
| T003-STALE | **C — Transaction kiểm tra authoritative state, conflict/stale khi trạng thái đổi** | Không last-write-wins, không thêm version/etag | Mã lỗi conflict/stale, client refresh và giữ draft; test race với Admin/thiết bị khác |
| T003-TEST | **D — Firebase Emulator + local Edge Function harness** | Bao phủ duplicate/concurrent submit, resubmit race, Admin action, timeout, read-back và protected-field bypass | Dựng owner submit harness và các test contract; không dùng Android build thay cho security/concurrency test |
| T003-AUTH | **B — Verify Firebase ID token bằng shared auth và đọc `users/{uid}` authoritative** | Không tin UID/role/appRole do client gửi; server xác định quyền | Chốt token refresh/error contract và tích hợp function với shared helpers |
| T003-ERROR | **C — Typed result/error contract** | Dùng `SUCCESS`, `CONFLICT/STALE`, `NOT_AUTHORIZED`, `AMBIGUOUS_RESULT`, `VALIDATION_ERROR`, `RETRYABLE_ERROR` | Ánh xạ mã lỗi Edge Function → repository → ViewModel; SUCCESS phải kèm identity authoritative |

### Trạng thái sau khi chọn

- Quyết định nghiệp vụ T001 và các lựa chọn kỹ thuật T003 đã có phương án được chọn trong contract.
- Composite index trong T003-IDENTITY mới là quyết định cần triển khai/phê duyệt ở database owner; chưa được tạo trong lượt này.
- T003 chưa hoàn thành cho đến khi Edge Function/transaction, query/index, error contract và Emulator/local tests được triển khai và kiểm chứng.
- Không đánh dấu task hoặc mở submit/resubmit implementation chỉ vì đã chọn đáp án.

## Rà soát khả năng unblock sau khi chọn phương án

Kết quả kiểm tra repository sau khi các đáp án được chọn:

- `firestore.indexes.json` đã có composite index `ownerApplications(uid ASC, status ASC, submittedAt DESC)`. Index chỉ làm cho query được hỗ trợ; nó **không** tạo uniqueness constraint và không tự ngăn hai transaction cùng tạo application `PENDING`.
- `firestore.rules` hiện vẫn cho `ownerApplications` chỉ `get` applicant/admin và deny `create`, `update`, `delete` của client. Đây là trạng thái phù hợp với server boundary đã chọn, nhưng chưa phải implementation của submit/resubmit.
- Supabase hiện có `_shared/auth.ts`, `_shared/firestore.ts` và `on-signup`; chưa có owner submit/resubmit function, transaction helper cho workflow này, error mapping hoặc local owner harness.
- Vì vậy T003 đã có quyết định nhưng còn **BLOCKED — IMPLEMENTATION**. T015/T016/T026/T027/T029 không được coi là sẵn sàng chỉ dựa trên contract/index.
- Phần upload/download vẫn **BLOCKED — DECISION** ở T002/T009: README xác nhận bucket và `sign-upload`/`sign-download`, nhưng chưa có input/output, applicant authorization và policy local được phê duyệt. Không mở Storage policy để làm UI hoạt động.

## Bảng lựa chọn kỹ thuật đã xác nhận sau phiên trắc nghiệm

Ngày xác nhận: 2026-10-26. Các lựa chọn dưới đây được nhóm xác nhận bằng CONFIRM; đây là quyết định kỹ thuật để unblock thiết kế, chưa đồng nghĩa task triển khai đã hoàn thành.

| ID | Lựa chọn | Tác động và giới hạn |
|---|---|---|
| T002-CONTRACT | A — Android gọi HTTPS Supabase Edge Function với Firebase ID token Bearer và shared auth. | Cần owner functions; không mở client write trực tiếp. |
| T002-UPLOAD | A — sign-upload kiểm tra token, UID/application, trạng thái, path, MIME và kích thước. | Bucket private; cần policy và test ownership. |
| T004-CLEANUP | D — server delete-account dọn tài nguyên; periodic cleanup chỉ xóa orphan đã xác nhận. | Phụ thuộc account/delete contract; chưa triển khai. |
| T012-MAP | B — map/marker thủ công từ dependency hiện có, không bắt buộc location permission. | District catalog vẫn qua boundary được duyệt. |
| T013-FILES | A — Photo Picker/SAF, state theo file, chỉ lưu path sau upload thành công. | URI local không được coi là object đã upload. |
| T007-RULES | B — client create/update/delete bị deny; server transaction là boundary. | Cần Rules tests khi server operation tồn tại. |
| T019-STATUS | B — users.ownerStatus authoritative; ambiguous thì conflict/refresh. | Không suy ra quyền từ APPROVED cũ sau revoke. |
| T025-REJECTED | A — giữ draft/path cũ; resubmit cùng document REJECTED -> PENDING. | Cần stale handling T003. |
| T033-DOWNLOAD | B — sign-download server-side, xác minh token/ownership/path, URL ngắn hạn. | Không public URL; cần function/test. |
| T003-RESULT | B — response tối thiểu trả applicationId; trạng thái phải read-back authoritative. | Cần typed error/read-back contract; không suy ra từ cache. |
| T012-DISTRICTS | B — catalog do server/interface được kiểm soát cung cấp. | Không mở client read districts tùy tiện. |
| T014-REVIEW | A — chỉ submit khi file bắt buộc đã server-confirmed. | Pending/failed phải retry hoặc sửa. |
| T003-TEST | C — Firebase Emulator + local Edge Function harness với fake boundary. | Chưa phải production integration. |
| T035-USABILITY | A — ít nhất 10 người dùng mục tiêu sau khi flow ổn định. | Không giả lập kết quả bằng test tự động. |
| T003-RETRY | C — timeout read-back authoritative; ambiguous thì dừng và giữ draft. | Không có idempotency mạnh khi thiếu requestId. |
| T003-STALE | C — transaction kiểm tra state; stale trả typed conflict. | Không ghi đè quyết định mới hơn. |
| T003-IDENTITY | B — query UID/status với invariant; không duy nhất thì ambiguous/conflict. | Index không tự bảo đảm uniqueness sau revoke. |
| T032-INTEGRATION | A — local emulator/harness trước; staging/production verification riêng. | Không dùng credentials production trong local. |

Các lựa chọn không thay đổi schema, Rules, Storage policy hoặc task checkbox. T002/T003 vẫn cần implementation thực tế: owner submit/resubmit, sign-upload, sign-download, transaction/query helper, response/error mapping và test harness chưa tồn tại. T003-IDENTITY chỉ an toàn khi server chứng minh invariant; nếu không, phải trả ambiguous thay vì chọn document theo auto-ID hoặc thứ tự query. T007/T008 chỉ có thể hoàn thiện phần Rules/read tests độc lập; T012/T014/T017 có thể tiếp tục phần UI/fake; T019/T020/T022/T023 chỉ làm status UI/mapper bằng fake cho tới khi query authoritative được triển khai.

## Hồ sơ triển khai hiện tại — ranh giới đã chốt và phần còn mở

Phần này ghi lại chi tiết triển khai đã có căn cứ trong quyết định nhóm ngày 2026-10-26 và code hiện tại; không mở rộng schema hay quyền client.

### App → Edge Functions

- Android gọi Supabase Edge Function bằng Supabase Kotlin Functions client đã có trong dự án. Firebase ID token được gắn bởi shared auth retry; khi hết hạn app refresh Firebase token rồi thử lại theo `FunctionErrors`. Request chỉ chứa dữ liệu applicant-owned. Không gửi role, `ownerStatus`, application status hoặc review fields làm nguồn tin cậy.
- Edge Functions đặt `verify_jwt = false` ở gateway để không đòi Supabase Auth JWT; mỗi handler phải gọi shared `requireUser`, xác minh Firebase ID token và dùng UID đã xác minh. Quyền trạng thái đọc từ `users/{uid}` bằng Firestore REST service boundary.
- `get-owner-application-status` là GET không payload; trả `ownerStatus`, role authoritative và application duy nhất khớp UID/status. Document paths chỉ trả trong application của UID đã xác minh; không trả signed URL hoặc thông tin reviewer.
- `owner-document-download` là POST `{ applicationId, path }`; thành công trả `{ signedUrl, expiresInSeconds: 300 }`, có `Cache-Control: private, no-store`. Handler kiểm tra UID trong path, application owner và membership chính xác trong `documentPaths`; lỗi không tiết lộ path khác. Android yêu cầu URL mới cho từng thao tác mở và không lưu URL.
- Các nhóm lỗi server dùng mã ổn định (ví dụ `NOT_AUTHORIZED`, `NOT_FOUND`, `VALIDATION_ERROR`, `AMBIGUOUS_RESULT`, `CONFLICT`, `RETRYABLE_ERROR`, `STORAGE_UNAVAILABLE`). Client hiển thị thông báo tổng quát; chỉ retry auth theo shared helper hoặc yêu cầu URL mới sau thao tác người dùng.

### Submit/resubmit hiện thực

- `owner-application-submit` là POST gồm `applicationId` Firestore auto-ID do app cấp trước khi upload, `venueDraft`, `representativeName`, `representativePhone`, `documentPaths`. Auto-ID này định danh document, không phải idempotency key.
- Trước Firestore transaction, server phải xác minh mọi object đã tồn tại; verifier production hiện chưa được cấu hình và mặc định trả `UPLOAD_VERIFICATION_UNAVAILABLE`. Không được bỏ qua verifier hoặc báo gửi thành công khi verifier không hoạt động.
- Transaction đọc `users/{uid}` authoritative, query theo UID/status và chặn trạng thái không nhất quán. `NONE` tạo một application mới; `REJECTED` cập nhật cùng document nếu ID trùng; `PENDING` không tạo application cạnh tranh; `APPROVED` không cho submit. User và application được ghi trong cùng transaction.
- Thành công trả `{ applicationId }`; Android luôn gọi authoritative status read-back. Sau timeout, chỉ retry khi read-back chứng minh `NONE`; trạng thái PENDING cùng ID xác nhận thành công, ID khác là conflict, kết quả không duy nhất/không đọc được là ambiguous và giữ draft. Transaction abort trả retryable conflict; không có `requestId`, version hoặc field mới.
- Đây là contract đang được source triển khai, chưa được coi là đã kiểm chứng cho tới khi Edge Function harness và Firebase Emulator/local integration tests chạy.

### Giới hạn upload còn chặn T009/T013

- Nhóm đã chốt shared-auth signed upload, MIME `image/jpeg`, `image/png`, `image/webp`, tối đa 2 MB, và giấy tờ theo `{uid}/{applicationId}/{filename}`. Tuy nhiên README chỉ cho `venue-photos` path `{venueId}/{filename}` và quyền upload owner đã duyệt; Feature 110 cần upload trước khi có `venueId`.
- Không có quyết định path/bucket cho ảnh draft trước duyệt hoặc vòng đời xóa object khi application mới sau revoke. Vì vậy chưa thể cấp signed upload cho ảnh venue, thêm policy applicant vào bucket `venue-photos`, hoặc xác minh submit toàn bộ path an toàn. `owner-application-submit` tiếp tục fail-closed cho tới khi server verifier có thể xác minh cả ảnh và giấy tờ theo path đã duyệt.
- Applicant không được upload trực tiếp bằng RLS suy diễn từ JWT. Khi path ảnh draft được nhóm chốt, signed upload phải giới hạn theo UID/application, MIME/size và thời hạn 300 giây; client chỉ lưu path sau xác nhận upload, không lưu URL.
