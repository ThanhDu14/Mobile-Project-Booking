# Handoff: statistics source, denominator and freshness

**Feature owners:** database owner and Feature 120 owner of `dailyStats`/aggregation; Feature 050/060 own payment and booking fields.  
**Feature 112 consumer:** real venue-scoped revenue, occupancy and peak-hour report.

## Existing definitions in Feature 112

- Revenue separates `DEPOSITED`, `PAID` and `REFUNDED`; `AT_VENUE` counts as paid only after server-confirmed collection; rejected/cancelled/expired/unpaid bookings are excluded. Refund amount comes from `refundAmount` if present.
- Occupancy numerator includes confirmed use (`CONFIRMED`, `COMPLETED`, `NO_SHOW`) and excludes blocked time; peak periods are 60-minute buckets in `Asia/Ho_Chi_Minh`.
- Database README mentions `venues/{venueId}/dailyStats/{yyyyMMdd}` but does not define metric fields, schedule snapshots, freshness or multi-court allocation. Feature 120 mentions the aggregate without settling these details.

## Source selection received

The user selected bounded live booking/slot queries, with no new aggregate schema and no Feature 112 writes to `dailyStats` (2026-11-05). This conflicts with the current wording in Feature 112 FR-015 (“dailyStats when data exists”). Do not silently edit the approved spec; the spec owner must align that wording with this newer source decision. Feature 112 still requires a server-side venue-scoped projection that excludes QR secrets. The database/Feature 060 owner must publish the query/adapter contract and maximum date range.

Still define occupancy denominator for historical reports: availability by active court × operating hours or price-rule-covered hours; how historical venue/court/hour changes are represented; how blocked slots and overlapping bookings are removed; allocation of a booking spanning midnight/multiple courts; treatment of partial 30-minute slots. Confirm whether each booking duration contributes once per court and split across local date/hour buckets.

Proposed freshness response includes `computedAt` and a completeness boundary (for example, through the last completed local day); same-day data must be visibly partial or excluded, never presented as fully current. Confirm exact format before implementation.

## Acceptance tests

Compare calculation against real-shaped fixtures for DEPOSITED, PAID, REFUNDED with/without refundAmount, AT_VENUE unpaid/paid, rejected/cancelled/expired, confirmed/completed/no-show, blocked hours, overlapping/multi-court bookings, slot-boundary/day-boundary and DST/timezone conversion. Empty and stale aggregate cases must be distinguishable. Enforce venue owner isolation on every query.

## Receiving files and integration

Publish the bounded live query/server projection contract and tests owned by the database/Feature 060 owner, including endpoint location, maximum report range, response model, freshness semantics and Emulator/test command. Align Feature 112 FR-015 with the source selection. Feature 112 will implement calculation/view presentation only against the approved actual source.

## Ready-to-send implementation prompt

```text
Implement the user-selected bounded live booking/slot query for Feature 112 statistics. Publish a venue-scoped server projection that excludes QR secrets, endpoint, maximum report range, response model and freshness/completeness semantics. Define occupancy denominator across historical venue hours, court changes, price-rule gaps, blocked slots, overlapping/multi-court bookings and local 60-minute buckets. Preserve revenue status rules and `Asia/Ho_Chi_Minh`. Add backend accuracy/isolation tests in the feature that owns the source. Align Feature 112 FR-015 with the selected source; do not add aggregate fields or allow direct client reads of booking documents. Report exact commands/results and the adapter contract.
```
