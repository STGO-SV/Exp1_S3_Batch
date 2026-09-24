#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ContainerName = "banco-legacy-kafka",
    [string]$BootstrapServer = "localhost:19092",
    [string]$ConsumerGroup = "banco-legacy-anomaly-processors"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$topics = @("banco.transacciones.anomalas.v1", "banco.transacciones.anomalas.v1.DLT")
$failures = [Collections.Generic.List[string]]::new()

if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker no está disponible." }
$health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0) { throw "No existe el contenedor $ContainerName." }
if ($health -ne "healthy") { $failures.Add("broker no healthy: $health") }
Write-Host "Kafka container: $ContainerName ($health)"

foreach ($topic in $topics) {
    $description = docker exec $ContainerName /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BootstrapServer --describe --topic $topic
    if ($LASTEXITCODE -ne 0) { $failures.Add("no se pudo describir $topic"); continue }
    $joined = $description -join "`n"
    if ($joined -notmatch 'PartitionCount:\s*3') { $failures.Add("$topic no tiene 3 particiones") }
    if ($joined -notmatch 'ReplicationFactor:\s*1') { $failures.Add("$topic no tiene replication factor 1") }
    Write-Host $joined
}

$groupDescription = docker exec $ContainerName /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server $BootstrapServer --group $ConsumerGroup --describe
if ($LASTEXITCODE -ne 0) {
    $failures.Add("no se pudo describir el consumer group $ConsumerGroup")
} else {
    Write-Host ($groupDescription -join "`n")
    $dataRows = @($groupDescription | Where-Object { $_ -match '^\s*\S+\s+\S+\s+\d+' })
    if ($dataRows.Count -ne 3) { $failures.Add("el grupo no muestra exactamente 3 particiones asignadas") }
    $lags = @($dataRows | ForEach-Object { ($_.Trim() -split '\s+')[5] } | Where-Object { $_ -match '^\d+$' })
    if ($lags.Count -ne 3) { $failures.Add("no fue posible determinar lag para las 3 particiones") }
    elseif (@($lags | Where-Object { [long]$_ -ne 0 }).Count -gt 0) { $failures.Add("existe lag distinto de cero: $($lags -join ',')") }
}

if ($failures.Count -gt 0) {
    Write-Host "WEEK7 VERIFICATION: FAIL"
    $failures | ForEach-Object { Write-Host "- $_" }
    exit 1
}
Write-Host "WEEK7 VERIFICATION: PASS"
Write-Host "Broker healthy; tópicos 3xRF1; consumer group con 3 particiones y lag 0."
