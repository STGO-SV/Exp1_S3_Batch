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
    $movementsUrl="$AtmUrl/api/atm/accounts/$AccountId/movements"
    $withdrawalAmount=[decimal]0.01
    $after=$null
    $before=Invoke-Week5Http -Client $client -Url $balanceUrl -Token $tokens.ATM
    if($before.Status -eq 200) {
        $balanceBefore=[decimal](($before.Body | ConvertFrom-Json).availableBalance)
        if($balanceBefore -ge $withdrawalAmount) {
            $reply=Check 'completed-withdrawal' "$AtmUrl/api/atm/accounts/$AccountId/withdrawals" $tokens.ATM 200 'POST' '{"amount":0.01}'
            $withdrawal=if($reply.Status -eq 200){$reply.Body | ConvertFrom-Json}else{$null}
            $completed=$null -ne $withdrawal -and $withdrawal.status -eq 'COMPLETED' -and
                [decimal]$withdrawal.balanceBefore -eq $balanceBefore -and
                [decimal]$withdrawal.balanceAfter -eq ($balanceBefore-$withdrawalAmount)
            $results.Add([pscustomobject]@{case='withdrawal-completed';passed=$completed})

            $after=Invoke-Week5Http -Client $client -Url $balanceUrl -Token $tokens.ATM
            $balanceChanged=$after.Status -eq 200 -and
                [decimal](($after.Body|ConvertFrom-Json).availableBalance) -eq ($balanceBefore-$withdrawalAmount)
            $results.Add([pscustomobject]@{case='balance-decreased-by-withdrawal';passed=$balanceChanged})

            $movements=Invoke-Week5Http -Client $client -Url $movementsUrl -Token $tokens.ATM
            $movementList=if($movements.Status -eq 200){@($movements.Body|ConvertFrom-Json)}else{@()}
            $registered=$movementList.Count -gt 0 -and $movementList[0].type -eq 'retiro' -and
                [decimal]$movementList[0].amount -eq $withdrawalAmount
            $results.Add([pscustomobject]@{case='withdrawal-movement-registered';passed=$registered})
        }
        $currentBalance=if($null -ne $after -and $after.Status -eq 200){[decimal](($after.Body|ConvertFrom-Json).availableBalance)}else{$balanceBefore}
        $excess=(@{amount=($currentBalance+1)} | ConvertTo-Json -Compress)
        $null=Check 'insufficient-funds' "$AtmUrl/api/atm/accounts/$AccountId/withdrawals" $tokens.ATM 400 'POST' $excess
    }
} finally { $client.Dispose(); $tokens=$null }
$report=@{ timestampUtc=[DateTime]::UtcNow.ToString('o'); transport=if($InternalHttp){'explicit-local-http-or-https'}elseif($LabSkipCertificateValidation){'https-laboratory-unverified'}elseif($TrustedCertificate){'https-custom-trust-verified'}else{'https-system-trust-verified'}
    note='No tokens or account payloads persisted in this report. This verification performs one real withdrawal against the explicitly supplied laboratory account.'
    results=$results; allPassed=(@($results|Where-Object passed -EQ $false).Count -eq 0) }
[IO.Directory]::CreateDirectory((Split-Path ([IO.Path]::GetFullPath($Output)))) | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Output -Encoding utf8
Write-Host "$($results.Count) verificaciones; éxito global: $($report.allPassed)"
if(!$report.allPassed) { throw 'Hay verificaciones fallidas. Consulte el informe saneado.' }
