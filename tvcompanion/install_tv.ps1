<#
.SYNOPSIS
  Install and launch the Luma TV companion on an LG webOS TV.

.DESCRIPTION
  One-shot install using the official @webos-tools/cli. The TV must be on the
  same LAN, Developer Mode enabled, and its Developer Mode app OPEN with
  "Key Server" turned on. The passphrase is the one shown in that app.

.EXAMPLE
  .\install_tv.ps1 -Passphrase '123456'

  .\install_tv.ps1 -Passphrase '123456' -Ip '192.168.1.20' -SkipKey
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Passphrase,

    [string]$Ip = '192.168.1.2',
    [string]$Device = 'lumatv',
    [string]$AresBin = 'D:\apps\tools\webos-cli\node_modules\.bin',
    [switch]$SkipKey
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ipk = Join-Path $here 'com.luma.tvcompanion_1.0.0_all.ipk'

if (-not (Test-Path $ipk)) {
    throw "Missing package: $ipk. Run: python package_ipk.py"
}

function Ares($name, [string[]]$arguments) {
    $exe = Join-Path $AresBin "$name.cmd"
    if (-not (Test-Path $exe)) { $exe = $name }
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$name failed with exit code $LASTEXITCODE" }
}

Write-Host "Target TV: $Ip  (Developer Mode app must be open, Key Server ON)"

# (Re)create the device entry. ares-setup-device -a merges on this CLI version.
Ares 'ares-setup-device' @(
    '-a', $Device,
    '-i', 'username=root',
    '-i', "host=$Ip",
    '-i', 'port=9922',
    '-i', 'default=true'
)

if (-not $SkipKey) {
    Write-Host 'Fetching the SSH key from the TV key server (port 9991)…'
    Ares 'ares-novacom' @('--getkey', '-d', $Device, '--passphrase', $Passphrase)
    # The CLI stores the private key encrypted with the passphrase; ares uses
    # that stored value from then on, so no password is needed for install.
    Ares 'ares-setup-device' @('-m', $Device, '-i', "passphrase=$Passphrase")
}

Write-Host "Installing $ipk …"
Ares 'ares-install' @($ipk, '-d', $Device)

Write-Host 'Launching the companion service app…'
Ares 'ares-launch' @('com.luma.tvcompanion', '-d', $Device)

Write-Host 'Waiting for the HTTP status endpoint…'
$status = $null
for ($i = 0; $i -lt 20; $i++) {
    try {
        $status = (Invoke-WebRequest -Uri "http://${Ip}:8081/status" -UseBasicParsing -TimeoutSec 2).Content
        break
    } catch {
        Start-Sleep -Milliseconds 500
    }
}
if (-not $status) {
    throw 'App installed but http://TV:8081/status did not respond. Open the companion app on the TV once.'
}

Write-Host "STATUS: $status"
Write-Host ''
Write-Host 'Done. On the phone, tap "TV app" in the Connect screen, then Connect.'
