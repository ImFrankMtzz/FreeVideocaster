<#
.SYNOPSIS
  Empaqueta el canal roku-receiver en un ZIP y lo instala en el Roku (modo desarrollador).

.DESCRIPTION
  Requiere tener activado el modo desarrollador en el Roku
  (control: Inicio x3, Arriba x2, Derecha, Izquierda, Derecha, Izquierda, Derecha).
  El usuario siempre es "rokudev"; la contraseña es la que elegiste al activarlo.

.EXAMPLE
  .\scripts\deploy-roku.ps1 -RokuIp 192.168.1.50
#>
param(
    [Parameter(Mandatory = $true)][string]$RokuIp,
    [string]$Password
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'roku-receiver'
$outDir = Join-Path $root 'out'
$zipPath = Join-Path $outDir 'freevideocaster-roku.zip'

# --- Empaquetar --------------------------------------------------------------
# Se arma el ZIP a mano porque Compress-Archive (PowerShell 5.1) guarda las rutas
# con "\" y el instalador de Roku las rechaza.
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force $outDir | Out-Null
if (Test-Path $zipPath) { Remove-Item $zipPath }

$zip = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    Get-ChildItem $source -Recurse -File | ForEach-Object {
        $entry = $_.FullName.Substring($source.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $entry)
    }
} finally {
    $zip.Dispose()
}
Write-Host "Paquete creado: $zipPath"

# --- Instalar ----------------------------------------------------------------
if (-not $Password) {
    $secure = Read-Host 'Contraseña de desarrollador del Roku (usuario rokudev)' -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

Write-Host "Instalando en $RokuIp ..."
$response = & curl.exe -s -S -m 60 --digest --user "rokudev:$Password" `
    -F "mysubmit=Install" -F "archive=@$zipPath" `
    -w "`nHTTP %{http_code}" "http://$RokuIp/plugin_install"
$text = @($response) -join "`n"

if ($text -match 'HTTP 401') {
    Write-Host 'Contraseña incorrecta (HTTP 401).' -ForegroundColor Red
    exit 1
}
if ($text -match 'Install Success|Identical to previous version') {
    Write-Host 'Instalado. El canal se abre solo en el Roku.' -ForegroundColor Green
    Write-Host "Consola de depuración: telnet $RokuIp 8085"
    exit 0
}

# Mostrar el mensaje que devuelve el instalador para diagnosticar.
$messages = [regex]::Matches($text, "'(?:text|message)'\s*:\s*'([^']+)'|<font[^>]*>([^<]+)</font>") |
    ForEach-Object { ($_.Groups[1].Value + $_.Groups[2].Value).Trim() } | Where-Object { $_ }
Write-Host 'La instalación no confirmó éxito. Respuesta del Roku:' -ForegroundColor Red
if ($messages) { $messages | ForEach-Object { Write-Host "  $_" } } else { Write-Host $text }
exit 1
