param([string[]]$Tasks = @('testDebugUnitTest','assembleDebug','lintDebug'))
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME=Join-Path $toolRoot 'gradle-cache'
$env:Path="$env:JAVA_HOME\bin;"+$env:Path
if (!(Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'))) {
    1..50 | ForEach-Object { 'y' } | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" --licenses | Out-Null
    & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
}
Push-Location $projectRoot
try { & "$toolRoot\gradle-8.11.1\bin\gradle.bat" --no-daemon @Tasks; if($LASTEXITCODE -ne 0) { throw 'Build failed' } }
finally { Pop-Location }
