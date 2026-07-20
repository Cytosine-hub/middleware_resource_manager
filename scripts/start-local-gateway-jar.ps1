param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\api-gateway\target\api-gateway-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-gateway-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "gateway-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "gateway-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Gateway jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Gateway port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Gateway started. Launcher PID: $($process.Id)"
Write-Host "Gateway stdout log: $OutLogPath"
Write-Host "Gateway stderr log: $ErrLogPath"
