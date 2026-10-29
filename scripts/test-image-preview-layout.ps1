param([ValidateSet('landscape','portrait')][string]$Shape='landscape',[ValidateSet('1.0','1.5')][string]$Font='1.5',[string]$Label='verified',[ValidateSet('image-preview-layout','reader-image-flow')][string]$Mode='image-preview-layout')
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid report label'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only emulator allowed'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$oldSize=(& $adb -s $serial shell wm size)-join "`n"
$oldDensity=(& $adb -s $serial shell wm density)-join "`n"
$oldFont=(& $adb -s $serial shell settings get system font_scale).Trim()
if($oldFont -ne 'null' -and $oldFont -notmatch '^\d+(\.\d+)?$'){throw 'Unexpected font scale'}
$name=if($Mode -eq 'reader-image-flow'){"reader-image-$Shape-$($Font.Replace('.',''))-$Label"}else{"image-layout-$Shape-$($Font.Replace('.',''))-$Label"}
try{
    $size=if($Shape -eq 'landscape'){'1920x960'}else{'960x1920'}
    & $adb -s $serial shell wm size $size
    & $adb -s $serial shell wm density 480
    & $adb -s $serial shell settings put system font_scale $Font
    $result=& $adb -s $serial shell am instrument -w -e mode $Mode app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $result | Tee-Object -FilePath (Join-Path $projectRoot "artifacts/local-ai/$name-api36.txt")
    if($Mode -eq 'image-preview-layout'){& $adb -s $serial pull /sdcard/Android/data/app.quietreader/files/image-preview-layout.png (Join-Path $projectRoot "artifacts/local-ai/$name.png") *> $null}
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'failures=0'){throw 'Image layout diagnostic failed'}
}finally{
    if($oldSize -match 'Override size: (\d+x\d+)'){& $adb -s $serial shell wm size $Matches[1]}else{& $adb -s $serial shell wm size reset}
    if($oldDensity -match 'Override density: (\d+)'){& $adb -s $serial shell wm density $Matches[1]}else{& $adb -s $serial shell wm density reset}
    if($oldFont -eq 'null'){& $adb -s $serial shell settings delete system font_scale}else{& $adb -s $serial shell settings put system font_scale $oldFont}
}
