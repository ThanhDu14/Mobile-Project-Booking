# AI log: booking notifications, 24/11/2026

Tool: Codex, speckit-implement. User requested Feature 100 continuation from 36/56 with booking tests, BOOKING_NEW source discovery, reminder scheduling/deduplication and local verification. User explicitly approved B1 while deferring the missing payment source, and approved B2's once-per-booking/late/reschedule limits.

Used approved shared enqueue/worker code, added a B1 helper without inventing the absent payment transaction, implemented reminder scheduling and delivery-time stale-source checks. Preserved nanosecond REST cursor values, added the matching index and prepared an unapplied cron migration. UI/routes unchanged. T015/T016/T019 verified, progress39/56; T018 remains blocked on the actual payment source. Other unapproved source contracts remain open.

Evidence: specs/100-notification/validation/booking-2026-11-24.md and its log/hash folder. Booking emulator14 plus positive-delivery1, shared regressions22 and build/lint pass. Docker unavailable, so Supabase local/cron SQL unverified. No real FCM, hosted migration, deploy, commit or push. Coordination requests are shown directly in conversation.
