# Feature 110 implementation mapping

Current implementation, 2026-11-21. The production OwnerModule binds onboarding to OwnerOnboardingDataSource; form/review/status and the additional-application list use explicit purpose/application context.

| Requirement | Current boundary |
|---|---|
| Initial ownership application | OWNER_ONBOARDING; legacy missing purpose reads as onboarding. Latest account-application queries exclude additions. |
| Additional facilities | OwnerVenueListFragment opens VENUE_ADDITION form/review/status; stable draft/application ID, upload, pending/rejected/resubmit; no arbitrary count limit. |
| Account invariants | PENDING/REJECTED/resubmit of additions preserve OWNER/APPROVED, claims and previous facilities; SessionManager does not route account by newest additional application. |
| Admin review | admin-review-application transaction checks fresh admin/applicant/ownership; creates exactly venues/{applicationId}; repeat/concurrent review cannot create duplicate venues. Additional rejection only changes the application/reason. |
| Private documentation | owner-docs validates current authority and referenced paths for upload/download; signed URL is short-lived, legal documents stay private. Only venue photos are promoted to venue-photos on approval. |
| Android state/navigation | Initial/additional context and application ID retained across review/status/recreation; loading/validation/retry and draft retention use the existing flow. |
| Venue location | Inline map preview and saved-state DialogFragment picker reuse B's core OsmdroidSetup, MAPNIK tiles/cache, night mode and shuttlecock marker. Tap/drag or enter coordinates, validate Vietnam bounds, retain an unconfirmed pin through rotation, and return confirmed coordinates through FragmentResult. Loading/error/retry and OSM attribution remain visible when appropriate. |

Source contracts: contracts/owner-application-api.md and contracts/owner-onboarding-boundaries.md. Validation: [current Feature 11 evidence](../../docs/validation/feature11-2026-11-21/README.md), including both initial and additional real-phone application lifecycles on local services. Historical usability protocols are not evidence of a measured usability pass; no new AI_log_4 entry is made in this turn.
