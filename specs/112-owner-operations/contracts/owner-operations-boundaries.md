# Ranh giới dữ liệu và nghiệp vụ: Owner Operations

Hợp đồng hiện tại, đối chiếu code và quyết định đến 2026-11-21. Các handoff ngày 2026-11-05 ghi vấn đề lịch sử; các gate implementation đã có endpoint/DI thật. Xem implementation-mapping.md và bằng chứng local.

## Quyền và trạng thái

Mọi mutation xác thực Firebase token, đọc profile hiện tại ACTIVE, không deletionRequestedAt, OWNER/APPROVED hoặc ADMIN còn hợp lệ, và kiểm tra ownership venue trong cùng transaction. Client ownerId không cấp quyền. Owner không tự ghi venue mới, trạng thái booking, tiền, slot, counters hoặc quyền tài khoản.

Vận hành booking/drop-in/voucher yêu cầu venue ACTIVE. Cấu hình venue/court/price của 111 cho ACTIVE/HIDDEN, chặn OWNER trên LOCKED. Đọc thống kê quản lý không tự đổi trạng thái venue và không cấp quyền ghi.

## Booking và check-in

- Read booking theo venue qua Firestore Rules; chi tiết dùng Source.SERVER. Không dùng dữ liệu cache để cấp quyền mutation.
- POST owner-approve-booking: {venueId,bookingId}; PENDING còn approvalDeadline, slot vẫn BOOKED và trỏ đúng booking → CONFIRMED.
- POST owner-reject-booking / owner-cancel-booking thêm reason không rỗng. Reject chỉ PENDING còn hạn; cancel chỉ CONFIRMED chưa bắt đầu/chưa check-in. Nhả đúng slots, refund theo số tiền đã ghi, ledger chống hoàn trùng và notification trong transaction.
- POST checkin-booking: bookingId có thể là ID hoặc qrToken; preview và confirmPayment theo endpoint hiện có. Cửa sổ từ 30 phút trước giờ bắt đầu đến trước giờ kết thúc. AT_VENUE/DEPOSIT phải xác nhận khoản còn thu để ghi PAID. checkedInAt chỉ ghi một lần; không chuyển COMPLETED/NO_SHOW từ client.

## Drop-in và waitlist

- owner-create-session nhận venueId/courtIds/date yyyyMMdd/startTime/endTime HHmm/capacity/pricePerPerson/level/note. Kiểm tra giờ mở, giá bao phủ, court active, slot mới nhất; session và toàn bộ BLOCKED/DROP_IN cùng transaction.
- Retry CREATE cùng nội dung đối chiếu session và slots đang thuộc nó, trả sessionId cũ; không ghi đè session đã sửa/hủy hoặc slot bị thay đổi.
- owner-update-session thêm sessionId; capacity không thấp hơn registeredCount; tăng chỗ gọi promoteFromWaitlist của 081; giữ giá đăng ký cũ và thông báo thay đổi.
- owner-cancel-session: venueId/sessionId/reason; closeWaitlistInTx và cancelRegistrationInTx, hoàn tiền/nhả đúng slots nguyên tử. Không thay helper 081 hoặc cron dropin-close.
- D4: cả update và cancel nhận `requestId` UUID dùng lại khi retry. Transaction nguồn ghi journal theo session/requestId để trả lại kết quả trước đó hoặc từ chối khi payload khác. DROPIN_CHANGED gửi cho REGISTERED trước mutation khi đổi startAt/endAt hoặc hủy. Người thao tác, người chờ và người mới được promote trong cùng thao tác không nhận DROPIN_CHANGED. WAITLIST_CLOSED vẫn do helper 081 ghi.
- owner-checkin-dropin-registration: venueId/sessionId/credential. QR raw 43 ký tự hoặc ticket DI-XXXXXXXX theo alphabet 080; không dùng credential làm UID. Chỉ đăng ký REGISTERED của đúng session, session OPEN/FULL, trong [startAt - 30 phút, endAt), chuyển CHECKED_IN một lần. Clock chỉ server quyết định; mã lặp/sai/ngoài cửa sổ bị từ chối.
- Roster phải xác nhận session.venueId từ server trước khi đọc registrations. Không duyệt từng người đăng ký.

## Voucher

owner-create-voucher nhận action CREATE/UPDATE/SET_ACTIVE và venueId là context đường dẫn. Schema tại venues/{venueId}/vouchers/{code}; không thêm field venueId vào document. Validate tiền, phần trăm, ngày, limits và uniqueness toàn hệ thống trong transaction.

Client không ghi usedCount/redemptions. CREATE retry chỉ xác nhận lại bản giống hệt trong cùng venue, không reset counters; mã có payload khác hoặc ở venue/system khác trả conflict. UPDATE không giảm usageLimit dưới usedCount; toggle không sửa bộ đếm. Redemption/discount thuộc 050.

## Báo cáo

POST owner-statistics-source: {venueId,startDate,endDate}, ISO local dates, 1–30 ngày. Response chứa bookings/slots projection không QR hay danh tính, computedAt và availability {openTime,closeTime,courtIds,priceRules}. Availability là response DTO, không thêm schema Firestore; tất cả đọc trong cùng transaction.

Không dùng dailyStats. Tiền lấy từ PAID total, DEPOSITED depositAmount, REFUNDED refundAmount riêng; không lấy giá cấu hình làm doanh thu. Occupancy loại BLOCKED/deduplicate slot ID, numerator CONFIRMED/COMPLETED/NO_SHOW; peak bucket 60 phút Asia/Ho_Chi_Minh. Booking cũ còn là bằng chứng sau khi court tắt/giá đổi. Không có version lịch sử giờ mở/giá trong schema nên availability lịch sử dùng cấu hình hiện tại và slots còn lưu; không khẳng định tái dựng được cấu hình đã xóa.

Báo cáo quá giới hạn dữ liệu yêu cầu chọn kỳ ngắn hơn; lỗi không hiển thị thành empty hoặc số liệu mới. Ngày hiện tại ghi partial, refresh lấy cả cấu hình và dữ liệu mới.


## Feature 100 — quyết định đã duyệt 23/11/2026

D2-W1: owner-booking-command và booking-timeout giữ UUID inbox cũ, enqueue job bookingId/canonical terminal type trong cùng source transaction. Helper đọc recipient ACTIVE/không deleting trước mọi write; owner reject và timeout chia sẻ cùng identity BOOKING_REJECTED. Owner-update-session/cancel/dropin-close không gọi lại callback waitlist sau commit. Không đổi refund/state machine, không thêm BOOKING_NEW/reminder hoặc generation của DROPIN_CHANGED.

Nguồn: [backend decision package](../../100-notification/backend-decision-package.md). Chưa deploy hoặc migration dữ liệu thật.
