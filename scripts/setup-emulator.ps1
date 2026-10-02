$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$env:Path="$env:JAVA_HOME\bin;"+$env:Path
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" 'emulator' 'system-images;android-35;google_apis;x86_64'
if($LASTEXITCODE -ne 0){throw 'Emulator package installation failed'}
if(!(Test-Path -LiteralPath (Join-Path $env:ANDROID_AVD_HOME 'quiet-reader-test.ini'))) {
    'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd --name quiet-reader-test --package 'system-images;android-35;google_apis;x86_64' --device pixel_6
    if($LASTEXITCODE -ne 0){throw 'AVD creation failed'}
}
& "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check
