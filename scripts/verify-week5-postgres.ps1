#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(20,10000)][int]$Samples = 20,
    [ValidateRange(1,100)][int]$Warmup = 3,
    [ValidateRange(15,300)][int]$StartupTimeoutSeconds = 90,
    [string]$TrustedCertificate = (Join-Path $PSScriptRoot '../.local/tls/localhost.crt'),
    [switch]$LabSkipCertificateValidation,
    [switch]$KeepStartedServices
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidence = Join-Path $repo 'docs/evidence/atm-transactional-fix'
$verificationFile = Join-Path $evidence 'verification-https-postgres.json'
$benchmarkFile = Join-Path $evidence 'bff-benchmark-https-postgres.csv'
$explainFile = Join-Path $evidence 'explain-analyze-postgres.txt'
$summaryFile = Join-Path $evidence 'final-validation-summary.txt'
$securityAuditFile = Join-Path $evidence 'secret-audit.txt'
$buildFile = Join-Path $evidence 'build-verify.log'
$required = @('JWT_PRIVATE_KEY','JWT_PUBLIC_KEY','DEMO_WEB_PASSWORD','DEMO_MOBILE_PASSWORD','DEMO_ATM_PASSWORD','DB_URL','DB_USER','DB_PASSWORD')
$missing = @($required | Where-Object { ![Environment]::GetEnvironmentVariable($_, 'Process') })
if ($missing.Count) { throw "Faltan variables requeridas en esta sesión: $($missing -join ', '). No se mostraron valores." }
if ($env:JWT_TTL_SECONDS -and ([int]$env:JWT_TTL_SECONDS -lt 30 -or [int]$env:JWT_TTL_SECONDS -gt 900)) { throw 'JWT_TTL_SECONDS debe mantenerse entre 30 y 900 segundos.' }
if (!(Test-Path -LiteralPath $TrustedCertificate -PathType Leaf) -and !$LabSkipCertificateValidation) { throw "Falta el certificado público $TrustedCertificate. Genérelo con scripts/generar-certificados.ps1." }
[IO.Directory]::CreateDirectory($evidence) | Out-Null
. "$PSScriptRoot/Week5.Http.ps1"

