package io.github.freevideocaster

import android.content.Context
import io.github.freevideocaster.data.AppPrefs
import io.github.freevideocaster.data.HistoryStore
import io.github.freevideocaster.data.Settings
import io.github.freevideocaster.diag.Diagnostics
import io.github.freevideocaster.media.LinkResolver
import io.github.freevideocaster.media.MediaRequest
import io.github.freevideocaster.media.TitleCleaner
import io.github.freevideocaster.net.LocalNetwork
import io.github.freevideocaster.roku.CastException
import io.github.freevideocaster.roku.ChannelClient
import io.github.freevideocaster.roku.EcpClient
import io.github.freevideocaster.roku.LiveState
import io.github.freevideocaster.roku.Playback
import io.github.freevideocaster.roku.RokuDevice
import io.github.freevideocaster.roku.SsdpDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * Resultado de un envío: mensaje para el usuario, si quedó en cola (en vez de reproducirse ya) y el título
 * y las etiquetas con que se mostrará el video.
 */
data class CastResult(val message: String, val queued: Boolean, val title: String = "", val detail: String = "")

/** Cómo terminó la espera tras enviar: el mensaje y si el Roku ya está reproduciendo. */
data class PlaybackResult(val message: String, val started: Boolean, val failed: Boolean = false)

/**
 * Punto central de la app: elegir Roku, enviar videos, controlar la reproducción.
 * Todos los errores salen como CastException con un mensaje listo para mostrar.
 */
