# Feature 120 repair validation — 2026-11-12

Latest investigation and fresh physical-device results (13/11): [PERMISSION_DENIED report](permission-denied-2026-11-13.md), covering R120-19–R120-21. JVM: 129 pass / 2 existing Windows skips; Edge: 40 pass; local integration: 3 pass; Firestore: 223 pass / 4 unchanged spec 030 failures; device: 24/24 pass, 60 new screenshots. Original production-account rights/active hosted Rules are not verified or changed.

Status: repair implementation and requested local validation recorded. The full Firestore suite still has the four pre-existing spec 030 failures; step 8 design acceptance is blocked by its invalid reference. This report supersedes historical claims of 100% completion in quickstart/tasks; reviewer checklist markers are unchanged.

## Observed regression failures before repairs

- JVM: duplicate decision submission; rejection draft/signed URL state leaking between selected applications; invalid voucher dates/negative amounts (3 failures in 60 tests).
- JVM category regression: requesting amenities called `districts` (MockK verification failure).
- JVM queue regression: selected status/page window never reached the repository.
- Edge: approve retry returned success despite Auth failure; REJECT retry compared REJECT with REJECTED; Invalid Date accepted; list stopped after one page (4 failures).
- Edge: partial broadcast retry included users without ACTIVE status and repeated effects; revoked ADMIN claim reached application lookup; email lookup lost user identity/email; PERCENT contract rejected; missing submission version still decided a PENDING application; reopening a completed application had no claim recovery action; voucher UPDATE unsupported.
- Emulator voucher race with a barrier: both system and venue creation returned 200 for the same code before shared transaction; after repair the same test returns one 200 and one 409.
- First real device run: signed downloads/approval/moderation executed against local services; Espresso failed at the clipped “Đã đóng” button (less than 90% visible). Four inflation tests passed despite this failure. Device screenshots also exposed the remaining sample “2 days”/“00:15” text and empty sample-history section; these were removed and the report header/filter layout corrected before rerun.
- R120-17: device assertion failed before the hint repair: expected “Thêm tiện ích mới”, actual TextInputLayout hint “Thêm khu vực mới”. Original overlap screenshot and the red run are retained in validation/regressions and validation/results/admin-hint-red-device.txt. The hint now belongs to TextInputLayout and is restored from the selected category.

## Repair trace

| Tasks | Regression / integration evidence |
|---|---|
| R120-01, 04, 05, 10, 11 | AdminApplicationsViewModelTest; admin-review-application/handler.test.ts; owner-docs/handler.test.ts; admin-integration.local.test.ts; actual signed-download/review device flow. |
| R120-02, 03 | AdminCategoryRoutingTest, AdminQueueRepositoryTest, category/status tests in AdminApplicationsViewModelTest and AdminToolsViewModelTest; local category edits and live APPROVED/closed queues. |
| R120-06, 15 | AdminVoucherRepairTest, SystemVoucherValidationTest; admin-manage-voucher/handler.test.ts; owner-create-voucher/handler.test.ts; local shared-transaction barrier race. |
| R120-07 | _shared/firestore-list.test.ts; admin-broadcast-notification/handler.test.ts; local >300 recipients, injected second-batch failure, repeat delivery and preserved read state. |
| R120-08 | admin-manage-user/handler.test.ts: name/email/phone, filters/cursors/sanitization and exact email beyond scan window; real device email search. |
| R120-09 | AdminStatsRepositoryTest, AdminToolsViewModelTest; current-date systemStats fixture and source/time screenshots. |
| R120-12, 13, 17 | AdminLocalInteractionTest; regression screenshots; comparison notes in validation/README.md. |
| R120-16 | admin-current-profile.test.ts; stale-profile Edge regression in admin-review-application/handler.test.ts. |

Tests live under app/src/test/java/com/example/booking_app/feature/admin, app/src/adminLocalAndroidTest/java/com/example/booking_app/feature/admin, supabase/functions and firestore-tests/test. R120-14 records the full run results below. Markers in tasks.md summarize this evidence; they are not a substitute for it.

## Recorded runs

| Validation | Result / evidence |
|---|---|
| Reviewer checklist | requirements.md: 16 checked, 0 unchecked. Read-only. |
| Seven admin Edge suites plus shared list and affected owner-create-voucher | Latest completed run: 39 passed, 0 failed: 36 admin, 1 shared list, 2 owner-create-voucher (`.gradle/admin-edge-final.log`). |
| owner-docs endpoint | 1 passed, covering applicant/current admin/privacy/TTL and client-facing Storage URL (`.gradle/admin-owner-docs-test.log`). |
| Full Firestore suite | 223 passed, 4 failed, 26 files / 227 tests (`.gradle/admin-firestore-final.log`). New regression: stale ADMIN claims rejected after role revocation/lock/deletion. Four failures are unchanged spec 030 court-detail: VD-1b, VD-3, VD-4, VD-10. No skips or expectation changes. Existing allow fixtures now seed real ACTIVE ADMIN profiles. |
| Local Firebase + Supabase Storage | 3 integration tests passed (`.gradle/admin-integration-all.log`): decisions/claims/retry/race, user/venue/revoke/moderation/voucher/broadcast; simultaneous voucher writers; both signed documents/download/privacy/expiry/retry. |
| Real HTTP local Edge to Emulator | Passed (`.gradle/admin-http-smoke.mjs`), using demo-admin-repair only. |
| Full admin JVM + APK/instrumentation build | 68 JVM tests passed across 12 suites; APK/test APK build passed (`.gradle/admin-isolated-final-build.log`). Pre-fix red runs are separate and not reported as acceptance passes. |
| Default build configuration | compileDebugAndroidTestKotlin and all 68 admin JVM tests passed with the normal runner and without local opt-in flags (`validation/results/admin-default-build.txt`). |
| Attached device | Vivo V2247, serial 10ADBY2GH30038C, Android 15. Final phone-light, phone-dark, tablet-light and tablet-dark: 5/5 each, 0 failures/skips. Across the four runs: 4 actual interaction tests and 16 inflation smoke tests. All 56 screenshots were pulled. Hilt test application and Firebase/Supabase DI use local services only. |

