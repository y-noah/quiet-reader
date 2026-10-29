param([ValidateSet('swiftshader','software','host','swiftshader_indirect')][string]$Gpu='swiftshader',[switch]$EnableVulkan,[ValidateSet('quiet-reader-test','quiet-reader-api26','quiet-reader-api36','quiet-reader-upgrade-api36')][string]$Avd='quiet-reader-test')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$adb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$port=if($Avd -eq 'quiet-reader-api26'){5558}elseif($Avd -eq 'quiet-reader-api36'){5560}elseif($Avd -eq 'quiet-reader-upgrade-api36'){5562}else{5556}
$serial="emulator-$port"
$devices=& $adb devices
if($devices -match "^$serial\s+device") {
    if((& $adb -s $serial emu avd name) -notcontains $Avd){throw "Port $port belongs to another AVD."}
    Write-Output "Existing $Avd emulator is running; launch options were not changed.";return
}
if(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(emulator|qemu-system).*\.exe$' -and $_.CommandLine -match ('-avd\s+'+[regex]::Escape($Avd)+'(?:\s|$)') }) {
    throw 'The project emulator process already exists but adb is not ready; inspect its state rather than starting a duplicate.'
}
$logRoot=Join-Path $projectRoot ('artifacts/emulator-start-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$launchArguments=@('-avd',$Avd,'-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu',$Gpu,'-port',"$port")
# Project-only graphics compatibility experiment; never disable platform/network security.
if(!$EnableVulkan){$launchArguments+=@('-feature','-Vulkan')}
$process=Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList $launchArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logRoot\emulator.log" -RedirectStandardError "$logRoot\emulator-error.log"
[pscustomobject]@{pid=$process.Id;avd=$Avd;arguments=$launchArguments;started=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logRoot 'launch.json') -Encoding utf8
Write-Output "Emulator PID: $($process.Id); launch evidence: $logRoot"
