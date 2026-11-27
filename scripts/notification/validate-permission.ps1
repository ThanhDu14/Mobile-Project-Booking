param([string]$Device = '10ADBY2GH30038C', [switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
$adb = 'C:/Users/ACER/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$package = 'com.example.booking_app.adminlocal'
$evidence = Join-Path (Get-Location) 'specs/100-notification/validation/continuation-device'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if (-not $SkipInstall) {
    $metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $package) { throw 'Requires isolated adminLocalAppId build' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
    & $adb -s $Device install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
}
try {
    foreach ($method in @('deniedPermissionKeepsPreferencesAndInboxAndRefreshesAfterSystemSettingsGrant', 'grantedPermissionHidesNoticeWithoutChangingPreferences')) {
        # Revocation must happen before instrumentation starts; Android terminates the app on revoke.
        & $adb -s $Device shell am force-stop $package
        & $adb -s $Device shell pm revoke $package android.permission.POST_NOTIFICATIONS
        if ($LASTEXITCODE -ne 0) { throw 'Could not revoke permission' }
        & $adb -s $Device shell pm clear-permission-flags $package android.permission.POST_NOTIFICATIONS user-set user-fixed
        if ($LASTEXITCODE -ne 0) { throw 'Could not reset isolated test permission flags' }
        & $adb -s $Device shell input keyevent KEYCODE_WAKEUP
        $result = (& $adb -s $Device shell am instrument -w -r -e class "com.example.booking_app.feature.notification.NotificationPermissionUiTest#$method" -e localSupabaseKey notification-local-placeholder "$package.test/com.example.booking_app.feature.notification.NotificationTestRunner") -join "`n"
        [System.IO.File]::WriteAllText((Join-Path $evidence "$method.log"), $result)
        $result
        if ($result -notmatch 'OK \(1 test\)') { throw "Permission test failed: $method" }
    }
    $files = & $adb -s $Device shell ls "/sdcard/Android/data/$package/files/notification-permission-*.png"
    foreach ($file in $files) {
        if ($file -match '^/sdcard/Android/data/com[.]example[.]booking_app[.]adminlocal/files/notification-permission-[a-z-]+[.]png$') {
            $pull = Start-Process -FilePath $adb -ArgumentList @('-s', $Device, 'pull', $file, $evidence) -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $evidence 'pull.stdout.log') -RedirectStandardError (Join-Path $evidence 'pull.stderr.log')
            if ($pull.ExitCode -ne 0) { throw 'Screenshot pull failed' }
        }
    }
} finally {
    # Prior notification validation leaves this isolated package granted. Never touch the main app.
    & $adb -s $Device shell pm grant $package android.permission.POST_NOTIFICATIONS
}
