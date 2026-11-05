# Feature 110 implementation mapping

This map is based on `spec.md`, `tasks.md`, the three supplied UI boards, the Feature 111 boundary, and the current Android/Firebase/Supabase code. T001–T005 are approved and the API is specified in `contracts/owner-application-api.md`; the map provider is deferred.

| Story / requirements | UI reference → Android destination | Repository / API boundary | Test coverage and status |
|---|---|---|---|
| US1 submit; FR-001–006, FR-013–017, FR-019 | `OwnerApply` → form; `OwnerApplyReview` → review. Form uses Material time picker, per-file upload state/retry, server-backed submit; map-provider-dependent pin interaction remains open. | Android uses Firebase-token-authenticated `owner-applications` and `owner-docs` functions; submit uses server Firestore transaction. | Validator, ViewModel, retry/idempotency, accessibility tests pass; Android screen-inflation tests compile, but device execution did not start because emulator was OFFLINE. Firestore Rules emulator suite was 16 files/147 tests on prior run. Real Supabase local Storage suite is authored but not run; Docker daemon is stopped. |
| US2 status; FR-007–009, FR-012–015, FR-018 | `OwnerPending` → status screen. Status screen covers all authoritative states and exposes applicant document view/download. Supplied board depicts PENDING; other states follow spec. | Server returns authoritative `users.ownerStatus`, role and latest application; rejects mismatched role/status. Applicant document access requests a fresh signed URL for referenced evidence only. | ViewModel tests cover four statuses, current role, stale refresh and signed URL delegation. Android layout smoke test compiles; instrumentation execution and Storage integration remain open. |
| US3 resubmit; FR-004, FR-006, FR-010–011, FR-013–019 | Form/review reuses rejected application content; rejected ID remains the lifecycle ID. Local draft is restored for NONE, with a new ID if the stored ID already exists on the server. | Server handler reuses the same ID through a Firestore transaction; stale state conflicts and local draft are retained. | ViewModel tests cover same-ID resubmission, competing transition to PENDING, duplicate submit, upload retry and ambiguous submit response. Auth/Firestore Edge Function emulator tests pass 9/9; Storage integration remains open. |
| Approved owner initial venue / handoff | No fourth screen. Feature 120 creates venue from draft; Feature 111 owns post-approval setup/management. | Feature 110 never grants OWNER or writes venues. Approved status refreshes claim before navigating to Feature 111. | Approval integration remains dependent on Feature 120's trusted review implementation and recovery. |

## UI boards inspected

1. `ui/11_court-owner_buoc1_bieu-mau-dang-ky.png` — long Vietnamese form, map pin, court count/opening times, representative, venue photos, private evidence upload progress/errors, offline draft hint, and fixed `Xem lại` action.
2. `ui/11_court-owner_buoc2_xem-lai-va-nop.png` — review cards for venue, representative, photos/documents; per-section `Sửa`; pending immutability note; fixed `Nộp hồ sơ` action.
3. `ui/11_court-owner_buoc3_cho-duyet.png` — amber pending banner, three-step review timeline, read-only submitted-information card, and `Tiếp tục dùng app như người chơi` action.

## Remaining integration blockers

- Feature 120/100 must implement the approved photo-promotion → Firestore transaction → Firebase claim → notification order and make post-transaction operations retry-safe. Feature 120 currently marks review recovery as unresolved and has no review Edge Function.
- Map provider remains deferred. The form collects coordinates manually but cannot yet provide the reference's interactive pin map.
- Docker Desktop service is stopped and could not be started in this session; Supabase local signed upload/download, expiration, bucket MIME/size and authorization tests remain unverified. A real integration suite is at `supabase/tests/owner-docs-storage.integration.test.ts`; its previous Deno type-check passed before the latest handler contract assertion.
- Latest focused Android owner validation/ViewModel/accessibility unit tests pass; Android screen smoke-test sources compile, but connected test execution stopped because the emulator reported OFFLINE. Full unit tests report 506 tests, 7 failures and 2 skipped; the seven failures are in unchanged `FirebaseSessionManagerTest`. Lint reports one error in unchanged Feature 040 `SlotMatrixRowAdapter.kt` plus warnings.
- Feature 120 review/promotion/initial venue creation and Feature 100 notification remain cross-feature implementation dependencies. Feature 011 account deletion and the 30-day orphan sweeper remain outside Feature 110.

No collection, field, enum, index, callable endpoint, or client role write is introduced by the current implementation.