class Caster(
    private val context: Context,
    private val ecp: EcpClient,
    private val channel: ChannelClient,
    private val resolver: LinkResolver,
    private val discovery: SsdpDiscovery,
    val settings: Settings,
    val history: HistoryStore,
    scope: CoroutineScope,
) {
    val roku: Flow<RokuDevice?> = settings.roku

    /**
     * Preferencias de la persona. El primer valor se lee de golpe (DataStore tarda pocos milisegundos) para
     * que quien eligió el tema oscuro no vea un fogonazo claro al abrir la app.
     */
    val prefs: StateFlow<AppPrefs> =
        settings.prefs.stateIn(scope, SharingStarted.Eagerly, runBlocking { settings.prefs.first() })

    val diagnostics = Diagnostics(context, ecp, channel, resolver, discovery, settings)

    /**
     * Estado del Roku consultado cada segundo mientras alguien lo observe (pantalla de control o
     * notificación). Una sola consulta compartida, aunque lo miren varios a la vez.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<LiveState?> = settings.roku
        .flatMapLatest { device -> if (device == null) flowOf(null) else pollLoop(device) }
        .stateIn(scope, SharingStarted.WhileSubscribed(3000), null)

    private fun pollLoop(device: RokuDevice): Flow<LiveState> = flow {
        var previous: LiveState? = null
        var tick = 0
        while (true) {
            val next = runCatching { poll(device, previous, tick++) }.getOrDefault(LiveState.Offline)
            emit(next)
            previous = next
            delay(
                when {
                    !next.reachable -> 3000L
                    next.isActive -> 1000L
                    else -> 2500L
                },
            )
        }
    }

    private suspend fun poll(device: RokuDevice, previous: LiveState?, tick: Int): LiveState {
        if (!LocalNetwork.isOnLocalNetwork(context) || !LocalNetwork.hasPermission(context)) return LiveState.Offline

        val status = channel.status(device.ip)
        if (status != null) {
            var audio = previous?.audioCodec
            var video = previous?.videoCodec
            if (status.playback == Playback.Idle) {
                audio = null
                video = null
            } else if (previous == null || previous.key != status.key || tick % CODEC_EVERY == 0) {
                // El canal no sabe qué códec tiene el video; eso solo lo dice el reproductor del Roku.
                runCatching { ecp.mediaPlayer(device.ip) }.getOrNull()?.let {
                    audio = it.audioCodec
                    video = it.videoCodec
                }
            }
            return LiveState(
                reachable = true,
                ours = true,
                playback = status.playback,
                key = status.key,
                title = status.title,
                positionMs = status.positionMs,
                durationMs = status.durationMs,
                queue = status.queue,
                error = status.error,
                audioCodec = audio,
                videoCodec = video,
                audio = status.audio,
                audioCurrent = status.audioCurrent,
                subtitles = status.subtitles,
                subtitleCurrent = status.subtitleCurrent,
            )
        }

        // Sin canal v2 abierto: lo poco que se sabe viene del reproductor del Roku.
        val player = runCatching { ecp.mediaPlayer(device.ip) }.getOrNull() ?: return LiveState.Offline
        return LiveState(
            reachable = true,
            ours = player.appId == EcpClient.DEV_CHANNEL,
            playback = when (player.state) {
                "play" -> Playback.Playing
                "pause" -> Playback.Paused
                "buffer" -> Playback.Buffering
                else -> Playback.Idle
            },
            positionMs = player.positionMs,
            durationMs = player.durationMs,
            audioCodec = player.audioCodec,
            videoCodec = player.videoCodec,
        )
    }

    // --- Elegir Roku -----------------------------------------------------------------------

    suspend fun discover(): List<RokuDevice> {
        checkNetwork()
        val ips = discovery.discover()
        return coroutineScope {
            ips.map { ip -> async { runCatching { ecp.deviceInfo(ip) }.getOrNull() } }.awaitAll().filterNotNull()
        }
    }

    suspend fun select(device: RokuDevice) = settings.saveRoku(device)

    suspend fun selectByIp(ip: String): RokuDevice {
        val clean = ip.trim()
        if (!EcpClient.isPrivateIp(clean)) {
            throw CastException("«$clean» no es una IP de red local válida (ejemplo: 192.168.1.50).")
        }
        checkNetwork()
        return ecp.deviceInfo(clean).also { settings.saveRoku(it) }
    }

    // --- Enviar videos ---------------------------------------------------------------------

    /** Seq del canal justo antes de enviar; sirve para no confundir el error de un video anterior con el actual. */
    private var seqBeforeCast = 0

    /**
     * Envía un video al Roku. Con [enqueue] lo añade a la cola si ya hay algo reproduciéndose.
     * Con [startMs] > 0 empieza en ese punto (para retomar).
     */
    suspend fun cast(
        request: MediaRequest,
        enqueue: Boolean,
        startMs: Long = 0,
        onProgress: (String) -> Unit = {},
    ): CastResult {
        checkNetwork()
        onProgress("Conectando con el Roku…")
        val device = currentDevice(verify = true)

        onProgress("Comprobando el enlace…")
        val resolved = resolver.resolve(request)
        if (resolved.httpCode in DEAD_LINK_CODES) {
            throw CastException(
                "El enlace respondió con error HTTP ${resolved.httpCode}: probablemente expiró. " +
                    "Vuelve a abrirlo desde Stremio."
            )
        }

        if (resolved.isWebPage) {
            throw CastException(
                "Este enlace es una página web, no un video. Ábrela en la pestaña «Navegador» y la app " +
                    "detectará el video para enviarlo."
            )
        }

        val shown = nameFor(request, resolved)
        val title = shown.title.ifBlank { "Video" }
        val detail = request.detail.ifBlank { shown.detail }

        val before = channel.status(device.ip)
        seqBeforeCast = before?.seq ?: 0
        val busy = enqueue && before != null && before.playback != Playback.Idle

        val params = buildMap {
            put("action", if (enqueue) "queue" else "play")
            put("url", resolved.finalUrl)
            put("title", title)
            put("key", request.key)
            (request.format ?: resolved.format)?.let { put("format", it) }
            if (startMs >= 1000) put("start", (startMs / 1000).toString())
            request.subtitleUrl?.let { put("sub", it) }
            if (request.headers.isNotEmpty()) {
                put("headers", request.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            }
        }
        onProgress("Enviando a ${device.name}…")
        ecp.sendToChannel(device.ip, params)

        history.add(request.copy(title = title, detail = detail))
        CastSessionService.start(context)

        return if (busy) {
            CastResult("Añadido a la cola de ${device.name}.", queued = true, title = title, detail = detail)
        } else {
            CastResult("Enviado a ${device.name}.", queued = false, title = title, detail = detail)
        }
    }

    /**
     * Título y etiquetas legibles de un enlace antes de enviarlo. Stremio suele mandar un título inútil
     * («playback») y el nombre real solo aparece al abrir el enlace, así que aquí se consulta como al enviar.
     */
    suspend fun describe(request: MediaRequest): TitleCleaner.Parsed =
        nameFor(request, resolver.resolve(request))

    /** El título que manda Stremio suele ser basura («playback»); entonces se usa el nombre del archivo. */
    private fun nameFor(request: MediaRequest, resolved: LinkResolver.Resolved): TitleCleaner.Parsed {
        val rawName = listOf(request.title, resolved.fileName, TitleCleaner.nameFromUrl(resolved.finalUrl), TitleCleaner.nameFromUrl(request.url))
            .firstOrNull { !TitleCleaner.isUseless(it) }
            .orEmpty()
        return TitleCleaner.parse(rawName)
    }

    /** Espera unos segundos a que el Roku empiece a reproducir y resume el resultado. */
    suspend fun awaitPlayback(): PlaybackResult {
        val device = settings.roku.first() ?: return PlaybackResult("", started = false)
        val minSeq = seqBeforeCast + 1
        var sawLoading = false
        repeat(25) {
            delay(1000)
            val status = channel.status(device.ip)
            if (status != null) {
                // Mientras seq no suba, el estado todavía es el del video anterior.
                if (status.seq >= minSeq) {
                    when {
                        status.playback == Playback.Playing -> {
                            val warning = runCatching { ecp.mediaPlayer(device.ip) }.getOrNull()?.audioWarning
                            return PlaybackResult(listOfNotNull("Reproduciendo en ${device.name}.", warning).joinToString(" "), started = true)
                        }
                        status.playback == Playback.Idle && status.error.isNotBlank() ->
                            return PlaybackResult("El Roku no pudo reproducirlo. ${status.error}", started = false, failed = true)
                    }
                }
                return@repeat
            }
            // Canal antiguo sin estado en vivo: se deduce del reproductor del Roku.
            val player = runCatching { ecp.mediaPlayer(device.ip) }.getOrNull() ?: return@repeat
            when {
                player.error -> return PlaybackResult("El Roku no pudo reproducirlo. Mira el mensaje en la TV.", started = false, failed = true)
                player.isPlaying -> return PlaybackResult(
                    listOfNotNull("Reproduciendo en ${device.name}.", player.audioWarning).joinToString(" "),
                    started = true,
                )
                player.state == "buffer" -> sawLoading = true
                sawLoading && player.state in setOf("stop", "close", "none") ->
                    return PlaybackResult("El Roku no pudo reproducirlo. Mira el mensaje en la TV.", started = false, failed = true)
            }
        }
        return PlaybackResult("Enviado. El Roku sigue cargando el video.", started = false)
    }

    // --- Control remoto --------------------------------------------------------------------

    /** Órdenes para el canal FreeVideocaster: toggle, skip, seek, next, stop, clearqueue, dequeue. */
    suspend fun command(action: String, extra: Map<String, String> = emptyMap()) {
        checkNetwork()
        ecp.input(currentDevice(verify = false).ip, mapOf("action" to action) + extra)
    }

    suspend fun removeFromQueue(index: Int) = command("dequeue", mapOf("index" to index.toString()))

    /** Cambia la pista de audio del video actual. */
    suspend fun setAudioTrack(id: String) = command("audio", mapOf("id" to id))

    /** Activa la pista de subtítulos [id], o los desactiva si es null. */
    suspend fun setSubtitleTrack(id: String?) = command("subtitle", mapOf("id" to (id ?: "off")))

    /** Teclas del control remoto de Roku (Back, Home, VolumeUp…). */
    suspend fun keypress(key: String) {
        checkNetwork()
        ecp.keypress(currentDevice(verify = false).ip, key)
    }

    /** Guarda hasta dónde se vio el video actual, para poder retomarlo. */
    suspend fun saveProgress(state: LiveState) {
        if (state.ours && state.key.isNotEmpty() && state.durationMs > 0) {
            history.updateProgress(state.key, state.positionMs, state.durationMs)
        }
    }

    // --- Utilidades ------------------------------------------------------------------------

    private fun checkNetwork() {
        if (!LocalNetwork.isOnLocalNetwork(context)) {
            throw CastException("El teléfono no está en una red Wi-Fi. Conéctalo a la misma red que el Roku.")
        }
        if (!LocalNetwork.hasPermission(context)) {
            throw CastException("Falta el permiso de red local. Concédelo desde la pantalla principal de la app.")
        }
    }

    /** Devuelve el Roku guardado. Si ya no responde en su IP, lo busca por número de serie. */
    private suspend fun currentDevice(verify: Boolean): RokuDevice {
        val saved = settings.roku.first()
            ?: throw CastException("Primero elige tu Roku en la pestaña «Enviar».")
        if (!verify) return saved
        return try {
            ecp.deviceInfo(saved.ip)
            saved
        } catch (e: CastException) {
            if (saved.serial.isEmpty()) throw e
            val found = runCatching { discover() }.getOrDefault(emptyList()).firstOrNull { it.serial == saved.serial }
                ?: throw e
            settings.saveRoku(found)
            found
        }
    }

    private companion object {
        val DEAD_LINK_CODES = setOf(401, 403, 404, 410)
        const val CODEC_EVERY = 5
    }
}
