$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5556'
if ((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed.'}
if ((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-test'){throw 'Wrong project AVD.'}
if ((& $adb -s $serial shell getprop sys.boot_completed).Trim() -ne '1'){throw 'Project emulator has not booted.'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebug','assembleDebugAndroidTest','-PweiboReadingProbeUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial $serial
$runDirectory=Join-Path $projectRoot ('artifacts/weibo-reading-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-debug-apk.txt') -Encoding utf8
$report=& $adb -s $serial shell am instrument -w app.quietreader.test/app.quietreader.WeiboReadingProbeInstrumentation
$exitCode=$LASTEXITCODE
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/weibo-reading-qa/run-\d+)')
if($match.Success){& $adb -s $serial pull $match.Groups[1].Value $runDirectory;if($LASTEXITCODE -ne 0){throw 'Cannot pull diagnostic evidence.'}}
if($exitCode -ne 0 -or !($report -match '^COMPLETED 3 UA observations;')){throw "Weibo diagnostic incomplete: $runDirectory"}
Write-Output "Weibo observations saved in $runDirectory; not authentication or fulltext acceptance."
