param(
    [int]$Port = 8083
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\ai-service\target\ai-service-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-ai-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "ai-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "ai-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "AI service jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "AI service port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "AI service started. Launcher PID: $($process.Id)"
Write-Host "AI stdout log: $OutLogPath"
Write-Host "AI stderr log: $ErrLogPath"
