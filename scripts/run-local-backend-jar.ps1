$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\target\infra-portal-0.0.1-SNAPSHOT-exec.jar"

Set-Location -LiteralPath (Join-Path $ProjectRoot 'backend')
java -jar $JarPath
