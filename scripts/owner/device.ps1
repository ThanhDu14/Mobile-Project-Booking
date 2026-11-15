param(
    [ValidateSet('inspect','tap','type','capture','back','launch','swipe','scrollTop')][string]$Action = 'inspect',
    [string]$Target = 'screen',
    [string]$Value = '',
    [string]$Device = '10ADBY2GH30038C',
    [switch]$IncludeSystem,
    [switch]$KeepKeyboard
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$package = 'com.example.booking_app'
$evidence = Join-Path $PSScriptRoot '../../docs/validation/owner-110-112/screenshots'
function Read-OwnerScreen {
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $dumpResult = (& $adb -s $Device shell uiautomator dump /sdcard/owner-window.xml) -join "`n"
        if ($LASTEXITCODE -eq 0 -and $dumpResult -match 'dumped to') {
            [xml]$document = (& $adb -s $Device shell cat /sdcard/owner-window.xml) -join "`n"
            if ($LASTEXITCODE -eq 0 -and $document.DocumentElement) { return $document }
        }
        Start-Sleep -Milliseconds 300
    }
    throw 'Device UI dump is not ready; no control was tapped.'
}
switch ($Action) {
    'launch' { & $adb -s $Device shell am start -n "$package/.MainActivity" }
    'back' { & $adb -s $Device shell input keyevent KEYCODE_BACK }
    'swipe' { & $adb -s $Device shell input swipe 500 1800 500 600 450 }
    'scrollTop' { & $adb -s $Device shell input swipe 500 600 500 1800 450 }
    { $_ -in 'tap','type' } {
        $document = Read-OwnerScreen
        $node = $document.SelectNodes('//node') | Where-Object { $_.'resource-id' -eq "${package}:id/$Target" -or $_.'resource-id' -eq $Target -or $_.text -eq $Target -or $_.'content-desc' -eq $Target } | Select-Object -First 1
        if (-not $node) { throw "Control not found: $Target" }
        $boundsMatch = [regex]::Match($node.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $boundsMatch.Success) { throw 'Invalid bounds' }
        $x = [int](([int]$boundsMatch.Groups[1].Value+[int]$boundsMatch.Groups[3].Value)/2)
        $y = [int](([int]$boundsMatch.Groups[2].Value+[int]$boundsMatch.Groups[4].Value)/2)
        Write-Output "Device $Action $Target at $x,$y"
        & $adb -s $Device shell input tap $x $y
        if ($Action -eq 'type') {
            if ($Value -notmatch '^[a-zA-Z0-9 .,:@_/-]*$') { throw 'Only simple test input is accepted' }
            & $adb -s $Device shell input keyevent KEYCODE_MOVE_END
            $deleteKeys = @(1..([Math]::Min(150, $node.text.Length + 1)) | ForEach-Object { 'KEYCODE_DEL' })
            & $adb -s $Device shell input keyevent @deleteKeys
            & $adb -s $Device shell input text $Value.Replace(' ','%s')
            $imeState = (& $adb -s $Device shell dumpsys input_method) -join "`n"
            if (-not $KeepKeyboard -and $imeState -match 'mInputShown=true|mIsInputViewShown=true|isInputViewShown=true') {
                & $adb -s $Device shell input keyevent KEYCODE_BACK
            }
        }
    }
    'capture' {
        if ($Target -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid capture name' }
        New-Item -ItemType Directory -Force -Path $evidence | Out-Null
        & $adb -s $Device shell screencap -p /sdcard/owner-evidence.png
        & $adb -s $Device pull /sdcard/owner-evidence.png (Join-Path $evidence "$Target.png")
    }
}
if ($Action -ne 'capture') {
    $document = Read-OwnerScreen
    $document.SelectNodes('//node') | Where-Object { ($_.text -or $_.'content-desc' -or $_.class -eq 'android.widget.EditText') -and ($IncludeSystem -or $_.package -eq $package) -and $_.password -ne 'true' } |
        ForEach-Object { '{0} | {1} | {2} | {3}' -f $_.text, $_.'resource-id', $_.'content-desc', $_.bounds }
}
