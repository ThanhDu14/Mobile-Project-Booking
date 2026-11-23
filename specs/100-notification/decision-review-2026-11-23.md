# Feature 100 decision review, 23/11/2026

This review uses current repository code/contracts. No schema, token policy, event protocol or source permission is approved merely because it is proposed here.

## Decisions confirmed in this continuation

| Task/part | Evidence | Result |
|---|---|---|
| T012 cache ownership and behavior | User explicitly approved UID cache, server authority, read-only fallback, no offline write queue and logout/deletion cleanup | Implement with existing session DataStore and preference transaction; verification determines closure of T039 |
| T013/T035 group routes only | User explicitly approved the Feature 090 catalog mapping presented during this turn | Group/source/moderator SERVER checks; deleted/unknown events use stored detail; producer fan-out/transport remains pending |
| T011 logout portion | 011 research R1/R5, contracts/ui-screens.md integration section and security-rules P15; core/session/FcmSignOutHook.kt and FirebaseSessionManager.kt | Reuse the existing hook and arrayRemove; no competing logout hook or device schema |
| T009 SYSTEM selection | 120 admin-repairs.md Broadcast contract and current handler | Already closed; ACTIVE/non-deleting, stable requestId/resumable fanout remains the confirmed inbox behavior |

## Token lifecycle audit: T011/T051/T052 stay partial

Feature 011 assigns registration to Feature 100 and logout to core FcmSignOutHook. The hook gets the current token, removes exactly that token with arrayRemove while auth is valid, then calls deleteToken; ordinary errors are ignored so logout can continue. The session manager allows five seconds for the whole hook, while individual Firebase awaits use their shared timeout. Cancellation/timeout can interrupt the sequence, so the code does not establish a guaranteed completed rotation on every logout. ACCOUNT_DELETED bypasses authenticated hooks because credentials may already be dead; the server clears fcmTokens and deletes the profile, and local session cleanup still runs.

There is no FirebaseMessagingService.onNewToken registration implementation in app main source. fcmTokens is an existing array, with no approved installation binding, local raw-token persistence or maximum size. Rules currently permit own-profile fcmTokens changes through the shared editable-fields path; Feature 011 explicitly reserves detailed validation for 100 and only confirms arrayRemove in P15. Passing P15 is not evidence that registration/refresh/account-switch lifecycle or strict token shape is settled.

| Decision needed | Concrete recommendation | Alternative / tradeoff | Opens |
|---|---|---|---|
| Registration/refresh ownership | A Feature 100 registration coordinator uses the signed-in session, current SDK token and approved eligibility rules; preserve all other device tokens | Defer registration until producer/delivery schema and lifecycle ownership are reviewed together | T011/T051 registration portion |
| Account switch after failed detach/rotation | Keep inbox/login usable but do not attach an unconfirmed token to the next UID until the detach/rotation handoff is acknowledged; decide a server-supported ownership handoff if stronger guarantees are required | Best effort under existing array only, explicitly accepting a stale-token window; it cannot promise immediate push isolation | T011/T051 switch portion; may need a new server/DB contract, which is not approved |
| OS denial and regrant | Decide whether denial suspends registration or only OS display, and when a grant rechecks/registers the current token | Keeping tokens registered allows data-only delivery but needs an approved payload/suppression policy; T050 itself settles neither choice | T011/T051 and T053 |
| Raw token persistence / cardinality | Keep credentials outside session_prefs as Feature 011 specifies; approve either a bound or durable receipt initialization strategy before expanding token handling | A persistent installation record or raw-token journal is a schema/privacy decision, not a necessary inference from arrayRemove | T011/T052 and durable initialization |
| Rules write scope | Approve list/string validation and exact add/remove mutation constraints together with lifecycle semantics; preserve P15 own-user removal and deny other-user changes | A blanket own-user array write remains broad; do not silently impose a maximum/format that rejects existing clients | T052 |

No cross-UID token query, device collection, local token journal, background registration service or Rules change is implemented by this review. Invalid-token removal on provider UNREGISTERED belongs to the unapproved server pipeline. Client deleteToken being attempted is not proof of immediate provider invalidation or device display suppression.

## Remaining source-routing contracts

| Type | Current identifiers/destination | Remaining decision |
|---|---|---|
| BOOKING_NEW | Requires an owner-facing pending booking; existing owner queue/check-in destinations lack a target booking URI | Agree bookingId payload, owner detail destination and current venue ownership/role checks; do not open player booking detail |
| PROMOTION | Existing voucher forms are owner editing screens, not a confirmed recipient redemption target | Agree voucher/publication identity and player destination/eligibility; do not infer a voucherId or route to owner form |
| REVIEW_NEW | Producer has reviewId/venueId, owner reviews URI has no targeting args | Approve a venue/review target contract with current venue ownership; retain saved detail until then |
| OWNER_APPROVED / OWNER_REJECTED | Producer has applicationId. Status Fragment supports an ID from additional-venue list by initializing VENUE_ADDITION. Generic URI has no target ID; STATUS without purpose returns latest onboarding | Recommended: explicit purpose-aware read-only result target with applicant ownership, historical submission/version handling and no implicit app-mode switch. Existing additional-venue navigation alone is insufficient for all onboarding notifications |
| OWNER_REVOKED | Producer carries reason only | Use saved detail unless a specific account/revocation destination and lifecycle behavior is agreed |
| SYSTEM | Saved title/body | Deliberate detail view, no missing source identifier |

REVIEW_REPLIED is canonical with the previously approved REVIEW_REPLY read/routing alias. That approval does not define a legacy backend event transport. GROUP_ACTIVITY routing approval also does not confirm callback fan-out or durable event identity.

## Backend approvals still required

1. Review the parent job, per-token receipts and initialization/deletion amendments in delivery-state-proposal.md; update authoritative CSDL and server-only access contracts only after approval. This unlocks relevant portions of T006/T020/T025, not every producer adapter automatically.
2. Agree each producer's stable event identity and enqueue read/write set. Existing booking/waitlist/admin transactional inbox writers differ from review/group/chat post-commit callbacks. Repeatable edits, resubmitted applications and delete/recreate review need an agreed incarnation/version identity. This unlocks T004/T005/T017 and source adapters incrementally.
3. Resolve T007's Admin C versus server-only creation conflict with document owners; T009's existing fanout decision does not resolve it. Confirm T008's multi-device chat presence separately from inbox persistence.
4. Approve T014 generic lock-screen text plus authenticated inbox identifier (recommended), or explicitly permit title/body display. Payload, channel handling and token registration must use the same agreement before closing T053.

For the cache and group routing approved here, no shared Firestore schema or source feature permission changes are needed. For the unresolved items, keep their tasks open and continue independent validation.


## Quyết định 23/11/2026 — backend/FCM

Người dùng đã duyệt D1, D2-W1, T1, C1, P1 và S1 trong [backend-decision-package.md](backend-decision-package.md). Schema/giới hạn chính xác được ghi tại tài liệu đó và docs/design/database/README.md. Phần đề xuất cũ xung đột được thay bằng các quyết định này, đặc biệt: không tạo lại job cho inbox có sẵn sau retention; không backfill push. Các producer ngoài W1/source outbox/generation vẫn chưa duyệt. Đây là phê duyệt contract, chưa phải bằng chứng triển khai hay deploy.
