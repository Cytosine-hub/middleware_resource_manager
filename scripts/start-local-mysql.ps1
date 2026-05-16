$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlHome = Join-Path $projectRoot "tools\mysql-8.4.9-winx64"
$mysqlBin = Join-Path $mysqlHome "bin"
$mysqld = Join-Path $mysqlBin "mysqld.exe"
$defaultsFile = Join-Path $projectRoot "mysql\my.ini"
$dataDir = Join-Path $projectRoot "mysql\data"
$logDir = Join-Path $projectRoot "mysql\logs"
$serviceName = "MiddlewareResourceMySQL84"
$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue

if ($null -eq $service) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null

    if (!(Test-Path (Join-Path $dataDir "mysql"))) {
        & $mysqld "--defaults-file=$defaultsFile" --initialize-insecure
    }

    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$defaultsFile" -WorkingDirectory $projectRoot | Out-Null
    Write-Host "MySQL service '$serviceName' is not installed. Started standalone mysqld instead."
    return
}

if ($service.Status -ne "Running") {
    Start-Service -Name $serviceName
    $service.WaitForStatus("Running", [TimeSpan]::FromSeconds(30))
}

Write-Host "MySQL service '$serviceName' is running on 127.0.0.1:3306"
