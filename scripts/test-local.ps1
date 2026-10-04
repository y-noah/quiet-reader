param([string]$GradleInit='')
$ErrorActionPreference='Stop'
$tasks=@('testDebugUnitTest','lintDebug');if($GradleInit){$tasks=@('-I',$GradleInit)+$tasks}
& "$PSScriptRoot/build.ps1" -Tasks $tasks
foreach($suite in @('fiveSourceUi','dynamicReadingUi','answerPagingUi','userJourneyRepairUi')){& "$PSScriptRoot/test-current.ps1" -Suite $suite -GradleInit $GradleInit}
Write-Output 'Current five-platform regression passed. Review screenshots separately.'