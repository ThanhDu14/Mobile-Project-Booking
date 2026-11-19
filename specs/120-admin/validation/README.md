# Admin device evidence

Fake local data only. Source images are in [../ui](../ui/README.md). Step 8 is an `ERR_CONNECTION_REFUSED` page and is excluded as a design reference. No separate user-detail layout has been inferred from that image; existing actions expose schema-backed account information in a dialog.

Device: Vivo V2247 / Android 15, 1080×2388 px, physical density 440. Phone variants use the original density (about 393 dp wide). Tablet variants temporarily use density 240 (720 dp wide) to execute `layout-w600dp`; they are responsive-layout checks on the same phone, not physical tablet hardware validation. Screenshots preserve device pixels, system bars and the device's edge-panel handle. No image retouching.

Each variant runs the entire admin instrumentation package: four inflation smoke tests and one real MainActivity/Fragment/repository interaction test against Firebase Emulator + Supabase local. Only the interaction test provides end-to-end evidence. It downloads both signed documents, restores a rejection draft, approves a profile, observes the APPROVED queue, dismisses a report and observes the closed queue, searches by email, adds an amenity, edits/restores both category collections, sends broadcast and locks/unlocks a venue. Server integration tests separately cover rejection, expiry/privacy, races, claim failures/recovery, revoke, voucher and mid-batch retry.

| Source step | Screenshot name | Comparison / remaining difference |
|---|---|---|
| 1 | `01-home` | Same pending/report cards and metrics grid. Stored totals have accurate labels; real selected date/source/update time replace sample metrics/history. No fabricated recent-history rows. |
| 2 | `02-queue`, `05-approved` | Queue and status filters use real local records; Material chip/icon typography differs from canvas artwork. |
| 3 | `03-detail`, `03-documents` | Real email, district, GPS and both document cards. Each click requests a fresh signed URL; raw paths are never opened. |
| 4 | `04-rejection-draft` | Bottom sheet retains entered reason after close/reopen. Quick-reason chips scroll horizontally on narrow screens. |
| 5 | `05-reports` | Both open/closed tabs remain visible. Filters wrap with device width; Material toggle border/selection differs from the canvas pill/shadow. No invented related-report counts. |
| 6 | `06-report-detail` | Actual reporter, content and target. Unsupported related-count sample is hidden; no illustrative person/venue substitutes. |
| 7 | `07-users-search` | Search and account filters with real returned identity/email/phone. Account details use the existing action dialog; step 8 has no valid design. |
| 9 | `09-venue-unlock` | Real venue status and ACTIVE/HIDDEN choices, reached through tools → venue lookup. Tablet sheet width and lookup context differ from the phone canvas. The 12/11 captures show the old notification promise; R120-18 removed it. Fresh captures in `screenshots/2026-11-13-permission` confirm the correction. FR-009 defines no unlock-notification event. |
| 10 | `10-amenities` | Correct collection, add/edit controls, extra venue-management navigation. Counts are omitted without an authoritative count source. |
| 11 | `11-broadcast` | Existing form and confirmation with actual local ACTIVE recipient handling; retry uses stable request identity. |
| 12 | `12-voucher-form`, `12-voucher-validation-fields` | Added required year/timezone date and usage-limit fields; form is longer than the source. Long ISO example hints ellipsize on phone; inputs accept full values and validation rejects malformed dates. |

The valid canvas PNGs are visual references, not pixel-exact acceptance baselines for all four configurations. Generic Android/Material icons, typography and spacing still differ from the artwork. The missing step 8 prevents design acceptance of a dedicated user-detail screen.

Final results and screenshot directories are linked from [repair-validation.md](../repair-validation.md). `regressions/` retains original failure screenshots; `results/` contains captured build/test output. Transient toasts are allowed to expire before final comparison captures.

The [13/11 permission investigation](../permission-denied-2026-11-13.md) adds a second actual interaction case per variant: PLAYER stays in player navigation, never instantiates admin query ViewModels, and is denied a deliberate private-data read. Its four complete package runs passed 24/24 and generated 60 new images in a dated directory, preserving the prior 56-image baseline.
