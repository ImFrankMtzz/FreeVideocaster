<#
.SYNOPSIS
  Envía un enlace de video al canal FreeVideocaster del Roku (prueba de la fase 1a, sin app).

.DESCRIPTION
  -Mode launch (por defecto): abre el canal con el enlace (POST /launch/dev?...).
  -Mode input: envía el enlace al canal ya abierto (POST /input?...), sin reiniciarlo.

.EXAMPLE
  .\scripts\send-to-roku.ps1 -RokuIp 192.168.1.50 -Url "https://...mkv" -Title "Prueba" -Format mkv
  .\scripts\send-to-roku.ps1 -RokuIp 192.168.1.50 -Url "https://...mp4" -Mode input
  .\scripts\send-to-roku.ps1 -RokuIp 192.168.1.50 -Url "https://...mp4" -Sub "https://...srt"
#>
param(
    [Parameter(Mandatory = $true)][string]$RokuIp,
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Title = 'Prueba',
    [string]$Format,
    [string]$Sub,
    [ValidateSet('launch', 'input')][string]$Mode = 'launch'
)

$query = 'url=' + [uri]::EscapeDataString($Url) + '&title=' + [uri]::EscapeDataString($Title)
if ($Format) { $query += '&format=' + [uri]::EscapeDataString($Format) }
if ($Sub)    { $query += '&sub=' + [uri]::EscapeDataString($Sub) }

if ($Mode -eq 'launch') { $path = "/launch/dev?$query" } else { $path = "/input?$query" }
$target = "http://${RokuIp}:8060$path"

Write-Host "Enlace: $($Url.Length) caracteres. Petición completa: $($target.Length) caracteres."
Write-Host "POST $target" -ForegroundColor DarkGray

# -X POST sin cuerpo: en PowerShell 5.1 "curl.exe -d ''" no funciona.
$code = & curl.exe -s -m 10 -X POST -o NUL -w "%{http_code}" $target
Write-Host "Respuesta HTTP: $code"
switch ($code) {
    '200'   { Write-Host 'Aceptado. Mira la TV; si falla, el canal muestra el error en pantalla.' -ForegroundColor Green }
    '000'   { Write-Host 'Sin respuesta: revisa la IP y que el PC esté en la misma red.' -ForegroundColor Red }
    '403'   { Write-Host 'Bloqueado: activa "Control por aplicaciones móviles" en el Roku.' -ForegroundColor Red }
    '404'   { Write-Host 'No encontrado: ¿está instalado el canal de desarrollador? Ejecuta deploy-roku.ps1.' -ForegroundColor Red }
    default { Write-Host 'Respuesta inesperada.' -ForegroundColor Yellow }
}
