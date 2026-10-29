param([string]$Label='verified')
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid label'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only emulator allowed'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$wifi=(& $adb -s $serial shell settings get global wifi_on).Trim()
$mobile=(& $adb -s $serial shell settings get global mobile_data).Trim()
if($wifi -notmatch '^[01]$' -or $mobile -notmatch '^[01]$'){throw 'Unknown original radio state'}
$id=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$report=Join-Path $projectRoot "artifacts/local-ai/image-offline-restart-$Label-api36.txt"
try{
    $prepare=& $adb -s $serial shell am instrument -w -e mode image-offline-prepare -e probeId $id app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $prepare | Tee-Object -FilePath $report
    if($LASTEXITCODE -ne 0 -or ($prepare -join "`n") -notmatch 'failures=0'){throw 'Prepare failed'}
    & $adb -s $serial shell am force-stop app.quietreader
    $remaining=& $adb -s $serial shell pidof app.quietreader
    if($remaining){throw 'Target process still running'}
    'PASS target process absent after actual force-stop' | Tee-Object -FilePath $report -Append
    & $adb -s $serial shell svc wifi disable
    & $adb -s $serial shell svc data disable
    $restore=& $adb -s $serial shell am instrument -w -e mode image-offline-restore -e probeId $id app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $restore | Tee-Object -FilePath $report -Append
    if($LASTEXITCODE -ne 0 -or ($restore -join "`n") -notmatch 'failures=0'){throw 'Offline restore failed'}
}finally{
    if($wifi -eq '1'){& $adb -s $serial shell svc wifi enable}else{& $adb -s $serial shell svc wifi disable}
    if($mobile -eq '1'){& $adb -s $serial shell svc data enable}else{& $adb -s $serial shell svc data disable}
}
