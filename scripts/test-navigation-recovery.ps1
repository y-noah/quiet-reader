param([ValidateSet('emulator-5556','emulator-5558')][string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed.'}
$expectedAvd=if($Serial -eq 'emulator-5558'){'quiet-reader-api26'}else{'quiet-reader-test'}
if ((& $adb -s $Serial emu avd name) -notcontains $expectedAvd){throw 'Wrong project AVD.'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebug','assembleDebugAndroidTest','-PnavigationRecoveryUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial $Serial
$runDirectory=Join-Path $projectRoot ('artifacts/navigation-recovery-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-debug-apk.txt') -Encoding utf8
$report=& $adb -s $Serial shell am instrument -w app.quietreader.test/app.quietreader.NavigationRecoveryInstrumentation
$exitCode=$LASTEXITCODE
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/navigation-recovery-qa/run-\d+)')
if($match.Success){& $adb -s $Serial pull $match.Groups[1].Value $runDirectory;if($LASTEXITCODE -ne 0){throw 'Cannot pull navigation recovery evidence.'}}
if($exitCode -ne 0 -or !($report -match '^COMPLETED 8 navigation trials; checks=59 failures=0$')){throw "Navigation regression failed: $runDirectory"}
Write-Output "Controlled navigation result in $runDirectory; not real platform/login certification. Data preserved."
