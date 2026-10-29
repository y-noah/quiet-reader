param([Parameter(Mandatory=$true)][string]$Adb,[Parameter(Mandatory=$true)][string]$JavaHome,[string]$Label='verified')
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid report label'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$serial='emulator-5560'
$package='app.quietreader.discoveryprobe'
if((& $Adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only emulator allowed'}
if((& $Adb -s $serial emu avd name) -notcontains 'quiet-reader-api36'){throw 'Wrong AVD'}
$prefix=Join-Path $projectRoot "artifacts/local-ai/tieba-process-task-20261026-$Label"
$trialId='task-'+$Label
$instrumentProcess=$null
$forwardPort=$null
$classes=Join-Path $projectRoot 'artifacts/local-ai/tieba-task-jdi'
New-Item -ItemType Directory -Force -Path $classes *> $null
& "$JavaHome/bin/javac.exe" --add-modules jdk.jdi -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'TiebaTaskStateCheck.java')
if($LASTEXITCODE -ne 0){throw 'Host inspector compilation failed'}
function Restore-RecentTask {
    & $Adb -s $serial shell input keyevent 187
    Start-Sleep -Milliseconds 800
    & $Adb -s $serial shell rm -f /sdcard/quiet-reader-recents.xml
    & $Adb -s $serial shell uiautomator dump /sdcard/quiet-reader-recents.xml *> $null
    $raw=(& $Adb -s $serial shell cat /sdcard/quiet-reader-recents.xml) -join ''
    [xml]$tree=$raw
    $snapshots=@($tree.SelectNodes('//*[@content-desc="New"]') | Where-Object {$_.'resource-id' -match '/snapshot$'} | Sort-Object { $r=[regex]::Match($_.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]');[int]$r.Groups[3].Value-[int]$r.Groups[1].Value } -Descending)
    if(!$snapshots.Count){throw 'Most recent New task not visible'}
    $bounds=[regex]::Match($snapshots[0].bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    & $Adb -s $serial shell input tap ([int]( ([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2)) ([int]( ([int]$bounds.Groups[2].Value+[int]$bounds.Groups[4].Value)/2))
    Start-Sleep -Milliseconds 1200
}
function Tap-ReaderText([string]$label) {
    $node=$null
    for($attempt=0;$attempt -lt 4;$attempt++){
        & $Adb -s $serial shell rm -f /sdcard/quiet-reader-reader.xml
        & $Adb -s $serial shell uiautomator dump /sdcard/quiet-reader-reader.xml *>> "$prefix-ui-driver.txt"
        $raw=(& $Adb -s $serial shell cat /sdcard/quiet-reader-reader.xml 2>$null) -join ''
        if($raw){[xml]$tree=$raw;$node=$tree.SelectSingleNode("//*[@text='$label' or @content-desc='$label']");if($node){break}}
        Start-Sleep -Milliseconds 600
    }
    if(!$node){throw "Missing actual reader action: $label"}
    $bounds=[regex]::Match($node.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    & $Adb -s $serial shell input tap ([int]( ([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2)) ([int]( ([int]$bounds.Groups[2].Value+[int]$bounds.Groups[4].Value)/2))
    Start-Sleep -Milliseconds 500
}
function Scroll-Reader {
    $web=$null
    for($attempt=0;$attempt -lt 4;$attempt++){
        & $Adb -s $serial shell rm -f /sdcard/quiet-reader-reader.xml
        & $Adb -s $serial shell uiautomator dump /sdcard/quiet-reader-reader.xml *>> "$prefix-ui-driver.txt"
        $raw=(& $Adb -s $serial shell cat /sdcard/quiet-reader-reader.xml 2>$null) -join ''
        if($raw){[xml]$tree=$raw;$web=$tree.SelectSingleNode('//*[@class="android.webkit.WebView"]');if($web){break}}
        Start-Sleep -Milliseconds 600
    }
    if(!$web){throw 'Visible native reader WebView missing'}
    $bounds=[regex]::Match($web.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    $x=[int](([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2)
    $top=[int]$bounds.Groups[2].Value;$height=[int]$bounds.Groups[4].Value-$top
    & $Adb -s $serial shell input swipe $x ([int]($top+$height*.75)) $x ([int]($top+$height*.25)) 500
    Start-Sleep -Milliseconds 1500
}
try {
    $instrumentProcess=Start-Process -WindowStyle Hidden -FilePath $Adb -ArgumentList @('-s',$serial,'shell','am','instrument','-w','-e','mode','tieba-process-prepare','-e','probeId',$trialId,"$package.test/app.quietreader.FeedStoreInstrumentation") -RedirectStandardOutput "$prefix-prepare.txt" -RedirectStandardError "$prefix-prepare-stderr.txt" -PassThru
    $ready=$false
    for($step=0;$step -lt 100;$step++){
        if(Test-Path -LiteralPath "$prefix-prepare.txt"){$prepared=Get-Content -LiteralPath "$prefix-prepare.txt" -Raw;if($prepared -match 'READY_FOR_PROCESS_KILL'){$ready=$true;break}}
        if($instrumentProcess.HasExited){break}
        Start-Sleep -Milliseconds 250
    }
    if(!$ready -or $prepared -notmatch 'checks=\d+ failures=0'){throw 'Prepared live task was not ready'}
    $identity=[regex]::Match($prepared,'PREPARED task=(\d+) pid=(\d+) nativeY=(\d+)')
    if(!$identity.Success){throw 'Prepared identity missing'}
    $taskId=$identity.Groups[1].Value;$expectedPid=$identity.Groups[2].Value;$expectedScroll=$identity.Groups[3].Value
    $targetPid=((& $Adb -s $serial shell pidof $package) -join ' ').Trim()
    if($targetPid -notmatch '^[1-9]\d*$' -or $targetPid -ne $expectedPid){throw 'Live PID differs from prepared task process'}
    "verified-live PID=$targetPid" | Set-Content -LiteralPath "$prefix-kill.txt"
    & $Adb -s $serial shell dumpsys activity activities $package | Set-Content -LiteralPath "$prefix-android-task.txt"
    # run-as restricts this signal to the debuggable isolated application's UID.
    & $Adb -s $serial shell run-as $package kill -9 $targetPid
    if($LASTEXITCODE -ne 0){throw 'Could not terminate prepared process'}
    if(!$instrumentProcess.WaitForExit(10000)){throw 'Instrumentation did not observe process death'}
    $after=((& $Adb -s $serial shell pidof $package) -join ' ').Trim()
    "after-kill PID=$after" | Add-Content -LiteralPath "$prefix-kill.txt"
    if($after){throw 'Prepared process is still alive'}
    Get-Content -LiteralPath "$prefix-prepare.txt" -Tail 8
    # A second am instrument would force-stop the package and discard saved state.
    # Bring back the system-owned task directly; inspect fields from the host.
    Restore-RecentTask
    $ordinaryPid=''
    for($step=0;$step -lt 40;$step++){$ordinaryPid=((& $Adb -s $serial shell pidof $package) -join ' ').Trim();if($ordinaryPid){break};Start-Sleep -Milliseconds 250}
    if($ordinaryPid -notmatch '^[1-9]\d*$' -or $ordinaryPid -eq $targetPid){throw 'Ordinary restored process missing or unchanged'}
    "ordinary-bootstrap PID=$ordinaryPid" | Add-Content -LiteralPath "$prefix-kill.txt"
    # Establish a real non-instrumented selection before the process-death trial.
    Tap-ReaderText '更多选项'
    Tap-ReaderText '同话题帖子'
    Tap-ReaderText '第二帖实际标题'
    Scroll-Reader
    $forwardPort=((& $Adb -s $serial forward tcp:0 "jdwp:$ordinaryPid") -join '').Trim()
    if($forwardPort -notmatch '^\d+$'){throw 'Baseline JDWP forward unavailable'}
    $baseline=& "$JavaHome/bin/java.exe" --add-modules jdk.jdi -cp $classes TiebaTaskStateCheck $forwardPort -1
    $baseline | Set-Content -LiteralPath "$prefix-baseline.txt"
    if(($baseline -join "`n") -notmatch 'checks=\d+ failures=0'){throw 'Ordinary selected-post baseline failed'}
    $position=[regex]::Match(($baseline -join "`n"),'POSITION expected=-1 actual=(\d+)')
    if(!$position.Success){throw 'Baseline native scroll missing'}
    $expectedScroll=$position.Groups[1].Value
    & $Adb -s $serial forward --remove "tcp:$forwardPort";$forwardPort=$null
    & $Adb -s $serial shell input keyevent 3
    Start-Sleep -Milliseconds 800
    & $Adb -s $serial shell dumpsys activity activities $package | Set-Content -LiteralPath "$prefix-ordinary-task.txt"
    [xml]$history=(& $Adb -s $serial shell run-as $package cat shared_prefs/reading-history.xml) -join ''
    $entry=$history.SelectSingleNode('//string[@name="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=990066600"]')
    if(!$entry){throw 'Ordinary background history missing'}
    $marker=$entry.InnerText | ConvertFrom-Json
    [pscustomobject]@{position=$marker.position;bodySource=$marker.bodySource;hasViewport=($null -ne $marker.viewport)} | ConvertTo-Json | Set-Content -LiteralPath "$prefix-marker.txt"
    if([int]$marker.position -ne [int]$expectedScroll){throw 'Ordinary background did not persist expected position'}
    $livePid=((& $Adb -s $serial shell pidof $package) -join ' ').Trim()
    if($livePid -ne $ordinaryPid){throw 'Ordinary process changed before controlled death'}
    & $Adb -s $serial shell run-as $package kill -9 $ordinaryPid
    if($LASTEXITCODE -ne 0){throw 'Ordinary process signal failed'}
    Start-Sleep -Milliseconds 300
    if(((& $Adb -s $serial shell pidof $package) -join ' ').Trim()){throw 'Ordinary process still alive'}
    "ordinary-live-before-death PID=$ordinaryPid" | Add-Content -LiteralPath "$prefix-kill.txt"
    Restore-RecentTask
    $restoredPid=''
    for($step=0;$step -lt 40;$step++){$restoredPid=((& $Adb -s $serial shell pidof $package) -join ' ').Trim();if($restoredPid){break};Start-Sleep -Milliseconds 250}
    if($restoredPid -notmatch '^[1-9]\d*$' -or $restoredPid -eq $ordinaryPid){throw 'Ordinary process did not restart'}
    "ordinary-restored PID=$restoredPid" | Add-Content -LiteralPath "$prefix-kill.txt"
    $forwardPort=((& $Adb -s $serial forward tcp:0 "jdwp:$restoredPid") -join '').Trim()
    if($forwardPort -notmatch '^\d+$'){throw 'JDWP forward unavailable'}
    $restored=& "$JavaHome/bin/java.exe" --add-modules jdk.jdi -cp $classes TiebaTaskStateCheck $forwardPort $expectedScroll
    $restored | Set-Content -LiteralPath "$prefix-restore.txt"
    $restored | Select-Object -Last 17
    if(($restored -join "`n") -notmatch 'checks=\d+ failures=0'){throw 'Android task restoration failed'}
} finally {
    if($forwardPort){& $Adb -s $serial forward --remove "tcp:$forwardPort"}
    if($instrumentProcess -and !$instrumentProcess.HasExited){Stop-Process -Id $instrumentProcess.Id}
    & $Adb -s $serial shell am instrument -w -e mode tieba-process-cleanup -e probeId $trialId "$package.test/app.quietreader.FeedStoreInstrumentation" | Set-Content -LiteralPath "$prefix-cleanup.txt"
    & $Adb -s $serial shell am force-stop $package
    & $Adb -s $serial shell am force-stop "$package.test"
    & $Adb -s $serial shell rm -f /sdcard/quiet-reader-recents.xml /sdcard/quiet-reader-reader.xml
}