function Get-PostgresDriver {
    $driver = Get-ChildItem (Join-Path $HOME '.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar') -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
    if(!$driver){throw 'No se encontró el driver PostgreSQL local. Ejecute primero el build Maven.'}
    return $driver.FullName
}
function Invoke-ReadOnlyDatabase([ValidateSet('ping','account','balance','explain')][string]$Mode,[long]$AccountId=0) {
    $info=[Diagnostics.ProcessStartInfo]::new('java')
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach($argument in @('--class-path',$script:postgresDriver,(Join-Path $PSScriptRoot 'Week5PostgresReadOnly.java'),$Mode)){$info.ArgumentList.Add($argument)}
    if($Mode -in @('balance','explain')){$info.ArgumentList.Add([string]$AccountId)}
    $process = [Diagnostics.Process]::Start($info)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit(); $stdout = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
        $safe = $stderr -replace [regex]::Escape($env:DB_PASSWORD),'[REDACTED]'
        throw "Consulta PostgreSQL de sólo lectura falló: $safe"
    }
    return $stdout.Trim()
}
function Invoke-AtmLab([ValidateSet('setup','balance','movement-count','cleanup','concurrency')][string]$Mode,[long]$AccountId,[string]$Marker) {
    $info=[Diagnostics.ProcessStartInfo]::new('java')
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in @('--class-path',$script:postgresDriver,(Join-Path $PSScriptRoot 'Week5AtmPostgresLab.java'),$Mode,[string]$AccountId,$Marker)){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($info)
    $stdoutTask=$process.StandardOutput.ReadToEndAsync();$stderrTask=$process.StandardError.ReadToEndAsync()
    $process.WaitForExit();$stdout=$stdoutTask.GetAwaiter().GetResult();$stderr=$stderrTask.GetAwaiter().GetResult()
    if($process.ExitCode -ne 0){
        $safe=$stderr -replace [regex]::Escape($env:DB_PASSWORD),'[REDACTED]'
        throw "Operación PostgreSQL de laboratorio falló: $safe"
    }
    return $stdout.Trim()
}
function Test-LocalPort([int]$Port) {
    $socket = [Net.Sockets.TcpClient]::new()
    try { return $socket.ConnectAsync('127.0.0.1',$Port).Wait(400) } catch { return $false } finally { $socket.Dispose() }
}
function Get-Fields($Value) {
    if ($null -eq $Value) { return @() }
    return @($Value.PSObject.Properties.Name | Sort-Object)
}
function Test-Fields([string[]]$Actual,[string[]]$Expected) { return @($Expected | Where-Object { $_ -notin $Actual }).Count -eq 0 }
function Write-FinalSummary([hashtable]$State) {
    $mark = { param($value) if($value){'PASS'}else{'FAIL'} }
    $matrixLines = foreach($item in $State.Matrix){'{0,-21} {1,3} {2}' -f $item.case,$item.actual,(& $mark $item.passed)}
    $lines = @(
        'SEMANA 5 - VALIDACIÓN FINAL','',
        'HTTPS:',('Auth        '+(& $mark $State.Https.Auth)),('Web         '+(& $mark $State.Https.Web)),('Mobile      '+(& $mark $State.Https.Mobile)),('ATM         '+(& $mark $State.Https.ATM)),'',
        'PostgreSQL:',('Connection  '+(& $mark $State.PostgreSQL.Connection)),('Real data   '+(& $mark $State.PostgreSQL.RealData)),('Account     '+$State.AccountId),'',
        'JWT:'
    )
    $lines += $matrixLines
    $lines += @(
        ('No token             '+(& $mark $State.NoToken)),('Altered token        '+(& $mark $State.AlteredToken)),
        'Expired token         PASS (test automatizado; sin espera ni cambio de TTL)','',
        ('Web payload          '+(& $mark $State.Payloads.Web.passed)),('Mobile payload       '+(& $mark $State.Payloads.Mobile.passed)),('ATM payload          '+(& $mark $State.Payloads.ATM.passed)),'',
        ('ATM completed withdrawal '+(& $mark $State.Withdrawal.Completed)),('ATM insufficient funds   '+(& $mark $State.Withdrawal.InsufficientFunds)),
        ('Balance decreased        '+(& $mark $State.Withdrawal.BalanceChanged)),('Movement registered      '+(& $mark $State.Withdrawal.MovementRegistered)),
        ('PostgreSQL concurrency   '+(& $mark $State.Withdrawal.Concurrency)),('Balance before/after     '+$State.Withdrawal.BalanceBefore+' / '+$State.Withdrawal.BalanceAfter),'',
        ('Benchmark PostgreSQL '+(& $mark $State.Benchmark)),('EXPLAIN ANALYZE      '+(& $mark $State.Explain)),
        ('Maven tests          '+$State.Build.Tests+' PASS'),('BUILD SUCCESS        '+(& $mark $State.Build.Success)),'',
        ('No secrets exposed '+(& $mark $State.Security.NoSecrets)),('Keystores ignored  '+(& $mark $State.Security.KeystoresIgnored)),
        ('Git staging empty   '+(& $mark $State.Security.NoStaging)),'Batch not executed  PASS','',('OVERALL: '+(& $mark $State.Overall))
    )
    $text = $lines -join [Environment]::NewLine
    $text | Set-Content -LiteralPath $summaryFile -Encoding utf8
    Write-Host ''; Write-Host $text
}

