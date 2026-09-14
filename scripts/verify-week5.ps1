#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$WebUrl='https://localhost:8081',
    [string]$MobileUrl='https://localhost:8082',
    [string]$AtmUrl='https://localhost:8083',
    [string]$AuthUrl='https://localhost:8084',
    [long]$AccountId=101,
    [long]$MissingAccountId=9223372036854775807,
    [switch]$InternalHttp,
    [string]$TrustedCertificate,
    [switch]$LabSkipCertificateValidation,
    [string]$Output=(Join-Path $PSScriptRoot '../docs/evidence/semana-5/verification-results.json')
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Week5.Http.ps1"
foreach($url in @($WebUrl,$MobileUrl,$AtmUrl,$AuthUrl)) { Assert-Week5Url $url -InternalHttp:$InternalHttp }
$client=New-Week5HttpClient -TrustedCertificate $TrustedCertificate -LabSkipCertificateValidation:$LabSkipCertificateValidation
$results=[Collections.Generic.List[object]]::new()
function Check([string]$Label,[string]$Url,[string]$Token,[int]$Expected,[string]$Method='GET',[string]$Body='') {
    $reply=Invoke-Week5Http -Client $client -Url $Url -Token $Token -Method $Method -Body $Body
    $results.Add([pscustomobject]@{case=$Label; endpoint=$Url; expected=$Expected; actual=$reply.Status; passed=($reply.Status -eq $Expected); bytes=$reply.Bytes})
    return $reply
}
try {
    $cases=@(
        @{Channel='WEB';Url="$WebUrl/api/web/accounts/$AccountId/dashboard"},
        @{Channel='MOBILE';Url="$MobileUrl/api/mobile/accounts/$AccountId/summary"},
        @{Channel='MOBILE';Url="$MobileUrl/api/mobile/accounts/$AccountId/movements"},
        @{Channel='ATM';Url="$AtmUrl/api/atm/accounts/$AccountId/balance"},
        @{Channel='ATM';Url="$AtmUrl/api/atm/accounts/$AccountId/movements"}
    )
    foreach($case in $cases) {
        # Los tokens nuevos por bloque de endpoints evitan depender del TTL normal de 300 segundos.
        $tokens=Get-Week5Tokens -Client $client -AuthUrl $AuthUrl -InternalHttp:$InternalHttp
        foreach($role in @('WEB','MOBILE','ATM')) {
            $expected=if($role -eq $case.Channel){200}else{403}
            $null=Check "$role -> $($case.Channel)" $case.Url $tokens[$role] $expected
        }
        $null=Check 'missing-token' $case.Url '' 401
        $null=Check 'malformed-token' $case.Url 'not-a-jwt' 401
        $parts=$tokens[$case.Channel].Split('.')
        $signature=$parts[2]
        $replacement=if($signature[0] -eq 'A'){'B'}else{'A'}
        $parts[2]=$replacement+$signature.Substring(1)
        $null=Check 'altered-signature' $case.Url ($parts -join '.') 401
        $missing=$case.Url.Replace("/$AccountId/","/$MissingAccountId/")
        $null=Check 'missing-account' $missing $tokens[$case.Channel] 404
    }
    $tokens=Get-Week5Tokens -Client $client -AuthUrl $AuthUrl -InternalHttp:$InternalHttp
    $balanceUrl="$AtmUrl/api/atm/accounts/$AccountId/balance"
    $before=Invoke-Week5Http -Client $client -Url $balanceUrl -Token $tokens.ATM
    if($before.Status -eq 200) {
        $amount=[decimal](($before.Body | ConvertFrom-Json).availableBalance)
        if($amount -ge 0.01) {
            $reply=Check 'simulated-withdrawal' "$AtmUrl/api/atm/accounts/$AccountId/withdrawals" $tokens.ATM 200 'POST' '{"amount":0.01}'
            $simulated=$reply.Status -eq 200 -and ($reply.Body | ConvertFrom-Json).status -eq 'SIMULATED'
            $results.Add([pscustomobject]@{case='withdrawal-is-simulated';passed=$simulated})
        }
        $excess=(@{amount=($amount+1)} | ConvertTo-Json -Compress)
        $null=Check 'insufficient-funds' "$AtmUrl/api/atm/accounts/$AccountId/withdrawals" $tokens.ATM 400 'POST' $excess
        $after=Invoke-Week5Http -Client $client -Url $balanceUrl -Token $tokens.ATM
        $unchanged=$after.Status -eq 200 -and (($after.Body|ConvertFrom-Json).availableBalance -eq $amount)
        $results.Add([pscustomobject]@{case='balance-unchanged-via-api';passed=$unchanged})
    }
} finally { $client.Dispose(); $tokens=$null }
$report=@{ timestampUtc=[DateTime]::UtcNow.ToString('o'); transport=if($InternalHttp){'explicit-local-http-or-https'}elseif($LabSkipCertificateValidation){'https-laboratory-unverified'}elseif($TrustedCertificate){'https-custom-trust-verified'}else{'https-system-trust-verified'}
    note='No tokens or account payloads persisted. API balance comparison is not a database write audit.'
    results=$results; allPassed=(@($results|Where-Object passed -EQ $false).Count -eq 0) }
[IO.Directory]::CreateDirectory((Split-Path ([IO.Path]::GetFullPath($Output)))) | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Output -Encoding utf8
Write-Host "$($results.Count) verificaciones; éxito global: $($report.allPassed)"
if(!$report.allPassed) { throw 'Hay verificaciones fallidas. Consulte el informe saneado.' }
