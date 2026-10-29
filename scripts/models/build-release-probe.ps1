param([switch]$Offline,[switch]$IsolatedInstall,[switch]$FlowInstall,[switch]$NormalUpgrade,[switch]$DiscoveryInstall)
$ErrorActionPreference='Stop'
if(([int]$IsolatedInstall.IsPresent+[int]$FlowInstall.IsPresent+[int]$NormalUpgrade.IsPresent+[int]$DiscoveryInstall.IsPresent) -gt 1){throw 'Choose one diagnostic package mode'}
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$toolRoot='C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/.tools'
$env:JAVA_HOME=Join-Path $toolRoot 'jdk/jdk-21.0.12.1+1'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME=Join-Path $toolRoot 'gradle-cache'
$env:Path="$env:JAVA_HOME/bin;"+$env:Path
$nativeRoot='C:/Users/10197/Documents/Codex/quiet-reader-native'
$nativeSource=Join-Path $nativeRoot 'sources/sentencepiece-31646a467d2051eb904e0b45de3a73e91fe1c1e3'
$credential=Import-Clixml -LiteralPath (Join-Path $toolRoot 'signing/password.xml')
$previousSecret=$env:QR_STORE_PASSWORD
Push-Location $projectRoot
try {
    $env:QR_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    $arguments=@('-PreleaseDiscoveryUi','-PbundleLocalModels',"-PsentencepieceSource=$nativeSource","-PnativeBuildRoot=$nativeRoot/build",'assembleRelease','assembleDebugAndroidTest','--console=plain','--max-workers=4')
    if($IsolatedInstall){$arguments+='-PisolatedReleaseProbe'}
    if($FlowInstall){$arguments+='-PisolatedReleaseFlowProbe'}
    if($DiscoveryInstall){$arguments+=@('-PisolatedReleaseFlowProbe','-PisolatedDiscoveryProbe')}
    if($NormalUpgrade){$arguments+='-PnormalUpgradeProbe'}
    if($Offline){$arguments+='--offline'}
    & "$toolRoot/gradle-8.11.1/bin/gradle.bat" @arguments
    if($LASTEXITCODE -ne 0){throw 'Local minified diagnostic build failed'}
    $artifactName=if($DiscoveryInstall){'release-discovery-emulator'}elseif($NormalUpgrade){'release-discovery-upgrade'}elseif($FlowInstall){'release-discovery-flow'}elseif($IsolatedInstall){'release-discovery-isolated'}else{'release-discovery'}
    $testApk="artifacts/local-ai/$artifactName-test.apk"
    $appApk="artifacts/local-ai/$artifactName-app.apk"
    Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $appApk -Force
    & "$toolRoot/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$toolRoot/signing/quiet-reader.jks" --ks-key-alias quiet-reader --ks-pass env:QR_STORE_PASSWORD --key-pass env:QR_STORE_PASSWORD --out $testApk 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if($LASTEXITCODE -ne 0){throw 'Diagnostic test signing failed'}
    # Keep the emulator discovery target upgrade-compatible with its debug install.
    # Only this isolated artifact is re-signed; the original R8 output is retained.
    if($DiscoveryInstall){
        foreach($apk in @($appApk,$testApk)){
            & "$toolRoot/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$toolRoot/android-user/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android $apk
            if($LASTEXITCODE -ne 0){throw 'Emulator artifact signing failed'}
        }
    }
    $hashes=@($appApk,$testApk) | ForEach-Object { $file=Get-Item -LiteralPath $_; [pscustomobject]@{file=$file.Name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} }
    $hashes | ConvertTo-Json | Set-Content -LiteralPath "artifacts/local-ai/$artifactName-package.json" -Encoding UTF8
} finally {$env:QR_STORE_PASSWORD=$previousSecret;$credential=$null;Pop-Location}


