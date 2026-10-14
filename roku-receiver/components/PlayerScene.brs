sub init()
    m.video = m.top.findNode("video")
    m.idle = m.top.findNode("idle")
    m.statusBox = m.top.findNode("statusBox")
    m.statusLabel = m.top.findNode("statusLabel")
    m.hideTimer = m.top.findNode("hideTimer")
    m.statusTimer = m.top.findNode("statusTimer")
    m.queue = []
    m.current = invalid   ' Orden del video que se está reproduciendo
    m.seq = 0             ' Aumenta con cada video nuevo; la app lo usa para detectar cambios
    m.lastError = ""
    m.subsChosen = false  ' true si el usuario (o el enlace) eligió subtítulos para el video actual
    m.flip = invalid      ' Cambio de audio de ida y vuelta en curso (ver startAudioFlip)
    m.flipPending = false ' Hay que hacerlo en cuanto el video vuelva a reproducirse
    m.flipTimer = m.top.findNode("flipTimer")
    m.flipTimer.observeField("fire", "onFlipTimer")

    m.video.observeField("state", "onVideoState")
    m.video.observeField("currentSubtitleTrack", "onSubtitleChanged")
    m.hideTimer.observeField("fire", "hideStatus")
    m.statusTimer.observeField("fire", "onStatusTimer")

    showIpAddress()
    publishStatus()
    m.top.setFocus(true)
end sub

' Muestra la IP en la pantalla de espera para poder escribirla en el teléfono.
sub showIpAddress()
    ips = CreateObject("roDeviceInfo").GetIPAddrs()
    for each iface in ips
        m.top.findNode("ipLabel").text = "IP de este Roku: " + ips[iface]
        return
    end for
end sub

' Resume qué pasa ahora; main.brs lo sirve como JSON en http://ROKU:8061/status.
' Las claves van en minúsculas a propósito.
' Cada segundo: apaga los subtítulos que el Roku active solo y publica el estado.
sub onStatusTimer()
    keepSubtitlesOffUnlessChosen()
    publishStatus()
end sub

sub publishStatus()
    state = "idle"
    if m.video.visible
        videoState = m.video.state
        if videoState = "playing"
            state = "play"
        else if videoState = "paused"
            state = "pause"
        else
            state = "buffering"
        end if
    end if

    key = ""
    title = ""
    position = 0
    duration = 0
    if state <> "idle"
        if m.current <> invalid
            key = m.current.key
            title = titleOf(m.current)
        end if
        position = Int(m.video.position * 1000)
        duration = Int(m.video.duration * 1000)
    end if

    queue = []
    for each item in m.queue
        queue.Push({ key: item.key, title: titleOf(item) })
    end for

    ' Pistas de audio y de subtítulos disponibles (vacías en reposo) y cuál suena ahora.
    audio = []
    audioCurrent = ""
    subtitles = []
    subtitleCurrent = ""
    if state <> "idle"
        audio = trackList(m.video.availableAudioTracks, "Track", ["Name"])
        audioCurrent = textOf(m.video.currentAudioTrack)
        if m.flip <> invalid then audioCurrent = m.flip.audio  ' Durante el cambio se sigue mostrando el audio real
        subtitles = trackList(m.video.availableSubtitleTracks, "TrackName", ["Description"])
        if m.video.globalCaptionMode = "On" then subtitleCurrent = textOf(m.video.currentSubtitleTrack)
    end if

    m.top.status = {
        v: 2
        state: state
        key: key
        title: title
        position: position
        duration: duration
        queue: queue
        error: m.lastError
        seq: m.seq
        audio: audio
        audiocur: audioCurrent
        subs: subtitles
        subcur: subtitleCurrent
    }
end sub

function textOf(value as Dynamic) as String
    if GetInterface(value, "ifString") <> invalid then return value
    return ""
end function

