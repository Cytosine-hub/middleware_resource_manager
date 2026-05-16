$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "target\middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar"

Set-Location -LiteralPath $ProjectRoot
java -jar $JarPath
