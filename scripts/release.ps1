param([string]$GradleInit='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$signingRoot=Join-Path $projectRoot '.tools/signing'
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$keyFile=Join-Path $signingRoot 'quiet-reader.jks'
$passwordFile=Join-Path $signingRoot 'password.xml'
$jdkRoot=(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
if ((Test-Path -LiteralPath $keyFile) -and !(Test-Path -LiteralPath $passwordFile)) {throw 'Signing key exists without its local password file; do not overwrite the key.'}
if (!(Test-Path -LiteralPath $passwordFile)) {
    $randomBytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$rng.GetBytes($randomBytes)} finally {$rng.Dispose()}
    $secret=ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
    $credential=New-Object System.Management.Automation.PSCredential('quiet-reader',$secret)
    # Windows DPAPI: generated signing secret is only readable by this Windows user.
    $credential | Export-Clixml -LiteralPath $passwordFile
}
$credential=Import-Clixml -LiteralPath $passwordFile
$previousSecret=$env:QR_STORE_PASSWORD
try {
    $env:QR_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    if (!(Test-Path -LiteralPath $keyFile)) {
        & "$jdkRoot/bin/keytool.exe" -genkeypair -keystore $keyFile -storetype JKS -alias quiet-reader -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Quiet Reader Personal, O=Personal, C=CN' -storepass:env QR_STORE_PASSWORD -keypass:env QR_STORE_PASSWORD
        if($LASTEXITCODE -ne 0){throw 'Signing key generation failed'}
    }
    $tasks=@('testDebugUnitTest','assembleDebug','assembleDebugAndroidTest','assembleRelease','lintRelease')
    if($GradleInit){$tasks=@('-I',$GradleInit)+$tasks}
    & "$PSScriptRoot/build.ps1" -Tasks $tasks
    if($LASTEXITCODE -ne 0){throw 'Release build failed'}
    $apk=Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    & "$projectRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat" verify --verbose $apk
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
    New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'artifacts') | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $projectRoot 'artifacts/静读.apk')
    Copy-Item -LiteralPath $apk -Destination (Join-Path $projectRoot 'artifacts/news.apk')
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
} finally {
    $env:QR_STORE_PASSWORD=$previousSecret
    $credential=$null
}
