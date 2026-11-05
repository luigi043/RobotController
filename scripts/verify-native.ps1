param([ValidatePattern('^emulator-[0-9]+$')][string]$Serial = 'emulator-5554', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
function Invoke-RobotAdb([string[]]$Arguments) {
    & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'An emulator is required. No physical device is allowed.' }
Push-Location $repo
try {
    if (!$SkipBuild) {
        ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw 'Build or unit tests failed.' }
    }
    Invoke-RobotAdb -Arguments @('install','-r','app/build/outputs/apk/debug/app-debug.apk')
    Invoke-RobotAdb -Arguments @('install','-r','-t','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    # Only this app's synthetic emulator state is cleared.
    Invoke-RobotAdb -Arguments @('shell','pm','clear','com.robotcontroller.debug')
    $result = & $adb -s $Serial shell am instrument -w -r com.robotcontroller.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Set-Content docs/verification/evidence/instrumentation.txt
    $result | Write-Output
    Invoke-RobotAdb -Arguments @('pull','/sdcard/Android/data/com.robotcontroller.debug/files/verification/.','docs/verification/native')
    Copy-Item app/build/test-results/testDebugUnitTest/TEST-*.xml docs/verification/evidence
    if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw 'Native walkthrough failed. Partial evidence retained.' }
} finally { Pop-Location }
