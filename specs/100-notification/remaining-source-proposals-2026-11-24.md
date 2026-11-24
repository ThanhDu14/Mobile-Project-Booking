# Các quyết định nguồn còn mở, 24/11/2026

Trạng thái: người dùng duyệt D4/F1 và chọn quét theo trang khi nhóm vượt ngân sách, duyệt P2 với nguồn bộ đếm Feature 050 còn thiếu, đồng thời duyệt O2 và RR1 ngày 24/11/2026. D1/D2-W1/T1/C1/P1/S1/B1/B2/D3/R1/H1 tiếp tục có hiệu lực trong phạm vi đã chốt. Trạng thái triển khai theo implementation-review.md.

## D4. Thay đổi buổi vãng lai (T023, phần T004/T005/T025)

`owner-update-session` ghi giờ mới và các đăng ký trong transaction. `owner-cancel-session` đọc đăng ký trước khi hủy và đã ghi WAITLIST_CLOSED riêng. Đề xuất chỉ tạo DROPIN_CHANGED khi startAt/endAt đổi hoặc session chuyển CANCELLED. Chụp người nhận có trạng thái REGISTERED trước mutation, loại actor, không gửi người chờ hoặc người vừa được promote trong cùng lần chỉnh. Dữ liệu inbox `{sessionId}`, với title/body lưu rõ đổi giờ hoặc hủy. Giữ route hiện tại cùng fallback.

Thêm UUID `requestId` vào hai API và giữ nguyên UUID qua retry. Tạo nhật ký server-only `dropInSessions/{sessionId}/notificationCommands/{requestId}` trong transaction nguồn, gồm actor, hash payload và kết quả. Kiểm tra quyền và replay trước mutation, trả kết quả cũ khi khớp và 409 khi cùng UUID khác payload. Giữ nhật ký theo vòng đời session, không tái sử dụng session ID. Enqueue từng người bằng D1 trong transaction nếu đủ ngân sách. Nếu vượt 400 writes hoặc 8 MiB ước lượng payload REST, đề xuất ghi snapshot người nhận vào outbox cùng transaction theo F1 để tránh từ chối buổi hợp lệ chỉ vì thông báo.

## F1. Fanout nhóm (T045/T047/T048 và phần T044)

Nguồn hiện tại gọi callback sau commit. Giữ mapping nguồn: JOIN_REQUESTED cho OWNER/ADMIN; APPROVED/REJECTED/REMOVED cho đối tượng; EVENT_CREATED cho thành viên hiện tại; EVENT_UPDATED/DELETED cho người tham dự theo điều kiện hiện hành; CHAT_MESSAGE cho thành viên trừ sender. Giữ `{event,groupId,postId?}` và `{chatId,messageId}`; mute/presence chỉ lọc push.

Đề xuất outbox server-only `notificationFanouts/{eventId}` gồm source kind, IDs, actor, subtype, content, Timestamp, state/lease/attempt/cursor, cùng `recipients/{page}` cho snapshot UID đã sắp xếp, tối đa 200 UID/page. Với tập có thể lấy trong transaction, chốt membership tại commit nguồn và ghi trang snapshot cùng event. Nếu vượt 400 writes/8 MiB, không dùng danh sách bị cắt: cần quyết định riêng về giới hạn nguồn hoặc đổi contract sang người nhận được xét theo từng trang khi worker chạy. Với PROMOTION dùng cơ chế quét riêng bên dưới. Worker đọc lại profile ACTIVE và deletion gate trước enqueue, đồng thời kiểm tra membership hiện tại cho thông báo gửi cho tập thành viên; JOIN_REJECTED/REMOVED vẫn đến đối tượng khi họ đã rời nhóm. Worker tiến từng trang bằng cursor, ghi inbox/job và cursor trong transaction, dùng D1 xử lý push. Giữ event hoặc replay marker theo vòng đời nguồn, dọn các trang snapshot sau hoàn tất 7 ngày. Loại UID đã xóa khi materialize, không tái tạo inbox cho profile mất. Payload outbox không chứa token.

GROUP chat dùng message UUID đã có. Bài tạo dùng post ID hiện hành nếu bất biến qua retry. Lệnh membership và edit/delete thiếu UUID ổn định cần `requestId` từ client, nhật ký theo source + UUID và fingerprint tương tự D4, rồi mới bỏ callback tương ứng. Hướng dự phòng cho lượng thành viên vượt ngân sách cần được chốt cùng F1 trước khi coi toàn bộ fanout hoàn thành.

## P2. Phát hành voucher sân (T022/T024 và phần T025)

