package io.github.freevideocaster.roku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Habla con el Roku por ECP (External Control Protocol): HTTP en el puerto 8060.
 * Solo acepta IPs privadas de la red local.
 */
class EcpClient(baseClient: OkHttpClient) {

    private val http = baseClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun deviceInfo(ip: String): RokuDevice {
        val xml = XmlFields.parse(call(ip, "/query/device-info", post = false))
        val name = xml.text["user-device-name"].orEmpty()
            .ifBlank { xml.text["friendly-device-name"].orEmpty() }
            .ifBlank { "Roku" }
        val isTv = xml.text["is-tv"] == "true"
        return RokuDevice(
            ip = ip,
            serial = xml.text["serial-number"].orEmpty(),
            name = name,
            model = listOfNotNull(xml.text["model-name"], xml.text["model-number"]).joinToString(" "),
            isTv = isTv,
            supportsVolume = isTv || xml.text["supports-audio-volume-control"] == "true",
            ecpMode = xml.text["ecp-setting-mode"].orEmpty(),
            developerEnabled = xml.text["developer-enabled"] == "true",
            softwareVersion = xml.text["software-version"].orEmpty(),
            uiResolution = xml.text["ui-resolution"].orEmpty(),
        )
    }

    /** Apps instaladas en el Roku como pares (id, nombre). */
    suspend fun apps(ip: String): List<Pair<String, String>> =
        XmlFields.parseApps(call(ip, "/query/apps", post = false))

    suspend fun activeAppId(ip: String): String? =
        XmlFields.parse(call(ip, "/query/active-app", post = false)).attrs["app"]?.get("id")

    suspend fun mediaPlayer(ip: String): PlayerStatus {
        val xml = XmlFields.parse(call(ip, "/query/media-player", post = false))
        val player = xml.attrs["player"].orEmpty()
        val format = xml.attrs["format"].orEmpty()
        return PlayerStatus(
            appId = xml.attrs["plugin"]?.get("id"),
            state = player["state"] ?: "none",
            error = player["error"] == "true",
            positionMs = millis(xml.text["position"]),
            durationMs = millis(xml.text["duration"]),
            audioCodec = format["audio"],
            videoCodec = format["video"],
        )
    }

    suspend fun keypress(ip: String, key: String) {
        call(ip, "/keypress/$key", post = true)
    }

    /** Envía una orden al canal FreeVideocaster, que debe estar abierto. */
    suspend fun input(ip: String, params: Map<String, String>) {
        inputPaths(params).forEach { path -> call(ip, path, post = true) }
    }

    /** Abre el canal si hace falta y le envía la orden. */
    suspend fun sendToChannel(ip: String, params: Map<String, String>) {
        if (activeAppId(ip) == DEV_CHANNEL) {
            input(ip, params)
        } else if (!needsChunks(params)) {
            call(ip, "/launch/$DEV_CHANNEL?${encodeQuery(params)}", post = true)
        } else {
            call(ip, "/launch/$DEV_CHANNEL", post = true)
            waitForChannel(ip)
            input(ip, params)
        }
    }

    private suspend fun waitForChannel(ip: String) {
        repeat(30) {
            delay(300)
            if (runCatching { activeAppId(ip) }.getOrNull() == DEV_CHANNEL) {
                delay(700) // Margen para que el canal empiece a escuchar órdenes
                return
            }
        }
        throw CastException("El canal FreeVideocaster no terminó de abrirse en el Roku.")
    }

    private suspend fun call(ip: String, path: String, post: Boolean): String = withContext(Dispatchers.IO) {
        if (!isPrivateIp(ip)) throw CastException("«$ip» no es una IP de la red local.")
        val request = Request.Builder()
            .url("http://$ip:$PORT$path")
            .apply { if (post) post(ByteArray(0).toRequestBody()) }
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body.string()
                    response.code == 403 -> throw CastException(
                        "El Roku rechazó la orden. Actívalo en el Roku: Configuración › Sistema › " +
                            "Configuración avanzada del sistema › Control por aplicaciones móviles."
                    )
                    response.code == 404 && path.startsWith("/launch/$DEV_CHANNEL") -> throw CastException(
                        "El canal FreeVideocaster no está instalado en el Roku (modo desarrollador)."
                    )
                    else -> throw CastException("El Roku respondió con un error (HTTP ${response.code}).")
                }
            }
        } catch (e: CastException) {
            throw e
        } catch (e: IOException) {
            throw CastException(networkErrorMessage(ip, e))
        }
    }

    private fun networkErrorMessage(ip: String, e: IOException): String {
        val detail = e.message.orEmpty()
        return when {
            "EPERM" in detail || "EACCES" in detail || "not permitted" in detail ->
                "Android bloqueó el acceso a la red local. Concede a FreeVideocaster el permiso de red local."
            e is SocketTimeoutException || e is ConnectException || e is NoRouteToHostException ->
                "No se pudo conectar con el Roku en $ip. ¿Está encendido y en la misma red Wi-Fi?"
            else -> "Error de red con el Roku en $ip: $detail"
        }
    }

    companion object {
        const val PORT = 8060
        const val DEV_CHANNEL = "dev"

        /** Valores más largos que esto se envían troceados (Roku documenta 255 para contentId). */
        private const val MAX_VALUE = 240
        private const val CHUNK_SIZE = 180

        private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

        fun isPrivateIp(ip: String): Boolean {
            val octets = IPV4.matchEntire(ip.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
            if (octets.any { it > 255 }) return false
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }

        /**
         * Rutas /input para enviar [params]. Si algún valor es demasiado largo, la consulta completa
         * se manda en trozos (cid, i, n, chunk) que el canal reconstruye en orden.
         */
        internal fun inputPaths(params: Map<String, String>, id: String = UUID.randomUUID().toString().take(8)): List<String> {
            val query = encodeQuery(params)
            if (!needsChunks(params)) return listOf("/input?$query")
            val pieces = query.chunked(CHUNK_SIZE)
            return pieces.mapIndexed { index, piece -> "/input?cid=$id&i=$index&n=${pieces.size}&chunk=${encode(piece)}" }
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun encodeQuery(params: Map<String, String>) =
            params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

        private fun needsChunks(params: Map<String, String>) = params.values.any { it.length > MAX_VALUE }

        private fun millis(text: String?): Long = text?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
    }
}
