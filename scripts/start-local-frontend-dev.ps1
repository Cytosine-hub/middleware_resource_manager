param(
    [int]$Port = 5173
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$FrontendRoot = Join-Path $ProjectRoot "frontend"
$RunnerPath = Join-Path $PSScriptRoot "run-local-frontend-dev.ps1"
$OutLogPath = Join-Path $ProjectRoot "frontend-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "frontend-local.err.log"

if (-not (Test-Path -LiteralPath (Join-Path $FrontendRoot "package.json"))) {
    throw "Frontend package.json not found: $FrontendRoot"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Frontend port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory $FrontendRoot `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Frontend started. Launcher PID: $($process.Id)"
Write-Host "Frontend stdout log: $OutLogPath"
Write-Host "Frontend stderr log: $ErrLogPath"
