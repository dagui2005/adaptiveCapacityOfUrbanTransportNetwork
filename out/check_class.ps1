$ErrorActionPreference = 'Continue'
$f = 'simulation\target\classes\org\matsim\network\BusNetworkIntegrator.class'
if (Test-Path $f) {
    $b = [System.IO.File]::ReadAllBytes($f)
    $t = [System.Text.Encoding]::ASCII.GetString($b)
    Write-Host ("Class file size: " + (Get-Item $f).Length + " bytes")
    Write-Host ("Modified: " + (Get-Item $f).LastWriteTime)
    Write-Host ("leftIsVirt in class   : " + $t.Contains('leftIsVirt'))
    Write-Host ("rightIsVirt in class  : " + $t.Contains('rightIsVirt'))
    Write-Host ("tailBridge in class   : " + $t.Contains('tailBridge'))
    Write-Host ("10000+500 in class    : " + $t.Contains('10000+500'))
    Write-Host ("bridgeSegIndex in class: " + $t.Contains('bridgeSegIndex'))
    Write-Host ("1000+998 in class     : " + $t.Contains('1000+998'))
    Write-Host ("1000+999 in class     : " + $t.Contains('1000+999'))
    Write-Host ("Layer 5b in class     : " + $t.Contains('Layer 5b'))
} else {
    Write-Host 'Class file not found'
}