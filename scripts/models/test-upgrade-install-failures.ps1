param([ValidateSet('emulator-5562')][string]$Serial='emulator-5562')
$ErrorActionPreference='Stop'
$toolRoot='C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/.tools'
$adb=Join-Path $toolRoot 'android-sdk/platform-tools/adb.exe'
if(((& $adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim() -ne '1'){throw 'Dedicated emulator required'}
if((@(& $adb -s $Serial emu avd name)) -notcontains 'quiet-reader-upgrade-api36'){throw 'Wrong upgrade AVD'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$report=[ordered]@{serial=$Serial;scope='Rejected installation preserves existing v47 binary and synthetic legacy state';checks=@()}
function InstalledHash {
    $line=((& $adb -s $Serial shell pm path app.quietreader) | Select-Object -First 1).Trim()
    if($line -notmatch '^package:(/data/app/[A-Za-z0-9_~=/+.-]+/base\.apk)$'){throw 'Unexpected installed APK path'}
    $output=& $adb -s $Serial shell sha256sum $Matches[1]
    if($LASTEXITCODE -ne 0){throw 'Installed hash failed'}
    return ($output -split '\s+')[0]
}
function VerifyState([string]$label) {
    $output=(& $adb -s $Serial shell am instrument -w -e phase normal-upgrade-verify app.quietreader.test/app.quietreader.ReleaseDiscoveryInstrumentation) -join "`n"
    $output | Set-Content -Encoding UTF8 "artifacts/local-ai/normal-upgrade-$label-$stamp.txt"
    if($output -notmatch 'checks=11 failures=0'){throw 'Retained-state validation failed'}
}
function RejectedInstall([string]$apk,[string]$expected,[string]$label,[string]$before) {
    $output=(& $adb -s $Serial install --no-incremental -r $apk 2>&1 | ForEach-Object { $_.ToString() }) -join "`n"
    $installExit=$LASTEXITCODE
    $output | Set-Content -Encoding UTF8 "artifacts/local-ai/normal-upgrade-install-$label-$stamp.txt"
    $after=InstalledHash
    $passed=$installExit -ne 0 -and $output.Contains($expected) -and $after -eq $before
    $script:report.checks+=@{case=$label;exit=$installExit;expectedError=$expected;rejected=$output.Contains($expected);beforeSha256=$before;afterSha256=$after;passed=$passed}
    if(!$passed){throw 'Install was not rejected as expected or binary identity changed'}
    VerifyState $label
}
$original='artifacts/News-0.3.35.apk'
if((Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash -ne '6310e4579497f59134aaadbf81c128ed0a0f341445b200f881dd5583e94e9aa2'){throw 'Original fixture hash mismatch'}
$before=InstalledHash
VerifyState 'pre-failure'
RejectedInstall $original 'INSTALL_FAILED_VERSION_DOWNGRADE' 'downgrade-rejected' $before
$malformed="artifacts/local-ai/normal-upgrade-truncated-$stamp.apk"
$inputStream=[IO.File]::OpenRead((Join-Path (Get-Location) 'artifacts/local-ai/release-discovery-upgrade-app.apk'))
try{$bytes=New-Object byte[] 1024;$read=$inputStream.Read($bytes,0,$bytes.Length);if($read -ne 1024){throw 'Source APK too short'};[IO.File]::WriteAllBytes((Join-Path (Get-Location) $malformed),$bytes)}finally{$inputStream.Dispose()}
RejectedInstall $malformed 'INSTALL_PARSE_FAILED' 'malformed-rejected' $before
$report | ConvertTo-Json -Depth 5 | Tee-Object -FilePath "artifacts/local-ai/normal-upgrade-install-failures-$stamp.json"
