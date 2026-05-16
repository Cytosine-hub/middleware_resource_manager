$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlHome = Join-Path $projectRoot "tools\mysql-8.4.9-winx64"
$mysqlBin = Join-Path $mysqlHome "bin"
$mysqladmin = Join-Path $mysqlBin "mysqladmin.exe"
$defaultsFile = Join-Path $projectRoot "mysql\my.ini"
$serviceName = "MiddlewareResourceMySQL84"
$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue

if ($null -eq $service) {
    & $mysqladmin "--defaults-file=$defaultsFile" shutdown
    Write-Host "Standalone mysqld has been stopped"
    return
}

if ($service.Status -ne "Stopped") {
    Stop-Service -Name $serviceName
    $service.WaitForStatus("Stopped", [TimeSpan]::FromSeconds(30))
}

Write-Host "MySQL service '$serviceName' has been stopped"
