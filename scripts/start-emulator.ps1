param([ValidateSet('swiftshader','software','host','swiftshader_indirect')][string]$Gpu='swiftshader',[switch]$EnableVulkan)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$adb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$devices=& $adb devices
if($devices -match '^emulator-5556\s+device') {
    if((& $adb -s emulator-5556 emu avd name) -notcontains 'quiet-reader-test'){throw 'Port 5556 belongs to another AVD.'}
    Write-Output 'Existing quiet-reader-test emulator is running; launch options were not changed.';return
}
if(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(emulator|qemu-system).*\.exe$' -and $_.CommandLine -match '-avd\s+quiet-reader-test(?:\s|$)' }) {
    throw 'The project emulator process already exists but adb is not ready; inspect its state rather than starting a duplicate.'
}
$logRoot=Join-Path $projectRoot ('artifacts/emulator-start-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$launchArguments=@('-avd','quiet-reader-test','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu',$Gpu,'-port','5556')
# Project-only graphics compatibility experiment; never disable platform/network security.
if(!$EnableVulkan){$launchArguments+=@('-feature','-Vulkan')}
$process=Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList $launchArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logRoot\emulator.log" -RedirectStandardError "$logRoot\emulator-error.log"
[pscustomobject]@{pid=$process.Id;avd='quiet-reader-test';arguments=$launchArguments;started=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logRoot 'launch.json') -Encoding utf8
Write-Output "Emulator PID: $($process.Id); launch evidence: $logRoot"
