# Notification continuation validation, 24/11/2026

Scope: D3/R1/H1 explicitly approved by the user after review of continuation-decisions-2026-11-24.md. No deployment, hosted data access, real FCM transport, live migration, Git commit or push. Android uses the isolated `.adminlocal` application, demo Firebase configuration and fake notification repositories/session; backend uses local Auth/Firestore Emulator and fake FCM.

## Backend and Rules

| Check | Result | Evidence |
|---|---|---|
| D3 registration unit behavior | 29/29 pass | continuation-2026-11-24/registration-fake.log |
| W1 and worker fake regression | 65/65 pass, exit 0 | continuation-2026-11-24/backend-fake.log |
| Registration/waitlist/review/chat affected source suites | 89/89 pass | continuation-2026-11-24/source-emulator.log |
| Final R1 and waitlist assertions | 12/12 pass | continuation-2026-11-24/final-emulator-rules.log |
| Review/delivery/inbox Rules | 25/25 pass in same final run | continuation-2026-11-24/final-emulator-rules.log |
| Self-cancellation and waitlist leave/replay | 5/5 pass, 6 unrelated cases filtered | continuation-2026-11-24/self-action-emulator.log |

The 89 and 12 runs overlap; these numbers are separate executions, not a count of unique cases. An initial Deno literal filter selected zero tests; the corrected `/T021|Rời hàng chờ/` run executed five and is the acceptance evidence. Initial registration failures expected the removed callback; fixtures now explicitly seed ACTIVE recipients and assert persisted inbox/jobs. The final 29-test run passes.

D3 uses the existing request UUID in the registration transaction. A successful replay does not enqueue again; rejoin uses a new UUID, stale event replay is rejected before writes. Waitlist entry/leave, add-party and self cancellation remain silent. Existing promotion/closure behavior is retained and tested.

R1 stores a server UUID generation with each review, preserves it on edit and renews it on recreate. Legacy first reply assigns it without REVIEW_NEW backfill. Only create/first reply enqueue; actor, inactive, deleting and absent recipients are excluded. Real emulator transaction abort after preparation leaves source/rating/inbox/job untouched. A fake FCM transient failure after commit retries only the failed token and keeps the inbox stable. Rules deny generation forgery by player, owner and admin clients.

H1 DIRECT message/inbox/job share the source transaction. Sender exclusion, muted/viewing inbox retention, generic body, message replay and absent callback are checked. GROUP retains its existing callback and remains incomplete; this run does not establish durable group fanout.

T043 has a 19-type × three-preference-state table through the actual worker. Missing/default, explicit false and true are covered; eligible jobs send to both fake tokens while disabled types keep inbox without send. BOOKING_NEW ignores false, PROMOTION defaults false. Reminder/chat source guards are supplied valid fixtures. Earlier reservation/lease tests still cover rechecking eligibility before each token.

Reproduce with `scripts/notification/validate-backend.ps1 -Suite Fake` and `-Suite Continuation`, explicit `demo-*` project and localhost emulator variables. Run review, notification-delivery-security and notification-security Vitest suites against the same local emulator. Deno executable can be supplied via `-DenoExecutable`.

## Android and physical device

Device: vivo V2247, Android 15, 1080×2388. Default density 440 gives 393dp; density 240 exercises 720dp/w600dp. Font and other global settings are unchanged. Each UI variant restores its prior density and the isolated application's POST_NOTIFICATIONS grant/USER_SET/USER_FIXED flags in `finally`. Push/permission script also snapshots and restores permission flags; before/after files are included.

