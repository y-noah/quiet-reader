$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $projectRoot '.tools/android-sdk'
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$env:ANDROID_AVD_HOME=Join-Path $projectRoot '.tools/avd'
$adb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$existing=& $adb devices
if($existing -match '^emulator-5558\s+device') {
    $name=& $adb -s emulator-5558 emu avd name
    if($name -notcontains 'quiet-reader-api26'){throw 'Port 5558 belongs to another emulator.'}
    Write-Output 'Existing quiet-reader-api26 emulator is running.';return
}
if(!(Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'system-images/android-26/google_apis/x86_64/package.xml'))){throw 'Install the API 26 system image first.'}
if(!(Test-Path -LiteralPath (Join-Path $env:ANDROID_AVD_HOME 'quiet-reader-api26.ini'))) {
    'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd --name quiet-reader-api26 --package 'system-images;android-26;google_apis;x86_64' --device pixel_2
    if($LASTEXITCODE -ne 0){throw 'API 26 AVD creation failed'}
}
$process=Start-Process -FilePath "$env:ANDROID_HOME/emulator/emulator.exe" -ArgumentList @('-avd','quiet-reader-api26','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader_indirect','-memory','1536','-port','5558') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$projectRoot/artifacts/emulator-api26.log" -RedirectStandardError "$projectRoot/artifacts/emulator-api26-error.log"
Write-Output "Started API 26 emulator process $($process.Id) on port 5558."
