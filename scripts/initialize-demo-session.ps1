#requires -Version 7.0
<#
Inicializa una sesión local para Banco Legacy Semana 8.

- Genera una pareja RSA-2048 efímera para firma JWT.
- Genera secretos OAuth2 de los tres clientes técnicos.
- Configura issuer y audience.
- No escribe secretos ni claves en disco.

Debe cargarse mediante dot-source desde PowerShell 7:

    . .\scripts\initialize-demo-session.ps1
#>

[CmdletBinding()]
param(
    [string]$Issuer = 'https://localhost:8084',
    [string]$Audience = 'banco-bff'
)

$ErrorActionPreference = 'Stop'

function New-OAuthClientSecret {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)

    return [Convert]::ToBase64String($bytes).
        TrimEnd('=').
        Replace('+', '-').
        Replace('/', '_')
}

if ($env:JWT_PRIVATE_KEY -or $env:JWT_PUBLIC_KEY) {
    throw 'Ya hay claves JWT en la sesión. Use una sesión nueva para evitar rotarlas accidentalmente.'
}

$rsa = [Security.Cryptography.RSA]::Create(2048)

try {
    $env:JWT_PRIVATE_KEY = [Convert]::ToBase64String(
        $rsa.ExportPkcs8PrivateKey()
    )

    $env:JWT_PUBLIC_KEY = [Convert]::ToBase64String(
        $rsa.ExportSubjectPublicKeyInfo()
    )
}
finally { $rsa.Dispose() }

$webSecret = New-OAuthClientSecret
$mobileSecret = New-OauthClientSecret
$atmSecret = New-OAuthClientSecret

try {
    [Environment]::SetEnvironmentVariable(
        'OAUTH_WEB_CLIENT_SECRET',
        $webSecret,
        'Process'
    )

    [Environment]::SetEnvironmentVariable(
        'OAUTH_MOBILE_CLIENT_SECRET',
        $mobileSecret,
        'Process'
    )

    [Environment]::SetEnvironmentVariable(
        'OAUTH_ATM_CLIENT_SECRET',
        $atmSecret,
        'Process'
    )
}
finally {
    $webSecret = $null
    $mobileSecret = $null
    $atmSecret = $null
}

$env:OAUTH_ISSUER = $Issuer
$env:JWT_AUDIENCE = $Audience

Write-Host ''
Write-Host 'Sesión local Semana 8 inicializada correctamente.'
Write-Host "OAuth issuer: $env:OAUTH_ISSUER"
Write-Host "JWT audience: $env:JWT_AUDIENCE"
Write-Host 'JWT RSA : pareja efímera RSA-2048 cargada'
Write-Host 'OAuth clients : web, mobile y atm configurados'
Write-Host ''
Write-Host 'No se escribieron claves ni secretos en el disco.'
