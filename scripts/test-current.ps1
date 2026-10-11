param([ValidateSet('fiveSourceUi','answerPagingUi','dynamicReadingUi','userJourneyRepairUi','loginSessionUi','loginWindowUi','browsingExperienceUi','expandedBrowsingUi','mobileLayoutUi')][string]$Suite='fiveSourceUi',[string]$GradleInit='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runner=@{fiveSourceUi='FiveSourceInstrumentation';answerPagingUi='AnswerPagingInstrumentation';dynamicReadingUi='DynamicReadingInstrumentation';userJourneyRepairUi='UserJourneyRepairInstrumentation';loginSessionUi='LoginSessionInstrumentation';loginWindowUi='LoginWindowInstrumentation';browsingExperienceUi='BrowsingExperienceInstrumentation';expandedBrowsingUi='ExpandedBrowsingInstrumentation';mobileLayoutUi='MobileLayoutInstrumentation'}[$Suite]
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
if($instrumentExit -ne 0 -or !(($report -match 'checks=\d+ failures=0$') -or ($report -match '^CHECKS \d+ FAILURES 0$')) -or ($report -match '^FAIL |^ERROR |INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed') -or ($Suite -notin @('answerPagingUi','loginSessionUi','expandedBrowsingUi') -and !$remote)){throw 'Regression failed; inspect evidence'}

if($Suite -eq 'browsingExperienceUi'){
    & $adb -s emulator-5556 shell am force-stop app.quietreader
    $restored=& $adb -s emulator-5556 shell am instrument -w -e phase restore app.quietreader.test/app.quietreader.BrowsingExperienceInstrumentation
    $restored | Set-Content -Encoding utf8 (Join-Path $evidence 'restore.txt')
    $restored | Write-Output
    if(!($restored -match 'checks=\d+ failures=0$') -or ($restored -match '^FAIL |^ERROR |INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed')){throw 'Process restart regression failed'}
}
