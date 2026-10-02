param([ValidateSet('emulator-5556','emulator-5558')][string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed.'}
$expectedAvd=if($Serial -eq 'emulator-5558'){'quiet-reader-api26'}else{'quiet-reader-test'}
if ((& $adb -s $Serial emu avd name) -notcontains $expectedAvd){throw 'Wrong project AVD.'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebug','assembleDebugAndroidTest','-PidentityUi'
& "$PSScriptRoot/install-diagnostic.ps1" -Serial $Serial
$runDirectory=Join-Path $projectRoot ('artifacts/identity-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
Get-FileHash -LiteralPath (Join-Path $projectRoot '.tools/diagnostic-app.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-debug-apk.txt') -Encoding utf8
$report=& $adb -s $Serial shell am instrument -w app.quietreader.test/app.quietreader.IdentityInstrumentation
$exitCode=$LASTEXITCODE
$report | Tee-Object -FilePath (Join-Path $runDirectory 'instrumentation.txt')
$match=[regex]::Match(($report -join "`n"),'Output: (/storage/[^\s]+/identity-qa/run-\d+)')
if($match.Success){& $adb -s $Serial pull $match.Groups[1].Value $runDirectory;if($LASTEXITCODE -ne 0){throw 'Cannot pull identity evidence.'}}
if($exitCode -ne 0 -or !($report -match '^COMPLETED 6 identity trials; checks=48 failures=0$')){throw "Identity regression failed: $runDirectory"}
Write-Output "Synthetic same-origin history-navigation result in $runDirectory; not HTTP302 or platform authentication certification."
