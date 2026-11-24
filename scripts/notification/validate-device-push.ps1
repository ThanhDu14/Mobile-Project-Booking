param(
    [string]$Device = '10ADBY2GH30038C',
    [string]$EvidenceRoot = 'specs/100-notification/validation/continuation-2026-11-24/device/push-permission'
)
$ErrorActionPreference = 'Stop'
$adb = 'C:/Users/ACER/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$package = 'com.example.booking_app.adminlocal'
$permission = 'android.permission.POST_NOTIFICATIONS'
$evidence = Join-Path (Get-Location) $EvidenceRoot
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$before = (& $adb -s $Device shell dumpsys package $package) -join "`n"
if ($before -notmatch 'android[.]permission[.]POST_NOTIFICATIONS:\s*granted=(true|false),\s*flags=\[([^\]]*)\]') { throw 'Isolated app/permission unavailable' }
$wasGranted = $Matches[1] -eq 'true'
$flags = $Matches[2]
$density = (& $adb -s $Device shell wm density) -join "`n"
[IO.File]::WriteAllText((Join-Path $evidence 'device-before.txt'), "density=$density`npermissionGranted=$wasGranted`nflags=$flags")
function Run-Case([string]$Name, [string]$Classes, [int]$Expected) {
    $output = (& $adb -s $Device shell am instrument -w -r -e class $Classes -e localSupabaseKey notification-local-placeholder "$package.test/com.example.booking_app.feature.notification.NotificationTestRunner") -join "`n"
    [IO.File]::WriteAllText((Join-Path $evidence "$Name.log"), $output)
    if ($output -notmatch "OK \($Expected tests?\)" -or $output -match 'INSTRUMENTATION_STATUS_CODE: -[234]|INSTRUMENTATION_FAILED') { throw "$Name failed; inspect $evidence/$Name.log" }
    Write-Output "$Name passed ($Expected)"
}
try {
    & $adb -s $Device shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Device shell pm grant $package $permission
    Run-Case 'channels-renderer-handler' 'com.example.booking_app.feature.notification.NotificationChannelsTest,com.example.booking_app.feature.notification.NotificationPushRendererTest,com.example.booking_app.feature.notification.NotificationPushHandlerTest#injectedPayloadPostsGenericContentAndRejectsOtherAccountLogoutAndNotificationBlock' 6
    & $adb -s $Device shell pm revoke $package $permission
    Run-Case 'denied-handler' 'com.example.booking_app.feature.notification.NotificationPushHandlerTest#deniedPermissionDoesNotPostInjectedPayload' 1
    foreach ($method in @('grantedPermissionHidesNoticeWithoutChangingPreferences', 'deniedPermissionKeepsPreferencesAndInboxAndRefreshesAfterSystemSettingsGrant')) {
        & $adb -s $Device shell pm revoke $package $permission
        & $adb -s $Device shell pm clear-permission-flags $package $permission user-set user-fixed
        Run-Case $method "com.example.booking_app.feature.notification.NotificationPermissionUiTest#$method" 1
    }
    $remote = "/sdcard/Android/data/$package/files"
    foreach ($name in @('denied', 'denied-inbox', 'granted-prompt', 'granted-return')) {
        & $adb -s $Device pull "$remote/notification-permission-$name.png" $evidence
        if ($LASTEXITCODE -ne 0) { throw 'Cannot pull permission screenshot' }
    }
} finally {
    $operation = if ($wasGranted) { 'grant' } else { 'revoke' }
    & $adb -s $Device shell pm $operation $package $permission
    & $adb -s $Device shell pm clear-permission-flags $package $permission user-set user-fixed
    if ($flags -match 'USER_SET') { & $adb -s $Device shell pm set-permission-flags $package $permission user-set }
    if ($flags -match 'USER_FIXED') { & $adb -s $Device shell pm set-permission-flags $package $permission user-fixed }
    $after = (& $adb -s $Device shell dumpsys package $package) -join "`n"
    $line = ($after -split "`n" | Where-Object { $_ -match 'android[.]permission[.]POST_NOTIFICATIONS: granted=' }) -join "`n"
    [IO.File]::WriteAllText((Join-Path $evidence 'device-after.txt'), "density=$((& $adb -s $Device shell wm density) -join ' ')`n$line")
}
