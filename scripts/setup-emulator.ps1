param([ValidateSet(35,36)][int]$Api=35,[switch]$UpgradeProbe,[switch]$Offline)
$ErrorActionPreference='Stop'
if($UpgradeProbe -and $Api -ne 36){throw 'The normal-package upgrade probe requires API 36'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot=Join-Path $projectRoot '.tools'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$env:Path="$env:JAVA_HOME\bin;"+$env:Path
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
$avd=if($UpgradeProbe){'quiet-reader-upgrade-api36'}elseif($Api -eq 36){'quiet-reader-api36'}else{'quiet-reader-test'}
$package="system-images;android-$Api;google_apis;x86_64"
if($Offline){
    if(!(Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME "system-images/android-$Api/google_apis/x86_64/package.xml"))){throw 'The requested Android image is not installed'}
}else{
    & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" $package "platforms;android-$Api"
    if($LASTEXITCODE -ne 0){throw 'Emulator package installation failed'}
}
if(!(Test-Path -LiteralPath (Join-Path $env:ANDROID_AVD_HOME "$avd.ini"))) {
    'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd --name $avd --package $package --device pixel_6
    if($LASTEXITCODE -ne 0){throw 'AVD creation failed'}
}
& "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check
