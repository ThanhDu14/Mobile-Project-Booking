# Feature 110 implementation mapping

This map is based on `spec.md`, `tasks.md`, the three supplied UI boards, the Feature 111 boundary, and the current Android/Firebase/Supabase code. T001–T005 are approved and the API is specified in `contracts/owner-application-api.md`; the map provider is deferred.

| Story / requirements | UI reference → Android destination | Repository / API boundary | Test coverage and status |
|---|---|---|---|
| US1 submit; FR-001–006, FR-013–017, FR-019 | `OwnerApply` → form; `OwnerApplyReview` → review. Validator, form, repository and Edge Function submit path implemented; interactive map remains deferred. | Android uses Firebase-token-authenticated `owner-applications` and `owner-docs` functions; submit uses server Firestore transaction. | Validator/accessibility tests pass. Edge Function Deno + Auth/Firestore Emulator tests pass (8/8); full Firestore Rules suite passes (16 files/147 tests). Supabase Storage HTTP is mocked in handler tests; real local Storage verification remains open. |
| US2 status; FR-007–009, FR-012–015, FR-018 | `OwnerPending` → status screen. Status shell and approved/rejected actions exist; sample boards only show pending. | Server returns authoritative `users.ownerStatus` and latest application; applicant may read own application through read-only Rules. | No dedicated status UI/ViewModel test yet. Signed document URL endpoint and client repository exist; storage integration test is not configured. |
| US3 resubmit; FR-004, FR-006, FR-010–011, FR-013–019 | Form/review shell exists; rejected data prefilling and resubmit recovery need verification against the full flow. | Server handler reuses the same ID through a Firestore transaction; stale state conflicts and local draft is retained. | Handler test cases are authored for same-document resubmission/concurrency but unrun (Deno unavailable); no Android ViewModel/UI integration tests yet. |
| Approved owner initial venue / handoff | No fourth screen. Feature 120 creates venue from draft; Feature 111 owns post-approval setup/management. | Feature 110 never grants OWNER or writes venues. Approved status refreshes claim before navigating to Feature 111. | Approval integration remains dependent on Feature 120's trusted review implementation and recovery. |

## UI boards inspected

1. `ui/11_court-owner_buoc1_bieu-mau-dang-ky.png` — long Vietnamese form, map pin, court count/opening times, representative, venue photos, private evidence upload progress/errors, offline draft hint, and fixed `Xem lại` action.
2. `ui/11_court-owner_buoc2_xem-lai-va-nop.png` — review cards for venue, representative, photos/documents; per-section `Sửa`; pending immutability note; fixed `Nộp hồ sơ` action.
3. `ui/11_court-owner_buoc3_cho-duyet.png` — amber pending banner, three-step review timeline, read-only submitted-information card, and `Tiếp tục dùng app như người chơi` action.

## Remaining integration blockers

- Feature 120/100 must implement the approved photo-promotion → Firestore transaction → Firebase claim → notification order and make post-transaction operations retry-safe. Feature 120 currently marks review recovery as unresolved and has no review Edge Function.
- Map provider remains deferred. The form collects coordinates manually but cannot yet provide the reference's interactive pin map.
- Supabase local credentials and a runnable local Storage stack plus Deno are required to verify signed upload/download and cleanup end-to-end.
- Android full unit suite reports seven failures in unchanged `FirebaseSessionManagerTest`; Feature 110 validator/accessibility focused tests pass. Lint reports one error in unchanged Feature 040 `SlotMatrixRowAdapter.kt` and 348 warnings.

No collection, field, enum, index, callable endpoint, or client role write is introduced by the current implementation.
