#requires -Version 7.0
Set-StrictMode -Version Latest

function Get-Week6AccountServiceNames {
    param([object[]]$PropertySources)

    $names = @(
        foreach ($source in $PropertySources) {
            $property = $source.source.PSObject.Properties['banking.account-service-name']
            if ($null -ne $property) { $property.Value }
        }
    )
    return ,$names
}
