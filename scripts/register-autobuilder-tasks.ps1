param(
    [string]$TaskPrefix = "WebBasedTranslator",
    [string]$DailyTime = "09:00"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$scriptPath = Join-Path $repoRoot "scripts\autocommit-if-green.ps1"

if (-not (Test-Path $scriptPath)) {
    throw "Required script not found: $scriptPath"
}

$startupTask = "$TaskPrefix-AutoCommit-OnStartupFolder"
$dailyTask = "$TaskPrefix-AutoCommit-Daily"

$startupFolder = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup'
$startupCmdPath = Join-Path $startupFolder "$startupTask.cmd"
$taskCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""

if (-not (Test-Path $startupFolder)) {
    throw "Startup folder not found: $startupFolder"
}

Write-Host "Creating startup launcher: $startupCmdPath"
@(
    '@echo off',
    'cd /d "%~dp0"',
    $taskCommand
) | Set-Content -Path $startupCmdPath -Encoding ASCII

Write-Host "Registering daily task: $dailyTask at $DailyTime"
& schtasks /Create /TN $dailyTask /TR $taskCommand /SC DAILY /ST $DailyTime /F | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create daily task '$dailyTask' (exit code: $LASTEXITCODE)."
}

Write-Host "Tasks registered successfully."
Write-Host "Startup automation is installed via the Startup folder launcher."
Write-Host "Use schtasks /Query /TN $dailyTask to verify the daily task."
