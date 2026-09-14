#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$WebUrl='https://localhost:8081',
    [string]$MobileUrl='https://localhost:8082',
    [string]$AtmUrl='https://localhost:8083',
    [string]$AuthUrl='https://localhost:8084',
    [long]$AccountId=101,
    [ValidateRange(5,10000)][int]$Samples=50,
    [ValidateRange(1,100)][int]$Warmup=5,
    [string]$DatasetLabel='PostgreSQL legacy existente; tamaño no verificado por este script',
    [switch]$InternalHttp,
    [string]$TrustedCertificate,
    [switch]$LabSkipCertificateValidation,
    [string]$Output=(Join-Path $PSScriptRoot '../docs/evidence/semana-5/bff-benchmark.csv')
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Week5.Http.ps1"
foreach($url in @($WebUrl,$MobileUrl,$AtmUrl,$AuthUrl)) { Assert-Week5Url $url -InternalHttp:$InternalHttp }
$cases = @(
    @{Channel='WEB'; Url="$WebUrl/api/web/accounts/$AccountId/dashboard"},
    @{Channel='MOBILE'; Url="$MobileUrl/api/mobile/accounts/$AccountId/summary"},
    @{Channel='MOBILE'; Url="$MobileUrl/api/mobile/accounts/$AccountId/movements"},
    @{Channel='ATM'; Url="$AtmUrl/api/atm/accounts/$AccountId/balance"},
    @{Channel='ATM'; Url="$AtmUrl/api/atm/accounts/$AccountId/movements"}
)
$client=New-Week5HttpClient -TrustedCertificate $TrustedCertificate -LabSkipCertificateValidation:$LabSkipCertificateValidation
$rows=[Collections.Generic.List[object]]::new()
try {
    foreach($case in $cases) {
        # Renueva fuera de las solicitudes medidas; la emisión del token no forma parte de la latencia del BFF.
        $tokens=Get-Week5Tokens -Client $client -AuthUrl $AuthUrl -InternalHttp:$InternalHttp
        for($i=0;$i -lt $Warmup;$i++) {
            $reply=Invoke-Week5Http -Client $client -Url $case.Url -Token $tokens[$case.Channel]
            if($reply.Status -ne 200) { throw "Warmup falló: $($case.Channel), HTTP $($reply.Status). No se generó un benchmark válido." }
        }
        $values=[Collections.Generic.List[object]]::new()
        for($i=0;$i -lt $Samples;$i++) {
            $values.Add((Invoke-Week5Http -Client $client -Url $case.Url -Token $tokens[$case.Channel]))
        }
        $ok=@($values | Where-Object Status -EQ 200)
        $times=@($ok | ForEach-Object Milliseconds | Sort-Object)
        $median=$null; $p95=$null; $payloadMedian=$null
        if($times.Count -gt 0) {
            $mid=[int][Math]::Floor($times.Count/2)
            $median=if($times.Count % 2) {$times[$mid]} else {($times[$mid-1]+$times[$mid])/2}
            $p95=$times[[int][Math]::Ceiling(0.95*$times.Count)-1]
            $sizes=@($ok.Bytes | Sort-Object)
            $payloadMedian=$sizes[[int][Math]::Floor($sizes.Count/2)]
        }
        $rows.Add([pscustomobject]@{
            timestampUtc=[DateTime]::UtcNow.ToString('o'); channel=$case.Channel; endpoint=$case.Url
            accountId=$AccountId; dataset=$DatasetLabel; samples=$Samples; successes=$ok.Count
            payloadBytesMedian=$payloadMedian; medianMs=$median; p95Ms=$p95; errors=($Samples-$ok.Count)
            warmup=$Warmup; concurrency=1; certificateValidated=(([Uri]$case.Url).Scheme.Equals('https') -and !$LabSkipCertificateValidation); trustMode=if($LabSkipCertificateValidation){'laboratory-unverified'}elseif($TrustedCertificate){'custom-certificate'}else{'system'}
        })
    }
} finally { $client.Dispose(); $tokens=$null }
$parent=Split-Path ([IO.Path]::GetFullPath($Output))
[IO.Directory]::CreateDirectory($parent) | Out-Null
$rows | Export-Csv -LiteralPath $Output -NoTypeInformation -Encoding utf8
$rows | Select-Object channel,samples,payloadBytesMedian,medianMs,p95Ms,errors | Format-Table
if(@($rows | Where-Object errors -GT 0).Count) { throw 'Benchmark guardado con errores; no presentarlo como validación exitosa.' }
