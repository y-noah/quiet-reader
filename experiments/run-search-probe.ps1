param([ValidateSet('all','remaining')][string]$Source='all')
$ErrorActionPreference='Stop'
$qrRoot=Split-Path $PSScriptRoot -Parent
$qrAdb=Join-Path $qrRoot '.tools/android-sdk/platform-tools/adb.exe'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $qrRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
if ((& $qrAdb -s emulator-5556 emu avd name) -notcontains 'quiet-reader-test') {throw 'Dedicated emulator only'}
$qrVersion=(& $qrAdb -s emulator-5556 shell dumpsys package app.quietreader) -join "`n"
if ($qrVersion -notmatch 'versionName=0.3.15') {throw 'Expected unchanged stable app'}
$qrCredentials=Import-Clixml -LiteralPath (Join-Path $qrRoot '.tools/signing/password.xml')
$qrOldSecret=$env:QR_STORE_PASSWORD
try {
    $env:QR_STORE_PASSWORD=$qrCredentials.GetNetworkCredential().Password
    & "$qrRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$qrRoot/.tools/signing/quiet-reader.jks" --ks-key-alias quiet-reader --ks-pass env:QR_STORE_PASSWORD --key-pass env:QR_STORE_PASSWORD --out "$qrRoot/artifacts/event-search-test.apk" "$qrRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if($LASTEXITCODE -ne 0){throw 'Test signing failed'}
} finally {$env:QR_STORE_PASSWORD=$qrOldSecret;$qrCredentials=$null}
& $qrAdb -s emulator-5556 install -r "$qrRoot/artifacts/event-search-test.apk"
if($LASTEXITCODE -ne 0){throw 'Test component install failed'}
$qrReportDir=Join-Path $qrRoot ('artifacts/event-search-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $qrReportDir | Out-Null
& $qrAdb -s emulator-5556 shell am instrument -w -e mode event-search-probe -e source $Source app.quietreader.test/app.quietreader.ReleaseInstrumentation | Tee-Object -FilePath (Join-Path $qrReportDir 'instrumentation.txt')
$qrText=Get-Content -LiteralPath (Join-Path $qrReportDir 'instrumentation.txt') -Raw
$qrRemote=[regex]::Match($qrText,'Output: (/(?:sdcard|storage/emulated/0)/Android/data/app.quietreader/files/event-search-probe-\d+)').Groups[1].Value
if(!$qrRemote){throw 'Probe incomplete; retain partial output'}
& $qrAdb -s emulator-5556 pull "$qrRemote/report.json" "$qrReportDir/report.json"
if($LASTEXITCODE -ne 0){throw 'Evidence pull failed'}
Write-Output "Evidence: $qrReportDir"
