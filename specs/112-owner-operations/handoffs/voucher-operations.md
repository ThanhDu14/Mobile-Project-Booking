# Handoff: venue voucher ownership and schema

**Feature owners:** Feature 111 (venue configuration), Feature 050 (application/redemption and payment), database owner.  
**Feature 112 consumer:** list/create/edit/enable-disable venue vouchers; never apply vouchers or mutate redemption counters.

## Problem and current source

- Feature 112 FR-013 lists `venueId` among voucher values.
- Database README §4.6 places vouchers at `venues/{venueId}/vouchers/{code}` and does not list a stored `venueId` field.
- Feature 111 currently owns venue/court/price/blocked-time configuration and also has a venue voucher boundary; Feature 112 spec assigns voucher management to owner operations. Feature 050 owns applying and redeeming voucher codes.
- Current checkout has no voucher UI, repository, backend CRUD or uniqueness transaction.

## Contract and ownership decision requested

Recommended boundary: Feature 112 owns the owner-facing voucher lifecycle; Feature 111 supplies verified venue context and confirms path/schema; Feature 050 alone applies/redeems and changes `usedCount`/redemptions. Use the existing nested path as ownership context, not a duplicate field, unless the database owner approves and updates schema.

Confirm stored fields and constraints: code uniqueness scope (spec says system-wide), type/value, max discount, minimum order, valid-from/to, total usage limit, per-user limit, isActive, and server-managed usage/redemption fields. Identify which fields remain immutable after first redemption.

Proposed server authorization: derive UID from verified token; require `appRole == OWNER`, current APPROVED status, owner match on parent venue, and venue/court status as agreed. Never accept a client ownerId. Unique-code check and create must be one transaction or another serialized uniqueness mechanism. Toggle/edit may not change `usedCount` or redemption history. Return only voucher fields safe for owner display.

## Error handling and acceptance tests

Test create success, duplicate code racing across two venues, foreign venue, revoked owner, LOCKED venue, invalid code/range/value/limit, update before/after redemption, enable/disable, and attempted writes to usage/redemption counters. Include stale/offline retry behavior and prove retries do not duplicate vouchers.

## Receiving files and integration

Update Feature 111 voucher ownership boundary, Database README §4.6 if needed, Feature 050 redemption contract and the owning server implementation/tests. Confirm the exact endpoint and test harness. Feature 112 will implement owner UI/domain/data adapters only after that approval.

## Ready-to-send implementation prompt

```text
Resolve the attached Feature 112 voucher handoff. Confirm whether owner-facing venue voucher CRUD belongs to Feature 111 or Feature 112, reconcile spec 112 FR-013 with Database README §4.6, and establish whether `venueId` is only the parent path context. Keep redemption/application and usage counters owned by Feature 050. Define exact existing-field validation, global code uniqueness under concurrent creates, authorization, immutable-after-redemption rules, request/response and error behavior. Implement and test only in the owning feature/database files, update their contracts, and report exact test commands/results. Do not add a schema field or edit Feature 112 before the ownership/schema decision is approved.
```
