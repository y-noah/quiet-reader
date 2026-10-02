$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$serial='emulator-5556'
if ((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed.'}
if ((& $adb -s $serial emu avd name) -notcontains 'quiet-reader-test'){throw 'Wrong project AVD.'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebug','assembleDebugAndroidTest','-PlatencyUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial $serial
$runDirectory=Join-Path $projectRoot ('artifacts/latency-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-debug-apk.txt') -Encoding utf8
$report=& $adb -s $serial shell am instrument -w app.quietreader.test/app.quietreader.LatencyInstrumentation
$exitCode=$LASTEXITCODE
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/latency-qa/run-\d+)')
if($match.Success){& $adb -s $serial pull $match.Groups[1].Value $runDirectory;if($LASTEXITCODE -ne 0){throw 'Cannot pull latency evidence.'}}
if($exitCode -ne 0 -or !($report -match '^COMPLETED 4 latency trials; successes=4 failures=0$')){throw "Latency runner failed: $runDirectory"}
Write-Output "Latency observations saved in $runDirectory; not authentication or speed acceptance."
