param(
    [int]$Port = 8084
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\core-service\target\core-service-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-core-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "core-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "core-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Core service jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Core service port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Core service started. Launcher PID: $($process.Id)"
Write-Host "Core stdout log: $OutLogPath"
Write-Host "Core stderr log: $ErrLogPath"
