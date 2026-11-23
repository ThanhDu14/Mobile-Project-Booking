# Notification createdAt: producer fix and offline repair proposal

Status: producer fixes tested locally; legacy data repair requires a separate review and execution approval. No hosted data was read or changed.

## Confirmed scope

Three live-code producer paths encode ISO text as `stringValue`: `admin-broadcast-notification/handler.ts` (SYSTEM), `admin-review-application/handler.ts` (OWNER_APPROVED and OWNER_REJECTED), `admin-revoke-owner/handler.ts` (OWNER_REVOKED). `_shared/firestore.ts` deliberately encodes strings as strings; only `Date` becomes `timestampValue`. The fix wraps notification.createdAt in `new Date(nowIso)`, retaining the same instant and leaving unrelated profile/application fields unchanged.

Inventory of all direct `notifications/` writers also found `_shared/owner-booking-command.ts`, `_shared/waitlist.ts`, and `booking-timeout/handler.ts`. Those already pass `Date`. Review/group/chat use notification callbacks rather than a direct inbox writer; their shared delivery contract remains open.

Affected old documents can be any historical record of those four types emitted by the three paths. Their number and date range are unknown without an approved data inventory. Do not assume all records of those types are malformed. Mixed Firestore types sort by type before value, so ISO strings and Timestamp values do not form chronological pages. Android `getTimestamp` also rejects a string and can fail the whole loaded page. A client-only parse would not repair query order.

## Reviewable repair

1. Deploy the corrected producers through the normal reviewed release process before any data repair. This turn does not deploy.
2. Obtain a bounded snapshot/export of `users/*/notifications/*` including REST `name`, `updateTime`, and `fields.createdAt`, with project/date/count manifest and access-controlled backup. Include all types to detect unknown historical writers. Do not log title/body/token data.
3. Run the offline planner `scripts/notification/plan-timestamp-repair.ts` on the snapshot, with read permission only. It has no network or commit code. It accepts only canonical UTC strings produced by `Date.toISOString()`, only inbox paths, and only records with a concurrency precondition. Native Timestamps remain unchanged; invalid/ambiguous/missing dates require manual review.
4. Review output counts, project/path boundaries, duplicate inputs, rejects, and a sample of original instants. Approve a separate executor and manifest explicitly. Planned writes update only `createdAt`, keep the original instant, and require the exported `updateTime`; changed/read-marked records must be re-exported and replanned, never overwritten blindly.
5. A future approved executor should use bounded batches (at most 200), stop on precondition conflict, checkpoint completed document names, and support dry-run/resume. Do not reset to migration time, rewrite isRead/type/data or use server timestamp transforms for historical data. Re-run the query and Android paging checks after conversion. Rollback requires a fresh precondition check; never restore entire user documents over newer writes.

Example offline invocation (no real snapshot supplied or migration executed):

```powershell
deno run --allow-read=notification-snapshot.json scripts/notification/plan-timestamp-repair.ts notification-snapshot.json expected-project-id > notification-repair-plan.json
```

`plan-timestamp-repair.test.ts` verifies field mask, original instant, updateTime precondition, invalid calendar/zone rejection, native-date preservation and path/duplicate guards. Producer regression tests exercise actual handlers and REST encoding/decoding, including idempotent retry and preserved read state.

The local Firestore `notification-security.test.ts` additionally reproduces an older ISO-string record sorting before a newer native Timestamp in a descending query. Updating only that fixture's createdAt to the same instant as Timestamp restores chronology and leaves body/isRead unchanged. This is an emulator fixture conversion only, not execution of a real-data migration.

## Planner review, 23/11/2026

Three new regressions initially failed: mixed-project snapshots were accepted, conflicting duplicate versions silently used the first snapshot, and nonempty but invalid updateTime produced a write. The planner now validates the entire input before planning, rejects mixed/incorrect projects and conflicting duplicates, and validates REST object/value shapes. Exact duplicates still skip safely. The CLI requires an explicit expected project ID; the result also records projectId for manifest review.

Concurrency timestamps accept canonical UTC with up to nine fractional digits; calendar validity is checked without rounding the precondition placed in the plan. Original string createdAt remains limited to the Date.toISOString() form emitted by affected producers. Nanosecond updateTime is preserved byte-for-byte. Invalid preconditions are skipped rather than emitted. The planner remains read-only/offline; neither migration execution nor hosted inventory was added.
