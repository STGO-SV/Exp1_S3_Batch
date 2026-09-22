#requires -Version 7.0
# Certificado local compartido por los servidores; Account Service usa un keystore externo.
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
$truststore = Join-Path $localDirectory 'week6-truststore.p12'
$accountKeystore = Join-Path $localDirectory 'account-service-keystore.p12'
$obsoleteAccountResource = Join-Path $repoRoot 'banco-legacy-account-service/src/main/resources/keystore.p12'
if ((Test-Path -LiteralPath $accountKeystore) -and !$Force) { throw 'Ya existe el keystore externo de Account Service. Use -Force sólo para rotarlo deliberadamente.' }
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
    Copy-Item -LiteralPath $tempKeystore -Destination $accountKeystore -Force
    if (Test-Path -LiteralPath $truststore) { Remove-Item -LiteralPath $truststore -Force }
    & keytool -importcert -noprompt -alias banco-local-ca -file $certificate -keystore $truststore `
        -storetype PKCS12 -storepass changeit
    if ($LASTEXITCODE -ne 0) { throw 'keytool no pudo generar el truststore local.' }
    if (Test-Path -LiteralPath $obsoleteAccountResource) {
        Remove-Item -LiteralPath $obsoleteAccountResource -Force
    }
} finally {
    $resolvedTemp = [IO.Path]::GetFullPath($tempKeystore)
    if (!$resolvedTemp.StartsWith($localDirectory + [IO.Path]::DirectorySeparatorChar)) { throw 'Ruta temporal fuera del directorio previsto.' }
    if (Test-Path -LiteralPath $resolvedTemp) { Remove-Item -LiteralPath $resolvedTemp -Force }
}
Write-Host 'Certificado de laboratorio copiado a Auth, Web, Mobile y ATM; Account Service usa .local/tls/account-service-keystore.p12.'
Write-Host 'Certificado público: .local/tls/localhost.crt. No se modificó la confianza del sistema.'
Write-Host 'Truststore para llamadas HTTPS internas: .local/tls/week6-truststore.p12.'
Write-Host 'Antes de start-week6.ps1, ejecute mvn -B clean verify para actualizar los certificados empaquetados de Auth y Mobile.'
