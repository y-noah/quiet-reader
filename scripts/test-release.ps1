param([switch]$KeepData,[ValidateSet('emulator-5556','emulator-5558')][string]$Serial='emulator-5556',[ValidateSet('normal','sources','branding')][string]$Mode='normal',[ValidateSet('all','WEIBO','ZHIHU','TIEBA','HUPU','WALLSTREET','HACKERNEWS','SMZDM','CLS','GEEKPARK')][string]$Source='all',[switch]$WallstreetOriginal,[switch]$FixedTieba,[switch]$WeiboProbe,[switch]$WeiboFulltextProbe,[switch]$WeiboReaderJourney,[switch]$SmzdmImageProbe,[switch]$SmzdmImageJourney)
$ErrorActionPreference='Stop'
if($SmzdmImageJourney -and ($Mode -ne 'sources' -or $Source -ne 'SMZDM' -or $SmzdmImageProbe)){throw 'SmzdmImageJourney is restricted to the fixed public SMZDM source journey, separate from the probe.'}
if($SmzdmImageProbe -and ($Mode -ne 'sources' -or $Source -ne 'SMZDM')){throw 'SmzdmImageProbe is restricted to the fixed public SMZDM source observation.'}
if($WallstreetOriginal -and ($Mode -ne 'sources' -or $Source -ne 'WALLSTREET')){throw 'WallstreetOriginal is restricted to the WALLSTREET source observation.'}
if($FixedTieba -and ($Mode -ne 'sources' -or $Source -ne 'TIEBA')){throw 'FixedTieba is restricted to the TIEBA source observation.'}
if($WeiboProbe -and ($Mode -ne 'sources' -or $Source -ne 'WEIBO')){throw 'WeiboProbe is restricted to WEIBO source observations.'}
if($WeiboFulltextProbe -and ($Mode -ne 'sources' -or $Source -ne 'WEIBO')){throw 'WeiboFulltextProbe is restricted to WEIBO source observations.'}
if($WeiboReaderJourney -and ($Mode -ne 'sources' -or $Source -ne 'WEIBO')){throw 'WeiboReaderJourney is restricted to WEIBO source observations.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if ((& $adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'This script is restricted to the isolated emulator.'}
$expectedAvd=if($Serial -eq 'emulator-5558'){'quiet-reader-api26'}else{'quiet-reader-test'}
if ((& $adb -s $serial emu avd name) -notcontains $expectedAvd){throw 'The connected emulator is not this project test AVD.'}
& "$PSScriptRoot/build.ps1" -Tasks 'assembleDebugAndroidTest','-PreleaseUi'
$credential=Import-Clixml -LiteralPath (Join-Path $projectRoot '.tools/signing/password.xml')
$previousSecret=$env:QR_STORE_PASSWORD
try {
    $env:QR_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    & "$projectRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$projectRoot/.tools/signing/quiet-reader.jks" --ks-key-alias quiet-reader --ks-pass env:QR_STORE_PASSWORD --key-pass env:QR_STORE_PASSWORD --out "$projectRoot/artifacts/release-test.apk" "$projectRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if($LASTEXITCODE -ne 0){throw 'Test APK signing failed'}
} finally {$env:QR_STORE_PASSWORD=$previousSecret;$credential=$null}
# Only test-owned emulator packages are reset; no physical device or host data is touched.
if(!$KeepData) {
    & $adb -s $serial uninstall app.quietreader.test
    & $adb -s $serial uninstall app.quietreader
}
& $adb -s $serial install -r "$projectRoot/artifacts/静读.apk"
if($LASTEXITCODE -ne 0){throw 'Release APK installation failed'}
& $adb -s $serial install -r "$projectRoot/artifacts/release-test.apk"
if($LASTEXITCODE -ne 0){throw 'Release test installation failed'}
if($Mode -eq 'sources'){
    # Create identity/hash BEFORE instrumentation. Even a qemu exit or missing final
    # Output line must leave a known remote directory and a truthful host artifact.
    $sourceRunId=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $runDirectory=Join-Path $projectRoot ('artifacts/release-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    $remoteDirectory='/sdcard/Android/data/app.quietreader/files/release-qa/sources-'+$sourceRunId
    @("mode=sources","source=$Source","wallstreetOriginal=$WallstreetOriginal","fixedTieba=$FixedTieba","serial=$Serial","runId=$sourceRunId","remote=$remoteDirectory",'State: started, not passed') | Out-File (Join-Path $runDirectory 'run.txt') -Encoding utf8
    Get-FileHash -LiteralPath (Join-Path $projectRoot 'artifacts/静读.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-release-apk.txt') -Encoding utf8
    function Invoke-QrBoundedAdb {
        param([string[]]$AdbArguments,[string]$OutputFile,[string]$ErrorFile,[int]$TimeoutSeconds)
        # All calls target only this script's already-validated project emulator.
        $quotedArguments=($AdbArguments | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
        $child=Start-Process -FilePath $adb -ArgumentList $quotedArguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $OutputFile -RedirectStandardError $ErrorFile
        try {
            $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
            while(!$child.HasExited){
                if([DateTime]::UtcNow -ge $deadline){
                    # Stop only this bounded adb client, never the server or emulator.
                    $child.Kill();[void]$child.WaitForExit(2000)
                    throw "adb client exceeded ${TimeoutSeconds}s; output retained in $OutputFile"
                }
                Start-Sleep -Milliseconds 250
                $child.Refresh()
            }
            $child.WaitForExit()
            return $child.ExitCode
        } finally {$child.Dispose()}
    }
    $instrumentExit=-1;$sourceFailure=$null;$evidenceFailure=$false
    try {
        $timeout=if($Source -eq 'all'){1200}else{240}
        $originalArgument=if($WallstreetOriginal){'1'}else{'0'}
        $tiebaArgument=if($FixedTieba){'1'}else{'0'}
        $weiboArgument=if($WeiboProbe){'1'}else{'0'}
        $fulltextArgument=if($WeiboFulltextProbe){'1'}else{'0'}
        $journeyArgument=if($WeiboReaderJourney){'1'}else{'0'}
        $smzdmProbeArgument=if($SmzdmImageProbe){'1'}else{'0'}
        $smzdmJourneyArgument=if($SmzdmImageJourney){'1'}else{'0'}
        $instrumentExit=Invoke-QrBoundedAdb -AdbArguments @('-s',$Serial,'shell','am','instrument','-w','-e','mode','sources','-e','source',$Source,'-e','wallstreetOriginal',$originalArgument,'-e','fixedTieba',$tiebaArgument,'-e','weiboProbe',$weiboArgument,'-e','weiboFulltextProbe',$fulltextArgument,'-e','weiboReaderJourney',$journeyArgument,'-e','smzdmImageProbe',$smzdmProbeArgument,'-e','smzdmImageJourney',$smzdmJourneyArgument,'-e','runId',$sourceRunId,'app.quietreader.test/app.quietreader.ReleaseInstrumentation') -OutputFile (Join-Path $runDirectory 'instrumentation.txt') -ErrorFile (Join-Path $runDirectory 'instrumentation-error.txt') -TimeoutSeconds $timeout
    } catch {
        $sourceFailure=$_.Exception.Message
        $sourceFailure | Out-File (Join-Path $runDirectory 'runner-error.txt') -Encoding utf8
    } finally {
        # No wait-for-device: an offline device is evidence loss, not an infinite wait.
        try {
            $stateExit=Invoke-QrBoundedAdb -AdbArguments @('-s',$Serial,'get-state') -OutputFile (Join-Path $runDirectory 'device-state.txt') -ErrorFile (Join-Path $runDirectory 'device-state-error.txt') -TimeoutSeconds 5
            $deviceState=(Get-Content -LiteralPath (Join-Path $runDirectory 'device-state.txt') -Raw).Trim()
            if($stateExit -eq 0 -and $deviceState -eq 'device'){
                $pullExit=Invoke-QrBoundedAdb -AdbArguments @('-s',$Serial,'pull',$remoteDirectory,$runDirectory) -OutputFile (Join-Path $runDirectory 'pull.txt') -ErrorFile (Join-Path $runDirectory 'pull-error.txt') -TimeoutSeconds 25
                if($pullExit -ne 0){throw "Evidence pull failed with exit $pullExit"}
            }else{throw 'Device is offline/missing; source evidence remains at the recorded remote directory if recoverable'}
        } catch {
            $evidenceFailure=$true
            $_.Exception.Message | Out-File (Join-Path $runDirectory 'evidence-error.txt') -Encoding utf8
        }
    }
    $report=if(Test-Path -LiteralPath (Join-Path $runDirectory 'instrumentation.txt')){@(Get-Content -LiteralPath (Join-Path $runDirectory 'instrumentation.txt'))}else{@()}
    $report | Write-Output
    if($sourceFailure -or $instrumentExit -ne 0 -or !($report -match '^PASS release source observations') -or $evidenceFailure){throw "Source observations incomplete/blocked; inspect $runDirectory"}
    'State: PASS with source evidence recovered' | Add-Content -LiteralPath (Join-Path $runDirectory 'run.txt') -Encoding utf8
    return
}
$normalRunId=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$report=& $adb -s $serial shell am instrument -w -e mode $Mode -e source $Source -e runId $normalRunId app.quietreader.test/app.quietreader.ReleaseInstrumentation
$exitCode=$LASTEXITCODE
$report | Write-Output
$runDirectory=Join-Path $projectRoot ('artifacts/release-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$report | Out-File (Join-Path $runDirectory 'instrumentation.txt') -Encoding utf8
Get-FileHash -LiteralPath (Join-Path $projectRoot 'artifacts/静读.apk') -Algorithm SHA256 | Format-List | Out-File (Join-Path $runDirectory 'tested-release-apk.txt') -Encoding utf8
$passed=$exitCode -eq 0 -and ($report -match '^PASS \d+ release UI assertions')
$screenshots=if($passed -and $Mode -eq 'branding'){@('icon','01-brand-home','02-brand-aggregate')}elseif($passed){@('01-live-board','02-live-reader','03-large-font','04-home-overflow','04b-search-retained')}else{@('failure')}
$normalRemote="/sdcard/Android/data/app.quietreader/files/release-qa/normal-$normalRunId"
foreach($name in $screenshots){& $adb -s $serial pull "$normalRemote/$name.png" $runDirectory}
$filterShots=& $adb -s $serial shell ls $normalRemote
foreach($shot in $filterShots){if($shot -match '^video-filtered-\d+\.png$'){& $adb -s $serial pull "$normalRemote/$shot" $runDirectory}}
if($exitCode -ne 0 -or !($report -match '^PASS \d+ release UI assertions')){throw 'Release instrumentation failed; see assertion output.'}
