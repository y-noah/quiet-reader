param([ValidateSet('THEPAPER','ITHOME')][string]$Source='THEPAPER',[string]$Label='verified')
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid report label'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$wifi=(& $adb -s $serial shell settings get global wifi_on).Trim()
$mobile=(& $adb -s $serial shell settings get global mobile_data).Trim()
if($wifi -notmatch '^[01]$' -or $mobile -notmatch '^[01]$'){throw 'Unknown original radio state'}
$name='live-image-prefetch-'+$Source.ToLowerInvariant()+'-'+$Label
$report=Join-Path $projectRoot "artifacts/local-ai/$name-api36.txt"
try{
    # The probe requires a newly acquired image. Already cached boards may legitimately
    # provide no qualifying sample; never erase user/derived caches to force a pass.
    $result=& $adb -s $serial shell am instrument -w -e mode live-image-prefetch -e source $Source app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $result | Tee-Object -FilePath $report
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'checks=7 failures=0'){throw 'Public image prefetch verification failed; inspect preserved report'}
    $line=$result | Where-Object {$_ -match '^screenshot=(/storage/emulated/0/Android/data/app.quietreader/files/live-image-prefetch/[A-Z]+-\d+\.png)$'} | Select-Object -Last 1
    if($line -notmatch '^screenshot=(/storage/emulated/0/Android/data/app.quietreader/files/live-image-prefetch/[A-Z]+-\d+\.png)$'){throw 'Screenshot evidence missing'}
    $remote=$Matches[1]
    & $adb -s $serial pull $remote (Join-Path $projectRoot "artifacts/local-ai/$name.png") *> $null
    if($LASTEXITCODE -ne 0){throw 'Screenshot retrieval failed'}
}finally{
    if($wifi -eq '1'){& $adb -s $serial shell svc wifi enable}else{& $adb -s $serial shell svc wifi disable}
    if($mobile -eq '1'){& $adb -s $serial shell svc data enable}else{& $adb -s $serial shell svc data disable}
    $restoredWifi=(& $adb -s $serial shell settings get global wifi_on).Trim()
    $restoredMobile=(& $adb -s $serial shell settings get global mobile_data).Trim()
    if($restoredWifi -ne $wifi -or $restoredMobile -ne $mobile){throw 'Original radio settings could not be restored'}
}
