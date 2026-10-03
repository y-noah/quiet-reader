param([string]$Query='西贝')
$ErrorActionPreference='Stop'
$qrRoot=Split-Path $PSScriptRoot -Parent
$qrJdk=(Get-ChildItem -LiteralPath (Join-Path $qrRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
$qrJsoup=Get-ChildItem -LiteralPath (Join-Path $qrRoot '.tools/gradle-cache/caches/modules-2/files-2.1/org.jsoup/jsoup/1.23.2') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$qrJson=Get-ChildItem -LiteralPath (Join-Path $qrRoot '.tools/gradle-cache/caches/modules-2/files-2.1/org.json/json/20240303') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$qrOutput=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $qrOutput | Out-Null
$qrCp="$qrJsoup;$qrJson;$qrRoot/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes;$qrOutput"
& "$qrJdk/bin/javac.exe" -encoding UTF-8 -cp $qrCp -d $qrOutput "$PSScriptRoot/TimelineFeasibility.java"
if($LASTEXITCODE -ne 0){throw 'Prototype compilation failed'}
Push-Location $qrRoot
try { & "$qrJdk/bin/java.exe" -cp $qrCp TimelineFeasibility $Query; if($LASTEXITCODE -ne 0){throw 'Prototype failed'} } finally { Pop-Location }
