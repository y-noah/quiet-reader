param([Parameter(Mandatory=$true)][string]$Url)
$ErrorActionPreference='Stop'
$ua='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'
try {
    $r=Invoke-WebRequest -Uri $Url -UserAgent $ua -TimeoutSec 18 -MaximumRedirection 5 -SkipHttpErrorCheck
    $body=[string]$r.Content
    $title=[regex]::Match($body,'(?is)<title[^>]*>(.*?)</title>').Groups[1].Value
    $links=@([regex]::Matches($body,'(?is)(?:src|action|href)=["'']([^"'']+)["'']') | ForEach-Object {$_.Groups[1].Value} | Where-Object {$_ -match 'search|\.js(?:\?|$)'} | Select-Object -Unique -First 18)
    [pscustomobject]@{url=$Url;status=[int]$r.StatusCode;final=$r.BaseResponse.RequestMessage.RequestUri.AbsoluteUri;length=$body.Length;title=$title;links=$links;sample=($body.Substring(0,[Math]::Min(600,$body.Length)))} | ConvertTo-Json -Depth 3
} catch { [pscustomobject]@{url=$Url;error=$_.Exception.Message} | ConvertTo-Json }
