# Admin repair interfaces (2026-11-12)

These interfaces implement the explicitly requested repairs. They use the existing collections and fields; no new business collection/state is introduced. Historical “all gates closed” notes in tasks/quickstart are not acceptance evidence.

| Boundary | Current interface / invariant |
|---|---|
| Authorization | All seven admin handlers require a verified ADMIN token **and** a current ACTIVE ADMIN user document; deleted/revoked/locked accounts fail closed. |
| Queue reads | Applications query one exact status, submittedAt then document ID, in bounded windows of 30. PENDING is oldest first per valid wireframe 2; history newest first. Reports query OPEN or both RESOLVED/DISMISSED, createdAt then ID. Loading another page extends the live window; the previous listener is cancelled. Composite indexes are supplied for review, not deployed. |
| Review | `admin-review-application`: applicationId, decision APPROVE/REJECT, expectedSubmittedAt (ISO instant, required for PENDING), optional trimmed rejectReason. Compare the current submission version inside the transaction. Responses use APPROVED/REJECTED. A matching retry by the reviewing admin creates no additional venue/notification. |
| Claim recovery | Retry always reconciles Auth from the **current** user role, including after revocation. Auth failure returns 503 CLAIM_SYNC_PENDING. `action=RECOVER_CLAIMS` on the same endpoint lets any current admin repair an already decided application's current rights after reopening it; it does not decide PENDING applications or repeat effects. UI exposes this on history details. |
| Evidence | Every document click invokes owner-docs SIGN_DOWNLOAD with the exact application/path, including the second document. Only signed access is launched, fetched anew on retry; raw storage paths are never fallback URLs. Existing TTL remains 300 seconds. |
| Categories | The selected kind is carried with each row/form. Districts always use districts; amenities always use amenities, including edits after changing tabs. Merge preserves fields outside the form. Venue counts are not derived from sortOrder. |
| Vouchers | CREATE/UPDATE/TOGGLE_ACTIVE in admin-manage-voucher. Validate safe integer amounts/limits, strict full dates including timezone and calendar validity. UPDATE preserves usage counters/redemptions; usageLimit cannot be below usedCount. Codes remain immutable on edit. Canonical type is PERCENT/FIXED; legacy PERCENTAGE input is normalized. Both system and venue creation use the same transaction read set for global uniqueness; the minimal owner-create-voucher change is necessary to close the concurrent writer gap. |
| Broadcast | requestId is required (16–80 alphanumeric/hyphen characters), retained by UI for retry of the same content. Notification ID is derived from admin UID + requestId, not time/randomness. Existing matching notifications are retained, including isRead. A mismatching payload conflicts. Batch retry resumes missing notifications. Recipients are explicitly ACTIVE and not deleting in the authoritative user scan; list follows all REST page tokens and preserves Firestore document IDs. |
| Users | admin-manage-user LIST accepts optional query, filter ALL/OWNERS/LOCKED, pageToken; returns sanitized users and nextPageToken. Search matches existing name/email/phone/UID fields; bounded scans use continuation tokens without adding a search-index schema. LOOKUP remains supported. Email and actual document identity are returned. |
| Stats | Read exactly systemStats/yyyyMMdd for the selected date (Asia/Ho_Chi_Minh), server source only. Show existing metrics, source document and supplied updatedAt. Missing fields use an unavailable marker, never zero/sample history. Totals are labelled as totals; undefined daily/new-user/drop-in metrics are not inferred. |

## Remaining design evidence boundary

`ui/12_admin_buoc8_chi-tiet-nguoi-dung.png` is an ERR_CONNECTION_REFUSED page. It is **not** a user-detail design. The existing list/action flow can show schema-backed account data in a standard dialog, but no visual compliance with step 8 is asserted. An actual artboard is still needed for a dedicated detail screen.

## Quyết định mở thêm cơ sở — 2026-11-20

Hồ sơ bổ sung dùng purpose VENUE_ADDITION, độc lập với quyền OWNER/APPROVED. Hồ sơ cũ thiếu purpose thuộc OWNER_ONBOARDING. Duyệt tạo venues/{applicationId} một lần; từ chối/nộp lại không đổi tài khoản hoặc venue cũ. Không giới hạn số venue/hồ sơ.

Hợp đồng đầy đủ: [owner-application-api.md](../../110-owner-onboarding/contracts/owner-application-api.md).


## Feature 100 — phê duyệt 23/11/2026

D2-W1/S1: SYSTEM qua server ADMIN claim + ACTIVE ADMIN profile, scan recipient ACTIVE/không deleting giữ nguyên. Tối đa100 recipient/source transaction, re-read profile, inbox và job. Inbox tồn tại khớp giữ isRead/createdAt và không backfill job cũ. Kết quả hồ sơ enqueue theo applicationId/submittedAt/decision cùng transaction, không đổi claim recovery.

Nguồn: [gói quyết định đã duyệt](../../100-notification/backend-decision-package.md). Chỉ áp dụng phạm vi ID được duyệt; chưa deploy.

O2 duyệt 24/11/2026. `admin-revoke-owner` nhận UUID `requestId` cùng `uid` và `reason`. Transaction kiểm tra ADMIN hiện tại và journal `users/{uid}/ownerRevocationCommands/{requestId}` trước khi đổi role. Journal chỉ server đọc/ghi, lưu actorUid, reasonHash, createdAt và được dọn khi xóa tài khoản. Cùng actor/lý do trả thành công khi replay, khác actor/lý do trả 409. Role, journal, inbox và delivery job cùng transaction. Claims reconcile theo profile hiện tại kể cả khi replay.

Android giữ UUID của yêu cầu lỗi trong vòng đời ViewModel; app và Edge Function cần được đồng bộ khi phát hành vì các caller cũ chưa gửi requestId.
