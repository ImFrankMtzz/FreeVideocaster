<#
.SYNOPSIS
  Fase 0: prueba el Roku por ECP (puerto 8060) sin ninguna app.

.DESCRIPTION
  1. Busca el Roku en la red (SSDP) si no se indica la IP.
  2. Muestra datos del equipo (/query/device-info) y canales clave (/query/apps).
  3. Comprueba que ECP acepta comandos (/keypress/Home).
  4. Prueba "Play on Roku" (15985) y Roku Media Player (2213) con videos de prueba
     y, opcionalmente, con tu enlace de TorBox. Te pregunta si se reprodujo.

.EXAMPLE
  .\scripts\test-ecp.ps1
  .\scripts\test-ecp.ps1 -RokuIp 192.168.1.50
  .\scripts\test-ecp.ps1 -RokuIp 192.168.1.50 -VideoUrl "https://...enlace de TorBox..."
#>
param(
    [string]$RokuIp,
    [string]$VideoUrl,
    [switch]$SkipKeypress
)

$SampleHttp  = 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4'
$SampleHttps = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4'

$results = New-Object System.Collections.Generic.List[object]

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "== $Text ==" -ForegroundColor Cyan
}

function Find-Roku {
    # SSDP: se envía un M-SEARCH por multicast y cada Roku responde con su URL de ECP.
    $udp = New-Object System.Net.Sockets.UdpClient
    $udp.Client.ReceiveTimeout = 1000
    $msg = "M-SEARCH * HTTP/1.1`r`nHost: 239.255.255.250:1900`r`nMan: `"ssdp:discover`"`r`nST: roku:ecp`r`nMX: 2`r`n`r`n"
    $bytes = [Text.Encoding]::ASCII.GetBytes($msg)
    $target = New-Object System.Net.IPEndPoint([Net.IPAddress]::Parse('239.255.255.250'), 1900)
    [void]$udp.Send($bytes, $bytes.Length, $target)

    $found = @()
    $deadline = (Get-Date).AddSeconds(4)
    while ((Get-Date) -lt $deadline) {
        try {
            $remote = New-Object System.Net.IPEndPoint([Net.IPAddress]::Any, 0)
            $resp = [Text.Encoding]::ASCII.GetString($udp.Receive([ref]$remote))
            if ($resp -match '(?im)^LOCATION:\s*http://([\d\.]+):8060') { $found += $Matches[1] }
        } catch [System.Net.Sockets.SocketException] {
            # Timeout de 1 s sin respuesta: se sigue esperando hasta el límite.
        }
    }
    $udp.Close()
    $found | Select-Object -Unique
}

function Invoke-Ecp([string]$Path, [switch]$Post) {
    # Ojo: en Windows PowerShell 5.1 "curl.exe -d ''" no funciona (se descarta el
    # argumento vacío), por eso se usa -X POST sin cuerpo.
    $url = "http://${RokuIp}:8060$Path"
    if ($Post) {
        $out = & curl.exe -s -m 10 -X POST -w "`n%{http_code}" $url
    } else {
        $out = & curl.exe -s -m 10 -w "`n%{http_code}" $url
    }
    $lines = @($out)
    $code = $lines[-1]
    $body = ($lines | Select-Object -SkipLast 1) -join "`n"
    [pscustomobject]@{ Code = $code; Body = $body }
}

function Read-YesNo([string]$Question) {
    $answer = Read-Host "$Question (s/n)"
    return $answer -match '^[sSyY]'
}

function Test-Playback([string]$Name, [string]$Path) {
    Write-Host ""
    Write-Host "-> $Name" -ForegroundColor Yellow
    Write-Host "   POST $Path" -ForegroundColor DarkGray
    $r = Invoke-Ecp $Path -Post
    Write-Host "   Respuesta HTTP: $($r.Code)"
    if ($r.Code -ne '200') {
        $results.Add([pscustomobject]@{ Prueba = $Name; HTTP = $r.Code; Reproduce = 'no (rechazado)' })
        return
    }
    Write-Host "   Espera unos 10 segundos mirando la TV..."
    Start-Sleep -Seconds 10
    $mp = Invoke-Ecp '/query/media-player'
    if ($mp.Body) { Write-Host "   Estado del reproductor:`n$($mp.Body)" -ForegroundColor DarkGray }
    $ok = Read-YesNo '   ¿Se está reproduciendo el video en el Roku?'
    $results.Add([pscustomobject]@{ Prueba = $Name; HTTP = $r.Code; Reproduce = $(if ($ok) { 'SI' } else { 'no' }) })
    [void](Invoke-Ecp '/keypress/Home' -Post)
    Start-Sleep -Seconds 2
}

