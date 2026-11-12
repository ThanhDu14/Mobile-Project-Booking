# Handoff: operational event delivery and idempotency

**Feature owner:** Feature 100.  
**Feature 112 producer needs:** booking-new/decision and drop-in session lifecycle events after the owning transaction commits.

## Current contract gap

### Chốt envelope tích hợp Feature 080 — DROPIN_REGISTERED

Giữ nguyên request của C tới `POST /functions/v1/send-notification` bằng service role:

```json
{ "type": "DROPIN_REGISTERED", "userId": "<uid người đăng ký>", "data": { "sessionId": "<sessionId>" } }
```

`userId` là người nhận; `data.sessionId` là tham chiếu buổi chơi. Không đổi thành `recipientId`, không đưa `sessionId` lên cấp gốc, không gửi QR/token. Producer gọi sau khi transaction đăng ký commit; không gửi khi rollback. Đây là envelope đã chốt riêng cho `DROPIN_REGISTERED`; retry/deduplication và các event khác vẫn là phần hợp đồng còn thiếu bên dưới. Repo hiện chưa có implementation `send-notification`, nên chưa xác nhận được việc giao thông báo end-to-end.

Feature 100 identifies notification event types/recipients and the `notifications` collection/FCM transport. Apart from the `DROPIN_REGISTERED` envelope above, event transport envelope, event ID, producer API, retry and idempotency are not closed. Feature 112 must not write notification documents directly or claim delivery by merely committing an operation.

## Contract request

Define how transaction owners enqueue after-commit events: approved function/API or outbox path; existing event type names; minimum payload and privacy boundaries; deterministic event identifier/deduplication key; retry/backoff and poison-event behavior; whether delivery status is observable. The payload should reference an entity ID and minimal display metadata; do not include QR/token, payment secret or unrelated personal fields.

Tests must prove no notification for rolled-back writes, one logical notification after duplicate/retry command, recipient isolation, retry after transport failure, and safe handling of stale/removed recipients.

## Receiving files and integration

Update Feature 100 contracts, event sender/worker and tests. Identify which booking/drop-in feature owns the after-commit call and the exact integration endpoint. Feature 112 will invoke only the agreed producer adapter.

## Ready-to-send implementation prompt

```text
Resolve the attached Feature 112 notification handoff. Close Feature 100's producer contract for booking/drop-in owner operations: event types, owner function/API, after-commit/outbox boundary, minimal payload, deterministic event ID, retry/idempotency and delivery status. Keep QR and sensitive payment/player data out of notification payloads. Add tests for rollback, duplicate command/retry, recipient authorization and transport retry. Update only Feature 100-owned contract/code/tests and report exact test commands/results; do not have Feature 112 write notifications directly.
```
