# Feature 112 dependency handoffs

These requests keep Feature 112 implementation moving without changing another feature's files or inventing deployed schema. Each receiving owner should answer in the named source contract, then update and test files they own. Feature 112 will integrate only after the answer is approved and testable.

| Handoff | Receiving owner | Blocks |
|---|---|---|
| [Booking schema, safe read and commands](booking-operations.md) | Features 040/050/060 and database owner | Booking queue/detail/decision, booking check-in, calendar booking labels |
| [Drop-in owner commands and roster](drop-in-operations.md) | Features 080/081 and database owner | Session CRUD, roster, waitlist behavior and QR check-in |
| [Voucher ownership and schema](voucher-operations.md) | Features 111/050 and database owner | Voucher list/create/edit/toggle |
| [Statistics source and metrics](statistics.md) | Database owner and Feature 120 aggregation owner | Revenue, occupancy, peak hours |
| [Notification delivery contract](notifications.md) | Feature 100 | Booking/drop-in event delivery |
| [Locked venue court Rules](venue-lock-rules.md) | Feature 111 Rules owner | Server-enforced venue/court availability |

## Prompt usage

Each handoff ends with a ready-to-send prompt for the receiving feature owner. The prompt requests contract/source reconciliation and implementation only inside that feature's ownership. No foreign code was changed when these requests were prepared.
