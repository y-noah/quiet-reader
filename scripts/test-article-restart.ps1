param([ValidateSet('emulator-5560')][string]$Serial='emulator-5560')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed'}
if((& $adb -s $Serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong project AVD'}
$probeId=[guid]::NewGuid().ToString('N')
$report=Join-Path $projectRoot 'artifacts/local-ai/article-process-restart-api36.txt'
$first=& $adb -s $Serial shell am instrument -w -e mode cache-restart-write -e probeId $probeId app.quietreader.test/app.quietreader.FeedStoreInstrumentation
$first | Set-Content -LiteralPath $report
$first | Write-Output
if($LASTEXITCODE -ne 0 -or ($first -join "`n") -notmatch 'PASS body persisted' -or ($first -join "`n") -match 'FAIL|ERROR'){throw 'Write phase failed'}
& $adb -s $Serial shell am force-stop app.quietreader
if($LASTEXITCODE -ne 0){throw 'Process stop failed'}
$remaining=& $adb -s $Serial shell pidof app.quietreader
if($remaining){throw 'Target process remains alive'}
'PASS target process absent after force-stop' | Tee-Object -FilePath $report -Append
$second=& $adb -s $Serial shell am instrument -w -e mode cache-restart-read -e probeId $probeId app.quietreader.test/app.quietreader.FeedStoreInstrumentation
$second | Add-Content -LiteralPath $report
$second | Write-Output
if($LASTEXITCODE -ne 0 -or ($second -join "`n") -notmatch 'PASS empty process memory restores body' -or ($second -join "`n") -match 'FAIL|ERROR'){throw 'Read phase failed'}
