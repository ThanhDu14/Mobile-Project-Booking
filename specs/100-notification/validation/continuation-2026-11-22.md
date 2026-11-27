# Feature 100 continuation — 22/11/2026

This report follows `android-2026-11-22.md`; its 32-test phone/w600dp light/dark matrix remains the baseline. Both original PNGs were opened again. No inbox/settings layout, typography, grouping, color or spacing was changed. The existing permission warning/action gains runtime request behavior and a contextual action label when permission is missing.

## Verified backend and data work

| Check | Result | Evidence |
|---|---|---|
| Auth/Firestore account deletion | 21/21 pass | Existing delete-account suite plus F100 T054 case: 405 inbox documents cross the 400-row cleanup batch boundary; profile with two fake tokens and per-type preferences is deleted; another user's inbox/profile remains. Tests now reject non-demo/non-local environments before clearing fixtures. |
| Firestore Rules and approved query | 28/28 pass | notification-security + users-profile suites. New fixture has 65 Timestamp records, including ties, with pages 30/30/5, descending times and no duplicate document IDs. A second fixture reproduces String/Timestamp sort failure and verifies a createdAt-only repair restores order while preserving body/isRead. |
| Producer regression and offline repair | 28/28 pass | Three existing admin handler suites, four actual producer/REST encoding cases and two offline repair tests. Includes SYSTEM deleting/locked/unspecified-status exclusions, idempotent retries and preserved read state. |
| Review reply producer | 5/5 pass | Actual review reply suite on Auth/Firestore emulator; first reply emits REVIEW_REPLIED, later edits do not emit another event. |

Commands and local logs: `.gradle/notification-account-deletion.log`, `notification-query-rules-final.log`, `notification-producers-repair.log`, `notification-review-reply.log`. All test backends use demo project IDs and loopback emulators or an in-memory REST transport. No hosted data, FCM service or production credentials were used.

## T055 index conclusion

The exact query is `users/{uid}/notifications.orderBy(createdAt, DESCENDING).limit(30)`, optionally `startAfter(DocumentSnapshot)`. There are no additional where clauses, collection-group query or separately sorted field. `firestore.indexes.json` contains no `notifications` field override, including no `*` exemption. The existing collection-scope automatic descending single-field index suffices; no composite index or configuration addition is justified. Document ID is the implicit tie breaker retained by the snapshot cursor.

