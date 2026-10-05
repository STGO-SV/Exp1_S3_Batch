param(
    [ValidateSet('Focused','Verify')]
    [string]$TestMode='Verify',
    [string]$LogName='etapa6-verify-final.log'
)
$ErrorActionPreference='Stop'
$repositoryRoot=Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
$previous=@{}
$names=@('EFT_POSTGRES_TEST_URL','EFT_POSTGRES_TEST_USER','EFT_POSTGRES_TEST_PASSWORD')
foreach($name in $names) { $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $line=Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^POSTGRES_PASSWORD=' } | Select-Object -First 1
    if(-not $line) { throw 'POSTGRES_PASSWORD no configurado en .env' }
    $env:EFT_POSTGRES_TEST_URL='jdbc:postgresql://localhost:5433/banco_legacy_batch'
    $env:EFT_POSTGRES_TEST_USER='postgres'
    $env:EFT_POSTGRES_TEST_PASSWORD=$line.Substring($line.IndexOf('=')+1).Trim().Trim('"').Trim("'")
    if($LogName -notmatch '^[a-zA-Z0-9._-]+[.]log$') { throw 'Nombre de log inválido' }
    $logPath=Join-Path '.local' $LogName
    $arguments=@('verify')
    if($TestMode -eq 'Focused') {
        $arguments=@('-pl','banco-legacy-account-service,banco-legacy-payment-service,banco-legacy-customer-service,banco-legacy-web-bff,banco-legacy-mobile-bff,banco-legacy-atm-bff','-am','test')
    }
    & mvn @arguments *> $logPath
    $result=$LASTEXITCODE
    Get-Content -LiteralPath $logPath -Tail 23
} finally {
    foreach($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    Pop-Location
}
exit $result
