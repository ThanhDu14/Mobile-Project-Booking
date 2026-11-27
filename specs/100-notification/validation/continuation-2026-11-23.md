# Feature 100 cache/group validation, 23/11/2026

Continuation starts from 23/56 tasks. User approved the presented GROUP_ACTIVITY Android routing and UID-scoped DataStore cache policy during this turn. T012 and T039 are now complete, giving **25/56**. Other missing source routes and backend schema/lifecycle decisions remain open.

## Changes verified

- Group routes read group/member/post documents using SERVER. Requests require current OWNER/ADMIN group membership; membership decisions/removal may open the readable group intro; event create/update also require postId and readable/existing post. Deleted/unknown events, bad IDs, denied/missing sources and account changes use saved title/body. REVIEW_REPLIED remains canonical with the approved REVIEW_REPLY read alias. No source permission or navigation argument was added.
- DataStore is a cache of resolved per-type booleans, scoped to hashed UID. Successful server reads/transaction writes refresh it. Network/timeout read failure may return a marked snapshot; the UI shows saved settings with retry and disables edits until server refresh succeeds. Missing/forbidden profiles and changed accounts do not become cached success. BOOKING_NEW remains true and PROMOTION defaults false.
- Preference writes now use a client Firestore transaction, not direct update(), so there is no persistent offline mutation queue. The transaction reads the existing profile and updates only chosen dotted keys plus updatedAt. Cache IO failures do not replace acknowledged server success. See [Firebase transaction behavior](https://firebase.google.com/docs/firestore/manage-data/transactions).
- Existing session cleanup clears the local user-cache namespace and advances an epoch; late pre-logout operations cannot repopulate it with the old epoch. This cleanup also runs for ACCOUNT_DELETED, which skips authenticated SignOutHooks. Welcome and existing password-reset recovery values are preserved.
- Planner validates whole inputs, pins the expected CLI project, rejects mixed projects/conflicting duplicates/malformed REST Values, and validates updateTime while retaining nanosecond precision. It remains offline/read-only. A synthetic demo snapshot CLI smoke test produced one proposed write without network/commit permissions.

## Final checks

| Check | Result | Evidence |
|---|---|---|
| testDebugUnitTest assembleDebug lintDebug | PASS | 255 suites, 1,682 tests, 0 failures/errors; 2 existing Windows-only DataStoreSessionPrefsTest assumptions. 37 notification tests in eight suites pass without skips. Lint 0 errors, 856 warnings. |
| Isolated Android app/test build | PASS | adminLocalAppId=true, NotificationTestRunner; no production package installation |
| Firestore notification/profile/query/group | 53/53 | Emulator project demo-smashnow; includes existing group authorization and the new unavailable-transport preference transaction case |
| Offline planner | 6/6 | Exact field mask/precondition, native/invalid date handling, mixed-project/runtime shape/conflicting duplicate rejection and project pin/precision |
| Device UI matrix | 36/36 | Nine tests on phone/light, phone/dark, w600dp/light, w600dp/dark |
| Offline CLI smoke | PASS | Synthetic demo snapshot only, explicit expected project, one planned write, no send/apply mode |

[Machine-readable results and screenshot hashes](continuation-2026-11-23-results.json) and [extracted verification summary](continuation-2026-11-23-verification.log) accompany this report. Full local logs are `.gradle/notification-2026-11-23-focused.log`, `notification-2026-11-23-android-final.log`, `notification-2026-11-23-device-build.log`, `notification-2026-11-23-rules.log`, `notification-2026-11-23-planner.log`, and per-variant logs. No Android source changed after the final aggregate command began.

Six cache tests cover server seeding/fallback, forbidden/missing profile behavior, UID isolation, logout/deletion cleanup and stale epoch rejection, successful/failed transaction cache updates, cache IO failure and account changes. They run the production cache with a serialized in-memory DataStore to avoid the older Windows file-rename assumption. ViewModel tests verify disabled writes, retry and account reset. Four group route tests verify moderator access, readable group intro, post checks, unknown/deleted events and changed session.

The first compile found an invalid generic function reference in cache-key removal; it was replaced with an explicit lambda. Three planner regressions failed before their fixes. An initial emulator test incorrectly assumed JS disableNetwork() disabled transaction RPCs; that transaction succeeded. The corrected test uses a separate demo client pointed at a closed loopback port, asserts UNAVAILABLE, verifies server preferences remain unchanged, then verifies an online transaction changes one key. This is an actual transport-failure check, not a skipped or weakened assertion. Android repository tests separately verify the transaction path and that direct update() is never used for preference mutations.

## UI comparison

Both original PNGs and ui/README.md were opened directly. Existing card groups, colors, typography, gutters, icons and the two previously approved owner/system rows are retained. The original files are unchanged. The new cache state uses the existing error/retry area and Material disabled checkboxes, with an explicit saved-settings explanation; it does not claim server freshness. Successful retry removes the warning and restores editing.

| Variant | Tests | Cache screenshot |
|---|---:|---|
| Phone light, 393dp | 9/9 | [settings-cache](device-2026-11-23/phone-light/notification-393dp-light-settings-cache.png) |
| Phone dark, 393dp | 9/9 | [settings-cache](device-2026-11-23/phone-dark/notification-393dp-dark-settings-cache.png) |
| w600dp light, 720dp | 9/9 | [settings-cache](device-2026-11-23/tablet-light/notification-720dp-light-settings-cache.png) |
| w600dp dark, 720dp | 9/9 | [settings-cache](device-2026-11-23/tablet-dark/notification-720dp-dark-settings-cache.png) |

There are 24 final PNGs: inbox, settings, shell variants, extra rows and cache state for each variant. Representative cached settings and inbox images were opened after capture; disabled controls and warning/retry remain legible in both themes and widths. Standard inbox/settings retain the previous approved differences from the reference, including the shared application's current footer. Fixture dates and row counts differ between Fragment and shell cases as in the baseline; no pixel-identical claim is made. [Phone settings](device-2026-11-23/phone-light/notification-393dp-light-settings.png), [w600dp dark inbox shell](device-2026-11-23/tablet-dark/notification-720dp-dark-inbox-shell.png).

Tests run on the same vivo V2247 Android 15 using isolated package com.example.booking_app.adminlocal. w600dp is simulated with density 240, not a second physical tablet. Each script restores density in finally. Final readback: physical density 440, no override, physical size 1080x2388, font scale 1.0. Screenshot hashes are recorded in the results JSON; per-variant instrumentation.log files contain the passing nine-test results.

## Limits and unresolved decisions

UI repositories/UID stream/resolver remain fakes; the cache UI test supplies a marked cached snapshot. JVM cache tests use an in-memory DataStore and mocked Firebase SDK; Rules/transport tests use the JS emulator client. The phone was not put into airplane mode or connected to hosted Firestore. These checks do not claim end-to-end durable push, physical-disk failure recovery or producer-to-phone integration. T056 remains open.

Token registration/refresh/account-switch/OS eligibility and strict token Rules remain unconfirmed beyond Feature 011's existing logout hook/P15. T013/T035 remain partial for BOOKING_NEW, PROMOTION, REVIEW_NEW and OWNER_*. See [decision review](../decision-review-2026-11-23.md) for current evidence, concrete choices and tasks unlocked.

The delivery proposal now covers receipt recovery, unique lease claims, bounded initialization and deletion races. Its collection/fields, fanout outbox, event identity and worker are still unapproved. No delivery worker, token service, shared Firestore field, source permission or dependency was added. No hosted data was read/migrated, no real FCM was sent, and no commit/push/deployment was performed. Reviewer checklist markers remain unchanged; no extension hooks are configured.