Captured test/build output is retained in [validation/results](validation/results); JVM suite totals are in [admin-jvm-suites.json](validation/results/admin-jvm-suites.json). Device runs: [phone light](validation/results/admin-device-phone-light.txt), [phone dark](validation/results/admin-device-phone-dark.txt), [tablet light](validation/results/admin-device-tablet-light.txt), [tablet dark](validation/results/admin-device-tablet-dark.txt).

## UI evidence and known differences

- Valid wireframes 1–7 and 9–12 are the comparison sources. Step 8 is missing design evidence (connection error).
- Added required full date/timezone and usage-limit inputs to voucher form; wireframe 12 omits them.
- Added a reachable venue-management action to tools and an explicit claim-recovery action on decided application details.
- Stats totals are labelled according to stored fields; the wireframe's new-user/active-venue/drop-in metrics cannot be inferred from totals. Sample recent history is removed because there is no authoritative source contract for it.
- Category venue counts are omitted when no count source exists; sortOrder is not a count.
- Instrumentation inflation classes are smoke checks only. They do not establish an end-to-end pass. The separate actual interaction tests passed against local services.
- Comparison notes and remaining typography/icon/spacing differences are documented in [validation/README.md](validation/README.md). The unsupported unlock-notification sentence was removed in R120-18 on 13/11; prior screenshots still show the old copy. No unlock notification is claimed as implemented; FR-009 defines no such event. A dedicated user-detail artboard is still missing.
- Screenshots: [phone light](validation/screenshots/phone-light), [phone dark](validation/screenshots/phone-dark), [tablet light](validation/screenshots/tablet-light), [tablet dark](validation/screenshots/tablet-dark). [Manifest](validation/screenshots-manifest.json) records dimensions/checksums. Tablet variants exercise w600dp at 720 dp on the same physical phone using a temporary density override, not physical tablet hardware. No independent dark/tablet canvas PNG baselines are supplied; comparison uses the valid source structure and responsive rendering, not pixel-equivalence claims.

## Environment notes

- All mutation tests use demo Firebase Emulator and Supabase local. No deployment or hosted-project mutation.
- Supabase analytics/logflare was unhealthy. Local stack ran with logflare, vector, studio, realtime and imgproxy excluded; Auth/API/Storage/Edge services needed by these tests are running.
- Windows Bash/SDK sandbox restrictions required approved execution outside the sandbox. Git Bash prerequisite resolved the explicitly selected specs/120-admin; no feature-state/checklist rewrite.
- No before/after implementation extension hooks are registered (`.specify/extensions.yml` absent).
- Existing Vivo app signing differs from the validation APK. It is preserved. Device validation uses a separate `.adminlocal` application ID, fake demo Google Services configuration, and Hilt test DI. No uninstall/replacement of the existing app is authorized or performed.
- Final device check confirmed physical density 440 with no override and the original package still installed. Only the isolated `.adminlocal` application was force-stopped after collecting evidence; its APKs remain available for reruns.
- A first redirected final phone-light command reported adb's successful pull progress as a PowerShell native error after 5/5 tests had passed and all images had transferred. The script now captures progress as text and checks the actual adb exit code. The final phone-light rerun and the other final variants completed with exit code 0. Gradle's JVM sharing warning is printed on stderr; BUILD SUCCESSFUL and the 68-test XML results determine test status, and the default build command also completed with exit code 0.

## Follow-up 2026-11-13 — R120-18

Removed the unlock footer's unsupported owner-notification promise; retained the admin-only restriction. No backend notification behavior was added. `node --test scripts/admin/unlock-copy.test.cjs` first failed against the old resource (1 failure), then passed after correction (1 pass, no skips). It checks the wording and the unlock dialog's resource reference. Red/green output is retained under validation/results/admin-unlock-copy-*.txt.

Android `:app:processDebugResources` passed (39 seconds); output is retained in validation/results/admin-unlock-copy-resources.txt.

At the time of the text-only follow-up, no adb device was attached; the earlier 56 screenshots were preserved as historical evidence. That device blocker was later resolved by the physical-device investigation linked above, which produced 60 fresh screenshots and rechecked the corrected footer. Step 8 remains missing a valid design; reviewer checklists and court-detail.test.ts remain unchanged.
