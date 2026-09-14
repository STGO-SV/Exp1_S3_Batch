#requires -Version 7.0
Set-StrictMode -Version Latest

function New-Week5HttpClient {
    param([string]$TrustedCertificate, [switch]$LabSkipCertificateValidation)
    if ($TrustedCertificate -and $LabSkipCertificateValidation) {
        throw 'Elija confianza explícita o modo laboratorio sin validación, no ambos.'
    }
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    if ($LabSkipCertificateValidation) {
        $handler.ServerCertificateCustomValidationCallback = [Net.Http.HttpClientHandler]::DangerousAcceptAnyServerCertificateValidator
    } elseif ($TrustedCertificate) {
        if (!(Test-Path -LiteralPath $TrustedCertificate -PathType Leaf)) { throw 'No existe el certificado público de confianza.' }
        # Los callbacks TLS se ejecutan fuera del runspace de PowerShell. Se usa un delegado CLR, no un scriptblock.
        if (!('Week5.LocalCertificateTrust' -as [type])) {
            Add-Type -TypeDefinition @'
using System;
using System.Net.Http;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;
using System.IO;
namespace Week5 {
    public static class LocalCertificateTrust {
        public static void Configure(HttpClientHandler handler, string path) {
            var trusted = X509Certificate2.CreateFromPem(File.ReadAllText(path));
            handler.ServerCertificateCustomValidationCallback = (request, certificate, chain, errors) => {
                if (certificate == null || (errors & (SslPolicyErrors.RemoteCertificateNameMismatch
                        | SslPolicyErrors.RemoteCertificateNotAvailable)) != 0) return false;
                // Fija el certificado público proporcionado explícitamente y conserva la validación del hostname.
                // Este mecanismo es adecuado para el certificado único y autofirmado del laboratorio.
                return certificate.RawData.AsSpan().SequenceEqual(trusted.RawData);
            };
        }
    }
}
'@
        }
        [Week5.LocalCertificateTrust]::Configure($handler, [IO.Path]::GetFullPath($TrustedCertificate))
    }
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    return $client
}

function Assert-Week5Url([string]$Url, [switch]$InternalHttp) {
    $uri = [Uri]$Url
    if (!$uri.IsAbsoluteUri -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
        throw 'Use una URL absoluta sin credenciales, query ni fragmento.'
    }
    if ($uri.Scheme -eq 'https') { return }
    if ($InternalHttp -and $uri.Scheme -eq 'http' -and $uri.IsLoopback) { return }
    throw 'Se requiere HTTPS; HTTP sólo se permite explícitamente para loopback.'
}

function Invoke-Week5Http {
    param([Net.Http.HttpClient]$Client, [string]$Url, [string]$Token,
          [string]$Method = 'GET', [string]$Body)
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), $Url)
    if ($Token) { $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    if ($Body) { $request.Content = [Net.Http.StringContent]::new($Body, [Text.Encoding]::UTF8, 'application/json') }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $watch.Stop()
            return [pscustomobject]@{ Status = [int]$response.StatusCode; Bytes = $bytes.Length
                Milliseconds = $watch.Elapsed.TotalMilliseconds; Body = [Text.Encoding]::UTF8.GetString($bytes)
                ContentType = if ($response.Content.Headers.ContentType) { $response.Content.Headers.ContentType.ToString() } else { '' } }
        } finally { $response.Dispose() }
    } catch {
        # Nunca devuelve headers de la solicitud ni volcados de excepciones que puedan contener información sensible.
        $watch.Stop()
        return [pscustomobject]@{ Status = 0; Bytes = 0; Milliseconds = $watch.Elapsed.TotalMilliseconds; Body = ''; ContentType = ''
            ErrorType = $_.Exception.GetType().FullName; ErrorMessage = $_.Exception.Message }
    } finally { $request.Dispose() }
}

function Get-Week5Tokens {
    param([Net.Http.HttpClient]$Client, [string]$AuthUrl = 'https://localhost:8084', [switch]$InternalHttp)
    Assert-Week5Url $AuthUrl -InternalHttp:$InternalHttp
    $result = @{}
    foreach ($channel in @('WEB','MOBILE','ATM')) {
        $password = [Environment]::GetEnvironmentVariable("DEMO_${channel}_PASSWORD")
        if (!$password) { throw "Falta DEMO_${channel}_PASSWORD." }
        $body = @{username=$channel.ToLower()+'-user'; password=$password} | ConvertTo-Json -Compress
        $reply = Invoke-Week5Http -Client $Client -Url ($AuthUrl.TrimEnd('/')+'/auth/token') -Method POST -Body $body
        $body = $null; $password = $null
        if ($reply.Status -ne 200) { throw "Emisor: autenticación $channel falló (HTTP $($reply.Status))." }
        $result[$channel] = ($reply.Body | ConvertFrom-Json).access_token
    }
    return $result
}
