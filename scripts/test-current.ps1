param([ValidateSet('fiveSourceUi','answerPagingUi','dynamicReadingUi','userJourneyRepairUi')][string]$Suite='fiveSourceUi',[string]$GradleInit='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runner=@{fiveSourceUi='FiveSourceInstrumentation';answerPagingUi='AnswerPagingInstrumentation';dynamicReadingUi='DynamicReadingInstrumentation';userJourneyRepairUi='UserJourneyRepairInstrumentation'}[$Suite]
$tasks=@('assembleDebug','assembleDebugAndroidTest',"-P$Suite")
if($GradleInit){$tasks=@('-I',$GradleInit)+$tasks}
& "$PSScriptRoot/build.ps1" -Tasks $tasks
& "$PSScriptRoot/install-diagnostic.ps1"
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
$evidence=Join-Path $projectRoot ('artifacts/'+$Suite+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $evidence | Out-Null
$report=& $adb -s emulator-5556 shell am instrument -w "app.quietreader.test/app.quietreader.$runner"
$instrumentExit=$LASTEXITCODE
$report | Set-Content -Encoding utf8 (Join-Path $evidence 'instrumentation.txt')
$report | Write-Output
$remote=''; $location=$report | Select-String 'Output: (.+)' | Select-Object -Last 1
if($location){$remote=$location.Matches.Groups[1].Value.Trim()}
if($remote){& $adb -s emulator-5556 pull $remote $evidence; if($LASTEXITCODE -ne 0){throw 'Evidence pull failed'}}
if($instrumentExit -ne 0 -or !(($report -match 'checks=\d+ failures=0$') -or ($report -match '^CHECKS \d+ FAILURES 0$')) -or ($report -match '^FAIL |^ERROR |INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed') -or ($Suite -ne 'answerPagingUi' -and !$remote)){throw 'Regression failed; inspect evidence'}