This conclusion follows the [official Firestore automatic index defaults and ordering documentation](https://firebase.google.com/docs/firestore/query-data/index-overview#automatic_indexing). The emulator test proves query/Rules/cursor behavior, **not** production composite-index enforcement or the current deployed index inventory. No hosted index audit/deployment was performed. If hosted automatic indexes were manually exempted outside this repository, reconcile that configuration before release. T055 is closed as verified/no addition required for the approved query.

## Routing and contracts

Eleven canonical types now have source routes; legacy REVIEW_REPLY also maps to REVIEW_REPLIED under the user's explicit approval during this turn. REVIEW_REMINDER uses bookingId and current booking ownership; review reply uses reviewId/venueId, current review ownership, visible/replied status, venue match and venue existence. All reads use SERVER, check account identity after awaits, and return saved title/body detail for unavailable/forbidden sources. Unit tests cover current and legacy names, hidden/deleted/forbidden/wrong-user sources and account changes. No deep link was added to another feature or permission loosened.

BOOKING_NEW, PROMOTION, REVIEW_NEW, GROUP_ACTIVITY and OWNER_* retain saved detail while their target-specific contracts remain open. SYSTEM deliberately uses saved detail. See `contracts/event-integration.md` for evidence and proposed next decisions. T013/T035 stay partial; do not count them complete merely because fallback works.

## Permission and final Android checks

Android 13+ requests permission only on an explicit tap. Denial retains the in-screen warning and offers OS settings without a modal blocking preferences. Older Android opens the OS app notification switch. Return from system settings refreshes status in onResume; no per-type preference or inbox data is changed by permission state.

Final production-source `testDebugUnitTest assembleDebug lintDebug` succeeds (`.gradle/notification-final-checks.log`): 253 suites, 1,671 tests, zero failures/errors, two existing Windows-only DataStore session skips. Six notification suites contain 26 passing tests. Lint has zero errors and 856 warnings, matching the preceding increment. Both explicit SDK guards and pre-33/33+/granted policy unit cases are included. The pre-33 OS screen was not exercised on a separate older device; that branch has policy-unit coverage only.

The isolated adminlocal app/test APK build succeeds. Phone/light `NotificationUiTest` passes 8/8 (`continuation-device/baseline-ui.log`). Two permission scenarios pass independently with POST_NOTIFICATIONS revoked/reset before each instrumentation process: denial retains editable preferences and usable inbox, denial action survives Activity recreation, the real OS settings page opens and onResume updates after grant; a fresh permission dialog grant hides the warning without changing PROMOTION/BOOKING_NEW defaults. These run on vivo V2247 Android 15 at the unchanged phone density. The original 32/32 phone/w600dp light/dark matrix is retained, not rerun: no drawn layout/theme/group changes were made.

Intermediate evidence is retained separately: initial denial-UI wait failure (`permission-initial-failure.log`), nested ActivityScenario harness failure (`permission-harness-failure.log`), and vivo's OEM settings-package identification failure (`permission-oem-identification-failure.log`). One run was interrupted by device use, then resumed after the user confirmed availability. The harness now uses one ActivityScenario with Fragment back stack, recognizes the OEM settings activity, and waits for window animation before screenshots. Failed diagnostic screenshots are not final UI evidence. Capture guards require the isolated app window and do not capture unrelated foreground apps.

An intermediate lint run crashed in `androidx.annotation.experimental.lint.ExperimentalDetector` (Kotlin FIR analysis). No detector was disabled; subsequent normal production-source and local instrumentation lint checks succeeded. Intermediate logs are retained locally. No warning suppression, test disablement or new skip was used to obtain success.

## Final saved evidence

[Machine-readable results](continuation-results.json) records actual JVM suite totals, lint counts and SHA-256 hashes for the four final permission screenshots. [Verification summary](continuation-verification.log) is extracted from the passing run logs. The last capture build and instrumentation lint pass (`notification-capture-build.log`, `notification-lint-verified.log`); the final two device scenarios also pass after adding the animation wait (`notification-permission-captures.log`). No production source changed after the passing aggregate build/unit/lint run.

| State | Final screenshot |
|---|---|
| Denied, preferences remain editable | [notification-permission-denied.png](continuation-device/notification-permission-denied.png) |
| Inbox usable while denied | [notification-permission-denied-inbox.png](continuation-device/notification-permission-denied-inbox.png) |
| Granted in OS settings, returned to app | [notification-permission-granted-return.png](continuation-device/notification-permission-granted-return.png) |
| Granted in Android runtime prompt | [notification-permission-granted-prompt.png](continuation-device/notification-permission-granted-prompt.png) |

The stable return/inbox captures were opened directly after replacing earlier transition-frame captures. Settings matches the existing reference-based layout/theme/groups; the OS warning is the specified extra denied state. PROMOTION is checked in the denied/return scenario because that test deliberately changes it while permission is denied; the fresh-grant scenario retains its default off. The earlier `notification-permission-failure.png` shows the OEM settings page from a failed harness-identification run and is diagnostic only; it is excluded from final screenshot hashes. System status/navigation bars and the vivo edge handle belong to the test device.

The two original PNG references and `ui/README.md` are unchanged. Existing phone/w600dp light/dark captures and approved navigation/additional-row deviations remain documented in [the baseline validation](android-2026-11-22.md). No whole-matrix rerun or pixel-identical match is claimed for this increment.

## Boundaries

Producer Timestamp fixes are local code changes only. Legacy records have not been queried or migrated. The offline planner has no network/commit capability; see `../timestamp-repair-proposal.md` for the review/execution procedure.

T009 is resolved from the existing confirmed Feature 120 Broadcast contract. T007's CSDL Admin permission conflict remains open. Delivery jobs, per-token receipts, fanout outbox, generation/request fields, retries, cleanup for that proposed storage and push payload remain unapproved. Existing T054 validates existing inbox/profile cleanup, not storage proposed in delivery-state-proposal.md. DataStore cache policy T012/T039 is unchanged and open.