# --- 1. Localizar el Roku ---------------------------------------------------
if (-not $RokuIp) {
    Write-Step 'Buscando Roku en la red (SSDP, 4 s)'
    Write-Host 'Si Windows pregunta por el firewall, permite el acceso en redes privadas.'
    $ips = @(Find-Roku)
    if ($ips.Count -eq 0) {
        Write-Host 'No se encontró ningún Roku. Vuelve a ejecutar con -RokuIp <IP>.' -ForegroundColor Red
        Write-Host 'La IP está en el Roku: Configuración > Red > Acerca de.'
        exit 1
    }
    Write-Host "Encontrado(s): $($ips -join ', ')"
    $RokuIp = $ips[0]
}
Write-Host "Usando Roku en $RokuIp"

# --- 2. Información del equipo ----------------------------------------------
Write-Step 'Información del equipo (/query/device-info)'
$di = Invoke-Ecp '/query/device-info'
if ($di.Code -ne '200') {
    Write-Host "No responde ECP en ${RokuIp}:8060 (HTTP $($di.Code))." -ForegroundColor Red
    Write-Host 'Revisa: misma red Wi-Fi, IP correcta y en el Roku'
    Write-Host '  Configuración > Sistema > Configuración avanzada > Control por aplicaciones móviles > Acceso a la red.'
    exit 1
}
$info = ([xml]$di.Body).'device-info'
$fields = 'model-name', 'model-number', 'friendly-device-name', 'software-version', 'software-build',
          'is-tv', 'is-stick', 'ui-resolution', 'supports-audio-volume-control', 'supports-tv-power-control',
          'developer-enabled', 'network-type'
foreach ($f in $fields) {
    $v = $info.$f
    if ($null -ne $v) { Write-Host ("  {0,-32} {1}" -f $f, $v) }
}

Write-Step 'Canales relevantes (/query/apps)'
$apps = ([xml](Invoke-Ecp '/query/apps').Body).apps.app
foreach ($id in '15985', '2213', 'dev') {
    $app = $apps | Where-Object { $_.id -eq $id }
    if ($app) { Write-Host "  $id -> $($app.'#text') (instalado)" -ForegroundColor Green }
    else      { Write-Host "  $id -> no aparece" -ForegroundColor DarkYellow }
}

# --- 3. ¿Acepta comandos? ---------------------------------------------------
if (-not $SkipKeypress) {
    Write-Step 'Prueba de comandos (/keypress/Home)'
    $kp = Invoke-Ecp '/keypress/Home' -Post
    Write-Host "  Respuesta HTTP: $($kp.Code)"
    if ($kp.Code -eq '403') {
        Write-Host '  403 = el Roku bloquea el control. Activa "Control por aplicaciones móviles".' -ForegroundColor Red
    }
    $results.Add([pscustomobject]@{ Prueba = 'keypress/Home'; HTTP = $kp.Code; Reproduce = '-' })
    Start-Sleep -Seconds 2
}

# --- 4. Reproducción con los canales de Roku --------------------------------
Write-Step 'Pruebas de reproducción'
$videos = [ordered]@{ 'MP4 HTTP' = $SampleHttp; 'MP4 HTTPS' = $SampleHttps }
if ($VideoUrl) { $videos['Tu enlace'] = $VideoUrl }

foreach ($name in $videos.Keys) {
    $u = [uri]::EscapeDataString($videos[$name])
    Test-Playback "15985 /input  ($name)"  "/input/15985?t=v&u=$u&videoName=Prueba&videoFormat=mp4"
    Test-Playback "15985 /launch ($name)"  "/launch/15985?t=v&u=$u&videoName=Prueba&videoFormat=mp4"
    Test-Playback "2213 /launch  ($name)"  "/launch/2213?contentId=$u&mediaType=movie"
}

if ($VideoUrl) {
    Write-Host ""
    Write-Host "Longitud de tu enlace: $($VideoUrl.Length) caracteres (codificado: $([uri]::EscapeDataString($VideoUrl).Length))."
    if ($VideoUrl.Length -gt 255) {
        Write-Host 'Supera los 255 caracteres que Roku documenta para contentId: dato importante para la fase 1.' -ForegroundColor DarkYellow
    }
}

# --- Resumen ----------------------------------------------------------------
Write-Step 'Resumen (copia esto y pásamelo)'
Write-Host "Modelo: $($info.'model-name') ($($info.'model-number'))  Roku OS: $($info.'software-version')  TV: $($info.'is-tv')"
$results | Format-Table -AutoSize | Out-String | Write-Host
