#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$required=@('JWT_PRIVATE_KEY','JWT_PUBLIC_KEY','DEMO_WEB_PASSWORD','DEMO_MOBILE_PASSWORD','DEMO_ATM_PASSWORD','DB_URL','DB_USER','DB_PASSWORD')
$missing=@($required|Where-Object{![Environment]::GetEnvironmentVariable($_,'Process')})
if($missing.Count){throw "Faltan variables requeridas: $($missing -join ', '). No se mostraron valores."}
$truststore=Join-Path $repo '.local/tls/week6-truststore.p12'
$accountKeystore=Join-Path $repo '.local/tls/account-service-keystore.p12'
foreach($artifact in @($truststore,$accountKeystore)){
    if(!(Test-Path -LiteralPath $artifact -PathType Leaf)){
        throw "Falta $([IO.Path]::GetFileName($artifact)). Ejecute scripts/generar-certificados.ps1 -Force."
    }
}
$accountKeystorePassword=$env:ACCOUNT_TLS_KEYSTORE_PASSWORD
if(!$accountKeystorePassword){$accountKeystorePassword=$env:TLS_KEYSTORE_PASSWORD}
if(!$accountKeystorePassword){$accountKeystorePassword='changeit'} # Contraseña oficial del laboratorio.
$apps=@(
    @{Name='config';Jar='banco-legacy-config-server/target/banco-legacy-config-server-0.0.1-SNAPSHOT.jar';Trust=$false},
    @{Name='discovery';Jar='banco-legacy-discovery-server/target/banco-legacy-discovery-server-0.0.1-SNAPSHOT.jar';Trust=$false},
    @{Name='auth';Jar='banco-legacy-auth/target/banco-legacy-auth-0.0.1-SNAPSHOT.jar';Trust=$false},
    @{Name='account';Jar='banco-legacy-account-service/target/banco-legacy-account-service-0.0.1-SNAPSHOT.jar';Trust=$false},
    @{Name='web';Jar='banco-legacy-web-bff/target/banco-legacy-web-bff-0.0.1-SNAPSHOT.jar';Trust=$true},
    @{Name='mobile';Jar='banco-legacy-mobile-bff/target/banco-legacy-mobile-bff-0.0.1-SNAPSHOT.jar';Trust=$true},
    @{Name='atm';Jar='banco-legacy-atm-bff/target/banco-legacy-atm-bff-0.0.1-SNAPSHOT.jar';Trust=$true}
)
$started=[Collections.Generic.List[object]]::new()
foreach($app in $apps){
    $jar=Join-Path $repo $app.Jar
    if(!(Test-Path -LiteralPath $jar)){throw "Falta $($app.Jar). Ejecute mvn -B package antes de iniciar."}
    $info=[Diagnostics.ProcessStartInfo]::new('java')
    $info.WorkingDirectory=$repo;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    if($app.Name -eq 'account'){
        $info.Environment['ACCOUNT_TLS_KEYSTORE']=([Uri]$accountKeystore).AbsoluteUri
        $info.Environment['ACCOUNT_TLS_KEYSTORE_PASSWORD']=$accountKeystorePassword
    }
    if($app.Trust){
        $info.ArgumentList.Add("-Djavax.net.ssl.trustStore=$truststore")
        $info.ArgumentList.Add('-Djavax.net.ssl.trustStorePassword=changeit')
    }
    $info.ArgumentList.Add('-jar');$info.ArgumentList.Add($jar)
    $process=[Diagnostics.Process]::Start($info)
    $started.Add([pscustomobject]@{Name=$app.Name;ProcessId=$process.Id})
    Start-Sleep -Seconds $(if($app.Name -in @('config','discovery')){4}else{2})
}
$started
Write-Host 'Servicios Semana 6 iniciados. Batch no fue iniciado.'