' Convierte las pistas del nodo Video en [{ id, lang, name }]; idKey es la clave que identifica cada pista.
function trackList(tracks as Dynamic, idKey as String, nameKeys as Object) as Object
    result = []
    if tracks = invalid then return result
    for each track in tracks
        name = ""
        for each key in nameKeys
            if name = "" then name = textOf(track[key])
        end for
        result.Push({ id: textOf(track[idKey]), lang: textOf(track.Language), name: name })
    end for
    return result
end function

' Ejecuta cada orden que llega desde main.brs.
sub onRequest()
    req = m.top.request
    if req = invalid then return
    action = req.action

    if action = "play"
        if req.url <> "" then playNow(req)
    else if action = "queue"
        if req.url = "" then return
        if isActive()
            m.queue.Push(req)
            showStatus("Añadido a la cola: " + titleOf(req) + queueSuffix(), true)
        else
            playNow(req)
        end if
    else if action = "dequeue"
        index = Int(Val(req.index))
        if index >= 0 and index < m.queue.Count() then m.queue.Delete(index)
    else if action = "next"
        playNext()
    else if action = "toggle"
        if m.video.state = "playing"
            m.video.control = "pause"
        else if m.video.state = "paused"
            m.video.control = "resume"
        end if
    else if action = "pause"
        m.video.control = "pause"
    else if action = "resume"
        m.video.control = "resume"
    else if action = "skip"
        if isActive() then m.video.seek = clampPosition(m.video.position + Val(req.delta))
    else if action = "seek"
        if isActive() then m.video.seek = clampPosition(Val(req.pos))
    else if action = "stop"
        m.lastError = ""
        goIdle()
    else if action = "clearqueue"
        m.queue = []
        showStatus("Cola vacía.", true)
    else if action = "audio"
        if isActive() and req.id <> "" then m.video.audioTrack = req.id
    else if action = "subtitle"
        if isActive()
            m.subsChosen = true
            if req.id = "" or LCase(req.id) = "off"
                m.flipPending = false
                m.video.globalCaptionMode = "Off"
            else
                m.video.subtitleTrack = req.id
                m.video.globalCaptionMode = "On"
                ' En MKV el Roku marca la pista como activa pero no la dibuja hasta que se reabre la
                ' transmisión (probado: reposicionar, retroceder o reiniciar no bastan; cambiar de
                ' audio sí). Se hace ese cambio y su regreso, sin sonido, ver startAudioFlip().
                if Left(req.id, 5) <> "<dev_" then
                    if m.video.state = "playing" then startAudioFlip() else m.flipPending = true
                end if
            end if
        end if
    end if

    publishStatus()
end sub

function isActive() as Boolean
    ' Activo desde que se pide el video, no desde que el reproductor lo confirma: el estado del
    ' nodo Video tarda un instante en pasar a "buffering", y una orden de cola que llegue justo
    ' entonces reemplazaría el video en vez de esperar su turno.
    return m.current <> invalid
end function

function clampPosition(seconds as Float) as Float
    if seconds < 0 then return 0
    if m.video.duration > 0 and seconds > m.video.duration - 1 then return m.video.duration - 1
    return seconds
end function

function titleOf(req as Object) as String
    if req.title <> invalid and req.title <> "" then return req.title
    return "Video"
end function

function queueSuffix() as String
    if m.queue.Count() = 0 then return ""
    return "  (en cola: " + m.queue.Count().ToStr() + ")"
end function

sub playNext()
    if m.queue.Count() = 0
        goIdle()
        return
    end if
    playNow(m.queue.Shift())
end sub

