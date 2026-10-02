$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$staging=Join-Path $projectRoot ('.tools/source-package-'+[Guid]::NewGuid().ToString('N')+'/quiet-reader')
New-Item -ItemType Directory -Force -Path (Join-Path $staging 'app') | Out-Null
foreach($file in @('README.md','DEVELOPMENT.md','VERIFICATION.md','VERIFICATION-0.2.md','VERIFICATION-0.2.1.md','VERIFICATION-0.2.2.md','VERIFICATION-0.2.3.md','VERIFICATION-0.2.4.md','VERIFICATION-0.2.5.md','VERIFICATION-0.2.6.md','VERIFICATION-0.2.7.md','VERIFICATION-0.2.8.md','VERIFICATION-0.3.0.md','VERIFICATION-0.3.1.md','design-qa.md','EXPLORATORY-QA.md','.gitignore','build.gradle','settings.gradle','gradle.properties')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination $staging
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.2.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.3.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.4.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.5.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.6.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'LIVE-QA-0.3.5.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'LIVE-QA-0.3.6.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.7.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'LIVE-QA-0.3.6-remaining.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'LATENCY-0.3.6.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'IDENTITY-QA.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.8.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.9.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.10.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'VERIFICATION-0.3.11.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'WEIBO-READING-QA.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'WEIBO-READER-JOURNEY-QA.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'WEIBO-RECOVERY-QA.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'IMAGE-LAYOUT-QA.md') -Destination $staging
Copy-Item -LiteralPath (Join-Path $projectRoot 'scripts') -Destination $staging -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/src') -Destination (Join-Path $staging 'app') -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/build.gradle'),(Join-Path $projectRoot 'app/proguard-rules.pro') -Destination (Join-Path $staging 'app')
$zip=Join-Path $projectRoot 'artifacts/静读-源码.zip'
Compress-Archive -LiteralPath $staging -DestinationPath $zip -Force
Get-Item -LiteralPath $zip | Select-Object FullName,Length
# Deliberately excludes .tools, caches, screenshots, session data and private signing keys.
