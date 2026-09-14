#requires -Version 7.0
# Adaptación directa del script del profesor: mismo certificado para Auth y los tres BFF.
[CmdletBinding()]
param([switch]$Force)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$services = @('banco-legacy-auth','banco-legacy-web-bff','banco-legacy-mobile-bff','banco-legacy-atm-bff')
$destinations = @($services | ForEach-Object { Join-Path $repoRoot "$_/src/main/resources/keystore.p12" })
foreach ($path in $destinations) {
    if (!(Test-Path (Split-Path $path))) { throw "No existe el directorio del módulo: $path" }
    if ((Test-Path $path) -and !$Force) { throw 'Ya existen keystores. Use -Force sólo para rotarlos deliberadamente.' }
}
if (!$env:TLS_KEYSTORE_PASSWORD) { $env:TLS_KEYSTORE_PASSWORD = 'changeit' } # Contraseña oficial de laboratorio.
if ($env:TLS_KEYSTORE_PASSWORD.Length -lt 6) { throw 'PKCS12 requiere contraseña de al menos seis caracteres.' }
$localDirectory = Join-Path $repoRoot '.local/tls'
[IO.Directory]::CreateDirectory($localDirectory) | Out-Null
$tempKeystore = Join-Path $localDirectory ('temporary-'+[guid]::NewGuid().ToString('N')+'.p12')
$certificate = Join-Path $localDirectory 'localhost.crt'
try {
    & keytool -genkeypair -alias bff-local -keyalg RSA -keysize 2048 -storetype PKCS12 `
        -keystore $tempKeystore -validity 365 -storepass:env TLS_KEYSTORE_PASSWORD -keypass:env TLS_KEYSTORE_PASSWORD `
        -dname 'CN=localhost, OU=BackendIII, O=DuocUC, L=VinaDelMar, ST=Valparaiso, C=CL' `
        -ext 'SAN=dns:localhost,ip:127.0.0.1'
    if ($LASTEXITCODE -ne 0) { throw 'keytool no pudo generar el keystore.' }
    # Exporta sólo el certificado público para curl --cacert o confianza local. Nunca exporta la clave privada.
    & keytool -exportcert -rfc -alias bff-local -keystore $tempKeystore `
        -storepass:env TLS_KEYSTORE_PASSWORD -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'keytool no pudo exportar el certificado público.' }
    foreach ($path in $destinations) { Copy-Item -LiteralPath $tempKeystore -Destination $path -Force }
} finally {
    $resolvedTemp = [IO.Path]::GetFullPath($tempKeystore)
    if (!$resolvedTemp.StartsWith($localDirectory + [IO.Path]::DirectorySeparatorChar)) { throw 'Ruta temporal fuera del directorio previsto.' }
    if (Test-Path -LiteralPath $resolvedTemp) { Remove-Item -LiteralPath $resolvedTemp -Force }
}
Write-Host 'Certificado de laboratorio copiado a Auth, Web, Mobile y ATM; los keystores están ignorados por Git.'
Write-Host 'Certificado público: .local/tls/localhost.crt. No se modificó la confianza del sistema.'
