# Starts Redis >= 6.2 for local BullMQ on port 6380 (avoids Laragon/Windows Redis 5.x on 6379).
$ErrorActionPreference = 'Stop'

$Port = 6380
$Root = Split-Path -Parent $PSScriptRoot
$ToolsDir = Join-Path $Root 'tools\redis-win'
$RedisServer = Join-Path $ToolsDir 'redis-server.exe'
$ZipUrl = 'https://github.com/redis-windows/redis-windows/releases/download/7.4.9/Redis-7.4.9-Windows-x64-msys2.zip'
$ZipPath = Join-Path $env:TEMP 'Redis-7.4.9-Windows-x64-msys2.zip'

function Test-RedisPort {
    param([int]$TargetPort)
    try {
        $pong = & redis-cli -p $TargetPort ping 2>$null
        if ($pong -ne 'PONG') { return $null }
        $versionLine = & redis-cli -p $TargetPort INFO server 2>$null | Select-String 'redis_version'
        if (-not $versionLine) { return $null }
        return ($versionLine -replace '.*:', '').Trim()
    } catch {
        return $null
    }
}

$existing = Test-RedisPort -TargetPort $Port
if ($existing) {
    Write-Host "Redis already running on port $Port (version $existing)."
    exit 0
}

if (-not (Test-Path $RedisServer)) {
    Write-Host "Downloading Redis 7.4.9 for Windows..."
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    Invoke-WebRequest -Uri $ZipUrl -OutFile $ZipPath -UseBasicParsing
    Expand-Archive -Path $ZipPath -DestinationPath $ToolsDir -Force
    $nested = Get-ChildItem -Path $ToolsDir -Recurse -Filter 'redis-server.exe' | Select-Object -First 1
    if (-not $nested) {
        throw "redis-server.exe not found after extract"
    }
    if ($nested.DirectoryName -ne $ToolsDir) {
        Copy-Item -Path (Join-Path $nested.DirectoryName '*') -Destination $ToolsDir -Recurse -Force
    }
    Remove-Item $ZipPath -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path $RedisServer)) {
    throw "Missing $RedisServer"
}

$running = Get-Process redis-server -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -and $_.Path.StartsWith($ToolsDir, [System.StringComparison]::OrdinalIgnoreCase)
}
if (-not $running) {
    Write-Host "Starting Redis 7 on port $Port..."
    Start-Process -FilePath $RedisServer -ArgumentList @('--port', "$Port", '--save', '""') -WindowStyle Hidden
    Start-Sleep -Seconds 2
}

$version = Test-RedisPort -TargetPort $Port
if (-not $version) {
    throw "Failed to start Redis on port $Port"
}

Write-Host "Redis ready on localhost:$Port (version $version). Set REDIS_PORT=$Port in .env"
