param([ValidateSet('all','core','stress','lifecycle','dark','audit','images','smzdm-cache','board-return','appearance','video-filter','image-layout')][string]$Mode='all',[ValidateSet('emulator-5556','emulator-5558')][string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
# This intentionally refuses physical devices and other AVDs. No uninstall or cookie clearing.
if ((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Start the project emulator before running this script.'}
$expectedAvd=if($Serial -eq 'emulator-5558'){'quiet-reader-api26'}else{'quiet-reader-test'}
if ((& $adb -s $serial emu avd name) -notcontains $expectedAvd){throw 'Wrong project AVD.'}
& "$PSScriptRoot/build.ps1" -Tasks 'testDebugUnitTest','assembleDebug','assembleDebugAndroidTest','-PexploratoryUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial $Serial
$runDirectory=Join-Path $projectRoot ('artifacts/experience-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-debug-apk.txt') -Encoding utf8
$report=& $adb -s $serial shell am instrument -w -e mode $Mode app.quietreader.test/app.quietreader.ExploratoryInstrumentation
$exitCode=$LASTEXITCODE
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/exploratory-qa/run-\d+)')
if($match.Success){
    & $adb -s $serial pull $match.Groups[1].Value $runDirectory
    if($LASTEXITCODE -ne 0){throw 'Could not retrieve fresh screenshots.'}
}
if($exitCode -ne 0 -or !($report -match 'SUMMARY \d+ checks; 0 failures\.')){throw "Experience test failed; inspect $runDirectory"}
if($Mode -eq 'appearance'){
    $api=[int]((& $adb -s $Serial shell getprop ro.build.version.sdk).Trim())
    $expectedChecks=if($api -ge 29){135}else{121}
    if(!($report -match "SUMMARY $expectedChecks checks; 0 failures\.")){throw "Appearance mode did not complete both themes/all six platforms: $runDirectory"}
}
if($Mode -eq 'video-filter' -and !($report -match 'SUMMARY 28 checks; 0 failures\.')){throw "Video-filter mode did not complete three synthetic user journeys: $runDirectory"}
if($Mode -eq 'image-layout' -and !($report -match 'SUMMARY 39 checks; 0 failures\.')){throw "Image-layout mode did not complete five delayed-image positions: $runDirectory"}
Write-Output "Interaction checks passed. Inspect fresh screenshots in $runDirectory; this does not certify platform authentication or production release."
