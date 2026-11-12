# Handoff: drop-in owner commands, registration and roster

**Feature owners:** Features 080/081 and database owner.  
**Feature 112 consumer:** create/edit/cancel a session, read an owner-safe roster, check in a registration once, and show capacity/waitlist state.

## Problem and current source

- Database README §4.9 lists the deployed collection/model; Feature 080/081 contracts describe additional registration/waitlist states such as `SESSION_CANCELLED`, `CLOSED`, `PROMOTED_GRACE` and fields not listed in §4.9.
- Feature 080/081 own player registration/cancellation, waitlist promotion and shared drop-in transaction helpers. Feature 112 must not recreate those algorithms or let an owner edit `registeredCount`/queue state.
- Current Feature 112 checkout has no owner session commands, roster repository, QR check-in API or Edge Function test harness.
- Feature 112 validation currently checks positive capacity, nonnegative VND price, and capacity not below server-supplied registered count. This is UX validation only.

## Contract request

### Owner QR/mã vé check-in — tích hợp schema 080

`POST /functions/v1/owner-checkin-dropin-registration` dùng Firebase ID token và body:

```json
{ "venueId": "<venueId>", "sessionId": "<sessionId>", "credential": "<qrToken hoặc ticketCode>" }
```

QR của app 080 chứa trực tiếp `qrToken` (43 ký tự base64url từ `newQrToken()`); nhập tay dùng `ticketCode` dạng `DI-XXXXXXXX` từ `newTicketCode()`. Server trim khoảng trắng; mã vé không phân biệt hoa/thường, token QR giữ nguyên hoa/thường. Không chấp nhận UID làm credential.

Sau khi xác minh owner được duyệt, quyền sở hữu venue và quan hệ session/venue, server tìm đúng một đăng ký theo `qrToken` hoặc `ticketCode` trong `dropInSessions/{sessionId}/registrations`. Đọc trạng thái và chuyển `REGISTERED` → `CHECKED_IN` trong cùng transaction; từ chối mã sai, khác buổi, đã dùng, đã hủy, đang chờ hoặc buổi không còn hoạt động. Response thành công gồm `success`, `registrationId` (UID tìm được), `sessionId`, `status`, `checkedInAt`; không trả QR/token/mã vé. Không thay đổi việc phát hành vé của C.

### Authoritative data model

Please reconcile README §4.9 with 080/081 contracts, implementations, fixtures and tests. Confirm session status, registration status, waitlist order/fields, cancellation/refund behavior and capacity occupancy definition. Session fields should reuse existing schema: owner/venue/court IDs, date and times, capacity, server-managed registered count, price and status. Do not add skill/note fields unless the schema owner approves them.

### Owner commands and safe responses

Proposed commands use verified UID/claims and a venue/session/court identifier; no `ownerId`, count, QR token or amount is trusted from client.

- Create/update/cancel validates owner APPROVED status, venue ownership and status, active court membership, venue open hours, 30-minute boundary, current session state and all target slots in one transaction.
- A successful create/update blocks/unblocks the exact existing slot IDs with the existing `DROP_IN` reason; any unavailable/HOLD/BOOKED/BLOCKED slot aborts all writes.
- Edit specifies allowed mutable fields and how already-registered people retain price and old court/time; preserve the clarified 100% refund and queue-close behavior on cancellation.
- Capacity increase calls the existing waitlist promotion helper in the same transaction and preserves queue order/grace window. Owner never promotes manually or writes server counters.
- Roster response contains only required display data and status (`REGISTERED`, `WAITLISTED`, cancelled, checked-in); never returns QR secret/token.
- Check-in accepts the existing QR/token representation only on the server, binds it to the correct active registration/session and owner venue, changes the existing status once, and rejects replay/cancelled/wrong-session tokens.

Confirm exact request/response types and function owner/path before Feature 112 binds an adapter. No new collection, field or endpoint name is presumed by this handoff.

## Concurrency, errors and acceptance tests

- Transaction conflict on any one slot leaves no session or partial slot write.
- Two simultaneous creates for overlapping slots yield at most one success.
- Duplicate registration, waitlist promotion, cancellation and QR check-in are idempotent or return explicit already-processed status.
- Capacity cannot fall below the authoritative occupying registrations; counter is recomputed/validated server-side.
- Deny unapproved/revoked/foreign owner, foreign venue/court, LOCKED venue, inactive court, invalid hours, invalid 30-minute alignment, full/cancelled session and replay.
- Test successful owner commands, rollback, competing writes, waitlist promotion order/grace, refunds on edit/cancel, roster isolation and QR replay using the receiving feature's real emulator harness.

## Receiving files and integration

Update `specs/080-*/`, `specs/081-*/`, the database README §4.9, shared `_shared/dropin.ts`/functions owned by 080/081 and their actual tests. Confirm command/API and run command. Feature 112 will add only `feature/owner` client adapters and UI after the contract is approved.

## Ready-to-send implementation prompt

```text
Resolve the attached Feature 112 drop-in handoff. Reconcile Database README §4.9 with Feature 080/081 contracts, current shared helpers, implementations and fixtures; establish one authoritative session/registration/waitlist model. Define owner create/update/cancel commands, safe owner roster response, and one-time QR check-in using the existing server authorization and transaction helpers. Specify exact endpoint owner/path, request/response, rollback/concurrency/idempotency behavior and test harness. Add real Emulator/backend tests for owner authorization, slot conflicts, rollback, capacity, waitlist promotion/refund and QR replay. Update only Feature 080/081/database-owner files; do not edit Feature 112 or create new schema without approval. Report exact test commands/results and the final adapter contract.
```
