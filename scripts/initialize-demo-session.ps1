#requires -Version 7.0
# Cargue este script con dot-source en la sesión PowerShell usada para iniciar Auth/BFF. No genera archivos ni certificados TLS.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
if ($env:JWT_PRIVATE_KEY -or $env:JWT_PUBLIC_KEY) {
    throw 'Ya hay claves JWT en la sesión. Use una sesión nueva para evitar rotarlas accidentalmente.'
}
$rsa = [Security.Cryptography.RSA]::Create(2048)
try {
    $env:JWT_PRIVATE_KEY = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
    $env:JWT_PUBLIC_KEY = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
} finally { $rsa.Dispose() }
foreach ($channel in @('WEB', 'MOBILE', 'ATM')) {
    $secure = Read-Host "Contraseña demo $channel (12–72 bytes UTF-8)" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ($value.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($value) -gt 72) {
            throw 'Contraseña fuera del rango permitido. Reinicie la sesión.'
        }
        [Environment]::SetEnvironmentVariable("DEMO_${channel}_PASSWORD", $value, 'Process')
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $value = $null
        $secure.Dispose()
    }
}
$env:JWT_ISSUER = 'banco-legacy-auth'
$env:JWT_AUDIENCE = 'banco-bff'
Write-Host 'Claves JWT efímeras y contraseñas cargadas en esta sesión; no se han escrito archivos.'
