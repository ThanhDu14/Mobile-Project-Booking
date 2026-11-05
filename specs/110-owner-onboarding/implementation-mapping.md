# Feature 110 implementation mapping

This map is based on `spec.md`, `tasks.md`, the three supplied UI boards, the Feature 111 boundary, and the current Android/Firebase/Supabase code. It records implementation status without resolving the open security/schema decisions in the boundary contract.

| Story / requirements | UI reference → Android destination | Repository / API boundary | Test coverage and status |
|---|---|---|---|
| US1 submit; FR-001–006, FR-013–017, FR-019 | `OwnerApply` → `fragment_owner_application_form.xml`; `OwnerApplyReview` → `fragment_owner_application_review.xml`. Only the validator/model is implemented. | Needs owner application repository and upload capability. Android → Supabase auth/token, upload authorization, submit, and idempotency are explicitly unresolved in T001–T004. | `OwnerApplicationValidationTest`: required fields, coordinates, time, venue/representative phone, photo/document presence, evidence MIME/size. Submit/retry/security tests remain blocked by boundary decisions. |
| US2 status; FR-007–009, FR-012–015, FR-018 | `OwnerPending` → `fragment_owner_application_status.xml`. Pending board exists. The supplied UI folder has no approved/rejected boards; implement those only from the documented status requirements and existing SmashNow resources after the status flow is agreed. | Account feature currently reads a latest application summary, but Feature 110 cannot treat that query as authoritative for all history/revoke cases until T001 confirms current-application selection. `role`/`ownerStatus` remain server-owned. | Existing account section resolver tests cover account summary behavior; dedicated Feature 110 status/UI/download tests remain open. No signed-download contract exists for the applicant flow. |
| US3 resubmit; FR-004, FR-006, FR-010–011, FR-013–019 | Reuse `OwnerApply` and `OwnerApplyReview`, prefilled from the rejected application. | Must refresh authoritative status, choose the approved history/document strategy, and submit through an idempotent trusted boundary. | Rejection/resubmit/concurrency tests are blocked by T001/T003; no client write is implemented. |
| Approved owner initial venue / handoff | Not a fourth onboarding screen. On approval, Feature 120 owns venue creation from `venueDraft`; Feature 111 owns post-approval venue setup and management. | Existing Feature 111 Firestore Rules allow only approved-owner operations on an existing venue and deny client venue creation. No Feature 110 code grants OWNER or creates a venue. | Feature 111 authorization emulator suite covers pending/foreign owner denial. Feature 110 approval→venue creation integration requires Feature 120's trusted implementation. |

## UI boards inspected

1. `ui/11_court-owner_buoc1_bieu-mau-dang-ky.png` — long Vietnamese form, map pin, court count/opening times, representative, venue photos, private evidence upload progress/errors, offline draft hint, and fixed `Xem lại` action.
2. `ui/11_court-owner_buoc2_xem-lai-va-nop.png` — review cards for venue, representative, photos/documents; per-section `Sửa`; pending immutability note; fixed `Nộp hồ sơ` action.
3. `ui/11_court-owner_buoc3_cho-duyet.png` — amber pending banner, three-step review timeline, read-only submitted-information card, and `Tiếp tục dùng app như người chơi` action.

## Decisions required before remaining end-to-end work

- T001/T003: current application and history selection, rejected resubmission identity, retry/idempotency and stale-write behavior.
- T002: Firebase token refresh/authentication and app-to-Supabase call/error/retry contract; applicant upload and signed-download authorization.
- T004: incomplete upload, draft recovery, and account deletion cleanup ownership/retention.
- T005: record rationale for any remaining dependency or SDK choice before adding one.
- Feature 120: confirm trusted approval writes OWNER/APPROVED and creates the initial venue from the draft idempotently.

No collection, field, enum, index, callable endpoint, or client role write is introduced by the current implementation.