sub playNow(req as Object)
    title = titleOf(req)
    m.current = req
    m.seq = m.seq + 1
    m.lastError = ""
    m.flipPending = false
    endAudioFlip()  ' Si había un cambio de audio a medias, devuelve el sonido

    content = CreateObject("roSGNode", "ContentNode")
    content.url = req.url
    content.title = title
    format = streamFormatFor(req.format, req.url)
    if format <> "" then content.streamFormat = format

    ' Punto de inicio en segundos, para retomar donde se quedó.
    startAt = Int(Val(req.start))
    if startAt > 0 then content.playStart = startAt

    ' Cabeceras HTTP opcionales ("Nombre: valor" separadas por saltos de línea),
    ' necesarias para algunos videos detectados en el navegador de la app.
    if req.headers <> invalid and req.headers <> ""
        headers = []
        for each line in req.headers.Split(Chr(10))
            if Instr(1, line, ":") > 1 then headers.Push(line)
        end for
        if headers.Count() > 0 then content.httpHeaders = headers
    end if

    ' Subtítulos externos: tienen que ir con el video. Cargarlos con el video ya en marcha lo congela.
    ' "sub" es una sola pista que empieza encendida; "subs" son varias ("idioma|nombre|url", una por línea)
    ' que empiezan apagadas y se eligen después.
    hasSub = req.subtitle <> invalid and req.subtitle <> ""
    tracks = []
    if hasSub then tracks.Push({ Language: "spa", TrackName: req.subtitle, Description: "Subtítulos" })
    for each track in parseExternalSubs(req.subs)
        tracks.Push(track)
    end for
    if tracks.Count() > 0 then content.subtitleTracks = tracks

    m.subsChosen = hasSub
    m.video.control = "stop"
    m.video.content = content
    if hasSub
        m.video.globalCaptionMode = "On"
        m.video.subtitleTrack = req.subtitle
    else
        m.video.globalCaptionMode = "Off"  ' Los subtítulos empiezan apagados; se eligen desde la app
    end if

    m.idle.visible = false
    m.video.visible = true
    m.video.setFocus(true)
    m.video.control = "play"

    if format = "" then format = "auto"
    showStatus("Cargando: " + title + "  (formato: " + format + ")" + queueSuffix(), false)
    publishStatus()
end sub

' Convierte "idioma|nombre|url" (una pista por línea) en la lista que espera ContentNode.subtitleTracks.
function parseExternalSubs(text as Dynamic) as Object
    result = []
    if GetInterface(text, "ifString") = invalid or text = "" then return result
    for each line in text.Split(Chr(10))
        first = Instr(1, line, "|")
        if first > 1
            second = Instr(first + 1, line, "|")
            if second > first + 1
                url = Mid(line, second + 1)
                if Left(url, 4) = "http"
                    result.Push({ Language: Left(line, first - 1), Description: Mid(line, first + 1, second - first - 1), TrackName: url })
                end if
            end if
        end if
    end for
    return result
end function

' Traduce el formato recibido (o la extensión de la URL) al streamFormat de Roku.
' Devuelve "" si no se sabe, y Roku intenta detectarlo solo.
function streamFormatFor(format as Dynamic, url as String) as String
    known = {
        mp4: "mp4", m4v: "mp4", mov: "mp4", mkv: "mkv",
        hls: "hls", m3u8: "hls", dash: "dash", mpd: "dash", ism: "ism"
    }
    if format <> invalid and format <> "" and known.DoesExist(format) then return known[format]

    path = LCase(url)
    queryStart = Instr(1, path, "?")
    if queryStart > 0 then path = Left(path, queryStart - 1)
    for each ext in known
        if Right(path, Len(ext) + 1) = "." + ext then return known[ext]
    end for
    return ""
end function

' Cambia a otra pista de audio y, pasados unos segundos, vuelve a la original, con el sonido
' silenciado para que no se oiga el otro idioma. Obliga al Roku a dibujar el subtítulo elegido.
' Sin una segunda pista de audio no hay a dónde cambiar y no se hace nada.
sub startAudioFlip()
    m.flipPending = false
    if m.flip <> invalid then return
    tracks = m.video.availableAudioTracks
    current = textOf(m.video.currentAudioTrack)
    if tracks = invalid or tracks.Count() < 2 or current = "" then return

    other = ""
    for each track in tracks
        if other = "" and textOf(track.Track) <> current then other = textOf(track.Track)
    end for
    if other = "" then return

    m.flip = { audio: current, mute: m.video.mute, stage: 1 }
    m.video.mute = true
    m.video.audioTrack = other
    m.flipTimer.duration = 3
    m.flipTimer.control = "start"
end sub

