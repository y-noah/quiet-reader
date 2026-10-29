param([ValidateSet('1.0','1.5')][string]$Font='1.5',[string]$Label='verified',[switch]$WhileImageLoads)
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid report label'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$originalFont=(& $adb -s $serial shell settings get system font_scale).Trim()
if($originalFont -ne 'null' -and $originalFont -notmatch '^\d+(\.\d+)?$'){throw 'Unknown original font scale'}
$mode=if($WhileImageLoads){'reader-rotation-loading'}else{'reader-rotation'}
$name=$mode+'-'+$Font.Replace('.','')+'-'+$Label
try{
    & $adb -s $serial shell settings put system font_scale $Font
    $result=& $adb -s $serial shell am instrument -w -e mode $mode app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $result | Tee-Object -FilePath (Join-Path $projectRoot "artifacts/local-ai/$name-api36.txt")
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'checks=\d+ failures=0'){throw 'Reader rotation verification failed'}
    foreach($shape in @('portrait','landscape')){
        & $adb -s $serial pull "/sdcard/Android/data/app.quietreader/files/$mode-$shape.png" (Join-Path $projectRoot "artifacts/local-ai/$name-$shape.png") *> $null
        if($LASTEXITCODE -ne 0){throw 'Rotation screenshot retrieval failed'}
    }
}finally{
    if($originalFont -eq 'null'){& $adb -s $serial shell settings delete system font_scale}else{& $adb -s $serial shell settings put system font_scale $originalFont}
}