| Check | Result | Evidence |
|---|---|---|
| Focused JVM, production assembleDebug/lintDebug | 64 tests in 12 suites; zero failures/errors/skips; build succeeds | android-final.log, jvm-suites.json |
| Final isolated app/instrumentation build | BUILD SUCCESSFUL | device-build-final.log |
| Lint after final test-harness update | zero errors, 858 existing warnings; BUILD SUCCESSFUL | lint-final.log |
| Phone light/dark and 720dp/w600dp light/dark | 12/12 each; 48/48 total | device/*/instrumentation.log, device-matrix-final.log |
| Channel/renderer/granted handler | 6/6 | device/push-permission/channels-renderer-handler.log |
| Denied handler | 1/1 | device/push-permission/denied-handler.log |
| Runtime prompt grant; deny/inbox/settings-return grant | 2/2 | device/push-permission/*Preferences*.log, device-push.log |

All paths in this table are under continuation-2026-11-24/. Physical-device total is **57 checks**, without skips. `device/device-final.txt` confirms physical density 440 with no override and the original granted permission/flags; push-permission before/after snapshots match. `results.json` records counts and `sha256.txt` hashes the evidence. Tests use installed isolated APKs; normal and isolated builds are separate invocations.

The checksum manifest covers published evidence only; the existing validation ignore rules omit ADB `pull.stdout.log`/`pull.stderr.log` transfer output. Scoped Git attributes preserve evidence bytes across checkouts so newline conversion cannot invalidate those checksums. Captured raw logs retain their original whitespace.

Device scenarios use fake source data. Paging tests exercise the real ViewModel/Fragment/adapter with 30/30/5 fake repository pages, read-one and global read-all before the last page; production Firestore query/transport semantics retain their earlier JVM/emulator evidence. Logout/account switch clears the rendered list immediately. The first device run exposed old RecyclerView rows during async diff/removal animations; the Fragment now hides an empty list, synchronously clears its adapter and ends removal animations. Initial failure log is retained. A later push-tap test hit an Espresso activity/dialog root-focus race; its harness now waits for the active dialog window before selecting an Espresso root. Neither failing run counts as acceptance.

Push tests call the exact `NotificationPushHandler` used by `FirebaseMessagingService`; they do not claim receipt through FCM infrastructure. They post only generic local content in the isolated app and cancel the test notification. They check UID mismatch, logout, notification-block rejection, malformed ID, private/public generic content and permission denial. Renderer/channel checks retain immutable distinct tap intents, channel metadata and preservation of OS-selected channel settings. Runtime permission scenarios deny/grant the real Android prompt and return from real OS settings; preference and inbox remain available.

## UI comparison

Reopened ui/README.md and both original PNGs, then directly opened inbox/settings screenshots for all four final variants. The drawn layouts/resources are unchanged; the account-reset fix only affects visibility while empty. Comparison retains approved grouped checkboxes/mixed state, extra owner-result and SYSTEM rows, phone scrolling, w600dp spacing, existing five-tab shell and typography/icon/time differences documented in earlier validation. Original UI documents/images were not edited. There are **24 current UI screenshots** (six per variant) plus **four current permission screenshots**; an old remote failure screenshot was excluded rather than treated as new evidence.

| Variant | Inbox | Settings |
|---|---|---|
| Phone light | [inbox](continuation-2026-11-24/device/phone-light/notification-393dp-light-inbox.png) | [settings](continuation-2026-11-24/device/phone-light/notification-393dp-light-settings.png) |
| Phone dark | [inbox](continuation-2026-11-24/device/phone-dark/notification-393dp-dark-inbox.png) | [settings](continuation-2026-11-24/device/phone-dark/notification-393dp-dark-settings.png) |
| w600dp light | [inbox](continuation-2026-11-24/device/tablet-light/notification-720dp-light-inbox.png) | [settings](continuation-2026-11-24/device/tablet-light/notification-720dp-light-settings.png) |
| w600dp dark | [inbox](continuation-2026-11-24/device/tablet-dark/notification-720dp-dark-inbox.png) | [settings](continuation-2026-11-24/device/tablet-dark/notification-720dp-dark-settings.png) |

Unread mint/dark-green rows, amber dots, white/dark read rows, title/body hierarchy, section order and green checks remain consistent with the approved baseline. Phone wraps/scrolls while 720dp shows all settings groups without clipped labels. The OS sidebar handle, status/navigation bars, static fixture dates and native font/icon/checkbox rendering differ from the exported artboards; these are the previously disclosed baseline differences, not pixel-equality claims or new document exceptions. Shell and cache/extra-row captures are beside each main screenshot. No new visual regression was found after the account-reset fix.

## Limits and remaining tasks

T021 and T043 are newly verified; total **41/56**. T023/T025/T044/T045/T046/T048 have additional implementation but remain open because their whole type/source scope is not complete. T004/T005 remain partial for the remaining sources, and T013/T035 retain twelve source routes plus REVIEW_REPLY read compatibility and saved-detail fallback.

- BOOKING_NEW: B1 helper is ready; actual Feature 050 HOLD→PENDING payment transaction is absent and will be supplied later. No replacement payment flow or callback was invented.
- DROPIN_CHANGED: repeated edit/cancel generation, command replay and recipient snapshot need a contract.
- PROMOTION: publication identity, source commit, eligible followers/snapshot and player destination remain unconfirmed.
- GROUP_ACTIVITY and GROUP chat: durable bounded fanout/outbox schema, source transaction, snapshot paging/retry and cleanup need review/approval. No collection or member cap was invented.
- OWNER_REVOKED: retained admin command identity/replay and confirmed source target remain missing.
- REVIEW_REMINDER: Feature 070 repeat wording versus Feature 100 once, timing/eligibility/query and identity remain unresolved.
- BOOKING_NEW, PROMOTION, REVIEW_NEW and OWNER_* routes lack complete destination/identifier/access contracts; saved title/body remains the fallback. SYSTEM intentionally opens saved detail.
- Docker daemon is unavailable: dockerDesktopLinuxEngine pipe absent even outside sandbox. Supabase local endpoints and cron SQL remain unexecuted; migrations are unapplied. No real FCM delivery, force-stop/background transport acceptance or full-catalog quickstart is claimed. T056 stays open.

Coordination requests are shown directly in the conversation, describing required source/schema updates for other members. No coordination-message file or outbound message is created.
