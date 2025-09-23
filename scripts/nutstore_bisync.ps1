param(
    [switch]$Resync,
    [switch]$VerboseLog
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path $scriptDir -Parent
$localDir = Join-Path $repoRoot 'resources'
$remote = 'nutstore:FastPig/resources'
$logDir = Join-Path $repoRoot 'logs'

if (-not (Test-Path $localDir)) {
    throw ("Local path not found: {0}" -f $localDir)
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# 查找 rclone 可执行文件
$rclone = $null
$toolsDir = Join-Path $repoRoot '.tools'
$candidates = @()
$candidates += 'rclone'
if (Test-Path $toolsDir) {
    $found = Get-ChildItem -Recurse -Filter 'rclone.exe' -Path $toolsDir -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($found) { $candidates += $found.FullName }
}

foreach ($p in $candidates) {
    try {
        & $p version *> $null
        if ($LASTEXITCODE -eq 0) { $rclone = $p; break }
    } catch {}
}

if (-not $rclone) {
    throw "rclone executable not found. Install or place under .tools"
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = Join-Path $logDir ("bisync_" + $timestamp + ".log")

$logLevel = if ($VerboseLog) { "DEBUG" } else { "INFO" }

$args = @(
    'bisync', $localDir, $remote,
    '--checkers','16',
    '--transfers','8',
    '--fast-list',
    '--log-level', $logLevel,
    '--log-file', $logFile
)

if ($Resync) { $args += '--resync' }

& $rclone @args
if ($LASTEXITCODE -ne 0) { throw ("bisync failed, see log: {0}" -f $logFile) }

Write-Output ("bisync done. log: {0}" -f $logFile)


