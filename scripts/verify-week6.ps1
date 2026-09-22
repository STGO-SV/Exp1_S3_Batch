#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)][long]$AccountId = 101,
    [string]$TrustedCertificate = (Join-Path $PSScriptRoot '../.local/tls/localhost.crt')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Week5.Http.ps1')
. (Join-Path $PSScriptRoot 'Week6.Config.ps1')
if (!(Test-Path -LiteralPath $TrustedCertificate -PathType Leaf)) { throw 'Falta el certificado público de laboratorio.' }
$client = New-Week5HttpClient -TrustedCertificate $TrustedCertificate
$channels = @(
    @{ Name = 'WEB'; Service = 'banco-legacy-web-bff'; Port = 8081; Paths = @('dashboard') },
    @{ Name = 'MOBILE'; Service = 'banco-legacy-mobile-bff'; Port = 8082; Paths = @('summary', 'movements') },
    @{ Name = 'ATM'; Service = 'banco-legacy-atm-bff'; Port = 8083; Paths = @('balance', 'movements') }
)
try {
    foreach ($channel in $channels) {
        $config = Invoke-RestMethod -Uri "http://localhost:8888/$($channel.Service)/default" -TimeoutSec 5
        if (@($config.propertySources).Count -eq 0) { throw "Config Server no devolvió $($channel.Service)." }
        $logicalNames = Get-Week6AccountServiceNames -PropertySources @($config.propertySources)
        if ('banco-legacy-account-service' -notin $logicalNames) {
            throw "Config Server no entregó el nombre lógico de Account Service a $($channel.Service)."
        }
    }
    $accountConfig = Invoke-RestMethod -Uri 'http://localhost:8888/banco-legacy-account-service/default' -TimeoutSec 5
    if (@($accountConfig.propertySources).Count -eq 0) { throw 'Config Server no devolvió Account Service.' }
    $registry = Invoke-RestMethod -Uri 'http://localhost:8761/eureka/apps' -Headers @{ Accept = 'application/json' } -TimeoutSec 5
    $registered = @($registry.applications.application | Where-Object { $_.instance.status -eq 'UP' } |
        ForEach-Object { $_.name })
    foreach ($required in @('BANCO-LEGACY-WEB-BFF', 'BANCO-LEGACY-MOBILE-BFF',
            'BANCO-LEGACY-ATM-BFF', 'BANCO-LEGACY-ACCOUNT-SERVICE')) {
        if ($required -notin $registered) { throw "Eureka no contiene $required UP." }
    }
    $tokens = Get-Week5Tokens -Client $client
    $results = [ordered]@{ ConfigServer = 'PASS'; Eureka = 'PASS'; RegisteredServices = ($registered -join ',') }
    foreach ($channel in $channels) {
        $prefix = "https://localhost:$($channel.Port)/api/$($channel.Name.ToLower())/accounts/$AccountId"
        foreach ($path in $channel.Paths) {
            $reply = Invoke-Week5Http -Client $client -Url "$prefix/$path" -Token $tokens[$channel.Name]
            if ($reply.Status -ne 200) { throw "$($channel.Name) $path respondió HTTP $($reply.Status); se esperaba 200." }
            $results["$($channel.Name)_$($path.ToUpper())"] = 200
        }
        $circuit = Invoke-Week5Http -Client $client -Url "https://localhost:$($channel.Port)/actuator/circuitbreakers" -Token $tokens[$channel.Name]
        if ($circuit.Status -ne 200) { throw "Actuator de $($channel.Name) respondió HTTP $($circuit.Status)." }
        $state = ($circuit.Body | ConvertFrom-Json).circuitBreakers.accountService.state
        if ($state -ne 'CLOSED') { throw "Circuit Breaker de $($channel.Name) quedó en $state; se esperaba CLOSED." }
        $results["$($channel.Name)_Circuit"] = $state
    }
    $matrix = [Collections.Generic.List[string]]::new()
    foreach ($tokenChannel in $channels) {
        foreach ($targetChannel in $channels) {
            $path = $targetChannel.Paths[0]
            $url = "https://localhost:$($targetChannel.Port)/api/$($targetChannel.Name.ToLower())/accounts/$AccountId/$path"
            $reply = Invoke-Week5Http -Client $client -Url $url -Token $tokens[$tokenChannel.Name]
            $expected = if ($tokenChannel.Name -eq $targetChannel.Name) { 200 } else { 403 }
            if ($reply.Status -ne $expected) {
                throw "JWT $($tokenChannel.Name) -> $($targetChannel.Name) respondió HTTP $($reply.Status); se esperaba $expected."
            }
            $matrix.Add("$($tokenChannel.Name)->$($targetChannel.Name):$expected")
        }
    }
    $results.JwtMatrix = $matrix -join ', '
    $repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    foreach ($channel in $channels) {
        $source = Join-Path $repo "banco-legacy-$($channel.Name.ToLower())-bff/src/main/java"
        $physical = @(Get-ChildItem -LiteralPath $source -Filter *.java -Recurse |
            Select-String -Pattern 'https?://(?:localhost|127\.0\.0\.1):8085|https?://[^" ]+:8085')
        if ($physical.Count) { throw "Se detectó una URL física de Account Service en $($channel.Name)." }
    }
    $results.LogicalServiceName = 'banco-legacy-account-service'
    $results.PhysicalAccountUrlHardcoded = $false
    $results.OVERALL = 'PASS'
    [pscustomobject]$results
} finally {
    $client.Dispose()
    $tokens = $null
}
