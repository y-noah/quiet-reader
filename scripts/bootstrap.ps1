$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
function Download($url, $name) {
    $target = Join-Path $toolRoot $name
    if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri $url -OutFile $target }
    return $target
}
if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'jdk'))) {
    $jdkInfo = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $jdk = $jdkInfo[0].binary.package
    $jdkZip = Download $jdk.link $jdk.name
    if ((Get-FileHash -LiteralPath $jdkZip -Algorithm SHA256).Hash.ToLower() -ne $jdk.checksum) { throw 'JDK checksum mismatch' }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath (Join-Path $toolRoot 'jdk')
}
$gradleZip = Download 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle.zip'
$hashResponse = (Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Content
$gradleHash = if ($hashResponse -is [byte[]]) { [Text.Encoding]::UTF8.GetString($hashResponse).Trim() } else { $hashResponse.Trim() }
if ((Get-FileHash -LiteralPath $gradleZip -Algorithm SHA256).Hash.ToLower() -ne $gradleHash) { throw 'Gradle checksum mismatch' }
if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'gradle-8.11.1'))) { Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolRoot }
$sdkZip = Download 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' 'android-cli.zip'
if ((Get-FileHash -LiteralPath $sdkZip -Algorithm SHA256).Hash.ToLower() -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') { throw 'Android CLI checksum mismatch' }
if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'sdk-cli'))) { Expand-Archive -LiteralPath $sdkZip -DestinationPath (Join-Path $toolRoot 'sdk-cli') }
$sdkCli=Join-Path $toolRoot 'android-sdk/cmdline-tools/latest'
if(!(Test-Path -LiteralPath $sdkCli)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $sdkCli -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $toolRoot 'sdk-cli/cmdline-tools') -Destination $sdkCli -Recurse
}
Write-Output 'Portable JDK, Gradle and Android CLI ready. No global environment variables changed.'
