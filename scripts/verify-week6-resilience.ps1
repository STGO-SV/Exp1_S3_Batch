#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)][long]$AccountId = 101,
    [string]$TrustedCertificate = (Join-Path $PSScriptRoot '../.local/tls/localhost.crt'),
    [string]$EvidenceDirectory = (Join-Path $PSScriptRoot '../docs/evidence/semana-6/live')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Week5.Http.ps1')
. (Join-Path $PSScriptRoot 'Week6.Process.ps1')

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$accountJar = Join-Path $repo 'banco-legacy-account-service/target/banco-legacy-account-service-0.0.1-SNAPSHOT.jar'
$accountKeystore = Join-Path $repo '.local/tls/account-service-keystore.p12'
$accountPort = 8085
$channels = @(
    @{ Name = 'WEB'; Port = 8081; Paths = @('dashboard') },
    @{ Name = 'MOBILE'; Port = 8082; Paths = @('summary', 'movements') },
    @{ Name = 'ATM'; Port = 8083; Paths = @('balance', 'movements') }
)
$phase = 'Prerequisitos'
$accountStopped = $false
$client = $null
$tokens = $null
$evidenceRun = $null
$accountProcess = $null
$result = [ordered]@{
    InitialHealthyChain = 'PENDING'
    InitialCircuitState = 'PENDING'
    AccountStopped = 'PENDING'
    Fallback503 = 'PENDING'
    CircuitOpened = 'PENDING'
    HalfOpenObserved = 'PENDING'
    AccountRestarted = 'PENDING'
    EurekaReregistered = 'PENDING'
    RecoveryHttpStatus = $null
    FinalCircuitState = 'PENDING'
    RestorationAfterFailure = 'NOT_NEEDED'
    FailedPhase = $null
    OVERALL = 'FAIL'
}

function Assert-Week6([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw [InvalidOperationException]::new($Message) }
}

function Get-EurekaServices {
    try {
        $registry = Invoke-RestMethod -Uri 'http://localhost:8761/eureka/apps' -Headers @{ Accept = 'application/json' } -TimeoutSec 5
        return @($registry.applications.application)
    } catch { throw [InvalidOperationException]::new('Eureka no respondió con el registro de servicios.') }
}

function Get-CircuitState($Channel) {
    $reply = Invoke-Week5Http -Client $client -Url "https://localhost:$($Channel.Port)/actuator/circuitbreakers" -Token $tokens[$Channel.Name]
    Assert-Week6 ($reply.Status -eq 200) "Actuator Circuit Breakers de $($Channel.Name) respondió HTTP $($reply.Status)."
    try {
        $state = ($reply.Body | ConvertFrom-Json).circuitBreakers.accountService.state
        Assert-Week6 (![string]::IsNullOrWhiteSpace($state)) 'Actuator no devolvió el estado de accountService.'
        return [string]$state
    } catch { throw [InvalidOperationException]::new('No se pudo leer el estado de accountService en Actuator.') }
}

function Get-OpenWaitMilliseconds($Config) {
    $key = 'resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state'
    $value = $null
    foreach ($source in $Config.propertySources) {
        $property = $source.source.PSObject.Properties[$key]
        if ($property) { $value = [string]$property.Value; break }
    }
    Assert-Week6 (![string]::IsNullOrWhiteSpace($value)) 'Config Server no entregó wait-duration-in-open-state.'
    if ($value -notmatch '^(\d+)(ms|s|m)$') {
        throw [InvalidOperationException]::new('wait-duration-in-open-state tiene un formato no soportado.')
    }
    $number = [long]$Matches[1]
    $factor = switch ($Matches[2]) { 'ms' { 1 }; 's' { 1000 }; 'm' { 60000 } }
    $milliseconds = $number * $factor
    Assert-Week6 ($milliseconds -ge 100 -and $milliseconds -le 300000) 'wait-duration-in-open-state está fuera del límite seguro de esta prueba.'
    return [int]$milliseconds
}

function Save-Week6Evidence([string]$Name, [string]$Status, [int]$HttpStatus = 0, [string]$CircuitState = '') {
    $record = [ordered]@{ Phase = $Name; ObservedAt = (Get-Date).ToString('o'); Result = $Status }
    if ($HttpStatus) { $record.HttpStatus = $HttpStatus }
    if ($CircuitState) { $record.CircuitState = $CircuitState }
    $path = Join-Path $evidenceRun ($Name + '.json')
    $record | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $path -Encoding utf8
}

