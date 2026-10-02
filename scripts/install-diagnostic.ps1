param([ValidateSet('emulator-5556','emulator-5558')][string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $projectRoot '.tools/android-user'
$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
$adb=Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
if((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1'){throw 'Only project emulator allowed'}
$expectedAvd=if($Serial -eq 'emulator-5558'){'quiet-reader-api26'}else{'quiet-reader-test'}
if((& $adb -s $Serial emu avd name) -notcontains $expectedAvd){throw 'Wrong project AVD'}
$credential=Import-Clixml -LiteralPath (Join-Path $projectRoot '.tools/signing/password.xml')
$previousSecret=$env:QR_STORE_PASSWORD
try {
    $env:QR_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    $entries=@(@('app/build/outputs/apk/debug/app-debug.apk','.tools/diagnostic-app.apk'),@('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','.tools/diagnostic-test.apk'))
    foreach($entry in $entries){
        & "$projectRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat" sign --ks "$projectRoot/.tools/signing/quiet-reader.jks" --ks-key-alias quiet-reader --ks-pass env:QR_STORE_PASSWORD --key-pass env:QR_STORE_PASSWORD --out "$projectRoot/$($entry[1])" "$projectRoot/$($entry[0])"
        if($LASTEXITCODE -ne 0){throw 'Diagnostic signing failed'}
        & $adb -s $Serial install -r "$projectRoot/$($entry[1])"
        if($LASTEXITCODE -ne 0){throw 'Diagnostic installation failed'}
    }
} finally {$env:QR_STORE_PASSWORD=$previousSecret;$credential=$null}
