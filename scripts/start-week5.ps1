#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$AuthOnly,
    [ValidateSet('auth','web-bff','mobile-bff','atm-bff')]
    [string[]]$Modules
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot
$required = @('JWT_PRIVATE_KEY','JWT_PUBLIC_KEY','DEMO_WEB_PASSWORD','DEMO_MOBILE_PASSWORD','DEMO_ATM_PASSWORD')
if ($AuthOnly -and $Modules) { throw 'Use AuthOnly o Modules, no ambos.' }
$modules = if ($Modules) { @($Modules) } elseif ($AuthOnly) { @('auth') } else { @('auth','web-bff','mobile-bff','atm-bff') }
if (@($modules | Where-Object { $_ -ne 'auth' }).Count) { $required += @('DB_URL','DB_USER','DB_PASSWORD') }
foreach ($name in $required) {
    if (![Environment]::GetEnvironmentVariable($name)) { throw "Falta $name en esta sesión." }
}
# Valida todos los artefactos antes de iniciar cualquier proceso.
foreach ($module in $modules) {
    if (!(Test-Path (Join-Path $repo "banco-legacy-$module/target/banco-legacy-$module-0.0.1-SNAPSHOT.jar"))) {
        throw "Falta el JAR de $module; ejecute mvn clean verify."
    }
}
foreach ($module in $modules) {
    $info = [Diagnostics.ProcessStartInfo]::new('java')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WorkingDirectory = $repo
    $info.ArgumentList.Add('-jar')
    $info.ArgumentList.Add((Join-Path $repo "banco-legacy-$module/target/banco-legacy-$module-0.0.1-SNAPSHOT.jar"))
    if ($module -ne 'auth') {
        foreach ($name in @('JWT_PRIVATE_KEY','DEMO_WEB_PASSWORD','DEMO_MOBILE_PASSWORD','DEMO_ATM_PASSWORD')) {
            $info.Environment.Remove($name) | Out-Null
        }
    } else {
        foreach ($name in @('DB_PASSWORD','DB_USER','DB_URL')) { $info.Environment.Remove($name) | Out-Null }
    }
    $process = [Diagnostics.Process]::Start($info)
    [pscustomobject]@{ Module = $module; ProcessId = $process.Id }
}
# Al terminar, detenga únicamente los identificadores de proceso devueltos. Este script nunca inicia Batch.
