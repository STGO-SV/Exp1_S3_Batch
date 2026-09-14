#requires -Version 7.0
# Smoke test HTTPS reproducible. Usa únicamente H2 sintético, nunca PostgreSQL ni Batch.
[CmdletBinding()]
param([string]$H2Jar=(Join-Path $env:USERPROFILE '.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar'))
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Week5.Http.ps1"
$repo=Split-Path $PSScriptRoot
if(!(Test-Path $H2Jar)) { throw 'Falta H2 2.2.224 del classpath de tests; ejecute mvn clean verify.' }
$run=Join-Path $repo ('.local/smoke-'+[guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($run) | Out-Null
$rsa=[Security.Cryptography.RSA]::Create(2048)
$public=[Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
$private=[Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
$rsa.Dispose()
$trustedCertificate=Join-Path $repo '.local/tls/localhost.crt'
if(!(Test-Path $trustedCertificate)) { throw 'Falta .local/tls/localhost.crt; ejecute generar-certificados.ps1.' }
$processes=[Collections.Generic.List[Diagnostics.Process]]::new()
$processByName=@{}
$outputTasks=@{}
$errorTasks=@{}
$ports=@{auth=8084;web=8081;mobile=8082;atm=8083}
foreach($name in $ports.Keys) {
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$ports[$name])
    try { $listener.Start() } catch { throw "El puerto $($ports[$name]) de $name ya está ocupado." }
    finally { $listener.Stop() }
}
$passwords=@{}
$previous=@{}
foreach($channel in @('WEB','MOBILE','ATM')) {
    $key="DEMO_${channel}_PASSWORD"
    $previous[$key]=[Environment]::GetEnvironmentVariable($key)
    $passwords[$channel]=[guid]::NewGuid().ToString()
    [Environment]::SetEnvironmentVariable($key,$passwords[$channel],'Process')
}
$fixture=Get-Content -Raw (Join-Path $repo 'banco-legacy-batch/src/main/resources/schema.sql')
$fixture+="`nINSERT INTO interes_procesado (id,cuenta_id,nombre,saldo_original,tasa,saldo_procesado,tipo) SELECT 1,101,'Synthetic',1000,0.01,1010,'ahorro' WHERE NOT EXISTS (SELECT 1 FROM interes_procesado WHERE id=1);"
for($i=1;$i -le 25;$i++) {
    $fixture+="`nINSERT INTO movimiento_anual_procesado (id,cuenta_id,fecha,tipo_transaccion,monto,descripcion) SELECT $i,101,DATE '2026-01-01','deposito',$i,'Synthetic movement' WHERE NOT EXISTS (SELECT 1 FROM movimiento_anual_procesado WHERE id=$i);"
}
for($i=1;$i -le 12;$i++) {
    $fixture+="`nINSERT INTO transaccion_procesada (id,transaccion_id,fecha,monto,tipo,anomalia) SELECT $i,$i,DATE '2026-01-01',2500,'debito',TRUE WHERE NOT EXISTS (SELECT 1 FROM transaccion_procesada WHERE id=$i);"
}
$fixturePath=(Join-Path $run 'fixture.sql').Replace('\','/')
[IO.File]::WriteAllText($fixturePath,$fixture)
try {
    foreach($name in @('auth','web','mobile','atm')) {
        $module=if($name -eq 'auth'){'banco-legacy-auth'}else{"banco-legacy-$name-bff"}
        $jar=Join-Path $repo "$module/target/$module-0.0.1-SNAPSHOT.jar"
        if(!(Test-Path $jar)) { throw "Falta JAR $module." }
        $expanded=Join-Path $run $name
        [IO.Compression.ZipFile]::ExtractToDirectory($jar,$expanded)
        $info=[Diagnostics.ProcessStartInfo]::new('java')
        $info.UseShellExecute=$false; $info.CreateNoWindow=$true
        $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
        $info.ArgumentList.Add('-cp')
        $info.ArgumentList.Add((Join-Path $expanded 'BOOT-INF/classes')+';'+(Join-Path $expanded 'BOOT-INF/lib/*')+';'+$H2Jar)
        $class=if($name -eq 'auth'){'com.duoc.banco_legacy.auth.AuthApplication'}else{
            'com.duoc.banco_legacy.'+$name+'.'+(Get-Culture).TextInfo.ToTitleCase($name)+'BffApplication'
        }
        $info.ArgumentList.Add($class)
        $info.ArgumentList.Add("--server.port=$($ports[$name])")
        $info.ArgumentList.Add('--server.address=127.0.0.1')
        $info.ArgumentList.Add('--server.ssl.enabled=true')
        $info.Environment['TLS_KEYSTORE_PASSWORD']='changeit'
        $info.Environment['JWT_PUBLIC_KEY']=$public
        $info.Environment['JWT_ISSUER']='banco-legacy-auth'
        $info.Environment['JWT_AUDIENCE']='banco-bff'
        # Sobrescribe explícitamente cualquier configuración Spring/DB heredada; nunca usa una base de datos real.
        $info.Environment.Remove('SPRING_APPLICATION_JSON') | Out-Null
        if($name -eq 'auth') {
            $info.Environment['JWT_PRIVATE_KEY']=$private
        } else {
            $info.Environment.Remove('JWT_PRIVATE_KEY') | Out-Null
            foreach($channel in @('WEB','MOBILE','ATM')) { $info.Environment.Remove("DEMO_${channel}_PASSWORD") | Out-Null }
            $info.ArgumentList.Add("--spring.datasource.url=jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '$fixturePath'")
            $info.ArgumentList.Add('--spring.datasource.username=sa')
            $info.ArgumentList.Add('--spring.datasource.password=')
            $info.ArgumentList.Add('--spring.datasource.driver-class-name=org.h2.Driver')
            $info.ArgumentList.Add('--spring.datasource.hikari.read-only=false')
            $info.ArgumentList.Add('--spring.datasource.hikari.maximum-pool-size=1')
            $info.ArgumentList.Add('--spring.sql.init.mode=never')
        }
        $p=[Diagnostics.Process]::Start($info)
        # Consume los streams de forma asíncrona sin guardar posibles salidas sensibles de la aplicación.
        $outputTasks[$name]=$p.StandardOutput.ReadToEndAsync()
        $errorTasks[$name]=$p.StandardError.ReadToEndAsync()
        $processByName[$name]=$p
        $processes.Add($p)
    }
        $client=New-Week5HttpClient -TrustedCertificate $trustedCertificate
    try {
        foreach($name in @('auth','web','mobile','atm')) {
            $ready=$false
            for($attempt=0;$attempt -lt 60;$attempt++) {
                if($processByName[$name].HasExited) {
                    $details=$errorTasks[$name].GetAwaiter().GetResult()
                    throw "$name terminó al iniciar: $($details.Substring([Math]::Max(0,$details.Length-1200)))"
                }
                $reply=Invoke-Week5Http -Client $client -Url "https://localhost:$($ports[$name])/"
                if($reply.Status -gt 0) { $ready=$true; break }
                Start-Sleep -Milliseconds 500
            }
            if(!$ready) { throw "No respondió $name por HTTPS: $($reply.ErrorType): $($reply.ErrorMessage)" }
        }
    } finally { $client.Dispose() }
    $arguments=@{WebUrl="https://localhost:$($ports.web)";MobileUrl="https://localhost:$($ports.mobile)"
        AtmUrl="https://localhost:$($ports.atm)";AuthUrl="https://localhost:$($ports.auth)";TrustedCertificate=$trustedCertificate}
    & "$PSScriptRoot/verify-week5.ps1" @arguments -Output (Join-Path $repo 'docs/evidence/semana-5/verification-https-h2.json')
    & "$PSScriptRoot/benchmark-bff.ps1" @arguments -Samples 20 -Warmup 3 -DatasetLabel 'Synthetic H2: 1 account, 25 movements, 12 anomalies; pool=1; HTTPS with explicit certificate trust; NOT PostgreSQL' -Output (Join-Path $repo 'docs/evidence/semana-5/bff-benchmark-https-h2.csv')
} finally {
    foreach($p in $processes) {
        if(!$p.HasExited) { $p.Kill(); $p.WaitForExit(10000) | Out-Null }
        $p.Dispose()
    }
    foreach($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    $private=$null; $passwords=$null
}
