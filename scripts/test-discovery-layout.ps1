param([ValidateSet('layout-dark','layout-light','reader-layout-dark','reader-layout-light','webview-accessibility-control')][string]$Mode='layout-dark',[ValidateSet(320,360,412)][int]$Width=320,[ValidateSet('1.0','1.3','1.5')][string]$Font='1.5',[switch]$NormalText,[switch]$ScreenReader,[switch]$AutomationOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5560'
if((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed'}
if((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$oldSize=(& $adb -s $serial shell wm size) -join "`n"
$oldDensity=(& $adb -s $serial shell wm density) -join "`n"
$oldFont=(& $adb -s $serial shell settings get system font_scale).Trim()
if($oldFont -ne 'null' -and $oldFont -notmatch '^\d+(\.\d+)?$'){throw 'Unexpected original font scale'}
$oldServices=(& $adb -s $serial shell settings get secure enabled_accessibility_services).Trim()
$oldAccessibility=(& $adb -s $serial shell settings get secure accessibility_enabled).Trim()
$probeService='app.quietreader.test/app.quietreader.ReaderProbeAccessibilityService'
# WebView needs a real visible service; default UiAutomation hides bound services.
$useBoundService=($ScreenReader -or $Mode -like 'reader-layout-*' -or $Mode -eq 'webview-accessibility-control') -and -not $AutomationOnly
try{
    if($useBoundService){
        $services=if($oldServices -eq 'null' -or [string]::IsNullOrEmpty($oldServices)){$probeService}elseif(($oldServices -split ':') -contains $probeService){$oldServices}else{$oldServices+':'+$probeService}
        & $adb -s $serial shell settings put secure enabled_accessibility_services $services
        if($LASTEXITCODE -ne 0){throw 'Cannot enable emulator probe service'}
        & $adb -s $serial shell settings put secure accessibility_enabled 1
        if($LASTEXITCODE -ne 0){throw 'Cannot enable emulator accessibility'}
    }
    & $adb -s $serial shell wm size "$($Width*3)x1920"
    & $adb -s $serial shell wm density 480
    & $adb -s $serial shell settings put system font_scale $Font
    $fontLabel=[int]([double]::Parse($Font,[Globalization.CultureInfo]::InvariantCulture)*100)
    $fixture=if($NormalText){'normal'}else{'extreme'}
    if($useBoundService){$fixture=if($Mode -eq 'webview-accessibility-control'){'screenreader'}else{'screenreader-'+$fixture}}
    $output=& $adb -s $serial shell am instrument -w -e mode $Mode -e target $fixture app.quietreader.test/app.quietreader.FeedStoreInstrumentation
    $fixtureSuffix=if($NormalText){"-normal"}else{""}
    if($useBoundService){$fixtureSuffix+="-screenreader"}
    $output | Tee-Object -FilePath (Join-Path $projectRoot "artifacts/local-ai/$Mode-$Width-$fontLabel$fixtureSuffix-api36.txt")
    if($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'checks=([1-9][0-9]*) failures=0'){throw 'Layout checks failed'}
    if($useBoundService -and ($output -join "`n") -notmatch 'PASS WebView can observe the actual emulator test accessibility service'){throw 'Accessibility service visibility was not verified'}
    if($Mode -ne 'webview-accessibility-control' -and ($output -join "`n") -notmatch "CONFIG widthDp=$Width fontScale=$Font" ){throw 'Unexpected display configuration'}
    foreach($line in $output){if($line -match '^SCREENSHOT (layout-[a-z0-9-]+\.png)$'){$name=$Matches[1]; & $adb -s $serial pull "/sdcard/Android/data/app.quietreader/files/$name" (Join-Path $projectRoot "artifacts/local-ai/$name") *> $null;if($LASTEXITCODE -ne 0){throw 'Screenshot pull failed'}}}
}finally{
    if($useBoundService){
        if($oldServices -eq 'null' -or [string]::IsNullOrEmpty($oldServices)){& $adb -s $serial shell settings delete secure enabled_accessibility_services *> $null}else{& $adb -s $serial shell settings put secure enabled_accessibility_services $oldServices}
        if($oldAccessibility -eq 'null'){& $adb -s $serial shell settings delete secure accessibility_enabled *> $null}else{& $adb -s $serial shell settings put secure accessibility_enabled $oldAccessibility}
    }
    if($oldSize -match 'Override size: (\d+x\d+)'){& $adb -s $serial shell wm size $Matches[1]}else{& $adb -s $serial shell wm size reset}
    if($oldDensity -match 'Override density: (\d+)'){& $adb -s $serial shell wm density $Matches[1]}else{& $adb -s $serial shell wm density reset}
    if($oldFont -eq 'null'){& $adb -s $serial shell settings delete system font_scale}else{& $adb -s $serial shell settings put system font_scale $oldFont}
    if($useBoundService){
        $restoredServices=(& $adb -s $serial shell settings get secure enabled_accessibility_services).Trim()
        $restoredAccessibility=(& $adb -s $serial shell settings get secure accessibility_enabled).Trim()
        $servicesOk=$restoredServices -eq $oldServices
        $accessibilityOk=$restoredAccessibility -eq $oldAccessibility
        Write-Output "RESTORED accessibility_services=$servicesOk accessibility_enabled=$accessibilityOk"
        if(-not $servicesOk -or -not $accessibilityOk){throw 'Emulator accessibility settings were not restored'}
    }
}

