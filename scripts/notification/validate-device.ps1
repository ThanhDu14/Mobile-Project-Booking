param(
    [ValidateSet('phone-light','phone-dark','tablet-light','tablet-dark')]
    [string]$Variant = 'phone-light',
    [string]$Device = '10ADBY2GH30038C',
    [switch]$SkipInstall,
    [string]$EvidenceRoot = 'specs/100-notification/validation/device'
)
$ErrorActionPreference = 'Stop'
$adb = 'C:/Users/ACER/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$package = 'com.example.booking_app.adminlocal'
$evidence = Join-Path (Join-Path (Get-Location) $EvidenceRoot) $Variant
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if (-not $SkipInstall) {
    $appMetadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if ($appMetadata.applicationId -ne $package) { throw 'Build isolated adminLocalAppId APK before running this script' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
    & $adb -s $Device install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation install failed' }
}
$density = (& $adb -s $Device shell wm density) -join "`n"
$previousOverride = if ($density -match 'Override density:\s*(\d+)') { $Matches[1] } else { $null }
$theme = if ($Variant.EndsWith('dark')) { 'dark' } else { 'light' }
try {
    if ($Variant.StartsWith('tablet')) {
        & $adb -s $Device shell wm density 240
        if ($LASTEXITCODE -ne 0) { throw 'Cannot apply tablet density' }
    }
    & $adb -s $Device shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Device shell pm grant $package android.permission.POST_NOTIFICATIONS
    $result = (& $adb -s $Device shell am instrument -w -r -e class com.example.booking_app.feature.notification.NotificationUiTest -e notificationTheme $theme -e localSupabaseKey notification-local-placeholder "$package.test/com.example.booking_app.feature.notification.NotificationTestRunner") -join "`n"
    [System.IO.File]::WriteAllText((Join-Path $evidence 'instrumentation.log'), $result)
    $result
    if ($result -notmatch 'OK \(9 tests\)') { throw 'Notification UI instrumentation did not pass all nine tests' }
    # Query names produced by the test instead of assuming the OEM rounds dp identically.
    $remote = "/sdcard/Android/data/$package/files"
    $files = & $adb -s $Device shell ls "$remote/notification-*dp-$theme-*.png"
    foreach ($file in $files) {
        if ($file -match '^/sdcard/Android/data/com[.]example[.]booking_app[.]adminlocal/files/notification-(?<width>[0-9]+)dp-(light|dark)-(inbox|settings)(-shell|-extra|-cache)?[.]png$') {
            if (([int]$Matches.width -ge 600) -ne $Variant.StartsWith('tablet')) { continue }
            $pull = Start-Process -FilePath $adb -ArgumentList @('-s', $Device, 'pull', $file, $evidence) -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $evidence 'pull.stdout.log') -RedirectStandardError (Join-Path $evidence 'pull.stderr.log')
            if ($pull.ExitCode -ne 0) { throw 'Screenshot pull failed' }
        }
    }
} finally {
    if ($null -ne $previousOverride) { & $adb -s $Device shell wm density $previousOverride }
    else { & $adb -s $Device shell wm density reset }
}
