#requires -Version 7.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../Week6.Config.ps1')

foreach ($channel in @('WEB', 'MOBILE', 'ATM')) {
    $config = @'
{
  "propertySources": [
    { "name": "file:config-repository/channel.yml", "source": {
      "banking.account-service-name": "banco-legacy-account-service"
    } },
    { "name": "file:config-repository/application.yml", "source": {
      "security.jwt.issuer": "banco-legacy-auth"
    } }
  ]
}
'@ | ConvertFrom-Json
    $names = Get-Week6AccountServiceNames -PropertySources @($config.propertySources)
    if ($names -isnot [array] -or $names.Count -ne 1 -or
            $names[0] -ne 'banco-legacy-account-service') {
        throw "$channel`: no se recuperó el nombre lógico esperado bajo StrictMode."
    }
}
'PASS: WEB, MOBILE y ATM aceptan una propertySource sin banking.account-service-name bajo StrictMode.'
