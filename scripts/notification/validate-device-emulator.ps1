param(
    [string]$Serial = '10ADBY2GH30038C',
    [int]$FirestorePort = 8080,
    [int]$AuthPort = 9099,
    [string]$ProducerSecret,
    [ValidateSet('all', 'group', 'payment')]
    [string]$ProducerPhase = 'all',
    [switch]$SkipInstall
)

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
    $ports = [ordered]@{ '8080' = $FirestorePort; '9099' = $AuthPort }
    if ($ProducerSecret) { $ports['18765'] = 18765 }
    foreach ($port in $ports.Keys) {
        $endpoint = "tcp:$port"
        $existing = $original | Where-Object { $_ -match "\s$endpoint\s" }
        if ($existing) { throw "USB reverse mapping already exists for $endpoint" }
        & $adb -s $Serial reverse --no-rebind $endpoint "tcp:$($ports[$port])"
        if ($LASTEXITCODE -ne 0) { throw "Cannot reverse $endpoint" }
        $added += $endpoint
    }
    if (!$SkipInstall) {
        & $adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Cannot install isolated app' }
        & $adb -s $Serial install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Cannot install instrumentation' }
    }
    $producerArgs = if ($ProducerSecret) {
        @('-e', 'notificationProducerSecret', $ProducerSecret, '-e', 'notificationProducerPhase', $ProducerPhase)
    } else { @() }
    $output = (& $adb -s $Serial shell am instrument -w -r -e class com.example.booking_app.feature.notification.NotificationEmulatorDeviceTest -e notificationEmulator true -e localSupabaseKey notification-local-placeholder @producerArgs $runner) -join "`n"
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
