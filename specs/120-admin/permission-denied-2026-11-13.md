# Admin PERMISSION_DENIED — physical-device investigation, 2026-11-13

Device: Vivo V2247, connected through wireless adb. The original `com.example.booking_app` process logged Firestore PERMISSION_DENIED for the PENDING ownerApplications query, OPEN reports query, and systemStats/20261113. Android system idmap/cgroup permission warnings are separate from these application errors.

Confirmed client defect: FirebaseSessionManager replaced the server role with ADMIN for one hard-coded email in debug builds. This can open admin navigation while Firestore and Edge correctly reject the account's actual token/profile. R120-19 removes the email override; session routing now follows the server-issued appRole in all builds. Legitimate ADMIN claims remain supported. No Firestore or Edge authorization restrictions were weakened.

Second confirmed client defect (R120-21): MainActivity collected admin badge state unconditionally, instantiating admin ViewModels whose initializers open protected ownerApplications/reports listeners even for PLAYER/guest sessions. A targeted physical-device regression failed with `PLAYER must not initialize admin query ViewModels`. MainActivity now starts these collectors only for a SignedIn ADMIN session, stops the listeners when that collection is cancelled, and prevents navigation callbacks from restarting them without an ADMIN session. This is a client query-lifetime correction, not a permission grant.

The JVM regression for the special email failed before repair (one test, one failure). It checks PLAYER, OWNER, ADMIN and missing claims, so the correction cannot silently deny legitimate ADMIN or promote an uninitialized account. The local device regression additionally checks player navigation and a denied admin-data read; the existing actual-admin interaction flow remains enabled.

The original installed account's cached Firebase session is encrypted. No token was exported or persisted, and its actual server claim/profile was not conclusively inspected. Consequently the device log plus reproduced client defect do not establish that every possible hosted Rules/configuration issue has been ruled out. Granting production administrator rights or deploying Rules is outside the authorized local-testing scope. A legitimate administrator requires an ADMIN claim and a current ACTIVE ADMIN profile without a deletion request; changing an email or UI role cannot grant access.

No saved Firebase CLI login was available, so active hosted Rules could not be inspected. All mutation tests target demo-admin-repair Emulator and Supabase local, using the isolated adminlocal APK and preserving the original app/data. The four known spec 030 failures and reviewer checklists remain unchanged.

## Fresh backend/build results

- JVM session + admin: 131 discovered, 129 passed, 0 failed, 2 existing Windows DataStore assumptions skipped. All 68 admin tests and all 15 FirebaseSessionManagerTest cases passed, including the new regression. No new skip was added.
- Seven admin Edge suites plus shared list, owner-create-voucher and owner-docs: 40 passed, 0 failed.
- Firebase/Supabase local integration: final sequential run 3 passed. An earlier overlapping run collided with the owner-docs fixture (2 pass / 1 fail); that failed attempt is retained and is not acceptance evidence.
- Firestore final full run: 223 passed, 4 unchanged spec 030 failures (VD-1b, VD-3, VD-4, VD-10), 0 skipped. An earlier infrastructure-timeout run was interrupted and is retained separately.
- APK and instrumentation build passed. Two real interaction tests exposed test-component lifetime issues: first repeated Firestore emulator configuration, then duplicate active DataStore files. R120-20 reuses the configured local Firestore client and gives each Hilt component its own real DataStore file. No production DataStore or backend permissions were changed.
- The PLAYER navigation assertion was corrected to inspect role-graph ancestry: the outer graph remains nav_main even when the active destination belongs to nav_player. The checks still require PLAYER, absence of admin UI/query ViewModels, and a server-denied protected read. Failed attempts are retained; they are not reported as successful runs.

Fresh targeted regression on Vivo passed after the MainActivity fix (1/1). Final full-package runs all passed: phone-light 6/6, phone-dark 6/6, tablet-light 6/6, tablet-dark 6/6; zero failures or skips. Across these four runs there are 8 actual interaction test executions and 16 inflation smoke executions. The successful negative case verifies PLAYER navigation, absence of admin query ViewModels/UI, and an intentionally denied admin-data read; the PERMISSION_DENIED from that deliberate read is expected protection, not a remaining startup-query failure.

All 60 new images are in [the dated screenshot directory](validation/screenshots/2026-11-13-permission), with dimensions/checksums in [manifest.json](validation/screenshots/2026-11-13-permission/manifest.json). Actual admin flows signed/downloaded both documents, approved a profile, closed a report, searched an email, added/edited the correct categories, sent a broadcast and locked/unlocked a venue. Tablet variants exercise w600dp using temporary density on the same Vivo, not separate tablet hardware. The new venue screenshots also confirm removal of the unsupported notification footer.

## Subsequent explicitly authorized account provisioning

After the user explicitly requested ADMIN for their UID and supplied a Service Account for mobile-22e6c, a direct server check confirmed the mismatch: Auth appRole=PLAYER, Firestore role=ADMIN, accountStatus=ACTIVE, no deletion request. The existing grant-role script was executed for that UID only. Read-back verification confirmed Auth appRole=ADMIN, Supabase role=authenticated, Firestore role=ADMIN, with ACTIVE/deletion status unchanged. No Rules were deployed. This was a separately authorized account-permission change, not a mutation performed by the local test suites. The signed-in app still needs a refreshed token (sign out/in); active hosted Rules and the device's refreshed session were not verified by this provisioning step. No private-key contents were written into the repository or report.

## Subsequently authorized production indexes

After the user reported FAILED_PRECONDITION, original-app device logs explicitly identified missing indexes for status-filtered ownerApplications and reports queries. The user then explicitly authorized creating the indexes on Firebase. The Service Account initially lacked index-creation permission (POST returned 403); the user supplied Cloud Datastore Index Admin access, after which creation succeeded.

Only these three COLLECTION-scoped indexes were created in mobile-22e6c/(default), matching firestore.indexes.json:

- ownerApplications: status ASC, submittedAt ASC, __name__ ASC.
- ownerApplications: status ASC, submittedAt DESC, __name__ ASC.
- reports: status ASC, createdAt DESC, __name__ ASC.

All three were polled until READY. State evidence: [firebase-admin-indexes-ready.json](validation/results/2026-11-13-permission/firebase-admin-indexes-ready.json). No Rules, existing index definitions or business documents were changed during index provisioning.

Read-only spot checks on the original installed app successfully loaded the pending and approved application lists and the open/closed report empty states without a visible FAILED_PRECONDITION or PERMISSION_DENIED. The rejected tab was not independently confirmed: the screen changed during the UI-check sequence, so subsequent controls were unavailable and no blind taps were issued. It shares the verified READY descending application index. These production spot checks are distinct from the earlier comprehensive local/emulator test runs.

Artifacts are kept in [validation/results/2026-11-13-permission](validation/results/2026-11-13-permission); this investigation does not relabel historical 12/11 screenshots as fresh evidence. The original installed app and production rights were preserved; these successful interaction runs use the isolated adminlocal APK with local services. Hosted account provisioning/active Rules remain unverified, so this report does not claim that production access for the original account has been repaired or granted.
