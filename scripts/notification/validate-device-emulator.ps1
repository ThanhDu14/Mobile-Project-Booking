param([string]$Serial = '10ADBY2GH30038C')

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$package = 'com.example.booking_app.adminlocal'
$runner = "$package.test/com.example.booking_app.feature.notification.NotificationTestRunner"
$metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build the isolated .adminlocal APK first' }

$added = @()
try {
    $original = & $adb -s $Serial reverse --list
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list USB reverse mappings' }
    foreach ($port in @(8080, 9099)) {
        $endpoint = "tcp:$port"
        $existing = $original | Where-Object { $_ -match "\s$endpoint\s" }
        if ($existing) { throw "USB reverse mapping already exists for $endpoint" }
        & $adb -s $Serial reverse --no-rebind $endpoint $endpoint
        if ($LASTEXITCODE -ne 0) { throw "Cannot reverse $endpoint" }
        $added += $endpoint
    }
    & $adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Cannot install isolated app' }
    & $adb -s $Serial install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Cannot install instrumentation' }
    $output = (& $adb -s $Serial shell am instrument -w -r -e class com.example.booking_app.feature.notification.NotificationEmulatorDeviceTest -e notificationEmulator true -e localSupabaseKey notification-local-placeholder $runner) -join "`n"
    $output
    if ($LASTEXITCODE -ne 0 -or $output -notmatch 'OK \(1 test\)' -or $output -match 'INSTRUMENTATION_STATUS_CODE: -[234]') {
        throw 'Device repository test failed or skipped'
    }
} finally {
    foreach ($endpoint in $added) {
        & $adb -s $Serial reverse --remove $endpoint
    }
    & $adb -s $Serial reverse --list
}