sub onFlipTimer()
    if m.flip = invalid then return
    if m.flip.stage = 1
        m.video.audioTrack = m.flip.audio
        m.flip.stage = 2
        m.flipTimer.duration = 2  ' Espera a que el audio original vuelva a sonar antes de quitar el silencio
        m.flipTimer.control = "start"
    else
        endAudioFlip()
    end if
end sub

sub endAudioFlip()
    if m.flip = invalid then return
    m.flipTimer.control = "stop"
    m.video.mute = m.flip.mute
    m.flip = invalid
end sub

' El Roku elige por su cuenta una pista de subtítulos unos segundos después de empezar
' (según su ajuste del sistema). Si nadie las pidió, se vuelven a apagar.
sub onSubtitleChanged()
    keepSubtitlesOffUnlessChosen()
end sub

sub keepSubtitlesOffUnlessChosen()
    if m.current = invalid or m.subsChosen then return
    if m.video.globalCaptionMode <> "Off"
        print "[FreeVideocaster] Subtítulos apagados (el Roku los activó solo)"
        m.video.globalCaptionMode = "Off"
    end if
end sub

sub onVideoState()
    state = m.video.state
    print "[FreeVideocaster] Estado del video: "; state
    if state = "playing"
        keepSubtitlesOffUnlessChosen()
        if m.flipPending then startAudioFlip()
    end if

    if state = "buffering"
        showStatus("Cargando…" + queueSuffix(), false)
    else if state = "playing"
        showStatus("Reproduciendo. " + audioDescription() + queueSuffix(), true)
    else if state = "error"
        showError()
    else if state = "finished"
        playNext()
    end if

    publishStatus()
end sub

' Indica el formato de la pista de audio activa (útil para detectar DTS/TrueHD sin sonido).
function audioDescription() as String
    tracks = m.video.availableAudioTracks
    if tracks = invalid or tracks.Count() = 0 then return ""
    for each track in tracks
        if track.Track = m.video.currentAudioTrack or tracks.Count() = 1
            text = "Audio: "
            if track.Format <> invalid and track.Format <> "" then text = text + track.Format + " "
            if track.Language <> invalid then text = text + track.Language
            return text
        end if
    end for
    return ""
end function

sub showError()
    code = m.video.errorCode
    hint = ""
    if code = -1
        hint = "Error HTTP: el enlace puede haber expirado o requerir permisos."
    else if code = -2
        hint = "Tiempo de espera agotado: el servidor no respondió."
    else if code = -5
        hint = "Formato o códec no compatible con este Roku."
    else if code = -6
        hint = "Contenido protegido (DRM): no se puede reproducir."
    end if

    detail = m.video.errorMsg
    if m.video.errorStr <> invalid and m.video.errorStr <> "" then detail = detail + " | " + m.video.errorStr

    print "[FreeVideocaster] ERROR "; code; ": "; detail
    showStatus("No se pudo reproducir (código " + code.ToStr() + "). " + hint + " " + detail, false)
    m.lastError = "Código " + code.ToStr() + ". " + hint
    m.flipPending = false
    endAudioFlip()
    m.current = invalid
    m.video.visible = false
    m.idle.visible = true
    m.top.setFocus(true)
end sub

sub showStatus(text as String, autoHide as Boolean)
    m.statusLabel.text = text
    m.statusBox.visible = true
    m.hideTimer.control = "stop"
    if autoHide then m.hideTimer.control = "start"
end sub

sub hideStatus()
    m.statusBox.visible = false
end sub

sub goIdle()
    m.flipPending = false
    endAudioFlip()
    m.video.control = "stop"
    m.video.visible = false
    m.idle.visible = true
    m.current = invalid
    ' Si terminó por un error, el mensaje se queda en pantalla para poder leerlo.
    if m.lastError = "" then hideStatus()
    m.top.setFocus(true)
    publishStatus()
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false

    ' "Atrás" durante el video vuelve a la pantalla de espera; desde ahí, cierra el canal.
    if key = "back" and m.video.visible
        m.lastError = ""
        goIdle()
        return true
    end if
    return false
end function
