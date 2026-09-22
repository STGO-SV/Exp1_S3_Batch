[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'infra\kafka\compose.yaml'
$containerName = 'banco-legacy-kafka'
$topics = @(
    'banco.transacciones.anomalas.v1',
    'banco.transacciones.anomalas.v1.DLT'
)

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)] [string] $Executable,
        [Parameter(Mandatory)] [string[]] $Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $Executable @Arguments
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($exitCode -ne 0) {
        throw "El comando '$Executable $($Arguments -join ' ')' terminó con código $exitCode."
    }
}

function Invoke-Compose {
    param([Parameter(Mandatory)] [string[]] $Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker compose version *> $null
    $composePluginExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($composePluginExitCode -eq 0) {
        Invoke-CheckedCommand -Executable 'docker' -Arguments (@('compose', '-f', $composeFile) + $Arguments)
        return
    }

    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        Invoke-CheckedCommand -Executable 'docker-compose' -Arguments (@('-f', $composeFile) + $Arguments)
        return
    }

    throw 'No se encontró Docker Compose como plugin ni como ejecutable independiente.'
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "No existe el archivo Compose esperado: $composeFile"
}

Invoke-Compose -Arguments @('up', '-d')

$healthy = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerName 2>$null)
    if ($LASTEXITCODE -eq 0 -and $health -eq 'healthy') {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $healthy) {
    & docker logs --tail 100 $containerName
    throw "Kafka no alcanzó el estado healthy dentro del tiempo esperado."
}

foreach ($topic in $topics) {
    Invoke-CheckedCommand -Executable 'docker' -Arguments @(
        'exec', $containerName,
        '/opt/kafka/bin/kafka-topics.sh',
        '--bootstrap-server', 'localhost:19092',
        '--create', '--if-not-exists',
        '--topic', $topic,
        '--partitions', '3',
        '--replication-factor', '1'
    )

    $description = (& docker exec $containerName /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server localhost:19092 --describe --topic $topic) -join "`n"
    if ($LASTEXITCODE -ne 0) {
        throw "No fue posible describir el tópico $topic."
    }
    if ($description -notmatch 'PartitionCount:\s*3' -or $description -notmatch 'ReplicationFactor:\s*1') {
        throw "El tópico $topic no tiene exactamente 3 particiones y replication factor 1.`n$description"
    }
    Write-Output $description
}

Write-Output 'Kafka está healthy y los tópicos requeridos quedaron verificados.'
