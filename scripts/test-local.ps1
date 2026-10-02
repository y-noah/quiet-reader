$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
# Run against an already running, isolated project emulator. Never a connected phone.
& "$PSScriptRoot/test-navigation-recovery.ps1"
& "$PSScriptRoot/test-identity.ps1"
& "$PSScriptRoot/test-session-reuse.ps1"
& "$PSScriptRoot/test-dynamic-reading.ps1"
& "$PSScriptRoot/test-experience.ps1"
& "$PSScriptRoot/test-experience.ps1" -Mode smzdm-cache
& "$PSScriptRoot/release.ps1"
& "$PSScriptRoot/test-release.ps1" -KeepData
& "$PSScriptRoot/test-offline.ps1"
Get-FileHash -LiteralPath (Join-Path $projectRoot 'artifacts/静读.apk') -Algorithm SHA256
Write-Output 'Local regression passed. Review screenshots and real-source/login gaps before delivery. API 26 remains a separate compatibility gate.'
