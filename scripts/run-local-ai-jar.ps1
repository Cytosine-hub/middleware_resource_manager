$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\ai-service\target\ai-service-0.0.1-SNAPSHOT-exec.jar"

Set-Location -LiteralPath (Join-Path $ProjectRoot 'backend')
java -jar $JarPath