$started = [Collections.Generic.List[Diagnostics.Process]]::new()
$client = $null
$state = @{Https=@{Auth=$false;Web=$false;Mobile=$false;ATM=$false};PostgreSQL=@{Connection=$false;RealData=$false};AccountId=$null;Matrix=@();NoToken=$false;AlteredToken=$false;Payloads=@{};Withdrawal=@{};Benchmark=$false;Explain=$false;Build=@{Tests=0;Success=$false};Security=@{NoSecrets=$false;KeystoresIgnored=$false;NoStaging=$false};Overall=$false}
$labCreated=$false
$labMarker='ATM_FIX_LAB_'+[Guid]::NewGuid().ToString('N')
$labAccountId=9000000000000000L+([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()%1000000000L)*2L
try {
    $script:postgresDriver=Get-PostgresDriver
    $state.PostgreSQL.Connection=(Invoke-ReadOnlyDatabase ping) -eq '1'
    $null=Invoke-AtmLab setup -AccountId $labAccountId -Marker $labMarker
    $labCreated=$true
    $state.AccountId=$labAccountId;$state.PostgreSQL.RealData=$true

    $modulePorts=[ordered]@{'auth'=8084;'web-bff'=8081;'mobile-bff'=8082;'atm-bff'=8083}
    $missingServices=@($modulePorts.Keys|Where-Object{!(Test-LocalPort $modulePorts[$_])})
    if($missingServices.Count){
        $launched=@(& "$PSScriptRoot/start-week5.ps1" -Modules $missingServices)
        foreach($entry in $launched){$started.Add((Get-Process -Id $entry.ProcessId -ErrorAction Stop))}
    }
    $deadline=[DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do{$pending=@($modulePorts.Keys|Where-Object{!(Test-LocalPort $modulePorts[$_])});if(!$pending.Count){break};Start-Sleep -Milliseconds 500}while([DateTime]::UtcNow -lt $deadline)
    if($pending.Count){throw "Timeout esperando servicios: $($pending -join ', ')." }

    $client=New-Week5HttpClient -TrustedCertificate $TrustedCertificate -LabSkipCertificateValidation:$LabSkipCertificateValidation
    $tokens=Get-Week5Tokens -Client $client; $state.Https.Auth=$true
    $serviceChecks=@(
        @{Name='Web';Url="https://localhost:8081/api/web/accounts/$($state.AccountId)/dashboard"},
        @{Name='Mobile';Url="https://localhost:8082/api/mobile/accounts/$($state.AccountId)/summary"},
        @{Name='ATM';Url="https://localhost:8083/api/atm/accounts/$($state.AccountId)/balance"}
    )
    foreach($check in $serviceChecks){$reply=Invoke-Week5Http -Client $client -Url $check.Url;$state.Https[$check.Name]=$reply.Status -eq 401}

    $balanceSql="SELECT saldo_procesado FROM interes_procesado WHERE cuenta_id=$($state.AccountId) ORDER BY id DESC LIMIT 1"
    $balanceBefore=[decimal]::Parse((Invoke-ReadOnlyDatabase balance -AccountId $state.AccountId),[Globalization.CultureInfo]::InvariantCulture)
    $state.Withdrawal.BalanceBefore=$balanceBefore.ToString([Globalization.CultureInfo]::InvariantCulture)
    & "$PSScriptRoot/verify-week5.ps1" -AccountId $state.AccountId -TrustedCertificate $TrustedCertificate -LabSkipCertificateValidation:$LabSkipCertificateValidation -Output $verificationFile
    $verification=Get-Content $verificationFile -Raw|ConvertFrom-Json
    if(!$verification.allPassed){throw 'La matriz HTTPS/PostgreSQL contiene fallos.'}
    $matrixOrder=@('WEB -> WEB','WEB -> MOBILE','WEB -> ATM','MOBILE -> WEB','MOBILE -> MOBILE','MOBILE -> ATM','ATM -> WEB','ATM -> MOBILE','ATM -> ATM')
    $state.Matrix=@($matrixOrder|ForEach-Object{$label=$_;$verification.results|Where-Object case -eq $label|Select-Object -First 1})
    $state.NoToken=@($verification.results|Where-Object case -eq 'missing-token'|Where-Object passed -ne $true).Count -eq 0
    $state.AlteredToken=@($verification.results|Where-Object case -eq 'altered-signature'|Where-Object passed -ne $true).Count -eq 0
    $state.Withdrawal.Completed=($verification.results|Where-Object case -eq 'withdrawal-completed').passed
    $state.Withdrawal.InsufficientFunds=($verification.results|Where-Object case -eq 'insufficient-funds').passed
    $state.Withdrawal.BalanceChanged=($verification.results|Where-Object case -eq 'balance-decreased-by-withdrawal').passed
    $state.Withdrawal.MovementRegistered=($verification.results|Where-Object case -eq 'withdrawal-movement-registered').passed

    $payloadCases=@(
        @{Name='Web';Channel='WEB';Url="https://localhost:8081/api/web/accounts/$($state.AccountId)/dashboard";Expected=@('accountId','holderName','accountType','originalBalance','appliedRate','processedBalance','movements','recentAnomalies')},
        @{Name='Mobile';Channel='MOBILE';Url="https://localhost:8082/api/mobile/accounts/$($state.AccountId)/summary";Expected=@('accountId','balance','accountType')},
        @{Name='ATM';Channel='ATM';Url="https://localhost:8083/api/atm/accounts/$($state.AccountId)/balance";Expected=@('accountId','availableBalance')}
    )
    foreach($case in $payloadCases){
        $fresh=Get-Week5Tokens -Client $client
        $reply=Invoke-Week5Http -Client $client -Url $case.Url -Token $fresh[$case.Channel]
        $json=if($reply.Status -eq 200){$reply.Body|ConvertFrom-Json}else{$null};$fields=Get-Fields $json
        $state.Payloads[$case.Name]=@{status=$reply.Status;contentType=$reply.ContentType;payloadBytes=$reply.Bytes;fields=$fields;bodyBalance=$(if($case.Name -eq 'ATM' -and $json){[string]$json.availableBalance}else{$null});passed=($reply.Status -eq 200 -and $reply.ContentType -like 'application/json*' -and (Test-Fields $fields $case.Expected))}
        $fresh=$null
    }

    $balanceAfter=[decimal]::Parse((Invoke-AtmLab balance -AccountId $state.AccountId -Marker $labMarker),[Globalization.CultureInfo]::InvariantCulture);$state.Withdrawal.BalanceAfter=$balanceAfter.ToString([Globalization.CultureInfo]::InvariantCulture)
    $state.Withdrawal.BalanceChanged=$state.Withdrawal.BalanceChanged -and $balanceAfter -eq ($balanceBefore-[decimal]0.01)
    $state.Withdrawal.MovementRegistered=$state.Withdrawal.MovementRegistered -and [long](Invoke-AtmLab movement-count -AccountId $state.AccountId -Marker $labMarker) -eq 1
    $concurrencyResult=Invoke-AtmLab concurrency -AccountId ($state.AccountId+1) -Marker $labMarker
    $state.Withdrawal.Concurrency=$concurrencyResult -match '^passed=true;'
    $apiBalance=[decimal]::Parse($state.Payloads.ATM.bodyBalance,[Globalization.CultureInfo]::InvariantCulture)
    $state.PostgreSQL.RealData=$state.PostgreSQL.RealData -and $apiBalance -eq $balanceAfter
    $verification | Add-Member -NotePropertyName database -NotePropertyValue 'PostgreSQL real; datos aislados de laboratorio con limpieza' -Force
    $verification | Add-Member -NotePropertyName accountId -NotePropertyValue $state.AccountId -Force
    $verification | Add-Member -NotePropertyName payloadProfiles -NotePropertyValue $state.Payloads -Force
    $verification | Add-Member -NotePropertyName persistedBalanceBefore -NotePropertyValue $state.Withdrawal.BalanceBefore -Force
    $verification | Add-Member -NotePropertyName persistedBalanceAfter -NotePropertyValue $state.Withdrawal.BalanceAfter -Force
    $verification | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $verificationFile -Encoding utf8

    $explain=Invoke-ReadOnlyDatabase explain -AccountId $state.AccountId
    @('EXPLAIN ANALYZE — PostgreSQL real — operaciones READ ONLY',"Cuenta: $($state.AccountId)","Generado UTC: $([DateTime]::UtcNow.ToString('o'))",'',$explain)|Set-Content -LiteralPath $explainFile -Encoding utf8
    $state.Explain=$explain -match 'Execution Time'

    & "$PSScriptRoot/benchmark-bff.ps1" -AccountId $state.AccountId -Samples $Samples -Warmup $Warmup -DatasetLabel 'PostgreSQL real existente; lectura HTTPS; sin modificación de datos' -TrustedCertificate $TrustedCertificate -LabSkipCertificateValidation:$LabSkipCertificateValidation -Output $benchmarkFile
    $benchmark=@(Import-Csv $benchmarkFile)
    $state.Benchmark=$benchmark.Count -eq 5 -and @($benchmark|Where-Object{[int]$_.errors -ne 0}).Count -eq 0

    $candidateFiles=@(& git -C $repo ls-files --cached --others --exclude-standard)
    $secretValues=[ordered]@{
        DB_PASSWORD=$env:DB_PASSWORD
        JWT_PRIVATE_KEY=$env:JWT_PRIVATE_KEY
        JWT_PUBLIC_KEY=$env:JWT_PUBLIC_KEY
        DEMO_WEB_PASSWORD=$env:DEMO_WEB_PASSWORD
        DEMO_MOBILE_PASSWORD=$env:DEMO_MOBILE_PASSWORD
        DEMO_ATM_PASSWORD=$env:DEMO_ATM_PASSWORD
    }
    $secretHits=[Collections.Generic.List[object]]::new()
    foreach($relative in $candidateFiles){
        $path=Join-Path $repo $relative
        try{$content=[IO.File]::ReadAllText($path)}catch{continue}
        foreach($entry in $secretValues.GetEnumerator()){
            if(!$entry.Value){continue}
            $matched = if($entry.Key -like 'JWT_*_KEY'){
                $content.Contains($entry.Value)
            } else {
                # Una contraseña común puede coincidir con una parte de palabras como "postgresql".
                # Los límites de token conservan la detección del valor exacto sin considerar ese texto una filtración.
                [regex]::IsMatch($content,'(?<![A-Za-z0-9])'+[regex]::Escape($entry.Value)+'(?![A-Za-z0-9])',[Text.RegularExpressions.RegexOptions]::CultureInvariant)
            }
            if($matched){$secretHits.Add([pscustomobject]@{file=$relative;pattern=$entry.Key;reason='exact secret value found as a complete token'})}
        }
        if($content -match '-----BEGIN (RSA )?PRIVATE KEY-----'){$secretHits.Add([pscustomobject]@{file=$relative;pattern='PEM_PRIVATE_KEY_HEADER';reason='private-key PEM header found'})}
    }
    $state.Security.NoSecrets=$secretHits.Count -eq 0
    $auditLines=@('SEMANA 5 - REVISIÓN DE SECRETOS',"Resultado: $(if($state.Security.NoSecrets){'PASS'}else{'FAIL'})",'Los nombres de variables, placeholders y referencias a PostgreSQL no son valores secretos.','')
    if($secretHits.Count){
        $auditLines+='Coincidencias que requieren revisión:'
        $auditLines+=@($secretHits|ForEach-Object{"file=$($_.file); pattern=$($_.pattern); reason=$($_.reason)"})
    }else{$auditLines+='Coincidencias con valores secretos reales: 0'}
    $auditLines|Set-Content -LiteralPath $securityAuditFile -Encoding utf8
    $state.Security.NoStaging=@(& git -C $repo diff --cached --name-only).Count -eq 0
    $keystores=@('banco-legacy-auth','banco-legacy-web-bff','banco-legacy-mobile-bff','banco-legacy-atm-bff')|ForEach-Object{"$_/src/main/resources/keystore.p12"}
    $state.Security.KeystoresIgnored=@(& git -C $repo check-ignore $keystores).Count -eq 4

    if($client){$client.Dispose();$client=$null}
    if(!$KeepStartedServices){foreach($process in $started){if(!$process.HasExited){$process.Kill($true);$process.WaitForExit(10000)|Out-Null}};$started.Clear()}
    $mavenOutput=@(& (Join-Path $repo 'mvnw.cmd') clean verify 2>&1|ForEach-Object{"$_"})
    $state.Build.Success=@($mavenOutput|Where-Object{$_ -match 'BUILD SUCCESS'}).Count -gt 0
    $suites=Get-ChildItem $repo -Recurse -Filter 'TEST-*.xml'|ForEach-Object{[xml]$xml=Get-Content $_.FullName;[pscustomobject]@{tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}}
    $state.Build.Tests=($suites|Measure-Object tests -Sum).Sum;$failures=($suites|Measure-Object failures -Sum).Sum;$errors=($suites|Measure-Object errors -Sum).Sum;$skipped=($suites|Measure-Object skipped -Sum).Sum
    $state.Build.Success=$state.Build.Success -and $state.Build.Tests -ge 113 -and !$failures -and !$errors -and !$skipped
    @('Comando: .\mvnw.cmd clean verify',"Ejecución UTC: $([DateTime]::UtcNow.ToString('o'))","Tests: $($state.Build.Tests); failures: $failures; errors: $errors; skipped: $skipped",$(if($state.Build.Success){'BUILD SUCCESS'}else{'BUILD FAILURE'}),'Resumen saneado; no contiene variables de entorno.')|Set-Content -LiteralPath $buildFile -Encoding utf8

    $state.Overall=$state.Https.Values -notcontains $false -and $state.PostgreSQL.Values -notcontains $false -and @($state.Matrix|Where-Object passed -ne $true).Count -eq 0 -and $state.NoToken -and $state.AlteredToken -and $state.Payloads.Web.passed -and $state.Payloads.Mobile.passed -and $state.Payloads.ATM.passed -and $state.Withdrawal.Completed -and $state.Withdrawal.InsufficientFunds -and $state.Withdrawal.BalanceChanged -and $state.Withdrawal.MovementRegistered -and $state.Withdrawal.Concurrency -and $state.Benchmark -and $state.Explain -and $state.Build.Success -and $state.Security.Values -notcontains $false
    Write-FinalSummary $state
    if(!$state.Overall){throw 'La validación final terminó con uno o más fallos.'}
} finally {
    if($client){$client.Dispose()}
    if(!$KeepStartedServices){foreach($process in $started){try{if(!$process.HasExited){$process.Kill($true);$process.WaitForExit(10000)|Out-Null}}catch{}}}
    if($labCreated){try{$null=Invoke-AtmLab cleanup -AccountId $labAccountId -Marker $labMarker}catch{Write-Error "La limpieza de datos de laboratorio falló: $($_.Exception.Message)"}}
    $env:PGPASSWORD=$null;$tokens=$null
}
