param(
    [ValidateSet('Fake', 'Emulator', 'Affected', 'Booking', 'Continuation')][string]$Suite = 'Fake',
    [string]$DenoExecutable = 'deno'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repoRoot
try {
    if ($Suite -eq 'Fake') {
        & $DenoExecutable test --allow-env supabase/functions/_shared/notification-worker.test.ts supabase/functions/notification-producers.test.ts supabase/functions/admin-broadcast-notification/handler.test.ts supabase/functions/admin-review-application/handler.test.ts supabase/functions/owner-approve-booking/handler.test.ts supabase/functions/owner-reject-booking/handler.test.ts supabase/functions/owner-cancel-booking/handler.test.ts supabase/functions/booking-timeout/handler.test.ts supabase/functions/_shared/waitlist.regression.test.ts
        exit $LASTEXITCODE
    }
    if ($env:FIREBASE_PROJECT_ID -notlike 'demo-*' -or
        $env:FIRESTORE_EMULATOR_HOST -notmatch '^(127\.0\.0\.1|localhost):\d+$' -or
        $env:FIREBASE_AUTH_EMULATOR_HOST -notmatch '^(127\.0\.0\.1|localhost):\d+$') {
        throw 'Emulator suites require an explicit demo-* project and localhost Auth/Firestore hosts.'
    }
    if ($Suite -eq 'Booking') {
        & $DenoExecutable test --allow-env --allow-net supabase/functions/booking-reminders/handler.test.ts
        exit $LASTEXITCODE
    }
    if ($Suite -eq 'Continuation') {
        & $DenoExecutable test --allow-env --allow-net supabase/functions/register-drop-in/index.test.ts supabase/functions/register-drop-in/waitlist.test.ts supabase/functions/review/ supabase/functions/chat-send/index.test.ts supabase/functions/_shared/chats.test.ts supabase/functions/cancel-drop-in/waitlist.test.ts supabase/functions/dropin-close/index.test.ts
        exit $LASTEXITCODE
    }
    & $DenoExecutable test --allow-env --allow-net supabase/functions/_shared/notification-emulator.test.ts
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Suite -eq 'Affected') {
        & $DenoExecutable test --allow-env --allow-net supabase/functions/_shared/chats.test.ts
        exit $LASTEXITCODE
    }
    & $DenoExecutable test --allow-env --allow-net --filter 'F100 T054' supabase/functions/delete-account/handler.test.ts
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $DenoExecutable test --allow-env --allow-net supabase/functions/_shared/waitlist.test.ts supabase/functions/_shared/chats.test.ts supabase/functions/chat-send/index.test.ts supabase/functions/dropin-close/index.test.ts supabase/functions/cancel-drop-in/waitlist.test.ts
    exit $LASTEXITCODE
} finally { Pop-Location }