function Start-AccountService {
    $password = [Environment]::GetEnvironmentVariable('ACCOUNT_TLS_KEYSTORE_PASSWORD', 'Process')
    if (!$password) { $password = [Environment]::GetEnvironmentVariable('TLS_KEYSTORE_PASSWORD', 'Process') }
    if (!$password) { $password = 'changeit' } # Contraseña oficial del laboratorio.
    $info = [Diagnostics.ProcessStartInfo]::new('java')
    $info.WorkingDirectory = $repo
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.Environment['ACCOUNT_TLS_KEYSTORE'] = ([Uri]$accountKeystore).AbsoluteUri
    $info.Environment['ACCOUNT_TLS_KEYSTORE_PASSWORD'] = $password
    $info.ArgumentList.Add('-jar')
    $info.ArgumentList.Add($accountJar)
    return [Diagnostics.Process]::Start($info)
}

function Wait-AccountPort([Diagnostics.Process]$Process, [int]$Seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { return $false }
        if ($Process.Id -in (Get-Week6AccountPortOwners -Port $accountPort)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

try {
    foreach ($name in @('JWT_PUBLIC_KEY', 'DB_URL', 'DB_USER', 'DB_PASSWORD',
            'DEMO_WEB_PASSWORD', 'DEMO_MOBILE_PASSWORD', 'DEMO_ATM_PASSWORD')) {
        Assert-Week6 (![string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) "Falta la variable requerida $name."
    }
    Assert-Week6 (Test-Path -LiteralPath $accountJar -PathType Leaf) 'Falta el JAR de Account Service.'
    Assert-Week6 (Test-Path -LiteralPath $accountKeystore -PathType Leaf) 'Falta el keystore externo de Account Service.'
    Assert-Week6 (Test-Path -LiteralPath $TrustedCertificate -PathType Leaf) 'Falta el certificado público de laboratorio.'
    $client = New-Week5HttpClient -TrustedCertificate $TrustedCertificate
    $tokens = Get-Week5Tokens -Client $client

    $phase = 'Estado sano inicial'
    try {
        $config = Invoke-RestMethod -Uri 'http://localhost:8888/banco-legacy-mobile-bff/default' -TimeoutSec 5
    } catch { throw [InvalidOperationException]::new('Config Server no respondió para Mobile.') }
    Assert-Week6 (@($config.propertySources).Count -gt 0) 'Config Server no devolvió configuración para Mobile.'
    foreach ($channel in $channels) {
        if ($channel.Name -eq 'MOBILE') { continue }
        try {
            $channelConfig = Invoke-RestMethod -Uri "http://localhost:8888/banco-legacy-$($channel.Name.ToLower())-bff/default" -TimeoutSec 5
        } catch { throw [InvalidOperationException]::new("Config Server no respondió para $($channel.Name).") }
        Assert-Week6 (@($channelConfig.propertySources).Count -gt 0) "Config Server no devolvió configuración para $($channel.Name)."
    }
    $openWaitMilliseconds = Get-OpenWaitMilliseconds $config
    $services = Get-EurekaServices
    $names = @($services | ForEach-Object { $_.name })
    foreach ($channel in $channels) {
        Assert-Week6 ("BANCO-LEGACY-$($channel.Name)-BFF" -in $names) "$($channel.Name) no figura en Eureka."
        Assert-Week6 (@($services | Where-Object {
            $_.name -eq "BANCO-LEGACY-$($channel.Name)-BFF" -and $_.instance.status -eq 'UP'
        }).Count -eq 1) "$($channel.Name) no tiene un único registro UP en Eureka."
    }
    Assert-Week6 ('BANCO-LEGACY-ACCOUNT-SERVICE' -in $names) 'Account Service no figura en Eureka.'
    $initialAccountRegistration = @($services | Where-Object { $_.name -eq 'BANCO-LEGACY-ACCOUNT-SERVICE' })
    Assert-Week6 ($initialAccountRegistration.Count -eq 1) 'Se esperaba un único registro de Account Service.'
    Assert-Week6 ($initialAccountRegistration[0].instance.status -eq 'UP') 'Account Service no figura UP en Eureka.'
    $initialEurekaUpdate = [long]$initialAccountRegistration[0].instance.lastUpdatedTimestamp
    foreach ($channel in $channels) {
        foreach ($path in $channel.Paths) {
            $url = "https://localhost:$($channel.Port)/api/$($channel.Name.ToLower())/accounts/$AccountId/$path"
            $initial = Invoke-Week5Http -Client $client -Url $url -Token $tokens[$channel.Name]
            Assert-Week6 ($initial.Status -eq 200) "$($channel.Name) $path inicial respondió HTTP $($initial.Status)."
        }
    }
    $result.InitialHealthyChain = 'PASS'
    foreach ($channel in $channels) {
        $initialCircuit = Get-CircuitState $channel
        Assert-Week6 ($initialCircuit -eq 'CLOSED') "El Circuit Breaker inicial de $($channel.Name) está en $initialCircuit; se requiere CLOSED."
    }
    $result.InitialCircuitState = 'CLOSED'

    $evidenceRun = Join-Path $EvidenceDirectory (Get-Date -Format 'yyyyMMdd-HHmmss')
    [IO.Directory]::CreateDirectory($evidenceRun) | Out-Null
    Save-Week6Evidence '01-healthy' 'PASS' 200 'CLOSED'

    $phase = 'Identificación de Account Service'
    $verifiedAccount = Get-Week6VerifiedAccountProcess -Port $accountPort -ExpectedJar $accountJar
    $ownerPid = $verifiedAccount.Pid

    $phase = 'Detención de Account Service'
    $verifiedBeforeStop = Get-Week6VerifiedAccountProcess -Port $accountPort -ExpectedJar $accountJar
    Assert-Week6 ($verifiedBeforeStop.Pid -eq $ownerPid) 'Cambió el proceso de Account Service antes de detenerlo.'
    $accountStopped = $true
    Stop-Process -Id $ownerPid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Week6AccountPortOwners -Port $accountPort).Count -gt 0 -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 250 }
    Assert-Week6 ((Get-Week6AccountPortOwners -Port $accountPort).Count -eq 0) 'El puerto 8085 siguió escuchando tras detener Account Service.'
    $result.AccountStopped = 'PASS'
    Save-Week6Evidence '02-account-stopped' 'PASS'

    $phase = 'Fallback y circuito OPEN'
    foreach ($channel in $channels) {
        $opened = $false
        for ($attempt = 1; $attempt -le 10; $attempt++) {
            foreach ($path in $channel.Paths) {
                $url = "https://localhost:$($channel.Port)/api/$($channel.Name.ToLower())/accounts/$AccountId/$path"
                $reply = Invoke-Week5Http -Client $client -Url $url -Token $tokens[$channel.Name]
                Assert-Week6 ($reply.Status -eq 503) "$($channel.Name) $path respondió HTTP $($reply.Status) durante la caída; se esperaba 503."
                try { $fallbackCode = ($reply.Body | ConvertFrom-Json).code } catch { $fallbackCode = $null }
                Assert-Week6 ($fallbackCode -eq 'ACCOUNT_SERVICE_UNAVAILABLE') "$($channel.Name) $path no devolvió el fallback esperado."
            }
            if ((Get-CircuitState $channel) -eq 'OPEN') { $opened = $true; break }
            Start-Sleep -Milliseconds 200
        }
        Assert-Week6 $opened "El Circuit Breaker de $($channel.Name) no alcanzó OPEN."
        Save-Week6Evidence "03-fallback-$($channel.Name.ToLower())" 'PASS' 503
        Save-Week6Evidence "04-open-$($channel.Name.ToLower())" 'PASS' 503 'OPEN'
    }
    $result.Fallback503 = 'PASS'
    Save-Week6Evidence '03-fallback' 'PASS' 503
    $result.CircuitOpened = 'PASS'

    $phase = 'Transición a HALF_OPEN'
    foreach ($channel in $channels) {
        $deadline = [DateTime]::UtcNow.AddMilliseconds($openWaitMilliseconds + 10000)
        $halfOpen = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            if ((Get-CircuitState $channel) -eq 'HALF_OPEN') { $halfOpen = $true; break }
            Start-Sleep -Milliseconds 250
        }
        Assert-Week6 $halfOpen "El Circuit Breaker de $($channel.Name) no alcanzó HALF_OPEN."
        Save-Week6Evidence "05-half-open-$($channel.Name.ToLower())" 'PASS' 0 'HALF_OPEN'
    }
    $result.HalfOpenObserved = 'PASS'

    $phase = 'Reinicio de Account Service'
    Assert-Week6 ((Get-Week6AccountPortOwners -Port $accountPort).Count -eq 0) 'El puerto 8085 está ocupado; no se iniciará otro Account Service.'
    $accountProcess = Start-AccountService
    Assert-Week6 ($null -ne $accountProcess) 'No se pudo iniciar el proceso de Account Service.'
    Assert-Week6 (Wait-AccountPort $accountProcess 45) 'Account Service no abrió el puerto 8085 tras reiniciarse.'
    $result.AccountRestarted = 'PASS'
    Save-Week6Evidence '06-account-restarted' 'PASS'

    $phase = 'Nuevo registro en Eureka'
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $registered = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        Assert-Week6 (!$accountProcess.HasExited) 'Account Service terminó antes de registrarse nuevamente.'
        $services = Get-EurekaServices
        $registered = @($services | Where-Object {
            $_.name -eq 'BANCO-LEGACY-ACCOUNT-SERVICE' -and $_.instance.status -eq 'UP' -and
            [long]$_.instance.lastUpdatedTimestamp -gt $initialEurekaUpdate
        }).Count -gt 0
        if ($registered) { break }
        Start-Sleep -Seconds 1
    }
    Assert-Week6 $registered 'Account Service no figuró UP en Eureka tras el reinicio.'
    foreach ($channel in $channels) {
        Assert-Week6 (@($services | Where-Object {
            $_.name -eq "BANCO-LEGACY-$($channel.Name)-BFF" -and $_.instance.status -eq 'UP'
        }).Count -eq 1) "$($channel.Name) ya no figura UP en Eureka."
    }
    $result.EurekaReregistered = 'PASS'
    Save-Week6Evidence '07-eureka-reregistered' 'PASS'

    $phase = 'Recuperación Mobile y circuito CLOSED'
    foreach ($channel in $channels) {
        foreach ($path in $channel.Paths) {
            $url = "https://localhost:$($channel.Port)/api/$($channel.Name.ToLower())/accounts/$AccountId/$path"
            $recovered = Invoke-Week5Http -Client $client -Url $url -Token $tokens[$channel.Name]
            Assert-Week6 ($recovered.Status -eq 200) "$($channel.Name) $path de recuperación respondió HTTP $($recovered.Status)."
        }
        $finalCircuit = Get-CircuitState $channel
        Assert-Week6 ($finalCircuit -eq 'CLOSED') "El Circuit Breaker final de $($channel.Name) quedó en $finalCircuit."
        Save-Week6Evidence "09-closed-$($channel.Name.ToLower())" 'PASS' 200 'CLOSED'
    }
    $result.RecoveryHttpStatus = 200
    Save-Week6Evidence '08-recovery' 'PASS' 200
    $result.FinalCircuitState = 'CLOSED'
    $result.OVERALL = 'PASS'
    Save-Week6Evidence '10-overall' 'PASS'
} catch {
    $result.FailedPhase = $phase
    $reason = if ($_.Exception -is [InvalidOperationException]) { $_.Exception.Message } else { 'Error inesperado; revise los servicios sin exponer credenciales.' }
    Write-Warning "Falló la fase '$phase': $reason"
    if ($accountStopped) {
        if ((Get-Week6AccountPortOwners -Port $accountPort).Count -eq 0) {
            try {
                $accountProcess = Start-AccountService
                if ($accountProcess -and (Wait-AccountPort $accountProcess 45)) {
                    $result.RestorationAfterFailure = 'PORT_8085_RESTORED'
                } else {
                    $result.RestorationAfterFailure = 'FAILED'
                }
            } catch { $result.RestorationAfterFailure = 'FAILED' }
        } else {
            $result.RestorationAfterFailure = if ($accountProcess -and $accountProcess.Id -in (Get-Week6AccountPortOwners -Port $accountPort)) {
                'ACCOUNT_RUNNING'
            } else { 'PORT_8085_OCCUPIED' }
        }
    }
    if ($evidenceRun) { Save-Week6Evidence '10-overall' 'FAIL' }
} finally {
    if ($client) { $client.Dispose() }
    $tokens = $null
}

$result.EvidenceDirectory = $evidenceRun
[pscustomobject]$result
if ($result.OVERALL -ne 'PASS') { exit 1 }
