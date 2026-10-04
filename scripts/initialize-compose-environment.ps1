#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$OutputDirectory = '.local/compose',
    [string]$EnvironmentFile = '.env',
    [string]$KeystorePassword,
    [switch]$RotateSecrets
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repo $OutputDirectory
$envFile = Join-Path $repo $EnvironmentFile
New-Item -ItemType Directory -Force -Path $output | Out-Null

function New-Secret([int]$Bytes = 32) {
    $buffer = [byte[]]::new($Bytes)
    [Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Read-EnvironmentFile([string]$Path) {
    $values = [ordered]@{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { continue }
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1)
    }
    return $values
}

function Test-RsaKeyPair([string]$PrivateKey, [string]$PublicKey) {
    if ([string]::IsNullOrWhiteSpace($PrivateKey) -or [string]::IsNullOrWhiteSpace($PublicKey)) {
        return $false
    }
    $privateRsa = [Security.Cryptography.RSA]::Create()
    $publicRsa = [Security.Cryptography.RSA]::Create()
    try {
        $privateBytes = [Convert]::FromBase64String($PrivateKey)
        $publicBytes = [Convert]::FromBase64String($PublicKey)
        $privateBytesRead = 0
        $publicBytesRead = 0
        $privateRsa.ImportPkcs8PrivateKey($privateBytes, [ref]$privateBytesRead)
        $publicRsa.ImportSubjectPublicKeyInfo($publicBytes, [ref]$publicBytesRead)
        $privateParameters = $privateRsa.ExportParameters($false)
        $publicParameters = $publicRsa.ExportParameters($false)
        return [Linq.Enumerable]::SequenceEqual($privateParameters.Modulus, $publicParameters.Modulus) -and
            [Linq.Enumerable]::SequenceEqual($privateParameters.Exponent, $publicParameters.Exponent)
    }
    catch { return $false }
    finally {
        $privateRsa.Dispose()
        $publicRsa.Dispose()
    }
}

function Test-KeytoolAlias([string]$Store, [string]$Password, [string]$Alias) {
    if (-not (Test-Path -LiteralPath $Store -PathType Leaf) -or [string]::IsNullOrWhiteSpace($Password)) {
        return $false
    }
    & keytool -list -keystore $Store -storepass $Password -alias $Alias *> $null
    return $LASTEXITCODE -eq 0
}

function Test-CertificateValidity([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $certificateObject = $null
    try {
        $certificateObject = [Security.Cryptography.X509Certificates.X509Certificate2]::new($Path)
        $now = Get-Date
        $sanExtension = $certificateObject.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.17' } | Select-Object -First 1
        $dnsNames = if ($null -ne $sanExtension) { $sanExtension.Format($false) } else { '' }
        return $certificateObject.NotBefore -le $now -and $certificateObject.NotAfter -gt $now.AddDays(1) -and
            $dnsNames -match '(?i)\bcustomer-service\b' -and $dnsNames -match '(?i)\bpayment-service\b'
    }
    catch { return $false }
    finally {
        if ($null -ne $certificateObject) { $certificateObject.Dispose() }
    }
}

$values = Read-EnvironmentFile $envFile
$generated = [Collections.Generic.List[string]]::new()
$reused = [Collections.Generic.List[string]]::new()

foreach ($name in @('POSTGRES_PASSWORD', 'OAUTH_WEB_CLIENT_SECRET',
        'OAUTH_MOBILE_CLIENT_SECRET', 'OAUTH_ATM_CLIENT_SECRET', 'OAUTH_DOMAIN_CLIENT_SECRET')) {
    if (-not $RotateSecrets -and -not [string]::IsNullOrWhiteSpace($values[$name])) {
        $reused.Add($name)
    }
    else {
        $values[$name] = New-Secret
        $generated.Add($name)
    }
}

$rsaIsValid = -not $RotateSecrets -and
    (Test-RsaKeyPair $values['JWT_PRIVATE_KEY'] $values['JWT_PUBLIC_KEY'])
if ($rsaIsValid) {
    $reused.Add('JWT_PRIVATE_KEY/JWT_PUBLIC_KEY')
}
else {
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $values['JWT_PRIVATE_KEY'] = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
        $values['JWT_PUBLIC_KEY'] = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
    }
    finally { $rsa.Dispose() }
    $generated.Add('JWT_PRIVATE_KEY/JWT_PUBLIC_KEY')
}

$keystore = Join-Path $output 'banco-legacy-docker.p12'
$certificate = Join-Path $output 'banco-legacy-docker.crt'
$truststore = Join-Path $output 'banco-legacy-truststore.p12'
$existingTlsPassword = $values['TLS_KEYSTORE_PASSWORD']
$tlsIsValid = -not $RotateSecrets -and
    (Test-KeytoolAlias $keystore $existingTlsPassword 'bff-local') -and
    (Test-KeytoolAlias $truststore $existingTlsPassword 'banco-legacy-local') -and
    (Test-CertificateValidity $certificate)

if ($tlsIsValid) {
    $reused.Add('TLS_KEYSTORE_PASSWORD/certificados')
}
else {
    if ($RotateSecrets -or [string]::IsNullOrWhiteSpace($existingTlsPassword)) {
        $values['TLS_KEYSTORE_PASSWORD'] = if ([string]::IsNullOrWhiteSpace($KeystorePassword)) {
            New-Secret 24
        } else { $KeystorePassword }
    }
    $tlsPassword = $values['TLS_KEYSTORE_PASSWORD']
    Remove-Item -LiteralPath @($keystore, $certificate, $truststore) -Force -ErrorAction SilentlyContinue

    $san = 'dns:localhost,dns:auth-server,dns:account-service,dns:web-bff,dns:mobile-bff,dns:atm-bff,dns:customer-service,dns:payment-service,ip:127.0.0.1'
    & keytool -genkeypair -alias bff-local -keyalg RSA -keysize 2048 -validity 365 `
        -dname 'CN=Banco Legacy Local, OU=Semana 8, O=DUOC, L=Santiago, C=CL' `
        -ext "SAN=$san" -storetype PKCS12 -keystore $keystore `
        -storepass $tlsPassword -keypass $tlsPassword -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible generar el keystore TLS.' }

    & keytool -exportcert -rfc -alias bff-local -keystore $keystore `
        -storepass $tlsPassword -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible exportar el certificado TLS.' }

    & keytool -importcert -alias banco-legacy-local -file $certificate -keystore $truststore `
        -storetype PKCS12 -storepass $tlsPassword -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible generar el truststore TLS.' }
    $generated.Add('TLS_KEYSTORE_PASSWORD/certificados')
}

if ([string]::IsNullOrWhiteSpace($values['BATCH_DATA_DIR'])) {
    $values['BATCH_DATA_DIR'] = '../bank_legacy_data/data/semana_3'
}

$orderedNames = @('POSTGRES_PASSWORD', 'OAUTH_WEB_CLIENT_SECRET', 'OAUTH_MOBILE_CLIENT_SECRET',
    'OAUTH_ATM_CLIENT_SECRET', 'JWT_PRIVATE_KEY', 'JWT_PUBLIC_KEY', 'TLS_KEYSTORE_PASSWORD', 'BATCH_DATA_DIR')
$additionalNames = @($values.Keys | Where-Object { $_ -notin $orderedNames } | Sort-Object)
$lines = foreach ($name in @($orderedNames + $additionalNames)) {
    if ($values.Contains($name)) { $name + '=' + $values[$name] }
}
[IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))

Write-Host 'Entorno Docker preparado sin mostrar secretos.'
Write-Host ('  Reutilizados: ' + $(if ($reused.Count -eq 0) { 'ninguno' } else { $reused -join ', ' }))
Write-Host ('  Generados: ' + $(if ($generated.Count -eq 0) { 'ninguno' } else { $generated -join ', ' }))
Write-Host "  Archivo de entorno: $envFile"
Write-Host "  Keystore: $keystore"
Write-Host "  Truststore: $truststore"
