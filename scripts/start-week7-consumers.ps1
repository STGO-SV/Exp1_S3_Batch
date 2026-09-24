#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$JarPath = "banco-legacy-anomaly-service/target/banco-legacy-anomaly-service-0.0.1-SNAPSHOT.jar",
    [string]$BootstrapServers = "localhost:9092",
    [string]$DatabaseUrl = "jdbc:postgresql://localhost:5432/banco_legacy",
    [string]$DatabaseUsername = "postgres",
    [string]$DatabasePassword = "postgres"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jar = [IO.Path]::GetFullPath((Join-Path $root $JarPath))
if (!(Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "No existe el JAR $jar. Ejecute .\mvnw.cmd -pl banco-legacy-anomaly-service -am package antes de iniciar los consumers."
}

$runtimeRoot = Join-Path $root "target/week7-consumers"
$statePath = Join-Path $runtimeRoot "current.json"
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null

if (Test-Path -LiteralPath $statePath) {
    $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $active = @($previous.processes | Where-Object { Get-Process -Id $_.pid -ErrorAction SilentlyContinue })
    if ($active.Count -gt 0) {
        throw "Ya existen $($active.Count) consumers registrados en ejecución. Use .\scripts\stop-week7-consumers.ps1 primero."
    }
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$logDirectory = Join-Path $runtimeRoot $runId
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$processes = @()

foreach ($number in 1..3) {
    $instance = "consumer-$number"
    $stdout = Join-Path $logDirectory "$instance.log"
    $stderr = Join-Path $logDirectory "$instance.err.log"
    $environment = @{
        KAFKA_BOOTSTRAP_SERVERS = $BootstrapServers
        ANOMALY_DB_URL = $DatabaseUrl
        ANOMALY_DB_USERNAME = $DatabaseUsername
        ANOMALY_DB_PASSWORD = $DatabasePassword
        ANOMALY_CONSUMER_INSTANCE = $instance
    }
    $arguments = @(
        "-jar", $jar,
        "--spring.kafka.consumer.group-id=banco-legacy-anomaly-processors",
        "--spring.kafka.listener.concurrency=1",
        "--spring.sql.init.mode=$(if ($number -eq 1) { 'always' } else { 'never' })"
    )
    $process = Start-Process -FilePath "java" -ArgumentList $arguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -Environment $environment
    $processes += [pscustomobject]@{
        instance = $instance
        pid = $process.Id
        startedAt = $process.StartTime.ToUniversalTime().ToString("O")
        stdout = $stdout
        stderr = $stderr
    }
}

Start-Sleep -Seconds 8
foreach ($entry in $processes) {
    if (!(Get-Process -Id $entry.pid -ErrorAction SilentlyContinue)) {
        foreach ($started in $processes) {
            $remaining = Get-Process -Id $started.pid -ErrorAction SilentlyContinue
            if ($null -ne $remaining -and $remaining.ProcessName -in @("java", "javaw")) {
                Stop-Process -Id $started.pid
            }
        }
        throw "$($entry.instance) terminó durante el arranque. Revise $($entry.stderr)."
    }
}

[pscustomobject]@{
    runId = $runId
    jar = $jar
    groupId = "banco-legacy-anomaly-processors"
    concurrencyPerProcess = 1
    bootstrapServers = $BootstrapServers
    logDirectory = $logDirectory
    processes = $processes
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Host "Tres procesos independientes están activos en el grupo banco-legacy-anomaly-processors."
$processes | Format-Table instance, pid, stdout
