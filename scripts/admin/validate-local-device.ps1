param(
    [ValidateSet('phone-light','phone-dark','tablet-light','tablet-dark')]
    [string]$Variant = 'phone-light',
    [string]$Device = '10ADBY2GH30038C',
    [ValidatePattern('^[a-zA-Z0-9-]*$')]
    [string]$EvidenceRun = '',
    [switch]$SkipInstall,
    [string]$AppPackage = 'com.example.booking_app.adminlocal',
    [string]$StatusFile = '.gradle/admin-local-status.json',
    [string]$Deno = 'C:/Users/ACER/AppData/Local/npm-cache/_npx/05b6ef7b13673c57/node_modules/deno/deno.exe'
)
$ErrorActionPreference = 'Stop'
$captureVariant = if ($EvidenceRun) { "$EvidenceRun/$Variant" } else { $Variant }
$adminRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $adminRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$status = Get-Content -LiteralPath $StatusFile -Raw | ConvertFrom-Json
if ($status.API_URL -ne 'http://127.0.0.1:54321') { throw 'Only local Supabase is permitted' }
$env:SUPABASE_URL = $status.API_URL
$env:SUPABASE_SERVICE_ROLE_KEY = $status.SERVICE_ROLE_KEY
$env:FIREBASE_PROJECT_ID = 'demo-admin-repair'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:18080'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:19099'
$rulesPayload = @{ rules = @{ files = @(@{ content = [System.IO.File]::ReadAllText((Join-Path $adminRoot 'firestore.rules')) }) } } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Put -Uri 'http://127.0.0.1:18080/emulator/v1/projects/demo-admin-repair:securityRules' -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($rulesPayload)) | Out-Null
& $Deno run --allow-env --allow-net --no-lock supabase/functions/admin-ui-fixture.local.ts
if ($LASTEXITCODE -ne 0) { throw 'Local fixture failed' }
foreach ($port in @(18080,19099,54321)) {
    & $adb -s $Device reverse "tcp:$port" "tcp:$port"
    if ($LASTEXITCODE -ne 0) { throw 'adb reverse failed' }
}
if (-not $SkipInstall) {
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
    & $adb -s $Device install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test APK install failed' }
}
$density = (& $adb -s $Device shell wm density) -join "`n"
$previousOverride = if ($density -match 'Override density:\s*(\d+)') { $Matches[1] } else { $null }
try {
    if ($Variant.StartsWith('tablet')) {
        & $adb -s $Device shell wm density 240
        if ($LASTEXITCODE -ne 0) { throw 'Tablet density override failed' }
    }
    & $adb -s $Device shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Device shell wm dismiss-keyguard
    $arguments = @('-s',$Device,'shell','am','instrument','-w','-r','-e','package','com.example.booking_app.feature.admin',
        '-e','localSupabaseKey',$status.PUBLISHABLE_KEY,'-e','captureVariant',$captureVariant,
        "${AppPackage}.test/com.example.booking_app.feature.admin.AdminLocalTestRunner")
    $result = & $adb @arguments 2>&1
    $result | Tee-Object -FilePath ".gradle/admin-device-$Variant.log"
    if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Admin instrumentation failed: $Variant"
    }
    New-Item -ItemType Directory -Force -Path "specs/120-admin/validation/screenshots/$captureVariant" | Out-Null
    # adb emits successful transfer progress on stderr. Capture it as text while
    # still checking its exit code, including when this script is redirected.
    $ErrorActionPreference = 'Continue'
    $pullOutput = & $adb -s $Device pull "/sdcard/Android/data/$AppPackage/files/admin-validation/$captureVariant/." "specs/120-admin/validation/screenshots/$captureVariant" 2>&1
    $pullExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $pullOutput | ForEach-Object { $_.ToString() }
    if ($pullExit -ne 0) { throw 'Screenshot pull failed' }
} finally {
    if ($Variant.StartsWith('tablet')) {
        if ($previousOverride) { & $adb -s $Device shell wm density $previousOverride }
        else { & $adb -s $Device shell wm density reset }
    }
}