`owner-create-voucher` có transaction CREATE/UPDATE/SET_ACTIVE. Feature 070 đã quy định favorites là follows; Feature 050 kiểm tra hạn dùng, giới hạn lượt và số tiền đơn tại checkout. Đề xuất phát đúng một lần khi voucher lần đầu vừa ACTIVE vừa trong thời hạn, kể cả voucher tạo trước validFrom. Thêm server-only `publishedAt` trên voucher và job F1 theo `venueId/code/PROMOTION`; ghi marker và job trong cùng transaction khi CREATE/UPDATE/SET_ACTIVE đã đủ điều kiện hoặc khi cron thấy voucher đến hạn. Không backfill voucher cũ và không phát lại khi bật lại hoặc sửa. Dùng query theo validFrom/isActive, bổ sung index sau khi chốt schema.

Đề xuất quét favorites theo venueId và document ID, chỉ xét favorite có createdAt không muộn hơn publishedAt, còn tồn tại lúc xử lý, profile PLAYER ACTIVE, đúng venue và còn hạn/lượt tổng/lượt cá nhân. Cần Feature 050 xác nhận đường dẫn bộ đếm perUserLimit và ánh xạ `validUntil/validTo` trước khi code. Kiểm tra minOrderTotal tại checkout vì tại lúc gửi chưa có đơn. Nhận định đủ điều kiện này là tại thời điểm worker xét, không bảo đảm voucher còn lượt khi mở. Dùng deterministic inbox ID theo event và UID. Route giữ saved detail cho đến khi có màn hình voucher phía người chơi cùng quyền đọc.

## O2 và RR1. Thu hồi quyền và nhắc review (T044/T046, phần T004/T005)

O2: `admin-revoke-owner` đã ghi inbox trong transaction, nhưng replay chỉ nhìn role hiện tại. Đề xuất API nhận UUID `requestId` do Android giữ qua retry, nhật ký server-only theo UID đích + UUID gồm actor/hash lý do và kết quả. Kiểm tra ADMIN hiện tại và đọc nhật ký trước khi thay role, ghi journal/inbox/job cùng transaction. Replay cũ trả kết quả cũ kể cả sau khi được duyệt lại, nhưng claims reconcile luôn dựa trên profile hiện tại. Giữ journal theo vòng đời tài khoản và dọn cùng account deletion. Giữ saved detail cho lý do thu hồi.

RR1: `complete-bookings` hiện dùng batch commit. Đề xuất chuyển riêng transition CONFIRMED → COMPLETED sang transaction, kiểm tra lại điều kiện hoàn thành và review chưa tồn tại. Enqueue một REVIEW_REMINDER cho bookingId với D1, không gửi cho NO_SHOW, không backfill, không nhắc lần hai. Cần chốt bỏ lần nhắc sau 5 ngày ở research 070, hoặc duyệt một lịch và identity riêng cho lần nhắc thứ hai. Route review/write hiện có, destination tiếp tục kiểm tra eligibility.

## Routing còn thiếu (T013/T035)

Đề xuất xác nhận từng đích ở contract nguồn: BOOKING_NEW mở chi tiết đơn phía OWNER đúng venue hiện tại; REVIEW_NEW mở đúng reviewId tại venueId của OWNER; OWNER_APPROVED/REJECTED mở đúng applicationId và purpose của hồ sơ lịch sử; PROMOTION cần đích phía PLAYER; OWNER_REVOKED giữ saved detail. Hiện chưa có đủ đích/đối số và quyền đọc tương ứng để nối an toàn. Không tái dùng màn sửa voucher của OWNER hoặc màn hồ sơ gần nhất.

B1 vẫn chỉ có helper `prepareBookingNew` cần được gọi trong transaction payment thực tế trước writes, với trạng thái PENDING dự kiến và inbox ID hiện có nếu caller đã cấp. Checkout không có producer HOLD→PENDING. T018 tiếp tục mở.

## Chi tiết triển khai D4

Nhánh snapshot dùng 100 UID mỗi trang. Cursor và inbox/job của trang được commit trong cùng Firestore transaction. Worker D4 không thực hiện I/O gửi push bên trong transaction nên hai worker đồng thời được phân xử bằng conflict trên cursor, và không cần lease ngoài transaction cho bước materialize này. D1 tiếp tục quản lý lease và receipt gửi FCM. Transaction nguồn vẫn chịu giới hạn Firestore cho chính các ghi nghiệp vụ. Outbox giảm số ghi thông báo, nhưng không chia nhỏ các ghi nghiệp vụ vốn nguyên tử.
