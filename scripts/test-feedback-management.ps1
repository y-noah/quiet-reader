param([ValidateSet('light','dark')][string]$Theme='light',[ValidateSet(320,360,412)][int]$Width=320,[ValidateSet('1.0','1.3','1.5')][string]$Font='1.5',[ValidatePattern('^[a-z0-9-]+$')][string]$Report='feedback-history-full')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Project emulator required'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$oldSize=(& $adb -s $serial shell wm size)-join "`n"
$oldDensity=(& $adb -s $serial shell wm density)-join "`n"
$oldFont=(& $adb -s $serial shell settings get system font_scale).Trim()
if($oldFont -ne 'null' -and $oldFont -notmatch '^\d+(\.\d+)?$'){throw 'Unexpected original font scale'}
try{
    & $adb -s $serial shell wm size "$($Width*3)x1920" *> $null
    & $adb -s $serial shell wm density 480 *> $null
    & $adb -s $serial shell settings put system font_scale $Font *> $null
    $mode=if($Theme -eq 'light'){'platform-preference'}else{'platform-preference-dark'}
    $fontLabel=[int]([double]::Parse($Font,[Globalization.CultureInfo]::InvariantCulture)*100)
    $output=& $adb -s $serial shell am instrument -w -e mode $mode app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $exitCode=$LASTEXITCODE
    $output | Set-Content (Join-Path $projectRoot "artifacts/local-ai/$Report-$Theme-$Width-$fontLabel-api36.txt")
    foreach($line in $output){if($line -match '^SCREENSHOT (feedback-history-[a-z-]+\.png)$'){
        $name=$Matches[1]
        & $adb -s $serial pull "/sdcard/Android/data/app.quietreader/files/$name" (Join-Path $projectRoot "artifacts/local-ai/$Report-$Width-$fontLabel-$name") *> $null
        if($LASTEXITCODE -ne 0){throw 'Screenshot pull failed'}
    }}
    if($exitCode -ne 0 -or ($output -join "`n") -notmatch 'checks=24 failures=0'){throw 'Feedback management checks failed'}
    if(($output -join "`n") -notmatch "CONFIG widthDp=$Width fontScale=$Font"){throw 'Unexpected display configuration'}
    "Feedback management verified: $Theme, width $Width, font $Font"
}finally{
    if($oldSize -match 'Override size: (\d+x\d+)'){& $adb -s $serial shell wm size $Matches[1] *> $null}else{& $adb -s $serial shell wm size reset *> $null}
    if($oldDensity -match 'Override density: (\d+)'){& $adb -s $serial shell wm density $Matches[1] *> $null}else{& $adb -s $serial shell wm density reset *> $null}
    if($oldFont -eq 'null'){& $adb -s $serial shell settings delete system font_scale *> $null}else{& $adb -s $serial shell settings put system font_scale $oldFont *> $null}
}