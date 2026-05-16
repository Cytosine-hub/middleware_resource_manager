$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlHome = Join-Path $projectRoot "tools\mysql-8.4.9-winx64"
$mysqlBin = Join-Path $mysqlHome "bin"
$mysqld = Join-Path $mysqlBin "mysqld.exe"
$defaultsFile = Join-Path $projectRoot "mysql\my.ini"
$dataDir = Join-Path $projectRoot "mysql\data"
$logDir = Join-Path $projectRoot "mysql\logs"
$serviceName = "MiddlewareResourceMySQL84"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    throw "Run this script from an Administrator PowerShell window."
}

if (!(Test-Path $mysqld)) {
    throw "mysqld.exe not found: $mysqld"
}

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Grant-AccessRule {
    param(
        [string]$Path,
        [string]$Identity,
        [System.Security.AccessControl.FileSystemRights]$Rights
    )

    $acl = Get-Acl -Path $Path
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $Identity,
        $Rights,
        [System.Security.AccessControl.InheritanceFlags]"ContainerInherit, ObjectInherit",
        [System.Security.AccessControl.PropagationFlags]::None,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    $acl.SetAccessRule($rule)
    Set-Acl -Path $Path -AclObject $acl
}

Grant-AccessRule -Path $mysqlHome -Identity "SYSTEM" -Rights ([System.Security.AccessControl.FileSystemRights]::ReadAndExecute)
Grant-AccessRule -Path $dataDir -Identity "SYSTEM" -Rights ([System.Security.AccessControl.FileSystemRights]::FullControl)
Grant-AccessRule -Path $logDir -Identity "SYSTEM" -Rights ([System.Security.AccessControl.FileSystemRights]::FullControl)
Grant-AccessRule -Path $dataDir -Identity $env:USERNAME -Rights ([System.Security.AccessControl.FileSystemRights]::FullControl)
Grant-AccessRule -Path $logDir -Identity $env:USERNAME -Rights ([System.Security.AccessControl.FileSystemRights]::FullControl)

if (!(Test-Path (Join-Path $dataDir "mysql"))) {
    & $mysqld "--defaults-file=$defaultsFile" --initialize-insecure
}

$existing = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    if ($existing.Status -eq "Running") {
        Stop-Service -Name $serviceName -Force
        Start-Sleep -Seconds 2
    }
    sc.exe delete $serviceName | Out-Null
    Start-Sleep -Seconds 2
}

& $mysqld --install $serviceName "--defaults-file=$defaultsFile"
sc.exe config $serviceName start= auto | Out-Null

$installed = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($null -eq $installed) {
    throw "Failed to install Windows service '$serviceName'."
}

Start-Service -Name $serviceName
(Get-Service -Name $serviceName).WaitForStatus("Running", [TimeSpan]::FromSeconds(30))

Write-Host "MySQL Windows service '$serviceName' is installed and running."
