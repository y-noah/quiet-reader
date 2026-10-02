$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5556'
if ((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only the isolated emulator is permitted.'}
if ((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-test'){throw 'The connected emulator is not this project test AVD.'}
$wifi=(& $adb -s $serial shell settings get global wifi_on).Trim()
$data=(& $adb -s $serial shell settings get global mobile_data).Trim()
try {
    & $adb -s $serial shell am force-stop app.quietreader
    & $adb -s $serial shell svc wifi disable
    & $adb -s $serial shell svc data disable
    $offline=$false
    for($attempt=0;$attempt -lt 20;$attempt++) {
        $network=(& $adb -s $serial shell dumpsys connectivity | Select-String 'Active default network').Line
        if($network -match 'Active default network: (none|null|-1)'){$offline=$true;break}
        Start-Sleep -Milliseconds 500
    }
    $network | Write-Output
    if(!$offline){throw 'The emulator still has an active default network; offline test not valid.'}
    $report=& $adb -s $serial shell am instrument -w -e mode offline app.quietreader.test/app.quietreader.ReleaseInstrumentation
    $exitCode=$LASTEXITCODE
    $report | Write-Output
    if($exitCode -ne 0 -or !($report -match '^PASS \d+ offline/restart assertions')){throw 'Offline persistence assertions failed.'}
} finally {
    if($wifi -ne '0'){& $adb -s $serial shell svc wifi enable}
    if($data -ne '0'){& $adb -s $serial shell svc data enable}
}
