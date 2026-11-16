$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:FIREBASE_PROJECT_ID = 'demo-owner-concurrency'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
$denoPath = 'C:/Users/ACER/AppData/Local/npm-cache/_npx/05b6ef7b13673c57/node_modules/deno/deno.exe'
$testCommand = "$denoPath test --allow-env --allow-net supabase/functions/owner-applications/handler.test.ts supabase/functions/owner-docs/handler.test.ts supabase/functions/owner-create-session/handler.test.ts supabase/functions/manage-slot-blocks/handler.test.ts supabase/functions/owner-reject-booking/handler.test.ts supabase/functions/owner-cancel-booking/handler.test.ts supabase/functions/owner-transactions.emulator.test.ts"
& npx.cmd --yes firebase-tools@15 emulators:exec --project demo-owner-concurrency --only auth,firestore $testCommand
exit $LASTEXITCODE
