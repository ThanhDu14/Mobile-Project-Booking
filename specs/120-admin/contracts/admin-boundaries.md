# Admin Integration Boundaries

This document records existing data/security boundaries only. It does not define new HTTP routes, request/response JSON, endpoint names, or schema. Any missing server contract below must be agreed before implementation.

## Identity and authorization

- App authenticates through Firebase Auth. App role comes from Firebase custom claim `appRole`; Firebase claim `role` is reserved for Supabase third-party authentication and is not the application role.
- Every admin read/mutation must validate current identity and `appRole == ADMIN` server-side. Client-supplied role is never authoritative. Invalid/revoked admin or expired session is denied.
- Firestore Rules enforce client access at the collection boundary. Privileged state changes/🔒 fields use the approved server path, per Constitution II and the shared database design.
- Admin accounts are provisioned manually; there is no registration or role-escalation flow.

## Data boundaries

| Operation | Existing data | Boundary |
|---|---|---|
| Review application | `ownerApplications/{id}`, `users/{uid}`, `venues/{venueId}` | Decision only from current `PENDING`; approve applies `venueDraft`, owner role/status and venue creation; reject requires non-empty reason. Re-submit belongs to Feature 110. Server rejects stale/duplicate decisions. |
| View evidence | Supabase private bucket `owner-docs` | Only applicant for that application and Admin can receive a signed URL; URL expires after five minutes. Use existing `sign-download` design after confirming admin authorization behavior. |
| Moderate report | `reports/{reportId}` plus `targetPath` | Only current `OPEN`; action must match `targetType` and existing target. Persist existing `status`, `action`, `handledBy`, `handledAt`. |
| Manage account/owner | `users/{uid}` | Admin controls `accountStatus`; owner revocation writes the specified role/status/reason and sends `OWNER_REVOKED`. Spec 010 owns sign-in effect; spec 011 displays outcome. |
| Manage venue | `venues/{venueId}.status` | Admin locks and unlocks to `ACTIVE` or `HIDDEN`; revocation does not change venue status. |
| System voucher | `systemVouchers/{code}` | Use Feature 050 schema; uniqueness is global across system and venue voucher collections; usage counters/redemptions remain server-owned. |
| Admin metrics/catalog | Existing `systemStats`, `dailyStats`, `districts`, `amenities` | Read/write boundaries are named in shared DB README, but detailed metric/category schemas and operations are not yet defined. |
| Notification | `users/{uid}/notifications/{id}` | Existing notification schema and Feature 100 delivery path; `SYSTEM` recipients are all active accounts. Admin UI does not write each recipient inbox directly. |

## Failure and concurrency semantics from the spec

- A stale application/report or a target whose state changed must not produce a second effect; return a conflict/current-state outcome for the UI to refresh.
- A permission failure must not reveal application, evidence, report target, or account details.
- A timeout leaves commit outcome unknown; app refreshes authoritative state before retrying. Client must not claim success optimistically.
- Offline/cache data can be shown only as stale context and cannot authorize a decision. Mutations and evidence access require server confirmation.
- Report dismissal uses `DISMISSED` + `NONE`; resolved action metadata must match the applied action.

## Contracts that remain unspecified

1. Exact callable interface and authentication mechanism for admin Edge Functions.
2. Atomicity/recovery across Firestore records, Firebase Auth custom claims, venue creation, and notification fan-out.
3. Idempotency key/version fields or another existing mechanism for retrying a timed-out decision; no such field is specified.
4. Field schemas and operations for `districts`, `amenities`, `systemStats`, and `dailyStats`.
5. Whether `DROP_IN` supports `HIDE_CONTENT` and, if yes, its existing hide field/state.
6. How “active account” is queried for system broadcast and how large fan-out/retry is handled without adding unapproved schema.

These are implementation blockers, not implied API design decisions.
