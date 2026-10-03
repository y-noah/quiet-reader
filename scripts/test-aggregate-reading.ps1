param([string[]]$Source=@(), [int]$Limit=50)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if((& $adb -s emulator-5556 shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only the project emulator is allowed'}
if((& $adb -s emulator-5556 emu avd name) -notcontains 'quiet-reader-test'){throw 'Wrong project AVD'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebug','assembleDebugAndroidTest','-PaggregateReadingUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial emulator-5556
$runDirectory=Join-Path $projectRoot ('artifacts/reading-audit-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-apk.txt') -Encoding utf8
$arguments=@('-s','emulator-5556','shell','am','instrument','-w','-e','limit',"$Limit")
if($Source){$sourceArgument=$Source -join ',';if($sourceArgument -notmatch '^[A-Z]+(?:,[A-Z]+)*$'){throw 'Source must be comma-separated enum names'};$arguments+=@('-e','source',$sourceArgument)}
$arguments+='app.quietreader.test/app.quietreader.AggregateReadingInstrumentation'
$report=& $adb @arguments
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/aggregate-reading/run-\d+)')
if($match.Success){& $adb -s emulator-5556 pull $match.Groups[1].Value $runDirectory}
if(!($report -match 'AUDIT COMPLETE')){throw "Audit did not complete; inspect $runDirectory"}
Write-Output "Audit observations saved in $runDirectory; inspect results.json and screenshots, not an all-source success claim."
