# Contract: Nguồn sự kiện và tích hợp Notification

## Nguyên tắc

Initial Android routing baseline (2026-11-22; superseded for reviews by the continuation below): booking result/reminder types use data.bookingId, existing booking detail, Source.SERVER read and userId == current UID; the four DROPIN types use data.sessionId and existing smashnow://dropin/session/{sessionId} after server read; CHAT_MESSAGE uses data.chatId and existing smashnow://chat/{chatId} after server read and memberIds check. Rules enforce current source permissions. Missing/forbidden source, missing identifier, account change or unavailable destination falls back to saved title/body. This does not approve a backend event envelope or schema.

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
| `SYSTEM` | Admin gửi hệ thống | Tài khoản có accountStatus ACTIVE và không deletionRequestedAt | 120 admin-repairs.md Broadcast xác nhận scan/fan-out/retry; T007 authorization và T006 durable push vẫn mở |

## Push delivery rules

### Confirmed continuation, 22/11/2026

- **T009 confirmed from Feature 120**, not an inferred predicate: `specs/120-admin/contracts/admin-repairs.md`, Broadcast row, requires an authoritative paginated users scan, explicit `accountStatus == ACTIVE`, no `deletionRequestedAt`, preserved document IDs, admin UID/requestId identity, payload-conflict detection and retry of missing inbox records without resetting isRead. `admin-broadcast-notification/handler.ts` implements that predicate and batches 300 writes; its regression tests cover 601 recipients/partial failure/retry. `notification-producers.test.ts` also excludes locked, unspecified-status and deleting fixtures. This closes recipient/fanout decision T009 only; T007 authorization conflict and T006 durable push recovery remain open.
- **Review reply name approved by user in this continuation**: emit `REVIEW_REPLIED`; accept legacy `REVIEW_REPLY` only when reading/routing. `review/handler.ts` and Feature 070 integration contract are aligned with 070 FR-026 and 100. Both names route through reviewId + venueId: SERVER reads must confirm recipient owns the review, its venue matches, status VISIBLE, reply remains present and venue exists/readable. Use the existing venue reviews URI, otherwise saved title/body. No legacy record rewrite or extra schema.
- `REVIEW_REMINDER`: Feature 070 UI contract and nav_review confirm bookingId -> `smashnow://review/write/{bookingId}`. Recheck booking SERVER existence and userId before opening. The destination enforces current review eligibility; missing/forbidden source falls back. Scheduler identity/timing remains unresolved separately.

Current routing covers eleven canonical types (plus the approved legacy alias). Remaining eight: BOOKING_NEW has no target-specific owner booking URI; PROMOTION has no agreed voucher/publication payload; REVIEW_NEW has reviewId/venueId but owner review destination accepts neither; GROUP_ACTIVITY's implemented subtype catalog still explicitly awaits D handoff confirmation in Feature 090 research R15; OWNER_APPROVED/REJECTED have applicationId but latest-application screens do not establish a notification target/mode contract; OWNER_REVOKED has reason only; SYSTEM intentionally uses saved detail. SYSTEM needs no source URI. T013/T035 remain open for missing source contracts, not for SYSTEM detail.

Proposed next routing decisions: confirm a bookingId-specific owner detail destination with current venue ownership checks; confirm applicationId-specific read-only result detail for applicant UID (including historical/revoked applications) without silently changing app mode; confirm 090's subtype catalog and requests-only-for-current-moderator routing. No new navigation argument, source permission or subtype is implemented by these proposals.

### Group routing approved by user, 23/11/2026

The user approved the Android mapping below after review of Feature 090 research R15, its UI contract and current Rules. This approval is limited to routing; transport, fan-out, retries and event identity remain undecided.

| data.event | Destination and current access check |
|---|---|
| JOIN_REQUESTED | SERVER group must exist as ACTIVE/LOCKED; SERVER members/currentUid must exist with OWNER or ADMIN role; open existing group requests URI |
| JOIN_APPROVED, JOIN_REJECTED, REMOVED | SERVER group must exist as ACTIVE/LOCKED and be readable; open existing group detail/intro URI; continued membership is not required for a public intro |
| EVENT_CREATED, EVENT_UPDATED | Require groupId and postId; SERVER group and post must exist/readable under current group/post Rules, then open group feed URI |
| EVENT_DELETED, unknown subtype, invalid identifier, deleted/forbidden source or changed account | Saved title/body detail; do not open a different event or bypass source Rules |

T013/T035 remain partial for BOOKING_NEW, PROMOTION, REVIEW_NEW and OWNER_* (SYSTEM deliberately uses saved detail). Existing OwnerApplicationStatusFragment accepts applicationId only by treating it as VENUE_ADDITION; nav_account's URI has no identifier and missing purpose means OWNER_ONBOARDING. This is not a general historical onboarding/result contract. An unqualified application URI could open the wrong submission; do not reuse it for every owner result without a purpose/target decision.

1. Inbox persistence độc lập với kết quả FCM.
2. Push xét preference theo type, ngoại lệ `BOOKING_NEW`, quyền OS, token hợp lệ và suppression khi đang xem đúng chat. OS có thể chặn hiển thị dù preference bật.
3. Tap chỉ chuyển identifier đã biết; app tải lại nguồn và kiểm tra quyền hiện tại. Nếu nguồn mất/không được phép, hiển thị chi tiết từ title/body đã lưu.
4. Không đổi state machine, owner nghiệp vụ hay kết quả của feature nguồn để phục vụ notification.
5. Không có event envelope, function request/response, retry schedule, idempotency key hoặc dead-letter schema nào được chốt ở đây. D và từng producer phải đồng thuận trước implementation.
