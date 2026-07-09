# Sync api.physical_device_ip in local.properties when api.mode=local.
# Usage (from kmp-app/):
#   .\scripts\sync-local-api-ip.ps1
#
# Gradle runs the same logic automatically via gradle/sync-local-api-ip.gradle.kts on every build.

param(
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)

$ErrorActionPreference = "Stop"

function Read-ApiMode {
    param([string]$Root)
    $props = @{}
    foreach ($file in @("api.properties", "local.properties")) {
        $path = Join-Path $Root $file
        if (-not (Test-Path -LiteralPath $path)) { continue }
        Get-Content -LiteralPath $path | ForEach-Object {
            $line = $_.Trim()
            if ($line -match '^\s*#' -or $line -eq "") { return }
            if ($line -match '^\s*([^=]+?)\s*=\s*(.*)$') {
                $props[$Matches[1].Trim()] = $Matches[2].Trim()
            }
        }
    }
    if ($props.ContainsKey("api.mode")) { return $props["api.mode"] }
    return "local"
}

function Get-LocalLanIp {
    $candidates = @()
    try {
        $candidates = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object {
                $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and
                $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)'
            } |
            Sort-Object @{
                Expression = {
                    if ($_.InterfaceAlias -match 'Wi-?Fi') { 0 }
                    elseif ($_.InterfaceAlias -match 'Ethernet') { 1 }
                    else { 2 }
                }
            }, InterfaceMetric |
            Select-Object -ExpandProperty IPAddress -First 1
    } catch {
        $candidates = $null
    }

    if ($candidates) { return $candidates }

    $ipconfig = ipconfig | Out-String
    $matches = [regex]::Matches(
        $ipconfig,
        'IPv4 Address[^:]*:\s*((?:192\.168|10\.|172\.(?:1[6-9]|2\d|3[01]))\.\d+\.\d+)'
    )
    if ($matches.Count -gt 0) { return $matches[0].Groups[1].Value }
    return $null
}

function Set-PropertyKey {
    param(
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )
    $lines = if (Test-Path -LiteralPath $FilePath) {
        [System.Collections.Generic.List[string]]@(Get-Content -LiteralPath $FilePath)
    } else {
        [System.Collections.Generic.List[string]]@()
    }

    $pattern = "^\s*$([regex]::Escape($Key))\s*="
    $newLine = "$Key=$Value"
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) {
            $index = $i
            break
        }
    }

    if ($index -ge 0) {
        $lines[$index] = $newLine
    } else {
        if ($lines.Count -gt 0 -and $lines[$lines.Count - 1].Trim() -ne "") {
            $lines.Add("")
        }
        $lines.Add($newLine)
    }

    $text = ($lines -join "`n").TrimEnd()
    if ($text.Length -gt 0) { $text += "`n" }
    Set-Content -LiteralPath $FilePath -Value $text -Encoding UTF8 -NoNewline
}

Set-Location $ProjectRoot

$apiMode = Read-ApiMode -Root $ProjectRoot
if ($apiMode -ne "local") {
    Write-Host ('[movit] api.mode=' + $apiMode + ' - skipping local IP sync')
    exit 0
}

$detected = Get-LocalLanIp
if (-not $detected) {
    Write-Host '[movit] api.mode=local but no LAN IPv4 found; leaving api.physical_device_ip unchanged'
    exit 0
}

$localProps = Join-Path $ProjectRoot "local.properties"
$current = $null
if (Test-Path -LiteralPath $localProps) {
    $match = Select-String -LiteralPath $localProps -Pattern '^\s*api\.physical_device_ip\s*=\s*(.+)$' |
        Select-Object -First 1
    if ($match) { $current = $match.Matches[0].Groups[1].Value.Trim() }
}

if ($current -eq $detected) {
    Write-Host ('[movit] api.physical_device_ip already ' + $detected)
    exit 0
}

Set-PropertyKey -FilePath $localProps -Key "api.physical_device_ip" -Value $detected
if ($current) {
    Write-Host ('[movit] api.mode=local -> api.physical_device_ip=' + $detected + ' (was ' + $current + ')')
} else {
    Write-Host ('[movit] api.mode=local -> api.physical_device_ip=' + $detected)
}
