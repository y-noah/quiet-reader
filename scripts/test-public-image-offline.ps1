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
$report=Join-Path $projectRoot "artifacts/local-ai/public-image-offline-$Label-api36.txt"
try{
    & $adb -s $serial shell svc wifi enable
    $online=& $adb -s $serial shell am instrument -w -e mode live-note-target-ui -e source THEPAPER -e target https://www.thepaper.cn/newsDetail_forward_34324278 app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $online | Tee-Object -FilePath $report
    if($LASTEXITCODE -ne 0 -or ($online -join "`n") -notmatch 'offlineAtRequest=false' -or ($online -join "`n") -notmatch 'readable=1/1 probeErrors=0'){throw 'Public online preparation failed'}
    & $adb -s $serial shell am force-stop app.quietreader
    if((& $adb -s $serial shell pidof app.quietreader)){throw 'Target process still alive'}
    'PASS process absent before public offline restore' | Tee-Object -FilePath $report -Append
    & $adb -s $serial shell svc wifi disable
    & $adb -s $serial shell svc data disable
    $offline=& $adb -s $serial shell am instrument -w -e mode live-note-target-ui -e source THEPAPER -e target https://www.thepaper.cn/newsDetail_forward_34324278 app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $offline | Tee-Object -FilePath $report -Append
    if($LASTEXITCODE -ne 0 -or ($offline -join "`n") -notmatch 'offlineAtRequest=true' -or ($offline -join "`n") -notmatch 'readable=1/1 probeErrors=0' -or ($offline -join "`n") -notmatch 'actualBodyImageLoaded=true'){throw 'Public offline image restore failed'}
    $line=$offline | Where-Object {$_ -match '^  screenshot=(/storage/[^\s]+\.png)$'} | Select-Object -Last 1
    if($line -match '^  screenshot=(/storage/[^\s]+\.png)$'){& $adb -s $serial pull $Matches[1] (Join-Path $projectRoot "artifacts/local-ai/public-image-offline-$Label.png") *> $null}
}finally{
    if($wifi -eq '1'){& $adb -s $serial shell svc wifi enable}else{& $adb -s $serial shell svc wifi disable}
    if($mobile -eq '1'){& $adb -s $serial shell svc data enable}else{& $adb -s $serial shell svc data disable}
}
