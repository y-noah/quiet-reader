param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('emulator-5556','emulator-5558','emulator-5560','emulator-5562')]
    [string]$Serial,
    [ValidateRange(1,60)][int]$TimeoutSeconds=60
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME=Join-Path $taskRoot '.tools/android-user'
$taskAdb=Join-Path $taskRoot '.tools/android-sdk/platform-tools/adb.exe'
$taskDeadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    $taskState=(& $taskAdb -s $Serial get-state 2>$null) -join ''
    if($LASTEXITCODE -eq 0 -and $taskState.Trim() -eq 'device'){
        $taskBoot=(& $taskAdb -s $Serial shell getprop sys.boot_completed 2>$null) -join ''
        $taskUser=(& $taskAdb -s $Serial shell am get-started-user-state 0 2>$null) -join ''
        $taskStorage=(& $taskAdb -s $Serial shell getprop sys.user.0.ce_available 2>$null) -join ''
        if($taskBoot.Trim() -eq '1' -and $taskUser.Trim() -eq 'RUNNING_UNLOCKED' -and $taskStorage.Trim() -eq 'true'){
            [pscustomobject]@{serial=$Serial;bootCompleted=$true;userUnlocked=$true;credentialStorageAvailable=$true}
            return
        }
    }
    Start-Sleep -Seconds 1
} while([DateTime]::UtcNow -lt $taskDeadline)
throw "Emulator $Serial is not yet booted with unlocked user storage. Inspect or wait again; do not reseed app data."
