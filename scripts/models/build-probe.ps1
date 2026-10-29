param([switch]$Offline,[switch]$BundleModels,[switch]$IsolatedInstall,[ValidateSet('models','store','back','mobile','reader')][string]$Target='models')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
# Use the existing short shared tool path to avoid the Windows AAPT2 path limit.
$toolRoot='C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/.tools'
$env:JAVA_HOME=Join-Path $toolRoot 'jdk/jdk-21.0.12.1+1'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$env:GRADLE_USER_HOME=Join-Path $toolRoot 'gradle-cache'
$env:Path="$env:JAVA_HOME/bin;"+$env:Path
$runner=if($Target -eq 'reader'){'-PbrowsingExperienceUi'}elseif($Target -eq 'mobile'){'-PmobileLayoutUi'}elseif($Target -eq 'store'){'-PdiscoveryStoreUi'}elseif($Target -eq 'back'){'-PsystemBackUi'}else{'-PlocalAiUi'}
$nativeRoot='C:/Users/10197/Documents/Codex/quiet-reader-native'
$nativeSource=Join-Path $nativeRoot 'sources/sentencepiece-31646a467d2051eb904e0b45de3a73e91fe1c1e3'
if(!(Test-Path -LiteralPath (Join-Path $nativeSource 'CMakeLists.txt'))){throw 'Run prepare_native.py with the short native root before building'}
$arguments=@($runner,"-PsentencepieceSource=$nativeSource","-PnativeBuildRoot=$nativeRoot/build",'testDebugUnitTest','assembleDebug','assembleDebugAndroidTest','--console=plain','--max-workers=4')
if($Offline){$arguments+='--offline'}
if($BundleModels){$arguments+='-PbundleLocalModels'}
if($IsolatedInstall){$arguments+='-PisolatedDiscoveryProbe'}
Push-Location $projectRoot
try{& "$toolRoot/gradle-8.11.1/bin/gradle.bat" @arguments;if($LASTEXITCODE -ne 0){throw 'Model probe build failed'}}finally{Pop-Location}
