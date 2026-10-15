# Contract: Nguồn sự kiện và tích hợp Notification

## Nguyên tắc

- Feature nguồn giữ trách nhiệm nghiệp vụ; không ghi notification inbox trực tiếp từ client.
- Nhóm D sở hữu pipeline: nhận sự kiện, xác định recipient đã thỏa thuận, tạo `users/{uid}/notifications/{id}` rồi đánh giá push.
- Event names, transport, payload schema, event identifier và retry envelope chưa được thống nhất. Bảng dưới là mapping nghiệp vụ, không phải API contract hoàn chỉnh.
- Producer không phát thông báo cho thao tác tự thực hiện (tự hủy booking/drop-in, tự vào waitlist). Server vẫn phải xác minh recipient, không tin recipient do client cung cấp.

## Mapping type và recipient

| Type | Sự kiện/điều kiện | Recipient | Nguồn/tích hợp |
|---|---|---|---|
| `BOOKING_CONFIRMED` | Booking sang CONFIRMED | Người đặt theo booking flow | 040/060/112 |
| `BOOKING_REJECTED` | Owner từ chối hoặc server timeout; body nêu lý do và refund amount | Người đặt | 050/060/112 |
| `BOOKING_NEW` | Booking sang PENDING; gửi ngay, kèm `approvalDeadline` | Chủ cơ sở | 050/112; không cho tắt preference |
| `BOOKING_CANCELLED` | Owner/system hủy, không phải user tự hủy | Người đặt | 040/060/011/112 |
| `BOOKING_REMINDER` | Một lần, 2 giờ trước `startAt` sớm nhất của booking CONFIRMED | Người đặt | 040/060; `booking-reminders` pg_cron |
| `DROPIN_REGISTERED` | Đăng ký thành công | Người đăng ký | 080/112 |
| `DROPIN_SPOT_OPENED` | Waitlisted registration được chuyển thành đăng ký | Người vừa được nhận chỗ | 081; nếu bị rời waitlist buổi trùng giờ, thể hiện trong cùng thông báo theo spec 081 |
| `DROPIN_CHANGED` | Buổi đổi giờ/hủy | Chỉ người đã đăng ký; không waitlisted | 080/112 |
| `DROPIN_WAITLIST_CLOSED` | Waitlist đóng trước giờ chơi mà chưa được nhận, hoặc session bị hủy | Người còn WAITLISTED | 081 |
| `PROMOTION` | Voucher sân theo dõi phát hành | Follower đủ điều kiện dùng voucher theo Feature 05 | 050/070/112; yêu thích=theo dõi |
| `OWNER_APPROVED`, `OWNER_REJECTED` | Admin quyết định hồ sơ | Người nộp hồ sơ | 110/120 |
| `OWNER_REVOKED` | Admin thu hồi quyền, kèm lý do | Owner bị thu hồi | 011/120 |
| `REVIEW_REPLIED` | Owner phản hồi review | Người viết review | 070 |
| `REVIEW_NEW` | Review mới | Chủ cơ sở | 070 |
| `REVIEW_REMINDER` | Booking sang COMPLETED | Người đặt đủ điều kiện review theo 070 | 060/070; thời điểm/one-time theo 070 |
| `GROUP_ACTIVITY` | Join request, quyết định, remove, event create/edit/delete | Thành viên liên quan theo spec 090 | 090; `data.event` nêu subtype, subtype schema chưa chốt |
| `CHAT_MESSAGE` | Tin mới trong chat | Thành viên nhận, không gồm sender | 091; inbox luôn tạo; push suppress khi recipient đang xem đúng chat |
| `SYSTEM` | Admin gửi hệ thống | Tất cả user account active (PLAYER và OWNER) | 120; active predicate/fan-out chưa định nghĩa đủ |

## Push delivery rules

1. Inbox persistence độc lập với kết quả FCM.
2. Push xét preference theo type, ngoại lệ `BOOKING_NEW`, quyền OS, token hợp lệ và suppression khi đang xem đúng chat. OS có thể chặn hiển thị dù preference bật.
3. Tap chỉ chuyển identifier đã biết; app tải lại nguồn và kiểm tra quyền hiện tại. Nếu nguồn mất/không được phép, hiển thị chi tiết từ title/body đã lưu.
4. Không đổi state machine, owner nghiệp vụ hay kết quả của feature nguồn để phục vụ notification.
5. Không có event envelope, function request/response, retry schedule, idempotency key hoặc dead-letter schema nào được chốt ở đây. D và từng producer phải đồng thuận trước implementation.
