#requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $root "target/week7-consumers/current.json"
if (!(Test-Path -LiteralPath $statePath)) {
    Write-Host "No existe un grupo de consumers registrado."
    exit 0
}

$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($entry in $state.processes) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Write-Host "$($entry.instance): ya estaba detenido (PID $($entry.pid))."
        continue
    }
    if ($process.ProcessName -notin @("java", "javaw")) {
        throw "Se rechazó detener PID $($entry.pid): ahora pertenece a '$($process.ProcessName)'."
    }
    $registeredStart = ([datetime]$entry.startedAt).ToUniversalTime()
    if ($process.StartTime.ToUniversalTime() -ne $registeredStart) {
        throw "Se rechazó detener PID $($entry.pid): su hora de inicio no coincide con el proceso registrado."
    }
    Stop-Process -Id $entry.pid
    Wait-Process -Id $entry.pid -Timeout 15 -ErrorAction SilentlyContinue
    Write-Host "$($entry.instance): detenido (PID $($entry.pid))."
}

$state | Add-Member -NotePropertyName stoppedAt -NotePropertyValue ((Get-Date).ToUniversalTime().ToString("O")) -Force
$state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8
