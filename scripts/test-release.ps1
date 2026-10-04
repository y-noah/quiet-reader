param([switch]$KeepData,[ValidateSet('branding')][string]$Mode='branding',[string]$GradleInit='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$env:JAVA_HOME=(Get-ChildItem (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if((& $adb -s emulator-5556 emu avd name) -notcontains 'quiet-reader-test'){throw 'Only the isolated project emulator is allowed'}
$tasks=@('assembleDebugAndroidTest','-PreleaseUi');if($GradleInit){$tasks=@('-I',$GradleInit)+$tasks}
& "$PSScriptRoot/build.ps1" -Tasks $tasks
$credential=Import-Clixml -LiteralPath (Join-Path $projectRoot '.tools/signing/password.xml')
$previousSecret=$env:QR_STORE_PASSWORD
try {
    $env:QR_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    & "$projectRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$projectRoot/.tools/signing/quiet-reader.jks" --ks-key-alias quiet-reader --ks-pass env:QR_STORE_PASSWORD --key-pass env:QR_STORE_PASSWORD --out "$projectRoot/artifacts/release-test.apk" "$projectRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if($LASTEXITCODE -ne 0){throw 'Test signing failed'}
} finally {$env:QR_STORE_PASSWORD=$previousSecret;$credential=$null}
foreach($apk in @('app/build/outputs/apk/release/app-release.apk','artifacts/release-test.apk')){
    & $adb -s emulator-5556 install -r "$projectRoot/$apk"
    if($LASTEXITCODE -ne 0){throw 'Installation failed'}
}
$evidence=Join-Path $projectRoot ('artifacts/release-ui-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $evidence | Out-Null
$report=& $adb -s emulator-5556 shell am instrument -w app.quietreader.test/app.quietreader.ReleaseInstrumentation
$instrumentExit=$LASTEXITCODE
$report | Set-Content -Encoding utf8 (Join-Path $evidence 'instrumentation.txt');$report | Write-Output
$location=$report | Select-String 'Output: (.+)' | Select-Object -Last 1
if($location){& $adb -s emulator-5556 pull $location.Matches.Groups[1].Value.Trim() $evidence;if($LASTEXITCODE -ne 0){throw 'Evidence pull failed'}}
if($instrumentExit -ne 0 -or !($report -match '^checks=\d+ failures=0$') -or ($report -match '^FAIL |^ERROR |INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed') -or !$location){throw 'Release UI failed'}
