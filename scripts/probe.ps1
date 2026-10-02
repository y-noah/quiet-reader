param([string]$Url)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:GRADLE_USER_HOME=Join-Path $toolRoot 'gradle-cache'
$env:Path="$env:JAVA_HOME\bin;"+$env:Path
Push-Location $projectRoot
try { & "$toolRoot\gradle-8.11.1\bin\gradle.bat" --no-daemon liveProbe "-PprobeUrl=$Url" } finally {Pop-Location}
