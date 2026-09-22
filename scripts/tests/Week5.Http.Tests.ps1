#requires -Version 7.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../Week5.Http.ps1')
$directory = Join-Path ([IO.Path]::GetTempPath()) ('week5-http-test-' + [guid]::NewGuid().ToString('N'))
[IO.Path]::GetFullPath($directory) | Out-Null
if (!([IO.Path]::GetFullPath($directory)).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Directorio temporal fuera de la ruta prevista.' }
[IO.Directory]::CreateDirectory($directory) | Out-Null
$rsa = [Security.Cryptography.RSA]::Create(2048)
$certificate = $null
try {
    $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
        'CN=localhost', $rsa, [Security.Cryptography.HashAlgorithmName]::SHA256,
        [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-1),
        [DateTimeOffset]::UtcNow.AddMinutes(5))
    [IO.File]::WriteAllText((Join-Path $directory 'localhost.crt'), $certificate.ExportCertificatePem())
    Push-Location $directory
    try {
        $client = New-Week5HttpClient -TrustedCertificate './localhost.crt'
        if (!$client) { throw 'No se creó HttpClient con una ruta relativa válida.' }
        $client.Dispose()
    } finally { Pop-Location }
    'PASS: New-Week5HttpClient acepta una ruta de certificado relativa.'
} finally {
    if ($certificate) { $certificate.Dispose() }
    $rsa.Dispose()
    Remove-Item -LiteralPath $directory -Recurse -Force
}
