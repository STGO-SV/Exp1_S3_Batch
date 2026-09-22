#requires -Version 7.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../Week6.Process.ps1')

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

$expectedJar = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../banco-legacy-account-service/target/banco-legacy-account-service-0.0.1-SNAPSHOT.jar'))
$relative = '"java" -jar .\banco-legacy-account-service\target\banco-legacy-account-service-0.0.1-SNAPSHOT.jar'
$absolute = '"C:\Program Files\Java\bin\java.exe" -jar "' + $expectedJar.Replace('\', '/') + '"'
$unrelated = '"java" -jar C:\Other\banco-legacy-account-service\target\banco-legacy-account-service-0.0.1-SNAPSHOT.jar'

Assert-True (Test-Week6AccountJarCommandLine $relative $expectedJar) 'No reconoció la ruta relativa.'
Assert-True (Test-Week6AccountJarCommandLine $absolute $expectedJar) 'No reconoció la ruta absoluta entre comillas y con slash.'
Assert-True (Test-Week6AccountJarCommandLine $relative.ToUpperInvariant() $expectedJar) 'No reconoció diferencias de casing.'
Assert-True (!(Test-Week6AccountJarCommandLine $unrelated $expectedJar)) 'Aceptó el mismo nombre de JAR en otro directorio.'
Assert-True (!(Test-Week6AccountJarCommandLine '"java" -jar C:\Other\otro-servicio.jar' $expectedJar)) 'Aceptó un Java no relacionado.'

$script:mockCommandLine = $relative
$script:mockProcessName = 'java'
$script:mockOwnerPids = @([uint32]25208)
function Get-NetTCPConnection {
    [CmdletBinding()]
    param([int]$LocalPort, [string]$State)
    foreach ($ownerPid in $script:mockOwnerPids) {
        [pscustomobject]@{ OwningProcess = $ownerPid }
    }
}
function Get-Process {
    [CmdletBinding()]
    param([int]$Id)
    return [pscustomobject]@{ ProcessName = $script:mockProcessName }
}
function Get-CimInstance {
    [CmdletBinding()]
    param([string]$ClassName, [string]$Filter)
    return [pscustomobject]@{
        Name = 'java.exe'
        ExecutablePath = 'C:\Program Files\Java\bin\java.exe'
        CommandLine = $script:mockCommandLine
    }
}

$owners = Get-Week6AccountPortOwners -Port 8085
Assert-True ($owners -is [array] -and $owners.Count -eq 1 -and $owners[0] -eq 25208) 'El único PID no permaneció como matriz bajo StrictMode.'
$verified = Get-Week6VerifiedAccountProcess -Port 8085 -ExpectedJar $expectedJar
Assert-True ($verified.Pid -eq 25208 -and $verified.JavaConfirmed -and $verified.JarRecognized) 'No verificó el proceso Java esperado.'
$script:mockCommandLine = $unrelated
try {
    Get-Week6VerifiedAccountProcess -Port 8085 -ExpectedJar $expectedJar | Out-Null
    throw 'La verificación aceptó un JAR de otro directorio.'
} catch [InvalidOperationException] {
    Assert-True ($_.Exception.Message -match 'JAR esperado') 'El rechazo no explicó la causa lógica.'
}
$script:mockCommandLine = $relative
$script:mockProcessName = 'otro-servicio'
try {
    Get-Week6VerifiedAccountProcess -Port 8085 -ExpectedJar $expectedJar | Out-Null
    throw 'La verificación aceptó un proceso que no es Java.'
} catch [InvalidOperationException] {
    Assert-True ($_.Exception.Message -match 'no es Java') 'El rechazo de otro proceso no explicó la causa lógica.'
}
$script:mockProcessName = 'java'
$script:mockOwnerPids = @([uint32]25208, [uint32]25209)
try {
    Get-Week6VerifiedAccountProcess -Port 8085 -ExpectedJar $expectedJar | Out-Null
    throw 'La verificación aceptó múltiples listeners.'
} catch [InvalidOperationException] {
    Assert-True ($_.Exception.Message -match 'exactamente uno') 'El rechazo de múltiples listeners no explicó la causa lógica.'
}
'PASS: PID único, Java y JAR esperado; rutas relativas y absolutas; rechazo de procesos no relacionados.'
