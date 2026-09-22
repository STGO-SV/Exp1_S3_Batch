#requires -Version 7.0
Set-StrictMode -Version Latest

function Get-Week6AccountPortOwners {
    param([int]$Port)
    # La coma evita que PowerShell desenvuelva el único PID a un UInt32 bajo StrictMode.
    return ,@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
}

function Test-Week6AccountJarCommandLine {
    param([string]$CommandLine, [string]$ExpectedJar)
    if ([string]::IsNullOrWhiteSpace($CommandLine) -or [string]::IsNullOrWhiteSpace($ExpectedJar)) { return $false }
    $match = [regex]::Match($CommandLine, '(?i)(?:^|\s)-jar\s+(?<argument>"[^"]+"|\S+)')
    if (!$match.Success) { return $false }
    $jarArgument = $match.Groups['argument'].Value.Trim('"').Replace('/', '\')
    $expectedAbsolute = [IO.Path]::GetFullPath($ExpectedJar).Replace('/', '\')
    if ([IO.Path]::IsPathFullyQualified($jarArgument)) {
        return [string]::Equals([IO.Path]::GetFullPath($jarArgument), $expectedAbsolute,
            [StringComparison]::OrdinalIgnoreCase)
    }
    if ($jarArgument.StartsWith('.\', [StringComparison]::Ordinal)) { $jarArgument = $jarArgument.Substring(2) }
    $expectedRelative = 'banco-legacy-account-service\target\banco-legacy-account-service-0.0.1-SNAPSHOT.jar'
    return [string]::Equals($jarArgument, $expectedRelative, [StringComparison]::OrdinalIgnoreCase)
}

function Get-Week6VerifiedAccountProcess {
    param([int]$Port, [string]$ExpectedJar)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -ne 1) {
        throw [InvalidOperationException]::new("Puerto $Port`: se encontraron $($listeners.Count) listeners; se requiere exactamente uno.")
    }
    $ownerPid = [int]$listeners[0].OwningProcess
    try { $process = Get-Process -Id $ownerPid -ErrorAction Stop } catch {
        throw [InvalidOperationException]::new("PID $ownerPid`: no se pudo consultar el proceso del puerto $Port.")
    }
    if ($process.ProcessName -notin @('java', 'javaw')) {
        throw [InvalidOperationException]::new("PID $ownerPid`: el proceso '$($process.ProcessName)' no es Java.")
    }
    try { $details = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerPid" -ErrorAction Stop } catch {
        throw [InvalidOperationException]::new("PID $ownerPid`: no se pudo consultar la CommandLine de Java.")
    }
    if ($null -eq $details -or $details.Name -notin @('java.exe', 'javaw.exe')) {
        throw [InvalidOperationException]::new("PID $ownerPid`: Win32_Process no confirmó el ejecutable Java.")
    }
    if ($details.ExecutablePath -and ([IO.Path]::GetFileName($details.ExecutablePath) -notin @('java.exe', 'javaw.exe'))) {
        throw [InvalidOperationException]::new("PID $ownerPid`: la ruta del ejecutable no corresponde a Java.")
    }
    if (!(Test-Week6AccountJarCommandLine -CommandLine $details.CommandLine -ExpectedJar $ExpectedJar)) {
        throw [InvalidOperationException]::new("PID $ownerPid`: Java confirmado, pero la CommandLine no referencia el JAR esperado de Account Service.")
    }
    return [pscustomobject]@{ Pid = $ownerPid; ProcessName = $process.ProcessName; JavaConfirmed = $true; JarRecognized = $true }
}
